package br.com.validadorlote.infrastructure.rules;

/** Caminho da RV 1173 governado pelo indicador da cClassTrib. */
public final class CreditReversalRequiredByClassRule implements RejectionRule {

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
        if (ctx.item().hasEstornoCred()) {
            return new RuleOutcome.Conforme();
        }
        if (CreditReversalRuleSupport.stockLoss(ctx.document())
                == CreditReversalRuleSupport.StockLoss.YES) {
            return new RuleOutcome.NaoAplicavel(
                    "O caminho tpNFDebito=07 é avaliado sem depender da cClassTrib.");
        }
        if (ctx.item().cClassTrib() == null) {
            return new RuleOutcome.NaoAplicavel(
                    "cClassTrib não informada: a obrigatoriedade da tag é avaliada pelo XSD.");
        }
        if (ctx.operationDate() == null) {
            return new RuleOutcome.NaoAvaliado(
                    "cClassTrib indisponível para consultar o indicador de estorno de crédito.");
        }
        var entry = ctx.tables().classTrib(ctx.item().cClassTrib(), ctx.operationDate());
        if (entry.isEmpty()) {
            return new RuleOutcome.NaoAvaliado(
                    "cClassTrib não consta na base embarcada para a data do documento.");
        }
        return entry.get().exigeEstornoCredito()
                ? new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), OFFICIAL_MESSAGE)
                : new RuleOutcome.NaoAplicavel(
                        "A cClassTrib não exige o grupo gEstornoCred neste item.");
    }
}
