package br.com.validadorlote.presentation.swing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DropZonePanelTest {

    @Test
    void dragAndDropSvgIsAvailableAtTheExpectedSize() {
        FlatSVGIcon icon = new FlatSVGIcon("images/drag-drop.svg", 48, 48);

        assertThat(icon.hasFound()).isTrue();
        assertThat(icon.getIconWidth()).isEqualTo(48);
        assertThat(icon.getIconHeight()).isEqualTo(48);
    }

    @Test
    void dragAndDropImportsEverySelectedXml(@TempDir Path dir) throws Exception {
        Path first = Files.writeString(dir.resolve("primeiro.xml"), "<x/>");
        Path second = Files.writeString(dir.resolve("segundo.xml"), "<x/>");
        List<Path> imported = new ArrayList<>();

        SwingUtilities.invokeAndWait(() -> {
            DropZonePanel panel = new DropZonePanel((path, includeSubfolders) -> imported.add(path),
                    () -> { }, () -> { }, new JToggleButton.ToggleButtonModel());
            Transferable files = fileList(List.of(first, second));
            TransferHandler.TransferSupport support =
                    new TransferHandler.TransferSupport(panel, files);

            assertThat(panel.getTransferHandler().canImport(support)).isTrue();
            assertThat(panel.getTransferHandler().importData(support)).isTrue();
        });

        assertThat(imported).containsExactly(first, second);
    }

    private static Transferable fileList(List<Path> paths) {
        List<java.io.File> files = paths.stream().map(Path::toFile).toList();
        return new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] {DataFlavor.javaFileListFlavor};
            }

            @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.javaFileListFlavor.equals(flavor);
            }

            @Override public Object getTransferData(DataFlavor flavor) {
                return files;
            }
        };
    }
}
