package br.com.validadorlote.infrastructure.xml;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/** Contrato externo assinado do canal de schemas curados. */
public record CuratedSchemaChannelManifest(
        int format,
        String keyId,
        SignedRelease signed,
        String signature) {

    public record SignedRelease(
            ArtifactId artifact,
            long releaseSequence,
            String version,
            Instant publishedAt,
            String minimumAppVersion,
            URI zipUrl,
            String zipSha256,
            List<SourceProvenance> sourceProvenance) {

        public SignedRelease {
            if (sourceProvenance != null) sourceProvenance = List.copyOf(sourceProvenance);
        }
    }

    public record SourceProvenance(String name, URI url, String revision) {}
}
