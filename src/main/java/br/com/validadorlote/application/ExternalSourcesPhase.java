package br.com.validadorlote.application;

/** Fase agregada do fluxo de consulta e aplicação das bases externas. */
public enum ExternalSourcesPhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATES_AVAILABLE,
    WAITING_FOR_VALIDATION,
    APPLYING,
    RELOADING_RUNTIME,
    UPDATED_AND_IN_USE,
    RESTART_REQUIRED,
    FAILED
}
