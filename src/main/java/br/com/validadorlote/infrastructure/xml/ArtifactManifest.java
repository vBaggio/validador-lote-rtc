package br.com.validadorlote.infrastructure.xml;

import java.time.Instant;
import java.util.Objects;

/** Metadados imutáveis e auditáveis de uma versão instalada de artefato. */
public record ArtifactManifest(ArtifactId artifact, String version, String sourceUrl,
        Instant publishedAt, String sha256, Instant lastCheckedAt, Instant updatedAt, String result) {
    public ArtifactManifest {
        Objects.requireNonNull(artifact); Objects.requireNonNull(version); Objects.requireNonNull(sourceUrl);
        Objects.requireNonNull(publishedAt); Objects.requireNonNull(sha256); Objects.requireNonNull(lastCheckedAt);
        Objects.requireNonNull(updatedAt); Objects.requireNonNull(result);
        if (!version.matches("[A-Za-z0-9._-]{1,100}") || !sha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Manifesto de artefato inválido");
    }
}
