package br.com.validadorlote;

import br.com.validadorlote.application.ValidationRuntime;
import br.com.validadorlote.application.ValidationRuntimeFactory;
import br.com.validadorlote.application.ExternalSourcesUseCase;
import br.com.validadorlote.application.ExternalSourcesPhase;
import br.com.validadorlote.application.ValidationLease;
import br.com.validadorlote.infrastructure.tables.FiscalTableArtifactStore;
import br.com.validadorlote.infrastructure.tables.SvrsTableUpdater;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateAction;
import br.com.validadorlote.infrastructure.update.ArtifactCheckResult;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCandidate;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCoordinator;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateException;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateStateStore;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import br.com.validadorlote.infrastructure.xml.ArtifactManifest;
import br.com.validadorlote.infrastructure.xml.CuratedSchemaChannelManifest;
import br.com.validadorlote.infrastructure.xml.CuratedSchemaManifestParser;
import br.com.validadorlote.infrastructure.xml.SchemaArtifactStore;
import br.com.validadorlote.infrastructure.xml.SchemasVersion;
import br.com.validadorlote.infrastructure.xml.XsdErrorTranslator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AppTest {

    @Test
    void packagedRuntimeSmokeInitializesTheTrustedEd25519Key() {
        App.verifyPackagedRuntime();
    }

    @Test
    void readsTheBuildVersionFromTheCompiledResourceWhenNotPackaged() {
        assertThat(System.getProperty("jpackage.app-version")).isNull();

        assertThat(App.applicationVersion()).matches("\\d+\\.\\d+\\.\\d+(-.+)?");
    }

    @Test
    void packagedVersionOverridesTheBuildResource() {
        System.setProperty("jpackage.app-version", "9.9.9");
        try {
            assertThat(App.applicationVersion()).isEqualTo("9.9.9");
        } finally {
            System.clearProperty("jpackage.app-version");
        }
    }

    @Test
    void verifiesThePublishedManifestWithTheProductionTrustAnchor() throws IOException {
        CuratedSchemaManifestParser parser = new CuratedSchemaManifestParser();
        CuratedSchemaChannelManifest manifest = parser.parse(publishedManifest());

        App.schemaManifestVerifier().verify(manifest.keyId(), parser.canonicalSignedBytes(manifest),
                manifest.signature());

        assertThat(manifest.keyId()).isEqualTo("schemas-2026-01");
        assertThat(manifest.signed().version()).isEqualTo("010e_v1.02-r2");
    }

    @Test
    void configuresThePublishedCuratedSchemaChannel(@TempDir Path temp) {
        SvrsTableUpdater tables = new SvrsTableUpdater(null, null, null, null);
        App.SchemaRuntime schemasRuntime = App.schemaRuntime(
                new XsdErrorTranslator(), new SchemaArtifactStore(temp));

        List<ArtifactUpdateAction> actions = App.updateActions(App.schemaUpdater(
                new SchemaArtifactStore(temp)), App.SCHEMA_CHANNEL_ID, tables,
                schemasRuntime.activeManifest());

        assertThat(schemasRuntime.provenance()).isEqualTo(SchemasVersion.read());
        assertThat(schemasRuntime.activeManifest()).isEmpty();
        assertThat(actions).extracting(ArtifactUpdateAction::artifact)
                .containsExactly(ArtifactId.NFE_SCHEMAS, ArtifactId.FISCAL_TABLES);
        assertThat(actions).extracting(ArtifactUpdateAction::channelId)
                .containsExactly(App.SCHEMA_CHANNEL_ID, "svrs-fiscal-table-v1");
        assertThat(App.SCHEMA_MANIFEST_URI)
                .isEqualTo(URI.create("https://vbaggio.github.io/validador-lote-rtc-bases/channels/nfe-schemas/stable.json"));
        assertThat(App.schemaUpdater(new SchemaArtifactStore(temp))).isPresent();
    }

    @Test
    void restoresCuratedSchemaProvenanceForFooterReportAndDisabledChannelAfterRestart(
            @TempDir Path temp) throws IOException {
        SchemaArtifactStore installer = new SchemaArtifactStore(temp);
        Path candidate = copyEmbeddedSchemas(temp.resolve("candidate"));
        installer.prepare(candidate, curatedRelease(), "curated-schemas-stable-v1",
                "https://channel.example/schemas/stable.json");
        installer.activate("rtc-curated-7");

        SchemaArtifactStore restartedStore = new SchemaArtifactStore(temp);
        App.SchemaRuntime schemasRuntime = App.schemaRuntime(
                new XsdErrorTranslator(), restartedStore);
        SvrsTableUpdater tables = new SvrsTableUpdater(null, null, null, null);
        ArtifactCheckResult disabledChannel = App.updateActions(Optional.empty(),
                "curated-schemas-disabled-v1", tables, schemasRuntime.activeManifest())
                .getFirst().check();

        assertThat(schemasRuntime.provenance())
                .contains("rtc-curated-7")
                .contains("Portal Nacional da NF-e")
                .contains("NT 2025.002 v1.30")
                .doesNotContain(SchemasVersion.metadata().profile());
        assertThat(schemasRuntime.activeManifest())
                .get()
                .extracting(manifest -> manifest.version())
                .isEqualTo("rtc-curated-7");
        assertThat(disabledChannel.detail())
                .containsIgnoringCase("base curada ativa rtc-curated-7")
                .doesNotContainIgnoringCase("base embarcada");
    }

    @Test
    void buildsANewCompleteRuntimeForEveryGenerationFromTheActiveStores(@TempDir Path temp) {
        ValidationRuntimeFactory ids = new ValidationRuntimeFactory();
        SchemaArtifactStore schemas = new SchemaArtifactStore(temp);
        var tables = new br.com.validadorlote.infrastructure.tables.FiscalTableArtifactStore(temp);

        ValidationRuntime r1 = App.validationRuntime(new XsdErrorTranslator(), schemas, tables, ids);
        ValidationRuntime r2 = App.validationRuntime(new XsdErrorTranslator(), schemas, tables, ids);

        assertThat(r2.useCase()).isNotSameAs(r1.useCase());
        assertThat(r2.bases().generation()).isGreaterThan(r1.bases().generation());
        assertThat(r2.bases().schemaVersion()).isEqualTo("010e_v1.02");
        assertThat(r2.bases().tableVersion()).startsWith("IT ");
    }

    @Test
    void legacyFiscalSnapshotFallsBackAtomicallyInRuntimeIdentity(@TempDir Path temp)
            throws Exception {
        FiscalTableArtifactStore tables = new FiscalTableArtifactStore(temp);
        writeLegacyTableArtifact(temp, "legacy-v021");

        ValidationRuntime runtime = App.validationRuntime(new XsdErrorTranslator(),
                new SchemaArtifactStore(temp), tables, new ValidationRuntimeFactory());

        assertThat(tables.activeFiscalTablesOrNull()).isNull();
        assertThat(runtime.bases().tableVersion()).startsWith("IT ");
        assertThat(runtime.bases().tableVersion()).doesNotContain("legacy-v021");
        assertThat(runtime.bases().tableProvenance())
                .contains("Informe Técnico 2025.002")
                .doesNotContain("fonte.exemplo/legacy");
    }

    @Test
    void partialPhysicalActivationBuildsR2WithTheNewSchemaAndThePreviousRealTable(
            @TempDir Path temp) throws IOException {
        SchemaArtifactStore schemas = new SchemaArtifactStore(temp);
        FiscalTableArtifactStore tables = new FiscalTableArtifactStore(temp);
        installSchema(schemas, temp, "schemas-r1");
        installTable(tables, "tables-r1");
        schemas.prepare(copyEmbeddedSchemas(temp.resolve("schemas-r2")), "schemas-r2",
                "https://schemas.example/r2", Instant.parse("2026-07-30T12:00:00Z"));
        ValidationRuntimeFactory ids = new ValidationRuntimeFactory();
        ValidationRuntime r1 = App.validationRuntime(new XsdErrorTranslator(), schemas, tables, ids);
        AtomicReference<ValidationRuntime> built = new AtomicReference<>();
        ArtifactUpdateCoordinator coordinator = coordinator(List.of(
                activating(ArtifactId.NFE_SCHEMAS, "schemas", candidate -> schemas.activate("schemas-r2")),
                failingTableAction()), temp);
        ExternalSourcesUseCase sources = new ExternalSourcesUseCase(coordinator, schemas, tables, r1,
                () -> built.updateAndGet(ignored -> App.validationRuntime(new XsdErrorTranslator(),
                        schemas, tables, ids)), Runnable::run);

        coordinator.checkNow();
        assertThat(sources.applyAvailable()).isTrue();

        assertThat(built.get()).isNotNull();
        assertThat(built.get().useCase()).isNotSameAs(r1.useCase());
        assertThat(built.get().bases().schemaVersion()).isEqualTo("schemas-r2");
        assertThat(built.get().bases().tableVersion()).isEqualTo("tables-r1");
        assertThat(sources.tryAcquireValidationLease(r1)).get()
                .extracting(ValidationLease::runtime).isSameAs(built.get());
    }

    @Test
    void failedInMemoryReloadKeepsRealCurrentR2WhileThisSessionStillLeasesR1(
            @TempDir Path temp) throws IOException {
        SchemaArtifactStore schemas = new SchemaArtifactStore(temp);
        FiscalTableArtifactStore tables = new FiscalTableArtifactStore(temp);
        installSchema(schemas, temp, "schemas-r1");
        installTable(tables, "tables-r1");
        schemas.prepare(copyEmbeddedSchemas(temp.resolve("schemas-r2")), "schemas-r2",
                "https://schemas.example/r2", Instant.parse("2026-07-30T12:00:00Z"));
        ValidationRuntimeFactory ids = new ValidationRuntimeFactory();
        ValidationRuntime r1 = App.validationRuntime(new XsdErrorTranslator(), schemas, tables, ids);
        ArtifactUpdateCoordinator coordinator = coordinator(List.of(
                activating(ArtifactId.NFE_SCHEMAS, "schemas", candidate -> schemas.activate("schemas-r2"))),
                temp);
        ExternalSourcesUseCase sources = new ExternalSourcesUseCase(coordinator, schemas, tables, r1,
                () -> { throw new IllegalStateException("compilação simulada falhou"); }, Runnable::run);

        coordinator.checkNow();
        sources.applyAvailable();

        assertThat(schemas.activeManifestOrNull()).extracting(ArtifactManifest::version)
                .isEqualTo("schemas-r2");
        assertThat(sources.snapshot().phase()).isEqualTo(ExternalSourcesPhase.RESTART_REQUIRED);
        assertThat(sources.snapshot().runtimeReloadDetail())
                .contains("ativada em disco")
                .contains("reinicie o aplicativo")
                .doesNotContain("compilação simulada falhou");
        assertThat(sources.snapshot().failedCount()).isZero();
        assertThat(sources.tryAcquireValidationLease(r1)).get()
                .extracting(ValidationLease::runtime).isSameAs(r1);
        ValidationRuntime afterRestart = App.validationRuntime(new XsdErrorTranslator(), schemas,
                tables, new ValidationRuntimeFactory());
        assertThat(afterRestart.bases().schemaVersion()).isEqualTo("schemas-r2");
        assertThat(afterRestart.useCase()).isNotSameAs(r1.useCase());
    }

    private static ArtifactUpdateCoordinator coordinator(List<ArtifactUpdateAction> actions, Path temp) {
        return new ArtifactUpdateCoordinator(actions, Duration.ofHours(24),
                Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC), Runnable::run,
                event -> { }, new ArtifactUpdateStateStore(temp.resolve("state")));
    }

    private static ArtifactUpdateAction activating(ArtifactId id, String channel,
            java.util.function.Function<ArtifactUpdateCandidate, ArtifactManifest> apply) {
        return new ArtifactUpdateAction() {
            @Override public ArtifactId artifact() { return id; }
            @Override public String channelId() { return channel; }
            @Override public ArtifactCheckResult check() {
                return ArtifactCheckResult.available(new ArtifactUpdateCandidate(id, "candidate",
                        "https://updates.example/", Instant.parse("2026-07-30T12:00:00Z"),
                        "a".repeat(64), "pronta"), "pronta");
            }
            @Override public ArtifactManifest apply(ArtifactUpdateCandidate candidate) {
                return apply.apply(candidate);
            }
        };
    }

    private static ArtifactUpdateAction failingTableAction() {
        return activating(ArtifactId.FISCAL_TABLES, "tables", candidate -> {
            throw ArtifactUpdateException.localStorage("tabela indisponível", null);
        });
    }

    private static void installSchema(SchemaArtifactStore schemas, Path temp, String version)
            throws IOException {
        schemas.install(copyEmbeddedSchemas(temp.resolve(version)), version,
                "https://schemas.example/" + version, Instant.parse("2026-07-30T12:00:00Z"));
    }

    private static void installTable(FiscalTableArtifactStore tables, String version)
            throws IOException {
        tables.install(Files.readAllBytes(Path.of("src/main/resources/tables/cst-cclasstrib.json")),
                version, "https://tables.example/" + version, Instant.parse("2026-07-30T12:00:00Z"));
    }

    private static CuratedSchemaChannelManifest.SignedRelease curatedRelease() {
        return new CuratedSchemaChannelManifest.SignedRelease(
                ArtifactId.NFE_SCHEMAS,
                7,
                "rtc-curated-7",
                Instant.parse("2026-07-30T12:00:00Z"),
                "0.1.0",
                URI.create("https://channel.example/schemas/rtc-curated-7.zip"),
                "a".repeat(64),
                List.of(new CuratedSchemaChannelManifest.SourceProvenance(
                        "Portal Nacional da NF-e",
                        URI.create("https://www.nfe.fazenda.gov.br/portal/"),
                        "NT 2025.002 v1.30")));
    }

    private static Path copyEmbeddedSchemas(Path target) throws IOException {
        Path sourceRoot = Path.of("src/main/resources/schemas/nfe");
        try (var paths = Files.walk(sourceRoot)) {
            for (Path source : paths.toList()) {
                Path destination = target.resolve(sourceRoot.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(source, destination);
                }
            }
        }
        return target;
    }

    private static byte[] publishedManifest() throws IOException {
        try (InputStream input = Objects.requireNonNull(AppTest.class.getResourceAsStream(
                "/fixtures/update/curated-schemas/published-stable.json"))) {
            return input.readAllBytes();
        }
    }

    private static void writeLegacyTableArtifact(Path temp, String version) throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = json.readTree(Files.readAllBytes(
                Path.of("src/main/resources/tables/cst-cclasstrib.json")));
        for (var cst : root) {
            var cstObject = (com.fasterxml.jackson.databind.node.ObjectNode) cst;
            cstObject.remove(List.of("exigeMonofasia", "exigeReducaoBaseCalculo",
                    "exigeTransferenciaCredito", "exigeCreditoPresumidoIbsZfm",
                    "exigeAjusteCompetencia"));
            for (var classification : cst.get("classificacoes")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) classification).remove(List.of(
                        "exigeTributacaoRegular", "permiteCreditoPresumido",
                        "exigeEstornoCredito", "exigeMonoValor", "exigeMonoRetencao",
                        "exigeMonoRetido", "exigeMonoDiferimento", "exigePbioDiferenca"));
            }
        }
        byte[] payload = json.writeValueAsBytes(root);
        Path artifactRoot = temp.resolve("artifacts/FISCAL_TABLES");
        Path base = artifactRoot.resolve("versions").resolve(version);
        Files.createDirectories(base);
        Files.write(base.resolve("cst-cclasstrib.json"), payload);
        Properties manifest = new Properties();
        manifest.setProperty("artifact", "FISCAL_TABLES");
        manifest.setProperty("version", version);
        manifest.setProperty("sourceUrl", "https://fonte.exemplo/legacy");
        manifest.setProperty("publishedAt", Instant.EPOCH.toString());
        manifest.setProperty("sha256", java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(payload)));
        manifest.setProperty("lastCheckedAt", Instant.EPOCH.toString());
        manifest.setProperty("updatedAt", Instant.EPOCH.toString());
        manifest.setProperty("result", "ACTIVE");
        try (var output = Files.newOutputStream(base.resolve("manifest.properties"))) {
            manifest.store(output, "manifesto legado íntegro");
        }
        Files.writeString(artifactRoot.resolve("current"), version + "\n");
    }
}
