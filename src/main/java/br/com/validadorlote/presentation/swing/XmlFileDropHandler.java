package br.com.validadorlote.presentation.swing;

import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Handler único para listas de arquivos entregues pelo Explorer e pelos desktops Linux. */
final class XmlFileDropHandler extends TransferHandler {

    private final BiConsumer<Path, Boolean> onInputChosen;
    private final BooleanSupplier includeSubfolders;

    XmlFileDropHandler(BiConsumer<Path, Boolean> onInputChosen,
            BooleanSupplier includeSubfolders) {
        this.onInputChosen = onInputChosen;
        this.includeSubfolders = includeSubfolders;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;
        try {
            Object transferred = support.getTransferable()
                    .getTransferData(DataFlavor.javaFileListFlavor);
            if (!(transferred instanceof List<?> files)) return false;
            boolean imported = false;
            for (Object candidate : files) {
                if (candidate instanceof File file && isSupported(file)) {
                    onInputChosen.accept(file.toPath(), includeSubfolders.getAsBoolean());
                    imported = true;
                }
            }
            return imported;
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isSupported(File file) {
        return file.isDirectory() || (file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".xml"));
    }
}
