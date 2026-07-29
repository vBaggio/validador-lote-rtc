package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;

import javax.swing.table.AbstractTableModel;
import java.nio.file.Path;
import java.util.List;

/** Tabela de achados da causa selecionada. */
final class FindingsTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Arquivo", "Item", "Linha", "Mensagem / explicação"};

    private List<Finding> findings = List.of();

    void setFindings(List<Finding> findings) {
        this.findings = List.copyOf(findings);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return findings.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        Finding finding = findings.get(row);
        return switch (column) {
            case 0 -> fileName(finding.source());
            case 1 -> finding.itemNumber() == null ? "" : finding.itemNumber();
            case 2 -> finding.line() == null ? "" : finding.line();
            case 3 -> detailText(finding);
            default -> "";
        };
    }

    private static String fileName(Path source) {
        if (source == null || source.getFileName() == null) {
            return "";
        }
        return source.getFileName().toString();
    }

    private static String detailText(Finding finding) {
        if (finding.kind() == FindingKind.NOT_EVALUATED) {
            return "Explicação local: " + text(finding.friendlyMessage());
        }
        if (finding.officialMessage() != null && !finding.officialMessage().isBlank()) {
            return finding.officialMessage();
        }
        return text(finding.friendlyMessage());
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
