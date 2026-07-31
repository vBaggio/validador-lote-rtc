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
        BASE("1076", "W35-10", "Rejeição: Total da BC do IBS e da CBS difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vBC"),
                item -> item.declaredAmounts().get("vBC")),
        IBS_UF_DIF("1077", "W38-10", "Rejeição: Total de Diferimento do IBS UF difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vDifIBSUF"), item -> item.declaredAmounts().get("vDifIBSUF")),
        IBS_UF_DEV("1078", "W39-10", "Rejeição: Total Devolvido do IBS UF difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vDevIBSUF"), item -> item.declaredAmounts().get("vDevIBSUF")),
        IBS_UF("1080", "W41-10", "Rejeição: Total de IBS UF difere da soma dos itens",
                FiscalDocument::totalIbsUf, ItemTaxGroup::valueIbsUf),
        IBS_MUNICIPAL("1084", "W46-10",
                "Rejeição: Total de IBS Municipal difere da soma dos itens",
                FiscalDocument::totalIbsMunicipal, ItemTaxGroup::valueIbsMunicipal),
        IBS_MUNICIPAL_DIF("1081", "W43-10", "Rejeição: Total de Diferimento do IBS Municipal difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vDifIBSMun"), item -> item.declaredAmounts().get("vDifIBSMun")),
        IBS_MUNICIPAL_DEV("1082", "W44-10", "Rejeição: Total Devolvido do IBS Municipal difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vDevIBSMun"), item -> item.declaredAmounts().get("vDevIBSMun")),
        IBS("1085", "W47-10", "Rejeição: Total do IBS difere da soma do vIBS dos itens",
                FiscalDocument::totalIbs, ItemTaxGroup::valueIbs),
        CBS("1091", "W56-10", "Rejeição: Total de CBS difere da soma dos itens",
                FiscalDocument::totalCbs, ItemTaxGroup::valueCbs),
        IBS_CREDIT("1086", "W48-10", "Rejeição: Total de Crédito Presumido do IBS UF difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vCredPresIBS"), item -> item.declaredAmounts().get("vCredPresIBS")),
        CBS_DIF("1088", "W53-10", "Rejeição: Total de Diferimento da CBS difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vDifCBS"), item -> item.declaredAmounts().get("vDifCBS")),
        CBS_DEV("1089", "W54-10", "Rejeição: Total Devolvido da CBS difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vDevCBS"), item -> item.declaredAmounts().get("vDevCBS")),
        CBS_CREDIT("1087", "W56a-10", "Rejeição: Total de Crédito Presumido da CBS difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vCredPresCBS"), item -> item.declaredAmounts().get("vCredPresCBS")),
        IBS_MONO("1092", "W58-10", "Rejeição: Total do IBS monofásico difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vIBSMono"), item -> item.declaredAmounts().get("vIBSMono")),
        CBS_MONO("1093", "W59-10", "Rejeição: Total da CBS monofásica difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vCBSMono"), item -> item.declaredAmounts().get("vCBSMono")),
        IBS_MONO_RETEN("1120", "W59a-10", "Rejeição: Total do IBS monofásico sujeito à retenção difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vIBSMonoReten"), item -> item.declaredAmounts().get("vIBSMonoReten")),
        CBS_MONO_RETEN("1121", "W59b-10", "Rejeição: Total da CBS monofásica sujeita à retenção difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vCBSMonoReten"), item -> item.declaredAmounts().get("vCBSMonoReten")),
        IBS_MONO_RET("1122", "W59c-10", "Rejeição: Total do IBS monofásico retido anteriormente difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vIBSMonoRet"), item -> item.declaredAmounts().get("vIBSMonoRet")),
        CBS_MONO_RET("1123", "W59d-10", "Rejeição: Total da CBS monofásica retida anteriormente difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vCBSMonoRet"), item -> item.declaredAmounts().get("vCBSMonoRet")),
        IBS_ESTORNO("1176", "W59f-10", "Rejeição: Total do IBS estornado difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vIBSEstCred"), item -> item.declaredAmounts().get("vIBSEstCred")),
        CBS_ESTORNO("1177", "W59g-10", "Rejeição: Total da CBS estornada difere da soma dos itens",
                document -> document.ibsCbsTotals().get("vCBSEstCred"), item -> item.declaredAmounts().get("vCBSEstCred"));

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
