package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.domain.NotEvaluatedCause;
import br.com.validadorlote.domain.ReferencedNote;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O motor e, sobretudo, a supressão em cascata (§4.4 do design).
 *
 * <p>Quase todo teste daqui afirma a <b>contagem</b> de achados, não só o tipo: um
 * {@code allSatisfy} sobre o tipo passa com sete duplicatas da mesma causa, que é exatamente o
 * defeito que a cascata existe para evitar.
 *
 * <p>Códigos reais da base embarcada, conferidos antes: {@code 000/000001} (CST que não exige
 * redução, classificação válida nos dois modelos) e {@code 011/011001} (CST que exige redução,
 * classificação vedada em NF-e e em NFC-e).
 */
class RuleEngineTest {

    private static FiscalTables tables;
    private static RuleEngine engine;
    private static final LocalDate DATA = LocalDate.of(2026, 8, 3);

    @BeforeAll
    static void load() {
        tables = FiscalTables.load();
        engine = new RuleEngine(tables);
    }

    /**
     * Construtor nomeado de {@link ItemTaxGroup}: são 13 campos posicionais e a ordem já mudou
     * mais de uma vez. Construção posicional errada compila e mente.
     */
    private static final class Item {
        private Integer numero = 1;
        private boolean involucro = true;
        private boolean grupoInterno = true;
        private String cst;
        private String classTrib;
        private boolean redUf;
        private boolean redMun;
        private boolean redCbs;
        private BigDecimal perc;
        private boolean difUf;
        private boolean difMun;
        private boolean difCbs;
        private boolean devTribUf;
        private boolean devTribMun;
        private boolean devTribCbs;
        private boolean credPresOper;
        private boolean credPresIbsZfm;
        private boolean tpCredPresIbsZfm;
        private boolean tribCompraGov;
        private ReferencedNote dfeReferenciado;

        Item numero(Integer v) { this.numero = v; return this; }

        Item cst(String v) { this.cst = v; return this; }

        Item classTrib(String v) { this.classTrib = v; return this; }

        Item semInvolucro() { this.involucro = false; this.grupoInterno = false; return this; }

        /** Grupo de redução nas três esferas, com o mesmo percentual declarado. */
        Item reducaoNasTresEsferas(String percentual) {
            this.redUf = this.redMun = this.redCbs = true;
            this.perc = new BigDecimal(percentual);
            return this;
        }

        Item difUf() { this.difUf = true; return this; }

        Item difMun() { this.difMun = true; return this; }

        Item difCbs() { this.difCbs = true; return this; }

        Item devTribUf() { this.devTribUf = true; return this; }

        Item devTribMun() { this.devTribMun = true; return this; }

        Item devTribCbs() { this.devTribCbs = true; return this; }

        Item credPresOper() { this.credPresOper = true; return this; }

        Item credPresIbsZfm() { this.credPresIbsZfm = true; return this; }

        Item tpCredPresIbsZfm() { this.tpCredPresIbsZfm = true; return this; }

        Item tribCompraGov() { this.tribCompraGov = true; return this; }

        Item dfeReferenciado(ReferencedNote v) { this.dfeReferenciado = v; return this; }

        ItemTaxGroup build() {
            return new ItemTaxGroup(numero, involucro, grupoInterno, cst, classTrib, null,
                    redUf, redMun, redCbs, perc, perc, perc, dfeReferenciado,
                    difUf, difMun, difCbs, devTribUf, devTribMun, devTribCbs,
                    credPresOper, credPresIbsZfm, tpCredPresIbsZfm, tribCompraGov);
        }
    }

    private static Item item() {
        return new Item();
    }

    private FiscalDocument doc(String crt) {
        return doc(crt, DATA, "55");
    }

    private FiscalDocument doc(String crt, LocalDate data, String modelo) {
        return doc(crt, data, modelo, false);
    }

    private FiscalDocument doc(String crt, LocalDate data, String modelo, boolean compraGov) {
        // hasIbsCbsTot=true por padrão: a esmagadora maioria dos testes deste arquivo usa item()
        // com o invólucro presente (padrão de Item), e a 1119 (item com grupo, total ausente)
        // acusaria um documento com total=false — ruído que nenhum destes testes quer. Os poucos
        // testes cujos itens não têm invólucro algum usam docSemTotal, que mantém as duas
        // presenças coerentemente ausentes.
        return doc(crt, data, modelo, compraGov, true);
    }

    private FiscalDocument doc(String crt, LocalDate data, String modelo, boolean compraGov,
            boolean hasIbsCbsTot) {
        return new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                data, modelo, "NFe", crt, null, null, compraGov, hasIbsCbsTot, List.of());
    }

    /**
     * Documento em que nenhum item tem o invólucro IBSCBS: o total também precisa estar ausente,
     * senão a 1118 (nova, coerência item×total — D-039) entraria em cena e o teste deixaria de
     * isolar o que quer isolar (a cascata por item).
     */
    private FiscalDocument docSemTotal(String crt) {
        return doc(crt, DATA, "55", false, false);
    }

    private List<Finding> achados(FiscalDocument documento, Item... itens) {
        return engine.evaluate(documento, List.of(itens).stream().map(Item::build).toList())
                .findings();
    }

    // ---- Nível 1 da cascata: o invólucro IBSCBS ausente suprime todas as regras do item ----

    @Test
    void itemWithoutTheWrapperProducesExactlyOneFinding() {
        // Sem o invólucro, as dez regras restantes seriam não aplicáveis pelo mesmo motivo.
        assertThat(achados(docSemTotal("3"), item().semInvolucro()))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.kind()).isEqualTo(FindingKind.REJECTION_RULE);
                    assertThat(f.rejectionCode()).isEqualTo("1115");
                    assertThat(f.ruleId()).isEqualTo("UB12-10");
                    assertThat(f.itemNumber()).isEqualTo(1);
                });
    }

    @Test
    void missingIssueDateProducesExactlyOneFindingPerItem() {
        // Toda regra consulta a tabela pela data do fato gerador: sem ela, nada é avaliável e a
        // causa é uma só. Onze achados dizendo "faltou a data" não seriam acionáveis. Dois itens
        // de propósito: com um só, "por item" seria promessa não verificada.
        var achados = achados(doc("3", null, "55"),
                item().numero(1).cst("000").classTrib("000001"),
                item().numero(2).cst("000").classTrib("000001"));

        assertThat(achados).hasSize(2);
        assertThat(achados).extracting(Finding::itemNumber).containsExactly(1, 2);
        assertThat(achados).allSatisfy(f -> {
            assertThat(f.kind()).isEqualTo(FindingKind.NOT_EVALUATED);
            // A recusa é da própria 1115, que identifica a si mesma no achado.
            assertThat(f.notEvaluatedCause()).isEqualTo(NotEvaluatedCause.RULE_SPECIFIC);
            assertThat(f.ruleId()).isEqualTo("UB12-10");
        });
    }

    // ---- Nível 3 da cascata: CST ausente ou fora da base ----

    @Test
    void absentCstProducesExactlyOneNotEvaluatedFinding() {
        // 1021, 1022, 1024, as três de grupo de redução e as três de percentual devolveriam
        // "CST não informado" cada uma.
        assertThat(achados(doc("3"), item()))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.kind()).isEqualTo(FindingKind.NOT_EVALUATED);
                    assertThat(f.notEvaluatedCause())
                            .isEqualTo(NotEvaluatedCause.CST_NOT_INFORMED);
                });
    }

    @Test
    void cstOutsideTheEmbeddedTableProducesExactlyOneNotEvaluatedFinding() {
        assertThat(achados(doc("3"), item().cst("999")))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.kind()).isEqualTo(FindingKind.NOT_EVALUATED);
                    assertThat(f.notEvaluatedCause()).isEqualTo(NotEvaluatedCause.CST_NOT_IN_TABLE);
                    assertThat(f.friendlyMessage()).contains("999");
                });
    }

    @Test
    void notEvaluatedFindingsCanBeAggregatedWithoutReadingText() {
        // O que a Task 9 precisa fazer: "não avaliei 3 itens — 2 por CST fora da base, 1 por
        // classificação" sem casar substring de motivo nenhum.
        var achados = achados(doc("3"),
                item().numero(1).cst("999"),
                item().numero(2).cst("998"),
                item().numero(3).cst("000").classTrib("999999"));

        assertThat(achados).extracting(Finding::notEvaluatedCause)
                .containsExactly(NotEvaluatedCause.CST_NOT_IN_TABLE,
                        NotEvaluatedCause.CST_NOT_IN_TABLE,
                        NotEvaluatedCause.CLASS_TRIB_UNAVAILABLE);
    }

    @Test
    void cstOutsideTheTableDoesNotSuppressTheClassTribRules() {
        // A 1024 compara a classificação com o CST que a tabela publica para ela — julgável
        // mesmo com o CST do item fora da base, e por isso fora do nível 3 da cascata.
        // 000001 pertence ao CST 000; o item declarou 999.
        var achados = achados(doc("3"), item().cst("999").classTrib("000001"));

        assertThat(achados).hasSize(2);
        assertThat(achados).filteredOn(f -> f.kind() == FindingKind.NOT_EVALUATED).hasSize(1);
        assertThat(achados).filteredOn(f -> f.kind() == FindingKind.REJECTION_RULE)
                .singleElement()
                .satisfies(f -> assertThat(f.rejectionCode()).isEqualTo("1024"));
    }

    // ---- Nível 2 da cascata: cClassTrib ausente ou fora da base ----

    @Test
    void classTribOutsideTheEmbeddedTableProducesExactlyOneNotEvaluatedFinding() {
        // 1024, 1025 e as três de percentual dependem da classificação na tabela.
        assertThat(achados(doc("3"), item().cst("000").classTrib("999999")))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.kind()).isEqualTo(FindingKind.NOT_EVALUATED);
                    assertThat(f.notEvaluatedCause())
                            .isEqualTo(NotEvaluatedCause.CLASS_TRIB_UNAVAILABLE);
                    assertThat(f.friendlyMessage()).contains("999999");
                });
    }

    @Test
    void absentClassTribProducesExactlyOneNotEvaluatedFinding() {
        // CST 011 exige o grupo de redução e o item o informou nas três esferas: as três regras
        // de percentual ficam sem o percentual oficial contra o qual comparar, pela mesma causa.
        var achados = achados(doc("3"), item().cst("011").reducaoNasTresEsferas("60.0"));

        assertThat(achados).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(FindingKind.NOT_EVALUATED);
            assertThat(f.friendlyMessage()).contains("cClassTrib");
        });
    }

    @Test
    void absentClassTribIsNeverTurnedIntoARejection() {
        // Nenhuma regra deste conjunto acusa a falta da cClassTrib — quem cobra a tag é o XSD.
        assertThat(achados(doc("3"), item().cst("000"))).isEmpty();
    }

    @Test
    void rejection1024KeepsOfficialMessageSeparateFromLocalDetail() {
        assertThat(achados(doc("3"), item().cst("000").classTrib("011001")))
                .filteredOn(f -> "1024".equals(f.rejectionCode()))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.officialMessage()).startsWith("Rejeição: Rejeição:");
                    assertThat(f.friendlyMessage()).contains("011001").contains("CST 011");
                });
    }

    // ---- A cascata não pode engolir causa distinta ----

    @Test
    void distinctCausesInTheSameItemAreAllReported() {
        // CST 011 exige o grupo de redução nas três esferas e a classificação 011001 é vedada
        // em NF-e (indNFe = 0). São quatro defeitos independentes, não um repetido.
        assertThat(achados(doc("3"), item().cst("011").classTrib("011001")))
                .extracting(Finding::rejectionCode)
                .containsExactly("1025", "1033", "1074", "1079");
    }

    @Test
    void conformItemProducesNoFinding() {
        var resultado = engine.evaluate(doc("3"),
                List.of(item().cst("000").classTrib("000001").build()));

        assertThat(resultado.findings()).isEmpty();
        assertThat(resultado.itemCount()).isEqualTo(1);
        assertThat(resultado.verifiedItemCount()).isEqualTo(1);
    }

    @Test
    void ruleNotYetInForceIsNeitherFindingNorVerification() {
        // CRT=1 antes de 04/01/2027: a exigência não vigora. Sem o contador de verificados, o
        // relatório mostraria "nenhum achado" e o contador concluiria que está tudo certo (§4.5).
        var resultado = engine.evaluate(docSemTotal("1"), List.of(item().semInvolucro().build()));

        assertThat(resultado.findings()).isEmpty();
        assertThat(resultado.itemCount()).isEqualTo(1);
        assertThat(resultado.verifiedItemCount()).isZero();
    }

    // ---- Por item, e sem tropeçar em nItem ilegível ----

    @Test
    void eachItemIsEvaluatedIndependently() {
        var achados = achados(doc("3"),
                item().numero(1).cst("000").classTrib("000001"),
                item().numero(2).semInvolucro());

        assertThat(achados).singleElement()
                .satisfies(f -> assertThat(f.itemNumber()).isEqualTo(2));
    }

    @Test
    void unreadableItemNumberIsCarriedAsNullInsteadOfDroppingTheItem() {
        assertThat(achados(docSemTotal("3"), item().numero(null).semInvolucro()))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.itemNumber()).isNull();
                    assertThat(f.rejectionCode()).isEqualTo("1115");
                });
    }

    @Test
    void documentWithoutItemsProducesNothing() {
        var resultado = engine.evaluate(docSemTotal("3"), List.of());

        assertThat(resultado.findings()).isEmpty();
        assertThat(resultado.itemCount()).isZero();
        assertThat(resultado.verifiedItemCount()).isZero();
    }

    // ---- 1118/1119: regras de nível documento, avaliadas uma vez, não por item (D-039) ----

    @Test
    void documentRuleFindingCarriesNoItemNumber() {
        // hasIbsCbsTot=false (docSemTotal) + item com invólucro: dispara a 1119. O achado é do
        // documento, não de item nenhum — itemNumber precisa sair null, e não "o item que causou".
        var achados = achados(docSemTotal("3"), item().cst("000").classTrib("000001"));

        assertThat(achados).filteredOn(f -> "1119".equals(f.rejectionCode()))
                .singleElement()
                .satisfies(f -> assertThat(f.itemNumber()).isNull());
    }

    @Test
    void documentRuleDoesNotAffectVerifiedItemCount() {
        // verifiedItemCount é contagem de item; a 1119 é achado de documento e não deve inflar
        // nem esconder esse número.
        var resultado = engine.evaluate(docSemTotal("3"),
                List.of(item().cst("000").classTrib("000001").build()));

        assertThat(resultado.findings()).extracting(Finding::rejectionCode).contains("1119");
        assertThat(resultado.itemCount()).isEqualTo(1);
        assertThat(resultado.verifiedItemCount()).isEqualTo(1);
    }

    @Test
    void totalPresentWithNoItemHavingTheWrapperFiresOnly1118() {
        // doc("3") teria hasIbsCbsTot=true por padrão; aqui construímos explicitamente para não
        // depender do default do helper.
        var documento = new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                DATA, "55", "NFe", "1", null, null, false, true, List.of());
        // CRT=1 antes de 04/01/2027: a 1115 não dispara (item sem invólucro é NaoAplicavel aqui).

        assertThat(engine.evaluate(documento, List.of(item().semInvolucro().build())).findings())
                .extracting(Finding::rejectionCode)
                .containsExactly("1118");
    }

    @Test
    void documentRuleCoexistsWithItemLevelFindingsInTheSameDocument() {
        // Dois itens: item 1 tem o invólucro (sustenta a coerência do total), item 2 não tem CST
        // na tabela — gera seu próprio achado de item, independente da regra de documento.
        var achados = achados(doc("3"),
                item().numero(1).cst("000").classTrib("000001"),
                item().numero(2).cst("999"));

        assertThat(achados).extracting(Finding::rejectionCode).doesNotContain("1118", "1119");
        assertThat(achados).singleElement()
                .satisfies(f -> assertThat(f.notEvaluatedCause())
                        .isEqualTo(NotEvaluatedCause.CST_NOT_IN_TABLE));
    }

    // ---- A invariante que sustenta a cascata inteira ----

    /**
     * Regra suprimida nunca chega a veredito. A invariante sustenta duas coisas de uma vez: que a
     * cascata não esconde rejeição, e que o {@code verifiedItemCount} está certo, já que o motor
     * pula a avaliação sem atualizar o contador. Conferi-la à mão não basta — uma regra nova com
     * precondição mal declarada quebraria as duas em silêncio, e nenhum dos testes de contagem
     * acima acusaria.
     */
    @Test
    void suppressedRuleNeverReachesAVerdict() {
        for (RuleEngine.Binding binding : RuleEngine.BINDINGS) {
            for (RuleEngine.Precondition faltante : binding.requires()) {
                for (Item item : itensSem(faltante)) {
                    for (FiscalDocument documento : documentos()) {
                        var desfecho = binding.rule().evaluate(new RuleContext(
                                documento, item.build(), tables, documento.issueDate()));

                        assertThat(desfecho)
                                .as("%s (%s) com %s faltando, modelo %s, compraGov %s",
                                        binding.rule().rejectionCode(), binding.rule().ruleId(),
                                        faltante, documento.model(), documento.hasCompraGov())
                                .isInstanceOfAny(RuleOutcome.NaoAplicavel.class,
                                        RuleOutcome.NaoAvaliado.class);
                    }
                }
            }
        }
    }

    /**
     * Itens em que só a precondição dada falta — as demais são satisfeitas com códigos reais. O
     * invólucro está sempre presente: os bindings só são consultados depois do corte de nível 1.
     */
    private List<Item> itensSem(RuleEngine.Precondition faltante) {
        List<Item> itens = new ArrayList<>();
        for (boolean comReducao : List.of(false, true)) {
            for (String classTribAusente : new String[] {null, "999999"}) {
                Item base = switch (faltante) {
                    case CST_PRESENT -> item().cst(null).classTrib("000001");
                    case CST_IN_TABLE -> item().cst("999").classTrib("000001");
                    case CLASS_TRIB_IN_TABLE -> item().cst("011").classTrib(classTribAusente);
                };
                itens.add(comReducao ? base.reducaoNasTresEsferas("60.0") : base);
            }
        }
        return itens;
    }

    /** Modelo, vigência e compra governamental variam; nenhum deles destrava a precondição. */
    private List<FiscalDocument> documentos() {
        return List.of(doc("3"), doc("3", DATA, "65"), doc("3", DATA, "55", true),
                doc("1", DATA, "55"));
    }
}
