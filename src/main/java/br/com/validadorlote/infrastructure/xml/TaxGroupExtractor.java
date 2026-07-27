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

    /**
     * Um item e o que ele declarou de IBS/CBS. Campos nulos quando o grupo não existe.
     *
     * <p>{@code itemNumber} é nulo quando o {@code nItem} do {@code <det>} está ausente ou não é
     * número: o item continua existindo com o número desconhecido explícito, porque deduzi-lo da
     * posição no arquivo seria inventar dado que o documento não declarou.
     *
     * <p><b>Dois elementos, dois campos.</b> {@code hasIbsCbsGroup} é o invólucro
     * {@code <IBSCBS>}, que carrega o {@code CST} e por isso existe sempre que o item declara
     * situação tributária. {@code hasGIbsCbsGroup} é o {@code <gIBSCBS>} de dentro dele, uma das
     * alternativas opcionais do {@code choice} do tipo {@code TTribNFe}. Confundir os dois faz
     * item de isenção corretamente emitido virar acusação — ver D-027.
     */
    public record ItemTaxGroup(Integer itemNumber, boolean hasIbsCbsGroup, boolean hasGIbsCbsGroup,
            String cst, String cClassTrib, String cProdANP,
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
        boolean emIbsCbs = false, temGrupo = false, temGrupoInterno = false;
        boolean redUf = false, redMun = false, redCbs = false;
        String cst = null, classTrib = null, prodANP = null;
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
                        emIbsCbs = temGrupo = temGrupoInterno = false;
                        redUf = redMun = redCbs = false;
                        cst = classTrib = prodANP = null;
                        pUf = pMun = pCbs = null;
                        esfera = null;
                    }
                    case "IBSCBS" -> { emIbsCbs = true; temGrupo = true; }
                    // O grupo interno só conta dentro do invólucro do item corrente.
                    case "gIBSCBS" -> { if (emIbsCbs) temGrupoInterno = true; }
                    case "cProdANP" -> { if (prodANP == null) prodANP = texto(r); }
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
                    // O item entra mesmo com nItem ilegível: descartá-lo o faria sumir do
                    // relatório inteiro — nem conforme, nem rejeitado, nem não avaliado.
                    itens.add(new ItemTaxGroup(nItem, temGrupo, temGrupoInterno, cst, classTrib,
                            prodANP, redUf, redMun, redCbs, pUf, pMun, pCbs));
                    nItem = null;
                }
            }
        }
        return itens;
    }

    /**
     * Conteúdo textual direto do elemento corrente, consumindo os eventos até o fechamento dele.
     *
     * <p>Conteúdo misto (filhos onde se esperava texto) devolve {@code null} — mesmo contrato do
     * {@link XmlMetadataParser}: o campo fica ilegível, o arquivo continua sendo lido. Quem
     * reporta o erro estrutural, com mensagem oficial, linha e coluna, é o XSD; perder todos os
     * itens do documento por causa de um campo esquisito seria desproporcional.
     *
     * <p>Não usa {@code getElementText()} de propósito: ele lança no conteúdo misto e deixa o
     * reader parado no filho, dessincronizando a máquina de estados de quem chamou.
     */
    private String texto(XMLStreamReader r) throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        boolean mixedContent = false;
        int depth = 0;
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                mixedContent = true;
                depth++;
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if (depth == 0) break;
                depth--;
            } else if (depth == 0
                    && (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA)) {
                text.append(r.getText());
            }
        }
        if (mixedContent) return null;
        String t = text.toString();
        return t.isBlank() ? null : t.trim();
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
