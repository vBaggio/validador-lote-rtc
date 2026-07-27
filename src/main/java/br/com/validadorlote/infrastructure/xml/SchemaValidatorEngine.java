package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.Severity;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida documentos contra o schema oficial (nota.xsd, raízes NFe/nfeProc/enviNFe),
 * coletando <b>todos</b> os erros de cada documento — ao contrário da calculadora oficial,
 * que para no primeiro. O {@link Schema} é compilado uma única vez (é thread-safe e caro);
 * o {@link Validator} é criado por documento (não é thread-safe).
 */
public final class SchemaValidatorEngine {

    private static final String SCHEMA_DIR = "/schemas/nfe/";
    private static final String SCHEMA_RESOURCE = SCHEMA_DIR + "nota.xsd";
    // Diretórios do classpath onde os includes/imports do schema oficial são procurados.
    private static final List<String> SCHEMA_LOOKUP_DIRS = List.of(SCHEMA_DIR + "originais/", SCHEMA_DIR);
    private static final String SIGNATURE_FIELD = "Signature";
    private static final String XSD_CODE_PREFIX = "cvc-";
    /**
     * Única variante de {@code cvc-complex-type.2.4} que significa "faltou elemento":
     * {@code .b} é <i>conteúdo incompleto</i>. As irmãs {@code .a} e {@code .d} significam
     * "elemento presente e proibido nesta posição" e listam os esperados apenas para orientar —
     * casá-las classificaria erro estrutural como assinatura ausente (ver {@link #isSignatureMissing}).
     */
    private static final String SIGNATURE_XSD_CODE = "cvc-complex-type.2.4.b";
    /**
     * Nome qualificado da assinatura como o Xerces o escreve na lista de elementos esperados.
     * Casar o nome completo (e não a substring "Signature") impede que o autor do XML force a
     * classificação criando um elemento batizado {@code SignatureXpto}.
     */
    private static final String SIGNATURE_QNAME = "\"http://www.w3.org/2000/09/xmldsig#\":Signature";

    /**
     * Locale das mensagens de validação. O Xerces do JDK traz bundles localizados
     * ({@code XMLSchemaMessages_de}, {@code _ja}, …): sem fixar, a máquina do usuário decide o
     * idioma e a extração de campo — que é por regex sobre o texto — para de funcionar,
     * silenciosamente (D-023). {@link Locale#ROOT} resolve direto no bundle base (inglês);
     * {@code Locale.ENGLISH} <b>não</b> serve, porque não existe {@code _en} e o
     * {@code ResourceBundle} cai no locale padrão da JVM — verificado sob {@code de_DE}.
     */
    private static final Locale VALIDATION_LOCALE = Locale.ROOT;
    private static final String LOCALE_PROPERTY = "http://apache.org/xml/properties/locale";

    // Erros "portadores": o Xerces os emite logo depois do erro de faceta, na mesma posição,
    // repetindo o mesmo defeito de valor — só que nomeando o elemento/atributo.
    private static final Set<String> VALUE_CARRIER_CODES = Set.of("cvc-type.3.1.3", "cvc-attribute.3");
    // Erros de faceta/tipo de dado que o Xerces emparelha com um portador.
    private static final Set<String> FACET_CODES = Set.of(
            "cvc-pattern-valid", "cvc-enumeration-valid", "cvc-datatype-valid.1.2.1",
            "cvc-length-valid", "cvc-minLength-valid", "cvc-maxLength-valid",
            "cvc-minInclusive-valid", "cvc-maxInclusive-valid",
            "cvc-minExclusive-valid", "cvc-maxExclusive-valid",
            "cvc-totalDigits-valid", "cvc-fractionDigits-valid");
    private static final String MERGED_MESSAGE_SEPARATOR = " | ";

    /**
     * Teto de achados por documento. Um documento patológico (lote com centenas de notas
     * repetidamente inválidas) gera dezenas de milhares de erros; acumular tudo estoura a heap
     * com {@link OutOfMemoryError}, que <b>não</b> é {@link Exception} e por isso escaparia do
     * tratamento por arquivo, derrubando o lote inteiro. Acima de alguns milhares o relatório
     * já é inútil para o usuário — o caminho é corrigir a causa sistêmica e validar de novo.
     *
     * <p>Este teto limita <b>quantidade</b>, e sozinho não basta: poucos achados citando valores
     * gigantes estouram a heap do mesmo jeito. O volume é limitado em paralelo por
     * {@link #MAX_ACCUMULATED_MESSAGE_CHARS}.
     */
    private static final int MAX_FINDINGS_PER_DOCUMENT = 5_000;

    /**
     * Orçamento de <b>texto</b> acumulado por documento, em paralelo ao teto de contagem.
     * O teto de contagem sozinho não protege a heap: o Xerces cita o valor rejeitado
     * <b>inteiro</b> na mensagem, então 1.200 notas com um valor de 20 KB cada geram só 1.200
     * achados — muito abaixo de 5.000 — e ainda assim ~24 MB de texto, que estouram a heap e
     * derrubam o lote (medido). 2 milhões de caracteres ≈ 2 MB: para mensagens de tamanho normal
     * (~200 caracteres) isso daria ~10.000 achados, bem acima do teto de contagem, de modo que
     * só documentos com valores anormalmente grandes esbarram aqui.
     */
    private static final int MAX_ACCUMULATED_MESSAGE_CHARS = 2_000_000;

    /**
     * Teto de uma mensagem oficial isolada. O maior valor legítimo da NF-e é {@code infCpl}
     * (5.000 caracteres), que cabe com folga; só valores absurdos são cortados. O corte é
     * anunciado no próprio texto para não parecer que o Xerces escreveu aquilo.
     */
    private static final int MAX_MESSAGE_CHARS = 8_000;
    private static final String MESSAGE_TRUNCATION_MARK =
            " […mensagem oficial cortada pelo validador em %,d caracteres: o valor rejeitado é "
            + "grande demais para o relatório. O texto acima é o início da mensagem do Xerces.]";

    /**
     * Teto de tamanho do arquivo. Uma NF-e real tem dezenas de KB; um {@code enviNFe} de lote
     * (máximo de 50 notas) fica na casa dos poucos MB. 32 MB dá folga de uma ordem de grandeza
     * sobre o maior documento plausível e ainda barra o arquivo patológico, cujo custo de heap
     * não se converte em diagnóstico útil para o contador.
     */
    private static final long MAX_DOCUMENT_BYTES = 32L * 1024 * 1024;
    private static final String SIZE_REFUSAL_NOTICE =
            "Arquivo grande demais para análise (%,.1f MB; o limite é %d MB). Documentos fiscais "
            + "legítimos são muito menores — confira se este arquivo é mesmo uma NF-e ou NFC-e.";
    private static final String OUT_OF_MEMORY_NOTICE =
            "Não foi possível analisar este arquivo: ele exigiu mais memória do que a disponível "
            + "(provavelmente há um valor anormalmente grande no XML). Os demais arquivos do lote "
            + "foram analisados normalmente.";

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final String COUNT_TRUNCATION_NOTICE =
            "Listagem truncada: este documento excedeu o limite de %,d achados por arquivo e a "
            + "análise foi interrompida. Os %,d achados acima são os primeiros encontrados; "
            + "existem outros erros neste documento. Corrija-os e valide novamente.";
    private static final String SIZE_TRUNCATION_NOTICE =
            "Listagem truncada: as mensagens de erro deste documento excederam %,d caracteres "
            + "acumulados (valores rejeitados muito grandes) e a análise foi interrompida. "
            + "Os %,d achados acima são os primeiros encontrados; existem outros erros neste "
            + "documento. Corrija-os e valide novamente.";

    /**
     * Extração do campo a partir da mensagem Xerces; a primeira que casar vence, por isso a
     * <b>ordem importa</b>. O atributo vem antes do elemento: {@code cvc-attribute.3} cita os
     * dois ("the value 'X' of attribute 'versao' on element 'infNFe'") e quem responde pelo
     * defeito é o atributo — com o elemento vencendo, {@code versao} e {@code Id} inválidos no
     * mesmo {@code infNFe} caíam na mesma chave de causa-raiz, rotulada com o campo errado.
     * O inglês é garantido por {@link #VALIDATION_LOCALE}; as variantes em português seguem
     * como rede de segurança caso a propriedade de locale não seja suportada no ambiente.
     */
    private static final List<Pattern> FIELD_PATTERNS = List.of(
            // Portador com atributo: "...the value 'X' of attribute 'versao' on element 'infNFe'..."
            Pattern.compile("(?i:of attribute|do atributo) '([A-Za-z0-9_]+)' (?i:on element|no elemento)"),
            // Portador com elemento: "The value 'X' of element 'pCBS' is not valid."
            Pattern.compile("(?i:of element|do elemento) '(?:\\{[^}]*\\}:?)?([A-Za-z0-9_:]+)'"
                    + " (?i:is not valid|não é válido)"),
            // Estrutural .2.4.a: o campo é o elemento INFRATOR, não o esperado.
            Pattern.compile("(?i:starting with element|começando com o elemento)"
                    + " '\\{\"[^\"]*\":([A-Za-z0-9_]+)\\}'"),
            // Estrutural .2.4.b: o campo é o elemento cujo conteúdo ficou incompleto.
            Pattern.compile("(?i:content of element|conteúdo do elemento)"
                    + " '(?:\\{[^}]*\\}:?)?([A-Za-z0-9_:]+)'"),
            // Genéricos, para códigos não previstos acima.
            Pattern.compile("(?i:attribute|atributo) '([A-Za-z0-9_]+)'"),
            Pattern.compile("(?i:element|elemento) '(?:\\{[^}]*\\}:?)?([A-Za-z0-9_:]+)'"),
            Pattern.compile("'\\{\"[^\"]*\":([A-Za-z0-9_]+)\\}'"));

    private final Schema schema;
    private final XsdErrorTranslator translator;

    public SchemaValidatorEngine(XsdErrorTranslator translator) {
        this.translator = translator;
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            // Secure processing proíbe acesso externo: os includes vêm do classpath, via resolver
            // próprio, que funciona igual em diretório de build e dentro do JAR (D-019).
            factory.setResourceResolver(new ClasspathSchemaResolver());
            forceMessageLocale(factory::setProperty);
            URL url = SchemaValidatorEngine.class.getResource(SCHEMA_RESOURCE);
            if (url == null) {
                throw new IllegalStateException("Schema ausente no classpath: " + SCHEMA_RESOURCE);
            }
            this.schema = factory.newSchema(url);
        } catch (SAXException e) {
            throw new IllegalStateException("Falha ao preparar o motor de validação XSD", e);
        }
    }

    /** Todos os achados de schema do documento; nunca lança — arquivo problemático vira achado. */
    public List<Finding> validate(Path xml, ParsedMetadata meta) {
        Finding tooLarge = refuseIfTooLarge(xml, meta);
        if (tooLarge != null) {
            return List.of(tooLarge);
        }
        List<Finding> findings = new ArrayList<>();
        // Acumula em vez de abortar no primeiro erro: é a feature que distingue o produto.
        ErrorHandler collector = new ErrorHandler() {
            /** Texto oficial já retido. Guarda a heap onde o teto de contagem não alcança. */
            private long messageChars;

            @Override
            public void warning(SAXParseException e) {
                // avisos de parser não são achados fiscais
            }

            @Override
            public void error(SAXParseException e) throws SAXException {
                if (findings.size() >= MAX_FINDINGS_PER_DOCUMENT) {
                    throw new FindingLimitReachedException(
                            COUNT_TRUNCATION_NOTICE, MAX_FINDINGS_PER_DOCUMENT);
                }
                if (messageChars >= MAX_ACCUMULATED_MESSAGE_CHARS) {
                    throw new FindingLimitReachedException(
                            SIZE_TRUNCATION_NOTICE, MAX_ACCUMULATED_MESSAGE_CHARS);
                }
                Finding finding = toFinding(xml, meta, e);
                messageChars += finding.officialMessage().length();
                addOrMerge(findings, finding);
            }

            @Override
            public void fatalError(SAXParseException e) throws SAXException {
                throw e; // malformado/DOCTYPE: o documento inteiro é ilegível
            }
        };
        try (InputStream in = Files.newInputStream(xml)) {
            InputSource source = new InputSource(in);
            source.setSystemId(xml.toUri().toString());
            Validator validator = schema.newValidator();
            forceMessageLocale(validator::setProperty);
            validator.setErrorHandler(collector);
            validator.validate(new SAXSource(secureReader(), source));
        } catch (FindingLimitReachedException e) {
            findings.add(truncationNotice(xml, meta, e, findings.size()));
            return findings;
        } catch (SAXException | IOException | ParserConfigurationException | RuntimeException e) {
            return List.of(unreadable(xml, meta, e));
        } catch (OutOfMemoryError e) {
            // Rede de segurança: o Xerces bufferiza o valor de um tipo simples INTEIRO antes que
            // qualquer teto nosso possa agir, então um único valor gigante estoura a heap aqui
            // dentro. Error não é Exception e escapava do catch acima, derrubando o lote inteiro —
            // violando a garantia de que um lote de 500 nunca aborta por causa de um arquivo.
            // Só OutOfMemoryError é tratado: os demais Error indicam defeito nosso, não do arquivo.
            findings.clear();
            return List.of(new Finding(xml, meta.document().accessKey(), null,
                    FindingKind.UNREADABLE, Severity.WARNING, null, null,
                    OUT_OF_MEMORY_NOTICE, null, null, null));
        }
        return findings;
    }

    /**
     * Recusa preventiva por tamanho. Documento fiscal legítimo não chega perto deste teto — uma
     * NF-e típica tem dezenas de KB e um {@code enviNFe} de lote, poucos MB. Arquivo muito acima
     * disso ou não é documento fiscal, ou é patológico; validá-lo custaria heap sem entregar
     * diagnóstico útil. Recusar antes de abrir evita o estouro em vez de remediá-lo.
     */
    private Finding refuseIfTooLarge(Path xml, ParsedMetadata meta) {
        try {
            long bytes = Files.size(xml);
            if (bytes <= MAX_DOCUMENT_BYTES) {
                return null;
            }
            return new Finding(xml, meta.document().accessKey(), null, FindingKind.UNREADABLE,
                    Severity.WARNING, null, null,
                    String.format(PT_BR, SIZE_REFUSAL_NOTICE, bytes / 1_048_576.0,
                            MAX_DOCUMENT_BYTES / 1_048_576), null, null, null);
        } catch (IOException e) {
            return unreadable(xml, meta, e);
        }
    }

    /**
     * Fixa o idioma das mensagens de validação. Sem isso o Xerces as resolve no locale da JVM do
     * usuário e a extração de campo — que lê o texto por regex — devolve {@code null}
     * silenciosamente, levando junto as traduções específicas por campo. Se o ambiente não
     * reconhecer a propriedade, seguimos sem ela: mensagem no idioma da máquina é ruim, abortar
     * a validação por causa disso é pior.
     */
    private void forceMessageLocale(LocaleSetter setter) {
        try {
            setter.set(LOCALE_PROPERTY, VALIDATION_LOCALE);
        } catch (SAXException e) {
            // propriedade não suportada: as regex em português seguem como rede de segurança
        }
    }

    @FunctionalInterface
    private interface LocaleSetter {
        void set(String property, Object value) throws SAXException;
    }

    /**
     * Para um valor inválido o Xerces emite dois erros na mesma posição: o de faceta (que
     * descreve a regra violada e tem tradução) e o "portador" {@code cvc-type.3.1.3} /
     * {@code cvc-attribute.3} (que nomeia o elemento/atributo, mas não tem tradução). Fundir o
     * par num achado só evita duas causas-raiz para um único erro e, ao herdar o campo do
     * portador, faz a tradução específica por campo (ex.: {@code cvc-pattern-valid.pCBS})
     * resolver — sem ela, o campo ficaria nulo e só a mensagem genérica apareceria.
     */
    private void addOrMerge(List<Finding> findings, Finding candidate) {
        int last = findings.size() - 1;
        if (last >= 0 && pairsWithPrevious(findings.get(last), candidate)) {
            findings.set(last, merge(findings.get(last), candidate));
            return;
        }
        findings.add(candidate);
    }

    private boolean pairsWithPrevious(Finding facet, Finding carrier) {
        return VALUE_CARRIER_CODES.contains(carrier.xsdCode())
                && facet.xsdCode() != null && FACET_CODES.contains(facet.xsdCode())
                && facet.kind() == carrier.kind()
                && Objects.equals(facet.line(), carrier.line())
                && Objects.equals(facet.column(), carrier.column());
    }

    /** Mantém o código da faceta e o campo do portador; preserva as duas mensagens oficiais. */
    private Finding merge(Finding facet, Finding carrier) {
        // O portador é quem nomeia o elemento/atributo; a faceta só descreve a regra violada.
        String field = carrier.field() != null ? carrier.field() : facet.field();
        String official = facet.officialMessage() + MERGED_MESSAGE_SEPARATOR + carrier.officialMessage();
        String friendly = translator.translate(facet.kind(), facet.xsdCode(), field)
                .map(XsdErrorTranslator.Translation::message).orElse(facet.friendlyMessage());
        return new Finding(facet.source(), facet.accessKey(), facet.itemNumber(), facet.kind(),
                facet.severity(), field, facet.xsdCode(), official, friendly,
                facet.line(), facet.column());
    }

    private Finding truncationNotice(Path xml, ParsedMetadata meta,
            FindingLimitReachedException limit, int kept) {
        // Locale fixo: o separador de milhar é o brasileiro, não o do sistema do usuário.
        String notice = String.format(PT_BR, limit.notice, limit.limit, kept);
        // Sem xsdCode: não é um erro do schema, é um aviso do motor sobre o próprio relatório.
        // O texto vai também em officialMessage porque é dele que o agrupamento de causa-raiz
        // tira a explicação quando não há tradução por código.
        return new Finding(xml, meta.document().accessKey(), null, FindingKind.SCHEMA,
                Severity.WARNING, null, null, notice, notice, null, null);
    }

    /** Sinaliza um teto (contagem ou volume de texto) sem confundir-se com erro real do documento. */
    private static final class FindingLimitReachedException extends SAXException {
        private final String notice;
        private final long limit;

        private FindingLimitReachedException(String notice, long limit) {
            super("limite por documento atingido");
            this.notice = notice;
            this.limit = limit;
        }
    }

    // SAXParserFactory não é thread-safe: cada validação monta o seu (custo irrisório).
    private XMLReader secureReader() throws SAXException, ParserConfigurationException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newSAXParser().getXMLReader();
    }

    private Finding toFinding(Path xml, ParsedMetadata meta, SAXParseException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        String xsdCode = extractXsdCode(message);
        FindingKind kind = isSignatureMissing(xsdCode, message)
                ? FindingKind.SIGNATURE_MISSING : FindingKind.SCHEMA;
        String field = kind == FindingKind.SIGNATURE_MISSING ? SIGNATURE_FIELD : extractField(message);
        // Severidade base; o FindingReclassifier decide a final conforme o modo pré-emissão.
        Severity severity = kind == FindingKind.SIGNATURE_MISSING ? Severity.INFO : Severity.REJECTION;
        String friendly = translator.translate(kind, xsdCode, field)
                .map(XsdErrorTranslator.Translation::message).orElse(null);
        Integer item = meta.itemIndex().itemAt(e.getLineNumber());
        // Código, classificação e campo saem da mensagem íntegra; só o texto retido é cortado.
        return new Finding(xml, meta.document().accessKey(), item, kind, severity, field,
                xsdCode, capMessage(message), friendly, e.getLineNumber(), e.getColumnNumber());
    }

    /** Corta mensagens desproporcionais, dizendo no texto que o corte foi nosso e não do Xerces. */
    private String capMessage(String message) {
        if (message.length() <= MAX_MESSAGE_CHARS) return message;
        return message.substring(0, MAX_MESSAGE_CHARS)
                + String.format(PT_BR, MESSAGE_TRUNCATION_MARK, MAX_MESSAGE_CHARS);
    }

    /** O código oficial é o prefixo até o primeiro ':' — nunca reescrevemos a mensagem. */
    private String extractXsdCode(String message) {
        if (!message.startsWith(XSD_CODE_PREFIX)) return null;
        int colon = message.indexOf(':');
        return colon < 0 ? message : message.substring(0, colon);
    }

    /**
     * Só é "assinatura ausente" quando o Xerces diz que o conteúdo está <b>incompleto</b>
     * ({@code cvc-complex-type.2.4.b}) <b>e</b> lista a própria {@code ds:Signature} entre os
     * elementos esperados.
     *
     * <p>As duas exigências são necessárias. Sem o nome qualificado, um elemento batizado
     * {@code SignatureXpto} forçaria a classificação a partir de texto que o autor do XML
     * controla. E sem prender o código em {@code .b}, as variantes {@code .a}/{@code .d}
     * ("conteúdo proibido encontrado", que também enumeram os esperados) casariam: um
     * {@code <lixoEstrutural/>} irmão de {@code infNFe}, ou uma assinatura com o namespace
     * errado, sairiam como assinatura ausente e — em modo pré-emissão — seriam rebaixados a
     * INFO, sumindo do relatório. Só {@code .b} significa "faltou elemento".
     */
    private boolean isSignatureMissing(String xsdCode, String message) {
        if (!SIGNATURE_XSD_CODE.equals(xsdCode)) return false;
        for (int at = message.indexOf(SIGNATURE_QNAME); at >= 0;
                at = message.indexOf(SIGNATURE_QNAME, at + 1)) {
            int after = at + SIGNATURE_QNAME.length();
            // Delimitador: fim da enumeração ('}') ou próximo esperado (','). Exclui
            // SignatureValue, SignatureMethod, SignatureProperties e afins.
            if (after < message.length()
                    && (message.charAt(after) == '}' || message.charAt(after) == ',')) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nome do campo defeituoso a partir da mensagem oficial. Os padrões são <b>ancorados</b> no
     * texto fixo que cerca o nome em cada formato de mensagem, e vence a <b>última</b> ocorrência:
     * o Xerces interpola o valor rejeitado <i>antes</i> de nomear o campo
     * ({@code The value 'X' of element 'Y' is not valid}), então um conteúdo hostil como
     * {@code <cNF>element 'pCBS'</cNF>} sequestraria a primeira ocorrência e o relatório mandaria
     * o contador corrigir o campo errado. Depois do nome verdadeiro só vem texto fixo do Xerces,
     * fora do alcance de quem escreve o XML. Nos formatos estruturais o nome vem da árvore do
     * documento (que não admite aspas nem chaves), então lá a ordem é indiferente.
     */
    private String extractField(String message) {
        for (Pattern pattern : FIELD_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            String captured = null;
            while (matcher.find()) {
                captured = matcher.group(1);
            }
            if (captured != null) {
                int colon = captured.lastIndexOf(':');
                return colon >= 0 ? captured.substring(colon + 1) : captured;
            }
        }
        return null;
    }

    private Finding unreadable(Path xml, ParsedMetadata meta, Exception e) {
        // Linha/coluna do erro fatal são a única pista de onde está o defeito num XML malformado.
        Integer line = null;
        Integer column = null;
        if (e instanceof SAXParseException parse) {
            line = positiveOrNull(parse.getLineNumber());
            column = positiveOrNull(parse.getColumnNumber());
        }
        return new Finding(xml, meta.document().accessKey(), null, FindingKind.UNREADABLE,
                Severity.WARNING, null, null, describe(e), null, line, column);
    }

    /** Exceções de I/O de arquivo trazem só o caminho como mensagem: sem isto o achado não explica nada. */
    private String describe(Exception e) {
        if (e instanceof NoSuchFileException) {
            return "Arquivo não encontrado ou removido durante a validação: " + e.getMessage();
        }
        if (e instanceof AccessDeniedException) {
            return "Sem permissão de leitura para o arquivo: " + e.getMessage();
        }
        return Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName());
    }

    private Integer positiveOrNull(int value) {
        return value > 0 ? value : null;
    }

    /**
     * Serve os includes/imports do schema oficial a partir do classpath, pelo nome do arquivo.
     * Os XSDs oficiais se referenciam por caminho relativo e têm nomes únicos na base embarcada;
     * usar só o nome evita depender do protocolo (file: em build, jar: em distribuição) e mantém
     * o acesso externo desligado.
     */
    private static final class ClasspathSchemaResolver implements LSResourceResolver {

        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId,
                String systemId, String baseUri) {
            if (systemId == null) return null;
            String name = systemId.substring(systemId.lastIndexOf('/') + 1);
            for (String dir : SCHEMA_LOOKUP_DIRS) {
                InputStream stream = SchemaValidatorEngine.class.getResourceAsStream(dir + name);
                if (stream != null) return new ClasspathInput(publicId, systemId, stream);
            }
            return null; // não resolvido: o Xerces reporta o include faltante
        }
    }

    /** {@link LSInput} mínimo sobre um recurso do classpath: só o byte stream importa. */
    private static final class ClasspathInput implements LSInput {

        private final InputStream byteStream;
        private String publicId;
        private String systemId;
        private String baseUri;
        private String encoding;
        private String stringData;
        private Reader characterStream;
        private boolean certifiedText;

        private ClasspathInput(String publicId, String systemId, InputStream byteStream) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.byteStream = byteStream;
        }

        @Override public InputStream getByteStream() { return byteStream; }
        @Override public void setByteStream(InputStream stream) { /* fonte fixa: o classpath */ }
        @Override public Reader getCharacterStream() { return characterStream; }
        @Override public void setCharacterStream(Reader reader) { this.characterStream = reader; }
        @Override public String getStringData() { return stringData; }
        @Override public void setStringData(String data) { this.stringData = data; }
        @Override public String getPublicId() { return publicId; }
        @Override public void setPublicId(String id) { this.publicId = id; }
        @Override public String getSystemId() { return systemId; }
        @Override public void setSystemId(String id) { this.systemId = id; }
        @Override public String getBaseURI() { return baseUri; }
        @Override public void setBaseURI(String uri) { this.baseUri = uri; }
        @Override public String getEncoding() { return encoding; }
        @Override public void setEncoding(String value) { this.encoding = value; }
        @Override public boolean getCertifiedText() { return certifiedText; }
        @Override public void setCertifiedText(boolean value) { this.certifiedText = value; }
    }
}
