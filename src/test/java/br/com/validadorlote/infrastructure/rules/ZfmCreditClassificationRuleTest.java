package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ZfmCreditClassificationRuleTest {

    private final ZfmCreditClassificationRule rule = new ZfmCreditClassificationRule();

    @Test
    void rejectsTheClassificationInNfeOutsideTheAllowedAreas() {
        RuleOutcome outcome = rule.evaluate(context("55", "SP", "3526902", true));

        assertThat(outcome).isInstanceOf(RuleOutcome.Rejeitado.class);
        var rejected = (RuleOutcome.Rejeitado) outcome;
        assertThat(rejected.rejectionCode()).isEqualTo("1166");
        assertThat(rejected.ruleId()).isEqualTo("I05k-20");
        assertThat(rejected.officialMessage()).contains("subapuração do IBS na ZFM");
    }

    @Test
    void permitsTheAllowedStatesAndTheTwoAllowedMunicipalitiesInAmapa() {
        assertThat(rule.evaluate(context("55", "AM", "1302603", true)))
                .isInstanceOf(RuleOutcome.Conforme.class);
        assertThat(rule.evaluate(context("55", "AP", "1600303", true)))
                .isInstanceOf(RuleOutcome.Conforme.class);
        assertThat(rule.evaluate(context("55", "AP", "1600600", true)))
                .isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void doesNotAccuseWhenTheLocationRequiredForAmapaIsAbsent() {
        assertThat(rule.evaluate(context("55", "AP", null, true)))
                .isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void isNotTheRuleForNfceOrForAnItemWithoutTheTag() {
        assertThat(rule.evaluate(context("65", "SP", "3526902", true)))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
        assertThat(rule.evaluate(context("55", "SP", "3526902", false)))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void predicts1166FromTheSameI05kPathUsedByTheRejectedDocument(@TempDir Path dir)
            throws IOException {
        Path xml = dir.resolve("i05k-sp.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <ide><mod>55</mod><dhEmi>2026-07-31T00:00:00-03:00</dhEmi></ide>
                  <emit><enderEmit><cMun>3526902</cMun><UF>SP</UF></enderEmit></emit>
                  <det nItem="1"><prod><tpCredPresIBSZFM>0</tpCredPresIBSZFM></prod>
                    <imposto><IBSCBS><CST>000</CST><cClassTrib>000001</cClassTrib></IBSCBS></imposto>
                  </det><total><IBSCBSTot><vBCIBSCBS>0</vBCIBSCBS></IBSCBSTot></total>
                </infNFe></NFe>
                """);
        var metadata = new XmlMetadataParser().parse(xml).document();
        var items = new TaxGroupExtractor().extract(xml);

        assertThat(new RuleEngine(FiscalTables.load()).evaluate(metadata, items).findings())
                .extracting(finding -> finding.rejectionCode()).contains("1166");
    }

    @Test
    void keeps1165OnTheSameProductPathForNfce(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("i05k-nfce.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <ide><mod>65</mod><dhEmi>2026-07-31T00:00:00-03:00</dhEmi></ide>
                  <emit><enderEmit><cMun>3526902</cMun><UF>SP</UF></enderEmit></emit>
                  <det nItem="1"><prod><tpCredPresIBSZFM>0</tpCredPresIBSZFM></prod>
                    <imposto><IBSCBS><CST>000</CST><cClassTrib>000001</cClassTrib></IBSCBS></imposto>
                  </det><total><IBSCBSTot><vBCIBSCBS>0</vBCIBSCBS></IBSCBSTot></total>
                </infNFe></NFe>
                """);

        assertThat(new RuleEngine(FiscalTables.load()).evaluate(
                new XmlMetadataParser().parse(xml).document(), new TaxGroupExtractor().extract(xml)).findings())
                .extracting(finding -> finding.rejectionCode()).contains("1165");
    }

    private RuleContext context(String model, String state, String municipality, boolean informed) {
        FiscalDocument document = new FiscalDocument(Path.of("example.xml"), "key", "14200166000187",
                "Emitente", state, municipality, "1", LocalDate.of(2026, 8, 3), model, "1",
                "NFe", "3", null, null, false, null, true, List.of());
        ItemTaxGroup item = new ItemTaxGroup(1, true, true, "000", "000001", null,
                false, false, false, null, null, null, null, false, false, false,
                false, false, false, false, false, informed, false);
        return new RuleContext(document, item, null, LocalDate.of(2026, 8, 3));
    }
}
