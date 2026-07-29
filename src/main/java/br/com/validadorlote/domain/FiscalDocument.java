package br.com.validadorlote.domain;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Metadados de um documento fiscal lido do disco. Campos null quando não extraíveis.
 *
 * @param finNFe finalidade da emissão (1 normal, 2 complementar, 3 ajuste, 4 devolução/retorno,
 *               5 nota de crédito, 6 nota de débito).
 * @param tpNFDebito tipo de nota de débito, opcional no leiaute.
 * @param hasCompraGov presença do grupo de compras governamentais. É de <b>documento</b>, não de
 *                     item: o XSD o declara em {@code infNFe/ide/gCompraGov}
 *                     ({@code leiauteNFe_v4.00.xsd:499}) e a NT o lista com pai B01 ({@code ide}).
 * @param pRedutorCompraGov {@code gCompraGov/pRedutor} (percentual de redução de alíquota em
 *                     compra governamental), bruto e sem aritmética aplicada. Filho direto de
 *                     {@code gCompraGov}, sequence {@code tpEnteGov, pRedutor, tpOperGov}
 *                     ({@code DFeTiposBasicos_v1.00.xsd:1144-1163}, tipo {@code TCompraGov}) — por
 *                     isso é de <b>documento</b>, como {@code hasCompraGov}, e não de item. Null
 *                     quando {@code gCompraGov} está ausente ou quando o texto não é legível
 *                     (mesmo contrato de "campo ausente vira null" do resto do parser). Insumo das
 *                     rejeições 1032/1007/1028 (bloco 7); a aritmética completa de compra
 *                     governamental fica para depois (D-030).
 * @param hasIbsCbsTot presença do grupo de totais {@code total/IBSCBSTot}. É de <b>documento</b>,
 *                     não de item: confirmado no XSD embarcado
 *                     ({@code leiauteNFe_v4.00.xsd:5613}, filho de {@code total}). Sustenta as
 *                     rejeições 1118/1119 (W34-10/W34-20), que comparam essa presença contra a do
 *                     invólucro {@code IBSCBS} em qualquer item do documento.
 * @param references notas referenciadas em {@code ide/NFref}; nunca nula, vazia quando não há.
 */
public record FiscalDocument(Path source, String accessKey, String emitterCnpj, String emitterName,
        String documentNumber, LocalDate issueDate, String model, String series, String rootElement,
        String crt, String finNFe, String tpNFDebito, boolean hasCompraGov,
        BigDecimal pRedutorCompraGov, boolean hasIbsCbsTot, List<ReferencedNote> references) {

    public FiscalDocument {
        // Cópia imutável e nunca nula: as regras iteram sobre isto sem guard de null.
        references = references == null ? List.of() : List.copyOf(references);
    }

    /** Construtor de compatibilidade para regras que não precisam dos campos de apresentação. */
    public FiscalDocument(Path source, String accessKey, String emitterCnpj, String documentNumber,
            LocalDate issueDate, String model, String rootElement, String crt, String finNFe,
            String tpNFDebito, boolean hasCompraGov, BigDecimal pRedutorCompraGov,
            boolean hasIbsCbsTot, List<ReferencedNote> references) {
        this(source, accessKey, emitterCnpj, null, documentNumber, issueDate, model, null,
                rootElement, crt, finNFe, tpNFDebito, hasCompraGov, pRedutorCompraGov,
                hasIbsCbsTot, references);
    }
}
