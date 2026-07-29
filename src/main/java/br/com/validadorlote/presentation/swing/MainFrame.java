package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.presentation.MainPresenter;
import br.com.validadorlote.presentation.MainView;
import br.com.validadorlote.presentation.WorkspaceDocument;
import br.com.validadorlote.application.ExternalSourceStatus;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;

/** Janela principal: view passiva, alterna cartões conforme o presenter manda. */
public final class MainFrame extends JFrame implements MainView {

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final ResultsPanel resultsPanel;
    private final ExternalSourcesDialog externalSourcesDialog;

    public MainFrame(MainPresenter presenter, String schemasVersion) {
        super("Validador de XML em Lote - Reforma Tributária");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(AppIcon.image());
        resultsPanel = new ResultsPanel(presenter);
        externalSourcesDialog = new ExternalSourcesDialog(this, presenter::checkExternalSourcesRequested);
        root.add(new DropZonePanel(presenter::inputChosen), "drop");
        root.add(resultsPanel, "results");
        JPanel content = new JPanel(new BorderLayout());
        content.add(root, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        JLabel version = new JLabel("  v0.1.0  •  " + schemasVersion);
        version.setBorder(javax.swing.BorderFactory.createEmptyBorder(7, 12, 7, 12));
        version.setForeground(new java.awt.Color(150, 150, 150));
        JButton sources = new JButton("Fontes externas");
        sources.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 10, 5, 10));
        sources.setForeground(new java.awt.Color(170, 170, 170));
        sources.addActionListener(event -> {
            externalSourcesDialog.setLocationRelativeTo(this);
            externalSourcesDialog.setVisible(true);
            presenter.externalSourcesRequested();
        });
        footer.add(version, BorderLayout.WEST);
        footer.add(sources, BorderLayout.EAST);
        content.add(footer, BorderLayout.SOUTH);
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
        JOptionPane.showMessageDialog(this,
                "A seleção continha arquivo(s) inválido(s), que não foram adicionados:\n\n"
                        + listed + more,
                "Arquivos não adicionados", JOptionPane.WARNING_MESSAGE);
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Erro", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showExternalSources(List<ExternalSourceStatus> sources, boolean checking) {
        externalSourcesDialog.showStatus(sources, checking);
    }
}
