package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.infrastructure.update.ArtifactFailureKind;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaArtifactStoreTest {
    private static final String CHANNEL = "curated-schemas-stable-v1";
    private static final URI MANIFEST_URI =
            URI.create("https://bases.validadorlote.example/schemas/stable.json");
    private static final URI ZIP_URI =
            URI.create("https://bases.validadorlote.example/schemas/rtc.zip");
    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

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
    void prepareKeepsCurrentAndActivatePublishesThePreparedSchemas() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        Path candidate = copyEmbedded("candidate");
        Instant publishedAt = Instant.parse("2026-07-30T12:00:00Z");

        ArtifactManifest prepared = store.prepare(candidate, "candidate-v2",
                "https://dfe-portal.svrs.rs.gov.br/NFe/Documentos",
                "https://dfe-portal.svrs.rs.gov.br/NFE/DownloadArquivoEstatico?Arquivo=x.zip",
                publishedAt);

        assertThat(store.activeManifestOrNull()).isNull();
        assertThat(prepared.version()).isEqualTo("candidate-v2");

        store.activate("candidate-v2");

        assertThat(store.activeManifestOrNull().version()).isEqualTo("candidate-v2");
    }

    @Test
    void activateRejectsPreparedSchemasChangedAfterValidation() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        Path candidate = copyEmbedded("candidate");
        store.prepare(candidate, "candidate-v2", "https://dfe-portal.svrs.rs.gov.br/x",
                Instant.parse("2026-07-30T12:00:00Z"));
        Files.writeString(temp.resolve("artifacts/NFE_SCHEMAS/versions/candidate-v2/nota.xsd"),
                "<corrompido/>");

        assertThatThrownBy(() -> store.activate("candidate-v2"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.activeManifestOrNull()).isNull();
        assertThat(temp.resolve("artifacts/NFE_SCHEMAS/current")).doesNotExist();
    }

    @Test
    void persistsCuratedSequenceChannelAndProvenanceForAudit() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);

        ArtifactManifest prepared = store.prepare(copyEmbedded("candidate"), release(7),
                CHANNEL, MANIFEST_URI.toString());
        store.activate(prepared.version());

        ArtifactManifest active = store.activeManifestOrNull();
        assertThat(active.releaseSequence()).isEqualTo(7);
        assertThat(active.channelId()).isEqualTo(CHANNEL);
        assertThat(active.provenance())
                .contains("Portal Nacional da NF-e")
                .contains("https://www.nfe.fazenda.gov.br/portal/")
                .contains("NT 2025.002 v1.30");
    }

    @Test
    void preservesCurrentWhenPreparedReleaseHasNoHigherSequence() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        store.prepare(copyEmbedded("release-7"), release(7), CHANNEL, MANIFEST_URI.toString());
        store.activate("rtc-7");
        Path previous = store.activeOrNull();

        assertThatThrownBy(() -> store.prepare(copyEmbedded("replayed-release"),
                release(7, "rtc-7-repacked", "b".repeat(64)),
                CHANNEL, MANIFEST_URI.toString()))
                .isInstanceOf(ArtifactUpdateException.class)
                .hasMessageContaining("sequência");

        assertThat(store.activeOrNull()).isEqualTo(previous);
        assertThat(store.activeManifestOrNull().version()).isEqualTo("rtc-7");
        assertThat(versionDirectory("rtc-7-repacked")).doesNotExist();
    }

    @Test
    void preservesCurrentWhenPreparedReleaseHasLowerSequence() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        store.prepare(copyEmbedded("release-7"), release(7), CHANNEL, MANIFEST_URI.toString());
        store.activate("rtc-7");
        Path previous = store.activeOrNull();

        assertThatThrownBy(() -> store.prepare(copyEmbedded("release-6"),
                release(6, "rtc-6", "c".repeat(64)), CHANNEL, MANIFEST_URI.toString()))
                .isInstanceOf(ArtifactUpdateException.class)
                .hasMessageContaining("sequência");

        assertThat(store.activeOrNull()).isEqualTo(previous);
        assertThat(store.activeManifestOrNull().releaseSequence()).isEqualTo(7);
        assertThat(versionDirectory("rtc-6")).doesNotExist();
    }

    @Test
    void activateCannotRollbackToAnOlderReleasePreparedBeforeTheCurrentOne() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        store.prepare(copyEmbedded("release-7"), release(7), CHANNEL, MANIFEST_URI.toString());
        store.activate("rtc-7");
        store.prepare(copyEmbedded("release-8"), release(8), CHANNEL, MANIFEST_URI.toString());
        store.prepare(copyEmbedded("release-9"), release(9), CHANNEL, MANIFEST_URI.toString());
        store.activate("rtc-9");
        Path current = store.activeOrNull();

        assertThat(store.activate("rtc-9").version()).isEqualTo("rtc-9");
        assertThatThrownBy(() -> store.activate("rtc-8"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ativar schemas preparados");

        assertThat(store.activeOrNull()).isEqualTo(current);
        assertThat(store.activeManifestOrNull().releaseSequence()).isEqualTo(9);
    }

    @Test
    void activateCannotReplaceCurrentWithAnotherReleaseOfTheSameChannelAndSequence()
            throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        String otherChannel = "curated-schemas-candidate-v1";
        store.prepare(copyEmbedded("release-8-a"),
                release(8, "rtc-8-a", "a".repeat(64)), CHANNEL, MANIFEST_URI.toString());
        store.prepare(copyEmbedded("release-8-b"),
                release(8, "rtc-8-b", "b".repeat(64)), otherChannel, MANIFEST_URI.toString());
        rewriteManifestProperty(versionDirectory("rtc-8-b"), "channelId", CHANNEL);
        store.activate("rtc-8-a");
        Path current = store.activeOrNull();

        assertThatThrownBy(() -> store.activate("rtc-8-b"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.activeOrNull()).isEqualTo(current);
        assertThat(store.activeManifestOrNull().version()).isEqualTo("rtc-8-a");
    }

    @Test
    void rejectsAmbiguousPreparedVersionsWithTheSameChannelAndSequence() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        store.prepare(copyEmbedded("release-8-a"),
                release(8, "rtc-8-a", "a".repeat(64)), CHANNEL, MANIFEST_URI.toString());

        assertThatThrownBy(() -> store.prepare(copyEmbedded("release-8-b"),
                release(8, "rtc-8-b", "b".repeat(64)), CHANNEL, MANIFEST_URI.toString()))
                .isInstanceOf(ArtifactUpdateException.class)
                .hasMessageContaining("sequência");

        assertThat(versionDirectory("rtc-8-a")).isDirectory();
        assertThat(versionDirectory("rtc-8-b")).doesNotExist();
    }

    @Test
    void rejectsReleaseOlderThanAnotherPreparedReleaseFromTheSameChannel() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        store.prepare(copyEmbedded("release-9"), release(9), CHANNEL, MANIFEST_URI.toString());

        assertThatThrownBy(() -> store.prepare(copyEmbedded("release-8"), release(8),
                CHANNEL, MANIFEST_URI.toString()))
                .isInstanceOf(ArtifactUpdateException.class)
                .hasMessageContaining("sequência");

        assertThat(versionDirectory("rtc-9")).isDirectory();
        assertThat(versionDirectory("rtc-8")).doesNotExist();
        assertThat(store.activeManifestOrNull()).isNull();
    }

    @Test
    void signedZipHashParticipatesInPreparedReleaseIdempotency() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        ArtifactManifest first = store.prepare(copyEmbedded("release-8"),
                release(8, "rtc-8", "a".repeat(64)), CHANNEL, MANIFEST_URI.toString());

        ArtifactManifest repeated = store.prepare(copyEmbedded("release-8-repeat"),
                release(8, "rtc-8", "a".repeat(64)), CHANNEL, MANIFEST_URI.toString());
        assertThat(repeated).isEqualTo(first);

        assertThatThrownBy(() -> store.prepare(copyEmbedded("release-8-repacked"),
                release(8, "rtc-8", "b".repeat(64)), CHANNEL, MANIFEST_URI.toString()))
                .isInstanceOf(ArtifactUpdateException.class);

        Properties persisted = readManifestProperties(versionDirectory("rtc-8"));
        assertThat(persisted.getProperty("zipSha256")).isEqualTo("a".repeat(64));
        assertThat(persisted.getProperty("signedReleaseSha256")).matches("[0-9a-f]{64}");
    }

    @Test
    void lowerSequenceFromAnotherChannelCanBePrepared() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        store.prepare(copyEmbedded("stable-7"), release(7), CHANNEL, MANIFEST_URI.toString());
        store.activate("rtc-7");

        ArtifactManifest prepared = store.prepare(copyEmbedded("candidate-1"),
                release(1, "candidate-1", "d".repeat(64)), "curated-schemas-candidate-v1",
                MANIFEST_URI.toString());

        assertThat(prepared.releaseSequence()).isEqualTo(1);
        assertThat(prepared.channelId()).isEqualTo("curated-schemas-candidate-v1");
        assertThat(store.activeManifestOrNull().version()).isEqualTo("rtc-7");
    }

    @Test
    void rejectsClosureWithoutTheRealNotaEntrypointWithoutChangingCurrent() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        store.install(copyEmbedded("legacy"), "legacy", "https://fonte.exemplo/schemas", NOW);
        Path previous = store.activeOrNull();
        Path candidate = copyEmbedded("without-nota");
        Files.delete(candidate.resolve("nota.xsd"));

        assertThatThrownBy(() -> store.prepare(candidate, release(8), CHANNEL,
                MANIFEST_URI.toString()))
                .isInstanceOf(ArtifactUpdateException.class)
                .extracting("kind")
                .isEqualTo(ArtifactFailureKind.UNSUPPORTED_SCHEMA_STRUCTURE);

        assertThat(store.activeOrNull()).isEqualTo(previous);
        assertThat(store.activeManifestOrNull().version()).isEqualTo("legacy");
        assertThat(versionDirectory("rtc-8")).doesNotExist();
        assertThat(stagingDirectories()).isEmpty();
    }

    @Test
    void corruptPreparedClosureCannotReplaceCurrentAndIsRemoved() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        store.install(copyEmbedded("legacy"), "legacy", "https://fonte.exemplo/schemas", NOW);
        Path previous = store.activeOrNull();
        store.prepare(copyEmbedded("release-8"), release(8), CHANNEL, MANIFEST_URI.toString());
        Path prepared = versionDirectory("rtc-8");
        Files.writeString(prepared.resolve("nota.xsd"), "<corrompido/>");

        assertThatThrownBy(() -> store.activate("rtc-8"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.activeOrNull()).isEqualTo(previous);
        assertThat(store.activeManifestOrNull().version()).isEqualTo("legacy");
        assertThat(prepared).doesNotExist();
    }

    @Test
    void permitsFirstCuratedReleaseOverLegacyManifestWithoutCuratedFields() throws IOException {
        SchemaArtifactStore store = new SchemaArtifactStore(temp);
        store.install(copyEmbedded("legacy"), "legacy", "https://fonte.exemplo/schemas", NOW);
        Path legacy = store.activeOrNull();
        removeCuratedProperties(legacy);

        ArtifactManifest legacyManifest = store.activeManifestOrNull();
        assertThat(legacyManifest.releaseSequence()).isZero();
        assertThat(legacyManifest.channelId()).isEmpty();
        assertThat(legacyManifest.provenance()).isEmpty();

        ArtifactManifest prepared = store.prepare(copyEmbedded("release-1"), release(1),
                CHANNEL, MANIFEST_URI.toString());

        assertThat(prepared.version()).isEqualTo("rtc-1");
        assertThat(store.activeManifestOrNull().version()).isEqualTo("legacy");
        assertThat(versionDirectory("rtc-1")).isDirectory();
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

    private CuratedSchemaChannelManifest.SignedRelease release(long sequence) {
        return release(sequence, "rtc-" + sequence, "a".repeat(64));
    }

    private CuratedSchemaChannelManifest.SignedRelease release(long sequence, String version,
            String zipSha256) {
        return new CuratedSchemaChannelManifest.SignedRelease(
                ArtifactId.NFE_SCHEMAS,
                sequence,
                version,
                NOW,
                "0.1.0",
                ZIP_URI,
                zipSha256,
                List.of(new CuratedSchemaChannelManifest.SourceProvenance(
                        "Portal Nacional da NF-e",
                        URI.create("https://www.nfe.fazenda.gov.br/portal/"),
                        "NT 2025.002 v1.30")));
    }

    private Path versionDirectory(String version) {
        return temp.resolve("artifacts/NFE_SCHEMAS/versions").resolve(version);
    }

    private void removeCuratedProperties(Path base) throws IOException {
        Path file = base.resolve("manifest.properties");
        Properties properties = new Properties();
        try (var in = Files.newInputStream(file)) {
            properties.load(in);
        }
        properties.remove("releaseSequence");
        properties.remove("channelId");
        properties.remove("provenance");
        properties.remove("zipSha256");
        properties.remove("signedReleaseSha256");
        try (var out = Files.newOutputStream(file)) {
            properties.store(out, "legacy");
        }
    }

    private void rewriteManifestProperty(Path base, String key, String value) throws IOException {
        Path file = base.resolve("manifest.properties");
        Properties properties = readManifestProperties(base);
        properties.setProperty(key, value);
        try (var out = Files.newOutputStream(file)) {
            properties.store(out, "test setup");
        }
    }

    private Properties readManifestProperties(Path base) throws IOException {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(base.resolve("manifest.properties"))) {
            properties.load(in);
        }
        return properties;
    }

    private List<Path> stagingDirectories() throws IOException {
        Path artifactRoot = temp.resolve("artifacts/NFE_SCHEMAS");
        if (!Files.isDirectory(artifactRoot)) return List.of();
        try (var entries = Files.list(artifactRoot)) {
            return entries.filter(path -> path.getFileName().toString().startsWith("staging-"))
                    .toList();
        }
    }

    private void rewriteHash(Path base) throws IOException {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(base.resolve("manifest.properties"))) { properties.load(in); }
        properties.setProperty("sha256", SchemaArtifactStore.treeHash(base));
        try (var out = Files.newOutputStream(base.resolve("manifest.properties"))) { properties.store(out, "forged"); }
    }
}
