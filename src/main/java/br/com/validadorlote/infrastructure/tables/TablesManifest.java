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

    /** Proveniência da base embarcada, para bootstrap e transparência local. */
    public TablesManifest() {
        try (InputStream in = TablesManifest.class.getResourceAsStream("/tables/manifest.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                        "Manifesto de tabelas ausente — reinstale o aplicativo; para suporte, "
                                + "a base embarcada só muda por manutenção de release revisada");
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

    public LocalDate lastCheckedAt() {
        String value = props.getProperty("tables.lastCheckedAt");
        return value == null ? null : LocalDate.parse(value);
    }

    public String reference() { return props.getProperty("tables.reference"); }

    /** Origem do snapshot embarcado de cCredPres — sem canal de atualização (D-061). */
    public String creditPresumedSource() { return props.getProperty("ccredpres.source", "desconhecida"); }

    public LocalDate creditPresumedExtractedAt() {
        String v = props.getProperty("ccredpres.extractedAt");
        return v == null ? null : LocalDate.parse(v);
    }

    public int creditPresumedCount() {
        return Integer.parseInt(props.getProperty("ccredpres.count", "0"));
    }

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
