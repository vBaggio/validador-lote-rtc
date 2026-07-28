package br.com.validadorlote.presentation.swing;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.TransferHandler;
import java.awt.Component;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Zona de arrastar-e-soltar de pasta, com botão alternativo de escolha. */
public final class DropZonePanel extends JPanel {

    public DropZonePanel(Consumer<Path> onFolderChosen) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(60, 40, 60, 40));

        JLabel title = new JLabel("Arraste aqui a pasta com os XMLs");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("NF-e e NFC-e — a análise roda 100% no seu computador");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton choose = new JButton("Escolher pasta...");
        choose.setAlignmentX(Component.CENTER_ALIGNMENT);
        choose.addActionListener(event -> chooseFolder(onFolderChosen));

        add(Box.createVerticalGlue());
        add(title);
        add(Box.createVerticalStrut(8));
        add(subtitle);
        add(Box.createVerticalStrut(24));
        add(choose);
        add(Box.createVerticalGlue());

        setTransferHandler(new TransferHandler() {
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
                        if (candidate instanceof File file && file.isDirectory()) {
                            onFolderChosen.accept(file.toPath());
                            return true;
                        }
                    }
                    return false;
                } catch (Exception ignored) {
                    return false;
                }
            }
        });
    }

    private void chooseFolder(Consumer<Path> onFolderChosen) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            onFolderChosen.accept(chooser.getSelectedFile().toPath());
        }
    }
}
