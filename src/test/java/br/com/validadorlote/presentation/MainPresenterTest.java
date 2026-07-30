package br.com.validadorlote.presentation;

import br.com.validadorlote.application.ValidateBatchUseCase;
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

import static org.assertj.core.api.Assertions.assertThat;

class MainPresenterTest {

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
        }

        @Override
        public void showExternalSources(ExternalSourcesSnapshot snapshot) {
            calls.add("sources " + snapshot.sources().size() + " " + snapshot.phase());
        }

        @Override
        public void openExternalSourcesDialog() {
            calls.add("open-sources");
        }

        @Override
        public boolean confirmExternalSourcesUpdate(ExternalSourcesSnapshot snapshot) {
            calls.add("confirm-update");
            confirmOnUiThread = recordingUiThread != null && recordingUiThread.executing;
            return acceptUpdate;
        }

        @Override
        public void showRestartRequired(ExternalSourcesSnapshot snapshot) {
            calls.add("restart-required");
        }

    }

    @BeforeEach
    void setUp() {
        presenter = new MainPresenter(useCase(), Runnable::run, Runnable::run);
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
        var deferredPresenter = new MainPresenter(useCase(), Runnable::run, deferredBackground);
        deferredPresenter.attach(fakeView);

        deferredPresenter.inputChosen(dir);
        queued.removeFirst().run();
        deferredPresenter.validateRequested();
        deferredPresenter.cancelRequested();
        assertThat(queued).hasSize(1);
        queued.removeFirst().run();

        assertThat(lastWorkspace).hasSize(1);
        assertThat(lastWorkspace.getFirst().status()).isEqualTo(DocumentStatus.PENDING);
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
        var sourcePresenter = new MainPresenter(useCase(), Runnable::run, Runnable::run, sources);
        sourcePresenter.attach(fakeView);

        sourcePresenter.externalSourcesRequested();
        sourcePresenter.checkExternalSourcesRequested();

        assertThat(calls).anySatisfy(call -> assertThat(call).startsWith("sources 2 "));
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
    void acceptedUpdateOpensTheSharedDialogAndStartsApplication(@TempDir Path dir) {
        configureExternalSources(dir, false);
        fakeView.acceptUpdate = true;

        sourcesPublishUpdateAvailable();

        assertThat(calls).containsSubsequence("confirm-update", "open-sources");
        assertThat(calls).filteredOn("confirm-update"::equals).hasSize(1);
        assertThat(schemasAction.applyCalls).isOne();
        assertThat(calls).filteredOn("restart-required"::equals).hasSize(1);
    }

    @Test
    void declinedRevisionIsNotOfferedAgainButANewRevisionIs(@TempDir Path dir) {
        configureExternalSources(dir, false);

        sourcesPublishUpdateAvailable();
        presenter.externalSourcesRequested();

        assertThat(calls).filteredOn("confirm-update"::equals).hasSize(1);

        observedSources.validationStateChanged(true);
        observedSources.validationStateChanged(false);

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
        observedSources.validationStateChanged(true);
        queuedUi.runLast();
        queuedUi.runAll();

        assertThat(calls).doesNotContain("confirm-update");
    }

    @Test
    void multipleCandidatesOpenSharedDialogOnlyOnceWhileApplying(@TempDir Path dir) {
        configureExternalSources(dir, false);
        fakeView.acceptUpdate = true;
        tablesAction.checkResult = ArtifactCheckResult.available(
                new ArtifactUpdateCandidate(ArtifactId.FISCAL_TABLES, "IT-1.61",
                        "https://dfe-portal.svrs.rs.gov.br/",
                        Instant.parse("2026-07-30T12:00:00Z"), "1".repeat(64),
                        "Tabela preparada"),
                "Tabela preparada");

        sourcesPublishUpdateAvailable();

        assertThat(calls).filteredOn("open-sources"::equals).hasSize(1);
        assertThat(schemasAction.applyCalls).isOne();
        assertThat(tablesAction.applyCalls).isOne();
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

    private final class RecordingUiThread implements UiThread {

        private int executions;
        private boolean executing;

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
    }

    private static final class QueuedUiThread implements UiThread {

        private final Deque<Runnable> actions = new ArrayDeque<>();

        @Override
        public void execute(Runnable action) {
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

    private static void copyFixture(Path dir, String fixture, String target) throws IOException {
        Files.copy(Path.of("src/test/resources/fixtures", fixture), dir.resolve(target));
    }

}
