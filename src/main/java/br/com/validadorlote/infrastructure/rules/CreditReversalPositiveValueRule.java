package br.com.validadorlote.infrastructure.rules;

import java.math.BigDecimal;

/** RV 1174: ao menos um dos valores declarados no estorno deve ser positivo. */
public final class CreditReversalPositiveValueRule implements RejectionRule {

    private static final String OFFICIAL_MESSAGE =
            "Rejeição: Valor do IBS ou da CBS deve ser maior que zero no estorno de crédito";

    @Override public String rejectionCode() { return "1174"; }

    @Override public String ruleId() { return "UB116-30"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        if (!ctx.item().hasIbsCbsGroup()) {
            return new RuleOutcome.NaoAplicavel(
                    "Item sem o invólucro IBSCBS: esse caso é da rejeição 1115.");
        }
        String model = ctx.document().model();
        if (model == null) {
            return new RuleOutcome.NaoAvaliado("Modelo do documento (ide/mod) não encontrado: "
                    + "sem ele não dá para saber se a UB116-30 se aplica.");
        }
        if (!"55".equals(model)) {
            return new RuleOutcome.NaoAplicavel("UB116-30 é exclusiva da NF-e modelo 55.");
        }
        if (!ctx.item().hasEstornoCred()) {
            return new RuleOutcome.NaoAplicavel("Grupo gEstornoCred não informado no item.");
        }
        var stockLoss = CreditReversalRuleSupport.stockLoss(ctx.document());
        if (stockLoss == CreditReversalRuleSupport.StockLoss.YES) {
            return new RuleOutcome.NaoAplicavel(
                    "Perda em estoque (tpNFDebito=07) excepciona a UB116-30.");
        }
        if (stockLoss == CreditReversalRuleSupport.StockLoss.UNKNOWN) {
            return new RuleOutcome.NaoAvaliado("Finalidade ou tipo da nota de débito ausente ou "
                    + "ilegível: não dá para excluir a exceção tpNFDebito=07.");
        }
        BigDecimal ibs = ctx.item().estornoCredIbs();
        BigDecimal cbs = ctx.item().estornoCredCbs();
        if (positive(ibs) || positive(cbs)) {
            return new RuleOutcome.Conforme();
        }
        if (ibs == null || cbs == null) {
            return new RuleOutcome.NaoAvaliado("Valor do IBS ou da CBS do estorno de crédito "
                    + "ausente ou ilegível: não dá para concluir que ambos são não positivos.");
        }
        return new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), OFFICIAL_MESSAGE);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
