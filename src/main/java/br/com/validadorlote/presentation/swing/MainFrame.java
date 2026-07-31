package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.presentation.MainPresenter;
import br.com.validadorlote.presentation.MainView;
import br.com.validadorlote.presentation.WorkspaceDocument;
import br.com.validadorlote.application.ExternalSourcesSnapshot;
import br.com.validadorlote.domain.ApplicationRelease;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Janela principal: view passiva, alterna cartões conforme o presenter manda. */
public final class MainFrame extends JFrame implements MainView {

    private static final Executor BROWSER_LAUNCHER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "release-page-browser");
        thread.setDaemon(true);
        return thread;
    });

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final ResultsPanel resultsPanel;
    private final ExternalSourcesDialog externalSourcesDialog;
    private final ExternalSourcesStatusBar externalSourcesStatusBar;
    private final String applicationVersion;
    private int modalDialogDepth;

    public MainFrame(MainPresenter presenter, String applicationVersion, String schemasVersion) {
        this(presenter, applicationVersion, schemasVersion, "");
    }

    public MainFrame(MainPresenter presenter, String applicationVersion, String schemasVersion,
            String tableVersion) {
        super("Validador de XML em Lote - Reforma Tributária");
        this.applicationVersion = applicationVersion;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(AppIcon.image());
        resultsPanel = new ResultsPanel(presenter, this::modalDialogOpened,
                () -> modalDialogClosed(presenter));
        externalSourcesDialog = new ExternalSourcesDialog(this, presenter::checkExternalSourcesRequested,
                presenter::applyExternalSourcesRequested, presenter::checkExternalSourcesRequested);
        externalSourcesStatusBar = new ExternalSourcesStatusBar(applicationVersion, schemasVersion,
                tableVersion, presenter::externalSourcesRequested,
                presenter::checkExternalSourcesRequested);
        root.add(new DropZonePanel(presenter::inputChosen, this::modalDialogOpened,
                () -> modalDialogClosed(presenter)), "drop");
        root.add(resultsPanel, "results");
        JPanel content = new JPanel(new BorderLayout());
        content.add(root, BorderLayout.CENTER);
        content.add(externalSourcesStatusBar, BorderLayout.SOUTH);
        setContentPane(content);
        setMinimumSize(new Dimension(1000, 660));
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int width = Math.min(1180, Math.max(1000, screen.width - 40));
        int height = Math.min(700, Math.max(660, screen.height - 28));
        setSize(width, height);
        setLocationRelativeTo(null);
    }

    @Override
    public void showIdle() {
        cards.show(root, "drop");
    }

    @Override
    public void showWorkspace(List<WorkspaceDocument> documents, boolean validating, int processed,
            int total) {
        resultsPanel.showWorkspace(documents, validating, processed, total);
        cards.show(root, "results");
    }

    @Override
    public void showInvalidFiles(List<Path> files) {
        String listed = files.stream().limit(8).map(path -> "• " + path.getFileName())
                .collect(java.util.stream.Collectors.joining("\n"));
        String more = files.size() > 8 ? "\n• e mais " + (files.size() - 8) + " arquivo(s)" : "";
        SwingDialogSupport.showMessage(this,
                "A seleção continha arquivo(s) inválido(s), que não foram adicionados:\n\n"
                        + listed + more,
                "Arquivos não adicionados", JOptionPane.WARNING_MESSAGE);
    }

    @Override
    public void showError(String message) {
        SwingDialogSupport.showMessage(this, message, "Erro", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showExternalSources(ExternalSourcesSnapshot snapshot) {
        externalSourcesStatusBar.showSnapshot(snapshot);
        externalSourcesDialog.showSnapshot(snapshot);
    }

    @Override
    public void openExternalSourcesDialog() {
        externalSourcesDialog.setLocationRelativeTo(this);
        externalSourcesDialog.open();
    }

    @Override
    public boolean isExternalSourcesDialogOpen() {
        return externalSourcesDialog.isOpen();
    }

    @Override
    public boolean isModalDialogOpen() {
        return modalDialogDepth > 0 || externalSourcesDialog.isOpen();
    }

    private void modalDialogOpened() {
        modalDialogDepth++;
    }

    private void modalDialogClosed(MainPresenter presenter) {
        modalDialogDepth = Math.max(0, modalDialogDepth - 1);
        if (modalDialogDepth == 0) presenter.modalDialogClosed();
    }

    @Override
    public boolean confirmExternalSourcesUpdate(ExternalSourcesSnapshot snapshot) {
        String message = snapshot.failedCount() > 0
                ? """
                  Há atualizações disponíveis para as bases de validação. Uma das fontes não respondeu e continuará usando a base atual.

                  Continuar sem atualizar pode deixar as validações defasadas ou menos precisas para as regras mais recentes.
                  Deseja atualizar o que foi verificado agora?
                  """
                : """
                  Há atualizações disponíveis para as bases de validação.

                  Continuar sem atualizar pode deixar as validações defasadas ou menos precisas para as regras mais recentes.
                  Deseja atualizar agora?
                  """;
        return SwingDialogSupport.showConfirm(this, message.strip(),
                "Atualização de bases", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
    }

    @Override
    public void showBasesUpdatedAndInUse(ExternalSourcesSnapshot snapshot) {
        externalSourcesDialog.showSnapshot(snapshot);
        if (externalSourcesDialog.isOpen()) {
            externalSourcesDialog.toFront();
            return;
        }
        SwingDialogSupport.showMessage(this,
                "As bases foram atualizadas e já estão em uso nesta sessão.",
                "Atualização concluída", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showRestartRequired(ExternalSourcesSnapshot snapshot) {
        externalSourcesDialog.showSnapshot(snapshot);
        if (externalSourcesDialog.isOpen()) {
            externalSourcesDialog.toFront();
            return;
        }
        SwingDialogSupport.showMessage(this,
                "A base foi atualizada em disco, mas será usada após reiniciar o aplicativo.",
                "Atualização concluída", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Oferece a página oficial da release sem baixar, instalar ou reiniciar o aplicativo. */
    public void showApplicationUpdate(ApplicationRelease release) {
        Object[] options = { "Continuar com esta versão", "Atualizar agora" };
        String message = """
                Existe uma versão mais recente disponível

                A versão atual pode estar defasada e não contemplar as regras fiscais mais recentes.

                Versão atual: %s
                Versão disponível: %s
                """.formatted(applicationVersion, release.version());
        int choice = SwingDialogSupport.showOption(this, message.strip(), "Nova versão disponível",
                JOptionPane.INFORMATION_MESSAGE, options, options[0]);
        if (choice == 1) openReleasePage(release);
    }

    private static void openReleasePage(ApplicationRelease release) {
        try {
            BROWSER_LAUNCHER.execute(() -> browseReleasePage(release));
        } catch (Exception ignored) {
            // O executor pode já estar encerrado; abrir o navegador continua opcional.
        }
    }

    private static void browseReleasePage(ApplicationRelease release) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(release.page());
            }
        } catch (Exception ignored) {
            // Abrir o navegador é opcional e jamais interfere com a janela principal.
        }
    }
}
