package br.com.validadorlote.domain;

import java.util.List;

/** Conjunto de achados que compartilham a mesma causa provável. */
public record RootCause(RootCauseKey key, String friendlyExplanation, String suggestedAction,
        List<Finding> findings, int affectedDocuments) {

    public RootCause {
        findings = List.copyOf(findings);
    }
}
