package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Regra genérica: "se esta tag/grupo está presente [e o modelo é o exigido], foi informado
 * indevidamente". Cobre sete rejeições sem indicador de CST nem exceção — a NT só proíbe a
 * presença, incondicionalmente (1111, 1112) ou restrita ao modelo 65 (1187, 1049, 1138, 1165,
 * 708). A 1165 observa o campo I05k em {@code det/prod}, não o grupo de crédito presumido ZFM.
 * Nenhuma instância consulta a tabela oficial, e por isso nenhuma participa da cascata de
 * {@link RuleEngine.Precondition} — todas são registradas em {@link RuleEngine#BINDINGS} sem
 * precondição.
 *
 * <p>1006 (B31-10, {@code gCompraGov} na NFC-e) segue o mesmo padrão de presença+modelo, mas é
 * regra de <b>documento</b> ({@code gCompraGov} é de {@code ide}, D-030) — por isso não é
 * instância desta classe, que implementa {@link RejectionRule} (item). Ver
 * {@link CompraGovForbiddenInNfceRule} e a decisão registrada no bloco 7.
 */
public final class PresenceForbiddenRule implements RejectionRule {

    private final String rejectionCode;
    private final String ruleId;
    private final String officialMessage;
    private final Predicate<ItemTaxGroup> presente;
    /** Modelo ao qual a proibição se restringe, ou {@code null} quando vale para 55 e 65. */
    private final String modeloExigido;

    public PresenceForbiddenRule(String rejectionCode, String ruleId, String officialMessage,
            Predicate<ItemTaxGroup> presente, String modeloExigido) {
        this.rejectionCode = Objects.requireNonNull(rejectionCode, "rejectionCode");
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        this.officialMessage = Objects.requireNonNull(officialMessage, "officialMessage");
        this.presente = Objects.requireNonNull(presente, "presente");
        this.modeloExigido = modeloExigido;
    }

    @Override public String rejectionCode() { return rejectionCode; }

    @Override public String ruleId() { return ruleId; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        if (modeloExigido != null) {
            String modelo = ctx.document().model();
            if (modelo == null) {
                return new RuleOutcome.NaoAvaliado("Modelo do documento (ide/mod) não encontrado: "
                        + "sem ele não dá para saber se a " + ruleId + " se aplica.");
            }
            if (!modeloExigido.equals(modelo)) {
                return new RuleOutcome.NaoAplicavel("Regra restrita ao modelo " + modeloExigido
                        + " (" + ruleId + "); documento é modelo " + modelo + ".");
            }
        }
        return presente.test(ctx.item())
                ? new RuleOutcome.Rejeitado(rejectionCode, ruleId, officialMessage)
                : new RuleOutcome.Conforme();
    }
}
