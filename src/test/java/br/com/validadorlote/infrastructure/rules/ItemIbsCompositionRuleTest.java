package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.infrastructure.tables.CreditPresumedTable;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ItemIbsCompositionRuleTest {

    private final ItemIbsCompositionRule rule = new ItemIbsCompositionRule(CreditPresumedTable.load());

    @Test
    void deductsPresumedCreditOnlyForOfficialCodeMarkedForDeduction() {
        RuleOutcome outcome = rule.evaluate(context("4", "0.90", LocalDate.of(2027, 1, 1)));

        assertThat(outcome).isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void rejectsWhenDeductiblePresumedCreditIsNotSubtractedFromItemIbs() {
        RuleOutcome outcome = rule.evaluate(context("4", "1.10", LocalDate.of(2027, 1, 1)));

        assertThat(outcome).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) outcome).rejectionCode()).isEqualTo("1150");
    }

    @Test
    void doesNotDeductCreditForOfficialCodeWithoutDeductionIndicator() {
        RuleOutcome outcome = rule.evaluate(context("1", "1.10", LocalDate.of(2027, 1, 1)));

        assertThat(outcome).isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void doesNotIssueVerdictBeforeTheCreditCodeIbsValidity() {
        RuleOutcome outcome = rule.evaluate(context("4", "0.90", LocalDate.of(2026, 12, 31)));

        assertThat(outcome).isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    private static RuleContext context(String code, String ibs, LocalDate date) {
        ItemTaxGroup item = new ItemTaxGroup(1, true, true, "000", "000001", null,
                false, false, false, null, null, null, null,
                false, false, false, false, false, false,
                true, false, false, false,
                new BigDecimal("0.50"), new BigDecimal("0.60"), new BigDecimal(ibs),
                null, code, new BigDecimal("0.20"), Map.of());
        return new RuleContext(null, item, null, date);
    }
}
