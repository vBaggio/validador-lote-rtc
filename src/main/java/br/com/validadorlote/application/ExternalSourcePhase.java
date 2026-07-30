package br.com.validadorlote.application;

/** Fase observável de uma base externa ativa na versão atual do aplicativo. */
public enum ExternalSourcePhase {
    NOT_CHECKED,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    APPLYING,
    APPLIED,
    FAILED
}
