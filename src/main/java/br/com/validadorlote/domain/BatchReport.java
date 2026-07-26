package br.com.validadorlote.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Resultado completo de uma execução de lote. cancelled=true rotula resultados parciais. */
public record BatchReport(Instant startedAt, Duration elapsed, int documentsScanned,
        int documentsWithFindings, int documentsUnreadable, boolean cancelled,
        List<RootCause> rootCauses, String schemasVersion) {

    public BatchReport {
        rootCauses = List.copyOf(rootCauses);
    }
}
