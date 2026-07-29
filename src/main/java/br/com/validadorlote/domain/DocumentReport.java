package br.com.validadorlote.domain;

import java.util.List;

/** Resultado de um XML fiscal legível, com metadados para a listagem e seus achados. */
public record DocumentReport(FiscalDocument document, List<Finding> findings) {

    public DocumentReport {
        findings = List.copyOf(findings);
    }
}
