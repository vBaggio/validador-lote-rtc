package br.com.validadorlote.presentation;

import br.com.validadorlote.application.ValidateBatchUseCase;
import br.com.validadorlote.domain.BatchReport;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.RootCauseGrouper;
import br.com.validadorlote.domain.Severity;
import br.com.validadorlote.infrastructure.csv.CsvExporter;
import br.com.validadorlote.infrastructure.fs.FolderScanner;
import br.com.validadorlote.infrastructure.rules.RuleEngine;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import br.com.validadorlote.infrastructure.xml.XsdErrorTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class MainPresenterTest {

    private final List<String> calls = new ArrayList<>();
    private BatchReport lastResults;
    private MainPresenter presenter;

    private final MainView fakeView = new MainView() {
        @Override
        public void showIdle() {
            calls.add("idle");
        }

        @Override
        public void showRunning(int processed, int total) {
            calls.add("running " + processed + "/" + total);
        }

        @Override
        public void showResults(BatchReport report) {
            calls.add("results");
            lastResults = report;
        }

        @Override
        public void showError(String message) {
            calls.add("error: " + message);
        }

        @Override
        public void showExportSuccess(Path folder) {
            calls.add("exportOk");
        }

        @Override
        public void showExportError(String message) {
            calls.add("exportErr");
        }
    };

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
    void folderChosenRunsBatchAndShowsResults(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-minima-invalida.xml", "a.xml");

        presenter.folderChosen(dir);

        assertThat(calls).contains("results");
        assertThat(lastResults.documentsScanned()).isEqualTo(1);
    }

    @Test
    void scanFailureShowsErrorNotCrash(@TempDir Path dir) {
        presenter.folderChosen(dir.resolve("nao-existe"));

        assertThat(calls).anySatisfy(call -> assertThat(call).startsWith("error: "));
    }

    @Test
    void toggleRegroupsLastReportWithoutRereadingXml(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida-sem-assinatura.xml", "a.xml");
        presenter.folderChosen(dir);
        int resultsBefore = (int) calls.stream().filter("results"::equals).count();
        assertThat(signatureSeverity(lastResults)).isEqualTo(Severity.INFO);
        Files.delete(dir.resolve("a.xml"));

        presenter.preEmissionToggled(false);

        assertThat(calls.stream().filter("results"::equals).count()).isEqualTo(resultsBefore + 1);
        assertThat(signatureSeverity(lastResults)).isEqualTo(Severity.REJECTION);
    }

    @Test
    void exportBeforeAnyRunReportsError(@TempDir Path out) {
        presenter.exportRequested(out);

        assertThat(calls).contains("exportErr");
    }

    @Test
    void exportAfterRunSucceeds(@TempDir Path dir, @TempDir Path out) throws IOException {
        copyFixture(dir, "nfe-minima-invalida.xml", "a.xml");
        presenter.folderChosen(dir);

        presenter.exportRequested(out);

        assertThat(calls).contains("exportOk");
        assertThat(out.resolve("causas-raiz.csv")).exists();
    }

    @Test
    void exportFailureIsShownInTheView(@TempDir Path dir, @TempDir Path out) throws IOException {
        copyFixture(dir, "nfe-minima-invalida.xml", "a.xml");
        presenter.folderChosen(dir);
        Path fileInsteadOfFolder = Files.createFile(out.resolve("destino.csv"));

        presenter.exportRequested(fileInsteadOfFolder);

        assertThat(calls).contains("exportErr");
    }

    @Test
    void newAnalysisReturnsToIdle(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida.xml", "a.xml");
        presenter.folderChosen(dir);

        presenter.newAnalysisRequested();

        assertThat(calls.getLast()).isEqualTo("idle");
    }

    @Test
    void cancellationPublishesThePartialReport(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida.xml", "a.xml");
        var queued = new ArrayList<Runnable>();
        Executor deferredBackground = queued::add;
        var deferredPresenter = new MainPresenter(useCase(), Runnable::run, deferredBackground);
        deferredPresenter.attach(fakeView);

        deferredPresenter.folderChosen(dir);
        deferredPresenter.cancelRequested();
        assertThat(queued).hasSize(1);
        queued.getFirst().run();

        assertThat(lastResults).isNotNull();
        assertThat(lastResults.cancelled()).isTrue();
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

    private static Severity signatureSeverity(BatchReport report) {
        return report.rootCauses().stream()
                .filter(cause -> cause.key().kind() == FindingKind.SIGNATURE_MISSING)
                .findFirst().orElseThrow().findings().getFirst().severity();
    }
}
