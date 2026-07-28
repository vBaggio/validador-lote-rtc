package br.com.validadorlote.presentation;

import br.com.validadorlote.domain.BatchReport;

import java.nio.file.Path;

/** Contrato passivo e independente de toolkit da tela principal. */
public interface MainView {
    void showIdle();

    void showRunning(int processed, int total);

    void showResults(BatchReport report);

    void showError(String message);

    void showExportSuccess(Path folder);

    void showExportError(String message);
}
