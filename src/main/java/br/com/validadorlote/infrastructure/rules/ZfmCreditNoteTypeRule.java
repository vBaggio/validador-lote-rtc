package br.com.validadorlote.infrastructure.rules;

import java.util.Set;

/** Rejeições 1158/1159 (UB131-40/50), governadas por {@code ide/tpNFCredito=02}. */
public final class ZfmCreditNoteTypeRule implements RejectionRule {

    public enum Direction { GROUP_FORBIDDEN, GROUP_REQUIRED }

    private static final Set<String> VALID_CREDIT_NOTE_TYPES =
            Set.of("01", "02", "03", "04", "05", "06");

    private final Direction direction;

    public ZfmCreditNoteTypeRule(Direction direction) {
        this.direction = direction;
    }

    @Override
    public String rejectionCode() {
        return direction == Direction.GROUP_FORBIDDEN ? "1158" : "1159";
    }

    @Override
    public String ruleId() {
        return direction == Direction.GROUP_FORBIDDEN ? "UB131-40" : "UB131-50";
    }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        if (!ctx.item().hasIbsCbsGroup()) {
            return new RuleOutcome.NaoAplicavel(
                    "Item sem o invólucro IBSCBS: esse caso é da rejeição 1115.");
        }
        String model = ctx.document().model();
        if (model == null) {
            return new RuleOutcome.NaoAvaliado("Modelo do documento (ide/mod) não encontrado: "
                    + "sem ele não dá para saber se a " + ruleId() + " se aplica.");
        }
        if (!"55".equals(model)) {
            return new RuleOutcome.NaoAplicavel(ruleId() + " é exclusiva da NF-e modelo 55.");
        }
        boolean present = ctx.item().hasCredPresIbsZfm();
        if (direction == Direction.GROUP_FORBIDDEN && !present) {
            return new RuleOutcome.NaoAplicavel("Grupo gCredPresIBSZFM não informado no item.");
        }
        if (direction == Direction.GROUP_REQUIRED && present) {
            return new RuleOutcome.NaoAplicavel("Grupo gCredPresIBSZFM já informado no item.");
        }
        String type = ctx.document().tpNFCredito();
        if (type == null && ctx.document().finNFe() != null
                && !"5".equals(ctx.document().finNFe())) {
            return new RuleOutcome.NaoAplicavel("Documento não é NF-e de crédito (finNFe=5) e "
                    + "não informa tpNFCredito; a " + ruleId() + " não se aplica.");
        }
        if (type == null || !VALID_CREDIT_NOTE_TYPES.contains(type)) {
            return new RuleOutcome.NaoAvaliado("Tipo de Nota de Crédito (ide/tpNFCredito) ausente "
                    + "ou ilegível: não pode ser tratado como igual nem diferente de 02.");
        }
        boolean zfmCreditNote = "02".equals(type);
        if (direction == Direction.GROUP_FORBIDDEN) {
            return zfmCreditNote
                    ? new RuleOutcome.Conforme()
                    : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(),
                            "Rejeição: Tipo de Nota de Crédito não permite o grupo para "
                                    + "apropriação de crédito presumido de IBS sobre o saldo "
                                    + "devedor na ZFM");
        }
        return zfmCreditNote
                ? new RuleOutcome.Rejeitado(rejectionCode(), ruleId(),
                        "Rejeição: Tipo de Nota de Crédito exige o grupo para apropriação de "
                                + "crédito presumido de IBS sobre o saldo devedor na ZFM")
                : new RuleOutcome.Conforme();
    }
}
