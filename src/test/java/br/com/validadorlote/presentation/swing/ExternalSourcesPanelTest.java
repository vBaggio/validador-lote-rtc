package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.application.ExternalSourcePhase;
import br.com.validadorlote.application.ExternalSourceState;
import br.com.validadorlote.application.ExternalSourcesPhase;
import br.com.validadorlote.application.ExternalSourcesSnapshot;
import br.com.validadorlote.infrastructure.update.ArtifactFailureKind;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalSourcesPanelTest {

    @Test
    void panelContainsOnlyActiveV0SourcesAndUsesScrollPane() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            ExternalSourcesSnapshot snapshot = new ExternalSourcesSnapshot(ExternalSourcesPhase.IDLE,
                    List.of(
                            ExternalSourcesStatusBarTest.source(ArtifactId.NFE_SCHEMAS, ExternalSourcePhase.NOT_CHECKED),
                            ExternalSourcesStatusBarTest.source(ArtifactId.FISCAL_TABLES, ExternalSourcePhase.NOT_CHECKED),
                            new ExternalSourceState(ArtifactId.CALCULATOR, "Nome de apresentação alterável",
                                    "v1", "local", null, null, null, ExternalSourcePhase.NOT_CHECKED,
                                    null, null, null)),
                    0, 0, false, 1);

            panel.showSnapshot(snapshot);

            assertThat(panel.sourceCardCount()).isEqualTo(2);
            assertThat(findComponents(panel, JScrollPane.class)).isNotEmpty();
        });
    }

    @Test
    void applyingDisablesEveryActionAndTheDialogPolicyRefusesClose() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            panel.showSnapshot(ExternalSourcesStatusBarTest.snapshot(ExternalSourcesPhase.APPLYING, 1, 0));

            assertThat(panel.enabledActionCount()).isZero();
            assertThat(ExternalSourcesDialog.canClose(ExternalSourcesPhase.APPLYING)).isFalse();
            assertThat(ExternalSourcesDialog.canClose(ExternalSourcesPhase.RESTART_REQUIRED)).isTrue();
        });
    }

    @Test
    void rebuildingCardsStopsSpinnersRemovedByATerminalSnapshot() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            panel.showSnapshot(activitySnapshot(ExternalSourcesPhase.CHECKING, ExternalSourcePhase.CHECKING));
            List<LoadingSpinner> removedSpinners = findComponents(panel, LoadingSpinner.class);
            assertThat(removedSpinners).allMatch(LoadingSpinner::isRunning);

            panel.showSnapshot(activitySnapshot(ExternalSourcesPhase.UP_TO_DATE, ExternalSourcePhase.UP_TO_DATE));

            assertThat(removedSpinners).allMatch(spinner -> !spinner.isRunning());
            assertThat(findComponents(panel, LoadingSpinner.class)).isEmpty();
        });
    }

    @Test
    void operationalIconsAreDrawnVectorsAndNotUnicodeText() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            panel.showSnapshot(ExternalSourcesStatusBarTest.snapshot(ExternalSourcesPhase.CHECKING, 0, 0));

            assertThat(allOperationalLabels(panel))
                    .noneMatch(label -> label.getText().matches(".*[●○◌].*"));
            assertThat(allOperationalLabels(panel)).allMatch(label -> label.getIcon() != null
                    || !label.getText().contains("atualiza"));
        });
    }

    @Test
    void actionsFollowTheSharedSnapshot() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            panel.showSnapshot(ExternalSourcesStatusBarTest.snapshot(ExternalSourcesPhase.IDLE, 0, 0));
            assertThat(panel.visibleActions()).containsExactly("Verificar agora", "Fechar");

            panel.showSnapshot(ExternalSourcesStatusBarTest.snapshot(ExternalSourcesPhase.UPDATES_AVAILABLE, 1, 0));
            assertThat(panel.visibleActions()).containsExactly("Atualizar agora", "Fechar");

            panel.showSnapshot(ExternalSourcesStatusBarTest.snapshot(ExternalSourcesPhase.FAILED, 0, 2));
            assertThat(panel.visibleActions()).containsExactly("Tentar novamente", "Fechar");

            panel.showSnapshot(ExternalSourcesStatusBarTest.snapshot(ExternalSourcesPhase.RESTART_REQUIRED, 0, 0));
            assertThat(ExternalSourcesDialog.canClose(ExternalSourcesPhase.RESTART_REQUIRED)).isTrue();
            assertThat(panel.summaryText())
                    .isEqualTo("Atualização concluída. Reinicie o aplicativo para usar as novas versões.");
        });
    }

    @Test
    void updateActionInvokesOnlyTheApplyCallback() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            java.util.concurrent.atomic.AtomicInteger applies = new java.util.concurrent.atomic.AtomicInteger();
            ExternalSourcesPanel panel = new ExternalSourcesPanel(() -> { }, applies::incrementAndGet,
                    () -> { }, () -> { });
            panel.showSnapshot(ExternalSourcesStatusBarTest.snapshot(
                    ExternalSourcesPhase.UPDATES_AVAILABLE, 1, 0));

            findComponents(panel, JButton.class).stream()
                    .filter(button -> "Atualizar agora".equals(button.getText()))
                    .findFirst().orElseThrow().doClick();

            assertThat(applies).hasValue(1);
        });
    }

    @Test
    void panelExplainsThatCurrentSchemasRemainActiveAndAppUpdateIsRequired() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            ExternalSourceState schemas = new ExternalSourceState(
                    ArtifactId.NFE_SCHEMAS, "Schemas NF-e/NFC-e",
                    "010e_v1.02 (embarcada)", "Canal curado", null, null, null,
                    ExternalSourcePhase.FAILED,
                    "A estrutura dos schemas mais recentes não é suportada",
                    ArtifactFailureKind.UNSUPPORTED_SCHEMA_STRUCTURE, null);
            panel.showSnapshot(new ExternalSourcesSnapshot(ExternalSourcesPhase.FAILED,
                    List.of(schemas), 0, 1, false, 2));

            String text = allOperationalLabels(panel).stream()
                    .map(JLabel::getText)
                    .reduce("", (left, right) -> left + " " + right)
                    .toLowerCase(java.util.Locale.ROOT);
            assertThat(text)
                    .contains("uma base mais nova foi encontrada")
                    .contains("estrutura não é suportada por esta versão do aplicativo")
                    .contains("base atual foi mantida")
                    .contains("atualize o aplicativo")
                    .contains("validações que não dependem do schema mais novo continuam normalmente");
        });
    }

    private static ExternalSourcesPanel panel() {
        return new ExternalSourcesPanel(() -> { }, () -> { }, () -> { }, () -> { });
    }

    private static ExternalSourcesSnapshot activitySnapshot(ExternalSourcesPhase phase,
            ExternalSourcePhase sourcePhase) {
        return new ExternalSourcesSnapshot(phase, List.of(
                ExternalSourcesStatusBarTest.source(ArtifactId.NFE_SCHEMAS, sourcePhase),
                ExternalSourcesStatusBarTest.source(ArtifactId.FISCAL_TABLES, sourcePhase)),
                0, 0, false, 2);
    }

    private static List<JLabel> allOperationalLabels(Container root) {
        return findComponents(root, JLabel.class);
    }

    private static <T extends Component> List<T> findComponents(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) result.add(type.cast(component));
            if (component instanceof Container child) result.addAll(findComponents(child, type));
        }
        return result;
    }
}
