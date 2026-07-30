package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import br.com.validadorlote.infrastructure.update.ArtifactCheckResult;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCandidate;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SvrsTableUpdaterTest {

    @TempDir Path temp;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void checkStagesTheCurrentSvrsRouteWithoutChangingTheActiveTable() throws Exception {
        String html = "<script>const dadosOriginais = " + rawFromEmbedded() + ";</script>";
        SafeHttpsClient https = new SafeHttpsClient(Set.of("dfe-portal.svrs.rs.gov.br"),
                Duration.ofSeconds(1), 6 * 1024 * 1024,
                (uri, timeout) -> new HttpsTransport.Response(200, uri, Map.of(),
                        html.getBytes(StandardCharsets.UTF_8)));
        FiscalTableArtifactStore store = new FiscalTableArtifactStore(temp);

        var updater = new SvrsTableUpdater(https, new SvrsTableExtractor(),
                new SvrsTableNormalizer(), store);

        ArtifactCheckResult result = updater.check();

        assertThat(result.status()).isEqualTo(ArtifactCheckResult.Status.UPDATE_AVAILABLE);
        assertThat(result.candidate().sourceUrl()).isEqualTo(SvrsTableUpdater.SOURCE.toString());
        assertThat(result.candidate().version()).startsWith("svrs-");
        assertThat(store.activeOrNull()).isNull();
    }

    @Test
    void repeatedCheckReusesAnIdenticalPreparedCandidateBeforeActivation() throws Exception {
        String html = "<script>const dadosOriginais = " + rawFromEmbedded() + ";</script>";
        SafeHttpsClient https = new SafeHttpsClient(Set.of("dfe-portal.svrs.rs.gov.br"),
                Duration.ofSeconds(1), 6 * 1024 * 1024,
                (uri, timeout) -> new HttpsTransport.Response(200, uri, Map.of(),
                        html.getBytes(StandardCharsets.UTF_8)));
        FiscalTableArtifactStore store = new FiscalTableArtifactStore(temp);
        var updater = new SvrsTableUpdater(https, new SvrsTableExtractor(),
                new SvrsTableNormalizer(), store);

        ArtifactCheckResult first = updater.check();
        ArtifactCheckResult second = updater.check();

        assertThat(first.status()).isEqualTo(ArtifactCheckResult.Status.UPDATE_AVAILABLE);
        assertThat(second.status()).isEqualTo(ArtifactCheckResult.Status.UPDATE_AVAILABLE);
        assertThat(second.candidate()).isEqualTo(first.candidate());
        assertThat(store.activeManifestOrNull()).isNull();
    }

    @Test
    void applyActivatesExactlyTheCandidateReturnedByCheck() throws Exception {
        String html = "<script>const dadosOriginais = " + rawFromEmbedded() + ";</script>";
        SafeHttpsClient https = new SafeHttpsClient(Set.of("dfe-portal.svrs.rs.gov.br"),
                Duration.ofSeconds(1), 6 * 1024 * 1024,
                (uri, timeout) -> new HttpsTransport.Response(200, uri, Map.of(),
                        html.getBytes(StandardCharsets.UTF_8)));
        FiscalTableArtifactStore store = new FiscalTableArtifactStore(temp);
        var updater = new SvrsTableUpdater(https, new SvrsTableExtractor(),
                new SvrsTableNormalizer(), store);
        var candidate = updater.check().candidate();

        var manifest = updater.apply(candidate);

        assertThat(manifest.version()).isEqualTo(candidate.version());
        assertThat(store.activeOrNull().classTribCount()).isEqualTo(FiscalTables.load().classTribCount());
        Path active = temp.resolve("artifacts/FISCAL_TABLES/versions")
                .resolve(candidate.version());
        assertThat(Files.readString(active.resolve("cst-cclasstrib.json")))
                .doesNotContain("ClassificacoesTributarias").doesNotContain("IndExigeTrib");
    }

    @Test
    void applyRejectsACandidateForAnotherArtifact() throws Exception {
        String html = "<script>const dadosOriginais = " + rawFromEmbedded() + ";</script>";
        SafeHttpsClient https = new SafeHttpsClient(Set.of("dfe-portal.svrs.rs.gov.br"),
                Duration.ofSeconds(1), 6 * 1024 * 1024,
                (uri, timeout) -> new HttpsTransport.Response(200, uri, Map.of(),
                        html.getBytes(StandardCharsets.UTF_8)));
        var updater = new SvrsTableUpdater(https, new SvrsTableExtractor(),
                new SvrsTableNormalizer(), new FiscalTableArtifactStore(temp));
        var schemaCandidate = new ArtifactUpdateCandidate(ArtifactId.NFE_SCHEMAS, "candidate-v2",
                "https://dfe-portal.svrs.rs.gov.br/x", java.time.Instant.EPOCH, "0".repeat(64), "");

        assertThatThrownBy(() -> updater.apply(schemaCandidate))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String rawFromEmbedded() throws Exception {
        JsonNode embedded = JSON.readTree(Files.readAllBytes(
                Path.of("src/main/resources/tables/cst-cclasstrib.json")));
        ArrayNode raw = JSON.createArrayNode();
        for (JsonNode sourceCst : embedded) {
            ObjectNode cst = raw.addObject();
            cst.put("Cst", sourceCst.get("cst").asText());
            cst.put("NomeCst", sourceCst.get("nome").asText());
            cst.put("IndExigeTrib", sourceCst.get("exigeGrupo").booleanValue());
            cst.put("IndReducaoAliq", sourceCst.get("exigeReducao").booleanValue());
            cst.put("IndDiferimento", sourceCst.get("exigeDiferimento").booleanValue());
            cst.set("DthIniVig", sourceCst.get("iniVig"));
            cst.set("DthFimVig", sourceCst.get("fimVig"));
            ArrayNode classifications = cst.putArray("ClassificacoesTributarias");
            for (JsonNode sourceClassification : sourceCst.get("classificacoes")) {
                ObjectNode classification = classifications.addObject();
                classification.put("CodClassTrib", sourceClassification.get("codigo").asText());
                classification.put("Cst", sourceCst.get("cst").asText());
                classification.put("NomeReduzido", sourceClassification.get("nome").asText());
                classification.put("IndNfe", sourceClassification.get("nfe").booleanValue());
                classification.put("IndNfce", sourceClassification.get("nfce").booleanValue());
                classification.set("PercRedIbs", sourceClassification.get("percRedIbs"));
                classification.set("PercRedCbs", sourceClassification.get("percRedCbs"));
                classification.set("DthIniVig", sourceClassification.get("iniVig"));
                classification.set("DthFimVig", sourceClassification.get("fimVig"));
            }
        }
        return JSON.writeValueAsString(raw);
    }
}
