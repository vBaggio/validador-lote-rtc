package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.RootCause;
import br.com.validadorlote.domain.Severity;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/** Tabela de causas-raiz, preservando a ordem recebida do relatório. */
final class CausesTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Camada", "Causa", "Campo", "Severidade",
            "Documentos", "Ocorrências", "Ação sugerida"};

    private List<RootCause> causes = List.of();

    void setCauses(List<RootCause> causes) {
        this.causes = List.copyOf(causes);
        fireTableDataChanged();
    }

    RootCause causeAt(int row) {
        return causes.get(row);
    }

    @Override
    public int getRowCount() {
        return causes.size();
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
        RootCause cause = causes.get(row);
        return switch (column) {
            case 0 -> layerFor(cause.key().kind());
            case 1 -> text(cause.friendlyExplanation());
            case 2 -> text(cause.key().field());
            case 3 -> severityFor(cause);
            case 4 -> cause.affectedDocuments();
            case 5 -> cause.findings().size();
            case 6 -> text(cause.suggestedAction());
            default -> "";
        };
    }

    static String layerFor(FindingKind kind) {
        return switch (kind) {
            case UNREADABLE -> "Leitura do arquivo";
            case SCHEMA, SIGNATURE_MISSING -> "Schema XML";
            case REJECTION_RULE, NOT_EVALUATED -> "Previsão de rejeição";
        };
    }

    private static String severityFor(RootCause cause) {
        if (cause.findings().isEmpty()) {
            return "";
        }
        Severity severity = cause.findings().getFirst().severity();
        return switch (severity) {
            case REJECTION -> "Rejeição";
            case WARNING -> "Aviso";
            case INFO -> "Informativo";
        };
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
