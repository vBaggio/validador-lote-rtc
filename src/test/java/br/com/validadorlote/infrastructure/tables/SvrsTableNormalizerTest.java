package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SvrsTableNormalizerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void normalizesThePublicFieldsConsumedByTheRuleEngine() throws Exception {
        byte[] normalized = new SvrsTableNormalizer().normalize(JSON.readTree(rawTable()));

        FiscalTables tables = FiscalTables.load(new ByteArrayInputStream(normalized));

        var date = java.time.LocalDate.of(2026, 8, 3);
        assertThat(tables.cst("000", date)).get().satisfies(cst -> {
            assertThat(cst.exigeMonofasia()).isFalse();
            assertThat(cst.exigeReducaoBaseCalculo()).isFalse();
            assertThat(cst.exigeTransferenciaCredito()).isFalse();
            assertThat(cst.exigeCreditoPresumidoIbsZfm()).isFalse();
            assertThat(cst.exigeAjusteCompetencia()).isFalse();
        });
        assertThat(tables.classTrib("000001", date)).get().satisfies(classification -> {
            assertThat(classification.exigeTributacaoRegular()).isFalse();
            assertThat(classification.permiteCreditoPresumido()).isFalse();
            assertThat(classification.exigeEstornoCredito()).isFalse();
            assertThat(classification.exigeMonoValor()).isFalse();
            assertThat(classification.exigeMonoRetencao()).isFalse();
            assertThat(classification.exigeMonoRetido()).isFalse();
            assertThat(classification.exigeMonoDiferimento()).isFalse();
            assertThat(classification.exigePbioDiferenca()).isFalse();
        });
    }

    @Test
    void rejectsAChangedBooleanContractInsteadOfDefaultingIt() throws Exception {
        String changed = rawTable().replace("\"IndNfe\":true", "\"IndNfe\":\"true\"");

        assertThatThrownBy(() -> new SvrsTableNormalizer().normalize(JSON.readTree(changed)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("IndNfe");
    }

    @ParameterizedTest(name = "campo ausente: {0}")
    @MethodSource("newBooleanFields")
    void rejectsEveryMissingGroupIndicator(String field, String normalizedField,
            boolean classificationField)
            throws Exception {
        var raw = JSON.readTree(rawTable());
        var target = classificationField
                ? (com.fasterxml.jackson.databind.node.ObjectNode) raw.get(0)
                        .get("ClassificacoesTributarias").get(0)
                : (com.fasterxml.jackson.databind.node.ObjectNode) raw.get(0);
        target.remove(field);

        assertThatThrownBy(() -> new SvrsTableNormalizer().normalize(raw))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(field);
    }

    @ParameterizedTest(name = "campo textual: {0}")
    @MethodSource("newBooleanFields")
    void rejectsEveryTextualGroupIndicator(String field, String normalizedField,
            boolean classificationField)
            throws Exception {
        var raw = JSON.readTree(rawTable());
        var target = classificationField
                ? (com.fasterxml.jackson.databind.node.ObjectNode) raw.get(0)
                        .get("ClassificacoesTributarias").get(0)
                : (com.fasterxml.jackson.databind.node.ObjectNode) raw.get(0);
        target.put(field, "false");

        assertThatThrownBy(() -> new SvrsTableNormalizer().normalize(raw))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(field);
    }

    @ParameterizedTest(name = "campo nulo: {0}")
    @MethodSource("newBooleanFields")
    void rejectsEveryNullGroupIndicator(String field, String normalizedField,
            boolean classificationField) throws Exception {
        var raw = JSON.readTree(rawTable());
        var target = classificationField
                ? (com.fasterxml.jackson.databind.node.ObjectNode) raw.get(0)
                        .get("ClassificacoesTributarias").get(0)
                : (com.fasterxml.jackson.databind.node.ObjectNode) raw.get(0);
        target.putNull(field);

        assertThatThrownBy(() -> new SvrsTableNormalizer().normalize(raw))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(field);
    }

    @ParameterizedTest(name = "mapeamento: {0} -> {1}")
    @MethodSource("newBooleanFields")
    void mapsEveryOfficialGroupIndicatorToItsSemanticField(String field,
            String normalizedField, boolean classificationField) throws Exception {
        var raw = JSON.readTree(rawTable());
        var target = classificationField
                ? (com.fasterxml.jackson.databind.node.ObjectNode) raw.get(0)
                        .get("ClassificacoesTributarias").get(0)
                : (com.fasterxml.jackson.databind.node.ObjectNode) raw.get(0);
        target.put(field, true);

        var normalized = JSON.readTree(new SvrsTableNormalizer().normalize(raw));
        var normalizedTarget = classificationField
                ? normalized.get(0).get("classificacoes").get(0)
                : normalized.get(0);
        assertThat(normalizedTarget.get(normalizedField).booleanValue()).isTrue();
    }

    @Test
    void rejectsDuplicateCodesThroughTheSameFiscalTableGate() throws Exception {
        var duplicate = JSON.readTree(rawTable());
        var classifications = (com.fasterxml.jackson.databind.node.ArrayNode) duplicate.get(0)
                .get("ClassificacoesTributarias");
        var copy = classifications.get(0).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) copy).put("NomeReduzido", "Duplicada");
        classifications.add(copy);

        assertThatThrownBy(() -> new SvrsTableNormalizer().normalize(duplicate))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("duplicada");
    }

    static String rawTable() {
        return """
                [{"Cst":"000","NomeCst":"Tributação integral","IndExigeTrib":true,
                  "IndReducaoAliq":false,"IndDiferimento":false,"IndMonofasica":false,
                  "IndReducaoBc":false,"IndTransferenciaCred":false,
                  "IndCredPresIbsZfm":false,"IndAjusteCompet":false,"DthIniVig":"2025-05-01",
                  "DthFimVig":null,"ClassificacoesTributarias":[{"CodClassTrib":"000001",
                  "Cst":"000","NomeReduzido":"Tributada","IndNfe":true,"IndNfce":true,
                  "IndTribRegular":false,"IndPermiteCredPres":false,"IndEstornoCred":false,
                  "IndMonoVal":false,"IndMonoRetem":false,"IndMonoRet":false,
                  "IndMonoDif":false,"IndPbioDiferenca":false,
                  "PercRedIbs":0,"PercRedCbs":0,"DthIniVig":"2025-05-05","DthFimVig":null}]}]
                """;
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> newBooleanFields() {
        return Stream.concat(
                Stream.of(
                        mapping("IndMonofasica", "exigeMonofasia", false),
                        mapping("IndReducaoBc", "exigeReducaoBaseCalculo", false),
                        mapping("IndTransferenciaCred", "exigeTransferenciaCredito", false),
                        mapping("IndCredPresIbsZfm", "exigeCreditoPresumidoIbsZfm", false),
                        mapping("IndAjusteCompet", "exigeAjusteCompetencia", false)),
                Stream.of(
                        mapping("IndTribRegular", "exigeTributacaoRegular", true),
                        mapping("IndPermiteCredPres", "permiteCreditoPresumido", true),
                        mapping("IndEstornoCred", "exigeEstornoCredito", true),
                        mapping("IndMonoVal", "exigeMonoValor", true),
                        mapping("IndMonoRetem", "exigeMonoRetencao", true),
                        mapping("IndMonoRet", "exigeMonoRetido", true),
                        mapping("IndMonoDif", "exigeMonoDiferimento", true),
                        mapping("IndPbioDiferenca", "exigePbioDiferenca", true)));
    }

    private static org.junit.jupiter.params.provider.Arguments mapping(String source,
            String target, boolean classification) {
        return org.junit.jupiter.params.provider.Arguments.of(source, target, classification);
    }
}
