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

/** Matriz fiscal das RVs superiores governadas por CST e tipo de nota de crédito. */
class CstConditionalRulesTest {

    private static final LocalDate BEFORE_MONO_REQUIRED = LocalDate.of(2027, 1, 3);
    private static final LocalDate MONO_REQUIRED = LocalDate.of(2027, 1, 4);

    private static RuleEngine engine;

    @BeforeAll
    static void load() {
        engine = new RuleEngine(FiscalTables.load());
    }

    private static Stream<Arguments> cstPresenceMatrix() {
        return Stream.of(
                // família, CST indicador 0, CST indicador 1, presença, código esperado
                Arguments.of(Group.MONO, "000", false, null),
                Arguments.of(Group.MONO, "000", true, "1151"),
                Arguments.of(Group.MONO, "620", false, "1116"),
                Arguments.of(Group.MONO, "620", true, null),
                Arguments.of(Group.TRANSFER, "000", false, null),
                Arguments.of(Group.TRANSFER, "000", true, "1131"),
                Arguments.of(Group.TRANSFER, "800", false, "1132"),
                Arguments.of(Group.TRANSFER, "800", true, null),
                Arguments.of(Group.ADJUSTMENT, "000", false, null),
                Arguments.of(Group.ADJUSTMENT, "000", true, "1169"),
                Arguments.of(Group.ADJUSTMENT, "811", false, "1170"),
                Arguments.of(Group.ADJUSTMENT, "811", true, null),
                Arguments.of(Group.ZFM_CREDIT, "000", false, null),
                Arguments.of(Group.ZFM_CREDIT, "000", true, "1134"),
                Arguments.of(Group.ZFM_CREDIT, "810", false, "1135"),
                Arguments.of(Group.ZFM_CREDIT, "810", true, null));
    }

    @ParameterizedTest
    @MethodSource("cstPresenceMatrix")
    void cstIndicatorAndGroupPresenceFollowTheFourQuadrants(Group group, String cst,
            boolean present, String expectedCode) {
        var findings = evaluate(document("55", MONO_REQUIRED, "1", null, null),
                item(cst).with(group, present));

        assertTargetCode(findings, group.codes(), expectedCode);
    }

    @Test
    void officialMessagesRemainLiteralAndDoNotCarryTheStructuralItemSuffix() {
        assertOfficialMessage(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                        item("000").with(Group.MONO, true)),
                "1151", "Rejeição: Grupo IBS/CBS Monofásico informado indevidamente");
        assertOfficialMessage(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                        item("620")),
                "1116", "Rejeição: Grupo IBS/CBS Monofásico não informado");
        assertOfficialMessage(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                        item("000").with(Group.TRANSFER, true)),
                "1131", "Rejeição: Grupo de transferência de crédito informado indevidamente");
        assertOfficialMessage(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                        item("800")),
                "1132", "Rejeição: Grupo de transferência de crédito não informado");
        assertOfficialMessage(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                        item("000").with(Group.ADJUSTMENT, true)),
                "1169", "Rejeição: Grupo de Ajuste de Competência informado indevidamente");
        assertOfficialMessage(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                        item("811")),
                "1170", "Rejeição: Grupo de Ajuste de Competência não informado");
        assertOfficialMessage(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                        item("000").with(Group.ZFM_CREDIT, true)),
                "1134", "Rejeição: CST do IBS/CBS informado não permite informação do grupo "
                        + "para apropriação de crédito presumido de IBS sobre o saldo devedor "
                        + "na ZFM");
        assertOfficialMessage(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                        item("810")),
                "1135", "Rejeição: CST do IBS/CBS informado exige a informação do grupo para "
                        + "apropriação de crédito presumido de IBS sobre o saldo devedor na ZFM");
    }

    @Test
    void monophaseRequiredDoesNotApplyBeforeProductionDate() {
        assertThat(evaluate(document("55", BEFORE_MONO_REQUIRED, "1", null, null), item("620")))
                .noneMatch(f -> "1116".equals(f.rejectionCode()));
    }

    @Test
    void monophaseRequiredHonorsStockLossException() {
        assertThat(evaluate(document("55", MONO_REQUIRED, "1", null, "07"), item("620")))
                .noneMatch(f -> "1116".equals(f.rejectionCode()));
    }

    @Test
    void monophaseRequiredIsNotEvaluatedInHomologation() {
        assertRuleNotEvaluated(evaluate(document("55", MONO_REQUIRED, "2", null, null),
                item("620")), "UB13-40");
    }

    @Test
    void monophaseRequiredIsNotEvaluatedWhenEnvironmentIsMissingOrUnreadable() {
        assertRuleNotEvaluated(evaluate(document("55", MONO_REQUIRED, null, null, null),
                item("620")), "UB13-40");
        assertRuleNotEvaluated(evaluate(document("55", MONO_REQUIRED, "9", null, null),
                item("620")), "UB13-40");
    }

    @Test
    void environmentIsNotNeededWhenTheRequiredMonophaseGroupIsPresent() {
        assertThat(evaluate(document("55", MONO_REQUIRED, null, null, null),
                item("620").with(Group.MONO, true)))
                .noneMatch(f -> "UB13-40".equals(f.ruleId()));
    }

    @Test
    void monophasePresencePairAlsoAppliesToNfce() {
        assertTargetCode(evaluate(document("65", MONO_REQUIRED, "1", null, null),
                item("000").with(Group.MONO, true)), Group.MONO.codes(), "1151");
        assertTargetCode(evaluate(document("65", MONO_REQUIRED, "1", null, null),
                item("620")), Group.MONO.codes(), "1116");
    }

    @ParameterizedTest
    @MethodSource("groupsRestrictedToNfe")
    void groupsThatConflictWithTheNfceXsdReceiveNoLocalCstVerdict(Group group, String cst,
            boolean present) {
        assertThat(evaluate(document("65", MONO_REQUIRED, "1", null, null),
                item(cst).with(group, present)))
                .noneMatch(f -> group.codes().contains(f.rejectionCode()));
    }

    private static Stream<Arguments> groupsRestrictedToNfe() {
        return Stream.of(
                Arguments.of(Group.TRANSFER, "000", true),
                Arguments.of(Group.TRANSFER, "800", false),
                Arguments.of(Group.ADJUSTMENT, "000", true),
                Arguments.of(Group.ADJUSTMENT, "811", false),
                Arguments.of(Group.ZFM_CREDIT, "000", true),
                Arguments.of(Group.ZFM_CREDIT, "810", false));
    }

    @Test
    void adjustmentValueAcceptsEitherPositiveReadableValue() {
        assertThat(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                item("811").adjustment(new BigDecimal("0.01"), null)))
                .noneMatch(f -> "UB112-30".equals(f.ruleId()));
        assertThat(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                item("811").adjustment(null, new BigDecimal("0.01"))))
                .noneMatch(f -> "UB112-30".equals(f.ruleId()));
    }

    @Test
    void adjustmentValueRejectsWhenBothReadableValuesAreNotPositive() {
        var findings = evaluate(document("55", MONO_REQUIRED, "1", null, null),
                item("811").adjustment(BigDecimal.ZERO, BigDecimal.ZERO));

        assertOfficialMessage(findings, "1171",
                "Rejeição: Valor do IBS ou da CBS deve ser maior que zero no ajuste de competência");
    }

    @Test
    void adjustmentValueIsNotEvaluatedWhenNoValueIsPositiveAndOneIsUnreadable() {
        assertRuleNotEvaluated(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                item("811").adjustment(BigDecimal.ZERO, null)), "UB112-30");
    }

    @Test
    void adjustmentValueRuleDoesNotApplyWithoutTheGroupOrInNfce() {
        assertThat(evaluate(document("55", MONO_REQUIRED, "1", null, null), item("811")))
                .noneMatch(f -> "UB112-30".equals(f.ruleId()));
        assertThat(evaluate(document("65", MONO_REQUIRED, "1", null, null),
                item("811").adjustment(BigDecimal.ZERO, BigDecimal.ZERO)))
                .noneMatch(f -> "UB112-30".equals(f.ruleId()));
    }

    @Test
    void zfmGroupAndCreditNoteTypeFollowBothDirections() {
        assertOfficialMessage(evaluate(document("55", MONO_REQUIRED, "1", "01", null),
                        item("810").with(Group.ZFM_CREDIT, true)),
                "1158", "Rejeição: Tipo de Nota de Crédito não permite o grupo para apropriação "
                        + "de crédito presumido de IBS sobre o saldo devedor na ZFM");
        assertThat(evaluate(document("55", MONO_REQUIRED, "1", "02", null),
                item("810").with(Group.ZFM_CREDIT, true)))
                .noneMatch(f -> List.of("1158", "1159").contains(f.rejectionCode()));
        assertOfficialMessage(evaluate(document("55", MONO_REQUIRED, "1", "02", null),
                        item("810")),
                "1159", "Rejeição: Tipo de Nota de Crédito exige o grupo para apropriação de "
                        + "crédito presumido de IBS sobre o saldo devedor na ZFM");
        assertThat(evaluate(document("55", MONO_REQUIRED, "1", "01", null), item("810")))
                .noneMatch(f -> List.of("1158", "1159").contains(f.rejectionCode()));
    }

    @Test
    void absentOrUnreadableCreditNoteTypeIsNotTreatedAsDifferentFrom02() {
        assertRuleNotEvaluated(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                item("810").with(Group.ZFM_CREDIT, true)), "UB131-40");
        assertRuleNotEvaluated(evaluate(document("55", MONO_REQUIRED, "1", "XX", null),
                item("810").with(Group.ZFM_CREDIT, true)), "UB131-40");
    }

    @Test
    void creditNoteTypeRulesAreRestrictedToNfe() {
        assertThat(evaluate(document("65", MONO_REQUIRED, "1", "01", null),
                item("810").with(Group.ZFM_CREDIT, true)))
                .noneMatch(f -> List.of("1158", "1159").contains(f.rejectionCode()));
        assertThat(evaluate(document("65", MONO_REQUIRED, "1", "02", null), item("810")))
                .noneMatch(f -> List.of("1158", "1159").contains(f.rejectionCode()));
    }

    @Test
    void cstOutsideTableOrOutsideItsValidityProducesNoConditionalRejection() {
        assertThat(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                item("999").with(Group.MONO, true)))
                .noneMatch(f -> Group.allCstCodes().contains(f.rejectionCode()));
        assertThat(evaluate(document("55", MONO_REQUIRED, "1", null, null),
                item("999").adjustment(BigDecimal.ZERO, BigDecimal.ZERO)))
                .noneMatch(f -> "1171".equals(f.rejectionCode()));
        assertThat(evaluate(document("55", LocalDate.of(2025, 9, 29), "1", null, null),
                item("811").adjustment(BigDecimal.ZERO, BigDecimal.ZERO)))
                .noneMatch(f -> Group.allCstCodes().contains(f.rejectionCode())
                        || "1171".equals(f.rejectionCode()));
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
                });
    }

    private static void assertRuleNotEvaluated(List<Finding> findings, String ruleId) {
        assertThat(findings).filteredOn(f -> ruleId.equals(f.ruleId()))
                .singleElement()
                .satisfies(f -> assertThat(f.kind()).isEqualTo(FindingKind.NOT_EVALUATED));
    }

    private static FiscalDocument document(String model, LocalDate date, String tpAmb,
            String tpNFCredito, String tpNFDebito) {
        return new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", null,
                null, null, "100", date, model, null, "NFe", "3", null, tpNFDebito,
                tpAmb, tpNFCredito, false, null, true, null, null, null, null,
                List.of(), Map.of());
    }

    private static Item item(String cst) {
        return new Item(cst);
    }

    private enum Group {
        MONO(List.of("1151", "1116")),
        TRANSFER(List.of("1131", "1132")),
        ADJUSTMENT(List.of("1169", "1170")),
        ZFM_CREDIT(List.of("1134", "1135"));

        private final List<String> codes;

        Group(List<String> codes) {
            this.codes = codes;
        }

        List<String> codes() {
            return codes;
        }

        static List<String> allCstCodes() {
            return Stream.of(values()).flatMap(group -> group.codes.stream()).toList();
        }
    }

    private static final class Item {
        private final String cst;
        private boolean mono;
        private boolean transfer;
        private boolean adjustment;
        private boolean zfmCredit;
        private BigDecimal adjustmentIbs;
        private BigDecimal adjustmentCbs;

        private Item(String cst) {
            this.cst = cst;
        }

        Item with(Group group, boolean present) {
            switch (group) {
                case MONO -> mono = present;
                case TRANSFER -> transfer = present;
                case ADJUSTMENT -> adjustment = present;
                case ZFM_CREDIT -> zfmCredit = present;
            }
            return this;
        }

        Item adjustment(BigDecimal ibs, BigDecimal cbs) {
            adjustment = true;
            adjustmentIbs = ibs;
            adjustmentCbs = cbs;
            return this;
        }

        ItemTaxGroup build() {
            return new ItemTaxGroup(1, true, false, cst, null, null,
                    false, false, false, null, null, null, null,
                    false, false, false, false, false, false,
                    false, zfmCredit, false, false, mono, transfer, adjustment,
                    false, false, false, null, adjustmentIbs, adjustmentCbs, null, null,
                    null, null, null, null, null, null, Map.of());
        }
    }
}
