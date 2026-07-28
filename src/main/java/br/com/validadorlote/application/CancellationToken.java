package br.com.validadorlote.application;

import java.util.concurrent.atomic.AtomicBoolean;

/** Sinal cooperativo de cancelamento de um lote em execução. */
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    public void cancel() { cancelled.set(true); }
    public boolean isCancelled() { return cancelled.get(); }
}
