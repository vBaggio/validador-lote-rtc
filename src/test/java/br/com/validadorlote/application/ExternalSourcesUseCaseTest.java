package br.com.validadorlote.application;

import br.com.validadorlote.infrastructure.tables.FiscalTableArtifactStore;
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
        assertThat(schemas.origin()).contains("dfe-portal.svrs.rs.gov.br/NFe/Documentos");
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
    void validationTurnsAvailableIntoWaitingAndRestoresItWhenValidationEnds() {
        schemasAction.checkReturns(available(ArtifactId.NFE_SCHEMAS, "010e_v1.03"));
        coordinator.checkNow();

        sources.validationStateChanged(true);
        assertThat(sources.snapshot().phase())
                .isEqualTo(ExternalSourcesPhase.WAITING_FOR_VALIDATION);

        sources.validationStateChanged(false);
        assertThat(sources.snapshot().phase())
                .isEqualTo(ExternalSourcesPhase.UPDATES_AVAILABLE);
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

        sources.validationStateChanged(true);
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
        sources.validationStateChanged(true);

        assertThat(sources.applyAvailable()).isFalse();
        assertThat(schemasAction.applyCalls).isZero();
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
}
