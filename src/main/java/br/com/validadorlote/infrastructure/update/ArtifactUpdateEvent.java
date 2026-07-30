package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.infrastructure.xml.ArtifactId;

import java.time.Instant;

/** Evento neutro para uma UI futura ou log local, sem dependência de Swing. */
public record ArtifactUpdateEvent(
        ArtifactId artifact,
        Status status,
        Instant at,
        ArtifactUpdateCandidate candidate,
        ArtifactFailureKind failureKind,
        String detail) {

    public enum Status {
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        APPLYING,
        APPLIED,
        FAILED
    }
}
