package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.infrastructure.update.ArtifactUpdateException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/** Instala árvores XSD candidatas sem substituir a base ativa antes de ela compilar. */
public final class SchemaArtifactStore {
    private static final ArtifactId ID = ArtifactId.NFE_SCHEMAS;
    private static final String MANIFEST_FILE = "manifest.properties";
    private final Path root;

    public SchemaArtifactStore(Path dataDirectory) { this.root = dataDirectory.resolve("artifacts").resolve(ID.name()); }

    /** Dados mutáveis nunca ficam ao lado do JAR/instalador. */
    public static SchemaArtifactStore forCurrentUser() {
        return new SchemaArtifactStore(Path.of(System.getProperty("user.home"), ".validador-lote-rtc"));
    }

    public ArtifactManifest install(Path candidate, String version, String sourceUrl, Instant publishedAt) {
        return install(candidate, version, sourceUrl, sourceUrl, publishedAt);
    }

    /** Registra separadamente a página oficial que declarou a versão e o ZIP que a transportou. */
    public ArtifactManifest install(Path candidate, String version, String discoveryUrl,
            String sourceUrl, Instant publishedAt) {
        ArtifactManifest prepared = prepare(candidate, version, discoveryUrl, sourceUrl, publishedAt);
        return activate(prepared.version());
    }

    public ArtifactManifest prepare(Path candidate, String version, String sourceUrl,
            Instant publishedAt) {
        return prepare(candidate, version, sourceUrl, sourceUrl, publishedAt);
    }

    /** Valida e guarda uma candidata, sem alterar a referência da base ativa. */
    public ArtifactManifest prepare(Path candidate, String version, String discoveryUrl,
            String sourceUrl, Instant publishedAt) {
        return prepare(candidate, version, discoveryUrl, sourceUrl, publishedAt, 0,
                "", "", "", "", false);
    }

    /** Valida e guarda uma release curada, preservando sequência e proveniência assinadas. */
    public ArtifactManifest prepare(Path candidate, CuratedSchemaChannelManifest.SignedRelease release,
            String channelId, String discoveryUrl) {
        Objects.requireNonNull(release);
        if (release.artifact() != ID || release.releaseSequence() <= 0
                || channelId == null || !channelId.matches("[A-Za-z0-9._-]{1,100}")
                || release.version() == null || release.publishedAt() == null
                || release.minimumAppVersion() == null || release.zipUrl() == null
                || release.zipSha256() == null || !release.zipSha256().matches("[0-9a-f]{64}")
                || release.sourceProvenance() == null
                || release.sourceProvenance().isEmpty()) {
            throw ArtifactUpdateException.invalidContent("Release curada de schemas inválida");
        }
        String provenance = release.sourceProvenance().stream()
                .map(SchemaArtifactStore::formatProvenance)
                .collect(Collectors.joining("\n"));
        String signedReleaseSha256 = signedReleaseHash(release);
        return prepare(candidate, release.version(), discoveryUrl, release.zipUrl().toString(),
                release.publishedAt(), release.releaseSequence(), channelId, provenance,
                release.zipSha256(), signedReleaseSha256, true);
    }

    private synchronized ArtifactManifest prepare(Path candidate, String version, String discoveryUrl,
            String sourceUrl, Instant publishedAt, long releaseSequence, String channelId,
            String provenance, String zipSha256, String signedReleaseSha256, boolean curated) {
        try {
            prepareRoot();
            validateVersion(version);
            rejectPreparationRollback(version, channelId, releaseSequence, zipSha256,
                    signedReleaseSha256);
            Path stage = Files.createTempDirectory(root, "staging-");
            try {
                copyTree(candidate, stage);
                String hash = treeHash(stage);
                validateSchemaTree(stage, curated);
                ArtifactManifest manifest = new ArtifactManifest(ID, version, sourceUrl, publishedAt, hash,
                        Instant.now(), Instant.now(), "PREPARED", releaseSequence, channelId,
                        provenance, zipSha256, signedReleaseSha256);
                writeManifest(stage, manifest, discoveryUrl);
                Path target = versionDirectory(version);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    ArtifactManifest existing = verifiedPreparedManifest(target, version);
                    if (samePreparedArtifact(existing, manifest)
                            && discoveryUrl.equals(readDiscoveryUrl(target))) return existing;
                    throw new IllegalArgumentException("Versão preparada diverge: " + version);
                }
                Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE);
                return manifest;
            } finally { if (Files.exists(stage)) deleteTree(stage); }
        } catch (IOException e) { throw new UncheckedIOException("Não foi possível instalar schemas", e); }
    }

    /** Revalida uma versão preparada e só então a publica como ativa. */
    public synchronized ArtifactManifest activate(String version) {
        Path target = versionDirectory(version);
        try {
            prepareRoot();
            ArtifactManifest manifest;
            try {
                manifest = verifiedPreparedManifest(target, version);
            } catch (IOException | RuntimeException failure) {
                removeFailedPreparedVersion(target, version, failure);
                throw failure;
            }
            rejectActivationRollback(manifest);
            replaceCurrent(version);
            return manifest;
        } catch (IOException | RuntimeException e) {
            if (e instanceof UncheckedIOException unchecked) throw unchecked;
            throw new IllegalStateException("Não foi possível ativar schemas preparados", e);
        }
    }

    /** Base local somente se referência, manifesto e conteúdo continuarem íntegros. */
    public Path activeOrNull() {
        Path active = activePathOrNull();
        if (active == null) return null;
        try {
            new SchemaValidatorEngine(new XsdErrorTranslator(), active);
            return active;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Permite evitar download/reinstalação quando a fonte declara a mesma versão já ativa. */
    public boolean isActiveVersion(String version) {
        Path active = activePathOrNull();
        if (active == null) return false;
        try {
            return readManifest(active).version().equals(version);
        } catch (IOException e) {
            return false;
        }
    }

    /** Manifesto da base local íntegra, para auditoria de apresentação sem expor o payload. */
    public ArtifactManifest activeManifestOrNull() {
        Path active = activePathOrNull();
        if (active == null) return null;
        try {
            return readManifest(active);
        } catch (IOException e) {
            return null;
        }
    }

    private Path activePathOrNull() {
        try {
            Path current = root.resolve("current");
            if (!Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(current)) return null;
            String version = Files.readString(current, StandardCharsets.UTF_8).trim();
            if (!version.matches("[A-Za-z0-9._-]{1,100}")) return null;
            Path base = root.resolve("versions").resolve(version);
            if (Files.isSymbolicLink(base) || !Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) return null;
            ArtifactManifest manifest = readManifest(base);
            if (manifest.artifact() != ID || !manifest.version().equals(version)
                    || !manifest.sha256().equals(treeHash(base))) return null;
            return base;
        } catch (Exception ignored) { return null; }
    }

    /** Abre a base local uma única vez para o bootstrap; nulo preserva o fallback embarcado. */
    public SchemaValidatorEngine activeEngineOrNull(XsdErrorTranslator translator) {
        Path active = activePathOrNull();
        if (active == null) return null;
        try {
            return new SchemaValidatorEngine(translator, active);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void replaceCurrent(String version) throws IOException {
        Path temp = Files.createTempFile(root, "current-", ".tmp");
        Files.writeString(temp, version + "\n", StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(temp, root.resolve("current"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void prepareRoot() throws IOException {
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Diretório de schemas inválido");
        }
        Path versions = root.resolve("versions");
        Files.createDirectories(versions);
        if (Files.isSymbolicLink(versions) || !Files.isDirectory(versions, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Diretório de versões de schemas inválido");
        }
    }

    private Path versionDirectory(String version) {
        validateVersion(version);
        Path versions = root.resolve("versions").normalize();
        Path directory = versions.resolve(version).normalize();
        if (!directory.startsWith(versions)) throw new IllegalArgumentException("Versão inválida: " + version);
        return directory;
    }

    private static void validateVersion(String version) {
        if (version == null || !version.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalArgumentException("Versão inválida: " + version);
        }
    }

    private ArtifactManifest verifiedPreparedManifest(Path base, String version) throws IOException {
        if (Files.isSymbolicLink(base) || !Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Versão preparada de schemas inválida: " + version);
        }
        ArtifactManifest manifest = readManifest(base);
        if (manifest.artifact() != ID || !manifest.version().equals(version)
                || !manifest.sha256().equals(treeHash(base))) {
            throw new IllegalStateException("Versão preparada de schemas perdeu integridade: " + version);
        }
        validateSchemaTree(base, manifest.releaseSequence() > 0);
        return manifest;
    }

    private static void validateSchemaTree(Path base, boolean curated) {
        Path entrypoint = base.resolve("nota.xsd");
        RuntimeException failure = null;
        if (Files.isSymbolicLink(entrypoint)
                || !Files.isRegularFile(entrypoint, LinkOption.NOFOLLOW_LINKS)) {
            failure = new IllegalStateException("Entrypoint nota.xsd ausente");
        } else {
            try {
                new SchemaValidatorEngine(new XsdErrorTranslator(), base);
                return;
            } catch (RuntimeException compilationFailure) {
                failure = compilationFailure;
            }
        }
        if (curated) {
            throw ArtifactUpdateException.unsupportedSchemaStructure(
                    "A estrutura dos schemas mais recentes não é suportada por esta versão do aplicativo",
                    failure);
        }
        throw failure;
    }

    private static boolean samePreparedArtifact(ArtifactManifest left, ArtifactManifest right) {
        return left.artifact() == right.artifact() && left.version().equals(right.version())
                && left.sourceUrl().equals(right.sourceUrl()) && left.publishedAt().equals(right.publishedAt())
                && left.sha256().equals(right.sha256())
                && left.releaseSequence() == right.releaseSequence()
                && left.channelId().equals(right.channelId())
                && left.provenance().equals(right.provenance())
                && left.zipSha256().equals(right.zipSha256())
                && left.signedReleaseSha256().equals(right.signedReleaseSha256());
    }

    private void rejectPreparationRollback(String version, String channelId, long releaseSequence,
            String zipSha256, String signedReleaseSha256) throws IOException {
        if (releaseSequence == 0) return;
        ArtifactManifest active = activeManifestOrNull();
        if (active != null && channelId.equals(active.channelId())
                && releaseSequence <= active.releaseSequence()) {
            throw ArtifactUpdateException.invalidContent(
                    "A sequência da release de schemas não é superior à base ativa");
        }
        Path versions = root.resolve("versions");
        try (var entries = Files.list(versions)) {
            for (Path entry : entries.toList()) {
                ArtifactManifest prepared = preparedManifestOrNull(entry);
                if (prepared == null || !channelId.equals(prepared.channelId())) continue;
                if (prepared.releaseSequence() > releaseSequence
                        || (prepared.releaseSequence() == releaseSequence
                                && !sameSignedRelease(prepared, version, zipSha256,
                                        signedReleaseSha256))) {
                    throw ArtifactUpdateException.invalidContent(
                            "A sequência da release de schemas conflita com uma versão preparada");
                }
            }
        }
    }

    private ArtifactManifest preparedManifestOrNull(Path directory) {
        try {
            if (Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            ArtifactManifest manifest = readManifest(directory);
            return manifest.artifact() == ID
                    && manifest.version().equals(directory.getFileName().toString())
                    ? manifest : null;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean sameSignedRelease(ArtifactManifest prepared, String version,
            String zipSha256, String signedReleaseSha256) {
        return prepared.version().equals(version)
                && prepared.zipSha256().equals(zipSha256)
                && prepared.signedReleaseSha256().equals(signedReleaseSha256);
    }

    private void rejectActivationRollback(ArtifactManifest candidate) {
        ArtifactManifest active = activeManifestOrNull();
        if (active == null || active.version().equals(candidate.version())
                || candidate.releaseSequence() == 0
                || !candidate.channelId().equals(active.channelId())) {
            return;
        }
        if (candidate.releaseSequence() <= active.releaseSequence()) {
            throw ArtifactUpdateException.invalidContent(
                    "A sequência da release preparada não é superior à base ativa");
        }
    }

    private static String formatProvenance(CuratedSchemaChannelManifest.SourceProvenance source) {
        if (source == null || source.name() == null || source.name().isBlank()
                || source.url() == null || source.revision() == null || source.revision().isBlank()) {
            throw ArtifactUpdateException.invalidContent("Proveniência da release de schemas inválida");
        }
        return source.name() + " | " + source.url() + " | " + source.revision();
    }

    private static String signedReleaseHash(CuratedSchemaChannelManifest.SignedRelease release) {
        byte[] signedBytes = new CuratedSchemaManifestParser().canonicalSignedBytes(
                new CuratedSchemaChannelManifest(1, "identity", release, "identity"));
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(signedBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void removeFailedPreparedVersion(Path target, String version, Exception failure) {
        if (isCurrentReference(version) || !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            deleteTree(target);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private boolean isCurrentReference(String version) {
        Path current = root.resolve("current");
        try {
            return Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(current)
                    && version.equals(Files.readString(current, StandardCharsets.UTF_8).trim());
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source)) throw new IllegalArgumentException("Symlink não permitido: " + source);
        Path real = source.toRealPath();
        try (var paths = Files.walk(real)) {
            for (Path file : paths.sorted().toList()) {
                if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("Symlink não permitido: " + file);
                Path relative = real.relativize(file); Path out = target.resolve(relative).normalize();
                if (!out.startsWith(target)) throw new IllegalArgumentException("Caminho fora da base: " + file);
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(out);
                else if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && file.getFileName().toString().endsWith(".xsd")) Files.copy(file, out);
                else throw new IllegalArgumentException("Arquivo não permitido: " + file);
            }
        }
    }
    static String treeHash(Path root) throws IOException {
        try { MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var paths = Files.walk(root)) { for (Path file : paths.sorted().toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Symlink não permitido: " + file);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                if (file.getFileName().toString().equals(MANIFEST_FILE)) continue;
                digest.update(root.relativize(file).toString().replace('\\','/').getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
                digest.update(Files.readAllBytes(file)); digest.update((byte) 0);
            }} return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void writeManifest(Path base, ArtifactManifest m, String discoveryUrl) throws IOException { Properties p = new Properties();
        p.setProperty("artifact",m.artifact().name()); p.setProperty("version",m.version()); p.setProperty("discoveryUrl",discoveryUrl); p.setProperty("sourceUrl",m.sourceUrl()); p.setProperty("publishedAt",m.publishedAt().toString()); p.setProperty("sha256",m.sha256()); p.setProperty("lastCheckedAt",m.lastCheckedAt().toString()); p.setProperty("updatedAt",m.updatedAt().toString()); p.setProperty("result",m.result()); p.setProperty("releaseSequence",Long.toString(m.releaseSequence())); p.setProperty("channelId",m.channelId()); p.setProperty("provenance",m.provenance()); p.setProperty("zipSha256",m.zipSha256()); p.setProperty("signedReleaseSha256",m.signedReleaseSha256());
        try (var out=Files.newOutputStream(base.resolve(MANIFEST_FILE))) { p.store(out,"Artefato externo auditável"); }}
    private static ArtifactManifest readManifest(Path base) throws IOException { Path file = base.resolve(MANIFEST_FILE); if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) throw new IOException("Manifesto inválido"); Properties p=new Properties(); try(var in=Files.newInputStream(file)){p.load(in);} String sequence=p.getProperty("releaseSequence"); return new ArtifactManifest(ArtifactId.valueOf(p.getProperty("artifact")),p.getProperty("version"),p.getProperty("sourceUrl"),Instant.parse(p.getProperty("publishedAt")),p.getProperty("sha256"),Instant.parse(p.getProperty("lastCheckedAt")),Instant.parse(p.getProperty("updatedAt")),p.getProperty("result"),sequence == null ? 0 : Long.parseLong(sequence),p.getProperty("channelId",""),p.getProperty("provenance",""),p.getProperty("zipSha256",""),p.getProperty("signedReleaseSha256","")); }
    private static String readDiscoveryUrl(Path base) throws IOException { Path file = base.resolve(MANIFEST_FILE); if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) throw new IOException("Manifesto inválido"); Properties p=new Properties(); try(var in=Files.newInputStream(file)){p.load(in);} return p.getProperty("discoveryUrl"); }
    private static void deleteTree(Path path) throws IOException { try(var paths=Files.walk(path)){ for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
}
