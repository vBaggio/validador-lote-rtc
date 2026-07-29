package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.presentation.UiThread;

import javax.swing.SwingUtilities;

/** Marshalling para a EDT. */
public final class SwingUiThread implements UiThread {

    @Override
    public void execute(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
