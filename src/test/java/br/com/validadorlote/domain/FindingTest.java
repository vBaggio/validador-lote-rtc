package br.com.validadorlote.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FindingTest {

    @Test
    void rejectionCarriesCodeAndRuleId() {
        var f = Finding.rejection(Path.of("a.xml"), "352607...", 1, "1115", "UB12-10",
                "Rejeição: IBS/CBS não informado", "O item não tem o grupo IBS/CBS.");

        assertThat(f.kind()).isEqualTo(FindingKind.REJECTION_RULE);
        assertThat(f.severity()).isEqualTo(Severity.REJECTION);
        assertThat(f.rejectionCode()).isEqualTo("1115");
        assertThat(f.ruleId()).isEqualTo("UB12-10");
        assertThat(f.xsdCode()).isNull();
    }

    @Test
    void notEvaluatedIsNeitherApprovedNorRejected() {
        // Base velha não é erro do emitente: acusar seria culpá-lo por limitação nossa.
        var f = Finding.notEvaluated(Path.of("a.xml"), null, 2,
                NotEvaluatedCause.CLASS_TRIB_UNAVAILABLE, "UB14-20",
                "cClassTrib 999999 não consta na base embarcada");

        assertThat(f.kind()).isEqualTo(FindingKind.NOT_EVALUATED);
        assertThat(f.severity()).isEqualTo(Severity.INFO);
        assertThat(f.rejectionCode()).isNull();
        assertThat(f.officialMessage()).contains("999999");
    }

    @Test
    void notEvaluatedCarriesANonTextualGroupingKey() {
        // Sem chave, a camada "não avaliado" só saberia agregar por substring do motivo.
        var f = Finding.notEvaluated(Path.of("a.xml"), null, 2,
                NotEvaluatedCause.CST_NOT_IN_TABLE, "UB13-20", "CST 999 não consta");

        assertThat(f.notEvaluatedCause()).isEqualTo(NotEvaluatedCause.CST_NOT_IN_TABLE);
        assertThat(f.ruleId()).isEqualTo("UB13-20");
    }

    @Test
    void rejectionHasNoNotEvaluatedCause() {
        var f = Finding.rejection(Path.of("a.xml"), null, 1, "1115", "UB12-10", "msg", null);

        assertThat(f.notEvaluatedCause()).isNull();
    }

    @Test
    void schemaFindingsKeepNullRejectionFields() {
        var f = new Finding(Path.of("a.xml"), null, 1, FindingKind.SCHEMA, Severity.REJECTION,
                "pCBS", "cvc-pattern-valid", "msg", null, 10, 5, null, null, null);

        assertThat(f.rejectionCode()).isNull();
        assertThat(f.ruleId()).isNull();
        assertThat(f.notEvaluatedCause()).isNull();
    }
}
