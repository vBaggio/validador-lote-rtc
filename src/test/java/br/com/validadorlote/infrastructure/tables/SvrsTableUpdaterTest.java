package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

class SvrsTableUpdaterTest {

    @TempDir Path temp;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void obtainsTheCurrentSvrsRouteWithoutNetworkAndInstallsOnlyTheNormalizedPayload() throws Exception {
        String html = "<script>const dadosOriginais = " + rawFromEmbedded() + ";</script>";
        SafeHttpsClient https = new SafeHttpsClient(Set.of("dfe-portal.svrs.rs.gov.br"),
                Duration.ofSeconds(1), 6 * 1024 * 1024,
                (uri, timeout) -> new HttpsTransport.Response(200, uri, Map.of(),
                        html.getBytes(StandardCharsets.UTF_8)));
        FiscalTableArtifactStore store = new FiscalTableArtifactStore(temp);

        var manifest = new SvrsTableUpdater(https, new SvrsTableExtractor(),
                new SvrsTableNormalizer(), store).update();

        assertThat(manifest.sourceUrl()).isEqualTo(SvrsTableUpdater.SOURCE.toString());
        assertThat(manifest.version()).startsWith("svrs-");
        assertThat(store.activeOrNull().classTribCount()).isEqualTo(FiscalTables.load().classTribCount());
        Path active = temp.resolve("artifacts/FISCAL_TABLES/versions")
                .resolve(manifest.version());
        assertThat(Files.readString(active.resolve("cst-cclasstrib.json")))
                .doesNotContain("ClassificacoesTributarias").doesNotContain("IndExigeTrib");
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
