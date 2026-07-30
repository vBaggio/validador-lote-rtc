package br.com.validadorlote.infrastructure.update;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Retenta somente falhas explicitamente transitórias, com espera injetável para testes. */
public final class ArtifactRetryPolicy {

    private final int maxAttempts;
    private final Duration delay;
    private final Consumer<Duration> sleeper;

    public ArtifactRetryPolicy(int maxAttempts, Duration delay, Consumer<Duration> sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Quantidade de tentativas inválida");
        }
        this.delay = Objects.requireNonNull(delay);
        if (delay.isNegative()) {
            throw new IllegalArgumentException("Atraso de retentativa inválido");
        }
        this.maxAttempts = maxAttempts;
        this.sleeper = Objects.requireNonNull(sleeper);
    }

    public static ArtifactRetryPolicy production() {
        return new ArtifactRetryPolicy(2, Duration.ofMillis(300), ArtifactRetryPolicy::sleep);
    }

    public <T> T execute(Supplier<T> operation) {
        Objects.requireNonNull(operation);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ensureNotInterrupted();
            try {
                return operation.get();
            } catch (ArtifactUpdateException failure) {
                if (failure.kind() == ArtifactFailureKind.INTERRUPTED) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
                if (!failure.retryable() || attempt == maxAttempts) {
                    throw failure;
                }
                ensureNotInterrupted();
                sleeper.accept(delay);
                ensureNotInterrupted();
            }
        }
        throw new IllegalStateException("Política de retentativa inconsistente");
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ArtifactUpdateException.interrupted(
                    "Retentativa da atualização interrompida", e);
        }
    }

    private static void ensureNotInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw ArtifactUpdateException.interrupted(
                    "Retentativa da atualização interrompida", null);
        }
    }
}
