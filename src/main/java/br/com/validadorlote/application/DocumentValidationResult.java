package br.com.validadorlote.application;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.domain.Finding;

import java.util.List;

/** Resultado da validação de um XML; documento nulo significa que ele se tornou ilegível. */
public record DocumentValidationResult(FiscalDocument document, List<Finding> findings) {
    public DocumentValidationResult {
        findings = List.copyOf(findings);
    }
}
