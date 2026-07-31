package br.com.validadorlote.presentation.swing;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import java.awt.Component;

/** Decoração comum dos diálogos da aplicação, sem o controle de minimizar. */
final class SwingDialogSupport {

    private SwingDialogSupport() { }

    static void hideMinimizeButton(JDialog dialog) {
        dialog.setUndecorated(true);
        dialog.getRootPane().setWindowDecorationStyle(JRootPane.PLAIN_DIALOG);
        dialog.getRootPane().putClientProperty("JRootPane.titleBarShowIconify", false);
        dialog.getRootPane().putClientProperty("JRootPane.titleBarShowMaximize", false);
    }

    static void showMessage(Component parent, Object message, String title, int messageType) {
        JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION);
        JDialog dialog = pane.createDialog(parent, title);
        hideMinimizeButton(dialog);
        dialog.setVisible(true);
    }

    static boolean showConfirm(Component parent, Object message, String title, int optionType,
            int messageType) {
        JOptionPane pane = new JOptionPane(message, messageType, optionType);
        JDialog dialog = pane.createDialog(parent, title);
        hideMinimizeButton(dialog);
        dialog.setVisible(true);
        return pane.getValue() instanceof Integer value && value == JOptionPane.YES_OPTION;
    }

    static int showOption(Component parent, Object message, String title, int messageType,
            Object[] options, Object initialValue) {
        JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION,
                null, options, initialValue);
        JDialog dialog = pane.createDialog(parent, title);
        hideMinimizeButton(dialog);
        dialog.setVisible(true);
        Object selected = pane.getValue();
        for (int index = 0; index < options.length; index++) {
            if (options[index].equals(selected)) return index;
        }
        return JOptionPane.CLOSED_OPTION;
    }
}
