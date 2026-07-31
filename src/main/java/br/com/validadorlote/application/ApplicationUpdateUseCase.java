package br.com.validadorlote.application;

import br.com.validadorlote.domain.ApplicationRelease;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Consulta consultiva da release do aplicativo, isolada das atualizações de bases. */
public final class ApplicationUpdateUseCase {

    private final String installedVersion;
    private final Supplier<Optional<ApplicationRelease>> releaseChecker;
    private final Executor background;
    private final Set<String> notifiedVersions = ConcurrentHashMap.newKeySet();

    public ApplicationUpdateUseCase(String installedVersion,
            Supplier<Optional<ApplicationRelease>> releaseChecker, Executor background) {
        this.installedVersion = installedVersion;
        this.releaseChecker = releaseChecker;
        this.background = background;
    }

    /** Agenda a consulta sem deixar falhas de rede, parsing ou executor alcançarem a interface. */
    public void checkAfterVisible(Consumer<ApplicationRelease> notification) {
        try {
            background.execute(() -> {
                try {
                    releaseChecker.get()
                            .filter(release -> isNewer(release.version(), installedVersion))
                            .filter(release -> notifiedVersions.add(release.version()))
                            .ifPresent(notification);
                } catch (RuntimeException ignored) {
                    // A consulta é estritamente consultiva e nunca degrada o uso offline.
                }
            });
        } catch (RuntimeException ignored) {
            // Um executor já encerrado também não pode atrasar o boot.
        }
    }

    static boolean isNewer(String available, String installed) {
        int[] availableParts = parse(available);
        int[] installedParts = parse(installed);
        if (availableParts == null || installedParts == null) return false;
        for (int index = 0; index < availableParts.length; index++) {
            if (availableParts[index] != installedParts[index]) {
                return availableParts[index] > installedParts[index];
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        if (version == null || !version.matches("(?:v)?[0-9]+\\.[0-9]+\\.[0-9]+")) return null;
        String[] parts = version.startsWith("v") ? version.substring(1).split("\\.")
                : version.split("\\.");
        try {
            return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]) };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
