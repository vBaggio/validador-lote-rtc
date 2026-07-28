package br.com.validadorlote.domain;

/** Natureza de um achado. */
public enum FindingKind {
    SCHEMA,
    SIGNATURE_MISSING,
    UNREADABLE,
    /** Regra de negócio da NT: a SEFAZ rejeitaria este documento. */
    REJECTION_RULE,
    /** Faltou dado para julgar. Nunca somado aos conformes nem aos rejeitados. */
    NOT_EVALUATED
}
