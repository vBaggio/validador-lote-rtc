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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê o grupo IBS/CBS de cada item. Separado do {@link XmlMetadataParser} porque este extrai
 * identificação do documento, enquanto aqui interessa o conteúdo tributário por item.
 *
 * <p>Contrato: campo ausente vira {@code null} e subgrupo ausente vira {@code false} — a leitura
 * não inventa dado nem acusa nada. Só falham o XML malformado e o DOCTYPE.
 */
public final class TaxGroupExtractor {

    private static final String NFE_NAMESPACE = "http://www.portalfiscal.inf.br/nfe";

    private record ElementScope(String namespace, String name) {}

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
     * @param hasIndBemMovelUsado presença do campo {@code det/prod/indBemMovelUsado}; seu valor
     *         pode ser nulo quando o texto informado é ilegível.
     * @param ajusteCompetIbs valor {@code gAjusteCompet/vIBS}; zero informado permanece zero.
     * @param ajusteCompetCbs valor {@code gAjusteCompet/vCBS}; ausente ou ilegível permanece nulo.
     * @param estornoCredIbs valor {@code gEstornoCred/vIBSEstCred}, nome real no XSD embarcado.
     * @param estornoCredCbs valor {@code gEstornoCred/vCBSEstCred}, nome real no XSD embarcado.
     */
    public record ItemTaxGroup(Integer itemNumber, boolean hasIbsCbsGroup, boolean hasGIbsCbsGroup,
            String cst, String cClassTrib, String cProdANP,
            boolean hasReducaoUf, boolean hasReducaoMun, boolean hasReducaoCbs,
            BigDecimal percReducaoUf, BigDecimal percReducaoMun, BigDecimal percReducaoCbs,
            ReferencedNote dfeReferenciado,
            boolean hasDifUf, boolean hasDifMun, boolean hasDifCbs,
            boolean hasDevTribUf, boolean hasDevTribMun, boolean hasDevTribCbs,
            boolean hasCredPresOper, boolean hasCredPresIbsZfm, boolean hasTpCredPresIbsZfm,
            boolean hasTribCompraGov, boolean hasGIbsCbsMono, boolean hasTransfCred,
            boolean hasAjusteCompet, boolean hasEstornoCred, boolean hasTribRegular,
            boolean hasIndBemMovelUsado, String indBemMovelUsado,
            BigDecimal ajusteCompetIbs, BigDecimal ajusteCompetCbs,
            BigDecimal estornoCredIbs, BigDecimal estornoCredCbs,
            BigDecimal valueIbsUf, BigDecimal valueIbsMunicipal, BigDecimal valueIbs,
            BigDecimal valueCbs, String presumedCreditCode, BigDecimal presumedIbsCredit,
            Map<String, BigDecimal> declaredAmounts) {

        public ItemTaxGroup {
            declaredAmounts = declaredAmounts == null ? Map.of() : Map.copyOf(declaredAmounts);
        }

        /** Compatibilidade para regras que não usam valores de totalização. */
        public ItemTaxGroup(Integer itemNumber, boolean hasIbsCbsGroup, boolean hasGIbsCbsGroup,
                String cst, String cClassTrib, String cProdANP,
                boolean hasReducaoUf, boolean hasReducaoMun, boolean hasReducaoCbs,
                BigDecimal percReducaoUf, BigDecimal percReducaoMun, BigDecimal percReducaoCbs,
                ReferencedNote dfeReferenciado,
                boolean hasDifUf, boolean hasDifMun, boolean hasDifCbs,
                boolean hasDevTribUf, boolean hasDevTribMun, boolean hasDevTribCbs,
                boolean hasCredPresOper, boolean hasCredPresIbsZfm, boolean hasTpCredPresIbsZfm,
                boolean hasTribCompraGov) {
            this(itemNumber, hasIbsCbsGroup, hasGIbsCbsGroup, cst, cClassTrib, cProdANP,
                    hasReducaoUf, hasReducaoMun, hasReducaoCbs, percReducaoUf, percReducaoMun,
                    percReducaoCbs, dfeReferenciado, hasDifUf, hasDifMun, hasDifCbs,
                    hasDevTribUf, hasDevTribMun, hasDevTribCbs, hasCredPresOper,
                    hasCredPresIbsZfm, hasTpCredPresIbsZfm, hasTribCompraGov,
                    false, false, false, false, false, false, null,
                    null, null, null, null, null, null, null, null, null, null, Map.of());
        }

        /** Compatibilidade com chamadores que montam o contrato completo anterior ao B11. */
        public ItemTaxGroup(Integer itemNumber, boolean hasIbsCbsGroup, boolean hasGIbsCbsGroup,
                String cst, String cClassTrib, String cProdANP,
                boolean hasReducaoUf, boolean hasReducaoMun, boolean hasReducaoCbs,
                BigDecimal percReducaoUf, BigDecimal percReducaoMun, BigDecimal percReducaoCbs,
                ReferencedNote dfeReferenciado,
                boolean hasDifUf, boolean hasDifMun, boolean hasDifCbs,
                boolean hasDevTribUf, boolean hasDevTribMun, boolean hasDevTribCbs,
                boolean hasCredPresOper, boolean hasCredPresIbsZfm, boolean hasTpCredPresIbsZfm,
                boolean hasTribCompraGov,
                BigDecimal valueIbsUf, BigDecimal valueIbsMunicipal, BigDecimal valueIbs,
                BigDecimal valueCbs, String presumedCreditCode, BigDecimal presumedIbsCredit,
                Map<String, BigDecimal> declaredAmounts) {
            this(itemNumber, hasIbsCbsGroup, hasGIbsCbsGroup, cst, cClassTrib, cProdANP,
                    hasReducaoUf, hasReducaoMun, hasReducaoCbs, percReducaoUf, percReducaoMun,
                    percReducaoCbs, dfeReferenciado, hasDifUf, hasDifMun, hasDifCbs,
                    hasDevTribUf, hasDevTribMun, hasDevTribCbs, hasCredPresOper,
                    hasCredPresIbsZfm, hasTpCredPresIbsZfm, hasTribCompraGov,
                    false, false, false, false, false, false, null,
                    null, null, null, null, valueIbsUf, valueIbsMunicipal, valueIbs, valueCbs,
                    presumedCreditCode, presumedIbsCredit, declaredAmounts);
        }
    }

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
        boolean tribCompraGov = false, grupoMono = false, transfCred = false,
                ajusteCompet = false, estornoCred = false, tribRegular = false,
                hasIndBemMovelUsado = false;
        String cst = null, classTrib = null, prodANP = null, indBemMovelUsado = null;
        BigDecimal pUf = null, pMun = null, pCbs = null;
        BigDecimal vIbsUf = null, vIbsMunicipal = null, vIbs = null, vCbs = null,
                vCredPresIbs = null, ajusteIbs = null, ajusteCbs = null,
                estornoIbs = null, estornoCbs = null;
        String cCredPres = null;
        Map<String, BigDecimal> declaredAmounts = new HashMap<>();
        ReferencedNote dfeReferenciado = null;
        // Escopos vivos de dentro do invólucro IBSCBS para gTribCompraGov e do produto para I05k.
        boolean emGIbsCbs = false, emProd = false;
        // DFeReferenciado é filho de det/prod, mas não tem o marcador de esfera das reduções:
        // precisa do próprio flag para chaveAcesso não ser lido fora do grupo.
        boolean emDFeReferenciado = false;
        boolean emCredPresOper = false, emIbsCredit = false, emCbsCredit = false,
                emEstornoCred = false, emAjusteCompet = false, emImposto = false;
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
        Deque<ElementScope> path = new ArrayDeque<>();

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
                path.push(new ElementScope(r.getNamespaceURI(), nome));
                // Um homônimo de terceiro, ou um elemento oficial sob ramo estrangeiro, não
                // pertence ao documento fiscal e não pode abrir nenhum dos estados abaixo.
                if (!isOfficialPath(path)) continue;
                // O det mais próximo governa a identidade do item. Um det falso aninhado em item
                // real não pode herdar emDet nem os flags de prod/imposto do ancestral.
                if (hasUnrecognizedNearestDet(path)) continue;
                Esfera aberta = Esfera.of(nome);
                if (aberta != null) {
                    // Só abre esfera dentro de um item: gIBSUF/gIBSMun/gCBS de
                    // total/IBSCBSTot não representam a esfera do item corrente.
                    if (emDet) esfera = aberta;
                    continue;
                }
                switch (nome) {
                    case "det" -> {
                        if (!isPath(path, "det", "infNFe")) break;
                        nItem = parseItem(r.getAttributeValue(null, "nItem"));
                        emIbsCbs = temGrupo = temGrupoInterno = false;
                        redUf = redMun = redCbs = false;
                        difUf = difMun = difCbs = false;
                        devTribUf = devTribMun = devTribCbs = false;
                        credPresOper = credPresIbsZfm = tpCredPresIbsZfm = false;
                        tribCompraGov = grupoMono = transfCred = ajusteCompet = estornoCred = false;
                        tribRegular = hasIndBemMovelUsado = false;
                        cst = classTrib = prodANP = indBemMovelUsado = null;
                        pUf = pMun = pCbs = null;
                        vIbsUf = vIbsMunicipal = vIbs = vCbs = null;
                        vCredPresIbs = ajusteIbs = ajusteCbs = estornoIbs = estornoCbs = null;
                        cCredPres = null;
                        declaredAmounts = new HashMap<>();
                        dfeReferenciado = null;
                        emDFeReferenciado = false;
                        esfera = null;
                        emGIbsCbs = emProd = emCbsCredit = emEstornoCred = emAjusteCompet = false;
                        emImposto = false;
                        emDet = true;
                    }
                    case "prod" -> {
                        if (emDet && isPath(path, "prod", "det", "infNFe")) emProd = true;
                    }
                    case "imposto" -> {
                        if (emDet && isPath(path, "imposto", "det", "infNFe")) emImposto = true;
                    }
                    case "IBSCBS" -> {
                        if (emImposto && isDirectChild(path, "IBSCBS", "imposto")) {
                            emIbsCbs = true;
                            temGrupo = true;
                        }
                    }
                    // O grupo interno só conta dentro do invólucro do item corrente.
                    case "gIBSCBS" -> {
                        if (emIbsCbs && isDirectChild(path, "gIBSCBS", "IBSCBS")) {
                            temGrupoInterno = true;
                            emGIbsCbs = true;
                        }
                    }
                    case "gIBSCBSMono" -> {
                        if (emIbsCbs && isDirectChild(path, "gIBSCBSMono", "IBSCBS")) {
                            grupoMono = true;
                        }
                    }
                    case "gTransfCred" -> {
                        if (emIbsCbs && isDirectChild(path, "gTransfCred", "IBSCBS")) {
                            transfCred = true;
                        }
                    }
                    case "gAjusteCompet" -> {
                        if (emIbsCbs && isDirectChild(path, "gAjusteCompet", "IBSCBS")) {
                            ajusteCompet = true;
                            emAjusteCompet = true;
                        }
                    }
                    // Filhos diretos do invólucro (2º choice de TTribNFe), não de gIBSCBS.
                    case "gCredPresOper" -> {
                        if (emIbsCbs && isDirectChild(path, "gCredPresOper", "IBSCBS")) {
                            credPresOper = true;
                            emCredPresOper = true;
                        }
                    }
                    case "gIBSCredPres" -> {
                        if (emCredPresOper
                                && isDirectChild(path, "gIBSCredPres", "gCredPresOper")) {
                            emIbsCredit = true;
                        }
                    }
                    case "gCBSCredPres" -> {
                        if (emCredPresOper
                                && isDirectChild(path, "gCBSCredPres", "gCredPresOper")) {
                            emCbsCredit = true;
                        }
                    }
                    case "gEstornoCred" -> {
                        if (emIbsCbs && isDirectChild(path, "gEstornoCred", "IBSCBS")) {
                            estornoCred = true;
                            emEstornoCred = true;
                        }
                    }
                    case "gCredPresIBSZFM" -> {
                        if (emIbsCbs && isDirectChild(path, "gCredPresIBSZFM", "IBSCBS")) {
                            credPresIbsZfm = true;
                        }
                    }
                    case "tpCredPresIBSZFM" -> {
                        if (emProd && isDirectChild(path, "tpCredPresIBSZFM", "prod")) {
                            tpCredPresIbsZfm = true;
                        }
                    }
                    case "indBemMovelUsado" -> {
                        if (emProd && isDirectChild(path, "indBemMovelUsado", "prod")) {
                            hasIndBemMovelUsado = true;
                            indBemMovelUsado = texto(r, path);
                        }
                    }
                    // Filho de gIBSCBS/TCIBS (não do invólucro) — precisa do escopo emGIbsCbs.
                    case "gTribCompraGov" -> {
                        if (emGIbsCbs && isDirectChild(path, "gTribCompraGov", "gIBSCBS")) {
                            tribCompraGov = true;
                        }
                    }
                    case "gTribRegular" -> {
                        if (emGIbsCbs && isDirectChild(path, "gTribRegular", "gIBSCBS")) {
                            tribRegular = true;
                        }
                    }
                    case "cProdANP" -> { if (prodANP == null) prodANP = texto(r, path); }
                    case "DFeReferenciado" -> emDFeReferenciado = true;
                    case "chaveAcesso" -> {
                        // NT v1.40 (VC02-14): a partir de 01/09/2026 é aqui, por item, que a
                        // devolução referencia a nota original — não mais em NFref (D-038).
                        // Mesma decodificação de AAMM que refNFe já usa.
                        if (emDFeReferenciado && dfeReferenciado == null) {
                            dfeReferenciado = new ReferencedNote("DFeReferenciado",
                                    AccessKeyMonth.ofAccessKey(texto(r, path)));
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
                    case "CST" -> { if (emIbsCbs && cst == null) cst = texto(r, path); }
                    case "cClassTrib" -> {
                        if (emIbsCbs && classTrib == null) classTrib = texto(r, path);
                    }
                    case "cCredPres" -> {
                        if (emCredPresOper && cCredPres == null) cCredPres = texto(r, path);
                    }
                    case "pRedAliq" -> {
                        BigDecimal v = decimal(texto(r, path));
                        if (esfera == Esfera.UF) pUf = v;
                        else if (esfera == Esfera.MUN) pMun = v;
                        else if (esfera == Esfera.CBS) pCbs = v;
                    }
                    case "vIBSUF" -> {
                        if (esfera == Esfera.UF) vIbsUf = decimal(texto(r, path));
                    }
                    case "vIBSMun" -> {
                        if (esfera == Esfera.MUN) vIbsMunicipal = decimal(texto(r, path));
                    }
                    case "vIBS" -> {
                        boolean directAdjustmentValue = isDirectChild(
                                path, "vIBS", "gAjusteCompet");
                        BigDecimal value = decimal(texto(r, path));
                        if (emAjusteCompet && directAdjustmentValue) {
                            ajusteIbs = value;
                        } else if (emGIbsCbs) {
                            vIbs = value;
                        }
                    }
                    case "vBC" -> {
                        if (emGIbsCbs) declaredAmounts.put("vBC", decimal(texto(r, path)));
                    }
                    case "vDif" -> {
                        BigDecimal value = decimal(texto(r, path));
                        if (esfera == Esfera.UF) declaredAmounts.put("vDifIBSUF", value);
                        else if (esfera == Esfera.MUN) declaredAmounts.put("vDifIBSMun", value);
                        else if (esfera == Esfera.CBS) declaredAmounts.put("vDifCBS", value);
                    }
                    case "vDevTrib" -> {
                        BigDecimal value = decimal(texto(r, path));
                        if (esfera == Esfera.UF) declaredAmounts.put("vDevIBSUF", value);
                        else if (esfera == Esfera.MUN) declaredAmounts.put("vDevIBSMun", value);
                        else if (esfera == Esfera.CBS) declaredAmounts.put("vDevCBS", value);
                    }
                    case "vCBS" -> {
                        boolean directAdjustmentValue = isDirectChild(
                                path, "vCBS", "gAjusteCompet");
                        BigDecimal value = decimal(texto(r, path));
                        if (emAjusteCompet && directAdjustmentValue) {
                            ajusteCbs = value;
                        } else if (esfera == Esfera.CBS) {
                            vCbs = value;
                        }
                    }
                    // gEstornoCred (TEstornoCred, DFeTiposBasicos_v1.00.xsd:1510-1519) nomeia os
                    // campos "vIBSEstCred"/"vCBSEstCred" — não "vIBS"/"vCBS" — mesmo por item.
                    case "vIBSEstCred" -> {
                        if (emEstornoCred
                                && isDirectChild(path, "vIBSEstCred", "gEstornoCred")) {
                            estornoIbs = decimal(texto(r, path));
                            if (estornoIbs != null) declaredAmounts.put("vIBSEstCred", estornoIbs);
                        }
                    }
                    case "vCBSEstCred" -> {
                        if (emEstornoCred
                                && isDirectChild(path, "vCBSEstCred", "gEstornoCred")) {
                            estornoCbs = decimal(texto(r, path));
                            if (estornoCbs != null) declaredAmounts.put("vCBSEstCred", estornoCbs);
                        }
                    }
                    case "vCredPres" -> {
                        if (emIbsCredit) {
                            vCredPresIbs = decimal(texto(r, path));
                            declaredAmounts.put("vCredPresIBS", vCredPresIbs);
                        } else if (emCbsCredit) {
                            declaredAmounts.put("vCredPresCBS", decimal(texto(r, path)));
                        }
                    }
                    case "vIBSMono" -> { if (emDet) declaredAmounts.put("vIBSMono", decimal(texto(r, path))); }
                    case "vCBSMono" -> { if (emDet) declaredAmounts.put("vCBSMono", decimal(texto(r, path))); }
                    case "vIBSMonoReten" -> { if (emDet) declaredAmounts.put("vIBSMonoReten", decimal(texto(r, path))); }
                    case "vCBSMonoReten" -> { if (emDet) declaredAmounts.put("vCBSMonoReten", decimal(texto(r, path))); }
                    case "vIBSMonoRet" -> { if (emDet) declaredAmounts.put("vIBSMonoRet", decimal(texto(r, path))); }
                    case "vCBSMonoRet" -> { if (emDet) declaredAmounts.put("vCBSMonoRet", decimal(texto(r, path))); }
                    default -> { /* demais elementos não alimentam nenhuma regra */ }
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if (!isOfficialPath(path)) {
                    path.pop();
                    continue;
                }
                if (hasUnrecognizedNearestDet(path)) {
                    path.pop();
                    continue;
                }
                String nome = r.getLocalName();
                if (Esfera.of(nome) != null) esfera = null;
                if ("IBSCBS".equals(nome) && isDirectChild(path, "IBSCBS", "imposto")) {
                    emIbsCbs = false;
                }
                if ("gIBSCBS".equals(nome) && isDirectChild(path, "gIBSCBS", "IBSCBS")) {
                    emGIbsCbs = false;
                }
                if ("gCredPresOper".equals(nome)
                        && isDirectChild(path, "gCredPresOper", "IBSCBS")) {
                    emCredPresOper = false;
                }
                if ("gIBSCredPres".equals(nome)
                        && isDirectChild(path, "gIBSCredPres", "gCredPresOper")) {
                    emIbsCredit = false;
                }
                if ("gCBSCredPres".equals(nome)
                        && isDirectChild(path, "gCBSCredPres", "gCredPresOper")) {
                    emCbsCredit = false;
                }
                if ("gEstornoCred".equals(nome)
                        && isDirectChild(path, "gEstornoCred", "IBSCBS")) {
                    emEstornoCred = false;
                }
                if ("gAjusteCompet".equals(nome)
                        && isDirectChild(path, "gAjusteCompet", "IBSCBS")) {
                    emAjusteCompet = false;
                }
                if ("prod".equals(nome) && isPath(path, "prod", "det", "infNFe")) {
                    emProd = false;
                }
                if ("imposto".equals(nome)
                        && isPath(path, "imposto", "det", "infNFe")) {
                    emImposto = false;
                }
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
                if ("det".equals(nome) && isPath(path, "det", "infNFe")) {
                    // O item entra mesmo com nItem ilegível: descartá-lo o faria sumir do
                    // relatório inteiro — nem conforme, nem rejeitado, nem não avaliado.
                    itens.add(new ItemTaxGroup(nItem, temGrupo, temGrupoInterno, cst, classTrib,
                            prodANP, redUf, redMun, redCbs, pUf, pMun, pCbs, dfeReferenciado,
                            difUf, difMun, difCbs, devTribUf, devTribMun, devTribCbs,
                            credPresOper, credPresIbsZfm, tpCredPresIbsZfm, tribCompraGov,
                            grupoMono, transfCred, ajusteCompet, estornoCred, tribRegular,
                            hasIndBemMovelUsado, indBemMovelUsado,
                            ajusteIbs, ajusteCbs, estornoIbs, estornoCbs,
                            vIbsUf, vIbsMunicipal, vIbs, vCbs, cCredPres, vCredPresIbs,
                            declaredAmounts));
                    nItem = null;
                    emDet = false;
                }
                path.pop();
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

    /** Consome o campo textual e fecha seu escopo, pois o laço externo não receberá o END. */
    private String texto(XMLStreamReader r, Deque<ElementScope> path) throws XMLStreamException {
        try {
            return texto(r);
        } finally {
            path.pop();
        }
    }

    private boolean isOfficialPath(Deque<ElementScope> path) {
        return path.stream().allMatch(scope -> NFE_NAMESPACE.equals(scope.namespace()));
    }

    private boolean isDirectChild(Deque<ElementScope> path, String child, String parent) {
        return isPath(path, child, parent);
    }

    private boolean isPath(Deque<ElementScope> path, String... names) {
        var iterator = path.iterator();
        for (String name : names) {
            if (!iterator.hasNext() || !name.equals(iterator.next().name())) return false;
        }
        return true;
    }

    /**
     * Indica que o elemento atual está sob um {@code det} cujo pai não é {@code infNFe}.
     * Considerar o det mais próximo, não apenas a existência de um ancestral válido, impede que
     * um det aninhado herde o estado do item fiscal real.
     */
    private boolean hasUnrecognizedNearestDet(Deque<ElementScope> path) {
        var iterator = path.iterator();
        while (iterator.hasNext()) {
            if (!"det".equals(iterator.next().name())) continue;
            return !iterator.hasNext() || !"infNFe".equals(iterator.next().name());
        }
        return false;
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
