package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.ReferencedNote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlMetadataParserTest {

    private final XmlMetadataParser parser = new XmlMetadataParser();

    private static final String KEY = "35200114200166000187550010000000015123456789";

    /** CNPJ do destinatário (dest) — deliberadamente diferente do emitente, para que o guard
     * de contexto pai (CNPJ/emit vs. CNPJ/dest) tenha um caso adverso real: se o guard cair,
     * emitterCnpj vira este valor em vez do do emit. */
    private static final String DEST_CNPJ = "99888777000166";

    private static final String NFE = String.join("\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",                                    // 1
            "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">",                            // 2
            "  <infNFe versao=\"4.00\" Id=\"NFe" + KEY + "\">",                              // 3
            "    <ide><cUF>35</cUF><mod>55</mod><nNF>15</nNF>",                              // 4
            "      <dhEmi>2026-07-20T10:00:00-03:00</dhEmi></ide>",                          // 5
            "    <dest><CNPJ>" + DEST_CNPJ + "</CNPJ><xNome>DESTINATARIO</xNome></dest>",     // 6 (antes do emit: caso adverso do guard)
            "    <emit><CNPJ>14200166000187</CNPJ><xNome>TESTE</xNome></emit>",              // 7
            "    <det nItem=\"1\">",                                                          // 8
            "      <prod><cProd>1</cProd></prod>",                                            // 9
            "      <imposto><IBSCBS><CST>000</CST></IBSCBS></imposto>",                       // 10
            "    </det>",                                                                     // 11
            "    <det nItem=\"2\">",                                                          // 12
            "      <prod><cProd>2</cProd></prod>",                                            // 13
            "    </det>",                                                                     // 14
            "    <total><IBSCBSTot><vIBS>0.00</vIBS></IBSCBSTot></total>",                    // 15
            "  </infNFe>",                                                                    // 16
            "</NFe>");                                                                        // 17

    /** O `<NFe>...</NFe>` sem a declaração XML, para aninhar em nfeProc/enviNFe. */
    private static final String NFE_BODY = NFE.substring(NFE.indexOf("<NFe"));

    private Path write(Path dir, String name, String content) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, content);
        return f;
    }

    @Test
    void extractsMetadataFromNfe(@TempDir Path dir) throws IOException {
        var doc = parser.parse(write(dir, "doc.xml", NFE)).document();

        assertThat(doc.accessKey()).isEqualTo(KEY);
        // <dest> vem antes de <emit> no fixture (leiaute real da NF-e) e tem um CNPJ diferente:
        // se o guard de contexto pai (CNPJ/emit) falhasse, este valor seria o do dest.
        assertThat(doc.emitterCnpj()).isEqualTo("14200166000187").isNotEqualTo(DEST_CNPJ);
        assertThat(doc.documentNumber()).isEqualTo("15");
        assertThat(doc.issueDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(doc.model()).isEqualTo("55");
        assertThat(doc.rootElement()).isEqualTo("NFe");
    }

    @Test
    void extractsMetadataFromNfceModel65(@TempDir Path dir) throws IOException {
        String nfce = NFE.replace("<mod>55</mod>", "<mod>65</mod>").replace("5500100000000151", "6500100000000151");
        var doc = parser.parse(write(dir, "nfce.xml", nfce)).document();

        assertThat(doc.model()).isEqualTo("65");
        assertThat(doc.accessKey()).contains("6500100000000151");
    }

    @Test
    void mapsLinesToItemRanges(@TempDir Path dir) throws IOException {
        var index = parser.parse(write(dir, "doc.xml", NFE)).itemIndex();

        assertThat(index.itemAt(4)).isNull();       // ide, antes do 1º det
        assertThat(index.itemAt(8)).isEqualTo(1);   // linha de abertura do det 1 (<det nItem="1">)
        assertThat(index.itemAt(9)).isEqualTo(1);   // dentro do det 1
        assertThat(index.itemAt(11)).isEqualTo(1);  // linha de fechamento do det 1 (</det>)
        assertThat(index.itemAt(12)).isEqualTo(2);  // linha de abertura do det 2 (<det nItem="2">)
        assertThat(index.itemAt(13)).isEqualTo(2);  // dentro do det 2
        assertThat(index.itemAt(14)).isEqualTo(2);  // linha de fechamento do det 2 (</det>)
        assertThat(index.itemAt(15)).isNull();      // total: IBSCBSTot não pertence a item
        assertThat(index.itemAt(17)).isNull();      // fecho do documento
    }

    @Test
    void acceptsNfeProcRoot(@TempDir Path dir) throws IOException {
        String xml = "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\">" + NFE_BODY + "</nfeProc>";
        var doc = parser.parse(write(dir, "proc.xml", xml)).document();

        assertThat(doc.rootElement()).isEqualTo("nfeProc");
        assertThat(doc.accessKey()).isEqualTo(KEY);
    }

    @Test
    void acceptsEnviNFeRootWithSingleNote(@TempDir Path dir) throws IOException {
        String xml = "<enviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<idLote>1</idLote>" + NFE_BODY + "</enviNFe>";
        var doc = parser.parse(write(dir, "lote1.xml", xml)).document();

        assertThat(doc.rootElement()).isEqualTo("enviNFe");
        assertThat(doc.accessKey()).isEqualTo(KEY);
        assertThat(doc.model()).isEqualTo("55");
    }

    @Test
    void enviNFeWithSeveralNotesHasNullMetadata(@TempDir Path dir) throws IOException {
        String xml = "<enviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<idLote>1</idLote>" + NFE_BODY + NFE_BODY + "</enviNFe>";
        var doc = parser.parse(write(dir, "lote2.xml", xml)).document();

        // D-016: metadados da 1ª nota valeriam para todas — nulo é melhor que errado.
        assertThat(doc.accessKey()).isNull();
        assertThat(doc.emitterCnpj()).isNull();
        assertThat(doc.documentNumber()).isNull();
        assertThat(doc.model()).isNull();
        assertThat(doc.issueDate()).isNull();
        assertThat(doc.rootElement()).isEqualTo("enviNFe");
    }

    @Test
    void invalidItemNumberYieldsNoItemForThatRange(@TempDir Path dir) throws IOException {
        String xml = String.join("\n",
                "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">",     // 1
                "  <infNFe Id=\"NFe" + KEY + "\">",                       // 2
                "    <det nItem=\"1\"><prod/></det>",                     // 3
                "    <det nItem=\"abc\"><prod/></det>",                   // 4
                "    <det nItem=\"99999999999\"><prod/></det>",           // 5
                "    <total/>",                                            // 6
                "  </infNFe>",                                             // 7
                "</NFe>");                                                 // 8
        var index = parser.parse(write(dir, "bad-item.xml", xml)).itemIndex();

        assertThat(index.itemAt(3)).isEqualTo(1);
        assertThat(index.itemAt(4)).isNull(); // nItem não numérico: não herda o item anterior
        assertThat(index.itemAt(5)).isNull(); // nItem fora da faixa de int
        assertThat(index.itemAt(6)).isNull();
    }

    @Test
    void blankFieldDoesNotBlockTheRealValue(@TempDir Path dir) throws IOException {
        String xml = String.join("\n",
                "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">",
                "  <infNFe Id=\"NFe" + KEY + "\">",
                "    <ide><mod>   </mod><nNF/></ide>",
                "    <emit><CNPJ/></emit>",
                "    <ide><mod>65</mod><nNF>15</nNF></ide>",
                "    <emit><CNPJ>14200166000187</CNPJ></emit>",
                "  </infNFe>",
                "</NFe>");
        var doc = parser.parse(write(dir, "blank.xml", xml)).document();

        assertThat(doc.emitterCnpj()).isEqualTo("14200166000187");
        assertThat(doc.model()).isEqualTo("65");
        assertThat(doc.documentNumber()).isEqualTo("15");
    }

    @Test
    void mixedContentFieldIsTreatedAsAbsent(@TempDir Path dir) throws IOException {
        String xml = "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe Id=\"NFe" + KEY + "\">"
                + "<emit><CNPJ>1<b/>2</CNPJ></emit><ide><nNF>15</nNF></ide>"
                + "<det nItem=\"1\"><prod/></det></infNFe></NFe>";
        var meta = parser.parse(write(dir, "mixed.xml", xml));

        assertThat(meta.document().emitterCnpj()).isNull();
        assertThat(meta.document().documentNumber()).isEqualTo("15"); // leitura segue normalmente
        assertThat(meta.itemIndex().itemAt(1)).isEqualTo(1);
    }

    @Test
    void idWithoutKeySuffixYieldsNullAccessKey(@TempDir Path dir) throws IOException {
        String xml = "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe Id=\"NFe\">"
                + "<ide><nNF>15</nNF></ide></infNFe></NFe>";
        var doc = parser.parse(write(dir, "empty-key.xml", xml)).document();

        assertThat(doc.accessKey()).isNull();
        assertThat(doc.documentNumber()).isEqualTo("15"); // leitura do resto segue normalmente
    }

    @Test
    void invalidIssueDateIsNull(@TempDir Path dir) throws IOException {
        String xml = NFE.replace("2026-07-20T10:00:00-03:00", "2026-13-45T10:00:00-03:00");
        var doc = parser.parse(write(dir, "bad-date.xml", xml)).document();

        assertThat(doc.issueDate()).isNull();
        assertThat(doc.accessKey()).isEqualTo(KEY);
    }

    @Test
    void malformedXmlThrowsUnreadable(@TempDir Path dir) throws IOException {
        assertThatThrownBy(() -> parser.parse(write(dir, "bad.xml", "<NFe><infNFe>")))
                .isInstanceOf(UnreadableXmlException.class);
    }

    @Test
    void doctypeIsRejected(@TempDir Path dir) throws IOException {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE NFe [<!ENTITY x \"y\">]><NFe/>";
        assertThatThrownBy(() -> parser.parse(write(dir, "dt.xml", xml)))
                .isInstanceOf(UnreadableXmlException.class)
                .hasMessageContaining("DOCTYPE");
    }

    @Test
    void unknownRootThrowsUnreadable(@TempDir Path dir) throws IOException {
        assertThatThrownBy(() -> parser.parse(write(dir, "other.xml", "<pedido><item/></pedido>")))
                .isInstanceOf(UnreadableXmlException.class)
                .hasMessageContaining("raiz");
    }

    // ---- finNFe, tpNFDebito e NFref: insumos das exceções da UB12-10 e da UB13-30 ----

    /** NF-e com o `<ide>` parametrizável, para exercitar finalidade e notas referenciadas. */
    private String nfeComIde(String miolo) {
        return "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe versao=\"4.00\" Id=\"NFe"
                + KEY + "\"><ide><cUF>35</cUF><mod>55</mod><nNF>15</nNF>"
                + "<dhEmi>2026-08-05T10:00:00-03:00</dhEmi>" + miolo + "</ide>"
                + "<emit><CNPJ>14200166000187</CNPJ><CRT>3</CRT></emit></infNFe></NFe>";
    }

    @Test
    void extractsFinalidadeAndTipoDeNotaDeDebito(@TempDir Path dir) throws IOException {
        var doc = parser.parse(write(dir, "fin.xml",
                nfeComIde("<finNFe>4</finNFe><tpNFDebito>07</tpNFDebito>"))).document();

        assertThat(doc.finNFe()).isEqualTo("4");
        assertThat(doc.tpNFDebito()).isEqualTo("07");
    }

    @Test
    void absentFinalidadeAndDebitoAreNull(@TempDir Path dir) throws IOException {
        var doc = parser.parse(write(dir, "sem-fin.xml", nfeComIde(""))).document();

        assertThat(doc.finNFe()).isNull();
        assertThat(doc.tpNFDebito()).isNull();
        assertThat(doc.references()).isEmpty();
    }

    @Test
    void referencedNoteIsDatedByTheAammOfItsAccessKey(@TempDir Path dir) throws IOException {
        // AAMM ocupa as posições 2-5 da chave: 35 | 2512 | ... => dezembro de 2025.
        var doc = parser.parse(write(dir, "ref.xml", nfeComIde("<finNFe>4</finNFe>"
                + "<NFref><refNFe>35251214200166000187550010000000015123456789</refNFe></NFref>")))
                .document();

        assertThat(doc.references()).singleElement().satisfies(ref -> {
            assertThat(ref.form()).isEqualTo("refNFe");
            assertThat(ref.issuedAt()).isEqualTo(YearMonth.of(2025, 12));
        });
    }

    @Test
    void allReferencesAreKeptNotJustTheFirst(@TempDir Path dir) throws IOException {
        // NFref aceita até 999 ocorrências, e basta uma anterior a 2026 para a exceção valer.
        var doc = parser.parse(write(dir, "refs.xml", nfeComIde("<finNFe>4</finNFe>"
                + "<NFref><refNFe>35260714200166000187550010000000015123456789</refNFe></NFref>"
                + "<NFref><refNFeSig>35251114200166000187550010000000015123456789</refNFeSig></NFref>")))
                .document();

        assertThat(doc.references()).extracting(ReferencedNote::issuedAt)
                .containsExactly(YearMonth.of(2026, 7), YearMonth.of(2025, 11));
    }

    @Test
    void paperNoteReferenceUsesItsOwnAammField(@TempDir Path dir) throws IOException {
        var doc = parser.parse(write(dir, "refnf.xml", nfeComIde("<finNFe>4</finNFe>"
                + "<NFref><refNF><cUF>35</cUF><AAMM>2508</AAMM><CNPJ>14200166000187</CNPJ>"
                + "<mod>01</mod><serie>1</serie><nNF>7</nNF></refNF></NFref>"))).document();

        assertThat(doc.references()).singleElement().satisfies(ref -> {
            assertThat(ref.form()).isEqualTo("refNF");
            assertThat(ref.issuedAt()).isEqualTo(YearMonth.of(2025, 8));
        });
    }

    @Test
    void producerNoteReferenceUsesItsOwnAammField(@TempDir Path dir) throws IOException {
        // O refNFP tem AAMM próprio e explícito no XSD (linha 393), com o mesmo pattern do
        // refNF. Tratá-lo como não datável produziria um "não avaliado" que o contador
        // investigaria à toa, tendo a data oficial ali no documento.
        var doc = parser.parse(write(dir, "refnfp.xml", nfeComIde("<finNFe>4</finNFe>"
                + "<NFref><refNFP><cUF>35</cUF><AAMM>2507</AAMM><CNPJ>14200166000187</CNPJ>"
                + "<IE>123456789012</IE><mod>04</mod><serie>1</serie><nNF>9</nNF></refNFP></NFref>")))
                .document();

        assertThat(doc.references()).singleElement().satisfies(ref -> {
            assertThat(ref.form()).isEqualTo("refNFP");
            assertThat(ref.issuedAt()).isEqualTo(YearMonth.of(2025, 7));
        });
    }

    @Test
    void paperReferenceWithoutAammIsStillRecorded(@TempDir Path dir) throws IOException {
        // Sem o AAMM a referência não some: some-la faria a exceção da UB12-10 deixar de ser
        // consultada e a devolução virar acusação por um campo que o XSD já reporta.
        var doc = parser.parse(write(dir, "refnf-sem-aamm.xml", nfeComIde("<finNFe>4</finNFe>"
                + "<NFref><refNF><cUF>35</cUF><CNPJ>14200166000187</CNPJ><mod>01</mod>"
                + "<serie>1</serie><nNF>7</nNF></refNF></NFref>"))).document();

        assertThat(doc.references()).singleElement().satisfies(ref -> {
            assertThat(ref.form()).isEqualTo("refNF");
            assertThat(ref.issuedAt()).isNull();
        });
    }

    @Test
    void undatableReferenceIsRecordedWithoutADate(@TempDir Path dir) throws IOException {
        // A referência existe e precisa aparecer: é o que faz a regra dizer "não avaliado"
        // em vez de acusar por uma data que ela nunca teve.
        var doc = parser.parse(write(dir, "refcte.xml", nfeComIde("<finNFe>4</finNFe>"
                + "<NFref><refCTe>35251214200166000187570010000000015123456789</refCTe></NFref>")))
                .document();

        assertThat(doc.references()).singleElement().satisfies(ref -> {
            assertThat(ref.form()).isEqualTo("refCTe");
            assertThat(ref.issuedAt()).isNull();
        });
    }

    @Test
    void malformedAccessKeyYieldsAnUndatableReference(@TempDir Path dir) throws IOException {
        var doc = parser.parse(write(dir, "refruim.xml", nfeComIde("<finNFe>4</finNFe>"
                + "<NFref><refNFe>chave-invalida</refNFe></NFref>"))).document();

        assertThat(doc.references()).singleElement()
                .satisfies(ref -> assertThat(ref.issuedAt()).isNull());
    }

    @Test
    void impossibleMonthInTheKeyIsNotInvented(@TempDir Path dir) throws IOException {
        // AAMM "2599": mês 99 não existe. Melhor não datar do que datar errado.
        var doc = parser.parse(write(dir, "refmes.xml", nfeComIde("<finNFe>4</finNFe>"
                + "<NFref><refNFe>35259914200166000187550010000000015123456789</refNFe></NFref>")))
                .document();

        assertThat(doc.references()).singleElement()
                .satisfies(ref -> assertThat(ref.issuedAt()).isNull());
    }
}
