package br.com.validadorlote.infrastructure.rules;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Rejeições 1034, 1046 e 1063 (UB27-10, UB46-10 e UB65-10): o {@code pRedAliq} declarado não é o
 * que a tabela oficial publica para a classificação tributária. Três regras espelhadas, uma por
 * {@link Esfera}.
 *
 * <p>O gatilho é o grupo {@code gRed} <b>informado</b>, não a existência do percentual: é assim
 * que a NT enuncia ("Se informado grupo de Redução de Alíquota").
 *
 * <p>A NT tem um segundo ramo, para CST com {@code ind_gRed = 0}, inteiramente dependente de
 * {@code gCompraGov/pRedutor}. Compra governamental tem aritmética própria — o {@code pRedAliq}
 * esperado passa a ser zero, mesmo onde o CST vedaria o preenchimento — e não está coberta nesta
 * versão: esses casos saem como não avaliados, nunca como acusação (D-030).
 */
public final class ReductionPercentageRule implements RejectionRule {

    private final Esfera esfera;

    public ReductionPercentageRule(Esfera esfera) {
        this.esfera = Objects.requireNonNull(esfera, "esfera");
    }

    @Override
    public String rejectionCode() {
        return switch (esfera) {
            case UF -> "1034";
            case MUNICIPIO -> "1046";
            case CBS -> "1063";
        };
    }

    @Override
    public String ruleId() {
        return switch (esfera) {
            case UF -> "UB27-10";
            case MUNICIPIO -> "UB46-10";
            case CBS -> "UB65-10";
        };
    }

    /** Transcrição literal da NT, sem o sufixo {@code [nItem: 999]} que o relatório já mostra. */
    private String mensagemOficial() {
        return switch (esfera) {
            case UF -> "Rejeição: Percentual de redução de alíquota da UF "
                    + "não é válido para este cClassTrib";
            case MUNICIPIO -> "Rejeição: Percentual de redução de alíquota do Município "
                    + "não é válido para este cClassTrib";
            case CBS -> "Rejeição: Percentual de redução de alíquota da CBS "
                    + "não é válido para este cClassTrib";
        };
    }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        if (!esfera.informouReducao(ctx.item())) {
            return new RuleOutcome.NaoAplicavel(
                    "Item sem o grupo gRed nesta esfera: a " + ruleId() + " não tem gatilho.");
        }
        if (ctx.document().hasCompraGov()) {
            // Sob compra governamental o pRedAliq esperado é zero, e não o da tabela. Comparar
            // contra a tabela acusaria uma nota governamental legítima — falso positivo.
            return new RuleOutcome.NaoAvaliado("Documento informa gCompraGov: em compra "
                    + "governamental o pRedAliq esperado é zero e o cálculo envolve "
                    + "gCompraGov/pRedutor, aritmética que esta versão ainda não cobre (D-030).");
        }
        String cst = ctx.item().cst();
        if (cst == null) {
            return new RuleOutcome.NaoAvaliado("CST não informado no grupo IBS/CBS do item: sem "
                    + "ele não dá para saber qual ramo da " + ruleId() + " se aplica.");
        }
        if (ctx.operationDate() == null) {
            return new RuleOutcome.NaoAvaliado("Data de emissão não encontrada no documento: sem "
                    + "ela não dá para consultar a vigência na tabela oficial.");
        }
        var cstEntry = ctx.tables().cst(cst, ctx.operationDate());
        if (cstEntry.isEmpty()) {
            return new RuleOutcome.NaoAvaliado(
                    "CST " + cst + " não consta na base embarcada para a data do documento.");
        }
        if (!cstEntry.get().exigeReducao()) {
            // Ramo ind_gRed = 0 da NT: só é julgável com gCompraGov/pRedutor em mãos.
            return new RuleOutcome.NaoAvaliado("CST " + cst + " veda o uso de redução de alíquota "
                    + "(ind_gRed = 0): esse ramo da " + ruleId() + " depende de "
                    + "gCompraGov/pRedutor e não está coberto nesta versão (D-030).");
        }
        String codigo = ctx.item().cClassTrib();
        if (codigo == null) {
            return new RuleOutcome.NaoAvaliado("Item sem cClassTrib: sem ela não há percentual "
                    + "oficial contra o qual comparar o declarado.");
        }
        var classTrib = ctx.tables().classTrib(codigo, ctx.operationDate());
        if (classTrib.isEmpty()) {
            return new RuleOutcome.NaoAvaliado("cClassTrib " + codigo
                    + " não consta na base embarcada para a data do documento.");
        }
        BigDecimal oficial = esfera.percentualOficial(classTrib.get());
        if (oficial == null) {
            return new RuleOutcome.NaoAvaliado("A base embarcada não publica percentual de "
                    + "redução para a cClassTrib " + codigo + " nesta esfera.");
        }
        BigDecimal declarado = esfera.percentualDeclarado(ctx.item());
        if (declarado == null) {
            // pRedAliq é obrigatório dentro do gRed: ausente aqui significa ilegível, e quem
            // reporta o erro estrutural com linha e coluna é o XSD.
            return new RuleOutcome.NaoAvaliado(
                    "pRedAliq não legível no grupo gRed desta esfera.");
        }
        // compareTo e nunca equals: 60.0 e 60.00 são o mesmo percentual em escalas diferentes.
        return declarado.compareTo(oficial) == 0
                ? new RuleOutcome.Conforme()
                : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), mensagemOficial());
    }
}
