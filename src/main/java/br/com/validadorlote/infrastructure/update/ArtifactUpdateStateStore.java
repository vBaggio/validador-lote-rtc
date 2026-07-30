package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.infrastructure.xml.ArtifactId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;
import java.util.Properties;

/** Estado operacional separado dos manifests imutáveis de cada versão instalada. */
public class ArtifactUpdateStateStore {

    private final Path file;

    public ArtifactUpdateStateStore(Path dataDirectory) {
        file = dataDirectory.resolve("artifacts").resolve("update-state.properties");
    }

    public static ArtifactUpdateStateStore forCurrentUser() {
        return new ArtifactUpdateStateStore(Path.of(System.getProperty("user.home"),
                ".validador-lote-rtc"));
    }

    public synchronized State read(ArtifactId artifact, String expectedChannelId) {
        Objects.requireNonNull(artifact);
        Objects.requireNonNull(expectedChannelId);
        State state = read(load(), artifact);
        return state != null && expectedChannelId.equals(state.channelId()) ? state : null;
    }

    public synchronized void write(String channelId, ArtifactUpdateEvent event) {
        Objects.requireNonNull(channelId);
        Objects.requireNonNull(event);
        if (channelId.isBlank()) {
            throw new IllegalArgumentException("Canal de atualização inválido");
        }
        try {
            Files.createDirectories(file.getParent());
            Properties properties = load();
            String prefix = event.artifact().name() + ".";
            State previous = read(properties, event.artifact());
            if (previous != null && !channelId.equals(previous.channelId())) {
                previous = null;
            }

            properties.setProperty(prefix + "channelId", channelId);
            properties.setProperty(prefix + "lastAttemptAt", event.at().toString());
            properties.setProperty(prefix + "result", event.status().name());
            setOrRemove(properties, prefix + "detail", event.detail());
            setOrRemove(properties, prefix + "failureKind",
                    event.failureKind() == null ? null : event.failureKind().name());

            Instant successfulCheck = switch (event.status()) {
                case UP_TO_DATE, UPDATE_AVAILABLE -> event.at();
                default -> previous == null ? null : previous.lastSuccessfulCheckAt();
            };
            setOrRemove(properties, prefix + "lastSuccessfulCheckAt",
                    successfulCheck == null ? null : successfulCheck.toString());

            String candidateVersion = switch (event.status()) {
                case APPLIED, UP_TO_DATE -> null;
                default -> event.candidate() == null ? null : event.candidate().version();
            };
            setOrRemove(properties, prefix + "candidateVersion", candidateVersion);

            Path temporary = Files.createTempFile(file.getParent(), "update-state-", ".tmp");
            try {
                try (var output = Files.newOutputStream(temporary,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    properties.store(output, "Estado local de consulta de artefatos");
                }
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw ArtifactUpdateException.localStorage(
                    "Não foi possível registrar a consulta de artefatos", e);
        }
    }

    private State read(Properties properties, ArtifactId artifact) {
        String prefix = artifact.name() + ".";
        String channelId = properties.getProperty(prefix + "channelId");
        String lastAttemptAt = properties.getProperty(prefix + "lastAttemptAt");
        String result = properties.getProperty(prefix + "result");
        if (channelId == null || lastAttemptAt == null || result == null) {
            return null;
        }
        try {
            return new State(channelId, Instant.parse(lastAttemptAt),
                    parseInstant(properties.getProperty(prefix + "lastSuccessfulCheckAt")),
                    ArtifactUpdateEvent.Status.valueOf(result),
                    properties.getProperty(prefix + "detail"),
                    parseFailureKind(properties.getProperty(prefix + "failureKind")),
                    properties.getProperty(prefix + "candidateVersion"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Properties load() {
        Properties properties = new Properties();
        if (!Files.isRegularFile(file)) {
            return properties;
        }
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
            return properties;
        } catch (IOException e) {
            return new Properties();
        }
    }

    private static Instant parseInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static ArtifactFailureKind parseFailureKind(String value) {
        return value == null ? null : ArtifactFailureKind.valueOf(value);
    }

    private static void setOrRemove(Properties properties, String key, String value) {
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
    }

    public record State(
            String channelId,
            Instant lastAttemptAt,
            Instant lastSuccessfulCheckAt,
            ArtifactUpdateEvent.Status result,
            String detail,
            ArtifactFailureKind failureKind,
            String candidateVersion) {

        public Instant lastCheckedAt() {
            return lastAttemptAt;
        }
    }
}
