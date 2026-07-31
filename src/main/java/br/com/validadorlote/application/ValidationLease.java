package br.com.validadorlote.application;

import java.util.Objects;

/** Identidade opaca da validação admitida junto com o runtime que ela deve usar integralmente. */
public final class ValidationLease {

    private final long id;
    private final ValidationRuntime runtime;

    ValidationLease(long id, ValidationRuntime runtime) {
        if (id < 1) {
            throw new IllegalArgumentException("Identificador da lease deve ser positivo");
        }
        this.id = id;
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public ValidationRuntime runtime() {
        return runtime;
    }

    long id() {
        return id;
    }
}
