package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.application.ExternalSourcesPhase;
import br.com.validadorlote.application.ExternalSourcesSnapshot;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/** Hospeda o painel de bases e aplica a política de modalidade e fechamento. */
final class ExternalSourcesDialog extends JDialog {

    private static final String ESCAPE_CLOSE = "close-external-sources";
    private final ExternalSourcesPanel panel;

    ExternalSourcesDialog(Window owner, Runnable checkNow, Runnable applyAvailable, Runnable retry,
            Runnable closeApplication) {
        super(owner, "Atualização de bases", Dialog.ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(false);
        panel = new ExternalSourcesPanel(checkNow, applyAvailable, retry, closeApplication);
        panel.setCloseDialog(() -> setVisible(false));
        setContentPane(panel);
        getRootPane().getActionMap().put(ESCAPE_CLOSE, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                if (canCloseCurrentPhase()) setVisible(false);
            }
        });
        installEscapeCloseBinding();
        pack();
        limitToUsableScreen();
        setLocationRelativeTo(owner);
    }

    void showSnapshot(ExternalSourcesSnapshot snapshot) {
        panel.showSnapshot(snapshot);
        applyClosePolicy(snapshot.phase());
        pack();
        limitToUsableScreen();
    }

    void open() {
        if (isVisible()) return;
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    boolean isOpen() {
        return isVisible();
    }

    static boolean canClose(ExternalSourcesPhase phase) {
        return phase != ExternalSourcesPhase.APPLYING;
    }

    private boolean canCloseCurrentPhase() {
        return getDefaultCloseOperation() != DO_NOTHING_ON_CLOSE;
    }

    private void applyClosePolicy(ExternalSourcesPhase phase) {
        if (canClose(phase)) {
            setDefaultCloseOperation(HIDE_ON_CLOSE);
            installEscapeCloseBinding();
        } else {
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .remove(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        }
    }

    private void installEscapeCloseBinding() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), ESCAPE_CLOSE);
    }

    private void limitToUsableScreen() {
        GraphicsConfiguration configuration = getGraphicsConfiguration();
        if (configuration == null) return;
        java.awt.Rectangle bounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        int usableWidth = Math.max(1, bounds.width - insets.left - insets.right);
        int usableHeight = Math.max(1, bounds.height - insets.top - insets.bottom);
        int maxWidth = (int) (usableWidth * .85d);
        int maxHeight = (int) (usableHeight * .85d);
        Dimension current = getSize();
        if (current.width > maxWidth || current.height > maxHeight) {
            setSize(Math.min(current.width, maxWidth), Math.min(current.height, maxHeight));
        }
    }
}
