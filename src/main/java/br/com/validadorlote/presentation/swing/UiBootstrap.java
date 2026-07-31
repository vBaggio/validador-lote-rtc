package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.application.ValidateBatchUseCase;
import br.com.validadorlote.application.ExternalSourcesUseCase;
import br.com.validadorlote.application.ApplicationUpdateUseCase;
import br.com.validadorlote.presentation.MainPresenter;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Font;
import java.util.concurrent.Executors;

/** Sobe a UI Swing: tema, presenter e frame na EDT. */
public final class UiBootstrap {

    private UiBootstrap() {}

    public static void launch(ValidateBatchUseCase useCase, String schemasVersion) {
        launch("0.1.0", useCase, schemasVersion, null, null, () -> { });
    }

    /** O callback roda após a janela visível; trabalho de rede deve apenas agendar executor próprio. */
    public static void launch(ValidateBatchUseCase useCase, String schemasVersion, Runnable afterVisible) {
        launch("0.1.0", useCase, schemasVersion, null, null, afterVisible);
    }

    /** O callback roda após a janela visível; trabalho de rede deve apenas agendar executor próprio. */
    public static void launch(ValidateBatchUseCase useCase, String schemasVersion,
            ExternalSourcesUseCase externalSources, Runnable afterVisible) {
        launch("0.1.0", useCase, schemasVersion, externalSources, null, afterVisible);
    }

    /** Mantém a consulta de release fora da EDT e só agenda o modal após o frame ser visível. */
    public static void launch(String applicationVersion, ValidateBatchUseCase useCase, String schemasVersion,
            ExternalSourcesUseCase externalSources, ApplicationUpdateUseCase applicationUpdate,
            Runnable afterVisible) {
        launch(applicationVersion, useCase, schemasVersion, "", externalSources, applicationUpdate,
                afterVisible);
    }

    public static void launch(String applicationVersion, ValidateBatchUseCase useCase, String schemasVersion,
            String tableVersion, ExternalSourcesUseCase externalSources,
            ApplicationUpdateUseCase applicationUpdate, Runnable afterVisible) {
        SwingUtilities.invokeLater(() -> {
            FlatRobotoFont.install();
            FlatDarkLaf.setup();
            UIManager.put("defaultFont", new Font(FlatRobotoFont.FAMILY, Font.PLAIN, 15));
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 10);
            UIManager.put("Component.focusWidth", 1);
            var presenter = new MainPresenter(useCase, new SwingUiThread(),
                    Executors.newSingleThreadExecutor(r -> {
                        Thread thread = new Thread(r, "batch-runner");
                        thread.setDaemon(true);
                        return thread;
                    }), externalSources);
            MainFrame frame = new MainFrame(presenter, applicationVersion, schemasVersion, tableVersion);
            presenter.attach(frame);
            frame.setVisible(true);
            if (applicationUpdate != null) {
                applicationUpdate.checkAfterVisible(release ->
                        SwingUtilities.invokeLater(() -> frame.showApplicationUpdate(release)));
            }
            afterVisible.run();
        });
    }
}
