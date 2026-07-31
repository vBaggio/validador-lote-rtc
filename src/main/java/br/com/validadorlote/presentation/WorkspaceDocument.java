package br.com.validadorlote.presentation;

import br.com.validadorlote.application.RuntimeBases;
import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.Severity;

import java.util.List;
import java.util.Objects;

/** Linha da grade de trabalho, inclusive antes de a validação ser solicitada. */
public record WorkspaceDocument(FiscalDocument document, DocumentStatus status,
        List<Finding> findings, RuntimeBases runtimeBases) {
    public WorkspaceDocument {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(status, "status");
        findings = List.copyOf(findings);
        if (status == DocumentStatus.PENDING || status == DocumentStatus.VALIDATING) {
            if (runtimeBases != null) {
                throw new IllegalArgumentException("Documento incompleto não pode ter runtimeBases");
            }
        } else if (runtimeBases == null) {
            throw new IllegalArgumentException("Documento concluído deve ter runtimeBases");
        }
    }

    public static WorkspaceDocument pending(FiscalDocument document) {
        return new WorkspaceDocument(document, DocumentStatus.PENDING, List.of(), null);
    }

    public WorkspaceDocument withStatus(DocumentStatus newStatus) {
        RuntimeBases bases = newStatus == DocumentStatus.PENDING || newStatus == DocumentStatus.VALIDATING
                ? null : runtimeBases;
        return new WorkspaceDocument(document, newStatus, findings, bases);
    }

    public WorkspaceDocument withResult(DocumentStatus newStatus, List<Finding> newFindings,
            RuntimeBases newRuntimeBases) {
        return new WorkspaceDocument(document, newStatus, newFindings,
                java.util.Objects.requireNonNull(newRuntimeBases, "runtimeBases"));
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
