package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.application.ExternalSourcePhase;
import br.com.validadorlote.application.ExternalSourceState;
import br.com.validadorlote.application.ExternalSourcesPhase;
import br.com.validadorlote.application.ExternalSourcesSnapshot;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Conteúdo adaptável do diálogo de bases; renderiza somente o snapshot recebido. */
final class ExternalSourcesPanel extends JPanel {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final Color MUTED = new Color(160, 160, 160);
    private static final Color SUCCESS = new Color(91, 190, 126);
    private static final Color WARNING = new Color(232, 180, 76);
    private static final Color ERROR = new Color(225, 96, 96);

    private final JPanel sourceCards = new JPanel();
    private final JLabel summary = new JLabel();
    private final JButton primaryAction = new JButton();
    private final JButton closeAction = new JButton();
    private final Runnable checkNow;
    private final Runnable applyAvailable;
    private final Runnable retry;
    private final Runnable closeApplication;
    private Runnable closeDialog = () -> { };
    private int sourceCardCount;

    ExternalSourcesPanel(Runnable checkNow, Runnable applyAvailable, Runnable retry,
            Runnable closeApplication) {
        super(new BorderLayout(0, 16));
        this.checkNow = checkNow;
        this.applyAvailable = applyAvailable;
        this.retry = retry;
        this.closeApplication = closeApplication;
        setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));

        JLabel title = new JLabel("Bases de validação");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JLabel subtitle = new JLabel("Atualizações são verificadas sem enviar XMLs ou dados do lote.");
        subtitle.setForeground(MUTED);
        JPanel heading = new JPanel();
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(title);
        heading.add(Box.createVerticalStrut(5));
        heading.add(subtitle);
        summary.setVisible(false);
        summary.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        heading.add(summary);
        add(heading, BorderLayout.NORTH);

        sourceCards.setLayout(new BoxLayout(sourceCards, BoxLayout.Y_AXIS));
        sourceCards.setOpaque(false);
        JScrollPane scroll = new JScrollPane(sourceCards,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.setPreferredSize(new Dimension(740, 340));
        add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        primaryAction.addActionListener(event -> runPrimaryAction());
        closeAction.addActionListener(event -> closeDialog.run());
        actions.add(primaryAction);
        actions.add(closeAction);
        add(actions, BorderLayout.SOUTH);
    }

    void setCloseDialog(Runnable closeDialog) {
        this.closeDialog = closeDialog;
    }

    void showSnapshot(ExternalSourcesSnapshot snapshot) {
        rebuildCards(snapshot.sources());
        configureSummary(snapshot.phase());
        configureActions(snapshot.phase());
    }

    int sourceCardCount() {
        return sourceCardCount;
    }

    int enabledActionCount() {
        int count = 0;
        if (primaryAction.isVisible() && primaryAction.isEnabled()) count++;
        if (closeAction.isVisible() && closeAction.isEnabled()) count++;
        return count;
    }

    List<String> visibleActions() {
        List<String> actions = new ArrayList<>();
        if (primaryAction.isVisible()) actions.add(primaryAction.getText());
        if (closeAction.isVisible()) actions.add(closeAction.getText());
        return actions;
    }

    String summaryText() {
        return summary.getText();
    }

    private void configureSummary(ExternalSourcesPhase phase) {
        if (phase == ExternalSourcesPhase.RESTART_REQUIRED) {
            summary.setText("Atualização concluída. Reinicie o aplicativo para usar as novas versões.");
            summary.setIcon(new OutlineIcon(OutlineIcon.Kind.CORRECT, 18, SUCCESS));
            summary.setForeground(SUCCESS);
            summary.setVisible(true);
        } else {
            summary.setText("");
            summary.setIcon(null);
            summary.setVisible(false);
        }
    }

    private void rebuildCards(List<ExternalSourceState> sources) {
        stopRemovedSpinners(sourceCards);
        sourceCards.removeAll();
        sourceCardCount = 0;
        for (ExternalSourceState source : sources) {
            if (source.isCalculator()) continue;
            sourceCards.add(sourceCard(source));
            sourceCards.add(Box.createVerticalStrut(10));
            sourceCardCount++;
        }
        sourceCards.revalidate();
        sourceCards.repaint();
    }

    private static void stopRemovedSpinners(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof LoadingSpinner spinner) {
                spinner.setRunning(false);
            }
            if (component instanceof Container child) {
                stopRemovedSpinners(child);
            }
        }
    }

    private void configureActions(ExternalSourcesPhase phase) {
        primaryAction.setVisible(true);
        closeAction.setVisible(true);
        primaryAction.setEnabled(true);
        closeAction.setEnabled(true);
        switch (phase) {
            case UPDATES_AVAILABLE -> {
                primaryAction.setText("Atualizar agora");
                primaryAction.setIcon(new OutlineIcon(OutlineIcon.Kind.DATABASE));
                closeAction.setText("Fechar");
            }
            case FAILED -> {
                primaryAction.setText("Tentar novamente");
                primaryAction.setIcon(new OutlineIcon(OutlineIcon.Kind.REFRESH));
                closeAction.setText("Fechar");
            }
            case APPLYING -> {
                primaryAction.setText("Atualizando…");
                primaryAction.setIcon(new OutlineIcon(OutlineIcon.Kind.REFRESH));
                closeAction.setText("Fechar");
                primaryAction.setEnabled(false);
                closeAction.setEnabled(false);
            }
            case RESTART_REQUIRED -> {
                primaryAction.setText("Encerrar agora");
                primaryAction.setIcon(new OutlineIcon(OutlineIcon.Kind.ERROR));
                closeAction.setText("Continuar e reiniciar depois");
            }
            case IDLE, CHECKING, UP_TO_DATE, WAITING_FOR_VALIDATION -> {
                primaryAction.setText("Verificar agora");
                primaryAction.setIcon(new OutlineIcon(OutlineIcon.Kind.REFRESH));
                primaryAction.setEnabled(phase != ExternalSourcesPhase.CHECKING
                        && phase != ExternalSourcesPhase.WAITING_FOR_VALIDATION);
                closeAction.setText("Fechar");
            }
        }
        primaryAction.getAccessibleContext().setAccessibleName(primaryAction.getText());
        closeAction.getAccessibleContext().setAccessibleName(closeAction.getText());
    }

    private void runPrimaryAction() {
        switch (primaryAction.getText()) {
            case "Atualizar agora" -> applyAvailable.run();
            case "Tentar novamente" -> retry.run();
            case "Encerrar agora" -> closeApplication.run();
            case "Verificar agora" -> checkNow.run();
            default -> { }
        }
    }

    private JPanel sourceCard(ExternalSourceState source) {
        JPanel card = new JPanel(new BorderLayout(14, 12));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(78, 78, 78)),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));

        JLabel name = new JLabel(source.name());
        name.setFont(name.getFont().deriveFont(Font.BOLD, 15f));
        JLabel purpose = new JLabel(purpose(source.name()));
        purpose.setForeground(MUTED);
        JPanel title = new JPanel();
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
        title.add(name);
        title.add(Box.createVerticalStrut(3));
        title.add(purpose);
        card.add(title, BorderLayout.NORTH);

        card.add(feedback(source), BorderLayout.CENTER);
        JPanel details = new JPanel(new GridLayout(1, 3, 18, 0));
        details.add(detail("Base ativa", source.activeVersion()));
        details.add(detail("Última verificação", format(source.checkedAt())));
        details.add(detail("Origem", origin(source.origin())));
        card.add(details, BorderLayout.SOUTH);
        return card;
    }

    private JPanel feedback(ExternalSourceState source) {
        Feedback presentation = feedbackFor(source);
        JPanel feedback = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        feedback.setOpaque(false);
        if (source.phase() == ExternalSourcePhase.CHECKING
                || source.phase() == ExternalSourcePhase.APPLYING) {
            LoadingSpinner spinner = new LoadingSpinner();
            spinner.setForeground(presentation.color());
            spinner.setRunning(true);
            feedback.add(spinner);
        }
        JLabel label = new JLabel(presentation.text(), presentation.icon(), JLabel.LEFT);
        label.setForeground(presentation.color());
        label.getAccessibleContext().setAccessibleName(presentation.text());
        feedback.add(label);
        return feedback;
    }

    private static Feedback feedbackFor(ExternalSourceState source) {
        String detail = source.detail();
        return switch (source.phase()) {
            case NOT_CHECKED -> new Feedback("Ainda não verificada", MUTED,
                    new OutlineIcon(OutlineIcon.Kind.DATABASE, 18, MUTED));
            case CHECKING -> new Feedback("Verificando atualização…", WARNING, null);
            case UP_TO_DATE -> new Feedback(detail == null ? "Base já está atualizada" : detail,
                    SUCCESS, new OutlineIcon(OutlineIcon.Kind.CORRECT, 18, SUCCESS));
            case UPDATE_AVAILABLE -> new Feedback("Atualização disponível: " + source.candidateVersion(),
                    WARNING, new OutlineIcon(OutlineIcon.Kind.WARNING, 18, WARNING));
            case APPLYING -> new Feedback("Aplicando atualização…", WARNING, null);
            case APPLIED -> new Feedback("Atualização pronta para o próximo boot", SUCCESS,
                    new OutlineIcon(OutlineIcon.Kind.CORRECT, 18, SUCCESS));
            case FAILED -> new Feedback(detail == null ? "Não foi possível consultar esta fonte" : detail,
                    ERROR, new OutlineIcon(OutlineIcon.Kind.ERROR, 18, ERROR));
        };
    }

    private static JLabel detail(String label, String value) {
        return new JLabel("<html><span style='color:#9c9c9c'>" + label + "</span><br>"
                + escape(value == null ? "—" : value) + "</html>");
    }

    private static String purpose(String sourceName) {
        return switch (sourceName) {
            case "Schemas NF-e/NFC-e" -> "Estrutura de XMLs NF-e e NFC-e";
            case "Tabela CST/cClassTrib" -> "CST e classificação tributária das previsões";
            default -> "Base externa";
        };
    }

    private static String origin(String origin) {
        if (origin.contains("dfe-portal.svrs.rs.gov.br")) return "Portal da SVRS";
        if (origin.contains("acbr")) return "Espelho técnico ACBr";
        return origin;
    }

    private static String format(Instant instant) {
        return instant == null ? "—" : DATE.format(instant);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record Feedback(String text, Color color, OutlineIcon icon) { }
}
