package br.com.validadorlote.infrastructure.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleOutcomeTest {

    @Test
    void naoAvaliadoIsDistinctFromConforme() {
        // A distinção é a espinha dorsal da confiança: aprovar sem verificar é mentir.
        RuleOutcome conforme = new RuleOutcome.Conforme();
        RuleOutcome naoAvaliado = new RuleOutcome.NaoAvaliado("cClassTrib fora da base");

        assertThat(conforme).isNotEqualTo(naoAvaliado);
        assertThat(naoAvaliado).isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void naoAplicavelCarriesReason() {
        RuleOutcome r = new RuleOutcome.NaoAplicavel("CRT=1: exigência vigora só em 04/01/2027");
        assertThat(((RuleOutcome.NaoAplicavel) r).motivo()).contains("2027");
    }

    @Test
    void rejeitadoCarriesOfficialIdentity() {
        RuleOutcome r = new RuleOutcome.Rejeitado("1115", "UB12-10",
                "Rejeição: IBS/CBS não informado");
        var rej = (RuleOutcome.Rejeitado) r;
        assertThat(rej.rejectionCode()).isEqualTo("1115");
        assertThat(rej.ruleId()).isEqualTo("UB12-10");
    }
}
