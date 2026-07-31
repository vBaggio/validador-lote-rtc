package br.com.validadorlote.infrastructure.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Properties;

/** Lê a proveniência da base de schemas embarcada (motor, base, data de extração). */
public final class SchemasVersion {

    private static final String RESOURCE = "/schemas/schemas-version.properties";

    private SchemasVersion() {}

    public static String read() {
        Metadata metadata = metadata();
        return "schemas " + metadata.profile() + " (publicado em " + metadata.publishedAt() + ")";
    }

    /** Metadados da closure embarcada, sem abrir ou expor qualquer XML de usuário. */
    public static Metadata metadata() {
        Properties properties = new Properties();
        try (InputStream in = SchemasVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException("Proveniência ausente: " + RESOURCE));
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(new IOException("Proveniência ilegível: " + RESOURCE, e));
        }
        return new Metadata(properties.getProperty("profile"), properties.getProperty("sourceUrl"),
                properties.getProperty("closureSha256"), LocalDate.parse(properties.getProperty("publishedAt")),
                LocalDate.parse(properties.getProperty("incorporatedAt")), curatedRelease(properties));
    }

    public record Metadata(String profile, String sourceUrl, String closureSha256, LocalDate publishedAt,
            LocalDate incorporatedAt, Optional<CuratedReleaseIdentity> curatedRelease) {}

    private static Optional<CuratedReleaseIdentity> curatedRelease(Properties properties) {
        String channelId = properties.getProperty("curated.channelId");
        String sequence = properties.getProperty("curated.releaseSequence");
        String zipSha256 = properties.getProperty("curated.zipSha256");
        String signedReleaseSha256 = properties.getProperty("curated.signedReleaseSha256");
        if (channelId == null || sequence == null || zipSha256 == null || signedReleaseSha256 == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CuratedReleaseIdentity(channelId, Long.parseLong(sequence), zipSha256,
                    signedReleaseSha256));
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("Identidade curada embarcada inválida", failure);
        }
    }

    public record CuratedReleaseIdentity(String channelId, long releaseSequence, String zipSha256,
            String signedReleaseSha256) {
        public CuratedReleaseIdentity {
            if (channelId == null || !channelId.matches("[A-Za-z0-9._-]{1,100}")
                    || releaseSequence <= 0 || zipSha256 == null
                    || !zipSha256.matches("[0-9a-f]{64}") || signedReleaseSha256 == null
                    || !signedReleaseSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Identidade curada embarcada inválida");
            }
        }
    }
}
