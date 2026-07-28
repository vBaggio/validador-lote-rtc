package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1118 (W34-10) e 1119 (W34-20): coerência entre {@code total/IBSCBSTot} (documento) e o
 * invólucro {@code IBSCBS} (item) — a primeira dupla de regras de <b>nível documento</b> do
 * motor (D-039). Testadas isoladamente, sem passar pelo {@link RuleEngine}: essa integração tem
 * teste próprio em {@link RuleEngineTest}.
 */
class TotalGroupRulesTest {

    private static final LocalDate DATA = LocalDate.of(2026, 8, 3);

    private FiscalDocument doc(boolean hasIbsCbsTot) {
        return new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                DATA, "55", "NFe", "3", null, null, false, null, hasIbsCbsTot, List.of());
    }

    private ItemTaxGroup item(boolean hasIbsCbsGroup) {
        return new ItemTaxGroup(1, hasIbsCbsGroup, hasIbsCbsGroup, hasIbsCbsGroup ? "000" : null,
                hasIbsCbsGroup ? "000001" : null, null, false, false, false, null, null, null,
                null, false, false, false, false, false, false, false, false, false, false);
    }

    // ---- 1118 (W34-10): total informado, nenhum item com o invólucro ----

    @Test
    void totalInformedWithNoItemHavingTheWrapperIsRejected() {
        var out = new TotalGroupForbiddenRule().evaluate(doc(true), List.of(item(false)));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).rejectionCode()).isEqualTo("1118");
        assertThat(((RuleOutcome.Rejeitado) out).ruleId()).isEqualTo("W34-10");
    }

    @Test
    void totalInformedWithAtLeastOneItemHavingTheWrapperIsConforme() {
        // Basta um item ter o invólucro entre vários — não precisa ser todos.
        assertThat(new TotalGroupForbiddenRule().evaluate(doc(true),
                List.of(item(false), item(true))))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void totalAbsentIsNotThe1118Territory() {
        // O caso "total ausente" é da 1119, não desta regra.
        assertThat(new TotalGroupForbiddenRule().evaluate(doc(false), List.of(item(false))))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void totalInformedWithNoItemsAtAllIsRejected() {
        // Vacuamente "nenhum item possui o invólucro" — documento sem item nenhum e total
        // informado não tem o que sustentar a presença do total.
        assertThat(new TotalGroupForbiddenRule().evaluate(doc(true), List.of()))
                .isInstanceOf(RuleOutcome.Rejeitado.class);
    }

    // ---- 1119 (W34-20): total ausente, algum item com o invólucro ----

    @Test
    void totalAbsentWithAnItemHavingTheWrapperIsRejected() {
        var out = new TotalGroupRequiredRule().evaluate(doc(false), List.of(item(true)));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).rejectionCode()).isEqualTo("1119");
        assertThat(((RuleOutcome.Rejeitado) out).ruleId()).isEqualTo("W34-20");
    }

    @Test
    void totalAbsentWithNoItemHavingTheWrapperIsConforme() {
        assertThat(new TotalGroupRequiredRule().evaluate(doc(false), List.of(item(false))))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void totalAbsentWithNoItemsAtAllIsConforme() {
        // Vacuamente coerente: sem item nenhum, "nenhum item tem o invólucro" é verdade.
        assertThat(new TotalGroupRequiredRule().evaluate(doc(false), List.of()))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void totalInformedIsNotThe1119Territory() {
        // O caso "total informado" é da 1118, não desta regra.
        assertThat(new TotalGroupRequiredRule().evaluate(doc(true), List.of(item(true))))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }
}
