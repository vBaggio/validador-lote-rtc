package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.infrastructure.xml.ArtifactId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactUpdateCoordinatorTest {

    @TempDir Path temp;

    @Test
    void runsAfterBootOutsideTheCallerThreadAndManualCheckForcesANewConsultation() throws Exception {
        List<ArtifactUpdateEvent> events = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> worker = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        ArtifactUpdateAction action = new ArtifactUpdateAction() {
            @Override public ArtifactId artifact() { return ArtifactId.NFE_SCHEMAS; }
            @Override public boolean updateIfNew() {
                worker.set(Thread.currentThread().getName());
                calls.incrementAndGet();
                completed.countDown();
                return true;
            }
        };
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "artifact-test"));
        try {
            var coordinator = new ArtifactUpdateCoordinator(List.of(action),
                    ArtifactUpdateCoordinator.DEFAULT_INTERVAL,
                    Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC), executor,
                    events::add, new ArtifactUpdateStateStore(temp));

            coordinator.checkAfterBoot();
            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            executor.submit(() -> { }).get(2, TimeUnit.SECONDS);
            coordinator.checkNow();
            executor.submit(() -> { }).get(2, TimeUnit.SECONDS);

            assertThat(worker.get()).isEqualTo("artifact-test");
            assertThat(calls).hasValue(2);
            assertThat(events).extracting(ArtifactUpdateEvent::status)
                    .containsExactly(ArtifactUpdateEvent.Status.STARTED, ArtifactUpdateEvent.Status.UPDATED,
                            ArtifactUpdateEvent.Status.STARTED, ArtifactUpdateEvent.Status.UPDATED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void turnsSourceFailureIntoAnEventWithoutRethrowing() {
        ArtifactUpdateAction failing = new ArtifactUpdateAction() {
            @Override public ArtifactId artifact() { return ArtifactId.FISCAL_TABLES; }
            @Override public boolean updateIfNew() { throw new IllegalStateException("fonte indisponível"); }
        };
        List<ArtifactUpdateEvent> events = new ArrayList<>();
        ArtifactUpdateStateStore state = new ArtifactUpdateStateStore(temp);
        var coordinator = new ArtifactUpdateCoordinator(List.of(failing), java.time.Duration.ofHours(24),
                Clock.systemUTC(), Runnable::run, events::add, state);

        coordinator.checkNow();

        assertThat(events).extracting(ArtifactUpdateEvent::status)
                .containsExactly(ArtifactUpdateEvent.Status.STARTED, ArtifactUpdateEvent.Status.FAILED);
        assertThat(state.read(ArtifactId.FISCAL_TABLES).result())
                .isEqualTo(ArtifactUpdateEvent.Status.FAILED);
    }

    @Test
    void persistsUnchangedAndFailedResultsAcrossCoordinatorRestartsWithoutTouchingVersions() {
        AtomicInteger calls = new AtomicInteger();
        ArtifactUpdateAction action = new ArtifactUpdateAction() {
            @Override public ArtifactId artifact() { return ArtifactId.NFE_SCHEMAS; }
            @Override public boolean updateIfNew() { return calls.incrementAndGet() == 1; }
        };
        ArtifactUpdateStateStore state = new ArtifactUpdateStateStore(temp);
        Instant start = Instant.parse("2026-07-29T12:00:00Z");
        var first = new ArtifactUpdateCoordinator(List.of(action), java.time.Duration.ofHours(24),
                Clock.fixed(start, ZoneOffset.UTC), Runnable::run, event -> { }, state);
        first.checkNow();

        List<ArtifactUpdateEvent> beforeLimit = new ArrayList<>();
        var restartedBeforeLimit = new ArtifactUpdateCoordinator(List.of(action), java.time.Duration.ofHours(24),
                Clock.fixed(start.plusSeconds(60), ZoneOffset.UTC), Runnable::run, beforeLimit::add, state);
        restartedBeforeLimit.checkAfterBoot();
        assertThat(calls).hasValue(1);
        assertThat(beforeLimit).isEmpty();

        List<ArtifactUpdateEvent> afterLimit = new ArrayList<>();
        var restartedAfterLimit = new ArtifactUpdateCoordinator(List.of(action), java.time.Duration.ofHours(24),
                Clock.fixed(start.plus(java.time.Duration.ofHours(24)), ZoneOffset.UTC), Runnable::run,
                afterLimit::add, state);
        restartedAfterLimit.checkAfterBoot();

        assertThat(calls).hasValue(2);
        assertThat(afterLimit).extracting(ArtifactUpdateEvent::status)
                .containsExactly(ArtifactUpdateEvent.Status.STARTED, ArtifactUpdateEvent.Status.UNCHANGED);
        assertThat(state.read(ArtifactId.NFE_SCHEMAS).result())
                .isEqualTo(ArtifactUpdateEvent.Status.UNCHANGED);
    }

    @Test
    void rejectsRepeatedManualCheckWhileTheFirstOneIsQueued() {
        AtomicInteger calls = new AtomicInteger();
        List<Runnable> queued = new ArrayList<>();
        ArtifactUpdateAction action = new ArtifactUpdateAction() {
            @Override public ArtifactId artifact() { return ArtifactId.NFE_SCHEMAS; }
            @Override public boolean updateIfNew() { calls.incrementAndGet(); return false; }
        };
        var coordinator = new ArtifactUpdateCoordinator(List.of(action), java.time.Duration.ofHours(24),
                Clock.systemUTC(), queued::add, event -> { }, new ArtifactUpdateStateStore(temp));

        assertThat(coordinator.checkNow()).isTrue();
        assertThat(coordinator.checkNow()).isFalse();
        assertThat(queued).hasSize(1);
        queued.getFirst().run();

        assertThat(calls).hasValue(1);
        assertThat(coordinator.isRunning()).isFalse();
    }
}
