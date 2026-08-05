package br.com.validadorlote.infrastructure.tables;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiscalTablesTest {

    private static final String MINIMAL_TABLE = """
            [{
              "cst": "000",
              "nome": "Tributação integral",
              "exigeGrupo": true,
              "exigeReducao": false,
              "exigeDiferimento": false,
              "exigeMonofasia": false,
              "exigeReducaoBaseCalculo": false,
              "exigeTransferenciaCredito": false,
              "exigeCreditoPresumidoIbsZfm": false,
              "exigeAjusteCompetencia": false,
              "iniVig": "2025-05-05T00:00:00",
              "fimVig": null,
              "classificacoes": [{
                "codigo": "000001",
                "nome": "Situações tributadas integralmente",
                "nfe": true,
                "nfce": true,
                "exigeTributacaoRegular": false,
                "permiteCreditoPresumido": false,
                "exigeEstornoCredito": false,
                "exigeMonoValor": false,
                "exigeMonoRetencao": false,
                "exigeMonoRetido": false,
                "exigeMonoDiferimento": false,
                "exigePbioDiferenca": false,
                "percRedIbs": 0.0,
                "percRedCbs": 0.0,
                "iniVig": "2025-05-05T00:00:00",
                "fimVig": null
              }]
            }]
            """;

    private static FiscalTables tables;
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 3);

    @BeforeAll
    static void load() {
        tables = FiscalTables.load();
    }

    @Test
    void findsCstWithItsIndicators() {
        var cst = tables.cst("011", HOJE).orElseThrow();

        assertThat(cst.exigeGrupo()).isTrue();
        assertThat(cst.exigeReducao()).isTrue();
    }

    @Test
    void embeddedSnapshotHasTheReviewedOfficialCoverage() {
        assertThat(tables.cstCount()).isEqualTo(18);
        assertThat(tables.classTribCount()).isEqualTo(164);
    }

    @Test
    void onlyThreeCstsRequireReductionGroup() {
        // Guarda contra a confusão com possuiPercentualReducao da Calculadora, que é
        // verdadeiro em 60 de 161 classificações. O indicador real é por CST.
        long comReducao = "000 010 011 200 220 221 222 400 410 510 515 550 620 800 810 811 820 830"
                .lines().flatMap(s -> java.util.Arrays.stream(s.split(" ")))
                .filter(c -> tables.cst(c, HOJE).map(CstEntry::exigeReducao).orElse(false))
                .count();

        assertThat(comReducao).isEqualTo(3);
    }

    @Test
    void onlyTwoCstsRequireTheDeferralGroup() {
        // exigeDiferimento (ind_gDif) é o indicador que a Task do bloco 7 conferiu contra a NT:
        // verdadeiro só nos dois CSTs de diferimento (510 e 515), nunca nos outros 16 — confirma
        // que o campo já embarcado (ex-permiteDiferimento) foi mapeado com a semântica correta.
        long comDiferimento = "000 010 011 200 220 221 222 400 410 510 515 550 620 800 810 811 820 830"
                .lines().flatMap(s -> java.util.Arrays.stream(s.split(" ")))
                .filter(c -> tables.cst(c, HOJE).map(CstEntry::exigeDiferimento).orElse(false))
                .count();

        assertThat(comDiferimento).isEqualTo(2);
        assertThat(tables.cst("510", HOJE).map(CstEntry::exigeDiferimento)).contains(true);
        assertThat(tables.cst("515", HOJE).map(CstEntry::exigeDiferimento)).contains(true);
        assertThat(tables.cst("000", HOJE).map(CstEntry::exigeDiferimento)).contains(false);
    }

    @Test
    void findsClassTribWithModelIndicators() {
        var ct = tables.classTrib("000001", HOJE).orElseThrow();

        assertThat(ct.cst()).isEqualTo("000");
        assertThat(ct.nfe()).isTrue();
        assertThat(ct.nfce()).isTrue();
    }

    @Test
    void unknownCodeYieldsEmptyNotException() {
        // Código publicado depois da nossa extração: base velha, não erro do emitente.
        assertThat(tables.classTrib("999999", HOJE)).isEmpty();
        assertThat(tables.cst("999", HOJE)).isEmpty();
    }

    @Test
    void respectsValidityOnTheOperationDate() {
        // Nenhum registro vigente em 2020 — a base começa em 2025.
        assertThat(tables.cst("000", LocalDate.of(2020, 1, 1))).isEmpty();
        assertThat(tables.cst("000", HOJE)).isPresent();
    }

    @Test
    void respectsExactValidityBoundaries() {
        // 220001 tem iniVig=2025-05-05 e fimVig=2026-01-01T00:00:00 — janela real da base,
        // não um caso sintético. vigenteEm usa !isBefore/!isAfter: ambas as bordas são inclusivas.
        var iniVig = LocalDate.of(2025, 5, 5);
        var fimVig = LocalDate.of(2026, 1, 1);

        assertThat(tables.classTrib("220001", iniVig)).isPresent();
        assertThat(tables.classTrib("220001", fimVig)).isPresent();
        assertThat(tables.classTrib("220001", fimVig.plusDays(1))).isEmpty();
    }

    @Test
    void permiteModeloDistinguishesNfeFromNfce() {
        // 000003 tem nfe=true/nfce=false na base real — cobre os dois ramos do ternário
        // ("65" retorna nfce; qualquer outro valor retorna nfe) com um único registro.
        var ct = tables.classTrib("000003", HOJE).orElseThrow();

        assertThat(ct.permiteModelo("55")).isTrue();
        assertThat(ct.permiteModelo("65")).isFalse();

        // Comportamento atual para modelo que não é "55" nem "65": cai no ramo NF-e (mesmo
        // ramo de "55"), porque a implementação testa apenas "65".equals(modelo). Documentado
        // aqui como comportamento vigente, não validado como correto — ver relatório da task.
        assertThat(ct.permiteModelo("01")).isTrue();
    }

    @Test
    void provenanceNamesSourceAndDate() {
        assertThat(tables.provenance())
                .contains("https://dfe-portal.svrs.rs.gov.br/DFE/TabelaClassificacaoTributaria")
                .contains("extraídas em 2026-08-04");
    }

    @Test
    void provenanceNamesTheOfficialPublicationVersion() {
        assertThat(tables.provenance())
                .contains("Informe Técnico 2025.002")
                .contains("v1.60")
                .contains("23/06/2026");
    }

    @Test
    void rootMustBeAnArray() {
        assertThatThrownBy(() -> FiscalTables.load(json("{}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lista");
    }

    @Test
    void emptyRootIsNotAValidFiscalTable() {
        assertThatThrownBy(() -> FiscalTables.load(json("[]")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vazia");
    }

    @Test
    void malformedJsonIsReportedAsInvalidFiscalTable() {
        assertThatThrownBy(() -> FiscalTables.load(json("[{")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    void trailingJsonValueIsReportedAsInvalidFiscalTable() {
        assertThatThrownBy(() -> FiscalTables.load(json(MINIMAL_TABLE.strip() + "{}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    void missingRequiredBooleanFailsInsteadOfDefaultingToFalse() {
        String malformed = MINIMAL_TABLE.replace("\"nfe\": true,", "");

        assertThatThrownBy(() -> FiscalTables.load(json(malformed)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nfe")
                .hasMessageContaining("000001");
    }

    @ParameterizedTest(name = "contrato destilado obrigatório: {0}")
    @MethodSource("newNormalizedBooleanFields")
    void everyNewIndicatorIsRequiredByTheDistilledContract(String field,
            boolean classificationField) throws Exception {
        var root = (ArrayNode) new ObjectMapper().readTree(MINIMAL_TABLE);
        ObjectNode target = classificationField
                ? (ObjectNode) root.get(0).get("classificacoes").get(0)
                : (ObjectNode) root.get(0);
        target.remove(field);

        assertThatThrownBy(() -> FiscalTables.load(json(root.toString())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(field);
    }

    @ParameterizedTest(name = "fingerprint inclui: {0}")
    @MethodSource("newNormalizedBooleanFields")
    void flippingAnyNewIndicatorChangesTheSemanticFingerprint(String field,
            boolean classificationField) throws Exception {
        byte[] embedded = Files.readAllBytes(
                Path.of("src/main/resources/tables/cst-cclasstrib.json"));
        var root = (ArrayNode) new ObjectMapper().readTree(embedded);
        ObjectNode target = classificationField
                ? (ObjectNode) root.get(0).get("classificacoes").get(0)
                : (ObjectNode) root.get(0);
        target.put(field, !target.get(field).booleanValue());

        assertThat(FiscalTables.load(json(root.toString())).semanticFingerprint())
                .isNotEqualTo(FiscalTables.load(json(new String(embedded,
                        StandardCharsets.UTF_8))).semanticFingerprint());
    }

    @Test
    void semanticFingerprintIgnoresJsonOrderWhitespaceAndDecimalScale() throws Exception {
        byte[] embedded = Files.readAllBytes(
                Path.of("src/main/resources/tables/cst-cclasstrib.json"));
        var root = (ArrayNode) new ObjectMapper().readTree(embedded);
        var reversed = new ObjectMapper().createArrayNode();
        for (int index = root.size() - 1; index >= 0; index--) reversed.add(root.get(index));
        ObjectNode classification = (ObjectNode) reversed.get(0).get("classificacoes").get(0);
        var percentage = classification.get("percRedIbs").decimalValue();
        classification.put("percRedIbs", percentage.setScale(percentage.scale() + 1));

        assertThat(FiscalTables.load(json(reversed.toString())).semanticFingerprint())
                .isEqualTo(FiscalTables.load(json(new String(embedded,
                        StandardCharsets.UTF_8))).semanticFingerprint());
    }

    @Test
    void duplicateCstFailsInsteadOfOverwritingTheFirstEntry() {
        String single = MINIMAL_TABLE.strip();
        String duplicated = "[" + single.substring(1, single.length() - 1)
                + "," + single.substring(1);

        assertThatThrownBy(() -> FiscalTables.load(json(duplicated)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CST duplicado")
                .hasMessageContaining("000");
    }

    @Test
    void malformedRequiredDateFailsInsteadOfBecomingOpenEnded() {
        String malformed = MINIMAL_TABLE.replace(
                "2025-05-05T00:00:00", "data-invalida");

        assertThatThrownBy(() -> FiscalTables.load(json(malformed)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("iniVig");
    }

    private static InputStream json(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> newNormalizedBooleanFields() {
        return Stream.concat(
                Stream.of("exigeMonofasia", "exigeReducaoBaseCalculo",
                        "exigeTransferenciaCredito", "exigeCreditoPresumidoIbsZfm",
                        "exigeAjusteCompetencia")
                        .map(field -> org.junit.jupiter.params.provider.Arguments.of(field, false)),
                Stream.of("exigeTributacaoRegular", "permiteCreditoPresumido",
                        "exigeEstornoCredito", "exigeMonoValor", "exigeMonoRetencao",
                        "exigeMonoRetido", "exigeMonoDiferimento", "exigePbioDiferenca")
                        .map(field -> org.junit.jupiter.params.provider.Arguments.of(field, true)));
    }
}
