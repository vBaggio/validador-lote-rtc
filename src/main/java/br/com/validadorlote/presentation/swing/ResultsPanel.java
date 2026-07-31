package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.presentation.MainPresenter;
import br.com.validadorlote.presentation.WorkspaceDocument;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/** Área de trabalho do lote, com ações contextuais e validação incremental. */
public final class ResultsPanel extends JPanel {

    private final JLabel summary = new JLabel();
    private final JLabel detail = new JLabel();
    private final JProgressBar progress = new JProgressBar();
    private final DocumentsTableModel documentsModel = new DocumentsTableModel();
    private final DocumentProblemsTableModel problemsModel = new DocumentProblemsTableModel();
    private final JTable documentsTable = new JTable(documentsModel);
    private final JButton add = new JButton("Adicionar XMLs...");
    private final JButton remove = new JButton("Excluir selecionado");
    private final JButton clear = new JButton("Limpar");
    private final JButton removeValid = new JButton("Remover válidos");
    private final JButton validate = new JButton("Validar pendentes");
    private final JButton cancel = new JButton("Interromper");
    private final TransferHandler dropHandler;
    private final Runnable modalOpened;
    private final Runnable modalClosed;

    public ResultsPanel(MainPresenter presenter) {
        this(presenter, () -> { }, () -> { });
    }

    public ResultsPanel(MainPresenter presenter, Runnable modalOpened, Runnable modalClosed) {
        setLayout(new BorderLayout(0, 18));
        this.modalOpened = modalOpened;
        this.modalClosed = modalClosed;
        setBorder(BorderFactory.createEmptyBorder(26, 32, 22, 32));

        summary.setFont(summary.getFont().deriveFont(java.awt.Font.BOLD, 18f));
        detail.setForeground(new Color(155, 155, 160));
        detail.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        progress.setStringPainted(true);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(220, 18));

        configureDocumentsTable();
        dropHandler = fileDropHandler(presenter);
        documentsTable.setTransferHandler(dropHandler);
        documentsTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) return;
            int row = documentsTable.getSelectedRow();
            problemsModel.setFindings(row < 0 ? List.of()
                    : documentsModel.documentAt(documentsTable.convertRowIndexToModel(row)).findings());
            remove.setEnabled(row >= 0 && !cancel.isVisible());
        });

        JPanel documents = titledPanel("Documentos Fiscais", new JScrollPane(documentsTable));
        JTable problemsTable = configuredProblemsTable();
        JPanel problems = titledPanel("Problemas do documento selecionado", new JScrollPane(problemsTable));
        JPanel grids = new JPanel(new GridBagLayout());
        GridBagConstraints documentsConstraints = constraints(0.72, new Insets(0, 0, 0, 0));
        grids.add(documents, documentsConstraints);
        GridBagConstraints problemsConstraints = constraints(0.28, new Insets(20, 0, 0, 0));
        problemsConstraints.gridy = 1;
        grids.add(problems, problemsConstraints);

        add.setIcon(new OutlineIcon(OutlineIcon.Kind.IMPORT));
        add.addActionListener(event -> chooseInput(presenter));
        remove.setIcon(new OutlineIcon(OutlineIcon.Kind.DELETE));
        remove.addActionListener(event -> removeSelected(presenter));
        clear.setIcon(new OutlineIcon(OutlineIcon.Kind.REFRESH));
        clear.addActionListener(event -> presenter.clearRequested());
        removeValid.setIcon(new OutlineIcon(OutlineIcon.Kind.CORRECT));
        removeValid.addActionListener(event -> presenter.removeValidRequested());
        validate.setIcon(new OutlineIcon(OutlineIcon.Kind.CORRECT));
        validate.addActionListener(event -> presenter.validateRequested());
        cancel.setIcon(new OutlineIcon(OutlineIcon.Kind.CANCEL));
        cancel.addActionListener(event -> presenter.cancelRequested());
        cancel.setVisible(false);

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(summary);
        titleBlock.add(detail);
        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.add(titleBlock, BorderLayout.WEST);
        header.add(progress, BorderLayout.EAST);

        JPanel secondaryActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        secondaryActions.add(remove);
        secondaryActions.add(removeValid);
        secondaryActions.add(clear);
        secondaryActions.add(validate);
        secondaryActions.add(cancel);
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.add(add, BorderLayout.WEST);
        toolbar.add(secondaryActions, BorderLayout.EAST);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(header);
        north.add(Box.createVerticalStrut(18));
        north.add(toolbar);

        add(north, BorderLayout.NORTH);
        add(grids, BorderLayout.CENTER);
    }

    void showWorkspace(List<WorkspaceDocument> documents, boolean validating, int processed, int total) {
        Path selected = selectedSource();
        documentsModel.setDocuments(documents);
        restoreSelection(selected);
        long pending = documents.stream().filter(document -> document.status()
                == br.com.validadorlote.presentation.DocumentStatus.PENDING).count();
        long completed = documents.size() - pending - documents.stream().filter(document -> document.status()
                == br.com.validadorlote.presentation.DocumentStatus.VALIDATING).count();
        long valid = documents.stream().filter(document -> document.status()
                == br.com.validadorlote.presentation.DocumentStatus.VALID).count();
        summary.setText(documents.size() + " documento(s) no lote");
        detail.setText(validating ? "Validando " + processed + " de " + total + " documento(s)…"
                : completed + " validado(s)  •  " + pending + " aguardando validação");
        progress.setVisible(validating);
        progress.setMinimum(0);
        progress.setMaximum(Math.max(1, total));
        progress.setValue(Math.min(processed, Math.max(1, total)));
        progress.setString(processed + " / " + total);

        add.setEnabled(!validating);
        clear.setEnabled(!validating);
        removeValid.setEnabled(!validating && valid > 0);
        validate.setVisible(!validating);
        validate.setEnabled(!validating && pending > 0);
        cancel.setVisible(validating);
        remove.setEnabled(!validating && documentsTable.getSelectedRow() >= 0);
        documentsTable.setTransferHandler(validating ? null : dropHandler);
    }

    private void configureDocumentsTable() {
        documentsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        documentsTable.setRowHeight(34);
        documentsTable.setIntercellSpacing(new Dimension(0, 0));
        documentsTable.setShowVerticalLines(false);
        documentsTable.getColumnModel().getColumn(0).setMinWidth(62);
        documentsTable.getColumnModel().getColumn(0).setPreferredWidth(68);
        documentsTable.getColumnModel().getColumn(0).setMaxWidth(78);
        documentsTable.getColumnModel().getColumn(1).setMinWidth(390);
        documentsTable.getColumnModel().getColumn(1).setPreferredWidth(400);
        documentsTable.getColumnModel().getColumn(1).setMaxWidth(410);
        documentsTable.getColumnModel().getColumn(2).setMinWidth(290);
        documentsTable.getColumnModel().getColumn(2).setPreferredWidth(440);
        documentsTable.getColumnModel().getColumn(3).setMinWidth(52);
        documentsTable.getColumnModel().getColumn(3).setPreferredWidth(62);
        documentsTable.getColumnModel().getColumn(3).setMaxWidth(74);
        documentsTable.getColumnModel().getColumn(4).setMinWidth(42);
        documentsTable.getColumnModel().getColumn(4).setPreferredWidth(50);
        documentsTable.getColumnModel().getColumn(4).setMaxWidth(62);
        documentsTable.getColumnModel().getColumn(5).setMinWidth(46);
        documentsTable.getColumnModel().getColumn(5).setPreferredWidth(54);
        documentsTable.getColumnModel().getColumn(5).setMaxWidth(66);
        documentsTable.getColumnModel().getColumn(6).setMinWidth(260);
        documentsTable.getColumnModel().getColumn(6).setPreferredWidth(360);

        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();
        centered.setHorizontalAlignment(JLabel.CENTER);
        DefaultTableCellRenderer status = new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                setText("");
                setIcon(value instanceof javax.swing.Icon icon ? icon : null);
            }
        };
        status.setHorizontalAlignment(JLabel.CENTER);
        documentsTable.getColumnModel().getColumn(0).setCellRenderer(status);
        documentsTable.getColumnModel().getColumn(3).setCellRenderer(centered);
        DefaultTableCellRenderer rightAligned = new DefaultTableCellRenderer();
        rightAligned.setHorizontalAlignment(JLabel.RIGHT);
        documentsTable.getColumnModel().getColumn(4).setCellRenderer(rightAligned);
        documentsTable.getColumnModel().getColumn(5).setCellRenderer(rightAligned);
        DefaultTableCellRenderer message = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected,
                        hasFocus, row, column);
                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
                return label;
            }
        };
        documentsTable.getColumnModel().getColumn(6).setCellRenderer(message);
    }

    private JTable configuredProblemsTable() {
        JTable table = new JTable(problemsModel);
        table.setRowHeight(28);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setShowVerticalLines(false);
        table.getColumnModel().getColumn(0).setMinWidth(88);
        table.getColumnModel().getColumn(0).setPreferredWidth(96);
        table.getColumnModel().getColumn(0).setMaxWidth(120);
        return table;
    }

    private GridBagConstraints constraints(double weightY, Insets insets) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = weightY;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = insets;
        return constraints;
    }

    private void chooseInput(MainPresenter presenter) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setDialogTitle("Adicionar pasta ou XML ao lote");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("Arquivos XML (*.xml)", "xml"));
        modalOpened.run();
        try {
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                presenter.inputChosen(chooser.getSelectedFile().toPath());
            }
        } finally {
            modalClosed.run();
        }
    }

    private void removeSelected(MainPresenter presenter) {
        int row = documentsTable.getSelectedRow();
        if (row >= 0) presenter.removeRequested(documentsModel.documentAt(
                documentsTable.convertRowIndexToModel(row)).document().source());
    }

    private Path selectedSource() {
        int row = documentsTable.getSelectedRow();
        return row < 0 ? null : documentsModel.documentAt(documentsTable.convertRowIndexToModel(row))
                .document().source();
    }

    private void restoreSelection(Path source) {
        documentsTable.clearSelection();
        if (source == null) {
            problemsModel.setFindings(List.of());
            return;
        }
        for (int row = 0; row < documentsModel.getRowCount(); row++) {
            if (documentsModel.documentAt(row).document().source().equals(source)) {
                documentsTable.setRowSelectionInterval(row, row);
                return;
            }
        }
        problemsModel.setFindings(List.of());
    }

    private TransferHandler fileDropHandler(MainPresenter presenter) {
        return new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        && (!support.isDrop() || (support.getDropAction() & COPY) == COPY);
            }

            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!(data instanceof List<?> files)) return false;
                    boolean imported = false;
                    for (Object candidate : files) {
                        if (candidate instanceof File file && supported(file)) {
                            presenter.inputChosen(file.toPath());
                            imported = true;
                        }
                    }
                    return imported;
                } catch (Exception ignored) {
                    return false;
                }
            }
        };
    }

    private static boolean supported(File file) {
        return file.isDirectory() || (file.isFile()
                && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xml"));
    }

    private static JPanel titledPanel(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD, 14f));
        panel.add(label, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }
}
