package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.infrastructure.xml.ArtifactId;

import java.time.Instant;

/** Candidata íntegra preparada localmente, ainda pendente de confirmação para ativação. */
public record ArtifactUpdateCandidate(
        ArtifactId artifact,
        String version,
        String sourceUrl,
        Instant publishedAt,
        String sha256,
        String detail) {
}
