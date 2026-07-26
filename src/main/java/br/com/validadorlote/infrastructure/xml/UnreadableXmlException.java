package br.com.validadorlote.infrastructure.xml;

/** Arquivo que não pôde ser lido como NF-e/NFC-e (corrompido, raiz estranha, DOCTYPE). */
public class UnreadableXmlException extends RuntimeException {

    public UnreadableXmlException(String message) {
        super(message);
    }

    public UnreadableXmlException(String message, Throwable cause) {
        super(message, cause);
    }
}
