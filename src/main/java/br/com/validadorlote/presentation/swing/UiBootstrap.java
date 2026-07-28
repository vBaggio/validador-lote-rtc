package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.application.ValidateBatchUseCase;
import br.com.validadorlote.presentation.MainPresenter;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.SwingUtilities;
import java.util.concurrent.Executors;

/** Sobe a UI Swing: tema, presenter e frame na EDT. */
public final class UiBootstrap {

    private UiBootstrap() {}

    public static void launch(ValidateBatchUseCase useCase, String schemasVersion) {
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            var presenter = new MainPresenter(useCase, new SwingUiThread(),
                    Executors.newSingleThreadExecutor(r -> {
                        Thread thread = new Thread(r, "batch-runner");
                        thread.setDaemon(true);
                        return thread;
                    }));
            MainFrame frame = new MainFrame(presenter, schemasVersion);
            presenter.attach(frame);
            frame.setVisible(true);
        });
    }
}
