package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.domain.RootCauseKey;
import br.com.validadorlote.domain.Severity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaValidatorEngineTest {

    private static SchemaValidatorEngine engine;
    private static final XmlMetadataParser parser = new XmlMetadataParser();

    @BeforeAll
    static void compileSchemaOnce() {
        engine = new SchemaValidatorEngine(new XsdErrorTranslator());
    }

    private Path fixture(String name) {
        return Path.of("src/test/resources/fixtures/" + name);
    }

    private List<Finding> validateFixture(String name) {
        Path xml = fixture(name);
        return engine.validate(xml, parser.parse(xml));
    }

    /** Metadados vazios, para exercitar o motor sem depender do parser (arquivo ilegível). */
    private ParsedMetadata bareMetadata(Path xml) {
        return new ParsedMetadata(
                new FiscalDocument(xml, null, null, null, null, null, "NFe", null),
                ItemLineIndex.of(List.of()));
    }

    @Test
    void collectsAllErrorsNotJustTheFirst() {
        Path xml = fixture("nfe-minima-invalida.xml");
        var findings = engine.validate(xml, parser.parse(xml));

        // NFe mínima viola o schema em vários pontos: coleta total exige > 1 achado
        // (o endpoint oficial devolve só o primeiro — nossa vantagem, spec §2).
        assertThat(findings).hasSizeGreaterThan(1);
        assertThat(findings).allSatisfy(f -> {
            assertThat(f.xsdCode()).startsWith("cvc-");
            assertThat(f.line()).isPositive();
            assertThat(f.source()).isEqualTo(xml);
        });
    }

    @Test
    void includesAreResolvedFromClasspath() {
        // Erros de tipos definidos no leiauteNFe (via include) provam a resolução:
        // a NFe mínima dispara cvc-complex-type.2.4.* de tipos do leiaute.
        Path xml = fixture("nfe-minima-invalida.xml");
        var findings = engine.validate(xml, parser.parse(xml));
        assertThat(findings).anySatisfy(f ->
                assertThat(f.xsdCode()).startsWith("cvc-complex-type.2.4"));
    }

    @Test
    void doctypeYieldsSingleUnreadableFinding(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("doctype.xml");
        Files.writeString(xml,
                "<?xml version=\"1.0\"?><!DOCTYPE NFe [<!ENTITY x \"y\">]>"
                + "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"/>");
        var findings = engine.validate(xml, bareMetadata(xml));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(FindingKind.UNREADABLE);
            assertThat(f.officialMessage()).isNotBlank();
        });
    }

    // --- classificação de assinatura -------------------------------------------------------
    // A regra é "ds:Signature está entre os elementos ESPERADOS", nunca "a mensagem contém a
    // substring Signature": o texto do Xerces cita nomes de elementos que o autor do XML escolhe.

    @Test
    void missingSignatureIsClassifiedAsSignatureMissing() {
        var findings = validateFixture("nfe-minima-invalida.xml");

        assertThat(findings).filteredOn(f -> f.kind() == FindingKind.SIGNATURE_MISSING)
                .singleElement().satisfies(f -> {
                    assertThat(f.severity()).isEqualTo(Severity.INFO); // base; pré-emissão decide a final
                    assertThat(f.field()).isEqualTo("Signature");
                    assertThat(f.friendlyMessage()).containsIgnoringCase("sem assinatura digital");
                });
    }

    @Test
    void brokenSignatureIsSchemaRejectionNotSignatureMissing() {
        // <Signature/> vazio: assinatura presente e quebrada. Dizer "sem assinatura" seria falso,
        // e em modo pré-emissão a rebaixaria a INFO — o contador transmitiria um XML rejeitado.
        var findings = validateFixture("nfe-assinatura-incompleta.xml");

        assertThat(findings).noneMatch(f -> f.kind() == FindingKind.SIGNATURE_MISSING);
        assertThat(findings).filteredOn(f -> "Signature".equals(f.field()))
                .singleElement().satisfies(f -> {
                    assertThat(f.kind()).isEqualTo(FindingKind.SCHEMA);
                    assertThat(f.severity()).isEqualTo(Severity.REJECTION);
                    assertThat(f.officialMessage()).contains("SignedInfo");
                });
    }

    @Test
    void elementNamedLikeSignatureIsNotDowngradedToInfo() {
        // Entrada hostil: SignatureXpto dentro de infNFe. O achado dele é erro de schema;
        // o SIGNATURE_MISSING que sobra é o da assinatura de fato ausente no fim do documento.
        var findings = validateFixture("nfe-elemento-nome-signature.xml");

        assertThat(findings).filteredOn(f -> "SignatureXpto".equals(f.field()))
                .singleElement().satisfies(f -> {
                    assertThat(f.kind()).isEqualTo(FindingKind.SCHEMA);
                    assertThat(f.severity()).isEqualTo(Severity.REJECTION);
                });
    }

    @Test
    void structuralGarbageBesideInfNFeIsNotDowngradedToInfo() {
        // Caso difícil: o lixo é IRMÃO de infNFe, posição onde ds:Signature ESTÁ entre os
        // esperados. O Xerces emite cvc-complex-type.2.4.a ("conteúdo proibido") listando a
        // assinatura — casar o prefixo .2.4 classificava isso como assinatura ausente e, em
        // pré-emissão, o erro estrutural sumia do relatório. Só .2.4.b significa "faltou".
        var findings = validateFixture("nfe-lixo-estrutural-irmao.xml");

        assertThat(findings).noneMatch(f -> f.kind() == FindingKind.SIGNATURE_MISSING);
        assertThat(findings).filteredOn(f -> "lixoEstrutural".equals(f.field()))
                .singleElement().satisfies(f -> {
                    assertThat(f.kind()).isEqualTo(FindingKind.SCHEMA);
                    assertThat(f.severity()).isEqualTo(Severity.REJECTION);
                    assertThat(f.xsdCode()).isEqualTo("cvc-complex-type.2.4.a");
                    // a mensagem cita a assinatura entre os esperados: é justamente a armadilha
                    assertThat(f.officialMessage()).contains("xmldsig#\":Signature");
                });
    }

    @Test
    void signatureInTheWrongNamespaceIsSchemaRejectionNotSignatureMissing() {
        // Bug trivial de emissor: <Signature/> herda o namespace nfe em vez do xmldsig#.
        // A assinatura existe e está defeituosa; chamar isso de "ausente" a rebaixaria a INFO.
        var findings = validateFixture("nfe-signature-namespace-errado.xml");

        assertThat(findings).noneMatch(f -> f.kind() == FindingKind.SIGNATURE_MISSING);
        assertThat(findings).filteredOn(f -> "Signature".equals(f.field()))
                .singleElement().satisfies(f -> {
                    assertThat(f.kind()).isEqualTo(FindingKind.SCHEMA);
                    assertThat(f.severity()).isEqualTo(Severity.REJECTION);
                    assertThat(f.officialMessage()).contains("portalfiscal.inf.br/nfe\":Signature");
                });
    }

    @Test
    void structurallyValidSignatureProducesNoSignatureFinding() {
        var findings = validateFixture("nfe-assinatura-bem-formada.xml");
        assertThat(findings).noneMatch(f -> f.kind() == FindingKind.SIGNATURE_MISSING);
    }

    // --- correlação faceta + portador ------------------------------------------------------

    @Test
    void facetAndTypeErrorsForTheSameValueBecomeOneFinding() {
        var findings = validateFixture("nfe-pcbs-invalido.xml");

        // O portador cvc-type.3.1.3 não sobrevive sozinho: seria um segundo achado para o mesmo
        // erro, com o texto cru do Xerces em inglês.
        assertThat(findings).noneMatch(f -> "cvc-type.3.1.3".equals(f.xsdCode()));
        assertThat(findings).filteredOn(f -> "cvc-pattern-valid".equals(f.xsdCode()))
                .singleElement().satisfies(f -> {
                    assertThat(f.field()).isEqualTo("pCBS");          // veio do portador
                    assertThat(f.itemNumber()).isEqualTo(1);          // índice linha→item
                    assertThat(f.friendlyMessage()).contains("pCBS"); // chave cvc-pattern-valid.pCBS
                    // as duas mensagens oficiais permanecem íntegras, nenhuma reescrita
                    assertThat(f.officialMessage())
                            .contains("cvc-pattern-valid:")
                            .contains("cvc-type.3.1.3:");
                });
    }

    @Test
    void attributeWinsOverElementSoDistinctDefectsGetDistinctRootCauses() {
        // cvc-attribute.3 cita atributo E elemento; com o elemento vencendo, versao e Id
        // inválidos no mesmo infNFe viravam UMA causa-raiz só, rotulada com o campo errado.
        var findings = validateFixture("nfe-atributos-invalidos.xml");

        assertThat(findings).filteredOn(f -> "cvc-pattern-valid".equals(f.xsdCode()))
                .extracting(Finding::field)
                .containsExactlyInAnyOrder("versao", "Id");
        // chave de causa-raiz = (kind, xsdCode, field): campos distintos ⇒ causas distintas
        assertThat(findings).filteredOn(f -> "cvc-pattern-valid".equals(f.xsdCode()))
                .extracting(f -> new RootCauseKey(f.kind(), f.xsdCode(), f.field()))
                .doesNotHaveDuplicates();
    }

    // --- independência do locale da JVM ----------------------------------------------------

    @Test
    void fieldExtractionSurvivesAForeignJvmLocale() {
        // As mensagens do Xerces são localizadas e a extração de campo lê o texto por regex.
        // Sob de_DE, sem fixar o locale, o achado de pCBS saía com field=null e mensagem
        // genérica — a coluna "campo" do relatório ficava vazia, silenciosamente.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            var findings = validateFixture("nfe-pcbs-invalido.xml");

            assertThat(findings).filteredOn(f -> "cvc-pattern-valid".equals(f.xsdCode()))
                    .singleElement().satisfies(f -> {
                        assertThat(f.field()).isEqualTo("pCBS");
                        assertThat(f.friendlyMessage()).contains("pCBS");
                        // mensagem oficial fixada em inglês, não no idioma da máquina
                        assertThat(f.officialMessage()).contains("is not facet-valid");
                    });
            // a classificação de assinatura também lê o texto oficial
            assertThat(findings).anyMatch(f -> f.kind() == FindingKind.SIGNATURE_MISSING);
        } finally {
            Locale.setDefault(original);
        }
    }

    // --- tetos por documento ---------------------------------------------------------------

    @Test
    void findingsAreCappedAndTruncationIsAnnounced(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("lote-patologico.xml");
        Files.writeString(xml, pathologicalBatch(1_000));

        var findings = engine.validate(xml, bareMetadata(xml));

        // Sem teto, este documento acumula dezenas de milhares de achados até o OutOfMemoryError —
        // que, sendo Error e não Exception, escaparia do tratamento por arquivo e mataria o lote.
        // Valores exatos: o teto é de 5.000 achados reais + 1 aviso de truncamento.
        assertThat(findings).hasSize(5_001);
        assertThat(findings).last().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(FindingKind.SCHEMA);
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
            assertThat(f.xsdCode()).isNull();
            assertThat(f.officialMessage()).contains("truncada").contains("5.000 achados");
            assertThat(f.friendlyMessage()).contains("truncada");
        });
        // Os achados reais continuam lá: truncar não é abortar.
        assertThat(findings).filteredOn(f -> f.xsdCode() != null).hasSize(5_000);
    }

    @Test
    void giantValuesHitTheByteBudgetLongBeforeTheCountCap(@TempDir Path dir) throws IOException {
        // Cenário medido: 24 MB, 1.200 notas com um valor inválido de 20 KB cada. São só 1.200
        // achados — muito abaixo do teto de 5.000 —, mas o Xerces cita o valor INTEIRO em cada
        // mensagem: ~24 MB de texto retido, OutOfMemoryError, lote inteiro derrubado.
        Path xml = dir.resolve("valores-gigantes.xml");
        Files.writeString(xml, batchWithGiantValues(1_200, 20_000));

        var findings = engine.validate(xml, bareMetadata(xml));

        assertThat(findings).hasSizeLessThan(2_000); // o teto de contagem nunca chegaria a agir
        assertThat(findings).last().satisfies(f -> {
            assertThat(f.xsdCode()).isNull();
            assertThat(f.officialMessage()).contains("truncada").contains("caracteres acumulados");
        });
        // Nenhuma mensagem isolada carrega o valor de 20 KB, e o corte é declarado como nosso.
        assertThat(findings).allSatisfy(f ->
                assertThat(f.officialMessage()).hasSizeLessThan(20_000));
        assertThat(findings).anySatisfy(f ->
                assertThat(f.officialMessage()).contains("cortada pelo validador"));
    }

    private String batchWithGiantValues(int notes, int valueChars) {
        String giant = "9".repeat(valueChars);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<enviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">\n")
                .append("<idLote>1</idLote><indSinc>0</indSinc>\n");
        for (int i = 0; i < notes; i++) {
            xml.append("<NFe><infNFe versao=\"4.00\" Id=\"NFe35200114200166000187550010000000015123456")
               .append("789\"><ide><cUF>").append(giant).append("</cUF></ide></infNFe></NFe>\n");
        }
        return xml.append("</enviNFe>\n").toString();
    }

    private String pathologicalBatch(int notes) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<enviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">\n")
                .append("<idLote>1</idLote><indSinc>0</indSinc>\n");
        for (int i = 0; i < notes; i++) {
            xml.append("<NFe><infNFe versao=\"4.00\" Id=\"NFe352001142001660001875500100000000151234567")
               .append("89\"><ide><cUF>99</cUF><cNF>x</cNF></ide><lixo1/><lixo2/><lixo3/>")
               .append("</infNFe></NFe>\n");
        }
        return xml.append("</enviNFe>\n").toString();
    }

    // --- arquivo ilegível ------------------------------------------------------------------

    @Test
    void malformedXmlKeepsLineAndColumn(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("malformado.xml");
        Files.writeString(xml, "<?xml version=\"1.0\"?>\n"
                + "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">\n"
                + "  <infNFe>\n"
                + "</NFe>\n");

        assertThat(engine.validate(xml, bareMetadata(xml))).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(FindingKind.UNREADABLE);
            assertThat(f.line()).isPositive();   // única pista de onde está o defeito
            assertThat(f.column()).isPositive();
        });
    }

    @Test
    void missingFileIsExplainedInPortuguese(@TempDir Path dir) {
        Path xml = dir.resolve("nao-existe.xml");

        assertThat(engine.validate(xml, bareMetadata(xml))).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(FindingKind.UNREADABLE);
            // sem isto a mensagem seria só o caminho do arquivo, sem explicação nenhuma
            assertThat(f.officialMessage()).contains("Arquivo não encontrado").contains("nao-existe.xml");
        });
    }

    @Test
    void fullyValidNfeYieldsNoFindings() {
        // Ausência de falso positivo é o requisito mais duro do produto: um validador que acusa
        // documento bom destrói a confiança mais rápido do que um que deixa passar documento ruim.
        assertThat(validateFixture("nfe-valida.xml")).isEmpty();
    }

    @Test
    void fullyValidNfceYieldsNoFindings() {
        assertThat(validateFixture("nfce-valida.xml")).isEmpty();
    }

    @Test
    void validDocumentWithoutSignatureYieldsOnlySignatureMissing() {
        // Caso do público-alvo: XML de pré-emissão, ainda não assinado. O único achado precisa ser
        // o da assinatura — se vier mais alguma coisa, o contador é afogado em ruído.
        var findings = validateFixture("nfe-valida-sem-assinatura.xml");

        assertThat(findings).singleElement().satisfies(f ->
                assertThat(f.kind()).isEqualTo(FindingKind.SIGNATURE_MISSING));
    }

    @Test
    void hostileValueCannotHijackTheFieldName() {
        // O Xerces interpola o valor rejeitado ANTES de nomear o campo, então um valor que imita a
        // própria mensagem sequestraria a extração e o relatório mandaria corrigir o campo errado —
        // e é a instrução de correção que o contador segue.
        var findings = validateFixture("nfe-campo-injetado.xml");

        // O achado do valor hostil aponta cUF, o campo que de fato tem o valor recusado.
        assertThat(findings).filteredOn(f -> "cvc-enumeration-valid".equals(f.xsdCode()))
                .singleElement()
                .satisfies(f -> assertThat(f.field()).isEqualTo("cUF"));
        // pCBS ainda aparece — a fixture tem um pCBS realmente inválido —, mas só no achado dele.
        assertThat(findings).filteredOn(f -> "pCBS".equals(f.field()))
                .singleElement()
                .satisfies(f -> assertThat(f.officialMessage()).contains("pCBS"));
    }

    @Test
    void oversizedFileIsRefusedWithoutValidating(@TempDir Path dir) throws IOException {
        // Documento fiscal legítimo tem dezenas de KB; um arquivo desta ordem ou não é NF-e ou é
        // patológico. Recusar antes de abrir evita o estouro de heap em vez de remediá-lo — e o
        // lote precisa seguir, porque um arquivo ruim nunca pode derrubar os outros 499.
        Path huge = dir.resolve("gigante.xml");
        try (var out = Files.newBufferedWriter(huge)) {
            out.write("<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe><ide><cUF>");
            char[] chunk = new char[1024 * 1024];
            java.util.Arrays.fill(chunk, '9');
            for (int i = 0; i < 33; i++) {
                out.write(chunk);
            }
            out.write("</cUF></ide></infNFe></NFe>");
        }

        var findings = engine.validate(huge, bareMetadata(huge));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(FindingKind.UNREADABLE);
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
            assertThat(f.officialMessage()).contains("grande demais");
        });
    }

    @Test
    void schemasVersionExposesEngineBaseAndExtractionDate() {
        // Formato, não valores: ./gradlew updateSchemas troca os números como operação de rotina.
        assertThat(SchemasVersion.read())
                .matches("motor \\S+ / base .+ \\(extração \\d{4}-\\d{2}-\\d{2}\\)");
    }
}
