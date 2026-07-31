package br.com.validadorlote.presentation.swing;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.BorderFactory;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Window;
import javax.swing.SwingUtilities;

/** Decoração comum dos diálogos da aplicação, sem o controle de minimizar. */
final class SwingDialogSupport {

    private SwingDialogSupport() { }

    static void hideMinimizeButton(JDialog dialog) {
        // JOptionPane.createDialog() já chama pack() e cria o peer. Descartá-lo permite
        // trocar a decoração antes do novo pack, evitando uma falha silenciosa no observer.
        if (dialog.isDisplayable()) {
            dialog.dispose();
        }
        dialog.setUndecorated(true);
        dialog.getRootPane().setWindowDecorationStyle(JRootPane.PLAIN_DIALOG);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(new Color(78, 78, 78)));
        dialog.getRootPane().putClientProperty("JRootPane.titleBarShowIconify", false);
        dialog.getRootPane().putClientProperty("JRootPane.titleBarShowMaximize", false);
    }

    static void showMessage(Component parent, Object message, String title, int messageType) {
        JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION);
        JDialog dialog = dialog(parent, title, pane);
        dialog.setVisible(true);
    }

    static boolean showConfirm(Component parent, Object message, String title, int optionType,
            int messageType) {
        JOptionPane pane = new JOptionPane(message, messageType, optionType);
        JDialog dialog = dialog(parent, title, pane);
        dialog.setVisible(true);
        return pane.getValue() instanceof Integer value && value == JOptionPane.YES_OPTION;
    }

    static int showOption(Component parent, Object message, String title, int messageType,
            Object[] options, Object initialValue) {
        JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION,
                null, options, initialValue);
        JDialog dialog = dialog(parent, title, pane);
        dialog.setVisible(true);
        Object selected = pane.getValue();
        for (int index = 0; index < options.length; index++) {
            if (options[index].equals(selected)) return index;
        }
        return JOptionPane.CLOSED_OPTION;
    }

    private static JDialog dialog(Component parent, String title, JOptionPane pane) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        hideMinimizeButton(dialog);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(pane);
        pane.addPropertyChangeListener(event -> {
            if (dialog.isVisible() && event.getSource() == pane
                    && JOptionPane.VALUE_PROPERTY.equals(event.getPropertyName())
                    && event.getNewValue() != JOptionPane.UNINITIALIZED_VALUE) {
                dialog.setVisible(false);
            }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        return dialog;
    }
}
