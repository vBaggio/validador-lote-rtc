package br.com.validadorlote.presentation;

import br.com.validadorlote.application.ExternalSourcesSnapshot;

import java.nio.file.Path;
import java.util.List;

/** Contrato passivo e independente de toolkit da tela principal. */
public interface MainView {
    void showIdle();

    void showWorkspace(List<WorkspaceDocument> documents, boolean validating, int processed, int total);

    void showInvalidFiles(List<Path> files);

    void showError(String message);

    void showExternalSources(ExternalSourcesSnapshot snapshot);

    void openExternalSourcesDialog();

    /** Informa se a janela de bases já está aberta e oferecendo o mesmo feedback ao usuário. */
    default boolean isExternalSourcesDialogOpen() {
        return false;
    }

    default boolean isModalDialogOpen() {
        return isExternalSourcesDialogOpen();
    }

    boolean confirmExternalSourcesUpdate(ExternalSourcesSnapshot snapshot);

    void showBasesUpdatedAndInUse(ExternalSourcesSnapshot snapshot);

    void showRestartRequired(ExternalSourcesSnapshot snapshot);
}
