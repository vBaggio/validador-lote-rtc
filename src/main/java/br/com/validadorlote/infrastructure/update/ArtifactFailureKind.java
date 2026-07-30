package br.com.validadorlote.infrastructure.update;

/** Categoria estável de falha operacional na aquisição ou ativação de uma base. */
public enum ArtifactFailureKind {
    CONNECTION,
    SECURE_CONNECTION,
    TEMPORARY_HTTP,
    REJECTED_HTTP,
    INVALID_CONTENT,
    UNSUPPORTED_SCHEMA_STRUCTURE,
    LOCAL_STORAGE,
    INTERRUPTED,
    UNKNOWN
}
