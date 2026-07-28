package br.com.validadorlote.infrastructure.rules;

/**
 * Rejeição 1141 (UB82a-10): {@code gCompraGov} informado no documento (modelo 55) e
 * {@code gIBSCBS/gTribCompraGov} ausente no item.
 *
 * <p>Exceção literal da NT: não se aplica a CST com indicador que não permite a informação do
 * IBS/CBS ({@code ind_gIBSCBS = 0}) — o mesmo indicador ({@code exigeGrupo}) que já governa
 * {@link GroupForbiddenRule}/{@link GroupRequiredByCstRule} (1021/1022).
 */
public final class ComprasGovComposicaoRequiredRule implements RejectionRule {

    private static final String MODELO_ALCANCADO = "55";

    private static final String MENSAGEM_OFICIAL = "Rejeição: Grupo de informações da composição "
            + "do valor do IBS e da CBS em compras governamentais não informado";

    @Override public String rejectionCode() { return "1141"; }

    @Override public String ruleId() { return "UB82a-10"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        String modelo = ctx.document().model();
        if (modelo == null) {
            return new RuleOutcome.NaoAvaliado(
                    "Modelo do documento (ide/mod) não encontrado: sem ele não dá para saber se "
                            + "a UB82a-10 se aplica.");
        }
        if (!MODELO_ALCANCADO.equals(modelo)) {
            return new RuleOutcome.NaoAplicavel(
                    "Regra restrita ao modelo 55; documento é modelo " + modelo + ".");
        }
        if (!ctx.document().hasCompraGov()) {
            return new RuleOutcome.NaoAplicavel("Documento sem gCompraGov: esse caso é da "
                    + "rejeição 1144, não desta.");
        }
        if (!ctx.item().hasIbsCbsGroup()) {
            return new RuleOutcome.NaoAplicavel(
                    "Item sem o invólucro IBSCBS: esse caso é da rejeição 1115.");
        }
        String cst = ctx.item().cst();
        if (cst == null) {
            return new RuleOutcome.NaoAvaliado("CST não informado no grupo IBS/CBS do item.");
        }
        if (ctx.operationDate() == null) {
            return new RuleOutcome.NaoAvaliado("Data de emissão não encontrada no documento: sem "
                    + "ela não dá para consultar a vigência do CST na tabela oficial.");
        }
        var entry = ctx.tables().cst(cst, ctx.operationDate());
        if (entry.isEmpty()) {
            return new RuleOutcome.NaoAvaliado(
                    "CST " + cst + " não consta na base embarcada para a data do documento.");
        }
        if (!entry.get().exigeGrupo()) {
            return new RuleOutcome.NaoAplicavel("Exceção da UB82a-10: CST " + cst + " não permite "
                    + "a informação do IBS/CBS (ind_gIBSCBS = 0).");
        }
        return ctx.item().hasTribCompraGov()
                ? new RuleOutcome.Conforme()
                : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), MENSAGEM_OFICIAL);
    }
}
