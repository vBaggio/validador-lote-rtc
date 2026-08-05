package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.infrastructure.tables.CstEntry;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Um lado de um par binário de presença governado por indicador de CST.
 *
 * <p>Cada instância conserva código, célula, mensagem e modelos explícitos no registro do motor;
 * a classe compartilha apenas a mecânica {@code indicador 0/1 × grupo ausente/presente}.
 */
public final class CstGroupPresenceRule implements RejectionRule {

    public enum Direction { FORBIDDEN, REQUIRED }

    private static final Set<String> SUPPORTED_MODELS = Set.of("55", "65");

    private final String rejectionCode;
    private final String ruleId;
    private final String officialMessage;
    private final Predicate<CstEntry> indicator;
    private final Predicate<ItemTaxGroup> groupPresent;
    private final Direction direction;
    private final Set<String> applicableModels;

    public CstGroupPresenceRule(String rejectionCode, String ruleId, String officialMessage,
            Predicate<CstEntry> indicator, Predicate<ItemTaxGroup> groupPresent,
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
        boolean required = indicator.test(entry.get());
        boolean applies = direction == Direction.REQUIRED ? required : !required;
        if (!applies) {
            return new RuleOutcome.NaoAplicavel("Indicador do CST " + cst
                    + " direciona o item para a regra espelhada de " + ruleId + ".");
        }
        boolean present = groupPresent.test(ctx.item());
        boolean rejected = direction == Direction.REQUIRED ? !present : present;
        return rejected
                ? new RuleOutcome.Rejeitado(rejectionCode, ruleId, officialMessage)
                : new RuleOutcome.Conforme();
    }
}
