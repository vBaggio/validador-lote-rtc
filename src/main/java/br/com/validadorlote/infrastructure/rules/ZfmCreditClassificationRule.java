package br.com.validadorlote.infrastructure.rules;

import java.util.Set;

/** Rejeição 1166 (I05k-20): subapuração do IBS/ZFM fora da área admitida. */
public final class ZfmCreditClassificationRule implements RejectionRule {

    private static final Set<String> ALLOWED_STATES = Set.of("AC", "AM", "RO", "RR");
    private static final Set<String> ALLOWED_AP_MUNICIPALITIES = Set.of("1600303", "1600600");

    @Override public String rejectionCode() { return "1166"; }

    @Override public String ruleId() { return "I05k-20"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        if (!ctx.item().hasTpCredPresIbsZfm()) {
            return new RuleOutcome.NaoAplicavel("Item sem tpCredPresIBSZFM.");
        }
        String model = ctx.document().model();
        if (model == null) {
            return new RuleOutcome.NaoAvaliado("Modelo do documento (ide/mod) não encontrado.");
        }
        if (!"55".equals(model)) {
            return new RuleOutcome.NaoAplicavel("I05k-20 é exclusiva da NF-e modelo 55.");
        }
        String state = ctx.document().emitterState();
        if (state == null) {
            return new RuleOutcome.NaoAvaliado("UF do emitente (emit/enderEmit/UF) não encontrada.");
        }
        if (ALLOWED_STATES.contains(state)) return new RuleOutcome.Conforme();
        if (!"AP".equals(state)) return rejected();
        String municipality = ctx.document().emitterMunicipalityCode();
        if (municipality == null) {
            return new RuleOutcome.NaoAvaliado(
                    "Município do emitente (emit/enderEmit/cMun) não encontrado para a exceção do AP.");
        }
        return ALLOWED_AP_MUNICIPALITIES.contains(municipality)
                ? new RuleOutcome.Conforme() : rejected();
    }

    private RuleOutcome.Rejeitado rejected() {
        return new RuleOutcome.Rejeitado(rejectionCode(), ruleId(),
                "Rejeição: Classificação para subapuração do IBS na ZFM informado indevidamente");
    }
}
