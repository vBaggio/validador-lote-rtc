package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.infrastructure.tables.HttpsTransport;
import br.com.validadorlote.infrastructure.tables.SafeHttpsClient;
import br.com.validadorlote.infrastructure.update.ArtifactCheckResult;
import br.com.validadorlote.infrastructure.update.ArtifactFailureKind;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCandidate;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CuratedSchemaUpdaterTest {

    private static final String KEY_ID = "test-key-2026";
    private static final String CHANNEL = "curated-schemas-stable-v1";
    private static final URI MANIFEST_URI =
            URI.create("https://manifest.test/schemas/stable.json");
    private static final URI ZIP_URI =
            URI.create("https://downloads.test/schemas/rtc.zip");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-30T12:00:00Z");

    @TempDir Path temp;

    private final CuratedSchemaManifestParser parser = new CuratedSchemaManifestParser();
    private final SchemaZipExtractor zip = new SchemaZipExtractor();
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private RecordingTransport transport;
    private SchemaArtifactStore store;
    private KeyPair keyPair;
    private CuratedSchemaUpdater updater;

    @BeforeEach
    void setUp() {
        transport = new RecordingTransport();
        store = new SchemaArtifactStore(temp);
        keyPair = generateKeyPair();
        updater = updater(
                new Ed25519ManifestVerifier(Map.of(KEY_ID,
                        Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))),
                SafeHttpsClient.SCHEMA_MAX_BYTES);
    }

    @Test
    void preparesOnlyASignedNewerZipWhoseHashMatchesTheManifest() throws IOException {
        byte[] validZip = validZip();
        transport.respond(MANIFEST_URI, signedManifest(release(8, sha256(validZip))));
        transport.respond(ZIP_URI, validZip);

        ArtifactCheckResult result = updater.check();

        assertThat(result.status()).isEqualTo(ArtifactCheckResult.Status.UPDATE_AVAILABLE);
        assertThat(store.activeManifestOrNull()).isNull();
        assertThat(preparedManifest(result.candidate().version()).getProperty("releaseSequence"))
                .isEqualTo("8");
        assertThat(preparedManifest(result.candidate().version()).getProperty("zipSha256"))
                .isEqualTo(sha256(validZip));
    }

    @Test
    void applyActivatesOnlyThePreparedSchemaCandidate() throws IOException {
        byte[] validZip = validZip();
        transport.respond(MANIFEST_URI, signedManifest(release(8, sha256(validZip))));
        transport.respond(ZIP_URI, validZip);
        ArtifactUpdateCandidate candidate = updater.check().candidate();

        ArtifactManifest activated = updater.apply(candidate);

        assertThat(activated.version()).isEqualTo("rtc-8");
        assertThat(store.activeManifestOrNull().version()).isEqualTo("rtc-8");
        assertThatThrownBy(() -> updater.apply(new ArtifactUpdateCandidate(
                ArtifactId.FISCAL_TABLES, "rtc-8", ZIP_URI.toString(), PUBLISHED_AT,
                "a".repeat(64), "inválida")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverExtractsTamperedZipAndKeepsThePreviousBase() throws IOException {
        installAndActivateRelease(7);
        byte[] validZip = validZip();
        transport.respond(MANIFEST_URI, signedManifest(release(8, sha256(validZip))));
        transport.respond(ZIP_URI, "not-the-signed-zip".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(updater::check)
                .isInstanceOf(ArtifactUpdateException.class)
                .hasMessageContaining("hash");

        assertCurrentRelease7AndNoPrepared8();
    }

    @Test
    void rejectsAlteredSignatureBeforeRequestingOrExtractingTheZip() throws IOException {
        installAndActivateRelease(7);
        byte[] manifest = signedManifest(release(8, "a".repeat(64)));
        ObjectNode root = readObject(manifest);
        ((ObjectNode) root.get("signed")).put("version", "rtc-altered");
        transport.respond(MANIFEST_URI, write(root));

        assertInvalidContent(updater::check);

        assertThat(transport.requests()).containsExactly(MANIFEST_URI);
        assertCurrentRelease7AndNoPrepared8();
    }

    @Test
    void rejectsUnknownPublicKeyBeforeRequestingOrExtractingTheZip() throws IOException {
        installAndActivateRelease(7);
        ObjectNode root = readObject(signedManifest(release(8, "a".repeat(64))));
        root.put("keyId", "unknown-key");
        transport.respond(MANIFEST_URI, write(root));

        assertInvalidContent(updater::check);

        assertThat(transport.requests()).containsExactly(MANIFEST_URI);
        assertCurrentRelease7AndNoPrepared8();
    }

    @Test
    void rejectsZipRedirectToAHostOutsideItsIndependentAllowlist() throws IOException {
        installAndActivateRelease(7);
        transport.respond(MANIFEST_URI, signedManifest(release(8, "a".repeat(64))));
        transport.redirect(ZIP_URI, URI.create("https://manifest.test/schemas/redirected.zip"));

        assertInvalidContent(updater::check);

        assertThat(transport.requests()).containsExactly(MANIFEST_URI, ZIP_URI);
        assertCurrentRelease7AndNoPrepared8();
    }

    @Test
    void rejectsOversizedZipBeforeExtraction() throws IOException {
        installAndActivateRelease(7);
        byte[] body = "12345".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        updater = updater(new Ed25519ManifestVerifier(Map.of(KEY_ID,
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))), 4);
        transport.respond(MANIFEST_URI, signedManifest(release(8, sha256(body))));
        transport.respond(ZIP_URI, body);

        assertThatThrownBy(updater::check)
                .isInstanceOf(ArtifactUpdateException.class)
                .hasMessageContaining("limite");

        assertCurrentRelease7AndNoPrepared8();
    }

    @Test
    void rejectsRollbackSequenceWithoutDownloadingTheZip() throws IOException {
        installAndActivateRelease(7);
        transport.respond(MANIFEST_URI, signedManifest(release(6, "a".repeat(64))));

        assertThatThrownBy(updater::check)
                .isInstanceOf(ArtifactUpdateException.class)
                .hasMessageContaining("rollback");

        assertThat(transport.requests()).containsExactly(MANIFEST_URI);
        assertCurrentRelease7AndNoPrepared8();
    }

    @Test
    void returnsUpToDateOnlyForTheEqualActiveSequenceWithoutDownloadingTheZip()
            throws IOException {
        installAndActivateRelease(7);
        transport.respond(MANIFEST_URI, signedManifest(release(7, "a".repeat(64))));

        ArtifactCheckResult result = updater.check();

        assertThat(result.status()).isEqualTo(ArtifactCheckResult.Status.UP_TO_DATE);
        assertThat(result.candidate()).isNull();
        assertThat(transport.requests()).containsExactly(MANIFEST_URI);
        assertThat(store.activeManifestOrNull().version()).isEqualTo("rtc-7");
    }

    @Test
    void rejectsMalformedZipAndKeepsThePreviousBase() throws IOException {
        installAndActivateRelease(7);
        byte[] malformed = "not-a-zip".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        transport.respond(MANIFEST_URI, signedManifest(release(8, sha256(malformed))));
        transport.respond(ZIP_URI, malformed);

        assertInvalidContent(updater::check);

        assertCurrentRelease7AndNoPrepared8();
    }

    @Test
    void rejectsMinimumAppVersionNewerThanTheAppBeforeDownloadingTheZip()
            throws IOException {
        installAndActivateRelease(7);
        transport.respond(MANIFEST_URI, signedManifest(
                release(8, "a".repeat(64), ArtifactId.NFE_SCHEMAS, "1.10.0")));

        ArtifactUpdateException failure = captureUpdateFailure(updater::check);

        assertThat(failure.kind())
                .isEqualTo(ArtifactFailureKind.UNSUPPORTED_SCHEMA_STRUCTURE);
        assertThat(failure.retryable()).isFalse();
        assertThat(transport.requests()).containsExactly(MANIFEST_URI);
        assertCurrentRelease7AndNoPrepared8();
    }

    @Test
    void rejectsAnotherArtifactBeforeDownloadingTheZip() throws IOException {
        installAndActivateRelease(7);
        transport.respond(MANIFEST_URI, signedManifest(
                release(8, "a".repeat(64), ArtifactId.FISCAL_TABLES, "1.0.0")));

        assertInvalidContent(updater::check);

        assertThat(transport.requests()).containsExactly(MANIFEST_URI);
        assertCurrentRelease7AndNoPrepared8();
    }

    @Test
    void classifiesStagingCreationFailureAsLocalStorageAndKeepsCurrent()
            throws IOException {
        installAndActivateRelease(7);
        configureValidRelease8();
        updater = updater(bytes -> {
            throw new IllegalStateException("Não foi possível criar staging",
                    new IOException("disco indisponível"));
        }, path -> {
            throw new AssertionError("Não existe staging para limpar");
        });

        ArtifactUpdateException failure = captureUpdateFailure(updater::check);

        assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.LOCAL_STORAGE);
        assertThat(failure.retryable()).isFalse();
        assertCurrentRelease7AndNoPrepared8();
    }

    @Test
    void classifiesStagingWriteFailureAsLocalStorageAndKeepsCurrent()
            throws IOException {
        installAndActivateRelease(7);
        configureValidRelease8();
        updater = updater(bytes -> {
            throw new IllegalStateException("Não foi possível escrever staging",
                    new UncheckedIOException(new IOException("volume somente leitura")));
        }, path -> {
            throw new AssertionError("Não existe staging para limpar");
        });

        ArtifactUpdateException failure = captureUpdateFailure(updater::check);

        assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.LOCAL_STORAGE);
        assertThat(failure.retryable()).isFalse();
        assertCurrentRelease7AndNoPrepared8();
    }

    @Test
    void preservesAnAlreadyTypedExtractorFailure() throws IOException {
        installAndActivateRelease(7);
        configureValidRelease8();
        ArtifactUpdateException typed = ArtifactUpdateException.localStorage(
                "Não foi possível gravar os schemas", new IOException("falha local"));
        updater = updater(bytes -> {
            throw typed;
        }, path -> {
            throw new AssertionError("Não existe staging para limpar");
        });

        Throwable failure = catchThrowable(updater::check);

        assertThat(failure).isSameAs(typed);
        assertCurrentRelease7AndNoPrepared8();
    }

    @Test
    void classifiesCleanupFailureAsLocalStorageWithoutChangingCurrent()
            throws IOException {
        installAndActivateRelease(7);
        byte[] body = configureValidRelease8();
        AtomicReference<Path> staging = new AtomicReference<>();
        updater = updater(bytes -> {
            Path extracted = zip.extract(bytes);
            staging.set(extracted);
            return extracted;
        }, path -> {
            throw new IOException("staging bloqueado");
        });

        try {
            ArtifactUpdateException failure = captureUpdateFailure(updater::check);

            assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.LOCAL_STORAGE);
            assertThat(store.activeManifestOrNull().version()).isEqualTo("rtc-7");
            assertThat(preparedManifest("rtc-8").getProperty("zipSha256"))
                    .isEqualTo(sha256(body));
        } finally {
            if (staging.get() != null) delete(staging.get());
        }
    }

    @Test
    void addsCleanupFailureAsSuppressedWithoutMaskingThePrimaryFailure()
            throws IOException {
        installAndActivateRelease(7);
        configureValidRelease8();
        Path invalidCandidate = temp.resolve("invalid-extraction");
        Files.createDirectories(invalidCandidate);
        Files.writeString(invalidCandidate.resolve("nota.xsd"), "<not-a-schema/>");
        updater = updater(bytes -> invalidCandidate, path -> {
            throw new IOException("staging bloqueado");
        });

        ArtifactUpdateException failure = captureUpdateFailure(updater::check);

        assertThat(failure.kind())
                .isEqualTo(ArtifactFailureKind.UNSUPPORTED_SCHEMA_STRUCTURE);
        assertThat(failure.getSuppressed()).singleElement()
                .isInstanceOfSatisfying(ArtifactUpdateException.class, cleanup ->
                        assertThat(cleanup.kind()).isEqualTo(
                                ArtifactFailureKind.LOCAL_STORAGE));
        assertCurrentRelease7AndNoPrepared8();
    }

    private CuratedSchemaUpdater updater(Ed25519ManifestVerifier verifier, int zipMaxBytes) {
        SafeHttpsClient manifestHttps = new SafeHttpsClient(Set.of(MANIFEST_URI.getHost()),
                Duration.ofSeconds(2), 256 * 1024, transport);
        SafeHttpsClient zipHttps = new SafeHttpsClient(Set.of(ZIP_URI.getHost()),
                Duration.ofSeconds(2), zipMaxBytes, transport);
        return new CuratedSchemaUpdater(manifestHttps, zipHttps, parser, verifier, zip, store,
                CHANNEL, MANIFEST_URI, "1.2.3");
    }

    private CuratedSchemaUpdater updater(CuratedSchemaUpdater.SchemaExtractor extractor,
            CuratedSchemaUpdater.StagingCleaner cleaner) {
        SafeHttpsClient manifestHttps = new SafeHttpsClient(Set.of(MANIFEST_URI.getHost()),
                Duration.ofSeconds(2), 256 * 1024, transport);
        SafeHttpsClient zipHttps = new SafeHttpsClient(Set.of(ZIP_URI.getHost()),
                Duration.ofSeconds(2), SafeHttpsClient.SCHEMA_MAX_BYTES, transport);
        Ed25519ManifestVerifier verifier = new Ed25519ManifestVerifier(Map.of(KEY_ID,
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())));
        return new CuratedSchemaUpdater(manifestHttps, zipHttps, parser, verifier, store,
                CHANNEL, MANIFEST_URI, "1.2.3", extractor, cleaner);
    }

    private byte[] configureValidRelease8() throws IOException {
        byte[] body = validZip();
        transport.respond(MANIFEST_URI, signedManifest(release(8, sha256(body))));
        transport.respond(ZIP_URI, body);
        return body;
    }

    private void installAndActivateRelease(long sequence) throws IOException {
        byte[] body = validZip();
        Path extracted = zip.extract(body);
        try {
            store.prepare(extracted, release(sequence, sha256(body)), CHANNEL,
                    MANIFEST_URI.toString());
            store.activate("rtc-" + sequence);
        } finally {
            delete(extracted);
        }
        transport.clearRequests();
    }

    private CuratedSchemaChannelManifest.SignedRelease release(long sequence, String hash) {
        return release(sequence, hash, ArtifactId.NFE_SCHEMAS, "1.0.0");
    }

    private CuratedSchemaChannelManifest.SignedRelease release(long sequence, String hash,
            ArtifactId artifact, String minimumAppVersion) {
        return new CuratedSchemaChannelManifest.SignedRelease(
                artifact,
                sequence,
                "rtc-" + sequence,
                PUBLISHED_AT,
                minimumAppVersion,
                ZIP_URI,
                hash,
                List.of(new CuratedSchemaChannelManifest.SourceProvenance(
                        "Portal Nacional da NF-e",
                        URI.create("https://www.nfe.fazenda.gov.br/portal/"),
                        "NT 2025.002 v1.30")));
    }

    private byte[] signedManifest(CuratedSchemaChannelManifest.SignedRelease release) {
        CuratedSchemaChannelManifest unsigned =
                new CuratedSchemaChannelManifest(1, KEY_ID, release, "pending");
        String signature = sign(parser.canonicalSignedBytes(unsigned));
        return write(json.valueToTree(
                new CuratedSchemaChannelManifest(1, KEY_ID, release, signature)));
    }

    private String sign(byte[] payload) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(keyPair.getPrivate());
            signature.update(payload);
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (java.security.GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    private KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (java.security.GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    private byte[] validZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (String name : List.of("DFeTiposBasicos_v1.00.xsd",
                    "leiauteNFe_v4.00.xsd", "nfe_v4.00.xsd", "tiposBasico_v4.00.xsd",
                    "xmldsig-core-schema_v1.01.xsd")) {
                output.putNextEntry(new ZipEntry("NFe/" + name));
                output.write(Files.readAllBytes(
                        Path.of("src/main/resources/schemas/nfe/originais", name)));
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private java.util.Properties preparedManifest(String version) throws IOException {
        java.util.Properties properties = new java.util.Properties();
        try (var input = Files.newInputStream(versionDirectory(version)
                .resolve("manifest.properties"))) {
            properties.load(input);
        }
        return properties;
    }

    private void assertCurrentRelease7AndNoPrepared8() {
        assertThat(store.activeManifestOrNull().version()).isEqualTo("rtc-7");
        assertThat(versionDirectory("rtc-8")).doesNotExist();
    }

    private Path versionDirectory(String version) {
        return temp.resolve("artifacts/NFE_SCHEMAS/versions").resolve(version);
    }

    private void assertInvalidContent(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ArtifactUpdateException.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.INVALID_CONTENT);
                    assertThat(failure.retryable()).isFalse();
                });
    }

    private ArtifactUpdateException captureUpdateFailure(ThrowingOperation operation) {
        Throwable failure = catchThrowable(operation::run);
        assertThat(failure).isInstanceOf(ArtifactUpdateException.class);
        return (ArtifactUpdateException) failure;
    }

    private ObjectNode readObject(byte[] document) {
        try {
            return (ObjectNode) json.readTree(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] write(com.fasterxml.jackson.databind.JsonNode node) {
        try {
            return json.writeValueAsBytes(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void delete(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }

    private static final class RecordingTransport implements HttpsTransport {
        private final Map<URI, Response> responses = new LinkedHashMap<>();
        private final List<URI> requests = new ArrayList<>();

        void respond(URI uri, byte[] body) {
            responses.put(uri, new Response(200, uri, Map.of(), body));
        }

        void redirect(URI uri, URI target) {
            responses.put(uri, new Response(302, uri,
                    Map.of("Location", List.of(target.toString())), new byte[0]));
        }

        List<URI> requests() {
            return List.copyOf(requests);
        }

        void clearRequests() {
            requests.clear();
        }

        @Override
        public Response get(URI uri, Duration timeout) throws IOException {
            requests.add(uri);
            Response response = responses.get(uri);
            if (response == null) throw new IOException("Resposta não configurada para " + uri);
            return response;
        }
    }
}
