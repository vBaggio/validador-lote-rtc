package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Matriz fiscal das RVs superiores governadas por cClassTrib. */
class ClassTribConditionalRulesTest {

    private static final LocalDate DATA = LocalDate.of(2026, 8, 4);
    private static FiscalTables tables;
    private static RuleEngine engine;

    @BeforeAll
    static void load() {
        tables = FiscalTables.load();
        engine = new RuleEngine(tables);
    }

    private static Stream<Arguments> regularTaxationMatrix() {
        return Stream.of(
                Arguments.of("000", "000001", false, null),
                Arguments.of("000", "000001", true, "1114"),
                Arguments.of("200", "200022", false, "1065"),
                Arguments.of("200", "200022", true, null));
    }

    @ParameterizedTest
    @MethodSource("regularTaxationMatrix")
    void regularTaxationFollowsClassIndicatorAndGroupPresence(String cst, String classTrib,
            boolean present, String expectedCode) {
        assertTargetCode(evaluate(document("55", "1", null),
                        item(cst, classTrib).regularTaxation(present)),
                List.of("1065", "1114"), expectedCode);
    }

    private static Stream<Arguments> creditReversalMatrix() {
        return Stream.of(
                Arguments.of("000", "000001", false, null),
                Arguments.of("000", "000001", true, "1172"),
                Arguments.of("200", "200054", false, "1173"),
                Arguments.of("200", "200054", true, null));
    }

    @ParameterizedTest
    @MethodSource("creditReversalMatrix")
    void creditReversalFollowsClassIndicatorAndGroupPresence(String cst, String classTrib,
            boolean present, String expectedCode) {
        Item item = item(cst, classTrib).creditReversal(present,
                present ? BigDecimal.ONE : null, present ? BigDecimal.ZERO : null);

        assertTargetCode(evaluate(document("55", "1", null), item),
                List.of("1172", "1173"), expectedCode);
    }

    @Test
    void stockLossRequiresCreditReversalWithoutDependingOnClassification() {
        var findings = evaluate(document("55", "6", "07"), item("000", null));

        assertThat(findings).filteredOn(f -> "1173".equals(f.rejectionCode()))
                .singleElement()
                .satisfies(f -> assertThat(f.ruleId()).isEqualTo("UB116-20"));
    }

    @Test
    void stockLossDoesNotDuplicate1173WhenTheClassIndicatorAlsoRequiresTheGroup() {
        assertThat(evaluate(document("55", "6", "07"), item("200", "200054")))
                .filteredOn(f -> "1173".equals(f.rejectionCode()))
                .singleElement();
    }

    @Test
    void stockLossSuppressesForbiddenAndPositiveValueCreditReversalRules() {
        var findings = evaluate(document("55", "6", "07"),
                item("000", "000001").creditReversal(true, BigDecimal.ZERO, BigDecimal.ZERO));

        assertThat(findings).noneMatch(f -> List.of("1172", "1174")
                .contains(f.rejectionCode()));
    }

    @Test
    void stockLossExceptionPrecedesUnavailableClassificationAndDateFor1172() {
        assertThat(evaluate(document("55", "6", "07"), item("000", null)
                .creditReversal(true, BigDecimal.ZERO, BigDecimal.ZERO)))
                .noneMatch(f -> "UB116-10".equals(f.ruleId()));
        assertThat(new CreditReversalForbiddenRule().evaluate(new RuleContext(
                document("55", null, "6", "07"),
                item("000", null).creditReversal(true, BigDecimal.ZERO, BigDecimal.ZERO).build(),
                tables, null))).isInstanceOf(RuleOutcome.NaoAplicavel.class);
        assertThat(evaluate(document("55", null, "6", "07"), item("000", "000001")
                .creditReversal(true, BigDecimal.ZERO, BigDecimal.ZERO)))
                .noneMatch(f -> "UB116-10".equals(f.ruleId()));
    }

    @Test
    void unreadableDebitTypeDoesNotLoseAPossibleStockLossException() {
        assertRuleNotEvaluated(evaluate(document("55", "6", null),
                item("000", "000001").creditReversal(true, BigDecimal.ZERO, BigDecimal.ZERO)),
                "UB116-10");
        assertRuleNotEvaluated(evaluate(document("55", "6", "XX"),
                item("200", "200054").creditReversal(true, BigDecimal.ZERO, BigDecimal.ZERO)),
                "UB116-30");
    }

    @Test
    void uncertainStockLossWithForbiddenClassLeaves1173NotEvaluated() {
        assertRuleNotEvaluated(evaluate(document("55", "6", null),
                item("000", "000001")), "UB116-20");
        assertRuleNotEvaluated(evaluate(document("55", "6", "XX"),
                item("000", "000001")), "UB116-20");
    }

    @Test
    void creditReversalValueAcceptsEitherPositiveReadableValue() {
        assertThat(evaluate(document("55", "1", null), item("200", "200054")
                .creditReversal(true, new BigDecimal("0.01"), null)))
                .noneMatch(f -> "UB116-30".equals(f.ruleId()));
        assertThat(evaluate(document("55", "1", null), item("200", "200054")
                .creditReversal(true, null, new BigDecimal("0.01"))))
                .noneMatch(f -> "UB116-30".equals(f.ruleId()));
    }

    @Test
    void creditReversalValueRejectsOnlyWhenBothReadableValuesAreNotPositive() {
        var findings = evaluate(document("55", "1", null), item("200", "200054")
                .creditReversal(true, BigDecimal.ZERO, new BigDecimal("-0.01")).number(7));

        assertOfficialMessage(findings, "1174",
                "Rejeição: Valor do IBS ou da CBS deve ser maior que zero no estorno de crédito");
    }

    @Test
    void creditReversalValueIsIndependentOfClassification() {
        assertThat(evaluate(document("55", "1", null), item("000", null)
                .creditReversal(true, BigDecimal.ZERO, BigDecimal.ZERO)))
                .filteredOn(f -> "1174".equals(f.rejectionCode()))
                .singleElement();
    }

    @Test
    void creditReversalValueIsNotEvaluatedWhenNoValueIsPositiveAndOneIsUnreadable() {
        assertRuleNotEvaluated(evaluate(document("55", "1", null), item("200", "200054")
                .creditReversal(true, BigDecimal.ZERO, null)), "UB116-30");
    }

    private static Stream<Arguments> presumedCreditMatrix() {
        return Stream.of(
                Arguments.of("000", "000001", false, null),
                Arguments.of("000", "000001", true, "1175"),
                Arguments.of("000", "000003", false, null),
                Arguments.of("000", "000003", true, null));
    }

    @ParameterizedTest
    @MethodSource("presumedCreditMatrix")
    void presumedCreditIndicatorPermitsButDoesNotRequireTheGroup(String cst, String classTrib,
            boolean present, String expectedCode) {
        assertTargetCode(evaluate(document("55", "1", null),
                        item(cst, classTrib).presumedCredit(present)),
                List.of("1175"), expectedCode);
    }

    @Test
    void usedMovableGoodSuppressesPresumedCreditRejection() {
        assertThat(evaluate(document("55", "1", null), item("000", "000001")
                .presumedCredit(true).usedMovableGood(true, "1")))
                .noneMatch(f -> "1175".equals(f.rejectionCode()));
    }

    @Test
    void usedMovableGoodExceptionPrecedesUnavailableClassificationAndDate() {
        assertThat(evaluate(document("55", "1", null), item("000", null)
                .presumedCredit(true).usedMovableGood(true, "1")))
                .noneMatch(f -> "UB120-20".equals(f.ruleId()));
        assertThat(new PresumedCreditOperationForbiddenRule().evaluate(new RuleContext(
                document("55", null, "1", null), item("000", null).presumedCredit(true)
                        .usedMovableGood(true, "1").build(), tables, null)))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
        assertThat(evaluate(document("55", null, "1", null), item("000", "000001")
                .presumedCredit(true).usedMovableGood(true, "1")))
                .noneMatch(f -> "UB120-20".equals(f.ruleId()));
    }

    @Test
    void unreadableUsedMovableGoodPrecedesUnavailableClassificationAndDate() {
        assertRuleNotEvaluated(evaluate(document("55", "1", null), item("000", null)
                .presumedCredit(true).usedMovableGood(true, null)), "UB120-20");
        assertRuleNotEvaluated(evaluate(document("55", null, "1", null), item("000", "000001")
                .presumedCredit(true).usedMovableGood(true, "XX")), "UB120-20");
        assertThat(new PresumedCreditOperationForbiddenRule().evaluate(new RuleContext(
                document("55", null, "1", null), item("000", null).presumedCredit(true)
                        .usedMovableGood(true, null).build(), tables, null)))
                .isInstanceOfSatisfying(RuleOutcome.NaoAvaliado.class,
                        outcome -> assertThat(outcome.motivo()).contains("indBemMovelUsado"));
    }

    @Test
    void absentUsedGoodTagDiffersFromPresentButUnreadableTag() {
        assertThat(evaluate(document("55", "1", null), item("000", "000001")
                .presumedCredit(true).usedMovableGood(false, null)))
                .filteredOn(f -> "1175".equals(f.rejectionCode()))
                .singleElement();
        assertRuleNotEvaluated(evaluate(document("55", "1", null), item("000", "000001")
                .presumedCredit(true).usedMovableGood(true, null)), "UB120-20");
        assertRuleNotEvaluated(evaluate(document("55", "1", null), item("000", "000001")
                .presumedCredit(true).usedMovableGood(true, "XX")), "UB120-20");
    }

    @Test
    void officialMessagesRemainLiteralAndItemNumberRemainsStructural() {
        assertOfficialMessage(evaluate(document("55", "1", null),
                        item("200", "200022").number(7)),
                "1065", "Rejeição: Classificação Tributária do IBS e da CBS informada obriga "
                        + "informação da tributação regular");
        assertOfficialMessage(evaluate(document("55", "1", null),
                        item("000", "000001").regularTaxation(true).number(7)),
                "1114", "Rejeição: Classificação Tributária do IBS e da CBS informada não "
                        + "permite informação da tributação regular");
        assertOfficialMessage(evaluate(document("55", "1", null), item("000", "000001")
                        .creditReversal(true, BigDecimal.ONE, BigDecimal.ZERO).number(7)),
                "1172", "Rejeição: Grupo de Estorno de Crédito informado indevidamente");
        assertOfficialMessage(evaluate(document("55", "1", null),
                        item("200", "200054").number(7)),
                "1173", "Rejeição: Grupo de Estorno de Crédito não informado");
        assertOfficialMessage(evaluate(document("55", "1", null), item("000", "000001")
                        .presumedCredit(true).number(7)),
                "1175", "Rejeição: Grupo de Crédito Presumido na Operação informado "
                        + "indevidamente");
    }

    @Test
    void regularTaxationStillAppliesToNfceButOtherRulesStayWithNfe() {
        assertTargetCode(evaluate(document("65", "1", null), item("200", "200022")),
                List.of("1065", "1114"), "1065");
        assertThat(evaluate(document("65", "1", null), item("000", "000001")
                .creditReversal(true, BigDecimal.ZERO, BigDecimal.ZERO).presumedCredit(true)))
                .noneMatch(f -> List.of("1172", "1173", "1174", "1175")
                        .contains(f.rejectionCode()));
    }

    @Test
    void unavailableClassificationOrDateProducesNoTargetRejection() {
        assertThat(evaluate(document("55", "1", null), item("000", "999999")
                .regularTaxation(true).presumedCredit(true)))
                .noneMatch(f -> f.rejectionCode() != null
                        && List.of("1065", "1114", "1172", "1173", "1175")
                                .contains(f.rejectionCode()));
        assertThat(evaluate(document("55", LocalDate.of(2025, 5, 4), "1", null),
                item("200", "200022")))
                .noneMatch(f -> f.rejectionCode() != null
                        && List.of("1065", "1114", "1172", "1173", "1175")
                                .contains(f.rejectionCode()));
    }

    private List<Finding> evaluate(FiscalDocument document, Item item) {
        return engine.evaluate(document, List.of(item.build())).findings();
    }

    private static void assertTargetCode(List<Finding> findings, List<String> targetCodes,
            String expectedCode) {
        var target = findings.stream()
                .filter(f -> f.rejectionCode() != null && targetCodes.contains(f.rejectionCode()))
                .toList();
        if (expectedCode == null) {
            assertThat(target).isEmpty();
        } else {
            assertThat(target).singleElement()
                    .satisfies(f -> assertThat(f.rejectionCode()).isEqualTo(expectedCode));
        }
    }

    private static void assertOfficialMessage(List<Finding> findings, String code, String message) {
        assertThat(findings).filteredOn(f -> code.equals(f.rejectionCode()))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.officialMessage()).isEqualTo(message);
                    assertThat(f.officialMessage()).doesNotContain("[nItem:");
                    assertThat(f.itemNumber()).isEqualTo(7);
                });
    }

    private static void assertRuleNotEvaluated(List<Finding> findings, String ruleId) {
        assertThat(findings).filteredOn(f -> ruleId.equals(f.ruleId()))
                .singleElement()
                .satisfies(f -> assertThat(f.kind()).isEqualTo(FindingKind.NOT_EVALUATED));
    }

    private static FiscalDocument document(String model, String finNFe, String tpNFDebito) {
        return document(model, DATA, finNFe, tpNFDebito);
    }

    private static FiscalDocument document(String model, LocalDate date, String finNFe,
            String tpNFDebito) {
        return new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", null,
                null, null, "100", date, model, null, "NFe", "3", finNFe, tpNFDebito,
                "1", null, false, null, true, null, null, null, null, List.of(), Map.of());
    }

    private static Item item(String cst, String classTrib) {
        return new Item(cst, classTrib);
    }

    private static final class Item {
        private Integer number = 1;
        private final String cst;
        private final String classTrib;
        private boolean regularTaxation;
        private boolean creditReversal;
        private boolean presumedCredit;
        private boolean hasUsedMovableGood;
        private String usedMovableGood;
        private BigDecimal reversalIbs;
        private BigDecimal reversalCbs;

        private Item(String cst, String classTrib) {
            this.cst = cst;
            this.classTrib = classTrib;
        }

        Item number(int value) { number = value; return this; }

        Item regularTaxation(boolean present) { regularTaxation = present; return this; }

        Item creditReversal(boolean present, BigDecimal ibs, BigDecimal cbs) {
            creditReversal = present;
            reversalIbs = ibs;
            reversalCbs = cbs;
            return this;
        }

        Item presumedCredit(boolean present) { presumedCredit = present; return this; }

        Item usedMovableGood(boolean present, String value) {
            hasUsedMovableGood = present;
            usedMovableGood = value;
            return this;
        }

        ItemTaxGroup build() {
            return new ItemTaxGroup(number, true, true, cst, classTrib, null,
                    false, false, false, null, null, null, null,
                    false, false, false, false, false, false,
                    presumedCredit, false, false, false,
                    false, false, false, creditReversal, regularTaxation,
                    hasUsedMovableGood, usedMovableGood,
                    null, null, reversalIbs, reversalCbs,
                    null, null, null, null, null, null, Map.of());
        }
    }
}
