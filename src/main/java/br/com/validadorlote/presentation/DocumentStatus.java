package br.com.validadorlote.presentation;

/** Estado de trabalho visível de um documento; pendente não é um veredito fiscal. */
public enum DocumentStatus {
    PENDING, VALIDATING, VALID, ERROR, WARNING, NOT_EVALUATED
}
