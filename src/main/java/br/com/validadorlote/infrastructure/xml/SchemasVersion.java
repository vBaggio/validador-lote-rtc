package br.com.validadorlote.infrastructure.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Properties;

/** Lê a proveniência da base de schemas embarcada (motor, base, data de extração). */
public final class SchemasVersion {

    private static final String RESOURCE = "/schemas/schemas-version.properties";

    private SchemasVersion() {}

    public static String read() {
        Metadata metadata = metadata();
        return "schemas " + metadata.profile() + " (publicado em " + metadata.publishedAt() + ")";
    }

    /** Metadados da closure embarcada, sem abrir ou expor qualquer XML de usuário. */
    public static Metadata metadata() {
        Properties properties = new Properties();
        try (InputStream in = SchemasVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException("Proveniência ausente: " + RESOURCE));
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(new IOException("Proveniência ilegível: " + RESOURCE, e));
        }
        return new Metadata(properties.getProperty("profile"), properties.getProperty("sourceUrl"),
                properties.getProperty("closureSha256"), LocalDate.parse(properties.getProperty("publishedAt")),
                LocalDate.parse(properties.getProperty("incorporatedAt")));
    }

    public record Metadata(String profile, String sourceUrl, String closureSha256, LocalDate publishedAt,
            LocalDate incorporatedAt) {}
}
