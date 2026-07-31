package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TaxTotalizationRuleTest {

    @Test
    void predicts1080WhenTheUfTotalDiffersFromItsItems(@TempDir Path dir) throws IOException {
        Path xml = write(dir, "0.00", "0.00", "0.90", "0.10", "0.00", "0.90");
        var document = new XmlMetadataParser().parse(xml).document();
        var items = new TaxGroupExtractor().extract(xml);

        var outcome = new TaxTotalizationRule(TaxTotalizationRule.Sphere.IBS_UF)
                .evaluate(document, items);

        assertThat(outcome).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat((RuleOutcome.Rejeitado) outcome).satisfies(rejection -> {
            assertThat(rejection.rejectionCode()).isEqualTo("1080");
            assertThat(rejection.ruleId()).isEqualTo("W41-10");
            assertThat(rejection.officialMessage())
                    .isEqualTo("Rejeição: Total de IBS UF difere da soma dos itens");
        });
    }

    @Test
    void enginePublishes1080ForTheSameDivergence(@TempDir Path dir) throws IOException {
        Path xml = write(dir, "0.00", "0.00", "0.90", "0.10", "0.00", "0.90");
        var document = new XmlMetadataParser().parse(xml).document();
        var items = new TaxGroupExtractor().extract(xml);

        assertThat(new RuleEngine(FiscalTables.load()).evaluate(document, items).findings())
                .anySatisfy(finding -> {
                    assertThat(finding.rejectionCode()).isEqualTo("1080");
                    assertThat(finding.ruleId()).isEqualTo("W41-10");
                });
    }

    @Test
    void predicts1085WhenTheGeneralIbsTotalDiffersFromItsItems(@TempDir Path dir) throws IOException {
        Path xml = write(dir, "0.10", "0.00", "0.90", "0.10", "0.00", "0.90");
        Files.writeString(xml, Files.readString(xml)
                .replace("<vIBS>0.10</vIBS></gIBS>", "<vIBS>0.00</vIBS></gIBS>"));
        var document = new XmlMetadataParser().parse(xml).document();
        var items = new TaxGroupExtractor().extract(xml);

        assertThat(new TaxTotalizationRule(TaxTotalizationRule.Sphere.IBS)
                .evaluate(document, items))
                .extracting(outcome -> (RuleOutcome.Rejeitado) outcome)
                .extracting(RuleOutcome.Rejeitado::rejectionCode)
                .isEqualTo("1085");
    }

    @Test
    void verifiesTheThreeDeclaredTotalsIndependently(@TempDir Path dir) throws IOException {
        Path xml = write(dir, "0.10", "0.00", "0.90", "0.10", "0.00", "0.90");
        var document = new XmlMetadataParser().parse(xml).document();
        var items = new TaxGroupExtractor().extract(xml);

        for (TaxTotalizationRule.Sphere sphere : TaxTotalizationRule.Sphere.values()) {
            assertThat(new TaxTotalizationRule(sphere).evaluate(document, items))
                    .as(sphere.name()).isInstanceOf(RuleOutcome.Conforme.class);
        }
    }

    @Test
    void missingItemValueIsNotTurnedIntoZeroOrARejection(@TempDir Path dir) throws IOException {
        Path xml = write(dir, "0.10", "0.00", "0.90", null, "0.00", "0.90");
        var document = new XmlMetadataParser().parse(xml).document();
        var items = new TaxGroupExtractor().extract(xml);

        assertThat(new TaxTotalizationRule(TaxTotalizationRule.Sphere.IBS_UF)
                .evaluate(document, items))
                .isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    private Path write(Path dir, String totalUf, String totalMun, String totalCbs,
            String itemUf, String itemMun, String itemCbs) throws IOException {
        Path xml = dir.resolve("totalizacao.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe Id="NFe35260724478942000148550010000001041154748111">
                  <ide><mod>55</mod><dhEmi>2026-07-31T10:00:00-03:00</dhEmi></ide>
                  <emit><CRT>3</CRT></emit>
                  <det nItem="1"><prod/><imposto><IBSCBS><CST>000</CST><cClassTrib>000001</cClassTrib><gIBSCBS>
                    <gIBSUF>%s</gIBSUF><gIBSMun>%s</gIBSMun><vIBS>0.10</vIBS><gCBS>%s</gCBS>
                  </gIBSCBS></IBSCBS></imposto></det>
                  <total><IBSCBSTot><gIBS><gIBSUF><vIBSUF>%s</vIBSUF></gIBSUF>
                    <gIBSMun><vIBSMun>%s</vIBSMun></gIBSMun><vIBS>0.10</vIBS></gIBS>
                    <gCBS><vCBS>%s</vCBS></gCBS></IBSCBSTot></total>
                </infNFe></NFe>
                """.formatted(valueTag("vIBSUF", itemUf), valueTag("vIBSMun", itemMun),
                valueTag("vCBS", itemCbs), totalUf, totalMun, totalCbs));
        return xml;
    }

    private String valueTag(String name, String value) {
        return value == null ? "" : "<" + name + ">" + value + "</" + name + ">";
    }
}
