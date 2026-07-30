package br.com.validadorlote.infrastructure.update;

import java.util.Objects;

/** Falha operacional tipada, com política de retentativa explícita. */
public final class ArtifactUpdateException extends RuntimeException {

    private final ArtifactFailureKind kind;
    private final boolean retryable;

    public ArtifactUpdateException(ArtifactFailureKind kind, boolean retryable, String message) {
        this(kind, retryable, message, null);
    }

    public ArtifactUpdateException(ArtifactFailureKind kind, boolean retryable, String message,
            Throwable cause) {
        super(message, cause);
        this.kind = Objects.requireNonNull(kind);
        this.retryable = retryable;
    }

    public static ArtifactUpdateException connection(String message, Throwable cause) {
        return new ArtifactUpdateException(ArtifactFailureKind.CONNECTION, true, message, cause);
    }

    public static ArtifactUpdateException secureConnection(String message, Throwable cause) {
        return new ArtifactUpdateException(ArtifactFailureKind.SECURE_CONNECTION, false, message, cause);
    }

    public static ArtifactUpdateException temporaryHttp(String message) {
        return new ArtifactUpdateException(ArtifactFailureKind.TEMPORARY_HTTP, true, message);
    }

    public static ArtifactUpdateException rejectedHttp(String message) {
        return new ArtifactUpdateException(ArtifactFailureKind.REJECTED_HTTP, false, message);
    }

    public static ArtifactUpdateException invalidContent(String message) {
        return new ArtifactUpdateException(ArtifactFailureKind.INVALID_CONTENT, false, message);
    }

    public static ArtifactUpdateException invalidContent(String message, Throwable cause) {
        return new ArtifactUpdateException(ArtifactFailureKind.INVALID_CONTENT, false, message, cause);
    }

    public static ArtifactUpdateException localStorage(String message, Throwable cause) {
        return new ArtifactUpdateException(ArtifactFailureKind.LOCAL_STORAGE, false, message, cause);
    }

    public static ArtifactUpdateException interrupted(String message, Throwable cause) {
        return new ArtifactUpdateException(ArtifactFailureKind.INTERRUPTED, false, message, cause);
    }

    public static ArtifactUpdateException unknown(String message, Throwable cause) {
        return new ArtifactUpdateException(ArtifactFailureKind.UNKNOWN, false, message, cause);
    }

    public ArtifactFailureKind kind() {
        return kind;
    }

    public boolean retryable() {
        return retryable;
    }
}
