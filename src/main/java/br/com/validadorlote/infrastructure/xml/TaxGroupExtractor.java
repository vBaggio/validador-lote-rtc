package br.com.validadorlote.infrastructure.xml;

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
     *
     * @param dfeReferenciado referência de {@code det/prod/DFeReferenciado} deste item, ou
     *         {@code null} quando o grupo não existe. A partir da NT v1.40 (produção 01/09/2026,
     *         VC02-14) é aqui, e não mais em {@code NFref}, que a devolução deve referenciar a
     *         nota original — ver D-038. {@code issuedAt} nulo dentro da referência significa
     *         grupo presente com chave não decodável, nunca ausência do grupo.
     * @param hasDifUf presença de {@code gIBSUF/gDif}. @param hasDifMun presença de
     *         {@code gIBSMun/gDif}. @param hasDifCbs presença de {@code gCBS/gDif}. Mesma forma de
     *         captura de {@code gRed}, por esfera (bloco 7).
     * @param hasDevTribUf presença de {@code gIBSUF/gDevTrib}. @param hasDevTribMun presença de
     *         {@code gIBSMun/gDevTrib}. @param hasDevTribCbs presença de {@code gCBS/gDevTrib}
     *         (bloco 7).
     * @param hasCredPresOper presença de {@code IBSCBS/gCredPresOper} — filho direto do invólucro
     *         {@code IBSCBS} (2º {@code choice} de {@code TTribNFe}), não de {@code gIBSCBS}
     *         (bloco 7).
     * @param hasCredPresIbsZfm presença de {@code IBSCBS/gCredPresIBSZFM}, mesma posição de
     *         {@code hasCredPresOper} — as duas são alternativas do mesmo {@code choice} (bloco 7).
     * @param hasTpCredPresIbsZfm presença de {@code det/prod/tpCredPresIBSZFM} (I05k), campo de
     *         produto independente do grupo {@code gCredPresIBSZFM}. Valor zero ainda conta como
     *         informado para as rejeições 1165/1166.
     * @param hasTribCompraGov presença de {@code gIBSCBS/gTribCompraGov}, filho de {@code TCIBS}
     *         — de dentro do grupo interno {@code gIBSCBS}, não do invólucro (bloco 7).
     */
    public record ItemTaxGroup(Integer itemNumber, boolean hasIbsCbsGroup, boolean hasGIbsCbsGroup,
            String cst, String cClassTrib, String cProdANP,
            boolean hasReducaoUf, boolean hasReducaoMun, boolean hasReducaoCbs,
            BigDecimal percReducaoUf, BigDecimal percReducaoMun, BigDecimal percReducaoCbs,
            ReferencedNote dfeReferenciado,
            boolean hasDifUf, boolean hasDifMun, boolean hasDifCbs,
            boolean hasDevTribUf, boolean hasDevTribMun, boolean hasDevTribCbs,
            boolean hasCredPresOper, boolean hasCredPresIbsZfm, boolean hasTpCredPresIbsZfm,
            boolean hasTribCompraGov) {}

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
        boolean difUf = false, difMun = false, difCbs = false;
        boolean devTribUf = false, devTribMun = false, devTribCbs = false;
        boolean credPresOper = false, credPresIbsZfm = false, tpCredPresIbsZfm = false;
        boolean tribCompraGov = false;
        String cst = null, classTrib = null, prodANP = null;
        BigDecimal pUf = null, pMun = null, pCbs = null;
        ReferencedNote dfeReferenciado = null;
        // Escopos vivos de dentro do invólucro IBSCBS para gTribCompraGov e do produto para I05k.
        boolean emGIbsCbs = false, emProd = false;
        // DFeReferenciado é filho de det/prod, mas não tem o marcador de esfera das reduções:
        // precisa do próprio flag para chaveAcesso não ser lido fora do grupo.
        boolean emDFeReferenciado = false;
        // total/IBSCBSTot/gCBS reusa o mesmo nome local que abre a esfera CBS do item (auditoria
        // docs/pesquisa/auditoria-regras-e-leitura.md §4.2): sem este flag, Esfera.of() não
        // consegue distinguir as duas ocorrências só pelo nome. Hoje a colisão é inofensiva por
        // coincidência de ordenação do leiaute (total vem depois do último det, e a subárvore de
        // totais não tem gRed/pRedAliq) — o flag existe para que deixe de depender disso.
        boolean emDet = false;
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
                    // Só abre esfera dentro de um item: gIBSUF/gIBSMun/gCBS de
                    // total/IBSCBSTot não representam a esfera do item corrente.
                    if (emDet) esfera = aberta;
                    continue;
                }
                switch (nome) {
                    case "det" -> {
                        nItem = parseItem(r.getAttributeValue(null, "nItem"));
                        emIbsCbs = temGrupo = temGrupoInterno = false;
                        redUf = redMun = redCbs = false;
                        difUf = difMun = difCbs = false;
                        devTribUf = devTribMun = devTribCbs = false;
                        credPresOper = credPresIbsZfm = tpCredPresIbsZfm = false;
                        tribCompraGov = false;
                        cst = classTrib = prodANP = null;
                        pUf = pMun = pCbs = null;
                        dfeReferenciado = null;
                        emDFeReferenciado = false;
                        esfera = null;
                        emGIbsCbs = emProd = false;
                        emDet = true;
                    }
                    case "prod" -> emProd = true;
                    case "IBSCBS" -> { emIbsCbs = true; temGrupo = true; }
                    // O grupo interno só conta dentro do invólucro do item corrente.
                    case "gIBSCBS" -> { if (emIbsCbs) { temGrupoInterno = true; emGIbsCbs = true; } }
                    // Filhos diretos do invólucro (2º choice de TTribNFe), não de gIBSCBS.
                    case "gCredPresOper" -> { if (emIbsCbs) credPresOper = true; }
                    case "gCredPresIBSZFM" -> {
                        if (emIbsCbs) credPresIbsZfm = true;
                    }
                    case "tpCredPresIBSZFM" -> { if (emProd) tpCredPresIbsZfm = true; }
                    // Filho de gIBSCBS/TCIBS (não do invólucro) — precisa do escopo emGIbsCbs.
                    case "gTribCompraGov" -> { if (emGIbsCbs) tribCompraGov = true; }
                    case "cProdANP" -> { if (prodANP == null) prodANP = texto(r); }
                    case "DFeReferenciado" -> emDFeReferenciado = true;
                    case "chaveAcesso" -> {
                        // NT v1.40 (VC02-14): a partir de 01/09/2026 é aqui, por item, que a
                        // devolução referencia a nota original — não mais em NFref (D-038).
                        // Mesma decodificação de AAMM que refNFe já usa.
                        if (emDFeReferenciado && dfeReferenciado == null) {
                            dfeReferenciado = new ReferencedNote("DFeReferenciado",
                                    AccessKeyMonth.ofAccessKey(texto(r)));
                        }
                    }
                    case "gRed" -> {
                        if (esfera == Esfera.UF) redUf = true;
                        else if (esfera == Esfera.MUN) redMun = true;
                        else if (esfera == Esfera.CBS) redCbs = true;
                    }
                    case "gDif" -> {
                        if (esfera == Esfera.UF) difUf = true;
                        else if (esfera == Esfera.MUN) difMun = true;
                        else if (esfera == Esfera.CBS) difCbs = true;
                    }
                    case "gDevTrib" -> {
                        if (esfera == Esfera.UF) devTribUf = true;
                        else if (esfera == Esfera.MUN) devTribMun = true;
                        else if (esfera == Esfera.CBS) devTribCbs = true;
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
                if ("gIBSCBS".equals(nome)) emGIbsCbs = false;
                if ("prod".equals(nome)) emProd = false;
                if ("DFeReferenciado".equals(nome)) {
                    if (dfeReferenciado == null) {
                        // O grupo abriu, mas chaveAcesso não veio (ausente, vazia ou conteúdo
                        // misto): a referência existe e não sabemos datá-la — nunca tratada como
                        // grupo ausente, sob pena de a Exceção 1 da 1115 acusar quem informou o
                        // grupo mas com um valor que o parser não leu.
                        dfeReferenciado = new ReferencedNote("DFeReferenciado", null);
                    }
                    emDFeReferenciado = false;
                }
                if ("det".equals(nome)) {
                    // O item entra mesmo com nItem ilegível: descartá-lo o faria sumir do
                    // relatório inteiro — nem conforme, nem rejeitado, nem não avaliado.
                    itens.add(new ItemTaxGroup(nItem, temGrupo, temGrupoInterno, cst, classTrib,
                            prodANP, redUf, redMun, redCbs, pUf, pMun, pCbs, dfeReferenciado,
                            difUf, difMun, difCbs, devTribUf, devTribMun, devTribCbs,
                            credPresOper, credPresIbsZfm, tpCredPresIbsZfm, tribCompraGov));
                    nItem = null;
                    emDet = false;
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
