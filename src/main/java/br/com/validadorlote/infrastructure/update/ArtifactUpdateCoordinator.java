package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.infrastructure.xml.ArtifactId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Coordena consultas fora da EDT e deixa bases já montadas intactas até o próximo boot. */
public final class ArtifactUpdateCoordinator {

    public static final Duration DEFAULT_INTERVAL = Duration.ofHours(24);
    private final List<ArtifactUpdateAction> actions;
    private final Duration interval;
    private final Clock clock;
    private final Executor background;
    private final Consumer<ArtifactUpdateEvent> events;
    private final ArtifactUpdateStateStore state;

    public ArtifactUpdateCoordinator(List<ArtifactUpdateAction> actions, Duration interval, Clock clock,
            Executor background, Consumer<ArtifactUpdateEvent> events, ArtifactUpdateStateStore state) {
        this.actions = List.copyOf(actions);
        this.interval = interval;
        this.clock = clock;
        this.background = background;
        this.events = events;
        this.state = state;
        if (interval.isNegative() || interval.isZero()) throw new IllegalArgumentException("Intervalo inválido");
    }

    /** Só agenda: a chamada é segura imediatamente após a janela Swing tornar-se visível. */
    public void checkAfterBoot() {
        background.execute(this::runDue);
    }

    /** Público para o futuro botão "verificar agora"; nunca executa aquisição na thread chamadora. */
    public void checkNow() {
        background.execute(this::runDue);
    }

    private void runDue() {
        for (ArtifactUpdateAction action : actions) {
            Instant now = clock.instant();
            if (!isDue(action.artifact(), now)) continue;
            publish(action.artifact(), ArtifactUpdateEvent.Status.STARTED, now, null);
            try {
                boolean updated = action.updateIfNew();
                publishAndPersist(action.artifact(), updated ? ArtifactUpdateEvent.Status.UPDATED
                        : ArtifactUpdateEvent.Status.UNCHANGED, clock.instant(), null);
            } catch (RuntimeException e) {
                publishAndPersist(action.artifact(), ArtifactUpdateEvent.Status.FAILED, clock.instant(),
                        e.getMessage());
            }
        }
    }

    private boolean isDue(ArtifactId artifact, Instant now) {
        ArtifactUpdateStateStore.State saved = state.read(artifact);
        Instant previous = saved == null ? null : saved.lastCheckedAt();
        return previous == null || !now.isBefore(previous.plus(interval));
    }

    private void publish(ArtifactId artifact, ArtifactUpdateEvent.Status status, Instant at,
            String detail) {
        events.accept(new ArtifactUpdateEvent(artifact, status, at, detail));
    }

    private void publishAndPersist(ArtifactId artifact, ArtifactUpdateEvent.Status status, Instant at,
            String detail) {
        ArtifactUpdateEvent event = new ArtifactUpdateEvent(artifact, status, at, detail);
        state.write(event);
        events.accept(event);
    }
}
