package br.com.validadorlote.infrastructure.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaArtifactStoreTest {
    @TempDir Path temp;

    @Test
    void installsOnlyAfterTheCandidateCompilesAndSelectsItByVerifiedManifest() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        Path candidate = copyEmbedded("candidate");

        var manifest = store.install(candidate, "2026.07.29", "https://fonte.exemplo/schemas", Instant.EPOCH);

        assertThat(manifest.artifact()).isEqualTo(ArtifactId.NFE_SCHEMAS);
        assertThat(store.activeOrNull()).isEqualTo(temp.resolve("artifacts/NFE_SCHEMAS/versions/2026.07.29"));
        assertThat(new SchemaValidatorEngine(new XsdErrorTranslator(), store.activeOrNull())).isNotNull();
    }

    @Test
    void brokenCandidateDoesNotReplaceTheLastActiveBase() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        store.install(copyEmbedded("good"), "good", "https://fonte.exemplo/good", Instant.EPOCH);
        Path active = store.activeOrNull();
        Path broken = copyEmbedded("broken");
        Files.delete(broken.resolve("originais/leiauteNFe_v4.00.xsd"));

        assertThatThrownBy(() -> store.install(broken, "broken", "https://fonte.exemplo/broken", Instant.EPOCH))
                .isInstanceOf(RuntimeException.class);

        assertThat(store.activeOrNull()).isEqualTo(active);
    }

    @Test
    void corruptionOrSymlinkMakesLocalBaseIneligible() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        store.install(copyEmbedded("good"), "good", "https://fonte.exemplo/good", Instant.EPOCH);
        Path active = store.activeOrNull();
        Files.writeString(active.resolve("nota.xsd"), "corrupted");

        assertThat(store.activeOrNull()).isNull();
    }

    @Test
    void forgedManifestHashStillFallsBackWhenTheAlteredTreeNoLongerCompiles() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        store.install(copyEmbedded("good"), "good", "https://fonte.exemplo/good", Instant.EPOCH);
        Path active = store.activeOrNull();
        Files.writeString(active.resolve("nota.xsd"), "<not-a-schema/>");
        rewriteHash(active);

        assertThat(store.activeOrNull()).isNull();
        var fallback = br.com.validadorlote.App.schemaEngine(new XsdErrorTranslator(), store);
        Path xml = Path.of("src/test/resources/fixtures/nfe-minima-invalida.xml");
        assertThat(fallback.validate(xml, new XmlMetadataParser().parse(xml))).isNotEmpty();
    }

    @Test
    void rejectsSymlinkInCandidateBeforeItCanBecomeCurrent() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        Path candidate = copyEmbedded("hostile");
        Files.delete(candidate.resolve("originais/tiposBasico_v4.00.xsd"));
        try {
            Files.createSymbolicLink(candidate.resolve("originais/tiposBasico_v4.00.xsd"),
                    Path.of("outside.xsd"));
        } catch (UnsupportedOperationException unsupported) {
            Assumptions.abort("Sistema de arquivos não permite criar links simbólicos neste teste.");
        } catch (java.nio.file.FileSystemException failure) {
            if (!isMissingSymlinkPrivilege(failure)) throw failure;
            Assumptions.abort("A conta atual não tem privilégio para criar links simbólicos.");
        }

        assertThatThrownBy(() -> store.install(candidate, "hostile", "https://fonte.exemplo/hostile", Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.activeOrNull()).isNull();
    }

    private boolean isMissingSymlinkPrivilege(java.nio.file.FileSystemException failure) {
        String reason = failure.getReason() == null ? "" : failure.getReason()
                .toLowerCase(Locale.ROOT);
        return reason.contains("privil") || reason.contains("operation not permitted")
                || reason.contains("operation not supported");
    }

    private Path copyEmbedded(String name) throws IOException {
        Path target = temp.resolve(name);
        try (var files = Files.walk(Path.of("src/main/resources/schemas/nfe"))) {
            for (Path source : files.toList()) {
                Path out = target.resolve(Path.of("src/main/resources/schemas/nfe").relativize(source));
                if (Files.isDirectory(source)) Files.createDirectories(out); else Files.copy(source, out);
            }
        }
        return target;
    }

    private void rewriteHash(Path base) throws IOException {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(base.resolve("manifest.properties"))) { properties.load(in); }
        properties.setProperty("sha256", SchemaArtifactStore.treeHash(base));
        try (var out = Files.newOutputStream(base.resolve("manifest.properties"))) { properties.store(out, "forged"); }
    }
}
