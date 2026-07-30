package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.application.ExternalSourcesPhase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalSourcesDialogTest {

    @Test
    void onlyApplyingPreventsClosingTheDialog() {
        assertThat(ExternalSourcesDialog.canClose(ExternalSourcesPhase.IDLE)).isTrue();
        assertThat(ExternalSourcesDialog.canClose(ExternalSourcesPhase.CHECKING)).isTrue();
        assertThat(ExternalSourcesDialog.canClose(ExternalSourcesPhase.APPLYING)).isFalse();
        assertThat(ExternalSourcesDialog.canClose(ExternalSourcesPhase.RESTART_REQUIRED)).isTrue();
    }
}
