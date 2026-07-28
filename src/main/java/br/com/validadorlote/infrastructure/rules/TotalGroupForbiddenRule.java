package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.util.List;

/**
 * Rejeição 1118 (W34-10): o grupo de totais {@code total/IBSCBSTot} foi informado, mas nenhum
 * item do documento possui o invólucro {@code IBSCBS}.
 *
 * <p>Texto literal da NT (p. 72, conferido no PDF <i>NT_2025.002_v1.50</i>, tabela do Grupo W03):
 * <i>"Se grupo de totais do IBS e da CBS (tag: total/IBSCBSTot) informado: Nenhum item possui
 * IBS / CBS informado (id: UB12, tag: IBSCBS)"</i>. A remissão a "UB12, tag: IBSCBS" é a mesma
 * regra e o mesmo elemento da 1115 — o <b>invólucro</b>, não o {@code gIBSCBS} interno (D-027) —
 * por isso esta regra observa {@link ItemTaxGroup#hasIbsCbsGroup()}, não
 * {@link ItemTaxGroup#hasGIbsCbsGroup()}.
 *
 * <p>Sem exceção documentada na NT e sem gatilho de vigência: é comparação pura de presença entre
 * documento e itens, não aritmética (docs/pesquisa/candidatas-rejeicao-pos-b6.md §"Lote 1").
 */
public final class TotalGroupForbiddenRule implements DocumentRejectionRule {

    private static final String MENSAGEM_OFICIAL =
            "Rejeição: Total de IBS e CBS informado indevidamente";

    @Override public String rejectionCode() { return "1118"; }

    @Override public String ruleId() { return "W34-10"; }

    @Override
    public RuleOutcome evaluate(FiscalDocument document, List<ItemTaxGroup> items) {
        if (!document.hasIbsCbsTot()) {
            return new RuleOutcome.NaoAplicavel(
                    "Documento sem o grupo total/IBSCBSTot: esse caso é da rejeição 1119.");
        }
        boolean algumItemTemGrupo = items.stream().anyMatch(ItemTaxGroup::hasIbsCbsGroup);
        return algumItemTemGrupo
                ? new RuleOutcome.Conforme()
                : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), MENSAGEM_OFICIAL);
    }
}
