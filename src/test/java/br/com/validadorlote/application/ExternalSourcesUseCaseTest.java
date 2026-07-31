package br.com.validadorlote.application;

import br.com.validadorlote.infrastructure.tables.FiscalTableArtifactStore;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.csv.CsvExporter;
import br.com.validadorlote.infrastructure.fs.FolderScanner;
import br.com.validadorlote.infrastructure.rules.RuleEngine;
import br.com.validadorlote.infrastructure.update.ArtifactCheckResult;
import br.com.validadorlote.infrastructure.update.ArtifactFailureKind;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateAction;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCandidate;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCoordinator;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateEvent;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateException;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateStateStore;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import br.com.validadorlote.infrastructure.xml.ArtifactManifest;
import br.com.validadorlote.infrastructure.xml.SchemaArtifactStore;
import br.com.validadorlote.application.ValidationRuntime;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import br.com.validadorlote.infrastructure.xml.XsdErrorTranslator;
import br.com.validadorlote.domain.RootCauseGrouper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalSourcesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    private static final String SCHEMA_CHANNEL = "test-schemas-v1";
    private static final String TABLE_CHANNEL = "test-tables-v1";

    @TempDir Path temp;

    private TestAction schemasAction;
    private TestAction tablesAction;
    private ArtifactUpdateCoordinator coordinator;
    private ExternalSourcesUseCase sources;

    @BeforeEach
    void setUp() {
        schemasAction = new TestAction(ArtifactId.NFE_SCHEMAS, SCHEMA_CHANNEL);
        tablesAction = new TestAction(ArtifactId.FISCAL_TABLES, TABLE_CHANNEL);
        coordinator = coordinator(new ArtifactUpdateStateStore(temp));
        sources = new ExternalSourcesUseCase(coordinator, new SchemaArtifactStore(temp),
                new FiscalTableArtifactStore(temp));
    }

    @Test
    void reportsEmbeddedProvenanceOnAFreshOfflineInstall() {
        ExternalSourceState schemas = source(ArtifactId.NFE_SCHEMAS);
        assertThat(schemas.activeVersion()).isEqualTo("010e_v1.02 (embarcada)");
        assertThat(schemas.origin()).isEqualTo(
                "https://www.nfe.fazenda.gov.br/portal/"
                        + "listaConteudo.aspx?tipoConteudo=BMPFMBoln3w%3D");
        assertThat(schemas.abbreviatedHash()).isEqualTo("1c7401d64600…");
        assertThat(schemas.updatedAt()).isEqualTo(Instant.parse("2026-07-29T00:00:00Z"));
        assertThat(schemas.checkedAt()).isNull();
        assertThat(schemas.phase()).isEqualTo(ExternalSourcePhase.NOT_CHECKED);

        ExternalSourceState tables = source(ArtifactId.FISCAL_TABLES);
        assertThat(tables.activeVersion()).isEqualTo("IT 1.60 (embarcada)");
        assertThat(tables.origin()).contains("dfe-portal.svrs.rs.gov.br");
        assertThat(tables.abbreviatedHash()).isNull();
        assertThat(tables.updatedAt()).isEqualTo(Instant.parse("2026-07-27T00:00:00Z"));
        assertThat(tables.checkedAt()).isEqualTo(Instant.parse("2026-07-29T00:00:00Z"));
        assertThat(tables.phase()).isEqualTo(ExternalSourcePhase.NOT_CHECKED);
    }

    @Test
    void exposesPartialSuccessWithoutHidingTheAvailableCandidate() {
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));
        tablesAction.checkFails(ArtifactUpdateException.invalidContent(
                "A tabela publicada não pôde ser validada"));

        coordinator.checkNow();

        ExternalSourcesSnapshot snapshot = sources.snapshot();
        assertThat(snapshot.phase()).isEqualTo(ExternalSourcesPhase.UPDATES_AVAILABLE);
        assertThat(snapshot.availableCount()).isOne();
        assertThat(snapshot.failedCount()).isOne();
        assertThat(source(ArtifactId.NFE_SCHEMAS).candidateVersion()).isEqualTo("010e_v1.03");
    }

    @Test
    void exposesPartialFailureWhenTheOtherSourceIsUpToDate() {
        schemasAction.checkReturns(ArtifactCheckResult.upToDate("Schemas atualizados"));
        tablesAction.checkFails(ArtifactUpdateException.connection(
                "Não foi possível consultar a tabela", null));

        coordinator.checkNow();

        ExternalSourcesSnapshot snapshot = sources.snapshot();
        assertThat(snapshot.phase()).isEqualTo(ExternalSourcesPhase.FAILED);
        assertThat(snapshot.availableCount()).isZero();
        assertThat(snapshot.failedCount()).isOne();
        assertThat(source(ArtifactId.NFE_SCHEMAS).phase())
                .isEqualTo(ExternalSourcePhase.UP_TO_DATE);
        assertThat(source(ArtifactId.FISCAL_TABLES).phase())
                .isEqualTo(ExternalSourcePhase.FAILED);
    }

    @Test
    void exposesUnsupportedSchemasWhileKeepingTheEmbeddedBaseWithoutRestart() {
        schemasAction.checkFails(new ArtifactUpdateException(
                ArtifactFailureKind.UNSUPPORTED_SCHEMA_STRUCTURE, false,
                "A estrutura dos schemas mais recentes não é suportada"));
        tablesAction.checkReturns(ArtifactCheckResult.upToDate("Tabela atualizada"));

        coordinator.checkNow();

        ExternalSourceState schemas = source(ArtifactId.NFE_SCHEMAS);
        assertThat(schemas.failureKind())
                .isEqualTo(ArtifactFailureKind.UNSUPPORTED_SCHEMA_STRUCTURE);
        assertThat(schemas.activeVersion()).isEqualTo("010e_v1.02 (embarcada)");
        assertThat(schemas.candidateVersion()).isNull();
        assertThat(sources.snapshot().phase()).isEqualTo(ExternalSourcesPhase.FAILED);
        assertThat(sources.applyAvailable()).isFalse();
        assertThat(sources.snapshot().phase()).isNotEqualTo(
                ExternalSourcesPhase.RESTART_REQUIRED);
    }

    @Test
    void unsupportedSchemasDoNotPreventTableActivationFromRequestingRestart() {
        schemasAction.checkFails(new ArtifactUpdateException(
                ArtifactFailureKind.UNSUPPORTED_SCHEMA_STRUCTURE, false,
                "A estrutura dos schemas mais recentes não é suportada"));
        tablesAction.checkReturns(available(ArtifactId.FISCAL_TABLES, "IT-1.61"));
        coordinator.checkNow();

        assertThat(sources.applyAvailable()).isTrue();

        assertThat(schemasAction.applyCalls).isZero();
        assertThat(tablesAction.applyCalls).isOne();
        assertThat(source(ArtifactId.NFE_SCHEMAS).phase()).isEqualTo(
                ExternalSourcePhase.FAILED);
        assertThat(source(ArtifactId.FISCAL_TABLES).phase()).isEqualTo(
                ExternalSourcePhase.APPLIED);
        assertThat(sources.snapshot().phase()).isEqualTo(
                ExternalSourcesPhase.RESTART_REQUIRED);
    }

    @Test
    void validationTurnsAvailableIntoWaitingAndRestoresItWhenValidationEnds() {
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));
        coordinator.checkNow();

        assertThat(startValidation()).isTrue();
        assertThat(sources.snapshot().phase())
                .isEqualTo(ExternalSourcesPhase.WAITING_FOR_VALIDATION);

        finishValidation();
        assertThat(sources.snapshot().phase())
                .isEqualTo(ExternalSourcesPhase.UPDATES_AVAILABLE);
    }

    @Test
    void validationLeaseCapturesTheEntireRuntimeBeforeAnActivationCanBeReserved() {
        ValidationRuntime r1 = validationRuntime("r1");
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));
        coordinator.checkNow();

        ValidationLease lease = sources.tryAcquireValidationLease(r1).orElseThrow();

        assertThat(lease.runtime()).isSameAs(r1);
        assertThat(sources.applyAvailable()).isFalse();

        sources.validationFinished(lease);

        assertThat(sources.applyAvailable()).isTrue();
    }

    @Test
    void onlyTheLeaseThatWasAdmittedCanReleaseTheValidationGate() {
        ValidationRuntime r1 = validationRuntime("r1");
        ValidationLease lease = sources.tryAcquireValidationLease(r1).orElseThrow();

        sources.validationFinished(new ValidationLease(lease.id() + 1, r1));

        assertThat(sources.tryAcquireValidationLease(validationRuntime("r2"))).isEmpty();

        sources.validationFinished(lease);

        assertThat(sources.tryAcquireValidationLease(validationRuntime("r2"))).isPresent();
    }

    @Test
    void publishesTheSameImmutableRevisionToEveryObserver() {
        List<ExternalSourcesSnapshot> first = new ArrayList<>();
        List<ExternalSourcesSnapshot> second = new ArrayList<>();
        sources.observe(first::add);
        sources.observe(second::add);

        coordinator.checkNow();

        assertThat(first.getLast()).isSameAs(second.getLast());
        assertThat(first.getLast().revision()).isPositive();
        assertThatThrownBy(() -> first.getLast().sources().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void serializesConcurrentPublicationsByRevision() throws InterruptedException {
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));
        CountDownLatch availablePublicationStarted = new CountDownLatch(1);
        CountDownLatch releaseAvailablePublication = new CountDownLatch(1);
        AtomicBoolean blockOnce = new AtomicBoolean();
        List<ExternalSourcesSnapshot> delivered = new CopyOnWriteArrayList<>();
        sources.observe(snapshot -> {
            if (snapshot.phase() == ExternalSourcesPhase.UPDATES_AVAILABLE
                    && blockOnce.compareAndSet(false, true)) {
                availablePublicationStarted.countDown();
                await(releaseAvailablePublication);
            }
        });
        sources.observe(delivered::add);

        Thread check = Thread.ofPlatform().start(coordinator::checkNow);
        assertThat(availablePublicationStarted.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(startValidation()).isTrue();
        releaseAvailablePublication.countDown();
        check.join(5_000);
        assertThat(check.isAlive()).isFalse();

        assertThat(delivered).extracting(ExternalSourcesSnapshot::revision)
                .isSorted()
                .doesNotHaveDuplicates();
        assertThat(delivered.getLast().phase())
                .isEqualTo(ExternalSourcesPhase.WAITING_FOR_VALIDATION);
    }

    @Test
    void initialSnapshotContainsOnlyTheTwoActiveV0Sources() {
        assertThat(sources.snapshot().sources())
                .extracting(ExternalSourceState::artifact)
                .containsExactly(ArtifactId.NFE_SCHEMAS, ArtifactId.FISCAL_TABLES);
    }

    @Test
    void ignoresPersistedStateWrittenByAnotherUpdateChannel() {
        ArtifactUpdateStateStore state = new ArtifactUpdateStateStore(temp);
        state.write("outro-canal", new ArtifactUpdateEvent(ArtifactId.NFE_SCHEMAS,
                ArtifactUpdateEvent.Status.FAILED, NOW, null,
                ArtifactFailureKind.CONNECTION, "Falha de outro canal"));
        coordinator = coordinator(state);

        sources = new ExternalSourcesUseCase(coordinator, new SchemaArtifactStore(temp),
                new FiscalTableArtifactStore(temp));

        ExternalSourceState schemas = source(ArtifactId.NFE_SCHEMAS);
        assertThat(schemas.phase()).isEqualTo(ExternalSourcePhase.NOT_CHECKED);
        assertThat(schemas.detail()).isNull();
    }

    @Test
    void appliedSourceKeepsRestartRequiredWhenAnotherSourceFails() {
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));
        tablesAction.checkReturns(available(ArtifactId.FISCAL_TABLES, "IT-1.61"));
        tablesAction.applyFails(ArtifactUpdateException.localStorage(
                "Não foi possível ativar a tabela", null));
        coordinator.checkNow();

        sources.applyAvailable();

        assertThat(sources.snapshot().phase()).isEqualTo(ExternalSourcesPhase.RESTART_REQUIRED);
        assertThat(sources.snapshot().failedCount()).isOne();
        assertThat(source(ArtifactId.NFE_SCHEMAS).phase()).isEqualTo(ExternalSourcePhase.APPLIED);
    }

    @Test
    void validationPreventsApplyingAnAvailableCandidate() {
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));
        coordinator.checkNow();
        assertThat(startValidation()).isTrue();

        assertThat(sources.applyAvailable()).isFalse();
        assertThat(schemasAction.applyCalls).isZero();
    }

    @Test
    void applyingReservationRejectsValidationUntilTheOperationCompletes() {
        List<Runnable> updateQueue = new ArrayList<>();
        coordinator = new ArtifactUpdateCoordinator(List.of(schemasAction, tablesAction),
                Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC), updateQueue::add,
                event -> { }, new ArtifactUpdateStateStore(temp));
        sources = new ExternalSourcesUseCase(coordinator, new SchemaArtifactStore(temp),
                new FiscalTableArtifactStore(temp));
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));
        assertThat(coordinator.checkNow()).isTrue();
        updateQueue.removeFirst().run();

        assertThat(sources.applyAvailable()).isTrue();

        assertThat(sources.snapshot().phase()).isEqualTo(ExternalSourcesPhase.APPLYING);
        assertThat(startValidation()).isFalse();
        assertThat(sources.snapshot().validationActive()).isFalse();

        updateQueue.removeFirst().run();
        assertThat(startValidation()).isTrue();
        finishValidation();
    }

    @Test
    void observerFailureAfterReservationDoesNotLeaveTheValidationGateStuck() {
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));
        coordinator.checkNow();
        sources.observe(snapshot -> {
            if (snapshot.phase() == ExternalSourcesPhase.APPLYING) {
                throw new IllegalStateException("observer indisponível");
            }
        });

        assertThat(sources.applyAvailable()).isTrue();

        assertThat(startValidation()).isTrue();
        finishValidation();
    }

    @Test
    void completionOfAnOlderCheckCannotReleaseANewerActivationReservation()
            throws InterruptedException {
        List<Runnable> updateQueue = new CopyOnWriteArrayList<>();
        CountDownLatch oldCompletionStarted = new CountDownLatch(1);
        CountDownLatch releaseOldCompletion = new CountDownLatch(1);
        AtomicBoolean blockOnce = new AtomicBoolean(true);
        coordinator = new ArtifactUpdateCoordinator(List.of(schemasAction, tablesAction),
                Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC), updateQueue::add,
                event -> { }, new ArtifactUpdateStateStore(temp));
        coordinator.addCompletionListener(() -> {
            if (blockOnce.compareAndSet(true, false)) {
                oldCompletionStarted.countDown();
                await(releaseOldCompletion);
            }
        });
        sources = new ExternalSourcesUseCase(coordinator, new SchemaArtifactStore(temp),
                new FiscalTableArtifactStore(temp));
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));

        assertThat(coordinator.checkNow()).isTrue();
        Thread oldCheck = Thread.ofPlatform().start(updateQueue.removeFirst());
        assertThat(oldCompletionStarted.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(sources.applyAvailable()).isTrue();
        assertThat(startValidation()).isFalse();

        releaseOldCompletion.countDown();
        oldCheck.join(5_000);
        assertThat(oldCheck.isAlive()).isFalse();
        assertThat(startValidation()).isFalse();

        updateQueue.removeFirst().run();

        assertThat(startValidation()).isTrue();
        finishValidation();
    }

    @Test
    void rejectedApplicationSchedulingReleasesTheReservationForValidation() {
        AtomicBoolean reject = new AtomicBoolean();
        coordinator = new ArtifactUpdateCoordinator(List.of(schemasAction, tablesAction),
                Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC), action -> {
                    if (reject.get()) {
                        throw new RejectedExecutionException("executor encerrado");
                    }
                    action.run();
                }, event -> { }, new ArtifactUpdateStateStore(temp));
        sources = new ExternalSourcesUseCase(coordinator, new SchemaArtifactStore(temp),
                new FiscalTableArtifactStore(temp));
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));
        coordinator.checkNow();
        reject.set(true);

        assertThatThrownBy(sources::applyAvailable)
                .isInstanceOf(RejectedExecutionException.class)
                .hasMessage("executor encerrado");

        assertThat(sources.snapshot().phase()).isEqualTo(ExternalSourcesPhase.UPDATES_AVAILABLE);
        assertThat(startValidation()).isTrue();
        finishValidation();
        assertThat(schemasAction.applyCalls).isZero();
    }

    @Test
    void persistenceFailureAfterPhysicalActivationKeepsRestartVisibleAndRecoversWithoutReapply() {
        FailAppliedWriteOnceStateStore stateStore = new FailAppliedWriteOnceStateStore(temp);
        coordinator = coordinator(stateStore);
        sources = new ExternalSourcesUseCase(coordinator, new SchemaArtifactStore(temp),
                new FiscalTableArtifactStore(temp));
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));
        coordinator.checkNow();

        assertThat(sources.applyAvailable()).isTrue();

        assertThat(schemasAction.applyCalls).isOne();
        assertThat(sources.snapshot().phase()).isEqualTo(ExternalSourcesPhase.RESTART_REQUIRED);
        assertThat(sources.snapshot().failedCount()).isOne();
        assertThat(source(ArtifactId.NFE_SCHEMAS).phase()).isEqualTo(ExternalSourcePhase.FAILED);
        assertThat(coordinator.state(ArtifactId.NFE_SCHEMAS))
                .satisfies(state -> {
                    assertThat(state.result()).isEqualTo(ArtifactUpdateEvent.Status.FAILED);
                    assertThat(state.failureKind()).isEqualTo(ArtifactFailureKind.LOCAL_STORAGE);
                });

        schemasAction.checkReturns(ArtifactCheckResult.upToDate("Base ativa confirmada"));
        coordinator.checkNow();

        assertThat(sources.applyAvailable()).isFalse();
        assertThat(schemasAction.applyCalls).isOne();
        assertThat(sources.snapshot().phase()).isEqualTo(ExternalSourcesPhase.RESTART_REQUIRED);
    }

    @Test
    void applyingPublicationFailureEndsTerminallyAndAllowsRetryAfterAFreshCheck() {
        AtomicBoolean failApplyingOnce = new AtomicBoolean(true);
        coordinator = new ArtifactUpdateCoordinator(List.of(schemasAction, tablesAction),
                Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC), Runnable::run,
                event -> {
                    if (event.status() == ArtifactUpdateEvent.Status.APPLYING
                            && failApplyingOnce.compareAndSet(true, false)) {
                        throw ArtifactUpdateException.localStorage(
                                "Não foi possível publicar o início da ativação", null);
                    }
                }, new ArtifactUpdateStateStore(temp));
        sources = new ExternalSourcesUseCase(coordinator, new SchemaArtifactStore(temp),
                new FiscalTableArtifactStore(temp));
        List<ArtifactUpdateEvent> observed = new ArrayList<>();
        coordinator.addListener(observed::add);
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));
        coordinator.checkNow();

        assertThat(sources.applyAvailable()).isTrue();

        assertThat(schemasAction.applyCalls).isZero();
        assertThat(coordinator.isRunning()).isFalse();
        assertThat(observed).extracting(ArtifactUpdateEvent::status)
                .containsSubsequence(ArtifactUpdateEvent.Status.APPLYING,
                        ArtifactUpdateEvent.Status.FAILED);
        assertThat(source(ArtifactId.NFE_SCHEMAS).phase()).isEqualTo(ExternalSourcePhase.FAILED);
        assertThat(sources.snapshot().phase()).isNotEqualTo(ExternalSourcesPhase.APPLYING);
        assertThat(sources.snapshot().failedCount()).isOne();
        assertThat(sources.applyAvailable()).isFalse();

        assertThat(coordinator.checkNow()).isTrue();
        assertThat(sources.applyAvailable()).isTrue();

        assertThat(schemasAction.applyCalls).isOne();
        assertThat(coordinator.isRunning()).isFalse();
        assertThat(sources.snapshot().phase()).isEqualTo(ExternalSourcesPhase.RESTART_REQUIRED);
    }

    @Test
    void restartRequiredRemainsLatchedAfterANewCheck() {
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));
        coordinator.checkNow();
        sources.applyAvailable();
        assertThat(sources.snapshot().phase()).isEqualTo(ExternalSourcesPhase.RESTART_REQUIRED);

        coordinator.checkNow();

        assertThat(sources.snapshot().phase()).isEqualTo(ExternalSourcesPhase.RESTART_REQUIRED);
    }

    private ArtifactUpdateCoordinator coordinator(ArtifactUpdateStateStore state) {
        return new ArtifactUpdateCoordinator(List.of(schemasAction, tablesAction),
                Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC), Runnable::run,
                event -> { }, state);
    }

    private ValidationLease activeLease;

    private boolean startValidation() {
        return sources.tryAcquireValidationLease(validationRuntime("gate"))
                .map(lease -> {
                    activeLease = lease;
                    return true;
                })
                .orElse(false);
    }

    private void finishValidation() {
        sources.validationFinished(activeLease);
        activeLease = null;
    }

    private ExternalSourceState source(ArtifactId artifact) {
        return sources.snapshot().sources().stream()
                .filter(status -> status.artifact() == artifact)
                .findFirst()
                .orElseThrow();
    }

    private static ArtifactCheckResult available(ArtifactId artifact, String version) {
        return ArtifactCheckResult.available(new ArtifactUpdateCandidate(artifact, version,
                "https://dfe-portal.svrs.rs.gov.br/", NOW, "0".repeat(64),
                "Atualização preparada"), "Atualização preparada");
    }

    private static ValidationRuntime validationRuntime(String version) {
        XsdErrorTranslator translator = new XsdErrorTranslator();
        ValidateBatchUseCase useCase = new ValidateBatchUseCase(new FolderScanner(),
                new XmlMetadataParser(), new TaxGroupExtractor(), new SchemaValidatorEngine(translator),
                new RuleEngine(FiscalTables.load()), new RootCauseGrouper(), translator,
                new CsvExporter(), "motor-teste");
        return new ValidationRuntime(useCase, new RuntimeBases("schemas-" + version,
                "canal-" + version, "tabelas-" + version, "svrs-" + version, 1));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Publicação concorrente não foi liberada");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Teste de publicação concorrente interrompido", e);
        }
    }

    private static final class TestAction implements ArtifactUpdateAction {

        private final ArtifactId artifact;
        private final String channelId;
        private Supplier<ArtifactCheckResult> check =
                () -> ArtifactCheckResult.upToDate("Base atual");
        private java.util.function.Function<ArtifactUpdateCandidate, ArtifactManifest> apply =
                TestAction::manifest;
        private int applyCalls;

        private TestAction(ArtifactId artifact, String channelId) {
            this.artifact = artifact;
            this.channelId = channelId;
        }

        private void checkReturns(ArtifactCheckResult result) {
            check = () -> result;
        }

        private void checkFails(ArtifactUpdateException failure) {
            check = () -> {
                throw failure;
            };
        }

        private void applyFails(ArtifactUpdateException failure) {
            apply = candidate -> {
                throw failure;
            };
        }

        @Override
        public ArtifactId artifact() {
            return artifact;
        }

        @Override
        public String channelId() {
            return channelId;
        }

        @Override
        public ArtifactCheckResult check() {
            return check.get();
        }

        @Override
        public ArtifactManifest apply(ArtifactUpdateCandidate candidate) {
            applyCalls++;
            return apply.apply(candidate);
        }

        private static ArtifactManifest manifest(ArtifactUpdateCandidate candidate) {
            return new ArtifactManifest(candidate.artifact(), candidate.version(),
                    candidate.sourceUrl(), candidate.publishedAt(), candidate.sha256(),
                    NOW, NOW, "APPLIED");
        }
    }

    private static final class FailAppliedWriteOnceStateStore extends ArtifactUpdateStateStore {

        private boolean failed;

        private FailAppliedWriteOnceStateStore(Path dataDirectory) {
            super(dataDirectory);
        }

        @Override
        public synchronized void write(String channelId, ArtifactUpdateEvent event) {
            if (event.status() == ArtifactUpdateEvent.Status.APPLIED && !failed) {
                failed = true;
                throw ArtifactUpdateException.localStorage(
                        "Não foi possível registrar a ativação", null);
            }
            super.write(channelId, event);
        }
    }
}
