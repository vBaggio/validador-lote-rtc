package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.RootCauseKey;
import br.com.validadorlote.domain.RootCauseTexts;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/** Tradução determinística de erros XSD para pt-BR, carregada de resources. */
public final class XsdErrorTranslator implements RootCauseTexts {

    public record Translation(String message, String action) {}

    private static final String RESOURCE = "/messages/xsd-translations.properties";
    private static final String SIGNATURE_KEY = "signature.missing";

    private final Properties table = new Properties();

    public XsdErrorTranslator() {
        InputStream stream = XsdErrorTranslator.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new UncheckedIOException(new IOException("Tabela de traduções ausente: " + RESOURCE));
        }
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            table.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(new IOException("Tabela de traduções ausente: " + RESOURCE, e));
        }
    }

    /** Construtor de injeção usado em testes, para exercitar o parsing sem depender do resource real. */
    XsdErrorTranslator(Properties table) {
        this.table.putAll(table);
    }

    public Optional<Translation> translate(FindingKind kind, String xsdCode, String field) {
        String raw = switch (kind) {
            case SIGNATURE_MISSING -> table.getProperty(SIGNATURE_KEY);
            default -> lookup(xsdCode, field);
        };
        if (raw == null || raw.isBlank()) return Optional.empty();
        int sep = raw.indexOf('|');
        return Optional.of(sep < 0
                ? new Translation(raw.trim(), null)
                : new Translation(raw.substring(0, sep).trim(), raw.substring(sep + 1).trim()));
    }

    private String lookup(String xsdCode, String field) {
        if (xsdCode == null) return null;
        String specific = field == null ? null : table.getProperty(xsdCode + "." + field);
        return specific != null ? specific : table.getProperty(xsdCode);
    }

    @Override
    public Optional<String> explanation(RootCauseKey key) {
        return translate(key.kind(), key.xsdCode(), key.field()).map(Translation::message);
    }

    @Override
    public Optional<String> action(RootCauseKey key) {
        return translate(key.kind(), key.xsdCode(), key.field()).map(Translation::action)
                .filter(a -> !a.isBlank());
    }
}
