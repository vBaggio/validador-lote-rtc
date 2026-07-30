package br.com.validadorlote.infrastructure.xml;

import java.time.Instant;
import java.util.Objects;

/** Metadados imutáveis e auditáveis de uma versão instalada de artefato. */
public record ArtifactManifest(ArtifactId artifact, String version, String sourceUrl,
        Instant publishedAt, String sha256, Instant lastCheckedAt, Instant updatedAt, String result,
        long releaseSequence, String channelId, String provenance, String zipSha256,
        String signedReleaseSha256) {
    public ArtifactManifest {
        Objects.requireNonNull(artifact); Objects.requireNonNull(version); Objects.requireNonNull(sourceUrl);
        Objects.requireNonNull(publishedAt); Objects.requireNonNull(sha256); Objects.requireNonNull(lastCheckedAt);
        Objects.requireNonNull(updatedAt); Objects.requireNonNull(result); Objects.requireNonNull(channelId);
        Objects.requireNonNull(provenance); Objects.requireNonNull(zipSha256);
        Objects.requireNonNull(signedReleaseSha256);
        if (!version.matches("[A-Za-z0-9._-]{1,100}") || !sha256.matches("[0-9a-f]{64}")
                || releaseSequence < 0
                || (releaseSequence > 0 && (!channelId.matches("[A-Za-z0-9._-]{1,100}")
                        || provenance.isBlank()))
                || (!zipSha256.isEmpty() && !zipSha256.matches("[0-9a-f]{64}"))
                || (!signedReleaseSha256.isEmpty()
                        && !signedReleaseSha256.matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("Manifesto de artefato inválido");
    }

    /** Compatibilidade com manifestos curados gravados antes da identidade assinada completa. */
    public ArtifactManifest(ArtifactId artifact, String version, String sourceUrl,
            Instant publishedAt, String sha256, Instant lastCheckedAt, Instant updatedAt,
            String result, long releaseSequence, String channelId, String provenance) {
        this(artifact, version, sourceUrl, publishedAt, sha256, lastCheckedAt, updatedAt, result,
                releaseSequence, channelId, provenance, "", "");
    }

    /** Compatibilidade com os artefatos legados, que não tinham metadados de canal curado. */
    public ArtifactManifest(ArtifactId artifact, String version, String sourceUrl,
            Instant publishedAt, String sha256, Instant lastCheckedAt, Instant updatedAt,
            String result) {
        this(artifact, version, sourceUrl, publishedAt, sha256, lastCheckedAt, updatedAt, result,
                0, "", "", "", "");
    }
}
