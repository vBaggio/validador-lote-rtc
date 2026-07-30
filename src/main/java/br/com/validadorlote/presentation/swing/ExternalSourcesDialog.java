package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.application.ExternalSourceState;
import br.com.validadorlote.application.ExternalSourcesPhase;
import br.com.validadorlote.application.ExternalSourcesSnapshot;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Diálogo consultivo: mostra metadados locais das bases, nunca dados do lote. */
final class ExternalSourcesDialog extends JDialog {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());
    private final DefaultTableModel model = new DefaultTableModel(new Object[] { "Fonte",
            "Base no próximo boot", "Origem", "Atualizada", "Verificada", "Resultado", "Hash" }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JButton checkNow = new JButton("Verificar agora");
    private final JLabel notice = new JLabel("Atualizações concluídas entram em uso somente no próximo boot.");

    ExternalSourcesDialog(Window owner, Runnable onCheckNow) {
        super(owner, "Fontes externas", Dialog.ModalityType.MODELESS);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        table.setAutoCreateRowSorter(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        notice.setForeground(new java.awt.Color(160, 160, 160));
        checkNow.addActionListener(event -> onCheckNow.run());
        actions.add(notice);
        actions.add(checkNow);
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(scroll, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        setMinimumSize(new Dimension(980, 300));
        setSize(1080, 350);
        setLocationRelativeTo(owner);
    }

    void showSnapshot(ExternalSourcesSnapshot snapshot) {
        model.setRowCount(0);
        for (ExternalSourceState source : snapshot.sources()) {
            model.addRow(new Object[] { source.name(), source.activeVersion(), source.origin(),
                    format(source.updatedAt()), format(source.checkedAt()), result(source),
                    source.abbreviatedHash() == null ? "—" : source.abbreviatedHash() });
        }
        boolean checking = snapshot.phase() == ExternalSourcesPhase.CHECKING;
        checkNow.setEnabled(!checking);
        checkNow.setText(checking ? "Verificando…" : "Verificar agora");
        notice.setText(checking ? "Consultando fontes em segundo plano; o lote permanece local."
                : "Atualizações concluídas entram em uso somente no próximo boot.");
    }

    private static String format(Instant instant) {
        return instant == null ? "—" : DATE.format(instant);
    }

    private static String result(ExternalSourceState source) {
        return switch (source.phase()) {
            case NOT_CHECKED -> "Ainda não verificada";
            case CHECKING -> "Verificando";
            case UP_TO_DATE -> source.detail() == null ? "Sem atualização" : source.detail();
            case UPDATE_AVAILABLE -> "Atualização disponível: " + source.candidateVersion();
            case APPLYING -> "Aplicando atualização";
            case APPLIED -> "Atualização instalada (próximo boot)";
            case FAILED -> "Aviso: " + (source.detail() == null ? "fonte indisponível" : source.detail());
        };
    }
}
