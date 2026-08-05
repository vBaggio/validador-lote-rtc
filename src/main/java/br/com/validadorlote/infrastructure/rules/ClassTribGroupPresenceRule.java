package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.infrastructure.tables.ClassTribEntry;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Um lado do par de presença governado por indicador de cClassTrib. */
public final class ClassTribGroupPresenceRule implements RejectionRule {

    public enum Direction { FORBIDDEN, REQUIRED }

    private static final Set<String> SUPPORTED_MODELS = Set.of("55", "65");

    private final String rejectionCode;
    private final String ruleId;
    private final String officialMessage;
    private final Predicate<ClassTribEntry> indicator;
    private final Predicate<ItemTaxGroup> groupPresent;
    private final Direction direction;
    private final Set<String> applicableModels;

    public ClassTribGroupPresenceRule(String rejectionCode, String ruleId, String officialMessage,
            Predicate<ClassTribEntry> indicator, Predicate<ItemTaxGroup> groupPresent,
            Direction direction, Set<String> applicableModels) {
        this.rejectionCode = Objects.requireNonNull(rejectionCode, "rejectionCode");
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        this.officialMessage = Objects.requireNonNull(officialMessage, "officialMessage");
        this.indicator = Objects.requireNonNull(indicator, "indicator");
        this.groupPresent = Objects.requireNonNull(groupPresent, "groupPresent");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.applicableModels = Set.copyOf(applicableModels);
    }

    @Override public String rejectionCode() { return rejectionCode; }

    @Override public String ruleId() { return ruleId; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        if (!ctx.item().hasIbsCbsGroup()) {
            return new RuleOutcome.NaoAplicavel(
                    "Item sem o invólucro IBSCBS: esse caso é da rejeição 1115.");
        }
        String model = ctx.document().model();
        if (model == null) {
            return new RuleOutcome.NaoAvaliado("Modelo do documento (ide/mod) não encontrado: "
                    + "sem ele não dá para saber se a " + ruleId + " se aplica.");
        }
        if (!SUPPORTED_MODELS.contains(model)) {
            return new RuleOutcome.NaoAvaliado("Modelo " + model + " não é NF-e (55) nem "
                    + "NFC-e (65): a " + ruleId + " só descreve esses modelos.");
        }
        if (!applicableModels.contains(model)) {
            return new RuleOutcome.NaoAplicavel(
                    "Regra " + ruleId + " não recebe veredito fiscal local no modelo " + model + ".");
        }
        String classTrib = ctx.item().cClassTrib();
        if (classTrib == null) {
            return new RuleOutcome.NaoAplicavel(
                    "cClassTrib não informada: a obrigatoriedade da tag é avaliada pelo XSD.");
        }
        if (ctx.operationDate() == null) {
            return new RuleOutcome.NaoAvaliado("Data de emissão não encontrada no documento: sem "
                    + "ela não dá para consultar a vigência da cClassTrib na tabela oficial.");
        }
        var entry = ctx.tables().classTrib(classTrib, ctx.operationDate());
        if (entry.isEmpty()) {
            return new RuleOutcome.NaoAvaliado("cClassTrib " + classTrib
                    + " não consta na base embarcada para a data do documento.");
        }
        boolean required = indicator.test(entry.get());
        boolean applies = direction == Direction.REQUIRED ? required : !required;
        if (!applies) {
            return new RuleOutcome.NaoAplicavel("Indicador da cClassTrib " + classTrib
                    + " direciona o item para a regra espelhada de " + ruleId + ".");
        }
        boolean present = groupPresent.test(ctx.item());
        boolean rejected = direction == Direction.REQUIRED ? !present : present;
        return rejected
                ? new RuleOutcome.Rejeitado(rejectionCode, ruleId, officialMessage)
                : new RuleOutcome.Conforme();
    }
}
