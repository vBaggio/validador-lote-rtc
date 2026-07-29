package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.Severity;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/** Problemas do documento selecionado na listagem principal. */
final class DocumentProblemsTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Severidade", "Causa"};
    private List<Finding> findings = List.of();

    void setFindings(List<Finding> findings) {
        this.findings = List.copyOf(findings);
        fireTableDataChanged();
    }

    @Override public int getRowCount() { return findings.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }

    @Override
    public Object getValueAt(int row, int column) {
        Finding finding = findings.get(row);
        return switch (column) {
            case 0 -> severity(finding.severity());
            case 1 -> cause(finding);
            default -> "";
        };
    }

    private static String severity(Severity severity) {
        return switch (severity) {
            case REJECTION -> "Erro";
            case WARNING -> "Atenção";
            case INFO -> "Informativo";
        };
    }

    private static String cause(Finding finding) {
        String text = finding.friendlyMessage() != null ? finding.friendlyMessage()
                : finding.officialMessage();
        if (finding.kind() == FindingKind.NOT_EVALUATED && text != null) {
            return "Não avaliado: " + text;
        }
        return text == null ? "" : text;
    }
}
