package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.infrastructure.xml.ArtifactId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final List<Consumer<ArtifactUpdateEvent>> listeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> completionListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();

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
        schedule(false);
    }

    /**
     * Agenda uma nova consulta sem esperar o intervalo normal. Retorna falso se outra consulta já
     * está em curso; assim uma ação repetida da interface não duplica downloads.
     */
    public boolean checkNow() {
        return schedule(true);
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Adiciona um observador operacional; o evento não carrega dados de lote. */
    public void addListener(Consumer<ArtifactUpdateEvent> listener) {
        listeners.add(listener);
    }

    public void addCompletionListener(Runnable listener) {
        completionListeners.add(listener);
    }

    private boolean schedule(boolean force) {
        if (!running.compareAndSet(false, true)) return false;
        background.execute(() -> {
            try {
                runDue(force);
            } finally {
                running.set(false);
                completionListeners.forEach(Runnable::run);
            }
        });
        return true;
    }

    private void runDue(boolean force) {
        for (ArtifactUpdateAction action : actions) {
            Instant now = clock.instant();
            if (!force && !isDue(action.artifact(), now)) continue;
            publish(action.artifact(), ArtifactUpdateEvent.Status.STARTED, now, null);
            try {
                ArtifactUpdateResult result = action.updateIfNew();
                publishAndPersist(action.artifact(), result.updated() ? ArtifactUpdateEvent.Status.UPDATED
                        : ArtifactUpdateEvent.Status.UNCHANGED, clock.instant(), result.detail());
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
        notify(new ArtifactUpdateEvent(artifact, status, at, detail));
    }

    private void publishAndPersist(ArtifactId artifact, ArtifactUpdateEvent.Status status, Instant at,
            String detail) {
        ArtifactUpdateEvent event = new ArtifactUpdateEvent(artifact, status, at, detail);
        state.write(event);
        notify(event);
    }

    private void notify(ArtifactUpdateEvent event) {
        events.accept(event);
        listeners.forEach(listener -> listener.accept(event));
    }
}
