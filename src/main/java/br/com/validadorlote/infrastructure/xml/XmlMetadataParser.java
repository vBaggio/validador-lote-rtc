package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.domain.ReferencedNote;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Extrai metadados mínimos e o índice linha→item via StAX seguro (sem DTD/entidades externas).
 *
 * <p>Contrato: metadado ausente ou inválido vira {@code null} — o parser não inventa dado nem
 * derruba o arquivo por um campo ruim, porque a validação XSD roda em seguida e reporta o erro
 * real com linha, coluna e mensagem oficial. Só falham o XML malformado, a raiz desconhecida
 * e o DOCTYPE.
 */
public final class XmlMetadataParser {

    private static final Set<String> KNOWN_ROOTS = Set.of("NFe", "nfeProc", "enviNFe");
    private static final int DATE_PREFIX_LENGTH = 10;

    /**
     * Formas de {@code NFref} que não trazem data passível de leitura offline. A chave de CT-e
     * tem {@code AAMM} no mesmo deslocamento da de NF-e, mas é documento de transporte e fica
     * de fora de propósito: alargar o escopo não compensa a margem.
     */
    private static final Set<String> UNDATABLE_REFERENCES = Set.of("refCTe", "refECF");
    /** Referências em papel: a data vem de um campo {@code AAMM} próprio, não de chave. */
    private static final Set<String> PAPER_REFERENCES = Set.of("refNF", "refNFP");
    private static final int ACCESS_KEY_LENGTH = 44;
    /** Posições do {@code AAMM} na chave de acesso, conforme a documentação do XSD (linha 322). */
    private static final int KEY_AAMM_START = 2;
    private static final int KEY_AAMM_END = 6;
    private static final int AAMM_LENGTH = 4;
    /**
     * Século usado para normalizar o AAMM em {@link YearMonth}. Para chaves eletrônicas, ele é
     * parte da política do formato; no AAMM avulso de papel, a ambiguidade fica explícita no
     * {@link ReferencedNote} e não pode ser descartada por esta normalização técnica.
     */
    private static final int NORMALIZED_CENTURY = 2000;

    public ParsedMetadata parse(Path xml) {
        // XMLInputFactory não é thread-safe: uma por chamada (custo irrisório vs I/O).
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (InputStream in = Files.newInputStream(xml)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            try {
                return read(xml, reader);
            } finally {
                closeQuietly(reader);
            }
        } catch (XMLStreamException | IOException e) {
            throw new UnreadableXmlException("Arquivo ilegível como XML: " + xml.getFileName(), e);
        }
    }

    /**
     * Libera os buffers/decodificadores internos do reader. Falha ao fechar não deve mascarar
     * uma exceção em curso (de {@link #read}), por isso é sempre engolida aqui.
     */
    private void closeQuietly(XMLStreamReader reader) {
        try {
            reader.close();
        } catch (XMLStreamException ignored) {
            // liberação best-effort; sem impacto funcional se falhar.
        }
    }

    private ParsedMetadata read(Path source, XMLStreamReader r) throws XMLStreamException {
        String root = null, accessKey = null, cnpj = null, nNF = null, mod = null, dhEmi = null,
                crt = null, finNFe = null, tpNFDebito = null;
        // gCompraGov é grupo, não campo de texto: interessa a presença, e ela é do documento
        // (infNFe/ide), não do item — daí não caber no TaxGroupExtractor.
        boolean hasCompraGov = false;
        int infNFeCount = 0;
        List<ReferencedNote> references = new ArrayList<>();
        // refNF/refNFP aberto cujo AAMM ainda não apareceu. Se ele fechar sem o campo, a
        // referência entra sem data: some-la faria a exceção da UB12-10 deixar de ser
        // consultada e a devolução virar acusação.
        String pendingPaperRef = null;
        List<int[]> ranges = new ArrayList<>();
        List<int[]> openDets = new ArrayList<>(); // pilha; aceita null (det sem faixa)
        Deque<String> stack = new ArrayDeque<>();

        // Captura de texto: só o conteúdo direto do elemento-alvo conta. Um filho no meio
        // (conteúdo misto) invalida a captura — campo tratado como ausente, não como erro.
        String capturing = null;
        int captureDepth = 0;
        boolean mixedContent = false;
        StringBuilder text = new StringBuilder();

        while (r.hasNext()) {
            switch (r.next()) {
                // SUPPORT_DTD=false impede o processamento das declarações, mas o DOCTYPE
                // ainda chega como evento. XML de terceiro com DTD é rejeitado por política.
                case XMLStreamConstants.DTD -> throw new UnreadableXmlException(
                        "DOCTYPE não é permitido em XML de terceiro: " + source.getFileName());
                case XMLStreamConstants.START_ELEMENT -> {
                    String name = r.getLocalName();
                    if (root == null) {
                        if (!KNOWN_ROOTS.contains(name)) {
                            throw new UnreadableXmlException(
                                    "Elemento raiz não reconhecido (<" + name + ">): " + source.getFileName());
                        }
                        root = name;
                    }
                    if (capturing != null) mixedContent = true;
                    if ("infNFe".equals(name)) {
                        infNFeCount++;
                        String id = r.getAttributeValue(null, "Id");
                        if (accessKey == null && id != null && id.startsWith("NFe")) {
                            accessKey = blankToNull(id.substring(3));
                        }
                    }
                    if ("NFref".equals(stack.peek())) {
                        if (UNDATABLE_REFERENCES.contains(name)) {
                            // Referência que existe mas não podemos datar sem consulta externa:
                            // registrada assim mesmo, para a regra saber que não sabe.
                            references.add(new ReferencedNote(name, null));
                        } else if (PAPER_REFERENCES.contains(name)) {
                            pendingPaperRef = name;
                        }
                    }
                    // stack.peek() ainda é o pai: o push do elemento corrente vem depois.
                    if ("gCompraGov".equals(name) && "ide".equals(stack.peek())) {
                        hasCompraGov = true;
                    }
                    if ("det".equals(name)) {
                        Integer item = parseItem(r.getAttributeValue(null, "nItem"));
                        openDets.add(item == null
                                ? null // nItem ausente ou inválido: nenhuma faixa para este bloco
                                : new int[]{r.getLocation().getLineNumber(), 0, item});
                    }
                    stack.push(name);
                    if (capturing == null) {
                        capturing = targetField(stack);
                        if (capturing != null) {
                            captureDepth = stack.size();
                            mixedContent = false;
                            text.setLength(0);
                        }
                    }
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    if (capturing != null) text.append(r.getText());
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if (capturing != null && stack.size() == captureDepth) {
                        String value = mixedContent ? null : blankToNull(text.toString());
                        switch (capturing) {
                            case "CNPJ" -> { if (cnpj == null) cnpj = value; }
                            case "nNF" -> { if (nNF == null) nNF = value; }
                            case "mod" -> { if (mod == null) mod = value; }
                            case "dhEmi" -> { if (dhEmi == null) dhEmi = value; }
                            case "CRT" -> { if (crt == null) crt = value; }
                            case "finNFe" -> { if (finNFe == null) finNFe = value; }
                            case "tpNFDebito" -> { if (tpNFDebito == null) tpNFDebito = value; }
                            // NFref aceita até 999 ocorrências: todas contam, nada de "primeira".
                            case "refNFe", "refNFeSig" ->
                                    references.add(new ReferencedNote(capturing, monthOfKey(value)));
                            case "refNF/AAMM" -> {
                                references.add(paperReference("refNF", value));
                                pendingPaperRef = null;
                            }
                            case "refNFP/AAMM" -> {
                                references.add(paperReference("refNFP", value));
                                pendingPaperRef = null;
                            }
                        }
                        capturing = null;
                    }
                    if (pendingPaperRef != null && pendingPaperRef.equals(r.getLocalName())) {
                        // Fechou sem AAMM legível: a referência existe e não sabemos datá-la.
                        references.add(new ReferencedNote(pendingPaperRef, null));
                        pendingPaperRef = null;
                    }
                    if ("det".equals(r.getLocalName()) && !openDets.isEmpty()) {
                        int[] range = openDets.remove(openDets.size() - 1);
                        if (range != null) {
                            range[1] = r.getLocation().getLineNumber();
                            ranges.add(range);
                        }
                    }
                    if (!stack.isEmpty()) stack.pop();
                }
                default -> { /* comentários, PIs e declaração: irrelevantes para metadados */ }
            }
        }

        if (infNFeCount > 1) {
            // Lote enviNFe com várias notas: metadados da 1ª nota valeriam para todas (D-016).
            return new ParsedMetadata(
                    new FiscalDocument(source, null, null, null, null, null, root, null,
                            null, null, false, List.of()),
                    ItemLineIndex.of(ranges));
        }
        return new ParsedMetadata(
                new FiscalDocument(source, accessKey, cnpj, nNF, parseIssueDate(dhEmi), mod, root,
                        crt, finNFe, tpNFDebito, hasCompraGov, references),
                ItemLineIndex.of(ranges));
    }

    /** Nome do campo cujo texto deve ser capturado, ou null se o elemento não interessa. */
    private String targetField(Deque<String> stack) {
        if (isFirst(stack, "CNPJ", "emit")) return "CNPJ";
        if (isFirst(stack, "CRT", "emit")) return "CRT";
        if (isFirst(stack, "nNF", "ide")) return "nNF";
        if (isFirst(stack, "mod", "ide")) return "mod";
        if (isFirst(stack, "dhEmi", "ide")) return "dhEmi";
        if (isFirst(stack, "finNFe", "ide")) return "finNFe";
        if (isFirst(stack, "tpNFDebito", "ide")) return "tpNFDebito";
        if (isFirst(stack, "refNFe", "NFref")) return "refNFe";
        if (isFirst(stack, "refNFeSig", "NFref")) return "refNFeSig";
        // refNF e refNFP trazem AAMM próprio e explícito no XSD (linhas 341 e 393).
        if (isFirst(stack, "AAMM", "refNF")) return "refNF/AAMM";
        if (isFirst(stack, "AAMM", "refNFP")) return "refNFP/AAMM";
        return null;
    }

    /**
     * Competência de emissão codificada na chave de acesso referenciada: o {@code AAMM} ocupa as
     * posições 2-5, conforme a documentação do próprio XSD. Chave fora do formato devolve
     * {@code null} — referência que não sabemos datar, e não uma data inventada.
     */
    private YearMonth monthOfKey(String key) {
        if (key == null || key.length() != ACCESS_KEY_LENGTH || !isDigits(key)) return null;
        return monthOfAamm(key.substring(KEY_AAMM_START, KEY_AAMM_END));
    }

    /** O campo AAMM de refNF/refNFP não informa o século; essa incerteza acompanha a referência. */
    private ReferencedNote paperReference(String form, String aamm) {
        return new ReferencedNote(form, monthOfAamm(aamm), true);
    }

    private YearMonth monthOfAamm(String aamm) {
        if (aamm == null || aamm.length() != AAMM_LENGTH || !isDigits(aamm)) return null;
        int month = Integer.parseInt(aamm.substring(2));
        if (month < 1 || month > 12) return null;
        return YearMonth.of(NORMALIZED_CENTURY + Integer.parseInt(aamm.substring(0, 2)), month);
    }

    private boolean isDigits(String value) {
        return value.chars().allMatch(Character::isDigit);
    }

    private boolean isFirst(Deque<String> stack, String element, String parent) {
        var it = stack.iterator();
        return it.hasNext() && it.next().equals(element) && it.hasNext() && it.next().equals(parent);
    }

    private String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private Integer parseItem(String nItem) {
        if (nItem == null) return null;
        try {
            return Integer.valueOf(nItem.trim());
        } catch (NumberFormatException e) {
            return null; // nItem fora do contrato: sem faixa, e o XSD reporta o erro real
        }
    }

    private LocalDate parseIssueDate(String dhEmi) {
        if (dhEmi == null || dhEmi.length() < DATE_PREFIX_LENGTH) return null;
        try {
            return LocalDate.parse(dhEmi.substring(0, DATE_PREFIX_LENGTH));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
