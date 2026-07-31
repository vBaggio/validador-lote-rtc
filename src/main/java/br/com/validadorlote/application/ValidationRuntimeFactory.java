package br.com.validadorlote.application;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Emite runtimes com gerações estritamente crescentes para uma sessão do aplicativo. */
public final class ValidationRuntimeFactory {

    private final AtomicLong lastGeneration;

    public ValidationRuntimeFactory() {
        this(0);
    }

    public ValidationRuntimeFactory(long initialGeneration) {
        if (initialGeneration < 0) {
            throw new IllegalArgumentException("initialGeneration não pode ser negativa");
        }
        lastGeneration = new AtomicLong(initialGeneration);
    }

    public ValidationRuntime create(ValidateBatchUseCase useCase, String schemaVersion,
            String schemaProvenance, String tableVersion, String tableProvenance) {
        return new ValidationRuntime(Objects.requireNonNull(useCase, "useCase"),
                nextBases(schemaVersion, schemaProvenance, tableVersion, tableProvenance));
    }

    public RuntimeBases nextBases(String schemaVersion, String schemaProvenance,
            String tableVersion, String tableProvenance) {
        long generation = lastGeneration.updateAndGet(previous -> Math.incrementExact(previous));
        return new RuntimeBases(schemaVersion, schemaProvenance, tableVersion, tableProvenance,
                generation);
    }
}
