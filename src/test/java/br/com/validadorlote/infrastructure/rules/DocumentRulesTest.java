package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.domain.ReferencedNote;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRulesTest {

    private static FiscalTables tables;
    private static final LocalDate VIGENTE = LocalDate.of(2026, 8, 3);
    private static final LocalDate ANTES = LocalDate.of(2026, 8, 2);
    private static final LocalDate VIGENTE_SIMPLES = LocalDate.of(2027, 1, 4);
    private static final LocalDate ANTES_SIMPLES = LocalDate.of(2027, 1, 3);

    @BeforeAll
    static void load() { tables = FiscalTables.load(); }

    private FiscalDocument doc(String crt, LocalDate data) {
        return doc(crt, data, null, null, List.of());
    }

    private FiscalDocument doc(String crt, LocalDate data, String finNFe, String tpNFDebito,
            List<ReferencedNote> referencias) {
        return new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                data, "55", "NFe", crt, finNFe, tpNFDebito, false, false, referencias);
    }

    /** Item cujo invólucro e cujo grupo interno andam juntos — o suficiente para a 1115. */
    private ItemTaxGroup item(boolean temGrupo, String cst) {
        return item(temGrupo, temGrupo, cst, null);
    }

    private ItemTaxGroup item(boolean temInvolucro, boolean temGrupoInterno, String cst,
            String cProdANP) {
        return item(temInvolucro, temGrupoInterno, cst, cProdANP, null);
    }

    private ItemTaxGroup item(boolean temInvolucro, boolean temGrupoInterno, String cst,
            String cProdANP, ReferencedNote dfeReferenciado) {
        return new ItemTaxGroup(1, temInvolucro, temGrupoInterno, cst,
                temInvolucro ? "000001" : null, cProdANP, false, false, false, null, null, null,
                dfeReferenciado);
    }

    private RuleOutcome mil115(FiscalDocument documento, ItemTaxGroup item) {
        return new GroupRequiredRule().evaluate(
                new RuleContext(documento, item, tables, documento.issueDate()));
    }

    // ---- 1115: grupo obrigatório ----

    @Test
    void crt3WithoutGroupOnOrAfterTheDeadlineIsRejected() {
        var out = mil115(doc("3", VIGENTE), item(false, null));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).rejectionCode()).isEqualTo("1115");
    }

    @Test
    void crt3WithoutGroupBeforeTheDeadlineIsNotApplicable() {
        assertThat(mil115(doc("3", ANTES), item(false, null)))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void simplesNacionalIsNotApplicableUntil2027() {
        // Dizer "conforme" aqui faria o contador ser surpreendido em janeiro.
        var out = mil115(doc("1", VIGENTE), item(false, null));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAplicavel.class);
        assertThat(((RuleOutcome.NaoAplicavel) out).motivo()).contains("2027");
    }

    @Test
    void simplesNacionalHasItsOwnDeadlineBorder() {
        // O par de borda existe porque um typo de dia/mês (04/01 virando 01/04) passaria
        // incólume por um teste que só procura "2027" no texto do motivo.
        assertThat(mil115(doc("1", ANTES_SIMPLES), item(false, null)))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
        assertThat(mil115(doc("1", VIGENTE_SIMPLES), item(false, null)))
                .isInstanceOf(RuleOutcome.Rejeitado.class);
    }

    @Test
    void deadlineIsShownInBrazilianFormat() {
        var out = mil115(doc("1", VIGENTE), item(false, null));

        // O motivo é lido por um contador, não por um parser ISO.
        assertThat(((RuleOutcome.NaoAplicavel) out).motivo()).contains("04/01/2027");
    }

    @Test
    void crt3WithGroupIsConforme() {
        assertThat(mil115(doc("3", VIGENTE), item(true, "000")))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void absentCrtIsNotEvaluatedNotRejected() {
        // Sem CRT não dá para saber se a exigência vale. Acusar seria chutar.
        assertThat(mil115(doc(null, VIGENTE), item(false, null)))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void blankCrtIsNotEvaluatedEither() {
        // O guard é isBlank(), não != null: um <CRT></CRT> vazio é tão desconhecido quanto
        // ausente. Trocar por != null num refactor futuro faria "" cair no ramo do Simples.
        assertThat(mil115(doc("", VIGENTE), item(false, null)))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void unknownCrtIsNotEvaluatedInsteadOfFallingIntoTheSimplesBranch() {
        // CRT 9 não existe na NT. O ternário ingênuo o jogaria no ramo do Simples e o acusaria
        // em 04/01/2027 — dado que não sabemos interpretar não vira acusação.
        assertThat(mil115(doc("9", VIGENTE_SIMPLES), item(false, null)))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void crtWithSurroundingSpaceStillCountsAsRegimeNormal() {
        // Sem trim(), " 3 " cairia no ramo do Simples e sairia NaoAplicavel em 03/08/2026:
        // falso negativo silencioso justamente no caso central do bloco.
        assertThat(mil115(doc(" 3 ", VIGENTE), item(false, null)))
                .isInstanceOf(RuleOutcome.Rejeitado.class);
    }

    @Test
    void missingIssueDateIsNotEvaluatedBy1115() {
        assertThat(mil115(doc("3", null), item(false, null)))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    // ---- 1115, Exceção 1: devolução/complementar de nota anterior a 2026 ----

    @Test
    void returnReferencingA2025NoteIsNotApplicable() {
        // Devolução em agosto/2026 de mercadoria vendida em 2025 é rotina. Acusá-la seria o
        // falso positivo em escala que motivou a leitura da NT inteira.
        var out = mil115(doc("3", VIGENTE, "4", null,
                List.of(new ReferencedNote("refNFe", YearMonth.of(2025, 12)))), item(false, null));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAplicavel.class);
        assertThat(((RuleOutcome.NaoAplicavel) out).motivo()).contains("Exceção 1");
    }

    @Test
    void complementaryNoteReferencingA2025NoteIsNotApplicable() {
        assertThat(mil115(doc("3", VIGENTE, "2", null,
                List.of(new ReferencedNote("refNFe", YearMonth.of(2025, 1)))), item(false, null)))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void returnReferencingA2026NoteIsStillRejected() {
        // A exceção é sobre a nota referenciada ser anterior a 2026, não sobre ser devolução.
        assertThat(mil115(doc("3", VIGENTE, "4", null,
                List.of(new ReferencedNote("refNFe", YearMonth.of(2026, 1)))), item(false, null)))
                .isInstanceOf(RuleOutcome.Rejeitado.class);
    }

    @Test
    void normalNoteReferencingA2025NoteIsRejected() {
        // A exceção só alcança finNFe 2 e 4; uma nota normal que referencia outra não escapa.
        assertThat(mil115(doc("3", VIGENTE, "1", null,
                List.of(new ReferencedNote("refNFe", YearMonth.of(2025, 12)))), item(false, null)))
                .isInstanceOf(RuleOutcome.Rejeitado.class);
    }

    @Test
    void returnReferencingAPaperNoteFrom2025IsNotApplicable() {
        // Leitura ampla deliberada: ao rigor da letra só refNFe/refNFeSig são "NFe", mas
        // as leituras 1925 e 2025 são ambas anteriores ao corte. A ambiguidade de século não
        // muda o desfecho conservador da exceção (D-028).
        assertThat(mil115(doc("3", VIGENTE, "4", null,
                List.of(new ReferencedNote("refNFP", YearMonth.of(2025, 7), true))),
                item(false, null)))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void returnReferencingRefNfWithAamm9912IsNotEvaluated() {
        var out = mil115(doc("3", VIGENTE, "4", null,
                List.of(new ReferencedNote("refNF", YearMonth.of(2099, 12), true))),
                item(false, null));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAvaliado.class);
        assertThat(((RuleOutcome.NaoAvaliado) out).motivo())
                .contains("refNF", "século", "12/99");
    }

    @Test
    void returnReferencingRefNfpWithAamm9912IsNotEvaluated() {
        var out = mil115(doc("3", VIGENTE, "4", null,
                List.of(new ReferencedNote("refNFP", YearMonth.of(2099, 12), true))),
                item(false, null));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAvaliado.class);
        assertThat(((RuleOutcome.NaoAvaliado) out).motivo())
                .contains("refNFP", "século", "12/99");
    }

    @Test
    void returnWithAnUndatableReferenceIsNotEvaluated() {
        var out = mil115(doc("3", VIGENTE, "4", null,
                List.of(new ReferencedNote("refCTe", null))), item(false, null));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAvaliado.class);
        assertThat(((RuleOutcome.NaoAvaliado) out).motivo()).contains("refCTe");
    }

    @Test
    void returnWithNoReferenceAtAllFollowsTheNormalCourse() {
        // A exceção exige uma referência; sem nenhuma ela não se aplica, e a SEFAZ rejeitaria.
        assertThat(mil115(doc("3", VIGENTE, "4", null, List.of()), item(false, null)))
                .isInstanceOf(RuleOutcome.Rejeitado.class);
    }

    @Test
    void oneOldReferenceAmongSeveralIsEnoughToExcuse() {
        assertThat(mil115(doc("3", VIGENTE, "4", null, List.of(
                new ReferencedNote("refNFe", YearMonth.of(2026, 7)),
                new ReferencedNote("refNFe", YearMonth.of(2025, 11)))), item(false, null)))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    // ---- 1115, Exceção 1: DFeReferenciado por item (NT v1.40, produção 01/09/2026, D-038) ----

    @Test
    void returnReferencingA2025NoteViaDFeReferenciadoIsNotApplicable() {
        // A partir de 01/09/2026 (VC02-14) o referenciamento de devolução migra exclusivamente
        // para este grupo, no nível do item. Sem lê-lo, uma devolução emitida corretamente —
        // só com DFeReferenciado, como a norma passa a exigir — viraria falso positivo.
        var out = mil115(doc("3", VIGENTE, "4", null, List.of()),
                item(false, false, null, null,
                        new ReferencedNote("DFeReferenciado", YearMonth.of(2025, 12))));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAplicavel.class);
        assertThat(((RuleOutcome.NaoAplicavel) out).motivo()).contains("Exceção 1");
    }

    @Test
    void returnReferencingA2026NoteViaDFeReferenciadoIsStillRejected() {
        assertThat(mil115(doc("3", VIGENTE, "4", null, List.of()),
                item(false, false, null, null,
                        new ReferencedNote("DFeReferenciado", YearMonth.of(2026, 1)))))
                .isInstanceOf(RuleOutcome.Rejeitado.class);
    }

    @Test
    void dfeReferenciadoWithUndecodableKeyIsNotEvaluatedNeverRejected() {
        // Chave fora do formato (não 44 dígitos, ou ausente): base incompleta é limitação
        // nossa, não defeito do emitente — nunca Rejeitado (mesmo padrão de D-028/D-029).
        var out = mil115(doc("3", VIGENTE, "4", null, List.of()),
                item(false, false, null, null, new ReferencedNote("DFeReferenciado", null)));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAvaliado.class);
        assertThat(((RuleOutcome.NaoAvaliado) out).motivo()).contains("DFeReferenciado");
    }

    @Test
    void nFrefAndDFeReferenciadoAreBothConsideredNotOneReplacingTheOther() {
        // NFref continua sendo lido: a VC02-14 só proíbe refNFe na devolução, e este brief só
        // acrescenta a segunda fonte. Aqui o NFref do documento é recente (não excusa sozinho) e
        // o DFeReferenciado do item é antigo — a exceção só se aplica se as duas fontes forem
        // consideradas juntas.
        assertThat(mil115(doc("3", VIGENTE, "4", null,
                List.of(new ReferencedNote("refNFe", YearMonth.of(2026, 7)))),
                item(false, false, null, null,
                        new ReferencedNote("DFeReferenciado", YearMonth.of(2025, 11)))))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void returnWithNeitherNFrefNorDFeReferenciadoFollowsTheNormalCourse() {
        // Nenhuma das duas fontes presente: a exceção exige uma referência, e sem ela o curso
        // normal decide — comportamento existente que não pode regredir com a nova fonte.
        assertThat(mil115(doc("3", VIGENTE, "4", null, List.of()),
                item(false, false, null, null, null)))
                .isInstanceOf(RuleOutcome.Rejeitado.class);
    }

    // ---- 1115, Exceção 2: combustível monofásico ----

    @Test
    void itemWithCProdAnpIsNotEvaluated() {
        // A exceção depende da Tabela de Combustíveis, que não está embarcada (D-029).
        var out = mil115(doc("3", VIGENTE), item(false, false, null, "210203001"));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAvaliado.class);
        assertThat(((RuleOutcome.NaoAvaliado) out).motivo()).contains("210203001");
    }

    @Test
    void itemWithCProdAnpAndTheGroupIsStillConforme() {
        // A exceção afasta a acusação, não a conformidade de quem informou o grupo.
        assertThat(mil115(doc("3", VIGENTE), item(true, true, "000", "210203001")))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    // ---- 1021: gIBSCBS proibido ----

    @Test
    void innerGroupInformedWhenCstForbidsIsRejected() {
        // CST 400 (Isenção) tem ind_gIBSCBS = 0: o gIBSCBS não pode vir preenchido.
        var out = new GroupForbiddenRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(true, true, "400", null), tables, VIGENTE));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).rejectionCode()).isEqualTo("1021");
    }

    @Test
    void exemptItemWithoutTheInnerGroupIsConforme() {
        // O par obrigatório do teste acima. O invólucro IBSCBS existe (ele carrega o CST), mas
        // não há gIBSCBS: é a isenção corretamente emitida. Olhar o invólucro em vez do grupo
        // interno acusaria este item — e mais 6 dos 18 CSTs. Conforme, e não NaoAplicavel:
        // a regra alcançou o item, verificou e aprovou.
        assertThat(new GroupForbiddenRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(true, false, "400", null), tables, VIGENTE)))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void cstThatAdmitsTheInnerGroupIsNotThe1021Territory() {
        // CST 000 permite o gIBSCBS: com ou sem ele, a proibição não tem o que dizer — quem
        // fala sobre a ausência é a 1022.
        assertThat(new GroupForbiddenRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(true, true, "000", null), tables, VIGENTE)))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
        assertThat(new GroupForbiddenRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(true, false, "000", null), tables, VIGENTE)))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void itemWithoutTheWrapperBelongsTo1115NotTo1021() {
        assertThat(new GroupForbiddenRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(false, null), tables, VIGENTE)))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void unknownCstIsNotEvaluated() {
        assertThat(new GroupForbiddenRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(true, true, "999", null), tables, VIGENTE)))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void missingIssueDateIsNotEvaluatedBy1021() {
        // Consultar a tabela com data nula estoura NPE na vigência e derrubaria o arquivo
        // inteiro do lote — todos os 18 CSTs têm iniVig não nulo.
        assertThat(new GroupForbiddenRule().evaluate(
                new RuleContext(doc("3", null), item(true, true, "400", null), tables, null)))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    // ---- 1022: gIBSCBS exigido ----

    @Test
    void innerGroupMissingWhenCstRequiresIsRejected() {
        var out = new GroupRequiredByCstRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(true, false, "000", null), tables, VIGENTE));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).rejectionCode()).isEqualTo("1022");
    }

    @Test
    void innerGroupPresentWhenCstRequiresIsConforme() {
        assertThat(new GroupRequiredByCstRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(true, true, "000", null), tables, VIGENTE)))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void lossInStockIsExemptFrom1022() {
        var out = new GroupRequiredByCstRule().evaluate(new RuleContext(
                doc("3", VIGENTE, "6", "07", List.of()), item(true, false, "000", null),
                tables, VIGENTE));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAplicavel.class);
        assertThat(((RuleOutcome.NaoAplicavel) out).motivo()).contains("07");
    }

    @Test
    void cstThatForbidsTheInnerGroupIsNotThe1022Territory() {
        assertThat(new GroupRequiredByCstRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(true, false, "400", null), tables, VIGENTE)))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void itemWithoutTheWrapperBelongsTo1115NotTo1022() {
        assertThat(new GroupRequiredByCstRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(false, null), tables, VIGENTE)))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void missingIssueDateIsNotEvaluatedBy1022() {
        assertThat(new GroupRequiredByCstRule().evaluate(
                new RuleContext(doc("3", null), item(true, false, "000", null), tables, null)))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }
}
