package br.com.validadorlote.domain;

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
 * @param references notas referenciadas em {@code ide/NFref}; nunca nula, vazia quando não há.
 */
public record FiscalDocument(Path source, String accessKey, String emitterCnpj,
        String documentNumber, LocalDate issueDate, String model, String rootElement,
        String crt, String finNFe, String tpNFDebito, boolean hasCompraGov,
        List<ReferencedNote> references) {

    public FiscalDocument {
        // Cópia imutável e nunca nula: as regras iteram sobre isto sem guard de null.
        references = references == null ? List.of() : List.copyOf(references);
    }
}
