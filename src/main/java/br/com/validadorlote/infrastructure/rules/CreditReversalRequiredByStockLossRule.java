package br.com.validadorlote.infrastructure.rules;

/** Caminho autônomo da RV 1173: perda em estoque exige o grupo mesmo sem classificação disponível. */
public final class CreditReversalRequiredByStockLossRule implements RejectionRule {

    private static final String OFFICIAL_MESSAGE =
            "Rejeição: Grupo de Estorno de Crédito não informado";

    @Override public String rejectionCode() { return "1173"; }

    @Override public String ruleId() { return "UB116-20"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        if (!ctx.item().hasIbsCbsGroup()) {
            return new RuleOutcome.NaoAplicavel(
                    "Item sem o invólucro IBSCBS: esse caso é da rejeição 1115.");
        }
        String model = ctx.document().model();
        if (model == null) {
            return new RuleOutcome.NaoAvaliado("Modelo do documento (ide/mod) não encontrado: "
                    + "sem ele não dá para saber se a UB116-20 se aplica.");
        }
        if (!"55".equals(model)) {
            return new RuleOutcome.NaoAplicavel(
                    "UB116-20 é exclusiva da NF-e modelo 55.");
        }
        if (CreditReversalRuleSupport.stockLoss(ctx.document())
                != CreditReversalRuleSupport.StockLoss.YES) {
            return new RuleOutcome.NaoAplicavel(
                    "Documento não declarou perda em estoque (tpNFDebito=07).");
        }
        return ctx.item().hasEstornoCred()
                ? new RuleOutcome.Conforme()
                : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), OFFICIAL_MESSAGE);
    }
}
