package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.application.ExternalSourcesPhase;
import br.com.validadorlote.application.ExternalSourcesSnapshot;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.Objects;

/** Rodapé que expõe o estado agregado das bases sem disputar atenção com o lote. */
final class ExternalSourcesStatusBar extends JPanel {

    private static final Color MUTED = new Color(150, 150, 150);
    private static final Color SUCCESS = new Color(91, 190, 126);
    private static final Color WARNING = new Color(232, 180, 76);
    private static final Color ERROR = new Color(225, 96, 96);

    private final JLabel status = new JLabel();
    private final LoadingSpinner spinner = new LoadingSpinner();
    private final JButton openSources = new JButton("Bases e atualizações");
    private final JButton retry = new JButton();

    ExternalSourcesStatusBar(String applicationVersion, String schemasVersion, Runnable openSources,
            Runnable retry) {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        JLabel version = new JLabel("v" + applicationVersion + "  ·  " + compactSchemaVersion(schemasVersion));
        version.setForeground(MUTED);
        version.setToolTipText(schemasVersion);
        add(version, BorderLayout.CENTER);

        JPanel state = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        state.setOpaque(false);
        spinner.setForeground(WARNING);
        state.add(spinner);
        status.getAccessibleContext().setAccessibleName("Estado das atualizações das bases");
        state.add(status);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actions.setOpaque(false);
        configureButton(this.openSources, new OutlineIcon(OutlineIcon.Kind.DATABASE),
                "Abrir detalhes das bases e atualizações", openSources);
        this.openSources.setText("");
        configureButton(this.retry, new OutlineIcon(OutlineIcon.Kind.REFRESH),
                "Tentar consultar as bases novamente", retry);
        this.retry.setText("");
        this.retry.setVisible(false);
        actions.add(state);
        actions.add(this.retry);
        actions.add(this.openSources);
        add(actions, BorderLayout.EAST);
    }

    void showSnapshot(ExternalSourcesSnapshot snapshot) {
        StatusPresentation presentation = presentation(snapshot);
        spinner.setRunning(snapshot.phase() == ExternalSourcesPhase.CHECKING
                || snapshot.phase() == ExternalSourcesPhase.APPLYING
                || snapshot.phase() == ExternalSourcesPhase.RELOADING_RUNTIME);
        status.setText(presentation.text());
        status.setForeground(presentation.color());
        status.setIcon(presentation.icon());
        status.setToolTipText(presentation.text());
        retry.setVisible(snapshot.phase() == ExternalSourcesPhase.FAILED);
    }

    String statusText() {
        return status.getText();
    }

    javax.swing.Icon statusIcon() {
        return status.getIcon();
    }

    boolean isSpinnerRunning() {
        return spinner.isRunning();
    }

    boolean isRetryVisible() {
        return retry.isVisible();
    }

    String retryText() {
        return retry.getText();
    }

    String openSourcesText() {
        return openSources.getText();
    }

    String retryTooltip() {
        return retry.getToolTipText();
    }

    void clickRetry() {
        retry.doClick();
    }

    private static void configureButton(JButton button, OutlineIcon icon, String tooltip,
            Runnable action) {
        button.setIcon(icon);
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(tooltip);
        button.addActionListener(event -> action.run());
    }

    private static StatusPresentation presentation(ExternalSourcesSnapshot snapshot) {
        String partial = partialSuffix(snapshot.failedCount());
        return switch (snapshot.phase()) {
            case IDLE -> new StatusPresentation("Bases ainda não verificadas", MUTED,
                    new OutlineIcon(OutlineIcon.Kind.DATABASE, 18, MUTED));
            case CHECKING -> new StatusPresentation("Consultando atualizações das bases…", WARNING,
                    null);
            case UP_TO_DATE -> new StatusPresentation("Bases verificadas e atualizadas" + partial,
                    SUCCESS, new OutlineIcon(OutlineIcon.Kind.CORRECT, 18, SUCCESS));
            case UPDATES_AVAILABLE -> new StatusPresentation("Atualizações de bases disponíveis" + partial,
                    WARNING, new OutlineIcon(OutlineIcon.Kind.WARNING, 18, WARNING));
            case WAITING_FOR_VALIDATION -> new StatusPresentation(
                    "Atualização disponível · aguardando o fim da validação" + partial,
                    WARNING, new OutlineIcon(OutlineIcon.Kind.WARNING, 18, WARNING));
            case APPLYING -> new StatusPresentation("Atualizando as bases verificadas…" + partial,
                    WARNING, null);
            case RELOADING_RUNTIME -> new StatusPresentation("Carregando as bases atualizadas…" + partial,
                    WARNING, null);
            case UPDATED_AND_IN_USE -> new StatusPresentation("Bases atualizadas e já em uso" + partial,
                    SUCCESS, new OutlineIcon(OutlineIcon.Kind.CORRECT, 18, SUCCESS));
            case RESTART_REQUIRED -> new StatusPresentation(
                    "Bases atualizadas · reinicie para usar as novas versões" + partial,
                    SUCCESS, new OutlineIcon(OutlineIcon.Kind.CORRECT, 18, SUCCESS));
            case FAILED -> new StatusPresentation("Não foi possível consultar as bases", ERROR,
                    new OutlineIcon(OutlineIcon.Kind.ERROR, 18, ERROR));
        };
    }

    private static String partialSuffix(int failedCount) {
        if (failedCount == 0) return "";
        return failedCount == 1 ? " · 1 fonte não respondeu"
                : " · " + failedCount + " fontes não responderam";
    }

    private static String compactSchemaVersion(String provenance) {
        String value = Objects.requireNonNullElse(provenance, "").strip();
        int details = value.indexOf(" (");
        return details > 0 ? value.substring(0, details) : value;
    }

    private record StatusPresentation(String text, Color color, OutlineIcon icon) { }
}
