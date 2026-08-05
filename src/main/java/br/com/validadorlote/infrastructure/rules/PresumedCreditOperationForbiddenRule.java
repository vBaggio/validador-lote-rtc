package br.com.validadorlote.infrastructure.rules;

/** RV 1175: cClassTrib veda o crédito presumido, salvo fornecimento de bem móvel usado. */
public final class PresumedCreditOperationForbiddenRule implements RejectionRule {

    private static final String OFFICIAL_MESSAGE =
            "Rejeição: Grupo de Crédito Presumido na Operação informado indevidamente";

    @Override public String rejectionCode() { return "1175"; }

    @Override public String ruleId() { return "UB120-20"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        if (!ctx.item().hasIbsCbsGroup()) {
            return new RuleOutcome.NaoAplicavel(
                    "Item sem o invólucro IBSCBS: esse caso é da rejeição 1115.");
        }
        String model = ctx.document().model();
        if (model == null) {
            return new RuleOutcome.NaoAvaliado("Modelo do documento (ide/mod) não encontrado: "
                    + "sem ele não dá para saber se a UB120-20 se aplica.");
        }
        if (!"55".equals(model)) {
            return new RuleOutcome.NaoAplicavel("UB120-20 é exclusiva da NF-e modelo 55.");
        }
        if (!ctx.item().hasCredPresOper()) {
            return new RuleOutcome.NaoAplicavel(
                    "Grupo gCredPresOper não informado no item; o indicador permite, não exige.");
        }
        if (ctx.item().hasIndBemMovelUsado()
                && "1".equals(ctx.item().indBemMovelUsado())) {
            return new RuleOutcome.NaoAplicavel(
                    "Fornecimento de bem móvel usado (indBemMovelUsado=1) excepciona a UB120-20.");
        }
        if (ctx.item().hasIndBemMovelUsado()
                && (ctx.item().cClassTrib() == null || ctx.operationDate() == null)) {
            return unreadableUsedGood();
        }
        if (ctx.item().cClassTrib() == null || ctx.operationDate() == null) {
            return new RuleOutcome.NaoAvaliado(
                    "cClassTrib indisponível para consultar o indicador de crédito presumido.");
        }
        var entry = ctx.tables().classTrib(ctx.item().cClassTrib(), ctx.operationDate());
        if (entry.isEmpty()) {
            return new RuleOutcome.NaoAvaliado(
                    "cClassTrib não consta na base embarcada para a data do documento.");
        }
        if (entry.get().permiteCreditoPresumido()) {
            return new RuleOutcome.Conforme();
        }
        if (ctx.item().hasIndBemMovelUsado()) {
            return unreadableUsedGood();
        }
        return new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), OFFICIAL_MESSAGE);
    }

    private RuleOutcome unreadableUsedGood() {
        return new RuleOutcome.NaoAvaliado("indBemMovelUsado foi informado, mas seu valor é "
                + "ilegível: não dá para excluir a exceção da UB120-20.");
    }
}
