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

    public MainFrame(MainPresenter presenter, String applicationVersion, String schemasVersion) {
        super("Validador de XML em Lote - Reforma Tributária");
        this.applicationVersion = applicationVersion;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(AppIcon.image());
        resultsPanel = new ResultsPanel(presenter);
        externalSourcesDialog = new ExternalSourcesDialog(this, presenter::checkExternalSourcesRequested,
                presenter::applyExternalSourcesRequested, presenter::checkExternalSourcesRequested);
        externalSourcesStatusBar = new ExternalSourcesStatusBar(applicationVersion, schemasVersion,
                presenter::externalSourcesRequested, presenter::checkExternalSourcesRequested);
        root.add(new DropZonePanel(presenter::inputChosen), "drop");
        root.add(resultsPanel, "results");
        JPanel content = new JPanel(new BorderLayout());
        content.add(root, BorderLayout.CENTER);
        content.add(externalSourcesStatusBar, BorderLayout.SOUTH);
        setContentPane(content);
        setMinimumSize(new Dimension(1000, 720));
        setSize(1200, 800);
        setLocationRelativeTo(null);
        setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
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
    public boolean confirmExternalSourcesUpdate(ExternalSourcesSnapshot snapshot) {
        String message = snapshot.failedCount() > 0
                ? """
                  Há atualização disponível. Uma das fontes não respondeu e continuará usando a base atual.
                  Deseja atualizar o que foi verificado?
                  """
                : """
                  Há atualizações disponíveis para as bases de validação.
                  Deseja atualizar agora?
                  """;
        return SwingDialogSupport.showConfirm(this, message.strip(),
                "Atualização de bases", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
    }

    @Override
    public void showBasesUpdatedAndInUse(ExternalSourcesSnapshot snapshot) {
        externalSourcesDialog.showSnapshot(snapshot);
        externalSourcesDialog.toFront();
    }

    @Override
    public void showRestartRequired(ExternalSourcesSnapshot snapshot) {
        externalSourcesDialog.showSnapshot(snapshot);
        externalSourcesDialog.toFront();
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
