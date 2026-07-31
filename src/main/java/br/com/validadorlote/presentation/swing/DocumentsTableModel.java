package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.presentation.DocumentStatus;
import br.com.validadorlote.presentation.WorkspaceDocument;
import br.com.validadorlote.presentation.WorkspaceDocumentOrder;

import javax.swing.Icon;
import java.awt.Color;
import javax.swing.table.AbstractTableModel;
import java.util.List;

/** Listagem principal: um XML fiscal legível por linha, com seu estado mais relevante. */
final class DocumentsTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Status", "Chave de acesso", "Emitente",
            "Mod.", "Série", "Num.", "Mensagem / explicação"};

    private List<WorkspaceDocument> documents = List.of();

    void setDocuments(List<WorkspaceDocument> documents) {
        this.documents = documents.stream().sorted(WorkspaceDocumentOrder.DISPLAY).toList();
        fireTableDataChanged();
    }

    WorkspaceDocument documentAt(int row) {
        return documents.get(row);
    }

    @Override public int getRowCount() { return documents.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 0 ? Icon.class : Object.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        WorkspaceDocument report = documents.get(row);
        var document = report.document();
        return switch (column) {
            case 0 -> statusIcon(report);
            case 1 -> text(document.accessKey());
            case 2 -> emitter(document.emitterName(), document.emitterCnpj());
            case 3 -> model(document.model());
            case 4 -> text(document.series());
            case 5 -> text(document.documentNumber());
            case 6 -> message(report);
            default -> "";
        };
    }

    private static Icon statusIcon(WorkspaceDocument report) {
        return switch (report.status()) {
            case PENDING -> new OutlineIcon(OutlineIcon.Kind.NEUTRAL, 18, new Color(157, 164, 177));
            case VALIDATING -> new OutlineIcon(OutlineIcon.Kind.PROGRESS, 18, new Color(116, 177, 255));
            case VALID -> new OutlineIcon(OutlineIcon.Kind.CORRECT, 18, new Color(84, 210, 123));
            case ERROR -> new OutlineIcon(OutlineIcon.Kind.ERROR, 18, new Color(237, 90, 90));
            case WARNING -> new OutlineIcon(OutlineIcon.Kind.NEUTRAL, 18, new Color(242, 190, 73));
            case NOT_EVALUATED -> new OutlineIcon(OutlineIcon.Kind.NEUTRAL, 18, Color.WHITE);
        };
    }

    private static String message(WorkspaceDocument report) {
        if (report.status() == DocumentStatus.PENDING) return "Aguardando validação";
        if (report.status() == DocumentStatus.VALIDATING) return "Validando…";
        List<Finding> findings = report.findings();
        if (findings.isEmpty()) return "Sem problemas identificados";
        Finding first = findings.getFirst();
        String message = first.friendlyMessage() != null ? first.friendlyMessage()
                : text(first.officialMessage());
        int remaining = findings.size() - 1;
        return remaining == 0 ? message : message + "  + " + remaining + " problema(s)";
    }

    private static String emitter(String name, String cnpj) {
        String formattedCnpj = formatCnpj(cnpj);
        if (name == null || name.isBlank()) return formattedCnpj;
        return formattedCnpj.isBlank() ? name : name + " - " + formattedCnpj;
    }

    private static String formatCnpj(String value) {
        if (value == null) return "";
        String digits = value.replaceAll("\\D", "");
        if (digits.length() != 14) return value;
        return digits.substring(0, 2) + "." + digits.substring(2, 5) + "."
                + digits.substring(5, 8) + "/" + digits.substring(8, 12) + "-"
                + digits.substring(12);
    }

    private static String model(String value) {
        return switch (value == null ? "" : value) {
            case "55" -> "NF-e";
            case "65" -> "NFC-e";
            case "" -> "";
            default -> value;
        };
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
