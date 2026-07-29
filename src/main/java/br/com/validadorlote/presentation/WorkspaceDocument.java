package br.com.validadorlote.presentation;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.Severity;

import java.util.List;

/** Linha da grade de trabalho, inclusive antes de a validação ser solicitada. */
public record WorkspaceDocument(FiscalDocument document, DocumentStatus status,
        List<Finding> findings) {
    public WorkspaceDocument {
        findings = List.copyOf(findings);
    }

    public static WorkspaceDocument pending(FiscalDocument document) {
        return new WorkspaceDocument(document, DocumentStatus.PENDING, List.of());
    }

    public WorkspaceDocument withStatus(DocumentStatus newStatus) {
        return new WorkspaceDocument(document, newStatus, findings);
    }

    public WorkspaceDocument withResult(DocumentStatus newStatus, List<Finding> newFindings) {
        return new WorkspaceDocument(document, newStatus, newFindings);
    }

    public static DocumentStatus statusFor(List<Finding> findings) {
        if (findings.stream().anyMatch(finding -> finding.severity() == Severity.REJECTION)) {
            return DocumentStatus.ERROR;
        }
        if (findings.stream().anyMatch(finding -> finding.severity() == Severity.WARNING)) {
            return DocumentStatus.WARNING;
        }
        if (findings.stream().anyMatch(finding -> finding.kind() == FindingKind.NOT_EVALUATED)) {
            return DocumentStatus.NOT_EVALUATED;
        }
        return DocumentStatus.VALID;
    }
}
