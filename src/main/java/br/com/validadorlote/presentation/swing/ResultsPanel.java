package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.domain.BatchReport;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.presentation.MainPresenter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

/** Cartão de resultados: resumo por camada, causas, achados e ações do lote. */
public final class ResultsPanel extends JPanel {

    private final JLabel summary = new JLabel();
    private final JLabel layers = new JLabel();
    private final JLabel prediction = new JLabel();
    private final JLabel valuesNotChecked =
            new JLabel("Conferência de valores: não executada (requer a Calculadora)");
    private final CausesTableModel causesModel = new CausesTableModel();
    private final FindingsTableModel findingsModel = new FindingsTableModel();
    private final JTable causesTable = new JTable(causesModel);
    private final JCheckBox preEmission =
            new JCheckBox("XMLs pré-emissão (assinatura ausente vira informativo)", true);

    public ResultsPanel(MainPresenter presenter) {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        causesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JTable findingsTable = new JTable(findingsModel);
        causesTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            int row = causesTable.getSelectedRow();
            findingsModel.setFindings(row < 0 ? List.of()
                    : causesModel.causeAt(causesTable.convertRowIndexToModel(row)).findings());
        });

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(causesTable), new JScrollPane(findingsTable));
        split.setResizeWeight(0.6);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(summary);
        top.add(Box.createVerticalStrut(4));
        top.add(layers);
        top.add(Box.createVerticalStrut(2));
        top.add(prediction);
        top.add(Box.createVerticalStrut(2));
        top.add(valuesNotChecked);
        top.add(Box.createVerticalStrut(4));
        preEmission.addActionListener(event -> presenter.preEmissionToggled(preEmission.isSelected()));
        top.add(preEmission);

        JButton export = new JButton("Exportar CSV...");
        export.addActionListener(event -> chooseExportFolder(presenter));
        JButton newAnalysis = new JButton("Nova análise");
        newAnalysis.addActionListener(event -> presenter.newAnalysisRequested());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(newAnalysis);
        actions.add(export);

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    void show(BatchReport report) {
        String cancelled = report.cancelled() ? "  •  ANÁLISE CANCELADA (parcial)" : "";
        summary.setText(String.format(
                "%d arquivos analisados  •  %d com achados  •  %d ilegíveis  •  base %s%s",
                report.documentsScanned(), report.documentsWithFindings(),
                report.documentsUnreadable(), report.schemasVersion(), cancelled));
        updateLayerSummary(report);
        causesModel.setCauses(report.rootCauses());
        causesTable.clearSelection();
        findingsModel.setFindings(List.of());
    }

    private void chooseExportFolder(MainPresenter presenter) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Escolha a pasta para gravar os CSVs");
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            presenter.exportRequested(chooser.getSelectedFile().toPath());
        }
    }

    private void updateLayerSummary(BatchReport report) {
        long unreadable = countCauses(report, FindingKind.UNREADABLE);
        long schema = countCauses(report, FindingKind.SCHEMA)
                + countCauses(report, FindingKind.SIGNATURE_MISSING);
        long predictedRejections = countCauses(report, FindingKind.REJECTION_RULE);
        long notEvaluated = countCauses(report, FindingKind.NOT_EVALUATED);
        layers.setText(String.format(
                "Camadas executadas: Leitura do arquivo (%d causas) • Schema XML (%d causas)",
                unreadable, schema));
        prediction.setText(String.format(
                "Previsão de rejeição: %d causas previstas • %d causas não avaliadas",
                predictedRejections, notEvaluated));
    }

    private static long countCauses(BatchReport report, FindingKind kind) {
        return report.rootCauses().stream().filter(cause -> cause.key().kind() == kind).count();
    }
}
