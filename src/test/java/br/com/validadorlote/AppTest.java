package br.com.validadorlote;

import br.com.validadorlote.infrastructure.tables.SvrsTableUpdater;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateAction;
import br.com.validadorlote.infrastructure.update.ArtifactCheckResult;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AppTest {

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
}
