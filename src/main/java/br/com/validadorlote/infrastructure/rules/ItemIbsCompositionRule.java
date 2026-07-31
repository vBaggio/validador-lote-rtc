package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.infrastructure.tables.CreditPresumedTable;

import java.math.BigDecimal;

/** UB54a-10: composição declarada do IBS do item, sem recalcular alíquota ou base. */
public final class ItemIbsCompositionRule implements RejectionRule {
    private final CreditPresumedTable credits;

    public ItemIbsCompositionRule(CreditPresumedTable credits) { this.credits = credits; }
    @Override public String rejectionCode() { return "1150"; }
    @Override public String ruleId() { return "UB54a-10"; }

    @Override public RuleOutcome evaluate(RuleContext context) {
        var item = context.item();
        if (!item.hasGIbsCbsGroup()) return new RuleOutcome.NaoAplicavel("Item sem gIBSCBS.");
        if (item.valueIbsUf() == null || item.valueIbsMunicipal() == null || item.valueIbs() == null) {
            return new RuleOutcome.NaoAplicavel("Valores IBS do item ausentes ou ilegíveis.");
        }
        BigDecimal expected = item.valueIbsUf().add(item.valueIbsMunicipal());
        if (item.presumedCreditCode() != null) {
            var credit = credits.find(item.presumedCreditCode(), context.operationDate());
            if (credit.isEmpty() || item.presumedIbsCredit() == null) {
                return new RuleOutcome.NaoAplicavel(
                        "Crédito presumido sem vigência IBS ou valor IBS legível.");
            }
            if (credit.get().deductsTotalValue()) expected = expected.subtract(item.presumedIbsCredit());
        }
        return expected.compareTo(item.valueIbs()) == 0 ? new RuleOutcome.Conforme()
                : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(),
                "Rejeição: Valor do IBS do Item (vIBS) difere do calculado");
    }
}
