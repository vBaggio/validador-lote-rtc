package br.com.validadorlote.infrastructure.rules;

import java.math.BigDecimal;

/** Rejeição 1171 (UB112-30): ao menos um dos valores declarados no ajuste deve ser positivo. */
public final class AdjustmentPositiveValueRule implements RejectionRule {

    private static final String OFFICIAL_MESSAGE =
            "Rejeição: Valor do IBS ou da CBS deve ser maior que zero no ajuste de competência";

    @Override public String rejectionCode() { return "1171"; }

    @Override public String ruleId() { return "UB112-30"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        if (!ctx.item().hasIbsCbsGroup()) {
            return new RuleOutcome.NaoAplicavel(
                    "Item sem o invólucro IBSCBS: esse caso é da rejeição 1115.");
        }
        String model = ctx.document().model();
        if (model == null) {
            return new RuleOutcome.NaoAvaliado("Modelo do documento (ide/mod) não encontrado: "
                    + "sem ele não dá para saber se a UB112-30 se aplica.");
        }
        if (!"55".equals(model)) {
            return new RuleOutcome.NaoAplicavel("UB112-30 é exclusiva da NF-e modelo 55.");
        }
        if (!ctx.item().hasAjusteCompet()) {
            return new RuleOutcome.NaoAplicavel("Grupo gAjusteCompet não informado no item.");
        }
        BigDecimal ibs = ctx.item().ajusteCompetIbs();
        BigDecimal cbs = ctx.item().ajusteCompetCbs();
        if (positive(ibs) || positive(cbs)) {
            return new RuleOutcome.Conforme();
        }
        if (ibs == null || cbs == null) {
            return new RuleOutcome.NaoAvaliado("Valor do IBS ou da CBS do ajuste de competência "
                    + "ausente ou ilegível: não dá para concluir que ambos são não positivos.");
        }
        return new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), OFFICIAL_MESSAGE);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
