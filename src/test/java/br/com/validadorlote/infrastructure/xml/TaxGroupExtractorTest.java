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

        // O item com nItem fora do contrato continua existindo, com o número desconhecido
        // explícito em vez de inventado a partir da posição. Sumir com ele seria o silêncio
        // que o relatório proíbe: nem conforme, nem rejeitado, nem não avaliado.
        var itens = extractor.extract(xml);

        assertThat(itens).hasSize(2);
        assertThat(itens.getFirst().itemNumber()).isNull();
        assertThat(itens.getFirst().cst()).isEqualTo("000");
        assertThat(itens.get(1).itemNumber()).isEqualTo(2);
        assertThat(itens.get(1).cst()).isEqualTo("200");
    }

    @Test
    void detSemNItemAindaProduzItem(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("sem-nitem.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det><imposto><IBSCBS><CST>200</CST><cClassTrib>200001</cClassTrib></IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        assertThat(extractor.extract(xml)).singleElement().satisfies(g -> {
            assertThat(g.itemNumber()).isNull();
            assertThat(g.hasIbsCbsGroup()).isTrue();
            assertThat(g.cst()).isEqualTo("200");
            assertThat(g.cClassTrib()).isEqualTo("200001");
        });
    }

    @Test
    void involucroEGrupoInternoSaoCamposDistintos(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("involucro-vs-grupo.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det nItem="1"><imposto><IBSCBS><CST>000</CST><cClassTrib>000001</cClassTrib>
                    <gIBSCBS><vBC>200.00</vBC></gIBSCBS>
                  </IBSCBS></imposto></det>
                  <det nItem="2"><imposto><IBSCBS><CST>400</CST><cClassTrib>400001</cClassTrib>
                  </IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        // O item 2 é uma isenção corretamente emitida: o invólucro existe porque ele carrega o
        // CST, e o gIBSCBS não vem. Colapsar os dois campos faz esse item virar acusação.
        var itens = extractor.extract(xml);

        assertThat(itens).hasSize(2);
        assertThat(itens.getFirst().hasIbsCbsGroup()).isTrue();
        assertThat(itens.getFirst().hasGIbsCbsGroup()).isTrue();
        assertThat(itens.get(1).hasIbsCbsGroup()).isTrue();
        assertThat(itens.get(1).hasGIbsCbsGroup()).isFalse();
        assertThat(itens.get(1).cst()).isEqualTo("400");
    }

    @Test
    void grupoInternoDaFixtureCanonicaEReconhecido() {
        var g = extractor.extract(fixture("nfe-valida.xml")).getFirst();

        assertThat(g.hasIbsCbsGroup()).isTrue();
        assertThat(g.hasGIbsCbsGroup()).isTrue();
    }

    @Test
    void semInvolucroNaoHaGrupoInterno() {
        var g = extractor.extract(fixture("nfe-crt3-sem-ibscbs.xml")).getFirst();

        assertThat(g.hasIbsCbsGroup()).isFalse();
        assertThat(g.hasGIbsCbsGroup()).isFalse();
    }

    @Test
    void gIbsCbsMonoNaoContaComoGrupoInterno(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("mono.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det nItem="1"><imposto><IBSCBS><CST>620</CST><cClassTrib>620001</cClassTrib>
                    <gIBSCBSMono><vIBSMono>1.00</vIBSMono></gIBSCBSMono>
                  </IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        // gIBSCBSMono é outra alternativa do choice, com indicador e rejeições próprios
        // (1151/1116). Contá-la como gIBSCBS misturaria dois julgamentos diferentes.
        assertThat(extractor.extract(xml)).singleElement()
                .satisfies(g -> assertThat(g.hasGIbsCbsGroup()).isFalse());
    }

    @Test
    void cProdAnpELidoPorItem(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("combustivel.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det nItem="1"><prod><cProd>1</cProd>
                    <comb><cProdANP>210203001</cProdANP><descANP>GLP</descANP></comb>
                  </prod><imposto><IBSCBS><CST>620</CST></IBSCBS></imposto></det>
                  <det nItem="2"><prod><cProd>2</cProd></prod>
                    <imposto><IBSCBS><CST>000</CST></IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        // O cProdANP do item 1 não pode vazar para o item 2: a Exceção 2 da UB12-10 é por item.
        var itens = extractor.extract(xml);

        assertThat(itens.getFirst().cProdANP()).isEqualTo("210203001");
        assertThat(itens.get(1).cProdANP()).isNull();
    }

    @Test
    void conteudoMistoNumCampoNaoDescartaODocumento(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("conteudo-misto.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det nItem="1"><imposto><IBSCBS>
                    <CST>0<x>0</x>0</CST><cClassTrib>000001</cClassTrib>
                  </IBSCBS></imposto></det>
                  <det nItem="2"><imposto><IBSCBS><CST>200</CST></IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        // Um campo ilegível não pode custar o arquivo inteiro: o XSD é quem reporta o erro
        // estrutural, com mensagem oficial, linha e coluna. Aqui o campo vira null e os
        // demais itens seguem íntegros.
        var itens = extractor.extract(xml);

        assertThat(itens).hasSize(2);
        assertThat(itens.getFirst().hasIbsCbsGroup()).isTrue();
        assertThat(itens.getFirst().cst()).isNull();
        assertThat(itens.getFirst().cClassTrib()).isEqualTo("000001");
        assertThat(itens.get(1).cst()).isEqualTo("200");
    }
}
