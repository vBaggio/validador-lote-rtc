package br.com.validadorlote.infrastructure.rules;

import java.util.Objects;

/**
 * Rejeições 1029, 1083 e 1090 (UB22-10, UB40-20 e UB59-20): grupo de diferimento informado quando
 * o CST não permite. Três regras espelhadas, uma por {@link Esfera} — mesmo padrão de
 * {@link GroupForbiddenRule} (1021), trocando o indicador consultado por
 * {@code CstEntry.exigeDiferimento} ({@code ind_gDif}).
 *
 * <p>Sem exceção nas três: a NT não traz cláusula de exceção para nenhuma delas (conferido no
 * texto literal da NT_2025.002 v1.50, diferente da UB26-20/UB45-20/UB64-20 de redução, que têm a
 * exceção de compra governamental).
 */
public final class DiferimentoForbiddenRule implements RejectionRule {

    private final Esfera esfera;

    public DiferimentoForbiddenRule(Esfera esfera) {
        this.esfera = Objects.requireNonNull(esfera, "esfera");
    }

    @Override
    public String rejectionCode() {
        return switch (esfera) {
            case UF -> "1029";
            case MUNICIPIO -> "1083";
            case CBS -> "1090";
        };
    }

    @Override
    public String ruleId() {
        return switch (esfera) {
            case UF -> "UB22-10";
            case MUNICIPIO -> "UB40-20";
            case CBS -> "UB59-20";
        };
    }

    /** Transcrição literal da NT, sem o sufixo {@code [nItem: 999]} que o relatório já mostra. */
    private String mensagemOficial() {
        return switch (esfera) {
            case UF -> "Rejeição: CST do IBS/CBS informado não permite informação de "
                    + "diferimento Estadual";
            case MUNICIPIO -> "Rejeição: CST do IBS/CBS informado não permite informação de "
                    + "diferimento Municipal";
            case CBS -> "Rejeição: CST do IBS/CBS informado não permite informação de "
                    + "diferimento da CBS";
        };
    }

    /** O par exigido desta esfera (1030/1044/1061), citado quando a exceção de fato se aplica. */
    private String ruleIdDaExigencia() {
        return switch (esfera) {
            case UF -> "UB22-20";
            case MUNICIPIO -> "UB40-10";
            case CBS -> "UB59-10";
        };
    }

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
        var entry = ctx.tables().cst(cst, ctx.operationDate());
        if (entry.isEmpty()) {
            return new RuleOutcome.NaoAvaliado(
                    "CST " + cst + " não consta na base embarcada para a data do documento.");
        }
        if (entry.get().exigeDiferimento()) {
            return new RuleOutcome.NaoAplicavel("CST " + cst + " exige o grupo de diferimento "
                    + "(ind_gDif = 1): a ausência dele, se for o caso, é da rejeição "
                    + ruleIdDaExigencia() + ".");
        }
        // O CST veda o grupo (ind_gDif = 0). Ausência aqui é conformidade, não omissão — como
        // GroupForbiddenRule faz para gIBSCBS.
        return esfera.informouDiferimento(ctx.item())
                ? new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), mensagemOficial())
                : new RuleOutcome.Conforme();
    }
}
