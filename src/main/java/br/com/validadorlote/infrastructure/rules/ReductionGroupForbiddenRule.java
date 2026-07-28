package br.com.validadorlote.infrastructure.rules;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Rejeições 1032, 1007 e 1028 (UB26-10, UB45-10 e UB64-10): grupo de redução de alíquota
 * informado quando o CST não permite. Três regras espelhadas, uma por {@link Esfera} — mesmo
 * padrão de {@link DiferimentoForbiddenRule}, trocando o indicador consultado por
 * {@code CstEntry.exigeReducao} ({@code ind_gRed}), e o espelho direto de
 * {@link ReductionGroupRule} (1033/1074/1079): mesmo indicador, mesmos dados de presença
 * (D-030), predicado invertido.
 *
 * <p>Uma exceção literal, idêntica nas três esferas (texto conferido em
 * {@code tmp/NT_2025.002_v1.50_RTC_NF-e_IBS_CBS_IS.md}, item UB26-10 55/65): "Exceção: Percentual
 * de redução da alíquota em compra governamental (tag: {@code gCompraGov/pRedutor}) informado e
 * {@code gIBSUF/gRed/pRedAliq} igual a zero." Os dois fatos são independentes do
 * {@code cClassTrib} — não há aritmética a comparar, só presença/valor bruto — por isso a decisão
 * fica inteira nesta regra, sem depender da camada de percentual (D-030).
 */
public final class ReductionGroupForbiddenRule implements RejectionRule {

    private final Esfera esfera;

    public ReductionGroupForbiddenRule(Esfera esfera) {
        this.esfera = Objects.requireNonNull(esfera, "esfera");
    }

    @Override
    public String rejectionCode() {
        return switch (esfera) {
            case UF -> "1032";
            case MUNICIPIO -> "1007";
            case CBS -> "1028";
        };
    }

    @Override
    public String ruleId() {
        return switch (esfera) {
            case UF -> "UB26-10";
            case MUNICIPIO -> "UB45-10";
            case CBS -> "UB64-10";
        };
    }

    /** Transcrição literal da NT, sem o sufixo {@code [nItem: 999]} que o relatório já mostra. */
    private String mensagemOficial() {
        return switch (esfera) {
            case UF -> "Rejeição: Grupo de redução de alíquota Estadual informado indevidamente";
            case MUNICIPIO ->
                    "Rejeição: Grupo de redução de alíquota Municipal informado indevidamente";
            case CBS -> "Rejeição: Grupo de redução de alíquota da CBS informado indevidamente";
        };
    }

    /** O par exigido desta esfera (1033/1074/1079), citado quando o CST de fato exige o grupo. */
    private String ruleIdDaExigencia() {
        return switch (esfera) {
            case UF -> "UB26-20";
            case MUNICIPIO -> "UB45-20";
            case CBS -> "UB64-20";
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
        if (entry.get().exigeReducao()) {
            return new RuleOutcome.NaoAplicavel("CST " + cst + " exige o grupo de redução de "
                    + "alíquota (ind_gRed = 1): a ausência dele, se for o caso, é da rejeição "
                    + ruleIdDaExigencia() + ".");
        }
        if (!esfera.informouReducao(ctx.item())) {
            // O CST veda o grupo (ind_gRed = 0). Ausência aqui é conformidade, não omissão — como
            // GroupForbiddenRule faz para gIBSCBS e DiferimentoForbiddenRule faz para gDif.
            return new RuleOutcome.Conforme();
        }
        if (!ctx.document().hasCompraGov()) {
            // Sem gCompraGov a exceção de compra governamental não tem como se aplicar.
            return new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), mensagemOficial());
        }
        BigDecimal declarado = esfera.percentualDeclarado(ctx.item());
        if (declarado == null) {
            // pRedAliq é obrigatório dentro do gRed presente: ilegível aqui é conteúdo misto, e
            // quem reporta o erro estrutural com linha e coluna é o XSD. Sem o valor não dá para
            // confirmar o segundo fato da exceção.
            return new RuleOutcome.NaoAvaliado("pRedAliq não legível no grupo gRed desta esfera: "
                    + "sem ele não dá para confirmar a exceção de compra governamental "
                    + "(gCompraGov/pRedutor).");
        }
        if (declarado.compareTo(BigDecimal.ZERO) != 0) {
            // A exceção exige pRedAliq = 0 nesta esfera; qualquer outro valor não a satisfaz,
            // não importa o que gCompraGov/pRedutor diga.
            return new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), mensagemOficial());
        }
        if (ctx.document().pRedutorCompraGov() == null) {
            // gCompraGov está presente, mas pRedutor não foi lido (ausente do XML ou conteúdo
            // misto): sem ele não dá para confirmar o primeiro fato da exceção, mesmo com
            // pRedAliq = 0 nesta esfera. Leitura conservadora: NaoAvaliado, nunca Rejeitado.
            return new RuleOutcome.NaoAvaliado("Documento informa gCompraGov, mas "
                    + "gCompraGov/pRedutor não é legível: sem ele não dá para confirmar a "
                    + "exceção de compra governamental desta " + ruleId() + ".");
        }
        // Os dois fatos literais da exceção estão confirmados: pRedutor informado e pRedAliq = 0
        // nesta esfera. A NT permite o grupo nesta configuração — não é "informado indevidamente".
        return new RuleOutcome.Conforme();
    }
}
