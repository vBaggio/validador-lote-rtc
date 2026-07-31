package br.com.validadorlote.presentation.swing;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Zona de arrastar-e-soltar de pasta ou XML, com botão alternativo de escolha. */
public final class DropZonePanel extends JPanel {

    public DropZonePanel(Consumer<Path> onInputChosen) {
        this(onInputChosen, () -> { }, () -> { });
    }

    public DropZonePanel(Consumer<Path> onInputChosen, Runnable modalOpened, Runnable modalClosed) {
        setLayout(new GridBagLayout());

        JPanel dropArea = new JPanel();
        dropArea.setLayout(new BoxLayout(dropArea, BoxLayout.Y_AXIS));
        dropArea.setPreferredSize(new Dimension(680, 400));
        dropArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(new Color(115, 115, 115), 1f, 7f, 4f, true),
                BorderFactory.createEmptyBorder(48, 48, 42, 48)));

        JLabel icon = new JLabel(new OutlineIcon(OutlineIcon.Kind.DRAG_DROP, 48));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = new JLabel("Arraste e solte seus XMLs aqui");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Importe uma pasta ou um arquivo XML para iniciar a análise");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton choose = new JButton("Escolher pasta ou XML...");
        choose.setIcon(new OutlineIcon(OutlineIcon.Kind.IMPORT));
        choose.setPreferredSize(new Dimension(250, 40));
        choose.setAlignmentX(Component.CENTER_ALIGNMENT);
        choose.addActionListener(event -> chooseInput(onInputChosen, modalOpened, modalClosed));

        dropArea.add(Box.createVerticalGlue());
        dropArea.add(icon);
        dropArea.add(Box.createVerticalStrut(18));
        dropArea.add(title);
        dropArea.add(Box.createVerticalStrut(12));
        dropArea.add(subtitle);
        dropArea.add(Box.createVerticalStrut(34));
        dropArea.add(choose);
        dropArea.add(Box.createVerticalStrut(22));
        JLabel hint = new JLabel("A análise é local e não envia seus dados pela rede");
        hint.setFont(hint.getFont().deriveFont(12f));
        hint.setForeground(new Color(150, 150, 150));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        dropArea.add(hint);
        dropArea.add(Box.createVerticalGlue());

        add(dropArea, new GridBagConstraints());

        TransferHandler handler = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        && (!support.isDrop()
                        || (support.getDropAction() & COPY) == COPY);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    Object transferData = support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!(transferData instanceof List<?> files)) {
                        return false;
                    }
                    for (Object candidate : files) {
                        if (candidate instanceof File file && isSupported(file)) {
                            onInputChosen.accept(file.toPath());
                            return true;
                        }
                    }
                    return false;
                } catch (Exception ignored) {
                    return false;
                }
            }
        };
        setTransferHandler(handler);
        dropArea.setTransferHandler(handler);
        icon.setTransferHandler(handler);
        title.setTransferHandler(handler);
        subtitle.setTransferHandler(handler);
        hint.setTransferHandler(handler);
    }

    private void chooseInput(Consumer<Path> onInputChosen, Runnable modalOpened, Runnable modalClosed) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setDialogTitle("Escolha uma pasta ou um XML");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("Arquivos XML (*.xml)", "xml"));
        modalOpened.run();
        try {
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File selected = chooser.getSelectedFile();
                if (isSupported(selected)) {
                    onInputChosen.accept(selected.toPath());
                }
            }
        } finally {
            modalClosed.run();
        }
    }

    private static boolean isSupported(File file) {
        return file.isDirectory() || (file.isFile()
                && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xml"));
    }
}
