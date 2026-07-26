package br.com.validadorlote.infrastructure.fs;

/** Falha ao acessar a pasta de entrada. Mensagem em pt-BR, apta para a UI. */
public class ScanException extends RuntimeException {
    public ScanException(String message) { super(message); }
    public ScanException(String message, Throwable cause) { super(message, cause); }
}
