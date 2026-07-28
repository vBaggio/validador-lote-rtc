package br.com.validadorlote.infrastructure.rules;

import java.util.Objects;

/**
 * Rejeições 1030, 1044 e 1061 (UB22-20, UB40-10 e UB59-10): grupo de diferimento ausente quando o
 * CST o exige ({@code ind_gDif = 1}). O espelho de {@link DiferimentoForbiddenRule}, mesmo padrão
 * de {@link GroupRequiredByCstRule} (1022).
 *
 * <p>Delega a {@link GroupForbiddenRule}/{@link GroupRequiredByCstRule} (1021/1022) o caso em que
 * o invólucro interno {@code gIBSCBS} está ausente: sem ele não há onde o {@code gDif} morar, e
 * repetir a acusação aqui encheria o relatório com o mesmo defeito duas vezes — mesmo desvio já
 * adotado por {@link ReductionGroupRule} para {@code gRed}.
 */
public final class DiferimentoRequiredRule implements RejectionRule {

    private final Esfera esfera;

    public DiferimentoRequiredRule(Esfera esfera) {
        this.esfera = Objects.requireNonNull(esfera, "esfera");
    }

    @Override
    public String rejectionCode() {
        return switch (esfera) {
            case UF -> "1030";
            case MUNICIPIO -> "1044";
            case CBS -> "1061";
        };
    }

    @Override
    public String ruleId() {
        return switch (esfera) {
            case UF -> "UB22-20";
            case MUNICIPIO -> "UB40-10";
            case CBS -> "UB59-10";
        };
    }

    /** Transcrição literal da NT, sem o sufixo {@code [nItem: 999]} que o relatório já mostra. */
    private String mensagemOficial() {
        return switch (esfera) {
            case UF -> "Rejeição: CST do IBS/CBS informado obriga informação de diferimento "
                    + "Estadual";
            case MUNICIPIO -> "Rejeição: CST do IBS/CBS informado obriga informação de "
                    + "diferimento Municipal";
            case CBS -> "Rejeição: CST do IBS/CBS informado obriga informação de diferimento "
                    + "da CBS";
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
        if (!entry.get().exigeDiferimento()) {
            return new RuleOutcome.NaoAplicavel("CST " + cst + " não exige o grupo de "
                    + "diferimento (ind_gDif = 0): esse caso é da rejeição " + ruleIdDaVedacao()
                    + ".");
        }
        if (!ctx.item().hasGIbsCbsGroup()) {
            // Sem gIBSCBS não há onde o gDif morar. Quem acusa a ausência dele é a 1022;
            // repetir a acusação aqui encheria o relatório de achados do mesmo defeito.
            return new RuleOutcome.NaoAplicavel(
                    "Grupo gIBSCBS não informado no item: esse caso é da rejeição 1022.");
        }
        return esfera.informouDiferimento(ctx.item())
                ? new RuleOutcome.Conforme()
                : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), mensagemOficial());
    }

    /** O par vedado desta esfera (1029/1083/1090), citado quando o CST não exige o grupo. */
    private String ruleIdDaVedacao() {
        return switch (esfera) {
            case UF -> "UB22-10";
            case MUNICIPIO -> "UB40-20";
            case CBS -> "UB59-20";
        };
    }
}
