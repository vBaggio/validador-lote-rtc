package br.com.validadorlote.infrastructure.rules;

import java.util.Objects;

/**
 * Rejeições 1033, 1074 e 1079 (UB26-20, UB45-20 e UB64-20): grupo de redução de alíquota ausente
 * quando a NT o exige. Três regras espelhadas, uma por {@link Esfera}.
 *
 * <p>O indicador é o {@code ind_gRed} da tabela de <b>CST</b> — verdadeiro em apenas 3 dos 18
 * (011, 200 e 515). Não confundir com o {@code possuiPercentualReducao} da Calculadora da RFB,
 * que é por classificação tributária e verdadeiro em 60 de 161: usar aquele aqui geraria falso
 * positivo em escala.
 *
 * <p>Dois detalhes literais da NT que o relatório precisa honrar:
 * <ul>
 *   <li><b>Gatilho extra:</b> "ou foi informado o grupo de compras governamentais
 *   ({@code gCompraGov})" — sob compra governamental o {@code gRed} é exigido mesmo com
 *   {@code ind_gRed = 0} (D-030);</li>
 *   <li><b>Exceção:</b> a regra não se aplica a CST com {@code ind_gIBSCBS = 0}, que não admite
 *   a informação do IBS/CBS. Hoje é inócua na prática — os três CSTs com {@code ind_gRed = 1}
 *   também têm {@code ind_gIBSCBS = 1} — mas é literal da norma e protege base futura.</li>
 * </ul>
 */
public final class ReductionGroupRule implements RejectionRule {

    private final Esfera esfera;

    public ReductionGroupRule(Esfera esfera) {
        this.esfera = Objects.requireNonNull(esfera, "esfera");
    }

    @Override
    public String rejectionCode() {
        return switch (esfera) {
            case UF -> "1033";
            case MUNICIPIO -> "1074";
            case CBS -> "1079";
        };
    }

    @Override
    public String ruleId() {
        return switch (esfera) {
            case UF -> "UB26-20";
            case MUNICIPIO -> "UB45-20";
            case CBS -> "UB64-20";
        };
    }

    /** Transcrição literal da NT, sem o sufixo {@code [nItem: 999]} que o relatório já mostra. */
    private String mensagemOficial() {
        return switch (esfera) {
            case UF -> "Rejeição: Não informado o grupo de redução de alíquota Estadual";
            case MUNICIPIO -> "Rejeição: Não informado o grupo de redução de alíquota Municipal";
            case CBS -> "Rejeição: Não informado o grupo de redução de alíquota da CBS";
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
            // Consultar a tabela com data nula estoura NPE na vigência e derrubaria o arquivo
            // inteiro do lote — todos os 18 CSTs têm iniVig não nulo.
            return new RuleOutcome.NaoAvaliado("Data de emissão não encontrada no documento: sem "
                    + "ela não dá para consultar a vigência do CST na tabela oficial.");
        }
        var entry = ctx.tables().cst(cst, ctx.operationDate());
        if (entry.isEmpty()) {
            return new RuleOutcome.NaoAvaliado(
                    "CST " + cst + " não consta na base embarcada para a data do documento.");
        }
        if (!entry.get().exigeGrupo()) {
            return new RuleOutcome.NaoAplicavel("Exceção da " + ruleId() + ": CST " + cst
                    + " não permite a informação do IBS/CBS (ind_gIBSCBS = 0).");
        }
        boolean compraGov = ctx.document().hasCompraGov();
        if (!entry.get().exigeReducao() && !compraGov) {
            return new RuleOutcome.NaoAplicavel("CST " + cst + " não exige o grupo de redução de "
                    + "alíquota (ind_gRed = 0) e o documento não informa compra governamental.");
        }
        if (!ctx.item().hasGIbsCbsGroup()) {
            // Sem gIBSCBS não há onde o gRed morar. Quem acusa a ausência dele é a 1022;
            // repetir a acusação aqui encheria o relatório de achados do mesmo defeito.
            return new RuleOutcome.NaoAplicavel(
                    "Grupo gIBSCBS não informado no item: esse caso é da rejeição 1022.");
        }
        return esfera.informouReducao(ctx.item())
                ? new RuleOutcome.Conforme()
                : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), mensagemOficial());
    }
}
