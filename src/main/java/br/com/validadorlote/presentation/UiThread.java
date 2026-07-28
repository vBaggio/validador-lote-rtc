package br.com.validadorlote.presentation;

/** Executa ações na thread da interface. */
public interface UiThread {
    void execute(Runnable action);
}
