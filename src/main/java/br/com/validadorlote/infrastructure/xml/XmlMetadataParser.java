package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.domain.ReferencedNote;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        String root = null, accessKey = null, cnpj = null, emitterName = null, emitterState = null,
                emitterMunicipalityCode = null, nNF = null,
                mod = null, serie = null, dhEmi = null,
                crt = null, finNFe = null, tpNFDebito = null;
        // gCompraGov é grupo, não campo de texto: interessa a presença, e ela é do documento
        // (infNFe/ide), não do item — daí não caber no TaxGroupExtractor. pRedutor, ao contrário,
        // é campo de texto direto do grupo (DFeTiposBasicos_v1.00.xsd:1144-1163, tipo
        // TCompraGov), capturado aqui pela mesma razão: é filho de ide, não de item.
        boolean hasCompraGov = false;
        String pRedutor = null;
        // IBSCBSTot é presença de grupo, mesmo raciocínio de gCompraGov: é filho de total, não de
        // item, e sustenta as rejeições 1118/1119 (docs/decisions.md D-039). Não cabe no
        // TaxGroupExtractor, que só existe para o conteúdo tributário por item.
        boolean hasIbsCbsTot = false;
        String totalIbsUf = null, totalIbsMunicipal = null, totalIbs = null, totalCbs = null;
        Map<String, BigDecimal> ibsCbsTotals = new HashMap<>();
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
                    if ("IBSCBSTot".equals(name) && "total".equals(stack.peek())) {
                        hasIbsCbsTot = true;
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
                            case "xNome" -> { if (emitterName == null) emitterName = value; }
                            case "emit/UF" -> { if (emitterState == null) emitterState = value; }
                            case "emit/cMun" -> {
                                if (emitterMunicipalityCode == null) emitterMunicipalityCode = value;
                            }
                            case "nNF" -> { if (nNF == null) nNF = value; }
                            case "mod" -> { if (mod == null) mod = value; }
                            case "serie" -> { if (serie == null) serie = value; }
                            case "dhEmi" -> { if (dhEmi == null) dhEmi = value; }
                            case "CRT" -> { if (crt == null) crt = value; }
                            case "finNFe" -> { if (finNFe == null) finNFe = value; }
                            case "tpNFDebito" -> { if (tpNFDebito == null) tpNFDebito = value; }
                            case "pRedutor" -> { if (pRedutor == null) pRedutor = value; }
                            case "total/vIBSUF" -> { if (totalIbsUf == null) totalIbsUf = value; }
                            case "total/vIBSMun" -> {
                                if (totalIbsMunicipal == null) totalIbsMunicipal = value;
                            }
                            case "total/vIBS" -> { if (totalIbs == null) totalIbs = value; }
                            case "total/vCBS" -> { if (totalCbs == null) totalCbs = value; }
                            case "total/vBC" -> ibsCbsTotals.put("vBC", decimal(value));
                            case "total/vDifIBSUF", "total/vDevIBSUF", "total/vDifIBSMun",
                                    "total/vDevIBSMun", "total/vCredPresIBS", "total/vDifCBS",
                                    "total/vDevCBS", "total/vCredPresCBS" ->
                                    ibsCbsTotals.put(capturing.substring("total/".length()), decimal(value));
                            case "total/vIBSMono", "total/vCBSMono", "total/vIBSMonoReten",
                                    "total/vCBSMonoReten", "total/vIBSMonoRet", "total/vCBSMonoRet",
                                    "total/vIBSEstCred", "total/vCBSEstCred" ->
                                    ibsCbsTotals.put(capturing.substring("total/".length()), decimal(value));
                            // NFref aceita até 999 ocorrências: todas contam, nada de "primeira".
                            case "refNFe", "refNFeSig" -> references.add(
                                    new ReferencedNote(capturing, AccessKeyMonth.ofAccessKey(value)));
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
                    new FiscalDocument(source, null, null, null, null, null, null, null, root,
                            null, null, null, false, null, false, List.of()),
                    ItemLineIndex.of(ranges));
        }
        return new ParsedMetadata(
                new FiscalDocument(source, accessKey, cnpj, emitterName, emitterState,
                        emitterMunicipalityCode, nNF, parseIssueDate(dhEmi), mod, serie, root,
                        crt, finNFe, tpNFDebito, hasCompraGov, decimal(pRedutor), hasIbsCbsTot,
                        decimal(totalIbsUf), decimal(totalIbsMunicipal), decimal(totalIbs),
                        decimal(totalCbs), references, ibsCbsTotals),
                ItemLineIndex.of(ranges));
    }

    /** Mesmo contrato de {@link #parseIssueDate}: valor ilegível vira {@code null}, nunca erro. */
    private BigDecimal decimal(String v) {
        if (v == null) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Nome do campo cujo texto deve ser capturado, ou null se o elemento não interessa. */
    private String targetField(Deque<String> stack) {
        if (isFirst(stack, "CNPJ", "emit")) return "CNPJ";
        if (isFirst(stack, "xNome", "emit")) return "xNome";
        if (isPath(stack, "UF", "enderEmit", "emit")) return "emit/UF";
        if (isPath(stack, "cMun", "enderEmit", "emit")) return "emit/cMun";
        if (isFirst(stack, "CRT", "emit")) return "CRT";
        if (isFirst(stack, "nNF", "ide")) return "nNF";
        if (isFirst(stack, "mod", "ide")) return "mod";
        if (isFirst(stack, "serie", "ide")) return "serie";
        if (isFirst(stack, "dhEmi", "ide")) return "dhEmi";
        if (isFirst(stack, "finNFe", "ide")) return "finNFe";
        if (isFirst(stack, "tpNFDebito", "ide")) return "tpNFDebito";
        if (isFirst(stack, "refNFe", "NFref")) return "refNFe";
        if (isFirst(stack, "refNFeSig", "NFref")) return "refNFeSig";
        // Único pRedutor de infNFe/ide: o gTribCompraGov do item (TTribCompraGov) não tem campo
        // de mesmo nome — conferido no XSD embarcado.
        if (isFirst(stack, "pRedutor", "gCompraGov")) return "pRedutor";
        if (isPath(stack, "vIBSUF", "gIBSUF", "gIBS", "IBSCBSTot", "total")) {
            return "total/vIBSUF";
        }
        if (isPath(stack, "vIBSMun", "gIBSMun", "gIBS", "IBSCBSTot", "total")) {
            return "total/vIBSMun";
        }
        if (isPath(stack, "vIBS", "gIBS", "IBSCBSTot", "total")) return "total/vIBS";
        if (isPath(stack, "vCBS", "gCBS", "IBSCBSTot", "total")) return "total/vCBS";
        // O total é "vBCIBSCBS" (TIBSCBSMonoTot, DFeTiposBasicos_v1.00.xsd:563), não "vBC" —
        // esse nome só existe no item (gIBSCBS/vBC, TCIBS/TCIBS_NFe). A chave interna continua
        // "total/vBC" para casar com Sphere.BASE em TaxTotalizationRule.
        // O total é "vBCIBSCBS" (TIBSCBSMonoTot, DFeTiposBasicos_v1.00.xsd:563), não "vBC" —
        // esse nome só existe no item (gIBSCBS/vBC, TCIBS/TCIBS_NFe). A chave interna continua
        // "total/vBC" para casar com Sphere.BASE em TaxTotalizationRule.
        if (isPath(stack, "vBCIBSCBS", "IBSCBSTot", "total")) return "total/vBC";
        if (isPath(stack, "vDif", "gIBSUF", "gIBS", "IBSCBSTot", "total")) return "total/vDifIBSUF";
        if (isPath(stack, "vDevTrib", "gIBSUF", "gIBS", "IBSCBSTot", "total")) return "total/vDevIBSUF";
        if (isPath(stack, "vDif", "gIBSMun", "gIBS", "IBSCBSTot", "total")) return "total/vDifIBSMun";
        if (isPath(stack, "vDevTrib", "gIBSMun", "gIBS", "IBSCBSTot", "total")) return "total/vDevIBSMun";
        if (isPath(stack, "vCredPres", "gIBS", "IBSCBSTot", "total")) return "total/vCredPresIBS";
        if (isPath(stack, "vDif", "gCBS", "IBSCBSTot", "total")) return "total/vDifCBS";
        if (isPath(stack, "vDevTrib", "gCBS", "IBSCBSTot", "total")) return "total/vDevCBS";
        if (isPath(stack, "vCredPres", "gCBS", "IBSCBSTot", "total")) return "total/vCredPresCBS";
        if (isPath(stack, "vIBSMono", "gMono", "IBSCBSTot", "total")) return "total/vIBSMono";
        if (isPath(stack, "vCBSMono", "gMono", "IBSCBSTot", "total")) return "total/vCBSMono";
        if (isPath(stack, "vIBSMonoReten", "gMono", "IBSCBSTot", "total")) return "total/vIBSMonoReten";
        if (isPath(stack, "vCBSMonoReten", "gMono", "IBSCBSTot", "total")) return "total/vCBSMonoReten";
        if (isPath(stack, "vIBSMonoRet", "gMono", "IBSCBSTot", "total")) return "total/vIBSMonoRet";
        if (isPath(stack, "vCBSMonoRet", "gMono", "IBSCBSTot", "total")) return "total/vCBSMonoRet";
        if (isPath(stack, "vIBSEstCred", "gEstornoCred", "IBSCBSTot", "total")) return "total/vIBSEstCred";
        if (isPath(stack, "vCBSEstCred", "gEstornoCred", "IBSCBSTot", "total")) return "total/vCBSEstCred";
        // refNF e refNFP trazem AAMM próprio e explícito no XSD (linhas 341 e 393).
        if (isFirst(stack, "AAMM", "refNF")) return "refNF/AAMM";
        if (isFirst(stack, "AAMM", "refNFP")) return "refNFP/AAMM";
        return null;
    }

    /** O campo AAMM de refNF/refNFP não informa o século; essa incerteza acompanha a referência. */
    private ReferencedNote paperReference(String form, String aamm) {
        return new ReferencedNote(form, AccessKeyMonth.ofAamm(aamm), true);
    }

    private boolean isFirst(Deque<String> stack, String element, String parent) {
        var it = stack.iterator();
        return it.hasNext() && it.next().equals(element) && it.hasNext() && it.next().equals(parent);
    }

    private boolean isPath(Deque<String> stack, String... path) {
        var it = stack.iterator();
        for (String element : path) {
            if (!it.hasNext() || !it.next().equals(element)) return false;
        }
        return true;
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
