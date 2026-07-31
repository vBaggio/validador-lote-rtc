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
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
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
                                    "v1", false, "local", null, null, null, null,
                                    ExternalSourcePhase.NOT_CHECKED,
                                    null, null, null)),
                    0, 0, false, 1);

            panel.showSnapshot(snapshot);

            assertThat(panel.sourceCardCount()).isEqualTo(2);
            assertThat(findComponents(panel, JScrollPane.class)).isNotEmpty();
        });
    }

    @Test
    void cardDetailsUseFriendlySingleLineOriginsAndKeepTheFullUrlInTooltip() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            panel.showSnapshot(new ExternalSourcesSnapshot(ExternalSourcesPhase.IDLE,
                    List.of(new ExternalSourceState(ArtifactId.NFE_SCHEMAS, "Schemas NF-e/NFC-e",
                            "010e_v1.02-r2", false, "https://dfe-portal.svrs.rs.gov.br/",
                            null, null, null, null,
                            ExternalSourcePhase.NOT_CHECKED, null, null, null)),
                    0, 0, false, 1));

            expand(panel, "Schemas NF-e/NFC-e");

            List<JLabel> labels = findComponents(panel, JLabel.class);
            assertThat(labels).extracting(JLabel::getText)
                    .contains("Base ativa", "010e_v1.02-r2", "Origem da base",
                            "Portal DF-e da SVRS")
                    .noneMatch(text -> text.contains("https://"));
            assertThat(labels).anyMatch(label ->
                    "https://dfe-portal.svrs.rs.gov.br/".equals(label.getToolTipText()));
        });
    }

    @Test
    void embeddedBaseIsExplainedInTheHeaderWithoutTakingADetailColumn() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            panel.showSnapshot(new ExternalSourcesSnapshot(ExternalSourcesPhase.IDLE,
                    List.of(new ExternalSourceState(ArtifactId.NFE_SCHEMAS,
                            "Schemas NF-e/NFC-e", "010e_v1.02", true,
                            "https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx",
                            null, null, null, null, ExternalSourcePhase.NOT_CHECKED,
                            null, null, null)),
                    0, 0, false, 1));

            expand(panel, "Schemas NF-e/NFC-e");

            assertThat(findComponents(panel, JLabel.class)).extracting(JLabel::getText)
                    .contains("Base ativa", "010e_v1.02", "Incluída no aplicativo")
                    .doesNotContain("Origem da base", "Portal Nacional da NF-e")
                    .doesNotContain("Base ativa (embarcada)");
        });
    }

    @Test
    void availableUpdateNamesTheCandidateRepositoryWithoutShowingItsUrl() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            panel.showSnapshot(new ExternalSourcesSnapshot(
                    ExternalSourcesPhase.UPDATES_AVAILABLE,
                    List.of(new ExternalSourceState(ArtifactId.NFE_SCHEMAS,
                            "Schemas NF-e/NFC-e", "010e_v1.02", true,
                            "https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx",
                            "https://vbaggio.github.io/validador-lote-rtc-bases/"
                                    + "releases/nfe-schemas/schemas-010e_v1.02-r2.zip",
                            null, null, null, ExternalSourcePhase.UPDATE_AVAILABLE,
                            null, null, "010e_v1.02-r2")),
                    1, 0, false, 2));

            assertThat(findComponents(panel, JLabel.class)).extracting(JLabel::getText)
                    .contains("Atualização disponível: 010e_v1.02-r2"
                            + " · vBaggio/validador-lote-rtc-bases")
                    .noneMatch(text -> text.contains("github.io"));
        });
    }

    @Test
    void sourcesStartCollapsedAndRevealDetailsOnlyWhenRequested() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            panel.showSnapshot(new ExternalSourcesSnapshot(ExternalSourcesPhase.IDLE,
                    List.of(new ExternalSourceState(ArtifactId.NFE_SCHEMAS,
                            "Schemas NF-e/NFC-e", "010e_v1.02", true,
                            "Canal curado", null, null, null, null,
                            ExternalSourcePhase.NOT_CHECKED, null, null, null)),
                    0, 0, false, 1));

            assertThat(findComponents(panel, JLabel.class)).extracting(JLabel::getText)
                    .contains("Schemas NF-e/NFC-e", "Ainda não verificada")
                    .doesNotContain("Base ativa", "010e_v1.02");

            expand(panel, "Schemas NF-e/NFC-e");

            assertThat(findComponents(panel, JLabel.class)).extracting(JLabel::getText)
                    .contains("Base ativa", "010e_v1.02", "Incluída no aplicativo");
        });
    }

    @Test
    void applyingDisablesEveryActionAndTheDialogPolicyRefusesClose() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            panel.showSnapshot(ExternalSourcesStatusBarTest.snapshot(ExternalSourcesPhase.APPLYING, 1, 0));

            assertThat(panel.enabledActionCount()).isZero();
            assertThat(ExternalSourcesDialog.canClose(ExternalSourcesPhase.APPLYING)).isFalse();
            assertThat(ExternalSourcesDialog.canClose(ExternalSourcesPhase.RELOADING_RUNTIME)).isFalse();
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
    void updatedRuntimeShowsSuccessAndOnlyNonTerminalActions() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            panel.showSnapshot(ExternalSourcesStatusBarTest.snapshot(
                    ExternalSourcesPhase.RELOADING_RUNTIME, 0, 0));
            assertThat(panel.enabledActionCount()).isZero();

            panel.showSnapshot(ExternalSourcesStatusBarTest.snapshot(
                    ExternalSourcesPhase.UPDATED_AND_IN_USE, 0, 0));
            assertThat(panel.summaryText()).isEqualTo("Bases atualizadas.");
            assertThat(panel.visibleActions()).containsExactly("Verificar agora", "Fechar");
            assertThat(ExternalSourcesDialog.canClose(ExternalSourcesPhase.UPDATED_AND_IN_USE)).isTrue();
        });
    }

    @Test
    void reloadingRuntimeDoesNotTellTheUserAppliedBasesNeedANextBoot() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            ExternalSourceState applied = ExternalSourcesStatusBarTest.source(
                    ArtifactId.NFE_SCHEMAS, ExternalSourcePhase.APPLIED);
            panel.showSnapshot(new ExternalSourcesSnapshot(ExternalSourcesPhase.RELOADING_RUNTIME,
                    List.of(applied), 0, 0, false, 3));

            assertThat(allOperationalLabels(panel)).extracting(JLabel::getText)
                    .contains("Carregando a base atualizada…")
                    .noneMatch(text -> text.contains("próximo boot"));
            assertThat(findComponents(panel, LoadingSpinner.class))
                    .anyMatch(LoadingSpinner::isRunning);
        });
    }

    @Test
    void restartFallbackUsesSanitizedRuntimeDetail() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            panel.showSnapshot(new ExternalSourcesSnapshot(ExternalSourcesPhase.RESTART_REQUIRED,
                    List.of(), 0, 0, false, 3,
                    "Base ativada em disco.\nReinicie\u0000 o aplicativo."));

            assertThat(panel.summaryText())
                    .isEqualTo("Base ativada em disco. Reinicie o aplicativo.")
                    .doesNotContain("\n");
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
    void primaryActionUsesStateInsteadOfItsVisibleText() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            java.util.concurrent.atomic.AtomicInteger applies = new java.util.concurrent.atomic.AtomicInteger();
            ExternalSourcesPanel panel = new ExternalSourcesPanel(() -> { }, applies::incrementAndGet,
                    () -> { }, () -> { });
            panel.showSnapshot(ExternalSourcesStatusBarTest.snapshot(
                    ExternalSourcesPhase.UPDATES_AVAILABLE, 1, 0));
            JButton update = findComponents(panel, JButton.class).stream()
                    .filter(button -> "Atualizar agora".equals(button.getText()))
                    .findFirst().orElseThrow();
            update.setText("Rótulo alterado");

            update.doClick();

            assertThat(applies).hasValue(1);
        });
    }

    @Test
    void panelExplainsThatCurrentSchemasRemainActiveAndAppUpdateIsRequired() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            ExternalSourceState schemas = new ExternalSourceState(
                    ArtifactId.NFE_SCHEMAS, "Schemas NF-e/NFC-e",
                    "010e_v1.02", true, "Canal curado", null, null, null, null,
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

    @Test
    void creditPresumedCardIsVisibleWithoutAnyUpdateAffordance() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            ExternalSourceState creditPresumed = new ExternalSourceState(
                    ArtifactId.CREDIT_PRESUMED_TABLE, "Tabela de crédito presumido (cCredPres)",
                    "13 códigos", true,
                    "https://dfe-portal.svrs.rs.gov.br/DFE/TabelaCreditoPresumido",
                    null, null, null, null,
                    ExternalSourcePhase.NOT_CHECKED, null, null, null);
            panel.showSnapshot(new ExternalSourcesSnapshot(ExternalSourcesPhase.IDLE,
                    List.of(ExternalSourcesStatusBarTest.source(ArtifactId.NFE_SCHEMAS,
                            ExternalSourcePhase.NOT_CHECKED), creditPresumed),
                    0, 0, false, 1));

            assertThat(panel.sourceCardCount()).isEqualTo(2);
            assertThat(findComponents(panel, JLabel.class)).extracting(JLabel::getText)
                    .contains("Tabela de crédito presumido (cCredPres)");

            expand(panel, "Tabela de crédito presumido (cCredPres)");

            assertThat(findComponents(panel, JLabel.class)).extracting(JLabel::getText)
                    .contains("Base ativa", "13 códigos", "Incluída no aplicativo", "Origem da base",
                            "Portal DF-e da SVRS");
            assertThat(allOperationalLabels(panel)).extracting(JLabel::getText)
                    .anyMatch(text -> text.contains("Tabela embarcada")
                            && text.contains("sem verificação automática nesta versão do app"));
        });
    }

    @Test
    void longStatusTextNeverPushesTheCardBeyondTheViewportNorClipsItsHeight() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            ExternalSourcesPanel panel = panel();
            // O cCredPres tem o texto de status mais longo do diálogo; é ele que estourava a
            // largura da coluna de cards, jogando borda direita e botão de expandir para fora da
            // área visível, e cortava a segunda linha contra uma altura fixa.
            panel.showSnapshot(new ExternalSourcesSnapshot(ExternalSourcesPhase.IDLE,
                    List.of(ExternalSourcesStatusBarTest.source(ArtifactId.NFE_SCHEMAS,
                                    ExternalSourcePhase.NOT_CHECKED),
                            new ExternalSourceState(ArtifactId.CREDIT_PRESUMED_TABLE,
                                    "Tabela de crédito presumido (cCredPres)", "13 códigos", true,
                                    "https://dfe-portal.svrs.rs.gov.br/DFE/TabelaCreditoPresumido",
                                    null, null, null, null,
                                    ExternalSourcePhase.NOT_CHECKED, null, null, null)),
                    0, 0, false, 1));

            JScrollPane scroll = findComponents(panel, JScrollPane.class).getFirst();
            Component view = scroll.getViewport().getView();

            // Sem isto a coluna fica mais larga que o viewport e, como não há barra horizontal,
            // o que passa da borda é irrecuperável.
            assertThat(view).isInstanceOf(Scrollable.class);
            assertThat(((Scrollable) view).getScrollableTracksViewportWidth()).isTrue();

            // Altura fixa cortaria o texto que quebra em duas linhas.
            for (Component card : ((Container) view).getComponents()) {
                if (!(card instanceof JPanel bordered) || bordered.getBorder() == null) continue;
                assertThat(bordered.getMaximumSize().height)
                        .isEqualTo(bordered.getPreferredSize().height);
            }
        });
    }

    private static ExternalSourcesPanel panel() {
        return new ExternalSourcesPanel(() -> { }, () -> { }, () -> { }, () -> { });
    }

    private static void expand(ExternalSourcesPanel panel, String sourceName) {
        findComponents(panel, JButton.class).stream()
                .filter(button -> ("Ver detalhes de " + sourceName).equals(
                        button.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow().doClick();
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
