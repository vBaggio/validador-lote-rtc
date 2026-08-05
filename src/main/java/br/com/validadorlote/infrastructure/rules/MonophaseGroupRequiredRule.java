package br.com.validadorlote.infrastructure.rules;

import java.time.LocalDate;
import java.util.Set;

/** Rejeição 1116 (UB13-40), com a implantação própria de produção e a exceção de perda. */
public final class MonophaseGroupRequiredRule implements RejectionRule {

    private static final LocalDate PRODUCTION_START = LocalDate.of(2027, 1, 4);
    private static final Set<String> SUPPORTED_MODELS = Set.of("55", "65");
    private static final String OFFICIAL_MESSAGE =
            "Rejeição: Grupo IBS/CBS Monofásico não informado";

    @Override public String rejectionCode() { return "1116"; }

    @Override public String ruleId() { return "UB13-40"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        if (!ctx.item().hasIbsCbsGroup()) {
            return new RuleOutcome.NaoAplicavel(
                    "Item sem o invólucro IBSCBS: esse caso é da rejeição 1115.");
        }
        String model = ctx.document().model();
        if (model == null) {
            return new RuleOutcome.NaoAvaliado("Modelo do documento (ide/mod) não encontrado: "
                    + "sem ele não dá para saber se a UB13-40 se aplica.");
        }
        if (!SUPPORTED_MODELS.contains(model)) {
            return new RuleOutcome.NaoAvaliado("Modelo " + model + " não é NF-e (55) nem "
                    + "NFC-e (65): a UB13-40 só descreve esses modelos.");
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
        if (!entry.get().exigeMonofasia()) {
            return new RuleOutcome.NaoAplicavel(
                    "CST " + cst + " não exige gIBSCBSMono (ind_gIBSCBSMono = 0).");
        }
        if (ctx.item().hasGIbsCbsMono()) {
            return new RuleOutcome.Conforme();
        }
        if ("07".equals(ctx.document().tpNFDebito())) {
            return new RuleOutcome.NaoAplicavel(
                    "UB13-40 não se aplica a tpNFDebito=07 (perda em estoque).");
        }
        if (ctx.operationDate().isBefore(PRODUCTION_START)) {
            return new RuleOutcome.NaoAplicavel(
                    "UB13-40 entra em produção em 04/01/2027.");
        }
        String environment = ctx.document().tpAmb();
        if ("1".equals(environment)) {
            return new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), OFFICIAL_MESSAGE);
        }
        if ("2".equals(environment)) {
            return new RuleOutcome.NaoAvaliado(
                    "UB13-40 tem implementação futura em homologação.");
        }
        return new RuleOutcome.NaoAvaliado("Ambiente de autorização (ide/tpAmb) ausente ou "
                + "ilegível: sem ele não dá para aplicar a vigência própria da UB13-40.");
    }
}
