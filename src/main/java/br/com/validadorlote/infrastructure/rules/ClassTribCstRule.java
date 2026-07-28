package br.com.validadorlote.infrastructure.rules;

/**
 * Rejeição 1024 (UB14-20): a classificação tributária informada não pertence ao CST informado.
 *
 * <p>Sai quase de graça porque a tabela oficial publica as classificações <b>aninhadas</b> sob o
 * CST a que pertencem, e a ingestão da Task 2 preservou esse vínculo em
 * {@link br.com.validadorlote.infrastructure.tables.ClassTribEntry#cst()}.
 */
public final class ClassTribCstRule implements RejectionRule {

    private static final String MENSAGEM_OFICIAL =
            "Rejeição: Rejeição: Classificação Tributária do IBS e da CBS "
                    + "incompatível com o CST informado";

    @Override public String rejectionCode() { return "1024"; }

    @Override public String ruleId() { return "UB14-20"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        String codigo = ctx.item().cClassTrib();
        if (codigo == null) {
            return new RuleOutcome.NaoAplicavel(
                    "Item sem cClassTrib: a UB14-20 só vale quando a tag é informada.");
        }
        String cst = ctx.item().cst();
        if (cst == null) {
            return new RuleOutcome.NaoAvaliado("CST não informado no grupo IBS/CBS do item: "
                    + "sem ele não há com o que comparar a classificação.");
        }
        if (ctx.operationDate() == null) {
            // Consultar a tabela com data nula estoura NPE na vigência e derrubaria o arquivo
            // inteiro do lote.
            return new RuleOutcome.NaoAvaliado("Data de emissão não encontrada no documento: sem "
                    + "ela não dá para consultar a vigência da classificação na tabela oficial.");
        }
        var entry = ctx.tables().classTrib(codigo, ctx.operationDate());
        if (entry.isEmpty()) {
            return new RuleOutcome.NaoAvaliado("cClassTrib " + codigo
                    + " não consta na base embarcada para a data do documento.");
        }
        String cstDaClassificacao = entry.get().cst();
        if (cstDaClassificacao.equals(cst)) {
            return new RuleOutcome.Conforme();
        }
        String detalhe = "cClassTrib " + codigo + " pertence ao CST " + cstDaClassificacao
                + "; o item informou CST " + cst;
        return new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), MENSAGEM_OFICIAL, detalhe);
    }
}
