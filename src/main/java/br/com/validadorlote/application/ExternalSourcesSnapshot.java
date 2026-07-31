package br.com.validadorlote.application;

import java.util.List;
import java.util.Objects;

/** Visão única e imutável das bases externas para todos os consumidores da aplicação. */
public record ExternalSourcesSnapshot(
        ExternalSourcesPhase phase,
        List<ExternalSourceState> sources,
        int availableCount,
        int failedCount,
        boolean validationActive,
        long revision,
        String runtimeReloadDetail) {

    public ExternalSourcesSnapshot {
        Objects.requireNonNull(phase);
        sources = List.copyOf(sources);
        if (availableCount < 0 || failedCount < 0 || revision < 0) {
            throw new IllegalArgumentException("Contadores do snapshot não podem ser negativos");
        }
    }

    /** Mantém compatibilidade para snapshots que não carregam falha de recarga de runtime. */
    public ExternalSourcesSnapshot(ExternalSourcesPhase phase, List<ExternalSourceState> sources,
            int availableCount, int failedCount, boolean validationActive, long revision) {
        this(phase, sources, availableCount, failedCount, validationActive, revision, null);
    }
}
