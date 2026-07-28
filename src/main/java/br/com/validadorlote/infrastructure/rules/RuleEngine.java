package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.domain.NotEvaluatedCause;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Roda as regras de rejeição sobre um documento, item a item.
 *
 * <p>O motor não julga: quem decide é a regra. O que ele faz, além de orquestrar, é
 * <b>supressão em cascata</b> (§4.4 do design) — quando um dado que várias regras pressupõem
 * está faltando, o item recebe <b>um</b> achado pela causa, não um por regra frustrada. Sem isso,
 * um item cujo CST não está na base embarcada geraria oito "não avaliado" dizendo a mesma coisa, e
 * o relatório deixaria de ser acionável: o contador precisa de uma causa, não de sintomas.
 *
 * <p>A supressão nunca esconde rejeição. Todo dado listado em {@link Precondition} é verificado
 * pelas próprias regras que dele dependem, e na falta dele elas só sabem dizer "não aplicável" ou
 * "não avaliado" — é essa resposta, e nenhuma outra, que a cascata deduplica. A invariante vale
 * também para {@code Conforme}, e é por isso que ela sustenta duas coisas de uma vez: ausência de
 * falso negativo e corretude do {@code verifiedItemCount}, já que regra suprimida não é regra que
 * teria aprovado. Quem a protege é {@code RuleEngineTest#suppressedRuleNeverReachesAVerdict}, que
 * percorre {@link #BINDINGS} em vez de confiar na leitura à mão.
 */
public final class RuleEngine {

    /**
     * Dado sem o qual um grupo de regras não chega a veredito, e a causa que ele imprime no
     * achado quando falta.
     *
     * <p>A ordem de declaração é a ordem de causa-raiz: quando falta mais de um dado ao mesmo
     * item, a regra é atribuída ao <b>primeiro</b> que ela exige, e só esse vira achado.
     */
    enum Precondition {
        /** CST informado no grupo IBS/CBS do item. */
        CST_PRESENT(NotEvaluatedCause.CST_NOT_INFORMED),
        /** CST presente na tabela oficial embarcada, vigente na data do fato gerador. */
        CST_IN_TABLE(NotEvaluatedCause.CST_NOT_IN_TABLE),
        /** cClassTrib informada e presente na tabela oficial, vigente na data do fato gerador. */
        CLASS_TRIB_IN_TABLE(NotEvaluatedCause.CLASS_TRIB_UNAVAILABLE);

        private final NotEvaluatedCause cause;

        Precondition(NotEvaluatedCause cause) {
            this.cause = cause;
        }

        NotEvaluatedCause cause() {
            return cause;
        }
    }

    /** Uma regra e os dados de que ela depende para chegar a veredito. */
    record Binding(RejectionRule rule, EnumSet<Precondition> requires) {}

    /**
     * O que a camada de rejeição apurou num documento.
     *
     * @param findings rejeições previstas e itens não avaliados, na ordem em que foram apurados.
     * @param itemCount itens do documento.
     * @param verifiedItemCount itens em que ao menos uma regra chegou a veredito — conforme ou
     *        rejeitado. Item sem veredito <b>não</b> é item aprovado: sem este número o relatório
     *        não distingue "verificado e conforme" de "nenhuma regra se aplicou" (§4.3 e §4.5).
     */
    public record RuleEvaluation(List<Finding> findings, int itemCount, int verifiedItemCount) {}

    /** Raiz da cascata: é a única regra que não pressupõe o invólucro, e por isso roda sempre. */
    private static final RejectionRule GROUP_REQUIRED = new GroupRequiredRule();

    /**
     * As dez regras restantes, na ordem em que a NT as numera. A ordem também escolhe quem fala
     * por uma causa suprimida: dentro de um grupo suprimido, o primeiro que tem o que dizer diz,
     * e a mensagem continua sendo a da regra — o motor não escreve explicação fiscal.
     */
    static final List<Binding> BINDINGS = List.of(
            binding(new GroupForbiddenRule(),
                    Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
            binding(new GroupRequiredByCstRule(),
                    Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
            binding(new ClassTribCstRule(),
                    Precondition.CST_PRESENT, Precondition.CLASS_TRIB_IN_TABLE),
            binding(new ClassTribModelRule(),
                    Precondition.CLASS_TRIB_IN_TABLE),
            binding(new ReductionGroupRule(Esfera.UF),
                    Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
            binding(new ReductionGroupRule(Esfera.MUNICIPIO),
                    Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
            binding(new ReductionGroupRule(Esfera.CBS),
                    Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
            binding(new ReductionPercentageRule(Esfera.UF),
                    Precondition.CST_PRESENT, Precondition.CST_IN_TABLE,
                    Precondition.CLASS_TRIB_IN_TABLE),
            binding(new ReductionPercentageRule(Esfera.MUNICIPIO),
                    Precondition.CST_PRESENT, Precondition.CST_IN_TABLE,
                    Precondition.CLASS_TRIB_IN_TABLE),
            binding(new ReductionPercentageRule(Esfera.CBS),
                    Precondition.CST_PRESENT, Precondition.CST_IN_TABLE,
                    Precondition.CLASS_TRIB_IN_TABLE));

    private static Binding binding(RejectionRule rule, Precondition... requires) {
        // noneOf + addAll, e não EnumSet.copyOf: isto roda na inicialização da classe, e copyOf de
        // coleção vazia lança — uma regra futura sem precondição viraria ExceptionInInitializerError.
        EnumSet<Precondition> required = EnumSet.noneOf(Precondition.class);
        required.addAll(List.of(requires));
        return new Binding(rule, required);
    }

    private final FiscalTables tables;

    public RuleEngine(FiscalTables tables) {
        this.tables = Objects.requireNonNull(tables, "tables");
    }

    public RuleEvaluation evaluate(FiscalDocument document, List<ItemTaxGroup> items) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(items, "items");
        List<Finding> findings = new ArrayList<>();
        int verified = 0;
        for (ItemTaxGroup item : items) {
            if (evaluateItem(document, item, findings)) {
                verified++;
            }
        }
        return new RuleEvaluation(List.copyOf(findings), items.size(), verified);
    }

    /** @return se ao menos uma regra chegou a veredito neste item. */
    private boolean evaluateItem(FiscalDocument document, ItemTaxGroup item, List<Finding> out) {
        var ctx = new RuleContext(document, item, tables, document.issueDate());

        RuleOutcome root = GROUP_REQUIRED.evaluate(ctx);
        report(out, ctx, GROUP_REQUIRED, root, NotEvaluatedCause.RULE_SPECIFIC);
        if (document.issueDate() == null || !item.hasIbsCbsGroup()) {
            // Os dois cortes de raiz da cascata. Sem a data do fato gerador nenhuma consulta à
            // tabela é possível; sem o invólucro IBSCBS não existe subgrupo a julgar, e as dez
            // regras restantes só saberiam repetir a causa que a 1115 acabou de reportar.
            return isVerdict(root);
        }

        EnumSet<Precondition> missing = missingPreconditions(document, item);
        EnumSet<Precondition> reported = EnumSet.noneOf(Precondition.class);
        boolean verified = isVerdict(root);
        for (Binding binding : BINDINGS) {
            Precondition cause = rootCause(binding, missing);
            if (cause == null) {
                RuleOutcome outcome = binding.rule().evaluate(ctx);
                verified |= isVerdict(outcome);
                report(out, ctx, binding.rule(), outcome, NotEvaluatedCause.RULE_SPECIFIC);
            } else if (!reported.contains(cause) && report(out, ctx, binding.rule(),
                    binding.rule().evaluate(ctx), cause.cause())) {
                reported.add(cause);
            }
        }
        return verified;
    }

    /**
     * Consulta de disponibilidade, não de mérito: se o código existe na base para a data. O que
     * o CST ou a classificação <i>exigem</i> continua sendo assunto exclusivo das regras.
     */
    private EnumSet<Precondition> missingPreconditions(FiscalDocument document, ItemTaxGroup item) {
        EnumSet<Precondition> missing = EnumSet.noneOf(Precondition.class);
        String cst = item.cst();
        if (cst == null) {
            missing.add(Precondition.CST_PRESENT);
            // Redundante para o roteamento de hoje: toda regra que exige CST_IN_TABLE também exige
            // CST_PRESENT, anterior na ordem do enum. Fica porque o conjunto descreve o que falta,
            // e CST ausente é o caso mais óbvio de CST fora da tabela — uma regra futura que
            // dependa só da tabela seria roteada errado se aqui o conjunto mentisse.
            missing.add(Precondition.CST_IN_TABLE);
        } else if (tables.cst(cst, document.issueDate()).isEmpty()) {
            missing.add(Precondition.CST_IN_TABLE);
        }
        String classTrib = item.cClassTrib();
        if (classTrib == null || tables.classTrib(classTrib, document.issueDate()).isEmpty()) {
            missing.add(Precondition.CLASS_TRIB_IN_TABLE);
        }
        return missing;
    }

    /** O dado faltante mais a montante entre os que esta regra exige, ou null se nada falta. */
    private Precondition rootCause(Binding binding, EnumSet<Precondition> missing) {
        for (Precondition precondition : Precondition.values()) {
            if (missing.contains(precondition) && binding.requires().contains(precondition)) {
                return precondition;
            }
        }
        return null;
    }

    /** @return se o desfecho virou achado. */
    private boolean report(List<Finding> out, RuleContext ctx, RejectionRule rule,
            RuleOutcome outcome, NotEvaluatedCause cause) {
        FiscalDocument document = ctx.document();
        Integer item = ctx.item().itemNumber();
        if (outcome instanceof RuleOutcome.Rejeitado rejeitado) {
            out.add(Finding.rejection(document.source(), document.accessKey(), item,
                    rejeitado.rejectionCode(), rejeitado.ruleId(), rejeitado.officialMessage(),
                    rejeitado.friendlyMessage()));
            return true;
        }
        if (outcome instanceof RuleOutcome.NaoAvaliado naoAvaliado) {
            // A regra que desistiu vai junto com a causa: é ela que separa "modelo desconhecido"
            // de "CRT ilegível" dentro de RULE_SPECIFIC, sem obrigar ninguém a ler o texto.
            out.add(Finding.notEvaluated(document.source(), document.accessKey(), item,
                    cause, rule.ruleId(), naoAvaliado.motivo()));
            return true;
        }
        // Conforme e NaoAplicavel não são problema do usuário e não entram na lista de achados.
        // O primeiro é o esperado; o segundo é ausência de exigência, que só o contador de
        // verificados distingue de aprovação.
        return false;
    }

    /** Conforme e Rejeitado são veredito; os outros dois são a confissão de que não houve. */
    private boolean isVerdict(RuleOutcome outcome) {
        return outcome instanceof RuleOutcome.Conforme || outcome instanceof RuleOutcome.Rejeitado;
    }
}
