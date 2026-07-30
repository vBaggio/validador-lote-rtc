package br.com.validadorlote.infrastructure.update;

/** Resultado de uma consulta que pode ser exibido sem revelar payloads ou dados de lote. */
public record ArtifactUpdateResult(boolean updated, String detail) {

    public static ArtifactUpdateResult updated(String detail) {
        return new ArtifactUpdateResult(true, detail);
    }

    public static ArtifactUpdateResult unchanged(String detail) {
        return new ArtifactUpdateResult(false, detail);
    }
}
