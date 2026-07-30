package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.application.ExternalSourcePhase;
import br.com.validadorlote.application.ExternalSourceState;
import br.com.validadorlote.application.ExternalSourcesPhase;
import br.com.validadorlote.application.ExternalSourcesSnapshot;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalSourcesStatusBarTest {

    @Test
    void footerRendersCheckingAvailablePartialAndFailedStates() throws Exception {
        runOnEdt(() -> {
            ExternalSourcesStatusBar statusBar = statusBar(() -> { });

            statusBar.showSnapshot(snapshot(ExternalSourcesPhase.CHECKING, 0, 0));
            assertThat(statusBar.statusText()).isEqualTo("Consultando atualizações das bases…");
            assertThat(statusBar.isSpinnerRunning()).isTrue();

            statusBar.showSnapshot(snapshot(ExternalSourcesPhase.UPDATES_AVAILABLE, 1, 1));
            assertThat(statusBar.statusText())
                    .isEqualTo("Atualizações de bases disponíveis · 1 fonte não respondeu");

            statusBar.showSnapshot(snapshot(ExternalSourcesPhase.FAILED, 0, 2));
            assertThat(statusBar.isRetryVisible()).isTrue();
        });
    }

    @Test
    void retryIsAnIconOnlyActionWithTooltipAndOneCallbackPerClick() throws Exception {
        runOnEdt(() -> {
            AtomicInteger retries = new AtomicInteger();
            ExternalSourcesStatusBar statusBar = statusBar(retries::incrementAndGet);
            statusBar.showSnapshot(snapshot(ExternalSourcesPhase.FAILED, 0, 2));

            assertThat(statusBar.retryText()).isEmpty();
            assertThat(statusBar.retryTooltip()).isEqualTo("Tentar consultar as bases novamente");
            statusBar.clickRetry();
            assertThat(retries).hasValue(1);
        });
    }

    private static ExternalSourcesStatusBar statusBar(Runnable retry) {
        return new ExternalSourcesStatusBar("0.1.0", "010e", () -> { }, retry);
    }

    static ExternalSourcesSnapshot snapshot(ExternalSourcesPhase phase, int available, int failed) {
        return new ExternalSourcesSnapshot(phase, List.of(
                source(ArtifactId.NFE_SCHEMAS, ExternalSourcePhase.UPDATE_AVAILABLE),
                source(ArtifactId.FISCAL_TABLES, failed > 0
                        ? ExternalSourcePhase.FAILED : ExternalSourcePhase.UP_TO_DATE)),
                available, failed, false, 1);
    }

    static ExternalSourceState source(ArtifactId artifact, ExternalSourcePhase phase) {
        return new ExternalSourceState(artifact,
                artifact == ArtifactId.NFE_SCHEMAS ? "Schemas NF-e/NFC-e" : "Tabela CST/cClassTrib",
                "010e", "https://dfe-portal.svrs.rs.gov.br/", "123456", null, null,
                phase, null, null, phase == ExternalSourcePhase.UPDATE_AVAILABLE ? "010f" : null);
    }

    static void runOnEdt(ThrowingRunnable runnable) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    runnable.run();
                } catch (Throwable error) {
                    failure.set(error);
                }
            });
            if (failure.get() instanceof Exception exception) throw exception;
            if (failure.get() instanceof Error error) throw error;
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
