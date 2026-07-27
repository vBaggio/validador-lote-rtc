package br.com.validadorlote.infrastructure.xml;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lê o grupo IBS/CBS de cada item. Separado do {@link XmlMetadataParser} porque este extrai
 * identificação do documento, enquanto aqui interessa o conteúdo tributário por item.
 *
 * <p>Contrato: campo ausente vira {@code null} e subgrupo ausente vira {@code false} — a leitura
 * não inventa dado nem acusa nada. Só falham o XML malformado e o DOCTYPE.
 */
public final class TaxGroupExtractor {

    /** Um item e o que ele declarou de IBS/CBS. Campos nulos quando o grupo não existe. */
    public record ItemTaxGroup(int itemNumber, boolean hasIbsCbsGroup, String cst, String cClassTrib,
            boolean hasReducaoUf, boolean hasReducaoMun, boolean hasReducaoCbs,
            BigDecimal percReducaoUf, BigDecimal percReducaoMun, BigDecimal percReducaoCbs) {}

    /** Esfera de tributação em que um subgrupo de redução pode aparecer. */
    private enum Esfera {
        UF, MUN, CBS;

        /** A esfera aberta por este elemento, ou null se ele não abre esfera nenhuma. */
        static Esfera of(String element) {
            return switch (element) {
                case "gIBSUF" -> UF;
                case "gIBSMun" -> MUN;
                case "gCBS" -> CBS;
                default -> null;
            };
        }
    }

    public List<ItemTaxGroup> extract(Path xml) {
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
            throw new UnreadableXmlException("Falha ao ler grupos IBS/CBS: " + xml.getFileName(), e);
        }
    }

    private void closeQuietly(XMLStreamReader reader) {
        try {
            reader.close();
        } catch (XMLStreamException ignored) {
            // liberação best-effort; sem impacto funcional se falhar.
        }
    }

    private List<ItemTaxGroup> read(Path source, XMLStreamReader r) throws XMLStreamException {
        List<ItemTaxGroup> itens = new ArrayList<>();
        Integer nItem = null;
        boolean emIbsCbs = false, temGrupo = false;
        boolean redUf = false, redMun = false, redCbs = false;
        String cst = null, classTrib = null;
        BigDecimal pUf = null, pMun = null, pCbs = null;
        // Esfera atualmente aberta. Precisa ser zerada no fechamento da esfera e na abertura de
        // cada det: um gRed fora de esfera não pode herdar a última esfera vista, sob pena de
        // acusar redução onde não há.
        Esfera esfera = null;

        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.DTD) {
                // SUPPORT_DTD=false impede o processamento das declarações, mas o DOCTYPE ainda
                // chega como evento. XML de terceiro com DTD é rejeitado por política.
                throw new UnreadableXmlException(
                        "DOCTYPE não é permitido em XML de terceiro: " + source.getFileName());
            }
            if (ev == XMLStreamConstants.START_ELEMENT) {
                String nome = r.getLocalName();
                Esfera aberta = Esfera.of(nome);
                if (aberta != null) {
                    esfera = aberta;
                    continue;
                }
                switch (nome) {
                    case "det" -> {
                        nItem = parseItem(r.getAttributeValue(null, "nItem"));
                        emIbsCbs = temGrupo = redUf = redMun = redCbs = false;
                        cst = classTrib = null;
                        pUf = pMun = pCbs = null;
                        esfera = null;
                    }
                    case "IBSCBS" -> { emIbsCbs = true; temGrupo = true; }
                    case "gRed" -> {
                        if (esfera == Esfera.UF) redUf = true;
                        else if (esfera == Esfera.MUN) redMun = true;
                        else if (esfera == Esfera.CBS) redCbs = true;
                    }
                    case "CST" -> { if (emIbsCbs && cst == null) cst = texto(r); }
                    case "cClassTrib" -> { if (emIbsCbs && classTrib == null) classTrib = texto(r); }
                    case "pRedAliq" -> {
                        BigDecimal v = decimal(texto(r));
                        if (esfera == Esfera.UF) pUf = v;
                        else if (esfera == Esfera.MUN) pMun = v;
                        else if (esfera == Esfera.CBS) pCbs = v;
                    }
                    default -> { /* demais elementos não alimentam nenhuma regra */ }
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                String nome = r.getLocalName();
                if (Esfera.of(nome) != null) esfera = null;
                if ("IBSCBS".equals(nome)) emIbsCbs = false;
                if ("det".equals(nome)) {
                    if (nItem != null) {
                        itens.add(new ItemTaxGroup(nItem, temGrupo, cst, classTrib,
                                redUf, redMun, redCbs, pUf, pMun, pCbs));
                    }
                    nItem = null;
                }
            }
        }
        return itens;
    }

    private String texto(XMLStreamReader r) throws XMLStreamException {
        String t = r.getElementText();
        return (t == null || t.isBlank()) ? null : t.trim();
    }

    private Integer parseItem(String v) {
        if (v == null) return null;
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;   // nItem inválido: o XSD reporta; aqui não derruba a leitura
        }
    }

    private BigDecimal decimal(String v) {
        if (v == null) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
