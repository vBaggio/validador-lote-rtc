package br.com.validadorlote.application;

import java.util.Objects;

/** Identidade legível e imutável das bases que compõem um runtime de validação. */
public record RuntimeBases(String schemaVersion, String schemaProvenance,
        String tableVersion, String tableProvenance, long generation) {

    public RuntimeBases {
        schemaVersion = requiredText(schemaVersion, "schemaVersion");
        schemaProvenance = requiredText(schemaProvenance, "schemaProvenance");
        tableVersion = requiredText(tableVersion, "tableVersion");
        tableProvenance = requiredText(tableProvenance, "tableProvenance");
        if (generation < 0) {
            throw new IllegalArgumentException("generation não pode ser negativa");
        }
    }

    /** Identidade provisória para o caminho de compatibilidade antes do composition root migrar. */
    public static RuntimeBases legacy() {
        return new RuntimeBases("não identificada", "runtime legado", "não identificada",
                "runtime legado", 0);
    }

    private static String requiredText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " não pode ser vazio");
        }
        return value;
    }
}
