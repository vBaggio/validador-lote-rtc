package br.com.validadorlote.presentation.swing;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Zona de arrastar-e-soltar de pasta ou XML, com botão alternativo de escolha. */
public final class DropZonePanel extends JPanel {

    public DropZonePanel(Consumer<Path> onInputChosen) {
        this((path, ignored) -> onInputChosen.accept(path), () -> { }, () -> { },
                new JToggleButton.ToggleButtonModel());
    }

    public DropZonePanel(Consumer<Path> onInputChosen, Runnable modalOpened, Runnable modalClosed) {
        this((path, ignored) -> onInputChosen.accept(path), modalOpened, modalClosed,
                new JToggleButton.ToggleButtonModel());
    }

    DropZonePanel(BiConsumer<Path, Boolean> onInputChosen, Runnable modalOpened,
            Runnable modalClosed, ButtonModel includeSubfoldersModel) {
        setLayout(new GridBagLayout());
        JCheckBox includeSubfolders = new JCheckBox("Incluir subpastas");
        includeSubfolders.setModel(includeSubfoldersModel);
        includeSubfolders.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel dropArea = new JPanel();
        dropArea.setLayout(new BoxLayout(dropArea, BoxLayout.Y_AXIS));
        dropArea.setPreferredSize(new Dimension(680, 400));
        dropArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(new Color(115, 115, 115), 1f, 7f, 4f, true),
                BorderFactory.createEmptyBorder(48, 48, 42, 48)));

        JLabel icon = new JLabel(new FlatSVGIcon("images/drag-drop.svg", 48, 48));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = new JLabel("Adicione seus XMLs");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Arraste arquivos ou uma pasta para esta área");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel alternative = new JLabel("ou");
        alternative.setForeground(new Color(150, 150, 150));
        alternative.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton choose = new JButton("Escolher pasta ou XML...");
        choose.setIcon(new OutlineIcon(OutlineIcon.Kind.FOLDER));
        choose.setPreferredSize(new Dimension(250, 40));
        choose.setAlignmentX(Component.CENTER_ALIGNMENT);
        choose.addActionListener(event -> chooseInput(onInputChosen, includeSubfolders,
                modalOpened, modalClosed));

        dropArea.add(Box.createVerticalGlue());
        dropArea.add(icon);
        dropArea.add(Box.createVerticalStrut(18));
        dropArea.add(title);
        dropArea.add(Box.createVerticalStrut(12));
        dropArea.add(subtitle);
        dropArea.add(Box.createVerticalStrut(20));
        dropArea.add(alternative);
        dropArea.add(Box.createVerticalStrut(12));
        dropArea.add(choose);
        dropArea.add(Box.createVerticalStrut(12));
        dropArea.add(includeSubfolders);
        dropArea.add(Box.createVerticalStrut(22));
        JLabel hint = new JLabel("A análise é local e não envia seus dados pela rede");
        hint.setFont(hint.getFont().deriveFont(12f));
        hint.setForeground(new Color(150, 150, 150));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        dropArea.add(hint);
        dropArea.add(Box.createVerticalGlue());

        add(dropArea, new GridBagConstraints());

        XmlFileDropHandler handler =
                new XmlFileDropHandler(onInputChosen, includeSubfolders::isSelected);
        setTransferHandler(handler);
        dropArea.setTransferHandler(handler);
        icon.setTransferHandler(handler);
        title.setTransferHandler(handler);
        subtitle.setTransferHandler(handler);
        alternative.setTransferHandler(handler);
        choose.setTransferHandler(handler);
        includeSubfolders.setTransferHandler(handler);
        hint.setTransferHandler(handler);
    }

    private void chooseInput(BiConsumer<Path, Boolean> onInputChosen, JCheckBox includeSubfolders,
            Runnable modalOpened, Runnable modalClosed) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Escolha uma pasta ou um XML");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("Arquivos XML (*.xml)", "xml"));
        modalOpened.run();
        try {
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File[] selected = chooser.getSelectedFiles();
                if (selected.length == 0 && chooser.getSelectedFile() != null) {
                    selected = new File[] {chooser.getSelectedFile()};
                }
                for (File file : selected) {
                    if (XmlFileDropHandler.isSupported(file)) {
                        onInputChosen.accept(file.toPath(), includeSubfolders.isSelected());
                    }
                }
            }
        } finally {
            modalClosed.run();
        }
    }

}
