package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.util.List;

/**
 * Rejeição 1119 (W34-20): o grupo de totais {@code total/IBSCBSTot} não foi informado, mas ao
 * menos um item do documento possui o invólucro {@code IBSCBS}. Espelho da 1118.
 *
 * <p>Texto literal da NT (p. 72, conferido no PDF <i>NT_2025.002_v1.50</i>, tabela do Grupo W03):
 * <i>"Se grupo de totais do IBS e da CBS (tag: total/IBSCBSTot) não informado: Pelo menos um item
 * possui IBS / CBS informado (id: UB12, tag: IBSCBS)"</i>. Mesma remissão da 1118 ao invólucro
 * {@code IBSCBS} da UB12-10, não ao {@code gIBSCBS} interno (D-027): observa
 * {@link ItemTaxGroup#hasIbsCbsGroup()}.
 *
 * <p>Sem exceção documentada na NT e sem gatilho de vigência, mesmo raciocínio da 1118.
 */
public final class TotalGroupRequiredRule implements DocumentRejectionRule {

    private static final String MENSAGEM_OFICIAL = "Rejeição: Total de IBS e CBS não informado";

    @Override public String rejectionCode() { return "1119"; }

    @Override public String ruleId() { return "W34-20"; }

    @Override
    public RuleOutcome evaluate(FiscalDocument document, List<ItemTaxGroup> items) {
        if (document.hasIbsCbsTot()) {
            return new RuleOutcome.NaoAplicavel(
                    "Documento com o grupo total/IBSCBSTot informado: esse caso é da rejeição 1118.");
        }
        boolean algumItemTemGrupo = items.stream().anyMatch(ItemTaxGroup::hasIbsCbsGroup);
        return algumItemTemGrupo
                ? new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), MENSAGEM_OFICIAL)
                : new RuleOutcome.Conforme();
    }
}
