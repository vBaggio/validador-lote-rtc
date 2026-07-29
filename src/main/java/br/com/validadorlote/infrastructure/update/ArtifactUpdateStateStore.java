package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.infrastructure.xml.ArtifactId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Properties;

/** Estado operacional separado dos manifests imutáveis de cada versão instalada. */
public final class ArtifactUpdateStateStore {

    private final Path file;

    public ArtifactUpdateStateStore(Path dataDirectory) {
        file = dataDirectory.resolve("artifacts").resolve("update-state.properties");
    }

    public static ArtifactUpdateStateStore forCurrentUser() {
        return new ArtifactUpdateStateStore(Path.of(System.getProperty("user.home"),
                ".validador-lote-rtc"));
    }

    public State read(ArtifactId artifact) {
        Properties properties = load();
        String prefix = artifact.name() + ".";
        String at = properties.getProperty(prefix + "lastCheckedAt");
        if (at == null) return null;
        try {
            return new State(Instant.parse(at), ArtifactUpdateEvent.Status.valueOf(
                    properties.getProperty(prefix + "result")), properties.getProperty(prefix + "detail"));
        } catch (RuntimeException e) {
            return null; // estado operacional inválido nunca impede a checagem nem toca a base ativa
        }
    }

    public void write(ArtifactUpdateEvent event) {
        try {
            Files.createDirectories(file.getParent());
            Properties properties = load();
            String prefix = event.artifact().name() + ".";
            properties.setProperty(prefix + "lastCheckedAt", event.at().toString());
            properties.setProperty(prefix + "result", event.status().name());
            if (event.detail() == null) properties.remove(prefix + "detail");
            else properties.setProperty(prefix + "detail", event.detail());
            Path temporary = Files.createTempFile(file.getParent(), "update-state-", ".tmp");
            try (var output = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(output, "Estado local de consulta de artefatos");
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível registrar a consulta de artefatos", e);
        }
    }

    private Properties load() {
        Properties properties = new Properties();
        if (!Files.isRegularFile(file)) return properties;
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
            return properties;
        } catch (IOException e) {
            return new Properties();
        }
    }

    public record State(Instant lastCheckedAt, ArtifactUpdateEvent.Status result, String detail) {}
}
