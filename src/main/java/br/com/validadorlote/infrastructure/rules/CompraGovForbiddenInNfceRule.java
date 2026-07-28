package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.util.List;

/**
 * Rejeição 1006 (B31-10): {@code gCompraGov} informado num documento modelo 65 (NFC-e).
 *
 * <p>Regra de <b>documento</b>, não de item: {@code gCompraGov} é declarado em
 * {@code infNFe/ide/gCompraGov} (D-030), e {@code hasCompraGov}/{@code model} já vêm prontos em
 * {@link FiscalDocument}. Mesmo padrão de presença+modelo de {@link PresenceForbiddenRule}, mas
 * essa classe não serve aqui porque implementa {@link RejectionRule} (item) — unificar as duas
 * interfaces custaria mais em acoplamento do que economiza em linhas para uma única instância.
 */
public final class CompraGovForbiddenInNfceRule implements DocumentRejectionRule {

    private static final String MODELO_ALCANCADO = "65";

    private static final String MENSAGEM_OFICIAL = "Rejeição: NFCe com grupo de compra governamental";

    @Override public String rejectionCode() { return "1006"; }

    @Override public String ruleId() { return "B31-10"; }

    @Override
    public RuleOutcome evaluate(FiscalDocument document, List<ItemTaxGroup> items) {
        String modelo = document.model();
        if (modelo == null) {
            return new RuleOutcome.NaoAvaliado(
                    "Modelo do documento (ide/mod) não encontrado: sem ele não dá para saber se "
                            + "a B31-10 se aplica.");
        }
        if (!MODELO_ALCANCADO.equals(modelo)) {
            return new RuleOutcome.NaoAplicavel(
                    "Regra restrita ao modelo 65; documento é modelo " + modelo + ".");
        }
        return document.hasCompraGov()
                ? new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), MENSAGEM_OFICIAL)
                : new RuleOutcome.Conforme();
    }
}
