package br.com.validadorlote.presentation;

import br.com.validadorlote.application.ValidateBatchUseCase;
import br.com.validadorlote.application.DocumentValidationResult;
import br.com.validadorlote.application.RuntimeBases;
import br.com.validadorlote.application.ValidationRuntimeFactory;
import br.com.validadorlote.application.ValidationRuntime;
import br.com.validadorlote.application.ValidationLease;
import br.com.validadorlote.application.ExternalSourcesPhase;
import br.com.validadorlote.application.ExternalSourcesSnapshot;
import br.com.validadorlote.application.ExternalSourcesUseCase;
import br.com.validadorlote.domain.RootCauseGrouper;
import br.com.validadorlote.infrastructure.csv.CsvExporter;
import br.com.validadorlote.infrastructure.fs.FolderScanner;
import br.com.validadorlote.infrastructure.rules.RuleEngine;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import br.com.validadorlote.infrastructure.xml.XsdErrorTranslator;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import br.com.validadorlote.infrastructure.xml.SchemaArtifactStore;
import br.com.validadorlote.infrastructure.tables.FiscalTableArtifactStore;
import br.com.validadorlote.infrastructure.update.ArtifactCheckResult;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateAction;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCandidate;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCoordinator;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateEvent;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateException;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateStateStore;
import br.com.validadorlote.infrastructure.xml.ArtifactManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MainPresenterTest {

    private static final UiThread DIRECT_UI_THREAD = new UiThread() {
        @Override public void execute(Runnable action) { action.run(); }
        @Override public void executeLater(Runnable action) { action.run(); }
    };

    private final List<String> calls = new ArrayList<>();
    private List<WorkspaceDocument> lastWorkspace = List.of();
    private MainPresenter presenter;
    private ExternalSourcesUseCase observedSources;
    private ArtifactUpdateCoordinator updateCoordinator;
    private TestUpdateAction schemasAction;
    private TestUpdateAction tablesAction;
    private RecordingUiThread recordingUiThread;
    private List<Runnable> queuedBackground;

    private final FakeView fakeView = new FakeView();

    private final class FakeView implements MainView {

        private boolean acceptUpdate;
        private boolean confirmOnUiThread;
        private boolean externalSourcesDialogOpen;
        private boolean errorOnUiThread;
        private boolean failIfDialogOpensBeforeTerminalSnapshot;

        @Override
        public void showIdle() {
            calls.add("idle");
        }

        @Override
        public void showWorkspace(List<WorkspaceDocument> documents, boolean validating, int processed,
                int total) {
            calls.add("workspace " + documents.size() + " " + validating + " " + processed + "/" + total);
            lastWorkspace = documents;
        }

        @Override
        public void showInvalidFiles(List<Path> files) {
            calls.add("invalidFiles " + files.size());
        }

        @Override
        public void showError(String message) {
            calls.add("error: " + message);
            errorOnUiThread = recordingUiThread != null && recordingUiThread.executing;
        }

        @Override
        public void warnRulesEffectiveDateForOlderSimples() {
            calls.add("warn-older-simples");
        }

        @Override
        public void showExternalSources(ExternalSourcesSnapshot snapshot) {
            calls.add("sources " + snapshot.sources().size() + " " + snapshot.phase());
        }

        @Override
        public void openExternalSourcesDialog() {
            calls.add("open-sources");
            if (failIfDialogOpensBeforeTerminalSnapshot
                    && !calls.contains("sources 3 RESTART_REQUIRED")) {
                throw new IllegalStateException(
                        "A abertura modal bloqueou a entrega do snapshot terminal");
            }
        }

        @Override
        public boolean isExternalSourcesDialogOpen() {
            return externalSourcesDialogOpen;
        }

        @Override
        public boolean confirmExternalSourcesUpdate(ExternalSourcesSnapshot snapshot) {
            calls.add("confirm-update");
            confirmOnUiThread = recordingUiThread != null && recordingUiThread.executing;
            return acceptUpdate;
        }

        @Override
        public void showBasesUpdatedAndInUse(ExternalSourcesSnapshot snapshot) {
            calls.add("bases-updated-in-use");
        }

        @Override
        public void showRestartRequired(ExternalSourcesSnapshot snapshot) {
            calls.add("restart-required");
        }

    }

    @BeforeEach
    void setUp() {
        presenter = new MainPresenter(useCase(), DIRECT_UI_THREAD, Runnable::run);
        presenter.attach(fakeView);
    }

    @Test
    void attachShowsIdle() {
        assertThat(calls).containsExactly("idle");
    }

    @Test
    void inputChosenImportsWithoutValidating(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-minima-invalida.xml", "a.xml");

        presenter.inputChosen(dir);

        assertThat(lastWorkspace).hasSize(1);
        assertThat(lastWorkspace.getFirst().status()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void inputChosenIncludesSubfoldersOnlyWhenRequested(@TempDir Path dir) throws IOException {
        Path subfolder = Files.createDirectory(dir.resolve("sub"));
        copyFixture(dir, "nfe-minima-invalida.xml", "direto.xml");
        copyFixture(subfolder, "nfe-valida-sem-assinatura.xml", "interno.xml");

        presenter.inputChosen(dir);
        assertThat(lastWorkspace).hasSize(1);

        presenter.clearRequested();
        presenter.inputChosen(dir, true);
        assertThat(lastWorkspace).hasSize(2);
    }

    @Test
    void inputChosenAcceptsASingleXmlFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("a.xml");
        copyFixture(dir, "nfe-minima-invalida.xml", file.getFileName().toString());

        presenter.inputChosen(file);

        assertThat(lastWorkspace).hasSize(1);
    }

    @Test
    void scanFailureShowsErrorNotCrash(@TempDir Path dir) {
        presenter.inputChosen(dir.resolve("nao-existe"));

        assertThat(calls).anySatisfy(call -> assertThat(call).startsWith("error: "));
    }

    @Test
    void invalidXmlIsReportedSeparatelyFromDocuments(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("quebrado.xml"), "isto não é XML");

        presenter.inputChosen(dir);

        assertThat(calls).contains("invalidFiles 1");
        assertThat(lastWorkspace).isEmpty();
    }

    @Test
    void validationPopulatesTheGridIncrementally(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida-sem-assinatura.xml", "a.xml");
        presenter.inputChosen(dir);
        presenter.validateRequested();

        assertThat(lastWorkspace.getFirst().status()).isNotEqualTo(DocumentStatus.PENDING);
        assertThat(calls).anySatisfy(call -> assertThat(call).contains("true 0/1"));
    }

    @Test
    void validationUsesTheEffectiveDateChoiceCapturedAtStart(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-crt3-sem-ibscbs.xml", "amostra-julho.xml");
        AtomicBoolean receivedChoice = new AtomicBoolean(true);
        presenter = new MainPresenter(runtime("r1"), DIRECT_UI_THREAD, Runnable::run, null,
                (runtime, source, considerRulesEffectiveDate, token) -> {
                    receivedChoice.set(considerRulesEffectiveDate);
                    return runtime.useCase().validateDocument(
                            source, true, considerRulesEffectiveDate, token);
                });
        presenter.attach(fakeView);
        presenter.inputChosen(dir);

        presenter.validateRequested(false);

        assertThat(receivedChoice).isFalse();
        assertThat(lastWorkspace.getFirst().findings())
                .noneMatch(finding -> "1115".equals(finding.rejectionCode()));
    }

    @Test
    void olderSimplesRequiresWarningBeforeEffectiveDateSimulation(@TempDir Path dir)
            throws IOException {
        copyFixture(dir, "rejeicao/c1115-simples-sem-grupo.xml", "simples-2026.xml");
        presenter.inputChosen(dir);

        presenter.validateRequested(true);

        assertThat(calls).contains("warn-older-simples");
        assertThat(lastWorkspace.getFirst().findings())
                .anyMatch(finding -> "1115".equals(finding.rejectionCode()));
    }

    @Test
    void actualEmissionDateModeDoesNotWarnForOlderSimples(@TempDir Path dir) throws IOException {
        copyFixture(dir, "rejeicao/c1115-simples-sem-grupo.xml", "simples-2026.xml");
        presenter.inputChosen(dir);

        presenter.validateRequested(false);

        assertThat(calls).doesNotContain("warn-older-simples");
        assertThat(lastWorkspace.getFirst().findings())
                .noneMatch(finding -> "1115".equals(finding.rejectionCode()));
    }

    @Test
    void completedDocumentKeepsTheRuntimeBasesCapturedBeforeANewerRuntimeExists(
            @TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida-sem-assinatura.xml", "a.xml");
        ValidationRuntimeFactory factory = new ValidationRuntimeFactory();
        ValidationRuntime r1 = factory.create(useCase(), "schemas-r1", "canal-r1", "tabelas-r1",
                "svrs-r1");
        presenter = new MainPresenter(r1, DIRECT_UI_THREAD,
                Runnable::run);
        presenter.attach(fakeView);

        presenter.inputChosen(dir);
        presenter.validateRequested();

        WorkspaceDocument completedWithR1 = lastWorkspace.getFirst();
        List<?> findingsWithR1 = completedWithR1.findings();
        AtomicReference<ValidationRuntime> publishedRuntime = new AtomicReference<>(r1);
        ValidationRuntime r2 = factory.create(useCase(), "schemas-r2", "canal-r2", "tabelas-r2",
                "svrs-r2");
        publishedRuntime.set(r2);

        assertThat(r2.useCase()).isNotSameAs(r1.useCase());
        assertThat(r2.bases().generation()).isGreaterThan(completedWithR1.runtimeBases().generation());
        assertThat(publishedRuntime.get().bases()).isEqualTo(r2.bases());
        assertThat(lastWorkspace.getFirst().runtimeBases()).isEqualTo(r1.bases());
        assertThat(lastWorkspace.getFirst().findings()).isEqualTo(findingsWithR1);
    }

    @Test
    void workspaceDocumentRejectsIdentityOnIncompleteStatesAndClearsItWhenReturningToPending() {
        RuntimeBases r1 = new RuntimeBases("schemas-r1", "canal-r1", "tabelas-r1", "svrs-r1", 1);
        var document = new br.com.validadorlote.domain.FiscalDocument(Path.of("a.xml"), null, null,
                null, null, "55", "NFe", "3", null, null, false, null, false, List.of());

        assertThatThrownBy(() -> new WorkspaceDocument(document, DocumentStatus.PENDING, List.of(), r1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkspaceDocument(document, DocumentStatus.VALID, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);

        WorkspaceDocument completed = WorkspaceDocument.pending(document)
                .withResult(DocumentStatus.VALID, List.of(), r1);

        assertThat(completed.withStatus(DocumentStatus.PENDING).runtimeBases()).isNull();
        assertThat(completed.withStatus(DocumentStatus.VALIDATING).runtimeBases()).isNull();
    }

    @Test
    void newAnalysisReturnsToIdle(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida.xml", "a.xml");
        presenter.inputChosen(dir);

        presenter.newAnalysisRequested();

        assertThat(calls.getLast()).isEqualTo("idle");
    }

    @Test
    void removeValidKeepsDocumentsThatNeedAttention(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida.xml", "valid.xml");
        copyFixture(dir, "nfe-minima-invalida.xml", "invalid.xml");
        presenter.inputChosen(dir);
        presenter.validateRequested();

        presenter.removeValidRequested();

        assertThat(lastWorkspace).hasSize(1);
        assertThat(lastWorkspace.getFirst().status()).isNotEqualTo(DocumentStatus.VALID);
    }

    @Test
    void cancellationKeepsDocumentsThatWereNotYetValidated(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida.xml", "a.xml");
        var queued = new ArrayList<Runnable>();
        Executor deferredBackground = queued::add;
        var deferredPresenter = new MainPresenter(new ValidationRuntime(useCase(),
                new RuntimeBases("schemas-r1", "canal-r1", "tabelas-r1", "svrs-r1", 1)),
                DIRECT_UI_THREAD, deferredBackground);
        deferredPresenter.attach(fakeView);

        deferredPresenter.inputChosen(dir);
        queued.removeFirst().run();
        deferredPresenter.validateRequested();
        deferredPresenter.cancelRequested();
        assertThat(queued).hasSize(1);
        queued.removeFirst().run();

        assertThat(lastWorkspace).hasSize(1);
        assertThat(lastWorkspace.getFirst().status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(lastWorkspace.getFirst().runtimeBases()).isNull();
    }

    @Test
    void cancellationAfterValidatingReturnsDocumentToPendingWithoutRuntimeBases(
            @TempDir Path dir) throws Exception {
        copyFixture(dir, "nfe-valida.xml", "a.xml");
        CountDownLatch validating = new CountDownLatch(1);
        CountDownLatch continueValidation = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicBoolean importing = new AtomicBoolean(true);
        Executor background = action -> {
            if (importing.getAndSet(false)) {
                action.run();
            } else {
                worker.execute(action);
            }
        };
        try {
            presenter = new MainPresenter(runtime("r1"), DIRECT_UI_THREAD, background, null,
                    (runtime, source, considerRulesEffectiveDate, token) -> {
                        validating.countDown();
                        await(continueValidation);
                        return new DocumentValidationResult(null, List.of());
                    });
            presenter.attach(fakeView);
            presenter.inputChosen(dir);
            presenter.validateRequested();

            await(validating);
            assertThat(lastWorkspace.getFirst().status()).isEqualTo(DocumentStatus.VALIDATING);
            presenter.cancelRequested();
            continueValidation.countDown();
        } finally {
            worker.shutdown();
            worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        }

        assertThat(lastWorkspace.getFirst().status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(lastWorkspace.getFirst().runtimeBases()).isNull();
    }

    @Test
    void validationExceptionAfterValidatingReturnsDocumentToPendingWithoutRuntimeBases(
            @TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida.xml", "a.xml");
        presenter = new MainPresenter(runtime("r1"), DIRECT_UI_THREAD, Runnable::run, null,
                (runtime, source, considerRulesEffectiveDate, token) -> {
                    throw new IllegalStateException("falha forçada");
                });
        presenter.attach(fakeView);
        presenter.inputChosen(dir);

        presenter.validateRequested();

        assertThat(calls).anySatisfy(call -> assertThat(call).contains("falha forçada"));
        assertThat(lastWorkspace.getFirst().status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(lastWorkspace.getFirst().runtimeBases()).isNull();
    }

    @Test
    void validationSchedulingFailureLeavesThePendingDocumentWithoutRuntimeIdentity(
            @TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida.xml", "a.xml");
        AtomicBoolean reject = new AtomicBoolean();
        Executor executor = action -> {
            if (reject.get()) throw new RejectedExecutionException("executor encerrado");
            action.run();
        };
        presenter = new MainPresenter(new ValidationRuntime(useCase(), new RuntimeBases("schemas-r1",
                "canal-r1", "tabelas-r1", "svrs-r1", 1)), DIRECT_UI_THREAD, executor);
        presenter.attach(fakeView);
        presenter.inputChosen(dir);
        reject.set(true);

        presenter.validateRequested();

        assertThat(lastWorkspace.getFirst().status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(lastWorkspace.getFirst().runtimeBases()).isNull();
    }

    @Test
    void externalSourcesAreShownAndManualCheckReportsProgressWithoutAnErrorModal(@TempDir Path dir) {
        ArtifactUpdateAction action = new ArtifactUpdateAction() {
            @Override public ArtifactId artifact() { return ArtifactId.NFE_SCHEMAS; }
            @Override public String channelId() { return "test-schemas-v1"; }
            @Override public br.com.validadorlote.infrastructure.update.ArtifactCheckResult check() {
                return br.com.validadorlote.infrastructure.update.ArtifactCheckResult.upToDate(null);
            }
            @Override public br.com.validadorlote.infrastructure.xml.ArtifactManifest apply(
                    br.com.validadorlote.infrastructure.update.ArtifactUpdateCandidate candidate) {
                throw new AssertionError("Não deveria aplicar sem candidata");
            }
        };
        var state = new ArtifactUpdateStateStore(dir);
        var coordinator = new ArtifactUpdateCoordinator(List.of(action), java.time.Duration.ofHours(24),
                java.time.Clock.systemUTC(), Runnable::run, event -> { }, state);
        var sources = new ExternalSourcesUseCase(coordinator, new SchemaArtifactStore(dir),
                new FiscalTableArtifactStore(dir));
        var sourcePresenter = new MainPresenter(useCase(), DIRECT_UI_THREAD, Runnable::run, sources);
        sourcePresenter.attach(fakeView);

        sourcePresenter.externalSourcesRequested();
        sourcePresenter.checkExternalSourcesRequested();

        assertThat(calls).anySatisfy(call -> assertThat(call).startsWith("sources 3 "));
        assertThat(calls).contains("open-sources");
        assertThat(calls).noneMatch(call -> call.startsWith("error:"));
    }

    @Test
    void doesNotOfferUpdateDuringValidationAndOffersItWhenValidationFinishes(
            @TempDir Path dir) throws IOException {
        configureExternalSources(dir, true);
        startQueuedValidation(dir);

        sourcesPublishUpdateAvailable();

        assertThat(calls).doesNotContain("confirm-update");

        completeQueuedValidation();

        assertThat(calls).filteredOn("confirm-update"::equals).hasSize(1);
    }

    @Test
    void acceptedUpdateStartsApplicationWithoutOpeningTheSharedDialog(@TempDir Path dir) {
        configureExternalSources(dir, false);
        fakeView.acceptUpdate = true;

        sourcesPublishUpdateAvailable();
        recordingUiThread.runDeferredActions();

        assertThat(calls).doesNotContain("open-sources");
        assertThat(calls).filteredOn("confirm-update"::equals).hasSize(1);
        assertThat(schemasAction.applyCalls).isOne();
        assertThat(calls).filteredOn("restart-required"::equals).hasSize(1);
    }

    @Test
    void openSourcesDialogUsesItsOwnFeedbackInsteadOfAnotherConfirmation(
            @TempDir Path dir) {
        configureExternalSources(dir, false);
        fakeView.externalSourcesDialogOpen = true;

        sourcesPublishUpdateAvailable();

        assertThat(calls).doesNotContain("confirm-update");
        assertThat(calls).contains("sources 3 UPDATES_AVAILABLE");
    }

    @Test
    void updatedRuntimeShowsOneInUseFeedbackWithoutRestartPrompt(@TempDir Path dir) {
        schemasAction = new TestUpdateAction(ArtifactId.NFE_SCHEMAS, "test-schemas-v1");
        tablesAction = new TestUpdateAction(ArtifactId.FISCAL_TABLES, "test-tables-v1");
        updateCoordinator = new ArtifactUpdateCoordinator(List.of(schemasAction, tablesAction),
                java.time.Duration.ofHours(24), java.time.Clock.systemUTC(), Runnable::run,
                event -> { }, new ArtifactUpdateStateStore(dir));
        ValidationRuntime r1 = runtime("r1");
        observedSources = new ExternalSourcesUseCase(updateCoordinator,
                new SchemaArtifactStore(dir), new FiscalTableArtifactStore(dir), r1,
                () -> runtime("r2"), Runnable::run);
        recordingUiThread = new RecordingUiThread();
        presenter = new MainPresenter(r1, recordingUiThread, Runnable::run, observedSources);
        presenter.attach(fakeView);
        fakeView.acceptUpdate = true;
        calls.clear();

        sourcesPublishUpdateAvailable();

        assertThat(calls).containsSubsequence("sources 3 APPLYING",
                "sources 3 RELOADING_RUNTIME", "sources 3 UPDATED_AND_IN_USE",
                "bases-updated-in-use");
        assertThat(calls).filteredOn("bases-updated-in-use"::equals).hasSize(1);
        assertThat(calls).doesNotContain("restart-required");
    }

    @Test
    void declinedRevisionIsNotOfferedAgainButANewRevisionIs(@TempDir Path dir) {
        configureExternalSources(dir, false);

        sourcesPublishUpdateAvailable();
        presenter.externalSourcesRequested();

        assertThat(calls).filteredOn("confirm-update"::equals).hasSize(1);

        ValidationLease lease = acquireObservedLease();
        observedSources.validationFinished(lease);

        assertThat(calls).filteredOn("confirm-update"::equals).hasSize(2);
    }

    @Test
    void cancellationAlsoReleasesThePendingUpdatePromptThroughUiThread(
            @TempDir Path dir) throws IOException {
        configureExternalSources(dir, true);
        startQueuedValidation(dir);
        sourcesPublishUpdateAvailable();

        presenter.cancelRequested();
        completeQueuedValidation();

        assertThat(recordingUiThread.executions).isPositive();
        assertThat(calls).contains("confirm-update");
        assertThat(fakeView.confirmOnUiThread).isTrue();
    }

    @Test
    void staleAvailableSnapshotDoesNotPromptAfterValidationSnapshot(@TempDir Path dir) {
        schemasAction = new TestUpdateAction(ArtifactId.NFE_SCHEMAS, "test-schemas-v1");
        tablesAction = new TestUpdateAction(ArtifactId.FISCAL_TABLES, "test-tables-v1");
        var state = new ArtifactUpdateStateStore(dir);
        updateCoordinator = new ArtifactUpdateCoordinator(List.of(schemasAction, tablesAction),
                java.time.Duration.ofHours(24), java.time.Clock.systemUTC(), Runnable::run,
                event -> { }, state);
        observedSources = new ExternalSourcesUseCase(updateCoordinator,
                new SchemaArtifactStore(dir), new FiscalTableArtifactStore(dir));
        QueuedUiThread queuedUi = new QueuedUiThread();
        presenter = new MainPresenter(useCase(), queuedUi, Runnable::run, observedSources);
        presenter.attach(fakeView);
        queuedUi.runAll();
        calls.clear();

        sourcesPublishUpdateAvailable();
        acquireObservedLease();
        queuedUi.runLast();
        queuedUi.runAll();

        assertThat(calls).doesNotContain("confirm-update");
    }

    @Test
    void multipleCandidatesApplyWithoutOpeningTheSharedDialog(@TempDir Path dir) {
        configureExternalSources(dir, false);
        fakeView.acceptUpdate = true;
        tablesAction.checkResult = ArtifactCheckResult.available(
                new ArtifactUpdateCandidate(ArtifactId.FISCAL_TABLES, "IT-1.61",
                        "https://dfe-portal.svrs.rs.gov.br/",
                        Instant.parse("2026-07-30T12:00:00Z"), "1".repeat(64),
                        "Tabela preparada"),
                "Tabela preparada");

        sourcesPublishUpdateAvailable();
        recordingUiThread.runDeferredActions();

        assertThat(calls).doesNotContain("open-sources");
        assertThat(schemasAction.applyCalls).isOne();
        assertThat(tablesAction.applyCalls).isOne();
    }

    @Test
    void applyingDoesNotOpenAClosedModal(@TempDir Path dir) {
        configureExternalSources(dir, false);
        fakeView.acceptUpdate = true;
        sourcesPublishUpdateAvailable();

        assertThat(calls).containsSubsequence(
                "sources 3 APPLYING",
                "sources 3 RESTART_REQUIRED");
        assertThat(calls).doesNotContain("open-sources");
    }

    @Test
    void terminalFailureAfterActivationStillShowsRestartRequiredThroughUiThread(@TempDir Path dir) {
        schemasAction = new TestUpdateAction(ArtifactId.NFE_SCHEMAS, "test-schemas-v1");
        tablesAction = new TestUpdateAction(ArtifactId.FISCAL_TABLES, "test-tables-v1");
        var state = new ArtifactUpdateStateStore(dir);
        updateCoordinator = new ArtifactUpdateCoordinator(List.of(schemasAction, tablesAction),
                java.time.Duration.ofHours(24), java.time.Clock.systemUTC(), Runnable::run,
                event -> {
                    if (event.status() == ArtifactUpdateEvent.Status.APPLIED) {
                        throw ArtifactUpdateException.localStorage(
                                "Não foi possível publicar o resultado", null);
                    }
                }, state);
        observedSources = new ExternalSourcesUseCase(updateCoordinator,
                new SchemaArtifactStore(dir), new FiscalTableArtifactStore(dir));
        recordingUiThread = new RecordingUiThread();
        presenter = new MainPresenter(useCase(), recordingUiThread, Runnable::run, observedSources);
        presenter.attach(fakeView);
        fakeView.acceptUpdate = true;
        calls.clear();

        sourcesPublishUpdateAvailable();

        assertThat(schemasAction.applyCalls).isOne();
        assertThat(calls).contains("restart-required");
        assertThat(calls).anySatisfy(call ->
                assertThat(call).isEqualTo("sources 3 RESTART_REQUIRED"));
        assertThat(recordingUiThread.executions).isPositive();
    }

    @Test
    void manualApplicationSchedulingFailureIsShownOnUiThreadWithoutANewPrompt(
            @TempDir Path dir) {
        schemasAction = new TestUpdateAction(ArtifactId.NFE_SCHEMAS, "test-schemas-v1");
        tablesAction = new TestUpdateAction(ArtifactId.FISCAL_TABLES, "test-tables-v1");
        AtomicBoolean reject = new AtomicBoolean();
        Executor updateBackground = action -> {
            if (reject.get()) {
                throw new RejectedExecutionException("executor encerrado");
            }
            action.run();
        };
        updateCoordinator = new ArtifactUpdateCoordinator(List.of(schemasAction, tablesAction),
                java.time.Duration.ofHours(24), java.time.Clock.systemUTC(), updateBackground,
                event -> { }, new ArtifactUpdateStateStore(dir));
        observedSources = new ExternalSourcesUseCase(updateCoordinator,
                new SchemaArtifactStore(dir), new FiscalTableArtifactStore(dir));
        recordingUiThread = new RecordingUiThread();
        presenter = new MainPresenter(useCase(), recordingUiThread, Runnable::run, observedSources);
        presenter.attach(fakeView);
        sourcesPublishUpdateAvailable();
        calls.clear();
        reject.set(true);

        presenter.applyExternalSourcesRequested();

        assertThat(calls).contains(
                "error: Não foi possível iniciar a atualização das bases: executor encerrado");
        assertThat(calls).doesNotContain("confirm-update");
        assertThat(fakeView.errorOnUiThread).isTrue();
        assertThat(schemasAction.applyCalls).isZero();
        assertThat(observedSources.snapshot().phase())
                .isEqualTo(ExternalSourcesPhase.UPDATES_AVAILABLE);
        acquireObservedLease();
    }

    @Test
    void automaticApplicationSchedulingFailureIsShownOnceWithoutPromptLoop(
            @TempDir Path dir) {
        schemasAction = new TestUpdateAction(ArtifactId.NFE_SCHEMAS, "test-schemas-v1");
        tablesAction = new TestUpdateAction(ArtifactId.FISCAL_TABLES, "test-tables-v1");
        AtomicInteger submissions = new AtomicInteger();
        Executor rejectSecondSubmission = action -> {
            if (submissions.incrementAndGet() == 2) {
                throw new RejectedExecutionException("executor encerrado");
            }
            action.run();
        };
        updateCoordinator = new ArtifactUpdateCoordinator(List.of(schemasAction, tablesAction),
                java.time.Duration.ofHours(24), java.time.Clock.systemUTC(),
                rejectSecondSubmission, event -> { }, new ArtifactUpdateStateStore(dir));
        observedSources = new ExternalSourcesUseCase(updateCoordinator,
                new SchemaArtifactStore(dir), new FiscalTableArtifactStore(dir));
        recordingUiThread = new RecordingUiThread();
        presenter = new MainPresenter(useCase(), recordingUiThread, Runnable::run, observedSources);
        presenter.attach(fakeView);
        fakeView.acceptUpdate = true;
        calls.clear();

        sourcesPublishUpdateAvailable();

        assertThat(calls).filteredOn("confirm-update"::equals).hasSize(1);
        assertThat(calls).filteredOn(call -> call.equals(
                        "error: Não foi possível iniciar a atualização das bases: "
                                + "executor encerrado"))
                .hasSize(1);
        assertThat(fakeView.errorOnUiThread).isTrue();
        assertThat(schemasAction.applyCalls).isZero();
        assertThat(observedSources.snapshot().phase())
                .isEqualTo(ExternalSourcesPhase.UPDATES_AVAILABLE);
        acquireObservedLease();
    }

    @Test
    void validationRequestedWhileActivationIsReservedWaitsWithoutStartingAWorker(
            @TempDir Path dir) throws IOException {
        schemasAction = new TestUpdateAction(ArtifactId.NFE_SCHEMAS, "test-schemas-v1");
        tablesAction = new TestUpdateAction(ArtifactId.FISCAL_TABLES, "test-tables-v1");
        List<Runnable> updateQueue = new ArrayList<>();
        updateCoordinator = new ArtifactUpdateCoordinator(List.of(schemasAction, tablesAction),
                java.time.Duration.ofHours(24), java.time.Clock.systemUTC(), updateQueue::add,
                event -> { }, new ArtifactUpdateStateStore(dir));
        observedSources = new ExternalSourcesUseCase(updateCoordinator,
                new SchemaArtifactStore(dir), new FiscalTableArtifactStore(dir));
        recordingUiThread = new RecordingUiThread();
        queuedBackground = new ArrayList<>();
        presenter = new MainPresenter(useCase(), recordingUiThread, queuedBackground::add,
                observedSources);
        presenter.attach(fakeView);
        calls.clear();

        copyFixture(dir, "nfe-valida-sem-assinatura.xml", "pendente.xml");
        presenter.inputChosen(dir);
        queuedBackground.removeFirst().run();
        schemasAction.checkResult = ArtifactCheckResult.available(
                new ArtifactUpdateCandidate(ArtifactId.NFE_SCHEMAS, "010e_v1.03",
                        "https://dfe-portal.svrs.rs.gov.br/NFe/Documentos",
                        Instant.parse("2026-07-30T12:00:00Z"), "0".repeat(64),
                        "Schemas preparados"),
                "Schemas preparados");
        assertThat(updateCoordinator.checkNow()).isTrue();
        updateQueue.removeFirst().run();

        assertThat(observedSources.applyAvailable()).isTrue();
        presenter.validateRequested();

        assertThat(queuedBackground).isEmpty();
        assertThat(observedSources.snapshot().validationActive()).isFalse();
        assertThat(calls).contains(
                "error: Aguarde a atualização das bases terminar antes de validar o lote.");
        assertThat(fakeView.errorOnUiThread).isTrue();

        updateQueue.removeFirst().run();
        assertThat(schemasAction.applyCalls).isOne();
    }

    @Test
    void validationSchedulingFailureReleasesTheExternalSourcesGate(@TempDir Path dir)
            throws IOException {
        schemasAction = new TestUpdateAction(ArtifactId.NFE_SCHEMAS, "test-schemas-v1");
        tablesAction = new TestUpdateAction(ArtifactId.FISCAL_TABLES, "test-tables-v1");
        updateCoordinator = new ArtifactUpdateCoordinator(List.of(schemasAction, tablesAction),
                java.time.Duration.ofHours(24), java.time.Clock.systemUTC(), Runnable::run,
                event -> { }, new ArtifactUpdateStateStore(dir));
        observedSources = new ExternalSourcesUseCase(updateCoordinator,
                new SchemaArtifactStore(dir), new FiscalTableArtifactStore(dir));
        recordingUiThread = new RecordingUiThread();
        AtomicBoolean reject = new AtomicBoolean();
        Executor rejectingBackground = action -> {
            if (reject.get()) {
                throw new RejectedExecutionException("executor encerrado");
            }
            action.run();
        };
        presenter = new MainPresenter(useCase(), recordingUiThread, rejectingBackground,
                observedSources);
        presenter.attach(fakeView);
        calls.clear();
        copyFixture(dir, "nfe-valida-sem-assinatura.xml", "pendente.xml");
        presenter.inputChosen(dir);
        reject.set(true);

        presenter.validateRequested();

        assertThat(observedSources.snapshot().validationActive()).isFalse();
        assertThat(calls).contains(
                "error: Não foi possível iniciar a validação: executor encerrado");
        assertThat(fakeView.errorOnUiThread).isTrue();
        assertThat(calls.getLast()).contains("workspace 1 false");
    }

    private void configureExternalSources(Path dir, boolean deferValidation) {
        schemasAction = new TestUpdateAction(ArtifactId.NFE_SCHEMAS, "test-schemas-v1");
        tablesAction = new TestUpdateAction(ArtifactId.FISCAL_TABLES, "test-tables-v1");
        var state = new ArtifactUpdateStateStore(dir);
        updateCoordinator = new ArtifactUpdateCoordinator(List.of(schemasAction, tablesAction),
                java.time.Duration.ofHours(24), java.time.Clock.systemUTC(), Runnable::run,
                event -> { }, state);
        observedSources = new ExternalSourcesUseCase(updateCoordinator,
                new SchemaArtifactStore(dir), new FiscalTableArtifactStore(dir));
        recordingUiThread = new RecordingUiThread();
        queuedBackground = new ArrayList<>();
        Executor validationBackground = deferValidation ? queuedBackground::add : Runnable::run;
        presenter = new MainPresenter(useCase(), recordingUiThread, validationBackground,
                observedSources);
        presenter.attach(fakeView);
        fakeView.confirmOnUiThread = false;
        calls.clear();
    }

    private void startQueuedValidation(Path dir) throws IOException {
        copyFixture(dir, "nfe-valida-sem-assinatura.xml", "pendente.xml");
        presenter.inputChosen(dir);
        queuedBackground.removeFirst().run();
        presenter.validateRequested();
        assertThat(queuedBackground).hasSize(1);
    }

    private void completeQueuedValidation() {
        queuedBackground.removeFirst().run();
    }

    private void sourcesPublishUpdateAvailable() {
        schemasAction.checkResult = ArtifactCheckResult.available(
                new ArtifactUpdateCandidate(ArtifactId.NFE_SCHEMAS, "010e_v1.03",
                        "https://dfe-portal.svrs.rs.gov.br/NFe/Documentos",
                        Instant.parse("2026-07-30T12:00:00Z"), "0".repeat(64),
                        "Schemas preparados"),
                "Schemas preparados");
        assertThat(updateCoordinator.checkNow()).isTrue();
    }

    private ValidationLease acquireObservedLease() {
        return observedSources.tryAcquireValidationLease(runtime("gate")).orElseThrow();
    }

    private final class RecordingUiThread implements UiThread {

        private int executions;
        private boolean executing;
        private final Deque<Runnable> deferredActions = new ArrayDeque<>();

        @Override
        public void execute(Runnable action) {
            executions++;
            boolean previous = executing;
            executing = true;
            try {
                action.run();
            } finally {
                executing = previous;
            }
        }

        @Override
        public void executeLater(Runnable action) {
            deferredActions.addLast(action);
        }

        private void runDeferredActions() {
            while (!deferredActions.isEmpty()) {
                execute(deferredActions.removeFirst());
            }
        }
    }

    private static final class QueuedUiThread implements UiThread {

        private final Deque<Runnable> actions = new ArrayDeque<>();

        @Override
        public void execute(Runnable action) {
            actions.addLast(action);
        }

        @Override
        public void executeLater(Runnable action) {
            actions.addLast(action);
        }

        private void runLast() {
            actions.removeLast().run();
        }

        private void runAll() {
            while (!actions.isEmpty()) {
                actions.removeFirst().run();
            }
        }
    }

    private static final class TestUpdateAction implements ArtifactUpdateAction {

        private final ArtifactId artifact;
        private final String channelId;
        private ArtifactCheckResult checkResult = ArtifactCheckResult.upToDate("Base atual");
        private int applyCalls;

        private TestUpdateAction(ArtifactId artifact, String channelId) {
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
            return checkResult;
        }

        @Override
        public ArtifactManifest apply(ArtifactUpdateCandidate candidate) {
            applyCalls++;
            Instant now = Instant.parse("2026-07-30T12:00:00Z");
            return new ArtifactManifest(candidate.artifact(), candidate.version(),
                    candidate.sourceUrl(), candidate.publishedAt(), candidate.sha256(),
                    now, now, "APPLIED");
        }
    }

    private static ValidateBatchUseCase useCase() {
        var translator = new XsdErrorTranslator();
        return new ValidateBatchUseCase(new FolderScanner(), new XmlMetadataParser(),
                new TaxGroupExtractor(), new SchemaValidatorEngine(translator),
                new RuleEngine(FiscalTables.load()), new RootCauseGrouper(), translator,
                new CsvExporter(), "motor-teste");
    }

    private static ValidationRuntime runtime(String generationName) {
        return new ValidationRuntime(useCase(), new RuntimeBases("schemas-" + generationName,
                "canal-" + generationName, "tabelas-" + generationName,
                "svrs-" + generationName, 1));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new AssertionError("A validação não alcançou o ponto de sincronização");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Thread interrompida", failure);
        }
    }

    private static void copyFixture(Path dir, String fixture, String target) throws IOException {
        Files.copy(Path.of("src/test/resources/fixtures", fixture), dir.resolve(target));
    }

}
