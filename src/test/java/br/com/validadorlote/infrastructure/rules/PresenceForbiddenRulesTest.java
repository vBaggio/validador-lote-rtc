package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.domain.ReferencedNote;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mecanismos 3 e 5 do bloco 7: sete rejeições de "grupo/tag informado indevidamente" sem
 * indicador de CST — {@link PresenceForbiddenRule}, a classe genérica de
 * {@link RuleEngine#PRESENCE_FORBIDDEN_RULES}. Cada instância é exercitada aqui pela combinação
 * exata registrada no motor, para que um erro de fiação (código trocado, presença errada, modelo
 * errado) apareça neste arquivo, e não só numa fixture ponta a ponta.
 */
class PresenceForbiddenRulesTest {

    private static final LocalDate DATA = LocalDate.of(2026, 8, 3);

    private PresenceForbiddenRule rule(String rejectionCode) {
        return RuleEngine.PRESENCE_FORBIDDEN_RULES.stream()
                .filter(r -> r.rejectionCode().equals(rejectionCode))
                .map(PresenceForbiddenRule.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Sem regra para " + rejectionCode));
    }

    private FiscalDocument doc(String modelo) {
        return new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                DATA, modelo, "NFe", "3", null, null, false, false, List.of());
    }

    private ItemTaxGroup item(boolean devTribUf, boolean devTribMun, boolean devTribCbs,
            boolean credPresOper, boolean credPresIbsZfm, boolean tpCredPresIbsZfm,
            ReferencedNote dfeReferenciado) {
        return new ItemTaxGroup(1, true, true, "000", "000001", null, false, false, false,
                null, null, null, dfeReferenciado, false, false, false,
                devTribUf, devTribMun, devTribCbs, credPresOper, credPresIbsZfm,
                tpCredPresIbsZfm, false);
    }

    private ItemTaxGroup itemLimpo() {
        return item(false, false, false, false, false, false, null);
    }

    private RuleContext ctx(String modelo, ItemTaxGroup item) {
        return new RuleContext(doc(modelo), item, null, DATA);
    }

    // ---- 1111 (UB24-10): gIBSUF/gDevTrib, qualquer modelo ----

    @Test
    void devTribUfInformedIsRejectedRegardlessOfModel() {
        var out = rule("1111").evaluate(
                ctx("55", item(true, false, false, false, false, false, null)));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        var rejeitado = (RuleOutcome.Rejeitado) out;
        assertThat(rejeitado.ruleId()).isEqualTo("UB24-10");
        assertThat(rejeitado.officialMessage()).isEqualTo(
                "Rejeição: Grupo de Devolução do IBS da UF informado indevidamente");
        assertThat(rule("1111").evaluate(ctx("65", item(true, false, false, false, false, false, null))))
                .isInstanceOf(RuleOutcome.Rejeitado.class);
    }

    @Test
    void withoutDevTribUfIsConforme() {
        assertThat(rule("1111").evaluate(ctx("55", itemLimpo())))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    // ---- 1112 (UB43-10): gIBSMun/gDevTrib, qualquer modelo ----

    @Test
    void devTribMunInformedIsRejectedRegardlessOfModel() {
        var out = rule("1112").evaluate(
                ctx("65", item(false, true, false, false, false, false, null)));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).ruleId()).isEqualTo("UB43-10");
    }

    // ---- 1187 (UB62-10): gCBS/gDevTrib, só modelo 65 ----

    @Test
    void devTribCbsInformedInNfceIsRejected() {
        var out = rule("1187").evaluate(
                ctx("65", item(false, false, true, false, false, false, null)));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).ruleId()).isEqualTo("UB62-10");
    }

    @Test
    void devTribCbsInformedInNfeIsNotApplicable() {
        // 1187 é exclusiva do modelo 65 — a mesma presença em NF-e não é território dela.
        assertThat(rule("1187").evaluate(ctx("55", item(false, false, true, false, false, false, null))))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void missingModelIsNotEvaluatedForAModelRestrictedRule() {
        assertThat(rule("1187").evaluate(ctx(null, item(false, false, true, false, false, false, null))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    // ---- 1049 (UB120-10): gCredPresOper, só modelo 65 ----

    @Test
    void credPresOperInNfceIsRejected() {
        var out = rule("1049").evaluate(
                ctx("65", item(false, false, false, true, false, false, null)));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        var rejeitado = (RuleOutcome.Rejeitado) out;
        assertThat(rejeitado.ruleId()).isEqualTo("UB120-10");
        assertThat(rejeitado.officialMessage()).isEqualTo(
                "Rejeição: Não é permitido o uso de Crédito Presumido na NFC-e modelo 65");
    }

    @Test
    void credPresOperInNfeIsNotApplicable() {
        assertThat(rule("1049").evaluate(ctx("55", item(false, false, false, true, false, false, null))))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    // ---- 1138 (UB131-10): gCredPresIBSZFM, só modelo 65 ----

    @Test
    void credPresIbsZfmInNfceIsRejected() {
        var out = rule("1138").evaluate(
                ctx("65", item(false, false, false, false, true, false, null)));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).ruleId()).isEqualTo("UB131-10");
    }

    // ---- 1165 (I05k-10): tpCredPresIBSZFM, só modelo 65 — booleano próprio, não herda o de 1138 ----

    @Test
    void tpCredPresIbsZfmInNfceIsRejected() {
        var out = rule("1165").evaluate(
                ctx("65", item(false, false, false, false, false, true, null)));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).ruleId()).isEqualTo("I05k-10");
    }

    @Test
    void credPresIbsZfmGroupWithoutTheFieldDoesNotTrigger1165() {
        // gCredPresIBSZFM presente mas tpCredPresIBSZFM (ilegível ou ausente) não: 1165 não
        // presume o campo a partir do grupo — são capturas independentes por decisão do brief.
        assertThat(rule("1165").evaluate(ctx("65", item(false, false, false, false, true, false, null))))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void tpCredPresIbsZfmFieldWithoutTheGroupStillTriggers1165() {
        // O inverso: o campo por si só (sem o boolean de grupo aceso) já é o gatilho da 1165 —
        // reforça que os dois não são o mesmo boolean reaproveitado.
        assertThat(rule("1165").evaluate(ctx("65", item(false, false, false, false, false, true, null))))
                .isInstanceOf(RuleOutcome.Rejeitado.class);
    }

    // ---- 708 (VC02-04): DFeReferenciado no item, só modelo 65 ----

    @Test
    void itemReferencingAnotherDocumentInNfceIsRejected() {
        var referencia = new ReferencedNote("DFeReferenciado", YearMonth.of(2026, 7));
        var out = rule("708").evaluate(
                ctx("65", item(false, false, false, false, false, false, referencia)));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        var rejeitado = (RuleOutcome.Rejeitado) out;
        assertThat(rejeitado.ruleId()).isEqualTo("VC02-04");
        assertThat(rejeitado.officialMessage())
                .isEqualTo("Rejeição: NFC-e não pode referenciar documento fiscal");
    }

    @Test
    void itemReferencingAnotherDocumentWithUndecodableKeyIsStillRejected() {
        // dfeReferenciado() não nulo é presença do grupo, mesmo com chave ilegível (mesma leitura
        // que a Exceção 1 da 1115 já usa) — 708 é sobre presença, não sobre decodificar a chave.
        var referencia = new ReferencedNote("DFeReferenciado", null);
        assertThat(rule("708").evaluate(ctx("65", item(false, false, false, false, false, false, referencia))))
                .isInstanceOf(RuleOutcome.Rejeitado.class);
    }

    @Test
    void itemReferencingAnotherDocumentInNfeIsNotApplicable() {
        var referencia = new ReferencedNote("DFeReferenciado", YearMonth.of(2026, 7));
        assertThat(rule("708").evaluate(ctx("55", item(false, false, false, false, false, false, referencia))))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    // ---- 1006 (B31-10): gCompraGov no documento, só modelo 65 — regra de documento ----

    @Test
    void compraGovInNfceIsRejected() {
        var documento = new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                DATA, "65", "NFe", "3", null, null, true, false, List.of());
        var out = new CompraGovForbiddenInNfceRule().evaluate(documento, List.of());

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        var rejeitado = (RuleOutcome.Rejeitado) out;
        assertThat(rejeitado.rejectionCode()).isEqualTo("1006");
        assertThat(rejeitado.ruleId()).isEqualTo("B31-10");
        assertThat(rejeitado.officialMessage())
                .isEqualTo("Rejeição: NFCe com grupo de compra governamental");
    }

    @Test
    void compraGovInNfeIsNotApplicable() {
        var documento = new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                DATA, "55", "NFe", "3", null, null, true, false, List.of());
        assertThat(new CompraGovForbiddenInNfceRule().evaluate(documento, List.of()))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void nfceWithoutCompraGovIsConforme() {
        var documento = new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                DATA, "65", "NFe", "3", null, null, false, false, List.of());
        assertThat(new CompraGovForbiddenInNfceRule().evaluate(documento, List.of()))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void missingModelIsNotEvaluatedFor1006() {
        var documento = new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                DATA, null, "NFe", "3", null, null, true, false, List.of());
        assertThat(new CompraGovForbiddenInNfceRule().evaluate(documento, List.of()))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }
}
