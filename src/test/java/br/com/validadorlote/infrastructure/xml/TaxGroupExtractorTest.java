package br.com.validadorlote.infrastructure.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TaxGroupExtractorTest {

    private final TaxGroupExtractor extractor = new TaxGroupExtractor();
    private final XmlMetadataParser parser = new XmlMetadataParser();

    private Path fixture(String nome) {
        return Path.of("src/test/resources/fixtures/" + nome);
    }

    @Test
    void extractsCrtFromDocument() {
        var doc = parser.parse(fixture("nfe-valida.xml")).document();
        assertThat(doc.crt()).isEqualTo("3");
    }

    @Test
    void crtAusenteViraNull() {
        var doc = parser.parse(fixture("nfe-minima-invalida.xml")).document();
        assertThat(doc.crt()).isNull();
    }

    @Test
    void readsIbsCbsGroupOfEachItem() {
        var grupos = extractor.extract(fixture("nfe-valida.xml"));

        assertThat(grupos).singleElement().satisfies(g -> {
            assertThat(g.itemNumber()).isEqualTo(1);
            assertThat(g.hasIbsCbsGroup()).isTrue();
            assertThat(g.cst()).isEqualTo("000");
            assertThat(g.cClassTrib()).isEqualTo("000001");
        });
    }

    @Test
    void detectsItemWithoutTheGroup() {
        // O caso dominante de 03/08: CRT=3 e nenhum grupo IBS/CBS.
        var grupos = extractor.extract(fixture("nfe-crt3-sem-ibscbs.xml"));

        assertThat(grupos).singleElement().satisfies(g -> {
            assertThat(g.hasIbsCbsGroup()).isFalse();
            assertThat(g.cst()).isNull();
            assertThat(g.cClassTrib()).isNull();
        });
    }

    @Test
    void grupoPresenteSemCstEDistinguivelDeGrupoAusente(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("grupo-sem-cst.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det nItem="1"><imposto><IBSCBS><cClassTrib>000001</cClassTrib></IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        // Sem o grupo, a rejeição é "grupo ausente"; com o grupo e sem CST, é outra rejeição.
        // Os dois casos não podem colapsar no mesmo estado.
        assertThat(extractor.extract(xml)).singleElement().satisfies(g -> {
            assertThat(g.hasIbsCbsGroup()).isTrue();
            assertThat(g.cst()).isNull();
            assertThat(g.cClassTrib()).isEqualTo("000001");
        });
        assertThat(extractor.extract(fixture("nfe-crt3-sem-ibscbs.xml")).getFirst().hasIbsCbsGroup())
                .isFalse();
    }

    @Test
    void readsReductionSubgroupsWhenPresent() {
        var g = extractor.extract(fixture("nfe-valida.xml")).getFirst();

        // A fixture canônica não tem gRed — os subgrupos precisam sair como ausentes,
        // não como null ambíguo.
        assertThat(g.hasReducaoUf()).isFalse();
        assertThat(g.hasReducaoMun()).isFalse();
        assertThat(g.hasReducaoCbs()).isFalse();
    }

    @Test
    void reducaoFicaNaEsferaEmQueFoiDeclarada() {
        var g = extractor.extract(fixture("nfe-reducao-por-esfera.xml")).getFirst();

        assertThat(g.itemNumber()).isEqualTo(1);
        assertThat(g.hasReducaoUf()).isTrue();
        assertThat(g.percReducaoUf()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(g.hasReducaoMun()).isFalse();
        assertThat(g.percReducaoMun()).isNull();
        assertThat(g.hasReducaoCbs()).isFalse();
        assertThat(g.percReducaoCbs()).isNull();
    }

    @Test
    void reducaoForaDeQualquerEsferaNaoContaminaNenhuma() {
        // O gRed do item 2 está solto em gIBSCBS, fora de gIBSUF/gIBSMun/gCBS. Atribuí-lo a uma
        // esfera seria veredito fiscal inventado — e a esfera "lembrada" do item anterior é
        // justamente o vazamento que o reset de contexto impede.
        var itens = extractor.extract(fixture("nfe-reducao-por-esfera.xml"));

        assertThat(itens).hasSize(2);
        var g = itens.get(1);
        assertThat(g.itemNumber()).isEqualTo(2);
        assertThat(g.hasReducaoUf()).isFalse();
        assertThat(g.hasReducaoMun()).isFalse();
        assertThat(g.hasReducaoCbs()).isFalse();
        assertThat(g.percReducaoUf()).isNull();
        assertThat(g.percReducaoMun()).isNull();
        assertThat(g.percReducaoCbs()).isNull();
    }

    @Test
    void variosItensSaemNaOrdemDoDocumento() {
        var itens = extractor.extract(fixture("nfe-reducao-por-esfera.xml"));

        assertThat(itens).extracting(TaxGroupExtractor.ItemTaxGroup::itemNumber)
                .containsExactly(1, 2);
        assertThat(itens).extracting(TaxGroupExtractor.ItemTaxGroup::cst)
                .containsExactly("200", "000");
    }

    @Test
    void cstDePisCofinsNaoVazaParaOGrupoIbsCbs() {
        // PIS e COFINS também têm <CST>, e vêm antes do IBSCBS no mesmo <imposto>.
        var g = extractor.extract(fixture("nfe-valida.xml")).getFirst();
        assertThat(g.cst()).isEqualTo("000");
    }

    @Test
    void nItemInvalidoNaoDerrubaALeitura(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("nitem-invalido.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det nItem="abc"><imposto><IBSCBS><CST>000</CST></IBSCBS></imposto></det>
                  <det nItem="2"><imposto><IBSCBS><CST>200</CST></IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        // O item com nItem fora do contrato não vira entrada (o XSD reporta o erro real),
        // mas a leitura do restante do documento continua.
        assertThat(extractor.extract(xml)).singleElement().satisfies(g -> {
            assertThat(g.itemNumber()).isEqualTo(2);
            assertThat(g.cst()).isEqualTo("200");
        });
    }
}
