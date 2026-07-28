package br.com.validadorlote.infrastructure.rules;

/**
 * Rejeição 1144 (UB82a-30): {@code gIBSCBS/gTribCompraGov} informado no item quando o documento
 * (modelo 55) não informou {@code gCompraGov}. O espelho de {@link ComprasGovComposicaoRequiredRule}
 * (1141), sem exceção literal na NT e sem consulta à tabela oficial.
 */
public final class ComprasGovComposicaoForbiddenRule implements RejectionRule {

    private static final String MODELO_ALCANCADO = "55";

    private static final String MENSAGEM_OFICIAL = "Rejeição: Grupo de informações da composição "
            + "do valor do IBS e da CBS em compras governamentais informado indevidamente";

    @Override public String rejectionCode() { return "1144"; }

    @Override public String ruleId() { return "UB82a-30"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        String modelo = ctx.document().model();
        if (modelo == null) {
            return new RuleOutcome.NaoAvaliado(
                    "Modelo do documento (ide/mod) não encontrado: sem ele não dá para saber se "
                            + "a UB82a-30 se aplica.");
        }
        if (!MODELO_ALCANCADO.equals(modelo)) {
            return new RuleOutcome.NaoAplicavel(
                    "Regra restrita ao modelo 55; documento é modelo " + modelo + ".");
        }
        if (ctx.document().hasCompraGov()) {
            return new RuleOutcome.NaoAplicavel("Documento com gCompraGov informado: esse caso é "
                    + "da rejeição 1141, não desta.");
        }
        if (!ctx.item().hasIbsCbsGroup()) {
            return new RuleOutcome.NaoAplicavel(
                    "Item sem o invólucro IBSCBS: esse caso é da rejeição 1115.");
        }
        return ctx.item().hasTribCompraGov()
                ? new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), MENSAGEM_OFICIAL)
                : new RuleOutcome.Conforme();
    }
}
