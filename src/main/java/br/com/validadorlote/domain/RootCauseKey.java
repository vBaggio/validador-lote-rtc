package br.com.validadorlote.domain;

/** Chave de agrupamento por causa-raiz. Campos null participam da igualdade normalmente. */
public record RootCauseKey(FindingKind kind, String xsdCode, String field) {}
