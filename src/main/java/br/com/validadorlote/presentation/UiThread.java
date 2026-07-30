package br.com.validadorlote.presentation;

/** Executa ações na thread da interface. */
public interface UiThread {

    /** Executa na thread da interface, podendo fazê-lo imediatamente se já estiver nela. */
    void execute(Runnable action);

    /** Enfileira para um ciclo posterior da thread da interface, mesmo quando chamado nela. */
    void executeLater(Runnable action);
}
