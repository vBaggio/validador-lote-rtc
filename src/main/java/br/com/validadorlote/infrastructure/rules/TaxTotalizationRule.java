package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * Conferência determinística dos totais IBS/CBS declarados contra os respectivos itens.
 *
 * <p>W41-10, W46-10 e W56-10 não recalculam tributo: conferem apenas a identidade aritmética
 * que a própria NT exige. Por isso pertencem a esta camada de totalização, independente da futura
 * calculadora. Campo ausente ou ilegível não vira zero: o XSD já apontará a estrutura e esta regra
 * não se aplica, evitando tanto uma rejeição inventada quanto ruído duplicado no relatório.
 */
public final class TaxTotalizationRule implements DocumentRejectionRule {

    public enum Sphere {
        IBS_UF("1080", "W41-10", "Rejeição: Total de IBS UF difere da soma dos itens",
                FiscalDocument::totalIbsUf, ItemTaxGroup::valueIbsUf),
        IBS_MUNICIPAL("1084", "W46-10",
                "Rejeição: Total de IBS Municipal difere da soma dos itens",
                FiscalDocument::totalIbsMunicipal, ItemTaxGroup::valueIbsMunicipal),
        IBS("1085", "W47-10", "Rejeição: Total do IBS difere da soma do vIBS dos itens",
                FiscalDocument::totalIbs, ItemTaxGroup::valueIbs),
        CBS("1091", "W56-10", "Rejeição: Total de CBS difere da soma dos itens",
                FiscalDocument::totalCbs, ItemTaxGroup::valueCbs);

        private final String rejectionCode;
        private final String ruleId;
        private final String officialMessage;
        private final Function<FiscalDocument, BigDecimal> documentValue;
        private final Function<ItemTaxGroup, BigDecimal> itemValue;

        Sphere(String rejectionCode, String ruleId, String officialMessage,
                Function<FiscalDocument, BigDecimal> documentValue,
                Function<ItemTaxGroup, BigDecimal> itemValue) {
            this.rejectionCode = rejectionCode;
            this.ruleId = ruleId;
            this.officialMessage = officialMessage;
            this.documentValue = documentValue;
            this.itemValue = itemValue;
        }
    }

    private final Sphere sphere;

    public TaxTotalizationRule(Sphere sphere) {
        this.sphere = sphere;
    }

    @Override public String rejectionCode() { return sphere.rejectionCode; }

    @Override public String ruleId() { return sphere.ruleId; }

    @Override
    public RuleOutcome evaluate(FiscalDocument document, List<ItemTaxGroup> items) {
        if (!document.hasIbsCbsTot()) {
            return new RuleOutcome.NaoAplicavel(
                    "Documento sem o grupo total/IBSCBSTot: não há total IBS/CBS a conferir.");
        }
        if (items.stream().noneMatch(ItemTaxGroup::hasIbsCbsGroup)) {
            // A 1118 já descreve este defeito estrutural. Não acrescentar três "não avaliado"
            // de totalização sobre uma base que a própria NT diz ser indevida.
            return new RuleOutcome.NaoAplicavel(
                    "Nenhum item possui IBSCBS: a coerência do grupo total é tratada pela 1118.");
        }
        String model = document.model();
        if (model == null) {
            return new RuleOutcome.NaoAvaliado(
                    "Modelo do documento (ide/mod) não encontrado: sem ele não dá para saber se "
                            + ruleId() + " se aplica.");
        }
        if (!"55".equals(model) && !"65".equals(model)) {
            return new RuleOutcome.NaoAplicavel(
                    "Regra restrita aos modelos 55 e 65; documento é modelo " + model + ".");
        }
        BigDecimal total = sphere.documentValue.apply(document);
        if (total == null) {
            // Falta estrutural é território do XSD. Exibi-la também como "não avaliado" em cada
            // esfera multiplicaria ruído e não acrescentaria julgamento fiscal.
            return new RuleOutcome.NaoAplicavel(
                    "Total " + sphere.name() + " ausente ou ilegível em total/IBSCBSTot.");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (ItemTaxGroup item : items) {
            BigDecimal value = sphere.itemValue.apply(item);
            if (value == null) {
                return new RuleOutcome.NaoAplicavel("Valor " + sphere.name()
                        + " ausente ou ilegível no item " + itemLabel(item) + ".");
            }
            sum = sum.add(value);
        }
        return total.compareTo(sum) == 0
                ? new RuleOutcome.Conforme()
                : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), sphere.officialMessage);
    }

    private String itemLabel(ItemTaxGroup item) {
        return item.itemNumber() == null ? "de número não identificado" : item.itemNumber().toString();
    }
}
