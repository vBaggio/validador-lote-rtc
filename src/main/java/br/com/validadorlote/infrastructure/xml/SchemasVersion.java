package br.com.validadorlote.infrastructure.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** Lê a proveniência da base de schemas embarcada (motor, base, data de extração). */
public final class SchemasVersion {

    private static final String RESOURCE = "/schemas/schemas-version.properties";

    private SchemasVersion() {}

    public static String read() {
        Properties properties = new Properties();
        try (InputStream in = SchemasVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException("Proveniência ausente: " + RESOURCE));
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(new IOException("Proveniência ilegível: " + RESOURCE, e));
        }
        return "schemas " + properties.getProperty("profile")
                + " (publicado em " + properties.getProperty("publishedAt") + ")";
    }
}
