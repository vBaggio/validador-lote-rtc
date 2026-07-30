package br.com.validadorlote.infrastructure.update;

/** Resultado de uma consulta que pode preparar uma candidata sem ativá-la. */
public record ArtifactCheckResult(Status status, ArtifactUpdateCandidate candidate, String detail) {
    public enum Status { UP_TO_DATE, UPDATE_AVAILABLE }

    public static ArtifactCheckResult upToDate(String detail) {
        return new ArtifactCheckResult(Status.UP_TO_DATE, null, detail);
    }

    public static ArtifactCheckResult available(ArtifactUpdateCandidate candidate, String detail) {
        return new ArtifactCheckResult(Status.UPDATE_AVAILABLE, candidate, detail);
    }
}
