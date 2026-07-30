package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.infrastructure.xml.ArtifactId;
import br.com.validadorlote.infrastructure.xml.ArtifactManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactUpdateCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    private static final Duration INTERVAL = Duration.ofHours(24);
    private static final ArtifactRetryPolicy NO_DELAY =
            new ArtifactRetryPolicy(2, Duration.ZERO, ignored -> { });

    @TempDir Path temp;

    @Test
    void runsAfterBootOutsideTheCallerThreadAndManualCheckForcesANewConsultation() throws Exception {
        List<ArtifactUpdateEvent> events = new ArrayList<>();
        AtomicReference<String> worker = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        TestAction action = action(ArtifactId.NFE_SCHEMAS, "schemas-v1");
        action.checkBehavior = () -> {
            worker.set(Thread.currentThread().getName());
            completed.countDown();
            return ArtifactCheckResult.upToDate("base atual");
        };
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "artifact-test"));
        try {
            var coordinator = coordinator(List.of(action), executor, events);

            coordinator.checkAfterBoot();
            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            executor.submit(() -> { }).get(2, TimeUnit.SECONDS);
            coordinator.checkNow();
            executor.submit(() -> { }).get(2, TimeUnit.SECONDS);

            assertThat(worker.get()).isEqualTo("artifact-test");
            assertThat(action.checkCalls).hasValue(2);
            assertThat(events).extracting(ArtifactUpdateEvent::status)
                    .containsExactly(ArtifactUpdateEvent.Status.CHECKING,
                            ArtifactUpdateEvent.Status.UP_TO_DATE,
                            ArtifactUpdateEvent.Status.CHECKING,
                            ArtifactUpdateEvent.Status.UP_TO_DATE);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retriesOneTransientFailureButDoesNotRetryInvalidContent() {
        TestAction transientAction = action(ArtifactId.NFE_SCHEMAS, "schemas-v1");
        AtomicInteger attempts = new AtomicInteger();
        transientAction.checkBehavior = () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new ArtifactUpdateException(ArtifactFailureKind.CONNECTION, true,
                        "Fonte temporariamente indisponível");
            }
            return ArtifactCheckResult.upToDate("base atual");
        };
        TestAction invalidAction = action(ArtifactId.FISCAL_TABLES, "tables-v1");
        invalidAction.checkBehavior = () -> {
            throw new ArtifactUpdateException(ArtifactFailureKind.INVALID_CONTENT, false,
                    "Conteúdo da fonte inválido");
        };
        var coordinator = coordinator(List.of(transientAction, invalidAction), Runnable::run,
                new ArrayList<>());

        coordinator.checkNow();

        assertThat(transientAction.checkCalls).hasValue(2);
        assertThat(invalidAction.checkCalls).hasValue(1);
    }

    @Test
    void checkingListenerFailureEndsTerminallyAndAllowsANewCheck() {
        TestAction action = action(ArtifactId.NFE_SCHEMAS, "schemas-v1");
        List<ArtifactUpdateEvent> events = new ArrayList<>();
        AtomicBoolean failCheckingOnce = new AtomicBoolean(true);
        var coordinator = coordinator(List.of(action), Runnable::run, events);
        coordinator.addListener(event -> {
            if (event.status() == ArtifactUpdateEvent.Status.CHECKING
                    && failCheckingOnce.compareAndSet(true, false)) {
                throw new IllegalStateException("observador indisponível");
            }
        });

        assertThat(coordinator.checkNow()).isTrue();

        assertThat(action.checkCalls).hasValue(0);
        assertThat(events).extracting(ArtifactUpdateEvent::status)
                .containsExactly(ArtifactUpdateEvent.Status.CHECKING,
                        ArtifactUpdateEvent.Status.FAILED);
        assertThat(coordinator.state(ArtifactId.NFE_SCHEMAS))
                .satisfies(state -> {
                    assertThat(state.result()).isEqualTo(ArtifactUpdateEvent.Status.FAILED);
                    assertThat(state.failureKind()).isEqualTo(ArtifactFailureKind.UNKNOWN);
                });
        assertThat(coordinator.isRunning()).isFalse();

        assertThat(coordinator.checkNow()).isTrue();

        assertThat(action.checkCalls).hasValue(1);
        assertThat(events).extracting(ArtifactUpdateEvent::status)
                .endsWith(ArtifactUpdateEvent.Status.CHECKING,
                        ArtifactUpdateEvent.Status.UP_TO_DATE);
        assertThat(coordinator.isRunning()).isFalse();
    }

    @Test
    void partialCheckKeepsCandidateAndApplyContinuesAfterAnotherSourceFailed() {
        ArtifactUpdateCandidate candidate = candidate(ArtifactId.NFE_SCHEMAS, "schemas-v2");
        TestAction successfulAction = action(ArtifactId.NFE_SCHEMAS, "schemas-v1");
        successfulAction.checkBehavior =
                () -> ArtifactCheckResult.available(candidate, "schemas disponíveis");
        TestAction failingAction = action(ArtifactId.FISCAL_TABLES, "tables-v1");
        failingAction.checkBehavior = () -> {
            throw new ArtifactUpdateException(ArtifactFailureKind.INVALID_CONTENT, false,
                    "Tabela inválida");
        };
        List<ArtifactUpdateEvent> events = new ArrayList<>();
        var coordinator = coordinator(List.of(successfulAction, failingAction), Runnable::run, events);

        coordinator.checkNow();

        assertThat(events).extracting(ArtifactUpdateEvent::status)
                .contains(ArtifactUpdateEvent.Status.UPDATE_AVAILABLE,
                        ArtifactUpdateEvent.Status.FAILED);

        assertThat(coordinator.applyAvailable()).isTrue();

        assertThat(successfulAction.applyCalls).hasValue(1);
        assertThat(failingAction.applyCalls).hasValue(0);
        assertThat(events).extracting(ArtifactUpdateEvent::status)
                .containsSubsequence(ArtifactUpdateEvent.Status.APPLYING,
                        ArtifactUpdateEvent.Status.APPLIED);
        assertThat(coordinator.state(ArtifactId.NFE_SCHEMAS).candidateVersion()).isNull();
    }

    @Test
    void failedApplicationDoesNotStopAnotherCandidateOrRetryBlindly() {
        TestAction schemas = action(ArtifactId.NFE_SCHEMAS, "schemas-v1");
        schemas.checkBehavior = () -> ArtifactCheckResult.available(
                candidate(ArtifactId.NFE_SCHEMAS, "schemas-v2"), "schemas disponíveis");
        schemas.applyBehavior = ignored -> {
            throw ArtifactUpdateException.localStorage("Não foi possível ativar os schemas", null);
        };
        TestAction tables = action(ArtifactId.FISCAL_TABLES, "tables-v1");
        tables.checkBehavior = () -> ArtifactCheckResult.available(
                candidate(ArtifactId.FISCAL_TABLES, "tables-v2"), "tabela disponível");
        var coordinator = coordinator(List.of(schemas, tables), Runnable::run, new ArrayList<>());
        coordinator.checkNow();

        assertThat(coordinator.applyAvailable()).isTrue();

        assertThat(schemas.applyCalls).hasValue(1);
        assertThat(tables.applyCalls).hasValue(1);
        assertThat(coordinator.state(ArtifactId.NFE_SCHEMAS).candidateVersion())
                .isEqualTo("schemas-v2");
        assertThat(coordinator.applyAvailable()).isFalse();
        assertThat(schemas.applyCalls).hasValue(1);
    }

    @Test
    void physicalActivationSurvivesTerminalPersistenceFailureWithoutBlindReapply() {
        TestAction schemas = action(ArtifactId.NFE_SCHEMAS, "schemas-v1");
        schemas.checkBehavior = () -> ArtifactCheckResult.available(
                candidate(ArtifactId.NFE_SCHEMAS, "schemas-v2"), "schemas disponíveis");
        FailAppliedWriteOnceStateStore stateStore = new FailAppliedWriteOnceStateStore(temp);
        List<ArtifactUpdateEvent> events = new ArrayList<>();
        var coordinator = new ArtifactUpdateCoordinator(List.of(schemas), INTERVAL,
                Clock.fixed(NOW, ZoneOffset.UTC), Runnable::run, events::add, stateStore, NO_DELAY);
        coordinator.checkNow();

        assertThat(coordinator.applyAvailable()).isTrue();

        assertThat(schemas.applyCalls).hasValue(1);
        assertThat(events).extracting(ArtifactUpdateEvent::status)
                .containsSubsequence(ArtifactUpdateEvent.Status.APPLYING,
                        ArtifactUpdateEvent.Status.APPLIED,
                        ArtifactUpdateEvent.Status.FAILED);
        assertThat(coordinator.state(ArtifactId.NFE_SCHEMAS))
                .satisfies(state -> {
                    assertThat(state.result()).isEqualTo(ArtifactUpdateEvent.Status.FAILED);
                    assertThat(state.failureKind()).isEqualTo(ArtifactFailureKind.LOCAL_STORAGE);
                    assertThat(state.candidateVersion()).isEqualTo("schemas-v2");
                });
        assertThat(coordinator.applyAvailable()).isFalse();
        assertThat(schemas.applyCalls).hasValue(1);
    }

    @Test
    void terminalPublicationFailureDoesNotHidePhysicalActivationFromOtherListeners() {
        TestAction schemas = action(ArtifactId.NFE_SCHEMAS, "schemas-v1");
        schemas.checkBehavior = () -> ArtifactCheckResult.available(
                candidate(ArtifactId.NFE_SCHEMAS, "schemas-v2"), "schemas disponíveis");
        List<ArtifactUpdateEvent> observed = new ArrayList<>();
        var coordinator = new ArtifactUpdateCoordinator(List.of(schemas), INTERVAL,
                Clock.fixed(NOW, ZoneOffset.UTC), Runnable::run, event -> {
                    if (event.status() == ArtifactUpdateEvent.Status.APPLIED) {
                        throw ArtifactUpdateException.localStorage(
                                "Não foi possível publicar o resultado", null);
                    }
                }, new ArtifactUpdateStateStore(temp), NO_DELAY);
        coordinator.addListener(observed::add);
        coordinator.checkNow();

        assertThat(coordinator.applyAvailable()).isTrue();

        assertThat(observed).extracting(ArtifactUpdateEvent::status)
                .containsSubsequence(ArtifactUpdateEvent.Status.APPLYING,
                        ArtifactUpdateEvent.Status.APPLIED,
                        ArtifactUpdateEvent.Status.FAILED);
        assertThat(schemas.applyCalls).hasValue(1);
        assertThat(coordinator.state(ArtifactId.NFE_SCHEMAS))
                .satisfies(state -> {
                    assertThat(state.result()).isEqualTo(ArtifactUpdateEvent.Status.FAILED);
                    assertThat(state.failureKind()).isEqualTo(ArtifactFailureKind.LOCAL_STORAGE);
                });
        assertThat(coordinator.applyAvailable()).isFalse();
    }

    @Test
    void failingCompletionListenerDoesNotPreventLaterListenersFromLeavingTheRunningState() {
        TestAction schemas = action(ArtifactId.NFE_SCHEMAS, "schemas-v1");
        var coordinator = coordinator(List.of(schemas), Runnable::run, new ArrayList<>());
        AtomicInteger laterListenerCalls = new AtomicInteger();
        coordinator.addCompletionListener(() -> {
            throw new IllegalStateException("observador indisponível");
        });
        coordinator.addCompletionListener(laterListenerCalls::incrementAndGet);

        assertThatThrownBy(coordinator::checkNow)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("observador indisponível");

        assertThat(laterListenerCalls).hasValue(1);
        assertThat(coordinator.isRunning()).isFalse();
    }

    @Test
    void rejectsCheckAndApplyWhileAnotherOperationIsRunning() {
        List<Runnable> queued = new ArrayList<>();
        TestAction action = action(ArtifactId.NFE_SCHEMAS, "schemas-v1");
        action.checkBehavior = () -> ArtifactCheckResult.available(
                candidate(ArtifactId.NFE_SCHEMAS, "schemas-v2"), "disponível");
        var coordinator = coordinator(List.of(action), queued::add, new ArrayList<>());

        assertThat(coordinator.checkNow()).isTrue();
        assertThat(coordinator.checkNow()).isFalse();
        assertThat(coordinator.applyAvailable()).isFalse();
        assertThat(queued).hasSize(1);

        queued.removeFirst().run();

        assertThat(coordinator.applyAvailable()).isTrue();
        assertThat(queued).hasSize(1);
    }

    @Test
    void failedAndAvailableStatesAreDueOnNextBootButSuccessWaitsTheInterval() {
        ArtifactUpdateStateStore store = new ArtifactUpdateStateStore(temp);
        TestAction failed = action(ArtifactId.NFE_SCHEMAS, "schemas-v1");
        TestAction available = action(ArtifactId.FISCAL_TABLES, "tables-v1");
        TestAction successful = action(ArtifactId.CALCULATOR, "calculator-v1");
        store.write(failed.channelId(), new ArtifactUpdateEvent(failed.artifact(),
                ArtifactUpdateEvent.Status.FAILED, NOW, null, ArtifactFailureKind.CONNECTION,
                "falha de conexão"));
        ArtifactUpdateCandidate tableCandidate =
                candidate(ArtifactId.FISCAL_TABLES, "tables-v2");
        store.write(available.channelId(), new ArtifactUpdateEvent(available.artifact(),
                ArtifactUpdateEvent.Status.UPDATE_AVAILABLE, NOW, tableCandidate, null,
                "disponível"));
        store.write(successful.channelId(), new ArtifactUpdateEvent(successful.artifact(),
                ArtifactUpdateEvent.Status.UP_TO_DATE, NOW, null, null, "atual"));
        var coordinator = new ArtifactUpdateCoordinator(List.of(failed, available, successful),
                INTERVAL, Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC), Runnable::run,
                event -> { }, store, NO_DELAY);

        coordinator.checkAfterBoot();

        assertThat(failed.checkCalls).hasValue(1);
        assertThat(available.checkCalls).hasValue(1);
        assertThat(successful.checkCalls).hasValue(0);
    }

    @Test
    void interruptedRetryStopsImmediatelyAndPreservesTheFlag() {
        ArtifactRetryPolicy retryPolicy = new ArtifactRetryPolicy(2, Duration.ZERO, ignored -> { });
        Supplier<String> alwaysTransientFailure = () -> {
            throw new ArtifactUpdateException(ArtifactFailureKind.CONNECTION, true,
                    "falha transitória");
        };
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> retryPolicy.execute(alwaysTransientFailure))
                    .isInstanceOf(ArtifactUpdateException.class)
                    .satisfies(failure ->
                            assertThat(Thread.currentThread().isInterrupted()).isTrue());
        } finally {
            Thread.interrupted();
        }
    }

    private ArtifactUpdateCoordinator coordinator(List<ArtifactUpdateAction> actions,
            java.util.concurrent.Executor executor, List<ArtifactUpdateEvent> events) {
        return new ArtifactUpdateCoordinator(actions, INTERVAL,
                Clock.fixed(NOW, ZoneOffset.UTC), executor, events::add,
                new ArtifactUpdateStateStore(temp), NO_DELAY);
    }

    private static TestAction action(ArtifactId artifact, String channelId) {
        return new TestAction(artifact, channelId);
    }

    private static ArtifactUpdateCandidate candidate(ArtifactId artifact, String version) {
        return new ArtifactUpdateCandidate(artifact, version,
                "https://dfe-portal.svrs.rs.gov.br/source", NOW, "0".repeat(64), "candidata");
    }

    private static ArtifactManifest manifest(ArtifactUpdateCandidate candidate) {
        return new ArtifactManifest(candidate.artifact(), candidate.version(),
                candidate.sourceUrl(), candidate.publishedAt(), candidate.sha256(),
                NOW, NOW, "UPDATED");
    }

    private static final class TestAction implements ArtifactUpdateAction {
        private final ArtifactId artifact;
        private final String channelId;
        private final AtomicInteger checkCalls = new AtomicInteger();
        private final AtomicInteger applyCalls = new AtomicInteger();
        private Supplier<ArtifactCheckResult> checkBehavior =
                () -> ArtifactCheckResult.upToDate("base atual");
        private Function<ArtifactUpdateCandidate, ArtifactManifest> applyBehavior =
                ArtifactUpdateCoordinatorTest::manifest;

        private TestAction(ArtifactId artifact, String channelId) {
            this.artifact = artifact;
            this.channelId = channelId;
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
            checkCalls.incrementAndGet();
            return checkBehavior.get();
        }

        @Override
        public ArtifactManifest apply(ArtifactUpdateCandidate candidate) {
            applyCalls.incrementAndGet();
            return applyBehavior.apply(candidate);
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
