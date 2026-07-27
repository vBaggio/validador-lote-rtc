package br.com.validadorlote.domain;

import java.nio.file.Path;

/** Um problema num documento. rejectionCode/ruleId só existem em achados de regra da NT. */
public record Finding(Path source, String accessKey, Integer itemNumber, FindingKind kind,
        Severity severity, String field, String xsdCode, String officialMessage,
        String friendlyMessage, Integer line, Integer column,
        String rejectionCode, String ruleId) {

    public Finding withSeverity(Severity newSeverity) {
        return new Finding(source, accessKey, itemNumber, kind, newSeverity, field, xsdCode,
                officialMessage, friendlyMessage, line, column, rejectionCode, ruleId);
    }

    /** Rejeição prevista: a mensagem oficial vem da NT e não é reescrita. */
    public static Finding rejection(Path source, String accessKey, Integer item,
            String rejectionCode, String ruleId, String officialMessage, String friendlyMessage) {
        return new Finding(source, accessKey, item, FindingKind.REJECTION_RULE, Severity.REJECTION,
                null, null, officialMessage, friendlyMessage, null, null, rejectionCode, ruleId);
    }

    /** Não foi possível julgar — falta dado na base embarcada, não é defeito do documento. */
    public static Finding notEvaluated(Path source, String accessKey, Integer item, String reason) {
        return new Finding(source, accessKey, item, FindingKind.NOT_EVALUATED, Severity.INFO,
                null, null, reason, null, null, null, null, null);
    }
}
