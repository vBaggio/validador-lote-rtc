package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.presentation.MainPresenter;
import br.com.validadorlote.presentation.WorkspaceDocument;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.JToggleButton;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Área de trabalho do lote, com ações contextuais e validação incremental. */
public final class ResultsPanel extends JPanel {

    private final JLabel summary = new JLabel();
    private final JLabel detail = new JLabel();
    private final JProgressBar progress = new JProgressBar();
    private final DocumentsTableModel documentsModel = new DocumentsTableModel();
    private final DocumentProblemsTableModel problemsModel = new DocumentProblemsTableModel();
    private final ZebraTable documentsTable = new ZebraTable(documentsModel);
    private final JButton add = new JButton("Adicionar XMLs...");
    private final JButton remove = new JButton("Excluir selecionado");
    private final JButton clear = new JButton("Limpar");
    private final JButton removeValid = new JButton("Remover válidos");
    private final JButton openSelected = new JButton("Abrir arquivo");
    private final JButton copyAccessKey = new JButton("Copiar chave");
    private final JCheckBox includeSubfolders = new JCheckBox("Incluir subpastas");
    private final JButton validate = new JButton("Validar pendentes");
    private final JButton cancel = new JButton("Abortar validação");
    private final TransferHandler dropHandler;
    private final Runnable modalOpened;
    private final Runnable modalClosed;

    public ResultsPanel(MainPresenter presenter) {
        this(presenter, () -> { }, () -> { }, new JToggleButton.ToggleButtonModel());
    }

    public ResultsPanel(MainPresenter presenter, Runnable modalOpened, Runnable modalClosed) {
        this(presenter, modalOpened, modalClosed, new JToggleButton.ToggleButtonModel());
    }

    ResultsPanel(MainPresenter presenter, Runnable modalOpened, Runnable modalClosed,
            ButtonModel includeSubfoldersModel) {
        setLayout(new BorderLayout(0, 18));
        this.modalOpened = modalOpened;
        this.modalClosed = modalClosed;
        includeSubfolders.setModel(includeSubfoldersModel);
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
            boolean hasRow = row >= 0 && row < documentsModel.getRowCount();
            problemsModel.setFindings(!hasRow ? List.of()
                    : documentsModel.documentAt(documentsTable.convertRowIndexToModel(row)).findings());
            remove.setEnabled(hasRow && !cancel.isVisible());
            updateSelectionActions();
        });

        JPanel documentActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        documentActions.add(openSelected);
        documentActions.add(copyAccessKey);
        documentActions.add(remove);
        documentActions.add(removeValid);
        documentActions.add(clear);
        JPanel documentContent = new JPanel(new BorderLayout(0, 8));
        documentContent.add(flexibleColumnScroll(documentsTable, 6, 260), BorderLayout.CENTER);
        documentContent.add(documentActions, BorderLayout.SOUTH);
        JPanel documents = titledPanel("Documentos Fiscais", documentContent);
        ZebraTable problemsTable = configuredProblemsTable();
        JPanel problems = titledPanel("Problemas do documento selecionado",
                flexibleColumnScroll(problemsTable, 1, 520));
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
        clear.addActionListener(event -> confirmClear(presenter));
        removeValid.setIcon(new OutlineIcon(OutlineIcon.Kind.CORRECT));
        removeValid.addActionListener(event -> presenter.removeValidRequested());
        openSelected.setIcon(new OutlineIcon(OutlineIcon.Kind.EXPORT));
        openSelected.addActionListener(event -> openSelectedFile());
        copyAccessKey.setIcon(new OutlineIcon(OutlineIcon.Kind.COPY));
        copyAccessKey.addActionListener(event -> copySelectedAccessKey());
        openSelected.setEnabled(false);
        copyAccessKey.setEnabled(false);
        validate.setIcon(new OutlineIcon(OutlineIcon.Kind.CORRECT));
        validate.addActionListener(event -> presenter.validateRequested());
        stylePrimaryAction(validate);
        cancel.setIcon(new OutlineIcon(OutlineIcon.Kind.CANCEL));
        cancel.addActionListener(event -> presenter.cancelRequested());
        stylePrimaryAction(cancel);
        cancel.setVisible(false);

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(summary);
        titleBlock.add(detail);
        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.add(titleBlock, BorderLayout.WEST);
        header.add(progress, BorderLayout.EAST);

        JPanel toolbar = new JPanel(new BorderLayout());
        JPanel importActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        importActions.add(add);
        importActions.add(includeSubfolders);
        toolbar.add(importActions, BorderLayout.WEST);
        JPanel primaryAction = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        primaryAction.add(validate);
        primaryAction.add(cancel);
        toolbar.add(primaryAction, BorderLayout.EAST);

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
        includeSubfolders.setEnabled(!validating);
        clear.setEnabled(!validating && !documents.isEmpty());
        removeValid.setEnabled(!validating && valid > 0);
        validate.setVisible(!validating);
        validate.setEnabled(!validating && pending > 0);
        cancel.setVisible(validating);
        remove.setEnabled(!validating && documentsTable.getSelectedRow() >= 0);
        updateSelectionActions();
        documentsTable.setTransferHandler(validating ? null : dropHandler);
    }

    private static void stylePrimaryAction(JButton button) {
        button.setFont(button.getFont().deriveFont(java.awt.Font.BOLD, button.getFont().getSize2D() + 1f));
        button.setPreferredSize(new Dimension(200, 38));
        button.setFocusPainted(false);
        button.putClientProperty("JButton.buttonType", "roundRect");
    }

    private void configureDocumentsTable() {
        documentsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        documentsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        documentsTable.setRowHeight(34);
        documentsTable.setIntercellSpacing(new Dimension(0, 0));
        documentsTable.setShowVerticalLines(false);
        documentsTable.getColumnModel().getColumn(0).setMinWidth(62);
        documentsTable.getColumnModel().getColumn(0).setPreferredWidth(68);
        documentsTable.getColumnModel().getColumn(0).setMaxWidth(78);
        documentsTable.getColumnModel().getColumn(1).setMinWidth(360);
        documentsTable.getColumnModel().getColumn(1).setPreferredWidth(375);
        documentsTable.getColumnModel().getColumn(1).setMaxWidth(390);
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

    private ZebraTable configuredProblemsTable() {
        ZebraTable table = new ZebraTable(problemsModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(28);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setShowVerticalLines(false);
        table.getColumnModel().getColumn(0).setMinWidth(88);
        table.getColumnModel().getColumn(0).setPreferredWidth(96);
        table.getColumnModel().getColumn(0).setMaxWidth(120);
        table.getColumnModel().getColumn(1).setMinWidth(520);
        table.getColumnModel().getColumn(1).setPreferredWidth(760);
        return table;
    }

    /**
     * Mantém a coluna de texto legível e usa o espaço livre do viewport sem
     * sacrificar a rolagem horizontal em janelas menores.
     */
    private static JScrollPane flexibleColumnScroll(ZebraTable table, int flexibleColumn,
            int minimumWidth) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                resizeFlexibleColumn(table, event.getComponent().getWidth(), flexibleColumn, minimumWidth);
            }
        });
        resizeFlexibleColumn(table, scroll.getViewport().getWidth(), flexibleColumn, minimumWidth);
        return scroll;
    }

    private static void resizeFlexibleColumn(ZebraTable table, int viewportWidth,
            int flexibleColumn, int minimumWidth) {
        int fixedWidth = 0;
        for (int index = 0; index < table.getColumnModel().getColumnCount(); index++) {
            if (index != flexibleColumn) {
                fixedWidth += table.getColumnModel().getColumn(index).getWidth();
            }
        }
        int targetWidth = Math.max(minimumWidth, viewportWidth - fixedWidth);
        var column = table.getColumnModel().getColumn(flexibleColumn);
        if (column.getWidth() != targetWidth) {
            column.setPreferredWidth(targetWidth);
            column.setWidth(targetWidth);
        }
        int tableWidth = fixedWidth + targetWidth;
        table.setPreferredContentWidth(tableWidth);
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
                presenter.inputChosen(chooser.getSelectedFile().toPath(),
                        includeSubfolders.isSelected());
            }
        } finally {
            modalClosed.run();
        }
    }

    private void confirmClear(MainPresenter presenter) {
        if (!clear.isEnabled()) return;
        if (SwingDialogSupport.showConfirm(this, "Limpar todos os documentos do lote?", "Limpar lote",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)) {
            presenter.clearRequested();
        }
    }

    private void updateSelectionActions() {
        boolean selected = documentsTable.getSelectedRow() >= 0;
        openSelected.setEnabled(selected);
        copyAccessKey.setEnabled(selected && selectedAccessKey() != null);
    }

    private void openSelectedFile() {
        Path source = selectedSource();
        if (source == null) return;
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            SwingDialogSupport.showMessage(this, "Não foi possível abrir arquivos neste ambiente.",
                    "Abrir arquivo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(source.toFile());
        } catch (IOException | RuntimeException failure) {
            SwingDialogSupport.showMessage(this, "Não foi possível abrir o arquivo selecionado.",
                    "Abrir arquivo", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void copySelectedAccessKey() {
        String key = selectedAccessKey();
        if (key == null) return;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(key), null);
        } catch (RuntimeException failure) {
            SwingDialogSupport.showMessage(this, "Não foi possível copiar a chave de acesso.",
                    "Copiar chave", JOptionPane.WARNING_MESSAGE);
        }
    }

    private String selectedAccessKey() {
        int row = documentsTable.getSelectedRow();
        if (row < 0 || row >= documentsModel.getRowCount()) return null;
        String key = documentsModel.documentAt(documentsTable.convertRowIndexToModel(row))
                .document().accessKey();
        return key == null || key.isBlank() ? null : key;
    }

    /** JTable com listras quase imperceptíveis, mantendo a seleção intacta. */
    static final class ZebraTable extends JTable {

        private int preferredContentWidth = -1;

        ZebraTable(javax.swing.table.TableModel model) {
            super(model);
        }

        void setPreferredContentWidth(int width) {
            if (preferredContentWidth == width) return;
            preferredContentWidth = width;
            revalidate();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension preferred = super.getPreferredSize();
            if (preferredContentWidth >= 0) preferred.width = preferredContentWidth;
            return preferred;
        }

        @Override
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            Component component = super.prepareRenderer(renderer, row, column);
            if (!isRowSelected(row)) {
                component.setBackground(row % 2 == 0 ? getBackground() : alternateRowColor());
            }
            return component;
        }

        private Color alternateRowColor() {
            Color base = getBackground();
            int shift = base.getRed() < 128 ? 5 : -5;
            return new Color(adjust(base.getRed(), shift), adjust(base.getGreen(), shift),
                    adjust(base.getBlue(), shift));
        }

        private static int adjust(int value, int shift) {
            return Math.max(0, Math.min(255, value + shift));
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
                            presenter.inputChosen(file.toPath(), includeSubfolders.isSelected());
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
