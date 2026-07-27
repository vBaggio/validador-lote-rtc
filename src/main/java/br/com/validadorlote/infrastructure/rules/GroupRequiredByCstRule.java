package br.com.validadorlote.infrastructure.rules;

/**
 * Rejeição 1022 (UB13-30): grupo IBS/CBS não informado, o espelho da 1021.
 *
 * <p>Observa o grupo <b>interno</b> {@code det/imposto/IBSCBS/gIBSCBS} (D-027). Quando o
 * indicador {@code ind_gIBSCBS} do CST é verdadeiro, o grupo detalhado é exigido; ausência dele
 * é rejeição. O caso do invólucro {@code IBSCBS} inteiro ausente pertence à 1115, não a esta.
 *
 * <p>Exceção literal da NT: não se aplica quando {@code tpNFDebito = 07} (Perda em estoque).
 */
public final class GroupRequiredByCstRule implements RejectionRule {

    /** Perda em estoque: a NT dispensa o grupo detalhado nessa nota de débito. */
    private static final String TP_NF_DEBITO_PERDA_EM_ESTOQUE = "07";

    private static final String MENSAGEM_OFICIAL = "Rejeição: Grupo IBS/CBS não informado";

    @Override public String rejectionCode() { return "1022"; }

    @Override public String ruleId() { return "UB13-30"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
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
        if (TP_NF_DEBITO_PERDA_EM_ESTOQUE.equals(normalizado(ctx.document().tpNFDebito()))) {
            return new RuleOutcome.NaoAplicavel(
                    "Exceção da UB13-30: tpNFDebito=07 (Perda em estoque).");
        }
        var entry = ctx.tables().cst(cst, ctx.operationDate());
        if (entry.isEmpty()) {
            return new RuleOutcome.NaoAvaliado(
                    "CST " + cst + " não consta na base embarcada para a data do documento.");
        }
        if (!entry.get().exigeGrupo()) {
            return new RuleOutcome.NaoAplicavel("CST " + cst + " não admite o grupo gIBSCBS: "
                    + "esse caso é da rejeição 1021.");
        }
        return ctx.item().hasGIbsCbsGroup()
                ? new RuleOutcome.Conforme()
                : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), MENSAGEM_OFICIAL);
    }

    /** Normaliza um código do XML: espaço em volta não pode mudar o veredito. */
    private String normalizado(String valor) {
        return valor == null ? null : valor.trim();
    }
}
