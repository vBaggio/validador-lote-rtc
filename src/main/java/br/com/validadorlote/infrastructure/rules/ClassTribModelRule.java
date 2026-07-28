package br.com.validadorlote.infrastructure.rules;

import java.util.Set;

/**
 * Rejeição 1025 (UB14-25): a classificação tributária informada não é permitida no modelo do
 * documento. Governada pelos indicadores {@code indNFe} e {@code indNFCe} da tabela oficial —
 * {@code indNFe = 0} veda o modelo 55 e {@code indNFCe = 0} veda o modelo 65.
 */
public final class ClassTribModelRule implements RejectionRule {

    /** Os únicos modelos para os quais a tabela publica indicador: NF-e e NFC-e. */
    private static final Set<String> MODELOS_COM_INDICADOR = Set.of("55", "65");

    private static final String MENSAGEM_OFICIAL =
            "Rejeição: cClassTrib do IBS/CBS não permitido neste modelo de DFe";

    @Override public String rejectionCode() { return "1025"; }

    @Override public String ruleId() { return "UB14-25"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        String codigo = ctx.item().cClassTrib();
        if (codigo == null) {
            return new RuleOutcome.NaoAplicavel(
                    "Item sem cClassTrib: a UB14-25 só vale quando a tag é informada.");
        }
        // Código cru, como nas demais regras: XmlMetadataParser já entrega trim() feito e
        // converte branco em null. O guard de null continua indispensável — Set.of() lança
        // NullPointerException em contains(null).
        String modelo = ctx.document().model();
        if (modelo == null) {
            return new RuleOutcome.NaoAvaliado(
                    "Modelo do documento (ide/mod) não encontrado: sem ele não dá para saber "
                    + "qual dos dois indicadores da tabela se aplica.");
        }
        if (!MODELOS_COM_INDICADOR.contains(modelo)) {
            // permiteModelo() decide por "65 ? indNFCe : indNFe": um modelo desconhecido cairia
            // no ramo da NF-e e seria julgado por um indicador que não é o dele.
            return new RuleOutcome.NaoAvaliado("Modelo " + modelo + " não é NF-e (55) nem "
                    + "NFC-e (65): a tabela oficial só publica indicador para esses dois.");
        }
        if (ctx.operationDate() == null) {
            return new RuleOutcome.NaoAvaliado("Data de emissão não encontrada no documento: sem "
                    + "ela não dá para consultar a vigência da classificação na tabela oficial.");
        }
        var entry = ctx.tables().classTrib(codigo, ctx.operationDate());
        if (entry.isEmpty()) {
            return new RuleOutcome.NaoAvaliado("cClassTrib " + codigo
                    + " não consta na base embarcada para a data do documento.");
        }
        return entry.get().permiteModelo(modelo)
                ? new RuleOutcome.Conforme()
                : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), MENSAGEM_OFICIAL);
    }
}
