package br.com.validadorlote.presentation;

import br.com.validadorlote.application.ExternalSourceStatus;

import java.nio.file.Path;
import java.util.List;

/** Contrato passivo e independente de toolkit da tela principal. */
public interface MainView {
    void showIdle();

    void showWorkspace(List<WorkspaceDocument> documents, boolean validating, int processed, int total);

    void showInvalidFiles(List<Path> files);

    void showError(String message);

    void showExternalSources(List<ExternalSourceStatus> sources, boolean checking);

}
