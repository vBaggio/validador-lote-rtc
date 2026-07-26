package br.com.validadorlote.domain;

import java.nio.file.Path;

/** Um problema num documento: um erro de schema, assinatura ausente ou arquivo ilegível. */
public record Finding(Path source, String accessKey, Integer itemNumber, FindingKind kind,
        Severity severity, String field, String xsdCode, String officialMessage,
        String friendlyMessage, Integer line, Integer column) {

    public Finding withSeverity(Severity newSeverity) {
        return new Finding(source, accessKey, itemNumber, kind, newSeverity, field,
                xsdCode, officialMessage, friendlyMessage, line, column);
    }
}
