package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.util.List;

/**
 * Uma regra da NT que julga o <b>documento inteiro</b>, não item a item.
 *
 * <p>Difere de {@link RejectionRule}, cujo {@link RuleContext} pressupõe sempre um item corrente:
 * as onze regras do primeiro corte (D-026) são todas dessa forma. 1118/1119 (W34-10/W34-20) são a
 * primeira exceção — comparam duas presenças de escopos diferentes, o grupo de totais
 * {@code total/IBSCBSTot} (documento) contra o invólucro {@code IBSCBS} em qualquer item da lista.
 * Forçá-las a fingir {@link RejectionRule} exigiria que {@link RuleContext} carregasse a lista de
 * itens inteira para as onze regras existentes, que não precisam disso (D-039).
 *
 * <p>Implementações são sem estado e reutilizáveis, mesmo contrato de {@link RejectionRule}.
 */
public interface DocumentRejectionRule {

    /** Código oficial da rejeição, ex.: "1118". */
    String rejectionCode();

    /** Identificador da regra na NT, ex.: "W34-10". */
    String ruleId();

    RuleOutcome evaluate(FiscalDocument document, List<ItemTaxGroup> items);
}
