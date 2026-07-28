package br.com.validadorlote.presentation;

import br.com.validadorlote.application.BatchRequest;
import br.com.validadorlote.application.CancellationToken;
import br.com.validadorlote.application.ValidateBatchUseCase;
import br.com.validadorlote.domain.BatchReport;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Dispara análises e exportações fora da UI e publica seus estados na tela principal. */
public final class MainPresenter {

    private final ValidateBatchUseCase useCase;
    private final UiThread uiThread;
    private final Executor background;

    private volatile MainView view;
    private volatile BatchReport lastReport;
    private volatile CancellationToken currentToken = new CancellationToken();
    private volatile boolean preEmissionMode = true;

    public MainPresenter(ValidateBatchUseCase useCase, UiThread uiThread, Executor background) {
        this.useCase = Objects.requireNonNull(useCase);
        this.uiThread = Objects.requireNonNull(uiThread);
        this.background = Objects.requireNonNull(background);
    }

    /** Liga a view e a coloca no estado inicial. */
    public void attach(MainView view) {
        this.view = Objects.requireNonNull(view);
        view.showIdle();
    }

    /** Inicia uma análise da pasta escolhida. */
    public void folderChosen(Path folder) {
        MainView attachedView = requireView();
        CancellationToken token = new CancellationToken();
        currentToken = token;
        boolean mode = preEmissionMode;
        attachedView.showRunning(0, 0);

        background.execute(() -> runBatch(folder, mode, token));
    }

    /** Solicita o cancelamento cooperativo da análise corrente. */
    public void cancelRequested() {
        currentToken.cancel();
    }

    /** Reagrupa o último relatório, sem reler os XMLs. */
    public void preEmissionToggled(boolean on) {
        preEmissionMode = on;
        BatchReport report = lastReport;
        if (report != null) {
            BatchReport regrouped = useCase.regroup(report, on);
            lastReport = regrouped;
            requireView().showResults(regrouped);
        }
    }

    /** Exporta o último relatório em background. */
    public void exportRequested(Path targetFolder) {
        BatchReport report = lastReport;
        if (report == null) {
            requireView().showExportError("Nenhuma análise para exportar.");
            return;
        }
        background.execute(() -> export(report, targetFolder));
    }

    /** Descarta o resultado visível e invalida qualquer análise ainda em curso. */
    public void newAnalysisRequested() {
        currentToken.cancel();
        currentToken = new CancellationToken();
        lastReport = null;
        requireView().showIdle();
    }

    private void runBatch(Path folder, boolean mode, CancellationToken token) {
        try {
            BatchReport report = useCase.execute(new BatchRequest(folder, mode),
                    (processed, total) -> showProgress(token, processed, total), token);
            if (token != currentToken) return;

            lastReport = report;
            uiThread.execute(() -> {
                if (token == currentToken) requireView().showResults(report);
            });
        } catch (RuntimeException e) {
            if (token != currentToken) return;
            uiThread.execute(() -> {
                if (token == currentToken) requireView().showError(messageFor(e));
            });
        }
    }

    private void showProgress(CancellationToken token, int processed, int total) {
        uiThread.execute(() -> {
            if (token == currentToken) requireView().showRunning(processed, total);
        });
    }

    private void export(BatchReport report, Path targetFolder) {
        try {
            useCase.exportCsv(report, targetFolder);
            uiThread.execute(() -> requireView().showExportSuccess(targetFolder));
        } catch (Exception e) {
            uiThread.execute(() -> requireView().showExportError(
                    "Não foi possível gravar o CSV: " + messageFor(e)));
        }
    }

    private MainView requireView() {
        return Objects.requireNonNull(view, "A view deve ser conectada antes do uso.");
    }

    private static String messageFor(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "erro desconhecido" : message;
    }
}
