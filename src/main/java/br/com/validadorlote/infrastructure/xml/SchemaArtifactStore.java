package br.com.validadorlote.infrastructure.xml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Properties;

/** Instala árvores XSD candidatas sem substituir a base ativa antes de ela compilar. */
public final class SchemaArtifactStore {
    private static final ArtifactId ID = ArtifactId.NFE_SCHEMAS;
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
        try {
            Files.createDirectories(root.resolve("versions"));
            Path stage = Files.createTempDirectory(root, "staging-");
            try {
                copyTree(candidate, stage);
                String hash = treeHash(stage);
                new SchemaValidatorEngine(new XsdErrorTranslator(), stage); // gate antes de publicar
                ArtifactManifest manifest = new ArtifactManifest(ID, version, sourceUrl, publishedAt, hash,
                        Instant.now(), Instant.now(), "INSTALLED");
                writeManifest(stage, manifest, discoveryUrl);
                Path target = root.resolve("versions").resolve(version);
                if (Files.exists(target)) throw new IllegalArgumentException("Versão já instalada: " + version);
                Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE);
                replaceCurrent(version);
                return manifest;
            } finally { if (Files.exists(stage)) deleteTree(stage); }
        } catch (IOException e) { throw new UncheckedIOException("Não foi possível instalar schemas", e); }
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
                if (file.getFileName().toString().equals("manifest.properties")) continue;
                digest.update(root.relativize(file).toString().replace('\\','/').getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
                digest.update(Files.readAllBytes(file)); digest.update((byte) 0);
            }} return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void writeManifest(Path base, ArtifactManifest m, String discoveryUrl) throws IOException { Properties p = new Properties();
        p.setProperty("artifact",m.artifact().name()); p.setProperty("version",m.version()); p.setProperty("discoveryUrl",discoveryUrl); p.setProperty("sourceUrl",m.sourceUrl()); p.setProperty("publishedAt",m.publishedAt().toString()); p.setProperty("sha256",m.sha256()); p.setProperty("lastCheckedAt",m.lastCheckedAt().toString()); p.setProperty("updatedAt",m.updatedAt().toString()); p.setProperty("result",m.result());
        try (var out=Files.newOutputStream(base.resolve("manifest.properties"))) { p.store(out,"Artefato externo auditável"); }}
    private static ArtifactManifest readManifest(Path base) throws IOException { Properties p=new Properties(); try(var in=Files.newInputStream(base.resolve("manifest.properties"))){p.load(in);} return new ArtifactManifest(ArtifactId.valueOf(p.getProperty("artifact")),p.getProperty("version"),p.getProperty("sourceUrl"),Instant.parse(p.getProperty("publishedAt")),p.getProperty("sha256"),Instant.parse(p.getProperty("lastCheckedAt")),Instant.parse(p.getProperty("updatedAt")),p.getProperty("result")); }
    private static void deleteTree(Path path) throws IOException { try(var paths=Files.walk(path)){ for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
}
