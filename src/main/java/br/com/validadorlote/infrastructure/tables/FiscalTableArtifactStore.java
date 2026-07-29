package br.com.validadorlote.infrastructure.tables;

import br.com.validadorlote.infrastructure.xml.ArtifactId;
import br.com.validadorlote.infrastructure.xml.ArtifactManifest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Properties;

/** Armazena a tabela destilada somente depois de validar formato e cobertura contra a base ativa. */
public final class FiscalTableArtifactStore {

    private static final ArtifactId ID = ArtifactId.FISCAL_TABLES;
    private static final String TABLE_FILE = "cst-cclasstrib.json";
    private static final String MANIFEST_FILE = "manifest.properties";
    private static final double MINIMUM_COVERAGE = 0.80;
    private final Path root;

    public FiscalTableArtifactStore(Path dataDirectory) {
        root = dataDirectory.resolve("artifacts").resolve(ID.name());
    }

    public static FiscalTableArtifactStore forCurrentUser() {
        return new FiscalTableArtifactStore(Path.of(System.getProperty("user.home"),
                ".validador-lote-rtc"));
    }

    /**
     * A base anterior só é trocada após parse completo e guarda de cobertura. O payload já é a
     * representação destilada — HTML e anexos não entram no diretório ativo.
     */
    public ArtifactManifest install(byte[] candidate, String version, String sourceUrl,
            Instant publishedAt) {
        try {
            Files.createDirectories(root.resolve("versions"));
            Path stage = Files.createTempDirectory(root, "staging-");
            try {
                Files.write(stage.resolve(TABLE_FILE), candidate, StandardOpenOption.CREATE_NEW);
                FiscalTables tables = FiscalTables.load(new ByteArrayInputStream(candidate));
                FiscalTables active = activeOrNull();
                ensureIdentityContinuity(tables, active == null ? FiscalTables.load() : active);
                ArtifactManifest manifest = new ArtifactManifest(ID, version, sourceUrl, publishedAt,
                        sha256(candidate), Instant.now(), Instant.now(), "INSTALLED");
                writeManifest(stage, manifest);
                Path target = root.resolve("versions").resolve(version);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException("Versão já instalada: " + version);
                }
                Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE);
                replaceCurrent(version);
                return manifest;
            } finally {
                if (Files.exists(stage, LinkOption.NOFOLLOW_LINKS)) deleteTree(stage);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível instalar a tabela fiscal", e);
        }
    }

    /** Tabela local apenas quando referência, manifesto, hash e formato permanecem íntegros. */
    public FiscalTables activeOrNull() {
        try {
            Path base = activePathOrNull();
            if (base == null) return null;
            try (var input = Files.newInputStream(base.resolve(TABLE_FILE))) {
                return FiscalTables.load(input);
            }
        } catch (RuntimeException | IOException ignored) {
            return null;
        }
    }

    /** Evita reinstalar a mesma tabela destilada em cada consulta periódica. */
    public boolean isActiveVersion(String version) {
        Path active = activePathOrNull();
        if (active == null) return false;
        try {
            return readManifest(active).version().equals(version);
        } catch (IOException e) {
            return false;
        }
    }

    /** Manifesto da tabela local íntegra, para auditoria de apresentação sem expor o payload. */
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
            if (!Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(current)) return null;
            String version = Files.readString(current, StandardCharsets.UTF_8).trim();
            if (!version.matches("[A-Za-z0-9._-]{1,100}")) return null;
            Path base = root.resolve("versions").resolve(version);
            if (!Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(base)
                    || !hasOnlyExpectedFiles(base)) return null;
            ArtifactManifest manifest = readManifest(base);
            if (manifest.artifact() != ID || !manifest.version().equals(version)
                    || !manifest.sha256().equals(sha256(Files.readAllBytes(base.resolve(TABLE_FILE))))) {
                return null;
            }
            return base;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void ensureIdentityContinuity(FiscalTables candidate, FiscalTables baseline) {
        if (!preservesAtLeast(candidate.cstCodes(), baseline.cstCodes())
                || !preservesAtLeast(candidate.classTribCodes(), baseline.classTribCodes())) {
            throw new IllegalStateException(
                    "A tabela candidata não preserva 80% das identidades da base ativa");
        }
    }

    private boolean preservesAtLeast(java.util.Set<String> candidate,
            java.util.Set<String> baseline) {
        long preserved = baseline.stream().filter(candidate::contains).count();
        return preserved >= Math.ceil(baseline.size() * MINIMUM_COVERAGE);
    }

    private boolean hasOnlyExpectedFiles(Path base) throws IOException {
        try (var files = Files.list(base)) {
            return files.allMatch(file -> !Files.isSymbolicLink(file)
                    && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    && (TABLE_FILE.equals(file.getFileName().toString())
                    || MANIFEST_FILE.equals(file.getFileName().toString())));
        }
    }

    private void replaceCurrent(String version) throws IOException {
        Path temporary = Files.createTempFile(root, "current-", ".tmp");
        Files.writeString(temporary, version + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(temporary, root.resolve("current"), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    static String sha256(byte[] payload) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void writeManifest(Path base, ArtifactManifest manifest) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("artifact", manifest.artifact().name());
        properties.setProperty("version", manifest.version());
        properties.setProperty("sourceUrl", manifest.sourceUrl());
        properties.setProperty("publishedAt", manifest.publishedAt().toString());
        properties.setProperty("sha256", manifest.sha256());
        properties.setProperty("lastCheckedAt", manifest.lastCheckedAt().toString());
        properties.setProperty("updatedAt", manifest.updatedAt().toString());
        properties.setProperty("result", manifest.result());
        try (var output = Files.newOutputStream(base.resolve(MANIFEST_FILE))) {
            properties.store(output, "Artefato fiscal externo auditável");
        }
    }

    private ArtifactManifest readManifest(Path base) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(base.resolve(MANIFEST_FILE))) {
            properties.load(input);
        }
        return new ArtifactManifest(ArtifactId.valueOf(properties.getProperty("artifact")),
                properties.getProperty("version"), properties.getProperty("sourceUrl"),
                Instant.parse(properties.getProperty("publishedAt")), properties.getProperty("sha256"),
                Instant.parse(properties.getProperty("lastCheckedAt")),
                Instant.parse(properties.getProperty("updatedAt")), properties.getProperty("result"));
    }

    private void deleteTree(Path path) throws IOException {
        try (var paths = Files.walk(path)) {
            for (Path file : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }
}
