package br.com.validadorlote.domain;

/** Chave de agrupamento por causa-raiz. Campos null participam da igualdade normalmente. */
public record RootCauseKey(FindingKind kind, String xsdCode, String field,
        String rejectionCode, NotEvaluatedCause notEvaluatedCause, String ruleId) {

    public RootCauseKey(FindingKind kind, String xsdCode, String field) {
        this(kind, xsdCode, field, null, null, null);
    }

    public static RootCauseKey from(Finding finding) {
        return switch (finding.kind()) {
            case SCHEMA -> new RootCauseKey(finding.kind(), finding.xsdCode(),
                    finding.field(), null, null, null);
            case REJECTION_RULE -> new RootCauseKey(finding.kind(), null, null,
                    finding.rejectionCode(), null, null);
            case NOT_EVALUATED -> {
                NotEvaluatedCause cause = finding.notEvaluatedCause();
                String specificRule = cause == NotEvaluatedCause.RULE_SPECIFIC
                        ? finding.ruleId() : null;
                yield new RootCauseKey(finding.kind(), null, null, null,
                        cause, specificRule);
            }
            case SIGNATURE_MISSING, UNREADABLE ->
                    new RootCauseKey(finding.kind(), null, null, null, null, null);
        };
    }
}
