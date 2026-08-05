package br.com.validadorlote.infrastructure.rules;

/** RV 1172: cClassTrib veda o grupo de estorno, salvo perda em estoque. */
public final class CreditReversalForbiddenRule implements RejectionRule {

    private static final String OFFICIAL_MESSAGE =
            "Rejeição: Grupo de Estorno de Crédito informado indevidamente";

    @Override public String rejectionCode() { return "1172"; }

    @Override public String ruleId() { return "UB116-10"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        if (!ctx.item().hasIbsCbsGroup()) {
            return new RuleOutcome.NaoAplicavel(
                    "Item sem o invólucro IBSCBS: esse caso é da rejeição 1115.");
        }
        if (!"55".equals(ctx.document().model())) {
            return modelOutcome(ctx, "UB116-10");
        }
        if (!ctx.item().hasEstornoCred()) {
            return new RuleOutcome.NaoAplicavel("Grupo gEstornoCred não informado no item.");
        }
        var classEntry = classEntry(ctx);
        if (classEntry == null) {
            return new RuleOutcome.NaoAvaliado(
                    "cClassTrib indisponível para consultar o indicador de estorno de crédito.");
        }
        if (classEntry.exigeEstornoCredito()) {
            return new RuleOutcome.NaoAplicavel(
                    "A cClassTrib permite o grupo gEstornoCred neste item.");
        }
        return switch (CreditReversalRuleSupport.stockLoss(ctx.document())) {
            case YES -> new RuleOutcome.NaoAplicavel(
                    "Perda em estoque (tpNFDebito=07) excepciona a UB116-10.");
            case UNKNOWN -> new RuleOutcome.NaoAvaliado("Finalidade ou tipo da nota de débito "
                    + "ausente ou ilegível: não dá para excluir a exceção tpNFDebito=07.");
            case NO -> new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), OFFICIAL_MESSAGE);
        };
    }

    private br.com.validadorlote.infrastructure.tables.ClassTribEntry classEntry(RuleContext ctx) {
        if (ctx.item().cClassTrib() == null || ctx.operationDate() == null) {
            return null;
        }
        return ctx.tables().classTrib(ctx.item().cClassTrib(), ctx.operationDate()).orElse(null);
    }

    private RuleOutcome modelOutcome(RuleContext ctx, String id) {
        String model = ctx.document().model();
        if (model == null) {
            return new RuleOutcome.NaoAvaliado("Modelo do documento (ide/mod) não encontrado: "
                    + "sem ele não dá para saber se a " + id + " se aplica.");
        }
        return new RuleOutcome.NaoAplicavel(
                id + " não recebe veredito fiscal local fora da NF-e modelo 55.");
    }
}
