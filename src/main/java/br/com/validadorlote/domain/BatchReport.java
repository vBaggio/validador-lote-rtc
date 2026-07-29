package br.com.validadorlote.domain;

import java.time.Duration;
import java.time.Instant;
import java.nio.file.Path;
import java.util.List;

/** Resultado completo de uma execução de lote. cancelled=true rotula resultados parciais. */
public record BatchReport(Instant startedAt, Duration elapsed, int documentsScanned,
        int documentsWithFindings, int documentsUnreadable, boolean cancelled,
        List<RootCause> rootCauses, String schemasVersion, List<DocumentReport> documents,
        List<Path> invalidFiles) {

    public BatchReport {
        rootCauses = List.copyOf(rootCauses);
        documents = List.copyOf(documents);
        invalidFiles = List.copyOf(invalidFiles);
    }

    /** Compatibilidade temporária para consumidores que só precisam do relatório agregado. */
    public BatchReport(Instant startedAt, Duration elapsed, int documentsScanned,
            int documentsWithFindings, int documentsUnreadable, boolean cancelled,
            List<RootCause> rootCauses, String schemasVersion) {
        this(startedAt, elapsed, documentsScanned, documentsWithFindings, documentsUnreadable,
                cancelled, rootCauses, schemasVersion, List.of(), List.of());
    }
}
