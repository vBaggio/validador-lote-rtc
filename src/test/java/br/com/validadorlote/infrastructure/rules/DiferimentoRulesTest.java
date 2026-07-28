package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mecanismo 1 do bloco 7 (brief {@code task-presenca-indicador-modelo}): rejeições 1029, 1030,
 * 1044, 1061, 1083 e 1090 — grupo de diferimento ({@code gDif}) exigido ou vedado conforme o
 * indicador {@code ind_gDif} do CST (agora {@code CstEntry.exigeDiferimento}).
 *
 * <p>Códigos reais da base embarcada: {@code 510} (Diferimento, {@code ind_gDif = 1} — só ele e o
 * {@code 515} têm o indicador verdadeiro) e {@code 000} (Tributação integral, {@code ind_gDif =
 * 0}, como os outros 16 CSTs).
 */
class DiferimentoRulesTest {

    private static FiscalTables tables;
    private static final LocalDate DATA = LocalDate.of(2026, 8, 3);
    private static final String CST_EXIGE_DIFERIMENTO = "510";
    private static final String CST_VEDA_DIFERIMENTO = "000";

    @BeforeAll
    static void load() { tables = FiscalTables.load(); }

    private static final class Item {
        private boolean involucro = true;
        private boolean grupoInterno = true;
        private String cst;
        private boolean difUf;
        private boolean difMun;
        private boolean difCbs;

        Item cst(String v) { this.cst = v; return this; }

        Item semInvolucro() { this.involucro = false; this.grupoInterno = false; return this; }

        Item semGrupoInterno() { this.grupoInterno = false; return this; }

        Item difUf() { this.difUf = true; return this; }

        Item difMun() { this.difMun = true; return this; }

        Item difCbs() { this.difCbs = true; return this; }

        ItemTaxGroup build() {
            return new ItemTaxGroup(1, involucro, grupoInterno, cst, null, null,
                    false, false, false, null, null, null, null,
                    difUf, difMun, difCbs, false, false, false, false, false, false, false);
        }
    }

    private static Item item() {
        return new Item();
    }

    private FiscalDocument doc(LocalDate data) {
        return new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                data, "55", "NFe", "3", null, null, false, false, List.of());
    }

    private RuleContext ctx(Item item) {
        return new RuleContext(doc(DATA), item.build(), tables, DATA);
    }

    private RuleContext ctx(LocalDate data, Item item) {
        return new RuleContext(doc(data), item.build(), tables, data);
    }

    // ---- 1029/1083/1090 (UB22-10/UB40-20/UB59-20): diferimento vedado ----

    @Test
    void forbiddenGroupInformedWhenCstDoesNotAllowIsRejected() {
        var out = new DiferimentoForbiddenRule(Esfera.UF)
                .evaluate(ctx(item().cst(CST_VEDA_DIFERIMENTO).difUf()));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        var rejeitado = (RuleOutcome.Rejeitado) out;
        assertThat(rejeitado.rejectionCode()).isEqualTo("1029");
        assertThat(rejeitado.ruleId()).isEqualTo("UB22-10");
        assertThat(rejeitado.officialMessage()).isEqualTo("Rejeição: CST do IBS/CBS informado "
                + "não permite informação de diferimento Estadual");
    }

    @Test
    void eachSphereHasItsOwnForbiddenRejectionCode() {
        assertThat((RuleOutcome.Rejeitado) new DiferimentoForbiddenRule(Esfera.MUNICIPIO)
                        .evaluate(ctx(item().cst(CST_VEDA_DIFERIMENTO).difMun())))
                .extracting(RuleOutcome.Rejeitado::rejectionCode, RuleOutcome.Rejeitado::ruleId)
                .containsExactly("1083", "UB40-20");
        assertThat((RuleOutcome.Rejeitado) new DiferimentoForbiddenRule(Esfera.CBS)
                        .evaluate(ctx(item().cst(CST_VEDA_DIFERIMENTO).difCbs())))
                .extracting(RuleOutcome.Rejeitado::rejectionCode, RuleOutcome.Rejeitado::ruleId)
                .containsExactly("1090", "UB59-20");
    }

    @Test
    void absenceOfTheForbiddenGroupIsConforme() {
        // O par obrigatório do teste acima: sem o gDif, a vedação foi respeitada.
        assertThat(new DiferimentoForbiddenRule(Esfera.UF)
                .evaluate(ctx(item().cst(CST_VEDA_DIFERIMENTO))))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void cstThatRequiresDeferralIsNotThe1029Territory() {
        // CST 510 exige o grupo (ind_gDif = 1): quem fala sobre a ausência é a 1030, não esta.
        assertThat(new DiferimentoForbiddenRule(Esfera.UF)
                .evaluate(ctx(item().cst(CST_EXIGE_DIFERIMENTO).difUf())))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void itemWithoutTheWrapperBelongsTo1115NotToTheForbiddenDeferralRule() {
        assertThat(new DiferimentoForbiddenRule(Esfera.UF).evaluate(ctx(item().semInvolucro())))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void itemWithoutCstIsNotEvaluatedByTheForbiddenDeferralRule() {
        assertThat(new DiferimentoForbiddenRule(Esfera.UF).evaluate(ctx(item())))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void unknownCstIsNotEvaluatedByTheForbiddenDeferralRule() {
        assertThat(new DiferimentoForbiddenRule(Esfera.UF).evaluate(ctx(item().cst("999"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void missingIssueDateIsNotEvaluatedByTheForbiddenDeferralRule() {
        assertThat(new DiferimentoForbiddenRule(Esfera.UF)
                .evaluate(ctx(null, item().cst(CST_VEDA_DIFERIMENTO))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    // ---- 1030/1044/1061 (UB22-20/UB40-10/UB59-10): diferimento exigido ----

    @Test
    void requiredGroupMissingWhenCstRequiresIsRejected() {
        var out = new DiferimentoRequiredRule(Esfera.UF)
                .evaluate(ctx(item().cst(CST_EXIGE_DIFERIMENTO)));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        var rejeitado = (RuleOutcome.Rejeitado) out;
        assertThat(rejeitado.rejectionCode()).isEqualTo("1030");
        assertThat(rejeitado.ruleId()).isEqualTo("UB22-20");
        assertThat(rejeitado.officialMessage()).isEqualTo("Rejeição: CST do IBS/CBS informado "
                + "obriga informação de diferimento Estadual");
    }

    @Test
    void eachSphereHasItsOwnRequiredRejectionCode() {
        assertThat((RuleOutcome.Rejeitado) new DiferimentoRequiredRule(Esfera.MUNICIPIO)
                        .evaluate(ctx(item().cst(CST_EXIGE_DIFERIMENTO))))
                .extracting(RuleOutcome.Rejeitado::rejectionCode, RuleOutcome.Rejeitado::ruleId)
                .containsExactly("1044", "UB40-10");
        assertThat((RuleOutcome.Rejeitado) new DiferimentoRequiredRule(Esfera.CBS)
                        .evaluate(ctx(item().cst(CST_EXIGE_DIFERIMENTO))))
                .extracting(RuleOutcome.Rejeitado::rejectionCode, RuleOutcome.Rejeitado::ruleId)
                .containsExactly("1061", "UB59-10");
    }

    @Test
    void requiredGroupPresentIsConforme() {
        assertThat(new DiferimentoRequiredRule(Esfera.UF)
                .evaluate(ctx(item().cst(CST_EXIGE_DIFERIMENTO).difUf())))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void cstThatForbidsDeferralIsNotThe1030Territory() {
        assertThat(new DiferimentoRequiredRule(Esfera.UF)
                .evaluate(ctx(item().cst(CST_VEDA_DIFERIMENTO))))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void itemWithoutTheInnerGroupBelongsTo1022NotToTheRequiredDeferralRule() {
        // Sem gIBSCBS não há onde o gDif morar: quem fala é a 1022, não esta — mesmo desvio de
        // ReductionGroupRule para gRed.
        var out = new DiferimentoRequiredRule(Esfera.UF)
                .evaluate(ctx(item().cst(CST_EXIGE_DIFERIMENTO).semGrupoInterno()));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAplicavel.class);
        assertThat(((RuleOutcome.NaoAplicavel) out).motivo()).contains("1022");
    }

    @Test
    void itemWithoutTheWrapperBelongsTo1115NotToTheRequiredDeferralRule() {
        assertThat(new DiferimentoRequiredRule(Esfera.UF).evaluate(ctx(item().semInvolucro())))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void itemWithoutCstIsNotEvaluatedByTheRequiredDeferralRule() {
        assertThat(new DiferimentoRequiredRule(Esfera.UF).evaluate(ctx(item())))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void unknownCstIsNotEvaluatedByTheRequiredDeferralRule() {
        assertThat(new DiferimentoRequiredRule(Esfera.UF).evaluate(ctx(item().cst("999"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void missingIssueDateIsNotEvaluatedByTheRequiredDeferralRule() {
        assertThat(new DiferimentoRequiredRule(Esfera.UF)
                .evaluate(ctx(null, item().cst(CST_EXIGE_DIFERIMENTO))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }
}
