package br.com.validadorlote.infrastructure.tables;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/** Proveniência das tabelas embarcadas: de onde vieram e quando. */
public final class TablesManifest {

    private final Properties props = new Properties();

    TablesManifest() {
        try (InputStream in = TablesManifest.class.getResourceAsStream("/tables/manifest.properties")) {
            if (in == null) {
                throw new IllegalStateException("manifest.properties ausente — rode ./gradlew updateFiscalTables");
            }
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String source() { return props.getProperty("tables.source", "desconhecida"); }

    public LocalDate extractedAt() {
        String v = props.getProperty("tables.extractedAt");
        return v == null ? null : LocalDate.parse(v);
    }

    public String reference() { return props.getProperty("tables.reference"); }

    public String referenceVersion() { return props.getProperty("tables.referenceVersion"); }

    public LocalDate referencePublishedAt() {
        return LocalDate.parse(props.getProperty("tables.referencePublishedAt"));
    }

    public String describe() {
        return reference() + " v" + referenceVersion()
                + ", publicada em "
                + referencePublishedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + "; tabelas de " + source() + ", extraídas em "
                + props.getProperty("tables.extractedAt");
    }
}
