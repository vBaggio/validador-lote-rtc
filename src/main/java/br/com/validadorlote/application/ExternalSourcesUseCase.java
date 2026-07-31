package br.com.validadorlote.application;

import br.com.validadorlote.infrastructure.tables.FiscalTableArtifactStore;
import br.com.validadorlote.infrastructure.tables.TablesManifest;
import br.com.validadorlote.infrastructure.update.ArtifactFailureKind;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCoordinator;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateEvent;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateStateStore;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import br.com.validadorlote.infrastructure.xml.ArtifactManifest;
import br.com.validadorlote.infrastructure.xml.SchemaArtifactStore;
import br.com.validadorlote.infrastructure.xml.SchemasVersion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Consulta artefatos e publica runtime completo sem alterar engines de validações já admitidas. */
public final class ExternalSourcesUseCase {

    private static final String SVRS = "https://dfe-portal.svrs.rs.gov.br/";

    private final ArtifactUpdateCoordinator coordinator;
    private final SchemaArtifactStore schemas;
    private final FiscalTableArtifactStore tables;
    private final SchemasVersion.Metadata embeddedSchemas = SchemasVersion.metadata();
    private final TablesManifest embeddedTables = new TablesManifest();
    private final Object stateLock = new Object();
    private final Map<ArtifactId, ArtifactUpdateEvent> currentEvents =
            new EnumMap<>(ArtifactId.class);
    private final List<Consumer<ExternalSourcesSnapshot>> observers =
            new CopyOnWriteArrayList<>();
    private final Deque<ExternalSourcesSnapshot> pendingPublications = new ArrayDeque<>();
    private final Supplier<ValidationRuntime> runtimeBuilder;
    private final Executor runtimeExecutor;

    private volatile ExternalSourcesSnapshot currentSnapshot;
    private ValidationLease validationLease;
    private boolean activationReserved;
    private boolean runtimeReloading;
    private boolean activationApplied;
    private boolean updatedAndInUse;
    private boolean publishing;
    private boolean restartRequired;
    private String runtimeReloadDetail;
    private long revision;
    private long nextLeaseId;
    private long nextActivationTicket;
    private long activationTicket;
    private ValidationRuntime publishedRuntime;

    public ExternalSourcesUseCase(ArtifactUpdateCoordinator coordinator,
            SchemaArtifactStore schemas, FiscalTableArtifactStore tables) {
        this(coordinator, schemas, tables, null, null, null);
    }

    /**
     * Conecta o gate de ativação à referência runtime que é publicada depois da montagem integral.
     * A factory pertence ao composition root e é sempre chamada fora do {@code stateLock}.
     */
    public ExternalSourcesUseCase(ArtifactUpdateCoordinator coordinator,
            SchemaArtifactStore schemas, FiscalTableArtifactStore tables,
            ValidationRuntime initialRuntime, Supplier<ValidationRuntime> runtimeBuilder,
            Executor runtimeExecutor) {
        this.coordinator = Objects.requireNonNull(coordinator);
        this.schemas = Objects.requireNonNull(schemas);
        this.tables = Objects.requireNonNull(tables);
        if ((initialRuntime == null) != (runtimeBuilder == null)
                || (initialRuntime == null) != (runtimeExecutor == null)) {
            throw new IllegalArgumentException("Runtime inicial, factory e executor devem ser informados juntos");
        }
        this.publishedRuntime = initialRuntime;
        this.runtimeBuilder = runtimeBuilder;
        this.runtimeExecutor = runtimeExecutor;
        currentSnapshot = createSnapshot();
        coordinator.addListener(this::eventReceived);
        coordinator.addCompletionListener(this::operationCompleted);
    }

    public ExternalSourcesSnapshot snapshot() {
        return currentSnapshot;
    }

    public void observe(Consumer<ExternalSourcesSnapshot> observer) {
        observers.add(Objects.requireNonNull(observer));
    }

    /** Força consulta em background; falso significa que a operação atual continua em curso. */
    public boolean checkNow() {
        return coordinator.checkNow();
    }

    /** Aplica as candidatas confirmadas somente fora de uma validação de lote. */
    public boolean applyAvailable() {
        boolean drain;
        long ticket;
        synchronized (stateLock) {
            if (validationLease != null || activationReserved) {
                return false;
            }
            activationReserved = true;
            activationApplied = false;
            updatedAndInUse = false;
            ticket = ++nextActivationTicket;
            activationTicket = ticket;
            drain = enqueue(updateSnapshot());
        }
        if (drain) {
            drainPublications();
        }

        boolean accepted;
        RuntimeException schedulingFailure = null;
        try {
            accepted = coordinator.applyAvailable(ticket);
        } catch (RuntimeException e) {
            accepted = false;
            schedulingFailure = e;
        }
        synchronized (stateLock) {
            if (!accepted && activationTicket == ticket) {
                activationReserved = false;
                activationTicket = 0;
            }
            drain = enqueue(updateSnapshot());
        }
        if (drain) {
            drainPublications();
        }
        if (schedulingFailure != null) {
            throw schedulingFailure;
        }
        return accepted;
    }

    /**
     * Captura o runtime inteiro no mesmo lock que protege a reserva de ativação.
     *
     * <p>A lease deve ser devolvida a {@link #validationFinished(ValidationLease)} exatamente
     * uma vez, inclusive quando o agendamento do worker falhar.</p>
     */
    public Optional<ValidationLease> tryAcquireValidationLease(ValidationRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        boolean drain;
        ValidationLease acquired;
        synchronized (stateLock) {
            if (activationReserved || validationLease != null) {
                return Optional.empty();
            }
            acquired = new ValidationLease(++nextLeaseId,
                    publishedRuntime == null ? runtime : publishedRuntime);
            validationLease = acquired;
            drain = enqueue(updateSnapshot());
        }
        if (drain) {
            drainPublications();
        }
        return Optional.of(acquired);
    }

    /** Captura o runtime atualmente publicado quando a instância é gerenciada pelo composition root. */
    public Optional<ValidationLease> tryAcquireValidationLease() {
        boolean drain;
        ValidationLease acquired;
        synchronized (stateLock) {
            if (publishedRuntime == null || activationReserved || validationLease != null) {
                return Optional.empty();
            }
            acquired = new ValidationLease(++nextLeaseId, publishedRuntime);
            validationLease = acquired;
            drain = enqueue(updateSnapshot());
        }
        if (drain) {
            drainPublications();
        }
        return Optional.of(acquired);
    }

    /** Libera somente a validação que recebeu a lease correspondente. */
    public void validationFinished(ValidationLease lease) {
        Objects.requireNonNull(lease, "lease");
        boolean drain;
        synchronized (stateLock) {
            if (validationLease != lease) {
                return;
            }
            validationLease = null;
            drain = enqueue(updateSnapshot());
        }
        if (drain) {
            drainPublications();
        }
    }

    private void eventReceived(ArtifactUpdateEvent event) {
        if (event.artifact() != ArtifactId.NFE_SCHEMAS
                && event.artifact() != ArtifactId.FISCAL_TABLES) {
            return;
        }
        boolean drain;
        synchronized (stateLock) {
            if (event.status() == ArtifactUpdateEvent.Status.CHECKING) {
                updatedAndInUse = false;
            }
            if (event.status() == ArtifactUpdateEvent.Status.APPLIED && runtimeBuilder == null) {
                restartRequired = true;
            }
            if (event.status() == ArtifactUpdateEvent.Status.APPLIED && activationReserved) {
                activationApplied = true;
            }
            currentEvents.put(event.artifact(), event);
            drain = enqueue(updateSnapshot());
        }
        if (drain) {
            drainPublications();
        }
    }

    private void operationCompleted(long completionTicket) {
        boolean drain;
        boolean reload = false;
        synchronized (stateLock) {
            if (completionTicket != 0 && completionTicket == activationTicket) {
                if (activationApplied && runtimeBuilder != null && !restartRequired) {
                    runtimeReloading = true;
                    reload = true;
                } else {
                    activationReserved = false;
                    activationTicket = 0;
                }
            }
            drain = enqueue(updateSnapshot());
        }
        if (drain) {
            drainPublications();
        }
        if (reload) {
            scheduleRuntimeReload(completionTicket);
        }
    }

    private void scheduleRuntimeReload(long ticket) {
        try {
            runtimeExecutor.execute(() -> {
                try {
                    runtimeReloaded(ticket, runtimeBuilder.get());
                } catch (RuntimeException failure) {
                    runtimeReloadFailed(ticket);
                }
            });
        } catch (RuntimeException failure) {
            runtimeReloadFailed(ticket);
        }
    }

    private void runtimeReloaded(long ticket, ValidationRuntime candidate) {
        Objects.requireNonNull(candidate, "Runtime candidato");
        boolean drain;
        synchronized (stateLock) {
            if (ticket != activationTicket || !runtimeReloading) {
                return;
            }
            publishedRuntime = candidate;
            runtimeReloading = false;
            activationReserved = false;
            activationTicket = 0;
            updatedAndInUse = true;
            currentEvents.replaceAll((artifact, event) -> event.status()
                    == ArtifactUpdateEvent.Status.APPLIED
                    ? new ArtifactUpdateEvent(artifact, ArtifactUpdateEvent.Status.UP_TO_DATE,
                            event.at(), null, null, event.detail())
                    : event);
            drain = enqueue(updateSnapshot());
        }
        if (drain) {
            drainPublications();
        }
    }

    private void runtimeReloadFailed(long ticket) {
        boolean drain;
        synchronized (stateLock) {
            if (ticket != activationTicket || !runtimeReloading) {
                return;
            }
            runtimeReloading = false;
            activationReserved = false;
            activationTicket = 0;
            restartRequired = true;
            runtimeReloadDetail = "A base foi ativada em disco, mas não pôde ser carregada agora; "
                    + "reinicie o aplicativo para usá-la.";
            drain = enqueue(updateSnapshot());
        }
        if (drain) {
            drainPublications();
        }
    }

    private ExternalSourcesSnapshot updateSnapshot() {
        revision++;
        currentSnapshot = createSnapshot();
        return currentSnapshot;
    }

    private ExternalSourcesSnapshot createSnapshot() {
        List<ExternalSourceState> states = List.of(
                sourceState(ArtifactId.NFE_SCHEMAS, "Schemas NF-e/NFC-e",
                        schemas.activeManifestOrNull(), embeddedSchemas.sourceUrl(),
                        embeddedSchemas.profile(), embeddedSchemas.closureSha256(),
                        at(embeddedSchemas.incorporatedAt()), null),
                sourceState(ArtifactId.FISCAL_TABLES, "Tabela CST/cClassTrib",
                        tables.activeManifestOrNull(), embeddedTables.source(),
                        "IT " + embeddedTables.referenceVersion(), null,
                        at(embeddedTables.extractedAt()), at(embeddedTables.lastCheckedAt())));
        int available = (int) states.stream()
                .filter(source -> source.phase() == ExternalSourcePhase.UPDATE_AVAILABLE)
                .count();
        int failed = (int) states.stream()
                .filter(source -> source.phase() == ExternalSourcePhase.FAILED)
                .count();
        boolean validationActive = validationLease != null;
        return new ExternalSourcesSnapshot(aggregate(states, validationActive,
                coordinator.isRunning(), activationReserved, runtimeReloading, restartRequired,
                updatedAndInUse), states,
                available, failed, validationActive, revision, runtimeReloadDetail);
    }

    private ExternalSourceState sourceState(ArtifactId artifact, String name,
            ArtifactManifest manifest, String fallbackOrigin, String embeddedVersion,
            String embeddedHash, Instant embeddedUpdatedAt, Instant embeddedCheckedAt) {
        OperationalState operation = operationalState(artifact);
        boolean embedded = manifest == null;
        return new ExternalSourceState(artifact, name,
                embedded ? embeddedVersion : manifest.version(),
                embedded,
                manifest == null ? fallbackOrigin : manifest.sourceUrl(),
                operation.candidateOrigin(),
                manifest == null ? abbreviate(embeddedHash) : abbreviate(manifest.sha256()),
                manifest == null ? embeddedUpdatedAt : manifest.updatedAt(),
                operation.checkedAt() == null ? embeddedCheckedAt : operation.checkedAt(),
                operation.phase(), operation.detail(), operation.failureKind(),
                operation.candidateVersion());
    }

    private OperationalState operationalState(ArtifactId artifact) {
        ArtifactUpdateEvent event = currentEvents.get(artifact);
        if (event != null) {
            return new OperationalState(phase(event.status()), event.at(), event.detail(),
                    event.failureKind(), event.candidate() == null
                    ? null : event.candidate().version(),
                    event.candidate() == null ? null : event.candidate().sourceUrl());
        }

        ArtifactUpdateStateStore.State saved = coordinator.state(artifact);
        if (saved == null) {
            return OperationalState.notChecked();
        }
        return switch (saved.result()) {
            case UP_TO_DATE -> new OperationalState(ExternalSourcePhase.UP_TO_DATE,
                    saved.lastCheckedAt(), saved.detail(), saved.failureKind(), null, null);
            case APPLIED -> new OperationalState(ExternalSourcePhase.UP_TO_DATE,
                    saved.lastCheckedAt(), saved.detail(), null, null, null);
            case FAILED -> new OperationalState(ExternalSourcePhase.FAILED,
                    saved.lastCheckedAt(), saved.detail(), saved.failureKind(),
                    saved.candidateVersion(), null);
            case CHECKING, UPDATE_AVAILABLE, APPLYING -> OperationalState.notChecked();
        };
    }

    private static ExternalSourcesPhase aggregate(List<ExternalSourceState> states,
            boolean validationActive, boolean operationRunning, boolean applyingOperation,
            boolean runtimeReloading, boolean restartRequired, boolean updatedAndInUse) {
        if (runtimeReloading) {
            return ExternalSourcesPhase.RELOADING_RUNTIME;
        }
        if (applyingOperation || has(states, ExternalSourcePhase.APPLYING)) {
            return ExternalSourcesPhase.APPLYING;
        }
        if (restartRequired || has(states, ExternalSourcePhase.APPLIED)) {
            return ExternalSourcesPhase.RESTART_REQUIRED;
        }
        if (updatedAndInUse) {
            return ExternalSourcesPhase.UPDATED_AND_IN_USE;
        }
        if (operationRunning) {
            return ExternalSourcesPhase.CHECKING;
        }
        if (has(states, ExternalSourcePhase.CHECKING)) {
            return ExternalSourcesPhase.CHECKING;
        }
        if (has(states, ExternalSourcePhase.UPDATE_AVAILABLE)) {
            return validationActive ? ExternalSourcesPhase.WAITING_FOR_VALIDATION
                    : ExternalSourcesPhase.UPDATES_AVAILABLE;
        }
        if (has(states, ExternalSourcePhase.FAILED)) {
            return ExternalSourcesPhase.FAILED;
        }
        if (all(states, ExternalSourcePhase.UP_TO_DATE)) {
            return ExternalSourcesPhase.UP_TO_DATE;
        }
        return ExternalSourcesPhase.IDLE;
    }

    private static boolean has(List<ExternalSourceState> states, ExternalSourcePhase phase) {
        return states.stream().anyMatch(source -> source.phase() == phase);
    }

    private static boolean all(List<ExternalSourceState> states, ExternalSourcePhase phase) {
        return states.stream().allMatch(source -> source.phase() == phase);
    }

    private boolean enqueue(ExternalSourcesSnapshot snapshot) {
        pendingPublications.addLast(snapshot);
        if (publishing) {
            return false;
        }
        publishing = true;
        return true;
    }

    private void drainPublications() {
        while (true) {
            ExternalSourcesSnapshot snapshot;
            synchronized (stateLock) {
                snapshot = pendingPublications.pollFirst();
                if (snapshot == null) {
                    publishing = false;
                    break;
                }
            }
            for (Consumer<ExternalSourcesSnapshot> observer : observers) {
                try {
                    observer.accept(snapshot);
                } catch (RuntimeException ignored) {
                    // Um observador não participa do protocolo de admissão nem pode prender o gate.
                }
            }
        }
    }

    private static ExternalSourcePhase phase(ArtifactUpdateEvent.Status status) {
        return switch (status) {
            case CHECKING -> ExternalSourcePhase.CHECKING;
            case UP_TO_DATE -> ExternalSourcePhase.UP_TO_DATE;
            case UPDATE_AVAILABLE -> ExternalSourcePhase.UPDATE_AVAILABLE;
            case APPLYING -> ExternalSourcePhase.APPLYING;
            case APPLIED -> ExternalSourcePhase.APPLIED;
            case FAILED -> ExternalSourcePhase.FAILED;
        };
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.substring(0, Math.min(12, value.length())) + "…";
    }

    private static Instant at(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private record OperationalState(ExternalSourcePhase phase, Instant checkedAt,
            String detail, ArtifactFailureKind failureKind, String candidateVersion,
            String candidateOrigin) {

        private static OperationalState notChecked() {
            return new OperationalState(ExternalSourcePhase.NOT_CHECKED,
                    null, null, null, null, null);
        }
    }
}
