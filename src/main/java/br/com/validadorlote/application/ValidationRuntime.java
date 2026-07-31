package br.com.validadorlote.application;

import java.util.Objects;

/** Conjunto imutável de caso de uso e bases capturado por uma validação. */
public record ValidationRuntime(ValidateBatchUseCase useCase, RuntimeBases bases) {

    public ValidationRuntime {
        Objects.requireNonNull(useCase, "useCase");
        Objects.requireNonNull(bases, "bases");
    }
}
