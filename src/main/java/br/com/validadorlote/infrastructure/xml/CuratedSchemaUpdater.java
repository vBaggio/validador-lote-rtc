package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.infrastructure.tables.SafeHttpsClient;
import br.com.validadorlote.infrastructure.update.ArtifactCheckResult;
import br.com.validadorlote.infrastructure.update.ArtifactFailureKind;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCandidate;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** Adquire e prepara schemas somente de um manifesto curado e assinado. */
public final class CuratedSchemaUpdater {

    private static final Pattern APP_VERSION = Pattern.compile(
            "(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)");

    private final SafeHttpsClient manifestHttps;
    private final SafeHttpsClient zipHttps;
    private final CuratedSchemaManifestParser parser;
    private final Ed25519ManifestVerifier verifier;
    private final SchemaExtractor extractor;
    private final StagingCleaner cleaner;
    private final SchemaArtifactStore store;
    private final String channelId;
    private final URI manifestUri;
    private final String appVersion;

    public CuratedSchemaUpdater(SafeHttpsClient manifestHttps, SafeHttpsClient zipHttps,
            CuratedSchemaManifestParser parser, Ed25519ManifestVerifier verifier,
            SchemaZipExtractor zip, SchemaArtifactStore store, String channelId,
            URI manifestUri, String appVersion) {
        this(manifestHttps, zipHttps, parser, verifier, store, channelId, manifestUri,
                appVersion, requireExtractor(zip), CuratedSchemaUpdater::deleteStaging);
    }

    CuratedSchemaUpdater(SafeHttpsClient manifestHttps, SafeHttpsClient zipHttps,
            CuratedSchemaManifestParser parser, Ed25519ManifestVerifier verifier,
            SchemaArtifactStore store, String channelId, URI manifestUri, String appVersion,
            SchemaExtractor extractor, StagingCleaner cleaner) {
        this.manifestHttps = Objects.requireNonNull(manifestHttps);
        this.zipHttps = Objects.requireNonNull(zipHttps);
        this.parser = Objects.requireNonNull(parser);
        this.verifier = Objects.requireNonNull(verifier);
        this.extractor = Objects.requireNonNull(extractor);
        this.cleaner = Objects.requireNonNull(cleaner);
        this.store = Objects.requireNonNull(store);
        this.channelId = requireChannelId(channelId);
        this.manifestUri = Objects.requireNonNull(manifestUri);
        this.appVersion = requireAppVersion(appVersion);
    }

    /** Verifica confiança, compatibilidade e integridade antes de preparar a candidata. */
    public ArtifactCheckResult check() {
        CuratedSchemaChannelManifest manifest = parser.parse(
                manifestHttps.getBytes(manifestUri));
        verifier.verify(manifest.keyId(), parser.canonicalSignedBytes(manifest),
                manifest.signature());

        CuratedSchemaChannelManifest.SignedRelease release = manifest.signed();
        requireCompatibleRelease(release);
        ArtifactCheckResult sequenceResult = checkSequence(release);
        if (sequenceResult != null) return sequenceResult;

        byte[] downloadedZip = zipHttps.getBytes(release.zipUrl());
        verifyZipHash(downloadedZip, release.zipSha256());
        Path candidate = extract(downloadedZip);
        ArtifactCheckResult result;
        try {
            ArtifactManifest prepared = store.prepare(candidate, release, channelId,
                    manifestUri.toString());
            String detail = "Schemas curados preparados para ativação";
            result = ArtifactCheckResult.available(new ArtifactUpdateCandidate(
                    ArtifactId.NFE_SCHEMAS,
                    prepared.version(),
                    prepared.sourceUrl(),
                    prepared.publishedAt(),
                    prepared.sha256(),
                    detail), detail);
        } catch (RuntimeException failure) {
            cleanup(candidate, failure);
            throw failure;
        }
        cleanup(candidate, null);
        return result;
    }

    /** Ativa somente uma candidata de schemas que já passou pela preparação. */
    public ArtifactManifest apply(ArtifactUpdateCandidate candidate) {
        if (candidate == null || candidate.artifact() != ArtifactId.NFE_SCHEMAS) {
            throw new IllegalArgumentException(
                    "Candidata não corresponde aos schemas NF-e");
        }
        return store.activate(candidate.version());
    }

    private void requireCompatibleRelease(
            CuratedSchemaChannelManifest.SignedRelease release) {
        if (release.artifact() != ArtifactId.NFE_SCHEMAS) {
            throw ArtifactUpdateException.invalidContent(
                    "Manifesto incompatível: artefato não corresponde aos schemas NF-e");
        }
        if (compareAppVersions(release.minimumAppVersion(), appVersion) > 0) {
            throw ArtifactUpdateException.unsupportedSchemaStructure(
                    "Manifesto incompatível: schemas exigem versão mais nova do aplicativo");
        }
    }

    private ArtifactCheckResult checkSequence(
            CuratedSchemaChannelManifest.SignedRelease release) {
        ArtifactManifest active = store.activeManifestOrNull();
        if (active == null || !channelId.equals(active.channelId())) return null;
        if (release.releaseSequence() == active.releaseSequence()) {
            return ArtifactCheckResult.upToDate(
                    "O canal curado já está na sequência ativa");
        }
        if (release.releaseSequence() < active.releaseSequence()) {
            throw ArtifactUpdateException.invalidContent(
                    "Manifesto do canal de schemas tenta rollback da sequência ativa");
        }
        return null;
    }

    private static void verifyZipHash(byte[] downloadedZip, String expectedHex) {
        byte[] expected = HexFormat.of().parseHex(expectedHex);
        byte[] actual;
        try {
            actual = MessageDigest.getInstance("SHA-256").digest(downloadedZip);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw ArtifactUpdateException.invalidContent(
                    "O hash SHA-256 do ZIP de schemas não confere");
        }
    }

    private Path extract(byte[] downloadedZip) {
        try {
            return extractor.extract(downloadedZip);
        } catch (ArtifactUpdateException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            if (hasIoCause(failure)) {
                throw ArtifactUpdateException.localStorage(
                        "Não foi possível preparar o staging local dos schemas", failure);
            }
            throw ArtifactUpdateException.invalidContent(
                    "ZIP do canal de schemas contém dados inválidos", failure);
        }
    }

    private static int compareAppVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        for (int index = 0; index < 3; index++) {
            int comparison = Integer.compare(
                    Integer.parseInt(leftParts[index]),
                    Integer.parseInt(rightParts[index]));
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static String requireChannelId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalArgumentException("Identidade do canal de schemas inválida");
        }
        return value;
    }

    private static String requireAppVersion(String value) {
        if (value == null || !APP_VERSION.matcher(value).matches()) {
            throw new IllegalArgumentException("Versão do aplicativo inválida");
        }
        try {
            for (String component : value.split("\\.")) Integer.parseInt(component);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Versão do aplicativo inválida", failure);
        }
        return value;
    }

    private void cleanup(Path root, RuntimeException primaryFailure) {
        try {
            cleaner.delete(root);
        } catch (IOException | RuntimeException failure) {
            ArtifactUpdateException cleanupFailure = failure instanceof ArtifactUpdateException typed
                    && typed.kind() == ArtifactFailureKind.LOCAL_STORAGE
                    ? typed
                    : ArtifactUpdateException.localStorage(
                            "Não foi possível limpar o staging local dos schemas", failure);
            if (primaryFailure != null) {
                primaryFailure.addSuppressed(cleanupFailure);
                return;
            }
            throw cleanupFailure;
        }
    }

    private static boolean hasIoCause(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof IOException || current instanceof UncheckedIOException) {
                return true;
            }
        }
        return false;
    }

    private static SchemaExtractor requireExtractor(SchemaZipExtractor zip) {
        return Objects.requireNonNull(zip)::extract;
    }

    private static void deleteStaging(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @FunctionalInterface
    interface SchemaExtractor {
        Path extract(byte[] zip);
    }

    @FunctionalInterface
    interface StagingCleaner {
        void delete(Path root) throws IOException;
    }
}
