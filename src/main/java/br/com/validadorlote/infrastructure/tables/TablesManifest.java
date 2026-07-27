package br.com.validadorlote.infrastructure.tables;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Properties;

/** Proveniência das tabelas embarcadas: de onde vieram e quando. */
public final class TablesManifest {

    private final Properties props = new Properties();

    TablesManifest() {
        try (InputStream in = TablesManifest.class.getResourceAsStream("/tables/manifest.properties")) {
            if (in == null) {
                throw new IllegalStateException("manifest.properties ausente — rode ./gradlew updateFiscalTables");
            }
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String source() { return props.getProperty("tables.source", "desconhecida"); }

    public LocalDate extractedAt() {
        String v = props.getProperty("tables.extractedAt");
        return v == null ? null : LocalDate.parse(v);
    }

    public String describe() {
        return "tabelas de " + source() + ", extraídas em " + props.getProperty("tables.extractedAt");
    }
}
