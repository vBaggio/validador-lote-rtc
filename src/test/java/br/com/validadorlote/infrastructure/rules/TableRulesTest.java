package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regras dirigidas pelos metadados da tabela oficial CST × cClassTrib.
 *
 * <p>Todos os códigos usados aqui são reais da base embarcada
 * ({@code src/main/resources/tables/cst-cclasstrib.json}), conferidos antes: {@code 000/000001}
 * (NF-e e NFC-e), {@code 000/000003} (só NF-e), {@code 011/011001} (CST que exige redução, 60%
 * nas duas esferas) e {@code 200/200025} (IBS 60%, CBS 100% — o par que separa as esferas).
 */
class TableRulesTest {

    private static FiscalTables tables;
    private static final LocalDate DATA = LocalDate.of(2026, 8, 3);

    @BeforeAll
    static void load() {
        tables = FiscalTables.load();
    }

    /**
     * Construtor nomeado de {@link ItemTaxGroup}: são 13 campos posicionais e a ordem já mudou
     * mais de uma vez (Task 6, e o brief de DFeReferenciado). Construção posicional errada
     * compila e mente.
     */
    private static final class Item {
        private boolean involucro = true;
        private boolean grupoInterno = true;
        private String cst;
        private String classTrib;
        private boolean redUf;
        private boolean redMun;
        private boolean redCbs;
        private BigDecimal pUf;
        private BigDecimal pMun;
        private BigDecimal pCbs;

        Item cst(String v) { this.cst = v; return this; }

        Item classTrib(String v) { this.classTrib = v; return this; }

        Item semInvolucro() { this.involucro = false; this.grupoInterno = false; return this; }

        Item semGrupoInterno() { this.grupoInterno = false; return this; }

        Item reducaoUf(String perc) { this.redUf = true; this.pUf = decimal(perc); return this; }

        Item reducaoMun(String perc) { this.redMun = true; this.pMun = decimal(perc); return this; }

        Item reducaoCbs(String perc) { this.redCbs = true; this.pCbs = decimal(perc); return this; }

        private static BigDecimal decimal(String v) {
            return v == null ? null : new BigDecimal(v);
        }

        ItemTaxGroup build() {
            return new ItemTaxGroup(1, involucro, grupoInterno, cst, classTrib, null,
                    redUf, redMun, redCbs, pUf, pMun, pCbs, null);
        }
    }

    private static Item item() {
        return new Item();
    }

    private FiscalDocument doc(String modelo, LocalDate data, boolean compraGov) {
        return new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                data, modelo, "NFe", "3", null, null, compraGov, List.of());
    }

    private RuleContext ctx(Item item) {
        return ctx("55", DATA, false, item);
    }

    private RuleContext ctx(String modelo, LocalDate data, boolean compraGov, Item item) {
        return new RuleContext(doc(modelo, data, compraGov), item.build(), tables, data);
    }

    // ---- 1024 (UB14-20): cClassTrib compatível com o CST ----

    @Test
    void classTribBelongingToTheInformedCstIsConforme() {
        assertThat(new ClassTribCstRule().evaluate(ctx(item().cst("000").classTrib("000001"))))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void classTribFromAnotherCstIsRejected() {
        // 011001 pertence ao CST 011 (planos de assistência funerária), não ao 000.
        var out = new ClassTribCstRule().evaluate(ctx(item().cst("000").classTrib("011001")));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        var rejeitado = (RuleOutcome.Rejeitado) out;
        assertThat(rejeitado.rejectionCode()).isEqualTo("1024");
        assertThat(rejeitado.ruleId()).isEqualTo("UB14-20");
        assertThat(rejeitado.officialMessage()).isEqualTo(
                "Rejeição: Rejeição: Classificação Tributária do IBS e da CBS "
                        + "incompatível com o CST informado");
        assertThat(rejeitado.friendlyMessage())
                .contains("011001").contains("011").contains("000");
    }

    @Test
    void itemWithoutClassTribIsNotApplicableTo1024() {
        // A UB14-20 é condicionada a "se cClassTrib for informado".
        assertThat(new ClassTribCstRule().evaluate(ctx(item().cst("000"))))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void itemWithoutCstIsNotEvaluatedBy1024() {
        // Sem CST não há com o que comparar — e comparar com nada seria acusar por chute.
        assertThat(new ClassTribCstRule().evaluate(ctx(item().classTrib("000001"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void unknownClassTribIsNotEvaluatedBy1024() {
        assertThat(new ClassTribCstRule().evaluate(ctx(item().cst("000").classTrib("999999"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void missingIssueDateIsNotEvaluatedBy1024() {
        // Consultar a tabela com data nula estoura NPE na vigência e derruba o arquivo do lote.
        assertThat(new ClassTribCstRule().evaluate(
                ctx("55", null, false, item().cst("000").classTrib("000001"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    // ---- 1025 (UB14-25): cClassTrib permitida no modelo ----

    @Test
    void classTribAllowedInModelIsConforme() {
        assertThat(new ClassTribModelRule().evaluate(ctx(item().cst("000").classTrib("000001"))))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void classTribForbiddenInTheModelIsRejected() {
        // 000003 (regime automotivo) tem indNFe=1 e indNFCe=0: numa NFC-e é rejeição.
        var out = new ClassTribModelRule().evaluate(
                ctx("65", DATA, false, item().cst("000").classTrib("000003")));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        var rejeitado = (RuleOutcome.Rejeitado) out;
        assertThat(rejeitado.rejectionCode()).isEqualTo("1025");
        assertThat(rejeitado.ruleId()).isEqualTo("UB14-25");
    }

    @Test
    void theSameClassTribIsAllowedInTheOtherModel() {
        // O par do teste acima: sem ele a regra poderia estar sempre acusando.
        assertThat(new ClassTribModelRule().evaluate(
                ctx("55", DATA, false, item().cst("000").classTrib("000003"))))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void unknownClassTribIsNotEvaluated() {
        // Código publicado depois da nossa extração: culpa da base, não do emitente.
        assertThat(new ClassTribModelRule().evaluate(ctx(item().cst("000").classTrib("999999"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void unknownModelIsNotEvaluatedInsteadOfFallingIntoTheNfeBranch() {
        // permiteModelo() é "65 ? nfce : nfe": qualquer modelo desconhecido cairia no ramo da
        // NF-e e seria julgado por um indicador que não é o dele.
        assertThat(new ClassTribModelRule().evaluate(
                ctx("57", DATA, false, item().cst("000").classTrib("000003"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void missingModelIsNotEvaluatedBy1025() {
        assertThat(new ClassTribModelRule().evaluate(
                ctx(null, DATA, false, item().cst("000").classTrib("000003"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void itemWithoutClassTribIsNotApplicableTo1025() {
        assertThat(new ClassTribModelRule().evaluate(ctx(item().cst("000"))))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void missingIssueDateIsNotEvaluatedBy1025() {
        assertThat(new ClassTribModelRule().evaluate(
                ctx("55", null, false, item().cst("000").classTrib("000001"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    // ---- 1033/1074/1079 (UB26-20/UB45-20/UB64-20): grupo de redução ausente ----

    private RuleOutcome grupo(Esfera esfera, RuleContext ctx) {
        return new ReductionGroupRule(esfera).evaluate(ctx);
    }

    @Test
    void cstRequiringReductionWithoutTheGroupIsRejected() {
        // CST 011 tem ind_gRed = 1.
        var out = grupo(Esfera.UF, ctx(item().cst("011").classTrib("011001")));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        var rejeitado = (RuleOutcome.Rejeitado) out;
        assertThat(rejeitado.rejectionCode()).isEqualTo("1033");
        assertThat(rejeitado.ruleId()).isEqualTo("UB26-20");
    }

    @Test
    void eachSphereHasItsOwnRejectionCode() {
        var semGrupo = item().cst("011").classTrib("011001");

        assertThat((RuleOutcome.Rejeitado) grupo(Esfera.MUNICIPIO, ctx(semGrupo)))
                .extracting(RuleOutcome.Rejeitado::rejectionCode, RuleOutcome.Rejeitado::ruleId)
                .containsExactly("1074", "UB45-20");
        assertThat((RuleOutcome.Rejeitado) grupo(Esfera.CBS, ctx(semGrupo)))
                .extracting(RuleOutcome.Rejeitado::rejectionCode, RuleOutcome.Rejeitado::ruleId)
                .containsExactly("1079", "UB64-20");
    }

    @Test
    void cstNotRequiringReductionIsNotApplicable() {
        // CST 000 tem ind_gRed = 0 — ausência do grupo é correta. Este é o teste que protege
        // contra o falso positivo em escala: se alguém trocar o indicador por CST pelo
        // possuiPercentualReducao da Calculadora (60 de 161, e não 3 de 18), este caso quebra.
        assertThat(grupo(Esfera.UF, ctx(item().cst("000").classTrib("000001"))))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void cstRequiringReductionWithTheGroupIsConforme() {
        assertThat(grupo(Esfera.UF, ctx(item().cst("011").classTrib("011001").reducaoUf("60.0"))))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void governmentPurchaseRequiresTheGroupEvenWhenTheCstDoesNot() {
        // Gatilho literal da UB26-20: "ou foi informado o grupo de compras governamentais".
        var out = grupo(Esfera.UF, ctx("55", DATA, true, item().cst("000").classTrib("000001")));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).rejectionCode()).isEqualTo("1033");
    }

    @Test
    void cstThatForbidsTheIbsCbsGroupIsExemptEvenUnderGovernmentPurchase() {
        // Exceção literal da UB26-20: não se aplica a CST com ind_gIBSCBS = 0 (aqui o 400).
        // O item traz o gIBSCBS presente de propósito: é XML implausível (o CST 400 o proíbe, e
        // a 1021 acusaria), mas é o único jeito de isolar a exceção. Com o grupo interno ausente
        // haveria dois caminhos para NaoAplicavel — a exceção e a delegação à 1022 — e apagar a
        // exceção do código deixaria este teste verde. A asserção sobre o motivo fecha a mesma
        // brecha por dentro.
        var out = grupo(Esfera.UF, ctx("55", DATA, true, item().cst("400").classTrib("400001")));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAplicavel.class);
        assertThat(((RuleOutcome.NaoAplicavel) out).motivo()).contains("ind_gIBSCBS");
    }

    @Test
    void itemWithoutTheWrapperBelongsTo1115NotToTheReductionRules() {
        assertThat(grupo(Esfera.UF, ctx(item().cst("011").classTrib("011001").semInvolucro())))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void itemWithoutTheInnerGroupBelongsTo1022NotToTheReductionRules() {
        // Sem gIBSCBS não há onde o gRed morar: quem fala é a 1022, e acusar as duas coisas
        // encheria o relatório de achados encadeados do mesmo defeito.
        assertThat(grupo(Esfera.UF,
                ctx(item().cst("011").classTrib("011001").semGrupoInterno())))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void itemWithoutCstIsNotEvaluatedByTheReductionGroupRule() {
        // Sem CST não há indicador ind_gRed que consultar — e o desfecho não pode ser acusação.
        assertThat(grupo(Esfera.UF, ctx(item().classTrib("011001"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void unknownCstIsNotEvaluatedByTheReductionGroupRule() {
        assertThat(grupo(Esfera.UF, ctx(item().cst("999").classTrib("000001"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void missingIssueDateIsNotEvaluatedByTheReductionGroupRule() {
        assertThat(grupo(Esfera.UF, ctx("55", null, false, item().cst("011").classTrib("011001"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    // ---- 1034/1046/1063 (UB27-10/UB46-10/UB65-10): percentual de redução divergente ----

    private RuleOutcome percentual(Esfera esfera, RuleContext ctx) {
        return new ReductionPercentageRule(esfera).evaluate(ctx);
    }

    @Test
    void declaredPercentageMatchingTheOfficialIsConforme() {
        assertThat(percentual(Esfera.UF,
                ctx(item().cst("011").classTrib("011001").reducaoUf("60.0"))))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void declaredPercentageDivergingFromTheOfficialIsRejected() {
        var out = percentual(Esfera.UF,
                ctx(item().cst("011").classTrib("011001").reducaoUf("40.0")));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        var rejeitado = (RuleOutcome.Rejeitado) out;
        assertThat(rejeitado.rejectionCode()).isEqualTo("1034");
        assertThat(rejeitado.ruleId()).isEqualTo("UB27-10");
    }

    @Test
    void eachSphereHasItsOwnPercentageRejectionCode() {
        var item = item().cst("011").classTrib("011001")
                .reducaoMun("40.0").reducaoCbs("40.0");

        assertThat((RuleOutcome.Rejeitado) percentual(Esfera.MUNICIPIO, ctx(item)))
                .extracting(RuleOutcome.Rejeitado::rejectionCode, RuleOutcome.Rejeitado::ruleId)
                .containsExactly("1046", "UB46-10");
        assertThat((RuleOutcome.Rejeitado) percentual(Esfera.CBS, ctx(item)))
                .extracting(RuleOutcome.Rejeitado::rejectionCode, RuleOutcome.Rejeitado::ruleId)
                .containsExactly("1063", "UB65-10");
    }

    @Test
    void cbsIsComparedAgainstItsOwnOfficialPercentage() {
        // 200025 (Prouni) é o caso que separa as esferas: IBS 60%, CBS 100%. Comparar a CBS
        // contra o percentual do IBS acusaria uma nota correta — e vice-versa.
        var item = item().cst("200").classTrib("200025")
                .reducaoUf("60.0").reducaoMun("60.0").reducaoCbs("100.0");

        assertThat(percentual(Esfera.UF, ctx(item))).isInstanceOf(RuleOutcome.Conforme.class);
        assertThat(percentual(Esfera.MUNICIPIO, ctx(item)))
                .isInstanceOf(RuleOutcome.Conforme.class);
        assertThat(percentual(Esfera.CBS, ctx(item))).isInstanceOf(RuleOutcome.Conforme.class);
        assertThat(percentual(Esfera.CBS,
                ctx(item().cst("200").classTrib("200025").reducaoCbs("60.0"))))
                .isInstanceOf(RuleOutcome.Rejeitado.class);
    }

    @Test
    void scaleDoesNotChangeTheVerdict() {
        // 60.00 e 60.0 são o mesmo percentual: equals() diria que não, compareTo() diz que sim.
        assertThat(percentual(Esfera.UF,
                ctx(item().cst("011").classTrib("011001").reducaoUf("60.00"))))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void withoutTheReductionGroupThePercentageRuleIsNotApplicable() {
        // O gatilho da UB27-10 é o grupo gRed informado, não a existência do percentual.
        assertThat(percentual(Esfera.UF, ctx(item().cst("011").classTrib("011001"))))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void governmentPurchaseIsNotEvaluatedByThePercentageRule() {
        // Sob compra governamental o pRedAliq esperado é zero, não o da tabela (D-030).
        // Sem este desvio, uma nota legítima com pRedAliq=0 seria acusada contra os 60%.
        var out = percentual(Esfera.UF,
                ctx("55", DATA, true, item().cst("011").classTrib("011001").reducaoUf("0")));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAvaliado.class);
        assertThat(((RuleOutcome.NaoAvaliado) out).motivo()).contains("gCompraGov");
    }

    @Test
    void cstThatForbidsReductionIsNotEvaluatedByThePercentageRule() {
        // Ramo ind_gRed = 0 da UB27-10: depende de gCompraGov/pRedutor, fora do escopo (D-030).
        assertThat(percentual(Esfera.UF,
                ctx(item().cst("000").classTrib("000001").reducaoUf("10.0"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void itemWithoutCstIsNotEvaluatedByThePercentageRule() {
        // O CST decide qual dos dois ramos da UB27-10 vale; sem ele, nenhum.
        assertThat(percentual(Esfera.UF, ctx(item().classTrib("011001").reducaoUf("60.0"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void unknownClassTribIsNotEvaluatedByThePercentageRule() {
        assertThat(percentual(Esfera.UF,
                ctx(item().cst("011").classTrib("999999").reducaoUf("60.0"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void unreadablePercentageIsNotEvaluatedInsteadOfRejected() {
        // gRed informado mas pRedAliq ilegível (conteúdo misto): o XSD reporta o erro real.
        assertThat(percentual(Esfera.UF,
                ctx(item().cst("011").classTrib("011001").reducaoUf(null))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void missingIssueDateIsNotEvaluatedByThePercentageRule() {
        assertThat(percentual(Esfera.UF,
                ctx("55", null, false, item().cst("011").classTrib("011001").reducaoUf("60.0"))))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }
}
