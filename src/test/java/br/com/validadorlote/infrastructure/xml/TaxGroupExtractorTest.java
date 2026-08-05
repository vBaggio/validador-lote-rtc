package br.com.validadorlote.infrastructure.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;

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
    void detectsTheZfmClassificationWhenItIsInTheProductAsDefinedByI05k(@TempDir Path dir)
            throws IOException {
        Path xml = dir.resolve("i05k.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det nItem="1"><prod><tpCredPresIBSZFM>0</tpCredPresIBSZFM></prod>
                    <imposto><IBSCBS><CST>000</CST><cClassTrib>000001</cClassTrib></IBSCBS></imposto>
                  </det>
                </infNFe></NFe>
                """);

        assertThat(extractor.extract(xml)).singleElement()
                .satisfies(group -> assertThat(group.hasTpCredPresIbsZfm()).isTrue());
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
    void readsPresumedCreditCodeAndIbsValueWithinItsOwnGroup(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("credito-presumido.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det nItem="1"><imposto><IBSCBS><CST>000</CST><cClassTrib>000001</cClassTrib>
                    <gCredPresOper><cCredPres>4</cCredPres><gIBSCredPres>
                      <vCredPres>0.20</vCredPres>
                    </gIBSCredPres></gCredPresOper>
                    <gIBSCBS><gIBSUF><vIBSUF>0.50</vIBSUF></gIBSUF>
                      <gIBSMun><vIBSMun>0.60</vIBSMun></gIBSMun><vIBS>0.90</vIBS></gIBSCBS>
                  </IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        assertThat(extractor.extract(xml)).singleElement().satisfies(group -> {
            assertThat(group.presumedCreditCode()).isEqualTo("4");
            assertThat(group.presumedIbsCredit()).isEqualByComparingTo("0.20");
            assertThat(group.valueIbs()).isEqualByComparingTo("0.90");
        });
    }

    @Test
    void readsItemLevelEstornoCredByItsRealTagNames(@TempDir Path dir) throws IOException {
        // W59f-10/1176 e W59g-10/1177 comparam o total contra a soma de "gEstornoCred/vIBS"
        // (texto literal da NT) — mas o XSD nomeia os campos "vIBSEstCred"/"vCBSEstCred"
        // (TEstornoCred, DFeTiposBasicos_v1.00.xsd:1510-1519), não "vIBS"/"vCBS" simples.
        Path xml = dir.resolve("estorno.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det nItem="1"><imposto><IBSCBS><CST>000</CST><cClassTrib>000001</cClassTrib>
                    <gEstornoCred><vIBSEstCred>1.00</vIBSEstCred><vCBSEstCred>2.00</vCBSEstCred></gEstornoCred>
                  </IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        var g = extractor.extract(xml).getFirst();

        assertThat(g.hasEstornoCred()).isTrue();
        assertThat(g.estornoCredIbs()).isEqualByComparingTo("1.00");
        assertThat(g.estornoCredCbs()).isEqualByComparingTo("2.00");
        assertThat(g.declaredAmounts()).containsEntry("vIBSEstCred", new BigDecimal("1.00"));
        assertThat(g.declaredAmounts()).containsEntry("vCBSEstCred", new BigDecimal("2.00"));
    }

    @Test
    void readsConditionalGroupsAndValuesPerItemWithoutStateLeak() {
        var items = extractor.extract(fixture("nfe-grupos-condicionais-itens.xml"));

        assertThat(items).hasSize(8);
        assertThat(items.get(0)).satisfies(item -> {
            assertThat(item.hasGIbsCbsMono()).isTrue();
            assertThat(item.hasIndBemMovelUsado()).isTrue();
            assertThat(item.indBemMovelUsado()).isEqualTo("1");
        });
        assertThat(items.get(1)).satisfies(item -> {
            assertThat(item.hasTransfCred()).isTrue();
            assertThat(item.hasIndBemMovelUsado()).isTrue();
            assertThat(item.indBemMovelUsado()).isNull();
            assertThat(item.hasGIbsCbsMono()).isFalse();
        });
        assertThat(items.get(2)).satisfies(item -> {
            assertThat(item.hasAjusteCompet()).isTrue();
            assertThat(item.ajusteCompetIbs()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.ajusteCompetCbs()).isEqualByComparingTo("1.25");
            assertThat(item.hasTransfCred()).isFalse();
        });
        assertThat(items.get(3)).satisfies(item -> {
            assertThat(item.hasTribRegular()).isTrue();
            assertThat(item.hasAjusteCompet()).isFalse();
            assertThat(item.ajusteCompetIbs()).isNull();
            assertThat(item.ajusteCompetCbs()).isNull();
        });
        assertThat(items.get(4)).satisfies(item -> {
            assertThat(item.hasEstornoCred()).isTrue();
            assertThat(item.estornoCredIbs()).isEqualByComparingTo("2.00");
            assertThat(item.estornoCredCbs()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(item.hasTribRegular()).isFalse();
        });
        assertThat(items.get(5)).satisfies(item -> {
            assertThat(item.hasGIbsCbsMono()).isFalse();
            assertThat(item.hasTransfCred()).isFalse();
            assertThat(item.hasAjusteCompet()).isFalse();
            assertThat(item.hasEstornoCred()).isFalse();
            assertThat(item.hasTribRegular()).isFalse();
            assertThat(item.hasIndBemMovelUsado()).isFalse();
            assertThat(item.indBemMovelUsado()).isNull();
            assertThat(item.ajusteCompetIbs()).isNull();
            assertThat(item.ajusteCompetCbs()).isNull();
            assertThat(item.estornoCredIbs()).isNull();
            assertThat(item.estornoCredCbs()).isNull();
        });
        assertThat(items.get(6)).satisfies(item -> {
            assertThat(item.hasAjusteCompet()).isTrue();
            assertThat(item.ajusteCompetIbs()).isNull();
            assertThat(item.ajusteCompetCbs()).isNull();
        });
        assertThat(items.get(7)).satisfies(item -> {
            assertThat(item.hasEstornoCred()).isTrue();
            assertThat(item.estornoCredIbs()).isNull();
            assertThat(item.estornoCredCbs()).isNull();
            assertThat(item.declaredAmounts())
                    .doesNotContainKeys("vIBSEstCred", "vCBSEstCred");
        });
    }

    @Test
    void homonymsOutsideOfficialParentsOrNamespaceDoNotCount(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("homonimos.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe" xmlns:x="urn:not-nfe"><infNFe>
                  <det nItem="1"><prod>
                    <gIBSCBSMono/><gTransfCred/><gAjusteCompet/><gEstornoCred/><gTribRegular/>
                  </prod><imposto><IBSCBS><CST>000</CST><cClassTrib>000001</cClassTrib>
                    <x:gIBSCBSMono/><x:gTransfCred/><x:gAjusteCompet/><x:gEstornoCred/>
                    <x:wrapper><gIBSCBSMono/></x:wrapper>
                    <gIBSCBS><x:gTribRegular/><gIBSCBSMono/><gTransfCred/>
                      <gAjusteCompet/><gEstornoCred/><wrapper><gTribRegular/></wrapper>
                    </gIBSCBS>
                  </IBSCBS><indBemMovelUsado>1</indBemMovelUsado></imposto></det>
                  <total><gAjusteCompet><vIBS>9.00</vIBS><vCBS>8.00</vCBS></gAjusteCompet>
                    <gEstornoCred><vIBSEstCred>7.00</vIBSEstCred><vCBSEstCred>6.00</vCBSEstCred></gEstornoCred>
                  </total>
                </infNFe></NFe>
                """);

        assertThat(extractor.extract(xml)).singleElement().satisfies(item -> {
            assertThat(item.hasGIbsCbsMono()).isFalse();
            assertThat(item.hasTransfCred()).isFalse();
            assertThat(item.hasAjusteCompet()).isFalse();
            assertThat(item.hasEstornoCred()).isFalse();
            assertThat(item.hasTribRegular()).isFalse();
            assertThat(item.hasIndBemMovelUsado()).isFalse();
            assertThat(item.indBemMovelUsado()).isNull();
            assertThat(item.ajusteCompetIbs()).isNull();
            assertThat(item.ajusteCompetCbs()).isNull();
            assertThat(item.estornoCredIbs()).isNull();
            assertThat(item.estornoCredCbs()).isNull();
        });
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

    // ---- DFeReferenciado: NT v1.40 move o referenciamento de devolução para o item (D-038) ----

    @Test
    void itemWithoutDFeReferenciadoHasNullReference() {
        var g = extractor.extract(fixture("nfe-valida.xml")).getFirst();
        assertThat(g.dfeReferenciado()).isNull();
    }

    @Test
    void readsDFeReferenciadoOfDevolutionItem(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("devolucao-dfe.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det nItem="1"><prod><cProd>1</cProd>
                    <DFeReferenciado><chaveAcesso>35251214200166000187550010000000015123456789</chaveAcesso><nItem>1</nItem></DFeReferenciado>
                  </prod><imposto><IBSCBS><CST>000</CST></IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        // AAMM 2512 nas posições 2-5 da chave: dezembro de 2025, mesma decodificação de refNFe.
        var g = extractor.extract(xml).getFirst();

        assertThat(g.dfeReferenciado()).isNotNull();
        assertThat(g.dfeReferenciado().form()).isEqualTo("DFeReferenciado");
        assertThat(g.dfeReferenciado().issuedAt()).isEqualTo(YearMonth.of(2025, 12));
    }

    @Test
    void dfeReferenciadoWithUnreadableKeyIsPresentButUndated(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("devolucao-chave-invalida.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det nItem="1"><prod><cProd>1</cProd>
                    <DFeReferenciado><chaveAcesso>chave-invalida</chaveAcesso></DFeReferenciado>
                  </prod><imposto><IBSCBS><CST>000</CST></IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        // A referência existe (o item declarou o grupo) mas a chave não é decodável: presença
        // sem data, nunca ausência — quem julga isso como Rejeitado ou não é a regra, não o
        // parser (D-038).
        var g = extractor.extract(xml).getFirst();

        assertThat(g.dfeReferenciado()).isNotNull();
        assertThat(g.dfeReferenciado().issuedAt()).isNull();
    }

    @Test
    void dfeReferenciadoWithoutChaveAcessoIsPresentButUndated(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("dfe-sem-chave.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det nItem="1"><prod><cProd>1</cProd>
                    <DFeReferenciado><nItem>1</nItem></DFeReferenciado>
                  </prod><imposto><IBSCBS><CST>000</CST></IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        // O XSD exige chaveAcesso dentro de DFeReferenciado, mas a extração de metadados é
        // deliberadamente tolerante a XML estruturalmente inválido (quem reporta o erro
        // estrutural é o XSD): o grupo em si está presente, então a referência não pode sumir.
        var g = extractor.extract(xml).getFirst();

        assertThat(g.dfeReferenciado()).isNotNull();
        assertThat(g.dfeReferenciado().issuedAt()).isNull();
    }

    @Test
    void dfeReferenciadoDoesNotLeakToOtherItems(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("dois-itens-dfe.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <det nItem="1"><prod><cProd>1</cProd>
                    <DFeReferenciado><chaveAcesso>35251214200166000187550010000000015123456789</chaveAcesso></DFeReferenciado>
                  </prod><imposto><IBSCBS><CST>000</CST></IBSCBS></imposto></det>
                  <det nItem="2"><prod><cProd>2</cProd></prod>
                    <imposto><IBSCBS><CST>000</CST></IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        var itens = extractor.extract(xml);

        assertThat(itens).hasSize(2);
        assertThat(itens.getFirst().dfeReferenciado()).isNotNull();
        assertThat(itens.get(1).dfeReferenciado()).isNull();
    }

    /**
     * Documenta o achado da sonda de mutação do fix de contexto (D-039, §"pré-requisito"): o
     * {@code det} zera {@code esfera}/{@code redUf}/{@code redMun}/{@code redCbs} de propósito
     * como primeiro efeito do próprio evento de abertura (antes de qualquer filho ser lido) — por
     * isso nenhum estado deixado por um {@code total/IBSCBSTot/gCBS} anterior sobrevive à abertura
     * do item seguinte, com ou sem a guarda de contexto. Comentar a guarda e reordenar
     * total/det aqui não derruba nenhuma asserção porque essa proteção independente já cobre o
     * caminho; ela não cobre a leitura de {@code total/IBSCBSTot} em si (que não existe nesta
     * classe — 1118/1119 leem a presença via {@link XmlMetadataParser}, D-039), nem um
     * {@code gCBS} de totais aninhado dentro de um {@code det} real (fora de escopo: exigiria
     * outro nome colidindo dentro do próprio item, não o caso que a auditoria descreve).
     */
    @Test
    void totalBeforeDetDoesNotPolluteTheFollowingItem(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("total-antes-do-det.xml");
        Files.writeString(xml, """
                <NFe xmlns="http://www.portalfiscal.inf.br/nfe"><infNFe>
                  <total><IBSCBSTot><gCBS><vDif>0.00</vDif><vDevTrib>0.00</vDevTrib><vCBS>0.00</vCBS><vCredPres>0.00</vCredPres><vCredPresCondSus>0.00</vCredPresCondSus></gCBS></IBSCBSTot></total>
                  <det nItem="1"><imposto><IBSCBS><CST>000</CST><cClassTrib>000001</cClassTrib>
                    <gIBSCBS><gIBSUF><gRed><pRedAliq>60.00</pRedAliq></gRed></gIBSUF></gIBSCBS>
                  </IBSCBS></imposto></det>
                </infNFe></NFe>
                """);

        var g = extractor.extract(xml).getFirst();

        assertThat(g.hasReducaoUf()).isTrue();
        assertThat(g.percReducaoUf()).isEqualByComparingTo("60.00");
        assertThat(g.hasReducaoCbs()).isFalse();
        assertThat(g.percReducaoCbs()).isNull();
    }
}
