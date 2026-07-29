package br.com.validadorlote.presentation;

import br.com.validadorlote.application.ValidateBatchUseCase;
import br.com.validadorlote.domain.RootCauseGrouper;
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
    private List<WorkspaceDocument> lastWorkspace = List.of();
    private MainPresenter presenter;

    private final MainView fakeView = new MainView() {
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
