package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.domain.BatchReport;
import br.com.validadorlote.presentation.MainPresenter;
import br.com.validadorlote.presentation.MainView;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.nio.file.Path;

/** Janela principal: view passiva, alterna cartões conforme o presenter manda. */
public final class MainFrame extends JFrame implements MainView {

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final RunningPanel runningPanel;
    private final ResultsPanel resultsPanel;

    public MainFrame(MainPresenter presenter, String schemasVersion) {
        super("Validador de Lote RTC — ferramenta independente (base " + schemasVersion + ")");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        runningPanel = new RunningPanel(presenter::cancelRequested);
        resultsPanel = new ResultsPanel(presenter);
        root.add(new DropZonePanel(presenter::folderChosen), "drop");
        root.add(runningPanel, "running");
        root.add(resultsPanel, "results");
        setContentPane(root);
        setSize(900, 620);
        setLocationRelativeTo(null);
    }

    @Override
    public void showIdle() {
        cards.show(root, "drop");
    }

    @Override
    public void showRunning(int processed, int total) {
        runningPanel.update(processed, total);
        cards.show(root, "running");
    }

    @Override
    public void showResults(BatchReport report) {
        resultsPanel.show(report);
        cards.show(root, "results");
    }

    @Override
    public void showError(String message) {
        cards.show(root, "drop");
        JOptionPane.showMessageDialog(this, message, "Erro", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showExportSuccess(Path folder) {
        JOptionPane.showMessageDialog(this, "CSVs gravados em:\n" + folder,
                "Exportação concluída", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showExportError(String message) {
        JOptionPane.showMessageDialog(this, message, "Falha na exportação", JOptionPane.ERROR_MESSAGE);
    }
}
