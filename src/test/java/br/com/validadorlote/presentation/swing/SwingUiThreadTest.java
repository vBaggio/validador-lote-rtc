package br.com.validadorlote.presentation.swing;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class SwingUiThreadTest {

    @Test
    void executeLaterAlwaysWaitsForTheCurrentEdtDispatchToReturn() throws Exception {
        var events = new ArrayList<String>();
        var uiThread = new SwingUiThread();

        SwingUtilities.invokeAndWait(() -> {
            events.add("before");
            uiThread.executeLater(() -> events.add("deferred"));
            events.add("after");

            assertThat(events).containsExactly("before", "after");
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(events).containsExactly("before", "after", "deferred");
    }
}
