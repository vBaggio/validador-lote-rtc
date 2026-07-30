package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.validadorlote.infrastructure.xml.ArtifactManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiscalTableArtifactStoreTest {

    @TempDir Path temp;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void installsAValidatedCandidateAndLoadsItFromTheVerifiedManifest() throws IOException {
        FiscalTableArtifactStore store = new FiscalTableArtifactStore(temp);

        var manifest = store.install(embedded(), "svrs-good", "https://fonte.exemplo/tabela", Instant.EPOCH);

        assertThat(manifest.artifact()).isEqualTo(br.com.validadorlote.infrastructure.xml.ArtifactId.FISCAL_TABLES);
        assertThat(store.activeOrNull()).isNotNull();
        assertThat(store.activeOrNull().classTribCount()).isEqualTo(FiscalTables.load().classTribCount());
    }

    @Test
    void prepareKeepsCurrentAndActivatePublishesThePreparedTable() throws IOException {
        FiscalTableArtifactStore store = new FiscalTableArtifactStore(temp);
        byte[] candidate = embedded();
        ArtifactManifest prepared = store.prepare(candidate, "candidate-v2",
                "https://dfe-portal.svrs.rs.gov.br/DFE/TabelaClassificacaoTributaria",
                Instant.parse("2026-07-30T12:00:00Z"));

        assertThat(store.activeManifestOrNull()).isNull();
        assertThat(prepared.version()).isEqualTo("candidate-v2");

        store.activate("candidate-v2");

        assertThat(store.activeManifestOrNull().version()).isEqualTo("candidate-v2");
    }

    @Test
    void activateRejectsPreparedTableChangedAfterValidation() throws IOException {
        FiscalTableArtifactStore store = new FiscalTableArtifactStore(temp);
        store.prepare(embedded(), "candidate-v2", "https://dfe-portal.svrs.rs.gov.br/x",
                Instant.parse("2026-07-30T12:00:00Z"));
        Files.writeString(temp.resolve(
                "artifacts/FISCAL_TABLES/versions/candidate-v2/cst-cclasstrib.json"), "{}");

        assertThatThrownBy(() -> store.activate("candidate-v2"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.activeManifestOrNull()).isNull();
        assertThat(temp.resolve("artifacts/FISCAL_TABLES/current")).doesNotExist();
    }

    @Test
    void identityGateRejectsSameSizeCandidateThatReplacesPublishedCodesAndPreservesThePreviousTable() throws Exception {
        FiscalTableArtifactStore store = new FiscalTableArtifactStore(temp);
        store.install(embedded(), "good", "https://fonte.exemplo/good", Instant.EPOCH);
        byte[] substituted = replacedCodes();
        FiscalTables candidate = FiscalTables.load(new ByteArrayInputStream(substituted));
        assertThat(candidate.cstCount()).isEqualTo(FiscalTables.load().cstCount());
        assertThat(candidate.classTribCount()).isEqualTo(FiscalTables.load().classTribCount());

        assertThatThrownBy(() -> store.install(substituted, "substituted",
                "https://fonte.exemplo/substituted", Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("identidades");

        assertThat(store.activeOrNull().cstCount()).isEqualTo(FiscalTables.load().cstCount());
    }

    @Test
    void corruptLocalPayloadFallsBackToTheEmbeddedTableInTheBootstrap() throws IOException {
        FiscalTableArtifactStore store = new FiscalTableArtifactStore(temp);
        store.install(embedded(), "good", "https://fonte.exemplo/good", Instant.EPOCH);
        Path payload = temp.resolve("artifacts/FISCAL_TABLES/versions/good/cst-cclasstrib.json");
        Files.writeString(payload, "{}");

        assertThat(store.activeOrNull()).isNull();
        assertThat(br.com.validadorlote.App.fiscalTables(store).cstCount())
                .isEqualTo(FiscalTables.load().cstCount());
    }

    private byte[] embedded() throws IOException {
        return Files.readAllBytes(Path.of("src/main/resources/tables/cst-cclasstrib.json"));
    }

    private byte[] replacedCodes() throws Exception {
        JsonNode root = JSON.readTree(embedded());
        for (int index = 0; index < 4; index++) {
            JsonNode cst = root.get(index);
            String replacement = "9" + String.format("%02d", index);
            ((com.fasterxml.jackson.databind.node.ObjectNode) cst).put("cst", replacement);
            for (JsonNode classification : cst.get("classificacoes")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) classification).put("cst", replacement);
            }
        }
        int replacement = 0;
        for (JsonNode cst : root) {
            for (JsonNode classification : cst.get("classificacoes")) {
                if (replacement++ >= 33) break;
                ((com.fasterxml.jackson.databind.node.ObjectNode) classification)
                        .put("codigo", String.format("9%05d", replacement));
            }
            if (replacement >= 33) break;
        }
        return JSON.writeValueAsBytes(root);
    }
}
