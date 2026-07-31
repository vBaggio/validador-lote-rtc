package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.domain.NotEvaluatedCause;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.time.LocalDate;
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
     * Regras de nível <b>documento</b>, avaliadas uma vez por documento — não por item, como as
     * de {@link #BINDINGS}. 1118/1119 (W34-10/W34-20) comparam a presença de
     * {@code total/IBSCBSTot} contra a de {@code IBSCBS} em qualquer item (D-039); 1006 (B31-10,
     * bloco 7) veda {@code gCompraGov} em documento modelo 65 — presença e modelo já vêm prontos
     * em {@code FiscalDocument} (D-030). As três são independentes entre si e não compartilham
     * precondição com as regras de item.
     */
    private static final List<DocumentRejectionRule> DOCUMENT_RULES = List.of(
            new TotalGroupForbiddenRule(), new TotalGroupRequiredRule(),
            new CompraGovForbiddenInNfceRule(),
            new TaxTotalizationRule(TaxTotalizationRule.Sphere.IBS_UF),
            new TaxTotalizationRule(TaxTotalizationRule.Sphere.IBS_MUNICIPAL),
            new TaxTotalizationRule(TaxTotalizationRule.Sphere.IBS),
            new TaxTotalizationRule(TaxTotalizationRule.Sphere.CBS));

    /**
     * As sete rejeições de "grupo/tag informado indevidamente" sem indicador de CST nem exceção
     * (mecanismos 3 e 5 do brief do bloco 7): 1111/1112 valem para 55 e 65 sem gatilho de modelo;
     * as demais cinco são restritas ao modelo 65. Uma única classe genérica parametrizada, para
     * não repetir a mesma forma sete vezes — ver {@link PresenceForbiddenRule}.
     */
    static final List<RejectionRule> PRESENCE_FORBIDDEN_RULES = List.of(
            new PresenceForbiddenRule("1111", "UB24-10",
                    "Rejeição: Grupo de Devolução do IBS da UF informado indevidamente",
                    ItemTaxGroup::hasDevTribUf, null),
            new PresenceForbiddenRule("1112", "UB43-10",
                    "Rejeição: Grupo de Devolução do IBS do Município informado indevidamente",
                    ItemTaxGroup::hasDevTribMun, null),
            new PresenceForbiddenRule("1187", "UB62-10",
                    "Rejeição: Grupo de Devolução da CBS informado indevidamente",
                    ItemTaxGroup::hasDevTribCbs, "65"),
            new PresenceForbiddenRule("1049", "UB120-10",
                    "Rejeição: Não é permitido o uso de Crédito Presumido na NFC-e modelo 65",
                    ItemTaxGroup::hasCredPresOper, "65"),
            new PresenceForbiddenRule("1138", "UB131-10",
                    "Rejeição: Não é permitido o uso de Crédito Presumido ZFM na NFC-e modelo 65",
                    ItemTaxGroup::hasCredPresIbsZfm, "65"),
            new PresenceForbiddenRule("1165", "I05k-10",
                    "Rejeição: Não é permitido informar a classificação para subapuração do IBS "
                            + "na ZFM na NFC-e modelo 65",
                    ItemTaxGroup::hasTpCredPresIbsZfm, "65"),
            new PresenceForbiddenRule("708", "VC02-04",
                    "Rejeição: NFC-e não pode referenciar documento fiscal",
                    item -> item.dfeReferenciado() != null, "65"));

    /** Regras de presença não dependem do invólucro IBSCBS nem de tabela e rodam para todo item. */
    private static final List<RejectionRule> INDEPENDENT_ITEM_RULES = independentItemRules();

    private static List<RejectionRule> independentItemRules() {
        List<RejectionRule> rules = new ArrayList<>(PRESENCE_FORBIDDEN_RULES);
        rules.add(new ZfmCreditClassificationRule());
        return List.copyOf(rules);
    }

    /**
     * Todas as regras de item, na ordem em que a NT as numera. A ordem também escolhe quem fala
     * por uma causa suprimida: dentro de um grupo suprimido, o primeiro que tem o que dizer diz,
     * e a mensagem continua sendo a da regra — o motor não escreve explicação fiscal.
     */
    static final List<Binding> BINDINGS = bindings();

    private static List<Binding> bindings() {
        List<Binding> bindings = new ArrayList<>(List.of(
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
                // Mecanismo 2 (bloco 7): lado "informado indevidamente" das três acima, espelho
                // direto delas — mesma precondição.
                binding(new ReductionGroupForbiddenRule(Esfera.UF),
                        Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
                binding(new ReductionGroupForbiddenRule(Esfera.MUNICIPIO),
                        Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
                binding(new ReductionGroupForbiddenRule(Esfera.CBS),
                        Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
                binding(new ReductionPercentageRule(Esfera.UF),
                        Precondition.CST_PRESENT, Precondition.CST_IN_TABLE,
                        Precondition.CLASS_TRIB_IN_TABLE),
                binding(new ReductionPercentageRule(Esfera.MUNICIPIO),
                        Precondition.CST_PRESENT, Precondition.CST_IN_TABLE,
                        Precondition.CLASS_TRIB_IN_TABLE),
                binding(new ReductionPercentageRule(Esfera.CBS),
                        Precondition.CST_PRESENT, Precondition.CST_IN_TABLE,
                        Precondition.CLASS_TRIB_IN_TABLE),
                // Mecanismo 1 (bloco 7): diferimento por indicador de CST, seis regras
                // espelhadas — mesma dependência de tabela que 1021/1022.
                binding(new DiferimentoForbiddenRule(Esfera.UF),
                        Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
                binding(new DiferimentoRequiredRule(Esfera.UF),
                        Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
                binding(new DiferimentoForbiddenRule(Esfera.MUNICIPIO),
                        Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
                binding(new DiferimentoRequiredRule(Esfera.MUNICIPIO),
                        Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
                binding(new DiferimentoForbiddenRule(Esfera.CBS),
                        Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
                binding(new DiferimentoRequiredRule(Esfera.CBS),
                        Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
                // Mecanismo 4 (bloco 7): gTribCompraGov. 1141 consulta a tabela (exceção
                // ind_gIBSCBS = 0); 1144 não.
                binding(new ComprasGovComposicaoRequiredRule(),
                        Precondition.CST_PRESENT, Precondition.CST_IN_TABLE),
                binding(new ComprasGovComposicaoForbiddenRule())));
        return List.copyOf(bindings);
    }

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
        return evaluate(document, items, document.issueDate());
    }

    /**
     * Avalia com uma data operacional explícita, sem alterar a data original do documento.
     *
     * <p>Esse caminho sustenta a simulação de vigência da interface: um XML de homologação
     * anterior à virada pode ser confrontado com as regras que passarão a valer, enquanto
     * metadados, schema e exceções baseadas no próprio documento permanecem intactos.
     */
    public RuleEvaluation evaluate(FiscalDocument document, List<ItemTaxGroup> items,
            LocalDate operationDate) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(items, "items");
        List<Finding> findings = new ArrayList<>();
        int verified = 0;
        for (ItemTaxGroup item : items) {
            if (evaluateItem(document, item, operationDate, findings)) {
                verified++;
            }
        }
        evaluateDocument(document, items, findings);
        return new RuleEvaluation(List.copyOf(findings), items.size(), verified);
    }

    /**
     * As regras de documento não participam da cascata de {@link Precondition}: não pressupõem
     * CST, cClassTrib nem o invólucro de item nenhum, e por isso rodam sempre, uma vez por
     * documento — não entram em {@link #verifiedItemCount}, que é contagem de item, não de
     * documento.
     */
    private void evaluateDocument(FiscalDocument document, List<ItemTaxGroup> items,
            List<Finding> out) {
        for (DocumentRejectionRule rule : DOCUMENT_RULES) {
            reportDocument(out, document, rule, rule.evaluate(document, items));
        }
    }

    /** @return se o desfecho de uma regra de documento virou achado. Espelha {@link #report}. */
    private boolean reportDocument(List<Finding> out, FiscalDocument document,
            DocumentRejectionRule rule, RuleOutcome outcome) {
        if (outcome instanceof RuleOutcome.Rejeitado rejeitado) {
            out.add(Finding.rejection(document.source(), document.accessKey(), null,
                    rejeitado.rejectionCode(), rejeitado.ruleId(), rejeitado.officialMessage(),
                    rejeitado.friendlyMessage()));
            return true;
        }
        if (outcome instanceof RuleOutcome.NaoAvaliado naoAvaliado) {
            // Nenhuma das duas regras de documento devolve isto hoje (não há precondição que
            // falte); tratado do mesmo jeito que o report() de item, para não deixar buraco se
            // uma regra de documento futura vier a desistir por falta de dado.
            out.add(Finding.notEvaluated(document.source(), document.accessKey(), null,
                    NotEvaluatedCause.RULE_SPECIFIC, rule.ruleId(), naoAvaliado.motivo()));
            return true;
        }
        // Conforme e NaoAplicavel não entram no relatório, mesma semântica do report() de item.
        return false;
    }

    /** @return se ao menos uma regra chegou a veredito neste item. */
    private boolean evaluateItem(FiscalDocument document, ItemTaxGroup item,
            LocalDate operationDate, List<Finding> out) {
        var ctx = new RuleContext(document, item, tables, operationDate);

        boolean verified = false;
        for (RejectionRule rule : INDEPENDENT_ITEM_RULES) {
            RuleOutcome outcome = rule.evaluate(ctx);
            verified |= isVerdict(outcome);
            report(out, ctx, rule, outcome, NotEvaluatedCause.RULE_SPECIFIC);
        }

        RuleOutcome root = GROUP_REQUIRED.evaluate(ctx);
        report(out, ctx, GROUP_REQUIRED, root, NotEvaluatedCause.RULE_SPECIFIC);
        if (operationDate == null || !item.hasIbsCbsGroup()) {
            // Os dois cortes de raiz da cascata. Sem a data do fato gerador nenhuma consulta à
            // tabela é possível; sem o invólucro IBSCBS não existe subgrupo a julgar, e as dez
            // regras restantes só saberiam repetir a causa que a 1115 acabou de reportar.
            return isVerdict(root);
        }

        EnumSet<Precondition> missing = missingPreconditions(item, operationDate);
        EnumSet<Precondition> reported = EnumSet.noneOf(Precondition.class);
        verified |= isVerdict(root);
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
    private EnumSet<Precondition> missingPreconditions(ItemTaxGroup item, LocalDate operationDate) {
        EnumSet<Precondition> missing = EnumSet.noneOf(Precondition.class);
        String cst = item.cst();
        if (cst == null) {
            missing.add(Precondition.CST_PRESENT);
            // Redundante para o roteamento de hoje: toda regra que exige CST_IN_TABLE também exige
            // CST_PRESENT, anterior na ordem do enum. Fica porque o conjunto descreve o que falta,
            // e CST ausente é o caso mais óbvio de CST fora da tabela — uma regra futura que
            // dependa só da tabela seria roteada errado se aqui o conjunto mentisse.
            missing.add(Precondition.CST_IN_TABLE);
        } else if (tables.cst(cst, operationDate).isEmpty()) {
            missing.add(Precondition.CST_IN_TABLE);
        }
        String classTrib = item.cClassTrib();
        if (classTrib == null || tables.classTrib(classTrib, operationDate).isEmpty()) {
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
            if (out.stream().anyMatch(finding -> finding.kind() == FindingKind.NOT_EVALUATED
                    && java.util.Objects.equals(finding.itemNumber(), item)
                    && java.util.Objects.equals(finding.friendlyMessage(), naoAvaliado.motivo()))) {
                return false;
            }
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
