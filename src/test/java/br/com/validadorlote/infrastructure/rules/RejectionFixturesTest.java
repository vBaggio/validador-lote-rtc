package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import br.com.validadorlote.infrastructure.xml.XsdErrorTranslator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corpus de XMLs estruturalmente válidos para a validação diferencial da camada de rejeição.
 * Cada caso positivo isola uma única rejeição; o controle imediatamente correspondente não gera
 * achado algum. Assim, uma mudança errada na regra, no extrator ou no mapa fiscal quebra este
 * teste antes de seguir para a comparação humana com a SVRS.
 */
class RejectionFixturesTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures/rejeicao");
    private static final List<Case> POSITIVE_CASES = List.of(
            new Case("r1115-sem-grupo.xml", "1115"),
            new Case("r1021-grupo-indevido.xml", "1021"),
            new Case("r1022-grupo-obrigatorio-ausente.xml", "1022"),
            new Case("r1024-classtrib-incompativel-cst.xml", "1024"),
            new Case("r1024-simples-grupo-invalido.xml", "1024"),
            new Case("r1025-classtrib-modelo.xml", "1025"),
            new Case("r1033-reducao-uf-ausente.xml", "1033"),
            new Case("r1074-reducao-municipio-ausente.xml", "1074"),
            new Case("r1079-reducao-cbs-ausente.xml", "1079"),
            new Case("r1034-percentual-uf-invalido.xml", "1034"),
            new Case("r1046-percentual-municipio-invalido.xml", "1046"),
            new Case("r1063-percentual-cbs-invalido.xml", "1063"),
            new Case("r1118-total-sem-item.xml", "1118"),
            new Case("r1119-item-sem-total.xml", "1119"),
            // Bloco 7 -- mecanismo 1 (diferimento por indicador de CST, sem exceção)
            new Case("r1029-diferimento-uf-indevido.xml", "1029"),
            new Case("r1030-diferimento-uf-ausente.xml", "1030"),
            new Case("r1044-diferimento-municipio-ausente.xml", "1044"),
            new Case("r1061-diferimento-cbs-ausente.xml", "1061"),
            new Case("r1083-diferimento-municipio-indevido.xml", "1083"),
            new Case("r1090-diferimento-cbs-indevido.xml", "1090"),
            // Bloco 7 -- mecanismo 3 (devolução de tributo proibida)
            new Case("r1111-devolucao-uf-indevida.xml", "1111"),
            new Case("r1112-devolucao-municipio-indevida.xml", "1112"),
            new Case("r1187-devolucao-cbs-nfce.xml", "1187"),
            // Bloco 7 -- mecanismo 5 (grupo proibido no modelo 65)
            new Case("r1006-compragov-nfce.xml", "1006"),
            new Case("r1049-credpresoper-nfce.xml", "1049"),
            new Case("r1138-credpresibszfm-nfce.xml", "1138"),
            new Case("r708-dfereferenciado-nfce.xml", "708"));
    private static final List<String> CONTROL_CASES = List.of(
            "c1115-com-grupo.xml",
            "c1115-crt3-antes-vigencia.xml",
            "c1115-simples-sem-grupo.xml",
            "c1021-sem-grupo-interno.xml",
            "c1022-com-grupo-interno.xml",
            "c1024-classtrib-compativel-cst.xml",
            "c1024-simples-grupo-valido.xml",
            "c1025-classtrib-permitida-modelo.xml",
            "c1033-reducao-uf-presente.xml",
            "c1074-reducao-municipio-presente.xml",
            "c1079-reducao-cbs-presente.xml",
            "c1034-percentual-uf-correto.xml",
            "c1046-percentual-municipio-correto.xml",
            "c1063-percentual-cbs-correto.xml",
            "c1118-total-com-item.xml",
            "c1119-item-com-total.xml",
            "c1029-sem-diferimento-uf.xml",
            "c1030-diferimento-uf-presente.xml",
            "c1044-diferimento-municipio-presente.xml",
            "c1061-diferimento-cbs-presente.xml",
            "c1083-sem-diferimento-municipio.xml",
            "c1090-sem-diferimento-cbs.xml",
            "c1111-sem-devolucao-uf.xml",
            "c1112-sem-devolucao-municipio.xml",
            "c1187-sem-devolucao-cbs-nfce.xml",
            "c1006-sem-compragov-nfce.xml",
            "c1049-sem-credpresoper-nfce.xml",
            "c1138-sem-credpresibszfm-nfce.xml",
            "c708-sem-dfereferenciado-nfce.xml");

    private static final XmlMetadataParser parser = new XmlMetadataParser();
    private static final TaxGroupExtractor extractor = new TaxGroupExtractor();
    private static RuleEngine rules;
    private static SchemaValidatorEngine schema;

    private record Case(String file, List<String> rejectionCodes) {
        Case(String file, String... rejectionCodes) {
            this(file, List.of(rejectionCodes));
        }
    }

    @BeforeAll
    static void setup() {
        rules = new RuleEngine(FiscalTables.load());
        schema = new SchemaValidatorEngine(new XsdErrorTranslator());
    }

    @Test
    void fixtureCorpusHasEveryPositiveAndControlCase() throws IOException {
        assertThat(Files.isDirectory(FIXTURES)).isTrue();
        try (var files = Files.list(FIXTURES)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrderElementsOf(expectedFiles());
        }
    }

    @Test
    void everyFixtureIsValidAgainstTheOfficialSchema() {
        for (String file : expectedFiles()) {
            Path xml = fixture(file);
            assertThat(schema.validate(xml, parser.parse(xml)))
                    .as("XSD of %s", file)
                    .isEmpty();
        }
    }

    @Test
    void fixturePairsContainTheirDeclaredNearMisses() {
        assertThat(parser.parse(fixture("r1115-sem-grupo.xml")).document().issueDate())
                .isEqualTo(LocalDate.of(2026, 8, 3));
        assertTaxGroup("r1115-sem-grupo.xml", null, null, false, false, false, false, false);
        assertTaxGroup("c1115-com-grupo.xml", "000", "000001", true, true,
                false, false, false);
        assertThat(parser.parse(fixture("c1115-crt3-antes-vigencia.xml")).document())
                .satisfies(document -> {
                    assertThat(document.crt()).isEqualTo("3");
                    assertThat(document.issueDate()).isEqualTo(LocalDate.of(2026, 7, 26));
                });
        assertTaxGroup("c1115-crt3-antes-vigencia.xml",
                null, null, false, false, false, false, false);
        assertThat(parser.parse(fixture("c1115-simples-sem-grupo.xml")).document().crt())
                .isEqualTo("1");
        assertTaxGroup("c1115-simples-sem-grupo.xml",
                null, null, false, false, false, false, false);

        assertTaxGroup("r1021-grupo-indevido.xml", "410", "410001", true, true,
                false, false, false);
        assertTaxGroup("c1021-sem-grupo-interno.xml", "410", "410001", true, false,
                false, false, false);
        assertTaxGroup("r1022-grupo-obrigatorio-ausente.xml", "200", "200030", true, false,
                false, false, false);
        assertTaxGroup("c1022-com-grupo-interno.xml", "200", "200030", true, true,
                true, true, true);
        assertTaxGroup("r1024-classtrib-incompativel-cst.xml", "000", "200030", true, true,
                false, false, false);
        assertTaxGroup("c1024-classtrib-compativel-cst.xml", "000", "000001", true, true,
                false, false, false);
        assertThat(parser.parse(fixture("r1024-simples-grupo-invalido.xml")).document().crt())
                .isEqualTo("1");
        assertTaxGroup("r1024-simples-grupo-invalido.xml", "000", "200030", true, true,
                false, false, false);
        assertThat(parser.parse(fixture("c1024-simples-grupo-valido.xml")).document().crt())
                .isEqualTo("1");
        assertTaxGroup("c1024-simples-grupo-valido.xml", "000", "000001", true, true,
                false, false, false);
        assertTaxGroup("r1025-classtrib-modelo.xml", "000", "000003", true, true,
                false, false, false);
        assertTaxGroup("c1025-classtrib-permitida-modelo.xml", "000", "000001", true, true,
                false, false, false);

        assertTaxGroup("r1033-reducao-uf-ausente.xml", "200", "200030", true, true,
                false, true, true);
        assertTaxGroup("r1074-reducao-municipio-ausente.xml", "200", "200030", true, true,
                true, false, true);
        assertTaxGroup("r1079-reducao-cbs-ausente.xml", "200", "200030", true, true,
                true, true, false);
        assertTaxGroup("r1034-percentual-uf-invalido.xml", "200", "200030", true, true,
                true, true, true);
        assertTaxGroup("r1046-percentual-municipio-invalido.xml", "200", "200030", true, true,
                true, true, true);
        assertTaxGroup("r1063-percentual-cbs-invalido.xml", "200", "200030", true, true,
                true, true, true);
        assertTaxGroup("c1033-reducao-uf-presente.xml", "200", "200030", true, true,
                true, true, true);
        assertTaxGroup("c1074-reducao-municipio-presente.xml", "200", "200030", true, true,
                true, true, true);
        assertTaxGroup("c1079-reducao-cbs-presente.xml", "200", "200030", true, true,
                true, true, true);
        assertTaxGroup("c1034-percentual-uf-correto.xml", "200", "200030", true, true,
                true, true, true);
        assertTaxGroup("c1046-percentual-municipio-correto.xml", "200", "200030", true, true,
                true, true, true);
        assertTaxGroup("c1063-percentual-cbs-correto.xml", "200", "200030", true, true,
                true, true, true);

        assertReductionPercentages("r1034-percentual-uf-invalido.xml", "59.99", "60.00", "60.00");
        assertReductionPercentages("r1046-percentual-municipio-invalido.xml", "60.00", "59.99", "60.00");
        assertReductionPercentages("r1063-percentual-cbs-invalido.xml", "60.00", "60.00", "59.99");

        // 1118/1119 (W34-10/W34-20): coerência entre total/IBSCBSTot (documento) e IBSCBS (item).
        assertTaxGroup("r1118-total-sem-item.xml", null, null, false, false, false, false, false);
        assertHasIbsCbsTot("r1118-total-sem-item.xml", true);
        assertTaxGroup("c1118-total-com-item.xml", "000", "000001", true, true,
                false, false, false);
        assertHasIbsCbsTot("c1118-total-com-item.xml", true);
        assertTaxGroup("r1119-item-sem-total.xml", "000", "000001", true, true,
                false, false, false);
        assertHasIbsCbsTot("r1119-item-sem-total.xml", false);
        assertTaxGroup("c1119-item-com-total.xml", "000", "000001", true, true,
                false, false, false);
        assertHasIbsCbsTot("c1119-item-com-total.xml", true);
    }

    @Test
    void everyPositiveFixtureFiresOnlyItsOwnRejection() {
        for (Case testCase : POSITIVE_CASES) {
            assertThat(findings(testCase.file()))
                    .as("findings of %s", testCase.file())
                    .extracting(Finding::kind).containsOnly(FindingKind.REJECTION_RULE);
            assertThat(rejectionCodes(testCase.file()))
                    .as("rejection codes of %s", testCase.file())
                    .containsExactlyElementsOf(testCase.rejectionCodes());
        }
    }

    @Test
    void everyControlFixtureAndTheCanonicalDocumentAreClean() {
        for (String file : CONTROL_CASES) {
            assertThat(findings(file)).as("findings of %s", file).isEmpty();
        }
        Path canonical = Path.of("src/test/resources/fixtures/nfe-valida.xml");
        assertThat(rules.evaluate(parser.parse(canonical).document(), extractor.extract(canonical)).findings())
                .as("findings of the canonical document").isEmpty();
    }

    private static Set<String> expectedFiles() {
        return Stream.concat(POSITIVE_CASES.stream().map(Case::file), CONTROL_CASES.stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Path fixture(String file) {
        return FIXTURES.resolve(file);
    }

    private static List<Finding> findings(String file) {
        Path xml = fixture(file);
        return rules.evaluate(parser.parse(xml).document(), extractor.extract(xml)).findings();
    }

    private static List<String> rejectionCodes(String file) {
        return findings(file).stream()
                .filter(finding -> finding.kind() == FindingKind.REJECTION_RULE)
                .map(Finding::rejectionCode)
                .toList();
    }

    private static void assertTaxGroup(String file, String cst, String classTrib, boolean wrapper,
            boolean innerGroup, boolean reductionUf, boolean reductionMun, boolean reductionCbs) {
        assertThat(extractor.extract(fixture(file))).as("items of %s", file).singleElement()
                .satisfies(item -> {
                    assertThat(item.cst()).isEqualTo(cst);
                    assertThat(item.cClassTrib()).isEqualTo(classTrib);
                    assertThat(item.hasIbsCbsGroup()).isEqualTo(wrapper);
                    assertThat(item.hasGIbsCbsGroup()).isEqualTo(innerGroup);
                    assertThat(item.hasReducaoUf()).isEqualTo(reductionUf);
                    assertThat(item.hasReducaoMun()).isEqualTo(reductionMun);
                    assertThat(item.hasReducaoCbs()).isEqualTo(reductionCbs);
                });
    }

    private static void assertHasIbsCbsTot(String file, boolean expected) {
        assertThat(parser.parse(fixture(file)).document().hasIbsCbsTot())
                .as("hasIbsCbsTot of %s", file).isEqualTo(expected);
    }

    private static void assertReductionPercentages(String file, String uf, String mun, String cbs) {
        assertThat(extractor.extract(fixture(file))).as("items of %s", file).singleElement()
                .satisfies(item -> {
                    assertThat(item.percReducaoUf()).isEqualByComparingTo(uf);
                    assertThat(item.percReducaoMun()).isEqualByComparingTo(mun);
                    assertThat(item.percReducaoCbs()).isEqualByComparingTo(cbs);
                });
    }
}
