package br.com.validadorlote.presentation;

import br.com.validadorlote.application.CancellationToken;
import br.com.validadorlote.application.DocumentValidationResult;
import br.com.validadorlote.application.ExternalSourcesPhase;
import br.com.validadorlote.application.ExternalSourcesSnapshot;
import br.com.validadorlote.application.ExternalSourcesUseCase;
import br.com.validadorlote.application.ImportedBatch;
import br.com.validadorlote.application.RuntimeBases;
import br.com.validadorlote.application.ValidationRuntime;
import br.com.validadorlote.application.ValidationLease;
import br.com.validadorlote.application.ValidateBatchUseCase;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/** Coordena o lote de trabalho: importar primeiro e validar sob solicitação do usuário. */
public final class MainPresenter {

    private final ValidationRuntime runtime;
    private final DocumentValidationRunner validationRunner;
    private final UiThread uiThread;
    private final Executor background;
    private final ExternalSourcesUseCase externalSources;
    private final Object workspaceLock = new Object();
    private final List<WorkspaceDocument> workspace = new ArrayList<>();

    private volatile MainView view;
    private volatile CancellationToken currentToken = new CancellationToken();
    private volatile boolean validating;
    private volatile int processed;
    private volatile int total;
    private long workspaceGeneration;
    private long lastOfferedExternalSourcesRevision = -1;
    private long latestExternalSourcesRevision = -1;
    private boolean externalSourcesApplicationRequested;
    private boolean inUseFeedbackShown;
    private boolean restartRequiredFeedbackShown;
    private int pendingImports;
    private boolean modalClosedWaitingForImport;

    private static final String ACTIVATION_IN_PROGRESS =
            "Aguarde a atualização das bases terminar antes de validar o lote.";
    private static final String APPLICATION_START_FAILED =
            "Não foi possível iniciar a atualização das bases: ";

    public MainPresenter(ValidateBatchUseCase useCase, UiThread uiThread, Executor background) {
        this(new ValidationRuntime(useCase, RuntimeBases.legacy()), uiThread, background, null);
    }

    public MainPresenter(ValidateBatchUseCase useCase, UiThread uiThread, Executor background,
            ExternalSourcesUseCase externalSources) {
        this(new ValidationRuntime(useCase, RuntimeBases.legacy()), uiThread, background,
                externalSources);
    }

    public MainPresenter(ValidationRuntime runtime, UiThread uiThread, Executor background) {
        this(runtime, uiThread, background, null);
    }

    public MainPresenter(ValidationRuntime runtime, UiThread uiThread, Executor background,
            ExternalSourcesUseCase externalSources) {
        this(runtime, uiThread, background, externalSources,
                (validationRuntime, source, token) -> validationRuntime.useCase()
                        .validateDocument(source, true, token));
    }

    MainPresenter(ValidationRuntime runtime, UiThread uiThread, Executor background,
            ExternalSourcesUseCase externalSources, DocumentValidationRunner validationRunner) {
        this.runtime = Objects.requireNonNull(runtime);
        this.uiThread = Objects.requireNonNull(uiThread);
        this.background = Objects.requireNonNull(background);
        this.externalSources = externalSources;
        this.validationRunner = Objects.requireNonNull(validationRunner);
    }

    /** Liga a view e a coloca no estado inicial. */
    public void attach(MainView view) {
        this.view = Objects.requireNonNull(view);
        view.showIdle();
        if (externalSources != null) {
            externalSources.observe(snapshot ->
                    uiThread.execute(() -> publishExternalSources(snapshot)));
            ExternalSourcesSnapshot initial = externalSources.snapshot();
            uiThread.execute(() -> publishExternalSources(initial));
        }
    }

    /** Importa metadados seguros para a grade, sem executar validação fiscal ou schema. */
    public void inputChosen(Path input) {
        inputChosen(input, false);
    }

    /** Importa a entrada e inclui subpastas somente quando solicitado pelo usuário. */
    public void inputChosen(Path input, boolean includeSubfolders) {
        if (validating) return;
        final long generation;
        synchronized (workspaceLock) {
            generation = workspaceGeneration;
            pendingImports++;
        }
        try {
            background.execute(() -> importInput(input, includeSubfolders, generation));
        } catch (RuntimeException failure) {
            synchronized (workspaceLock) {
                pendingImports--;
            }
            uiThread.execute(() -> requireView().showError(messageFor(failure)));
        }
    }

    /** Valida somente os documentos que ainda aguardam validação. */
    public void validateRequested() {
        final List<Path> pending;
        final CancellationToken token;
        final ValidationLease lease;
        synchronized (workspaceLock) {
            if (validating) return;
            pending = workspace.stream().filter(document -> document.status() == DocumentStatus.PENDING)
                    .sorted(WorkspaceDocumentOrder.DISPLAY)
                    .map(document -> document.document().source()).toList();
            if (pending.isEmpty()) {
                publishWorkspace();
                return;
            }
            if (externalSources != null) {
                Optional<ValidationLease> acquired =
                        externalSources.tryAcquireValidationLease(runtime);
                if (acquired.isEmpty()) {
                    uiThread.execute(() -> requireView().showError(ACTIVATION_IN_PROGRESS));
                    return;
                }
                lease = acquired.orElseThrow();
            } else {
                lease = null;
            }
            validating = true;
            processed = 0;
            total = pending.size();
            token = new CancellationToken();
            currentToken = token;
        }
        publishWorkspace();
        try {
            background.execute(() -> validatePending(pending, token,
                    lease == null ? runtime : lease.runtime(), lease));
        } catch (RuntimeException e) {
            synchronized (workspaceLock) {
                validating = false;
            }
            if (lease != null) {
                externalSources.validationFinished(lease);
            }
            uiThread.execute(() -> {
                requireView().showError(
                        "Não foi possível iniciar a validação: " + messageFor(e));
                publishOrShowIdle();
            });
        }
    }

    /** Solicita o cancelamento cooperativo da validação corrente. */
    public void cancelRequested() {
        if (validating) currentToken.cancel();
    }

    /** Exclui uma linha antes de iniciar uma validação. */
    public void removeRequested(Path source) {
        if (validating) return;
        synchronized (workspaceLock) {
            workspace.removeIf(item -> item.document().source().equals(source));
        }
        publishOrShowIdle();
    }

    /** Limpa todo o lote antes de iniciar uma validação. */
    public void clearRequested() {
        if (validating) return;
        synchronized (workspaceLock) {
            workspace.clear();
            workspaceGeneration++;
        }
        requireView().showIdle();
    }

    /** Remove os documentos aprovados e preserva os que ainda exigem atenção. */
    public void removeValidRequested() {
        if (validating) return;
        synchronized (workspaceLock) {
            workspace.removeIf(item -> item.status() == DocumentStatus.VALID);
        }
        publishOrShowIdle();
    }

    /** Mantém a ação existente como atalho semântico para limpar o lote. */
    public void newAnalysisRequested() {
        clearRequested();
    }

    /** Abre/atualiza a visão consultiva de fontes, sem afetar a área de trabalho atual. */
    public void externalSourcesRequested() {
        if (externalSources == null) return;
        ExternalSourcesSnapshot snapshot = externalSources.snapshot();
        uiThread.execute(() -> {
            publishExternalSources(snapshot);
            openExternalSourcesDialog();
        });
    }

    /** Ação manual não bloqueante; se já houver consulta, a view conserva o progresso em curso. */
    public void checkExternalSourcesRequested() {
        if (externalSources == null) return;
        externalSources.checkNow();
        ExternalSourcesSnapshot snapshot = externalSources.snapshot();
        uiThread.execute(() -> publishExternalSources(snapshot));
    }

    /** Reavalia uma atualização que chegou enquanto um seletor modal estava aberto. */
    public void modalDialogClosed() {
        synchronized (workspaceLock) {
            if (pendingImports > 0) {
                modalClosedWaitingForImport = true;
                return;
            }
        }
        if (externalSources != null && view != null) {
            publishExternalSources(externalSources.snapshot(), true);
        }
    }

    /** Aplica apenas candidatas já confirmadas e deixa o coordenador publicar o progresso. */
    public void applyExternalSourcesRequested() {
        if (externalSources == null) return;
        requestExternalSourcesApplication();
        ExternalSourcesSnapshot snapshot = externalSources.snapshot();
        uiThread.execute(() -> publishExternalSources(snapshot));
    }

    private void importInput(Path input, boolean includeSubfolders, long generation) {
        try {
            ImportedBatch imported = runtime.useCase().importDocuments(input, includeSubfolders);
            uiThread.execute(() -> {
                try {
                    mergeImport(imported, generation);
                } finally {
                    importFinished();
                }
            });
        } catch (RuntimeException e) {
            uiThread.execute(() -> {
                try {
                    requireView().showError(messageFor(e));
                } finally {
                    importFinished();
                }
            });
        }
    }

    private void importFinished() {
        boolean publishPending;
        synchronized (workspaceLock) {
            pendingImports = Math.max(0, pendingImports - 1);
            publishPending = pendingImports == 0 && modalClosedWaitingForImport;
            if (publishPending) modalClosedWaitingForImport = false;
        }
        if (publishPending && externalSources != null) {
            uiThread.executeLater(() -> publishExternalSources(externalSources.snapshot(), true));
        }
    }

    private void mergeImport(ImportedBatch imported, long generation) {
        if (validating) return;
        synchronized (workspaceLock) {
            if (generation != workspaceGeneration) return;
            for (var document : imported.documents()) {
                boolean alreadyAdded = workspace.stream().anyMatch(existing -> existing.document()
                        .source().equals(document.source()));
                if (!alreadyAdded) workspace.add(WorkspaceDocument.pending(document));
            }
        }
        publishOrShowIdle();
        if (!imported.invalidFiles().isEmpty()) requireView().showInvalidFiles(imported.invalidFiles());
    }

    private void validatePending(List<Path> pending, CancellationToken token,
            ValidationRuntime validationRuntime, ValidationLease lease) {
        for (Path source : pending) {
            if (token.isCancelled() || token != currentToken) break;
            uiThread.execute(() -> setStatus(source, DocumentStatus.VALIDATING, token));
            try {
                DocumentValidationResult result = validationRunner.validate(validationRuntime, source,
                        token);
                uiThread.execute(() -> applyValidation(source, result, token, validationRuntime.bases()));
                // TODO: remover após os testes manuais do fluxo de abortar validação.
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                uiThread.execute(() -> validationFailed(source, token, e));
            }
        }
        uiThread.execute(() -> finishValidation(token, lease));
    }

    private void setStatus(Path source, DocumentStatus status, CancellationToken token) {
        if (token != currentToken) return;
        synchronized (workspaceLock) {
            replace(source, item -> item.withStatus(status));
        }
        publishWorkspace();
    }

    private void applyValidation(Path source, DocumentValidationResult result, CancellationToken token,
            RuntimeBases runtimeBases) {
        if (token != currentToken) return;
        synchronized (workspaceLock) {
            if (token.isCancelled() && result.document() == null && result.findings().isEmpty()) {
                replace(source, item -> item.withStatus(DocumentStatus.PENDING));
            } else if (result.document() == null) {
                workspace.removeIf(item -> item.document().source().equals(source));
            } else {
                DocumentStatus status = WorkspaceDocument.statusFor(result.findings());
                replace(source, item -> item.withResult(status, result.findings(), runtimeBases));
                processed++;
            }
        }
        publishOrShowIdle();
        if (result.document() == null && !token.isCancelled()) requireView().showInvalidFiles(List.of(source));
    }

    private void finishValidation(CancellationToken token, ValidationLease lease) {
        if (token != currentToken) return;
        synchronized (workspaceLock) {
            validating = false;
        }
        if (lease != null) {
            externalSources.validationFinished(lease);
        }
        publishOrShowIdle();
    }

    private void validationFailed(Path source, CancellationToken token, RuntimeException failure) {
        if (token != currentToken) return;
        synchronized (workspaceLock) {
            replace(source, item -> item.withStatus(DocumentStatus.PENDING));
        }
        publishWorkspace();
        requireView().showError("Não foi possível validar " + source.getFileName() + ": "
                + messageFor(failure));
    }

    private void replace(Path source, java.util.function.UnaryOperator<WorkspaceDocument> update) {
        for (int index = 0; index < workspace.size(); index++) {
            if (workspace.get(index).document().source().equals(source)) {
                workspace.set(index, update.apply(workspace.get(index)));
                return;
            }
        }
    }

    private void publishOrShowIdle() {
        synchronized (workspaceLock) {
            if (workspace.isEmpty()) {
                requireView().showIdle();
                return;
            }
        }
        publishWorkspace();
    }

    private void publishWorkspace() {
        List<WorkspaceDocument> snapshot;
        synchronized (workspaceLock) {
            snapshot = List.copyOf(workspace);
        }
        requireView().showWorkspace(snapshot, validating, processed, total);
    }

    private void publishExternalSources(ExternalSourcesSnapshot snapshot) {
        publishExternalSources(snapshot, false);
    }

    private void publishExternalSources(ExternalSourcesSnapshot snapshot, boolean forced) {
        if (view == null || externalSources == null) {
            return;
        }
        if (!forced && snapshot.revision() <= latestExternalSourcesRevision) {
            return;
        }
        latestExternalSourcesRevision = snapshot.revision();
        MainView attachedView = requireView();
        attachedView.showExternalSources(snapshot);
        if (snapshot.phase() == ExternalSourcesPhase.APPLYING) {
            inUseFeedbackShown = false;
            restartRequiredFeedbackShown = false;
            return;
        }
        if (snapshot.phase() == ExternalSourcesPhase.UPDATES_AVAILABLE
                && !externalSourcesApplicationRequested
                && !attachedView.isModalDialogOpen()
                && snapshot.revision() != lastOfferedExternalSourcesRevision) {
            lastOfferedExternalSourcesRevision = snapshot.revision();
            if (attachedView.confirmExternalSourcesUpdate(snapshot)) {
                requestExternalSourcesApplication();
            }
        } else if (snapshot.phase() == ExternalSourcesPhase.UPDATED_AND_IN_USE
                && !inUseFeedbackShown) {
            inUseFeedbackShown = true;
            attachedView.showBasesUpdatedAndInUse(snapshot);
        } else if (snapshot.phase() == ExternalSourcesPhase.RESTART_REQUIRED
                && !restartRequiredFeedbackShown) {
            restartRequiredFeedbackShown = true;
            attachedView.showRestartRequired(snapshot);
        }
    }

    private void requestExternalSourcesApplication() {
        if (externalSourcesApplicationRequested) {
            return;
        }
        externalSourcesApplicationRequested = true;
        try {
            externalSources.applyAvailable();
        } catch (RuntimeException failure) {
            lastOfferedExternalSourcesRevision = Math.max(lastOfferedExternalSourcesRevision,
                    externalSources.snapshot().revision());
            uiThread.execute(() -> requireView().showError(
                    APPLICATION_START_FAILED + messageFor(failure)));
        } finally {
            externalSourcesApplicationRequested = false;
        }
    }

    private void openExternalSourcesDialog() {
        requireView().openExternalSourcesDialog();
    }

    private MainView requireView() {
        return Objects.requireNonNull(view, "A view deve ser conectada antes do uso.");
    }

    private static String messageFor(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "erro desconhecido" : message;
    }

    @FunctionalInterface
    interface DocumentValidationRunner {
        DocumentValidationResult validate(ValidationRuntime runtime, Path source,
                CancellationToken token);
    }
}
