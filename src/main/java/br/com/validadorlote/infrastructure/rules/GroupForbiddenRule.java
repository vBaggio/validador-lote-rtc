package br.com.validadorlote.infrastructure.rules;

/**
 * Rejeição 1021 (UB13-20): grupo IBS/CBS informado indevidamente.
 *
 * <p>Observa o grupo <b>interno</b> {@code det/imposto/IBSCBS/gIBSCBS}, e não o invólucro
 * {@code IBSCBS} — o invólucro carrega o CST e existe sempre que o item declara situação
 * tributária, então olhar para ele faria todo item de isenção ou imunidade corretamente emitido
 * virar acusação, em 7 dos 18 CSTs (D-027).
 *
 * <p>Governada pelo indicador {@code ind_gIBSCBS} da tabela de CST: quando ele é falso, o CST não
 * admite tributação detalhada e o {@code gIBSCBS} não deve vir preenchido.
 */
public final class GroupForbiddenRule implements RejectionRule {

    private static final String MENSAGEM_OFICIAL = "Rejeição: Grupo IBS/CBS informado indevidamente";

    @Override public String rejectionCode() { return "1021"; }

    @Override public String ruleId() { return "UB13-20"; }

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
            // Sem data não há como aplicar a vigência da tabela — e consultar assim mesmo
            // estouraria NullPointerException, derrubando o arquivo inteiro do lote.
            return new RuleOutcome.NaoAvaliado("Data de emissão não encontrada no documento: sem "
                    + "ela não dá para consultar a vigência do CST na tabela oficial.");
        }
        var entry = ctx.tables().cst(cst, ctx.operationDate());
        if (entry.isEmpty()) {
            return new RuleOutcome.NaoAvaliado(
                    "CST " + cst + " não consta na base embarcada para a data do documento.");
        }
        if (entry.get().exigeGrupo()) {
            return new RuleOutcome.NaoAplicavel("CST " + cst + " admite o grupo gIBSCBS: "
                    + "a ausência dele, se for o caso, é da rejeição 1022.");
        }
        // O CST proíbe o grupo e nós verificamos: ausência aqui é conformidade afirmada, não
        // omissão. É o que o item de isenção corretamente emitido merece ver no relatório.
        return ctx.item().hasGIbsCbsGroup()
                ? new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), MENSAGEM_OFICIAL)
                : new RuleOutcome.Conforme();
    }
}
