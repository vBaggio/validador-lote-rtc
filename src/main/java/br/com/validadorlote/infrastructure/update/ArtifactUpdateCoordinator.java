package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.infrastructure.xml.ArtifactId;

import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Coordena consulta, preparação e ativação sem trocar engines já montados na sessão. */
public final class ArtifactUpdateCoordinator {

    public static final Duration DEFAULT_INTERVAL = Duration.ofHours(24);

    private final List<ArtifactUpdateAction> actions;
    private final Map<ArtifactId, ArtifactUpdateAction> actionsByArtifact;
    private final Duration interval;
    private final Clock clock;
    private final Executor background;
    private final Consumer<ArtifactUpdateEvent> events;
    private final ArtifactUpdateStateStore stateStore;
    private final ArtifactRetryPolicy retryPolicy;
    private final Map<ArtifactId, ArtifactUpdateCandidate> candidates = new ConcurrentHashMap<>();
    private final Set<ArtifactId> blockedCandidates = ConcurrentHashMap.newKeySet();
    private final List<Consumer<ArtifactUpdateEvent>> listeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> completionListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();

    public ArtifactUpdateCoordinator(List<ArtifactUpdateAction> actions, Duration interval, Clock clock,
            Executor background, Consumer<ArtifactUpdateEvent> events,
            ArtifactUpdateStateStore stateStore, ArtifactRetryPolicy retryPolicy) {
        this.actions = List.copyOf(actions);
        this.interval = Objects.requireNonNull(interval);
        this.clock = Objects.requireNonNull(clock);
        this.background = Objects.requireNonNull(background);
        this.events = Objects.requireNonNull(events);
        this.stateStore = Objects.requireNonNull(stateStore);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("Intervalo inválido");
        }

        Map<ArtifactId, ArtifactUpdateAction> indexed = new LinkedHashMap<>();
        for (ArtifactUpdateAction action : this.actions) {
            Objects.requireNonNull(action);
            if (action.channelId() == null || action.channelId().isBlank()) {
                throw new IllegalArgumentException("Canal de atualização inválido");
            }
            if (indexed.put(action.artifact(), action) != null) {
                throw new IllegalArgumentException("Artefato de atualização duplicado");
            }
        }
        this.actionsByArtifact = Map.copyOf(indexed);
    }

    public ArtifactUpdateCoordinator(List<ArtifactUpdateAction> actions, Duration interval, Clock clock,
            Executor background, ArtifactRetryPolicy retryPolicy, Consumer<ArtifactUpdateEvent> events,
            ArtifactUpdateStateStore stateStore) {
        this(actions, interval, clock, background, events, stateStore, retryPolicy);
    }

    public ArtifactUpdateCoordinator(List<ArtifactUpdateAction> actions, Duration interval, Clock clock,
            Executor background, Consumer<ArtifactUpdateEvent> events,
            ArtifactUpdateStateStore stateStore) {
        this(actions, interval, clock, background, events, stateStore,
                ArtifactRetryPolicy.production());
    }

    /** Só agenda: a chamada é segura imediatamente após a janela Swing tornar-se visível. */
    public void checkAfterBoot() {
        schedule(() -> runChecks(false));
    }

    /** Força uma consulta completa; falso indica que outra operação já está em curso. */
    public boolean checkNow() {
        return schedule(() -> runChecks(true));
    }

    /** Aplica cada candidata confirmada sem impedir que outra fonte prossiga após uma falha. */
    public boolean applyAvailable() {
        if (candidates.keySet().stream().noneMatch(this::canApply)) {
            return false;
        }
        return schedule(this::runApplications);
    }

    public boolean isRunning() {
        return running.get();
    }

    public void addListener(Consumer<ArtifactUpdateEvent> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void addCompletionListener(Runnable listener) {
        completionListeners.add(Objects.requireNonNull(listener));
    }

    public ArtifactUpdateStateStore.State state(ArtifactId artifact) {
        ArtifactUpdateAction action = actionsByArtifact.get(artifact);
        return action == null ? null : stateStore.read(artifact, action.channelId());
    }

    private boolean schedule(Runnable operation) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        try {
            background.execute(() -> {
                try {
                    operation.run();
                } finally {
                    running.set(false);
                    notifyCompletionListeners();
                }
            });
            return true;
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
    }

    private void runChecks(boolean force) {
        for (ArtifactUpdateAction action : actions) {
            Instant now = clock.instant();
            if (!force && !isDue(action, now)) {
                continue;
            }
            publish(new ArtifactUpdateEvent(action.artifact(),
                    ArtifactUpdateEvent.Status.CHECKING, now, null, null, null));
            try {
                ArtifactCheckResult result = retryPolicy.execute(action::check);
                publishCheckResult(action, result);
            } catch (RuntimeException e) {
                ArtifactUpdateException failure = classify(e);
                blockedCandidates.add(action.artifact());
                publishTerminal(action, new ArtifactUpdateEvent(action.artifact(),
                        ArtifactUpdateEvent.Status.FAILED, clock.instant(),
                        candidates.get(action.artifact()), failure.kind(), failure.getMessage()));
                if (failure.kind() == ArtifactFailureKind.INTERRUPTED) {
                    return;
                }
            }
        }
    }

    private void publishCheckResult(ArtifactUpdateAction action, ArtifactCheckResult result) {
        if (result == null || result.status() == null) {
            throw ArtifactUpdateException.invalidContent(
                    "A fonte retornou um resultado de consulta inválido");
        }
        if (result.status() == ArtifactCheckResult.Status.UP_TO_DATE) {
            candidates.remove(action.artifact());
            blockedCandidates.remove(action.artifact());
            publishTerminal(action, new ArtifactUpdateEvent(action.artifact(),
                    ArtifactUpdateEvent.Status.UP_TO_DATE, clock.instant(), null, null,
                    result.detail()));
            return;
        }

        ArtifactUpdateCandidate candidate = result.candidate();
        if (candidate == null || candidate.artifact() != action.artifact()) {
            throw ArtifactUpdateException.invalidContent(
                    "A fonte retornou uma candidata de atualização inválida");
        }
        candidates.put(action.artifact(), candidate);
        blockedCandidates.remove(action.artifact());
        ArtifactUpdateEvent delivered = publishTerminal(action, new ArtifactUpdateEvent(
                action.artifact(), ArtifactUpdateEvent.Status.UPDATE_AVAILABLE, clock.instant(),
                candidate, null, result.detail()));
        if (delivered.status() != ArtifactUpdateEvent.Status.UPDATE_AVAILABLE) {
            blockedCandidates.add(action.artifact());
        }
    }

    private void runApplications() {
        for (ArtifactUpdateAction action : actions) {
            ArtifactUpdateCandidate candidate = candidates.get(action.artifact());
            if (candidate == null || !canApply(action.artifact())) {
                continue;
            }
            try {
                publish(new ArtifactUpdateEvent(action.artifact(),
                        ArtifactUpdateEvent.Status.APPLYING, clock.instant(), candidate, null,
                        candidate.detail()));
                action.apply(candidate);
                ArtifactUpdateEvent delivered = publishTerminal(action, new ArtifactUpdateEvent(
                        action.artifact(), ArtifactUpdateEvent.Status.APPLIED, clock.instant(),
                        candidate, null, candidate.detail()));
                if (delivered.status() == ArtifactUpdateEvent.Status.APPLIED) {
                    candidates.remove(action.artifact(), candidate);
                    blockedCandidates.remove(action.artifact());
                } else {
                    blockedCandidates.add(action.artifact());
                }
            } catch (RuntimeException e) {
                ArtifactUpdateException failure = classify(e);
                blockedCandidates.add(action.artifact());
                publishTerminal(action, new ArtifactUpdateEvent(action.artifact(),
                        ArtifactUpdateEvent.Status.FAILED, clock.instant(), candidate,
                        failure.kind(), failure.getMessage()));
                if (failure.kind() == ArtifactFailureKind.INTERRUPTED) {
                    return;
                }
            }
        }
    }

    private boolean canApply(ArtifactId artifact) {
        return candidates.containsKey(artifact) && !blockedCandidates.contains(artifact);
    }

    private boolean isDue(ArtifactUpdateAction action, Instant now) {
        ArtifactUpdateStateStore.State saved =
                stateStore.read(action.artifact(), action.channelId());
        if (saved == null || (saved.result() != ArtifactUpdateEvent.Status.UP_TO_DATE
                && saved.result() != ArtifactUpdateEvent.Status.APPLIED)) {
            return true;
        }
        Instant successfulCheck = saved.lastSuccessfulCheckAt();
        return successfulCheck == null || !now.isBefore(successfulCheck.plus(interval));
    }

    private ArtifactUpdateEvent publishTerminal(ArtifactUpdateAction action,
            ArtifactUpdateEvent event) {
        ArtifactUpdateEvent delivered = event;
        try {
            stateStore.write(action.channelId(), event);
        } catch (RuntimeException e) {
            ArtifactUpdateException failure = classifyStorage(e);
            delivered = new ArtifactUpdateEvent(action.artifact(),
                    ArtifactUpdateEvent.Status.FAILED, clock.instant(), event.candidate(),
                    failure.kind(), failure.getMessage());
            if (event.status() == ArtifactUpdateEvent.Status.APPLIED) {
                publish(event);
            }
            try {
                stateStore.write(action.channelId(), delivered);
            } catch (RuntimeException ignored) {
                // The visible failure below remains the fallback when persistence stays unavailable.
            }
        }
        publish(delivered);
        return delivered;
    }

    private void publish(ArtifactUpdateEvent event) {
        RuntimeException failure = null;
        try {
            events.accept(event);
        } catch (RuntimeException e) {
            failure = collect(failure, e);
        }
        for (Consumer<ArtifactUpdateEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                failure = collect(failure, e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void notifyCompletionListeners() {
        RuntimeException failure = null;
        for (Runnable listener : completionListeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                failure = collect(failure, e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException collect(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return next;
        }
        if (current != next) {
            current.addSuppressed(next);
        }
        return current;
    }

    private static ArtifactUpdateException classify(RuntimeException failure) {
        if (failure instanceof ArtifactUpdateException typed) {
            return typed;
        }
        if (failure instanceof UncheckedIOException) {
            return ArtifactUpdateException.localStorage(
                    "Não foi possível acessar o armazenamento local da base", failure);
        }
        if (Thread.currentThread().isInterrupted()) {
            return ArtifactUpdateException.interrupted(
                    "Atualização de bases interrompida", failure);
        }
        return ArtifactUpdateException.unknown(
                "Não foi possível processar a atualização da base", failure);
    }

    private static ArtifactUpdateException classifyStorage(RuntimeException failure) {
        if (failure instanceof ArtifactUpdateException typed
                && typed.kind() == ArtifactFailureKind.LOCAL_STORAGE) {
            return typed;
        }
        return ArtifactUpdateException.localStorage(
                "Não foi possível registrar o estado da atualização", failure);
    }
}
