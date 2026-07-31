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
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/** Conteúdo adaptável do diálogo de bases; renderiza somente o snapshot recebido. */
final class ExternalSourcesPanel extends JPanel {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final Color MUTED = new Color(160, 160, 160);
    private static final Color SUCCESS = new Color(91, 190, 126);
    private static final Color WARNING = new Color(232, 180, 76);
    private static final Color ERROR = new Color(225, 96, 96);
    private static final ResourceBundle MESSAGES = ResourceBundle.getBundle("messages");

    private final JPanel sourceCards = new JPanel();
    private final JLabel summary = new JLabel();
    private final JButton primaryAction = new JButton();
    private final JButton closeAction = new JButton("Fechar");
    private final Runnable checkNow;
    private final Runnable applyAvailable;
    private final Runnable retry;
    private Runnable closeDialog = () -> { };
    private int sourceCardCount;
    private PrimaryAction primaryActionKind = PrimaryAction.CHECK_NOW;
    private ExternalSourcesSnapshot displayedSnapshot;
    private String expandedSourceName;

    ExternalSourcesPanel(Runnable checkNow, Runnable applyAvailable, Runnable retry,
            Runnable closeDialog) {
        super(new BorderLayout(0, 16));
        this.checkNow = checkNow;
        this.applyAvailable = applyAvailable;
        this.retry = retry;
        this.closeDialog = closeDialog;
        setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));

        JLabel title = new JLabel("Bases de validação");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Atualizações são verificadas sem enviar XMLs ou dados do lote.");
        subtitle.setForeground(MUTED);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        JPanel heading = new JPanel();
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(title);
        heading.add(Box.createVerticalStrut(5));
        heading.add(subtitle);
        summary.setVisible(false);
        summary.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        summary.setAlignmentX(LEFT_ALIGNMENT);
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
        scroll.setPreferredSize(new Dimension(740, 380));
        add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        primaryAction.addActionListener(event -> runPrimaryAction());
        closeAction.addActionListener(event -> this.closeDialog.run());
        actions.add(primaryAction);
        actions.add(closeAction);
        add(actions, BorderLayout.SOUTH);
    }

    void showSnapshot(ExternalSourcesSnapshot snapshot) {
        displayedSnapshot = snapshot;
        rebuildCards(snapshot.sources(), snapshot.phase());
        configureSummary(snapshot);
        configureActions(snapshot.phase());
    }

    void setCloseDialog(Runnable closeDialog) {
        this.closeDialog = closeDialog;
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

    private void configureSummary(ExternalSourcesSnapshot snapshot) {
        if (snapshot.phase() == ExternalSourcesPhase.UPDATED_AND_IN_USE) {
            summary.setText("Bases atualizadas.");
            summary.setIcon(new OutlineIcon(OutlineIcon.Kind.CORRECT, 18, SUCCESS));
            summary.setForeground(SUCCESS);
            summary.setVisible(true);
        } else if (snapshot.phase() == ExternalSourcesPhase.RESTART_REQUIRED) {
            summary.setText(sanitizedRuntimeReloadDetail(snapshot.runtimeReloadDetail()));
            summary.setIcon(new OutlineIcon(OutlineIcon.Kind.CORRECT, 18, SUCCESS));
            summary.setForeground(SUCCESS);
            summary.setVisible(true);
        } else {
            summary.setText("");
            summary.setIcon(null);
            summary.setVisible(false);
        }
    }

    private void rebuildCards(List<ExternalSourceState> sources, ExternalSourcesPhase aggregatePhase) {
        if (sources.stream().noneMatch(source -> source.name().equals(expandedSourceName))) {
            expandedSourceName = null;
        }
        stopRemovedSpinners(sourceCards);
        sourceCards.removeAll();
        sourceCardCount = 0;
        for (ExternalSourceState source : sources) {
            if (source.isCalculator()) continue;
            sourceCards.add(sourceCard(source, aggregatePhase));
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
                primaryActionKind = PrimaryAction.APPLY;
                primaryAction.setText("Atualizar agora");
                primaryAction.setIcon(new OutlineIcon(OutlineIcon.Kind.DATABASE));
            }
            case FAILED -> {
                primaryActionKind = PrimaryAction.RETRY;
                primaryAction.setText("Tentar novamente");
                primaryAction.setIcon(new OutlineIcon(OutlineIcon.Kind.REFRESH));
            }
            case APPLYING, RELOADING_RUNTIME -> {
                primaryActionKind = PrimaryAction.NONE;
                primaryAction.setText("Atualizando…");
                primaryAction.setIcon(new OutlineIcon(OutlineIcon.Kind.REFRESH));
                primaryAction.setEnabled(false);
                closeAction.setEnabled(false);
            }
            case RESTART_REQUIRED -> {
                primaryActionKind = PrimaryAction.CHECK_NOW;
                primaryAction.setText("Verificar agora");
                primaryAction.setIcon(new OutlineIcon(OutlineIcon.Kind.REFRESH));
            }
            case IDLE, CHECKING, UP_TO_DATE, WAITING_FOR_VALIDATION, UPDATED_AND_IN_USE -> {
                primaryActionKind = PrimaryAction.CHECK_NOW;
                primaryAction.setText("Verificar agora");
                primaryAction.setIcon(new OutlineIcon(OutlineIcon.Kind.REFRESH));
                primaryAction.setEnabled(phase != ExternalSourcesPhase.CHECKING
                        && phase != ExternalSourcesPhase.WAITING_FOR_VALIDATION);
            }
        }
        primaryAction.getAccessibleContext().setAccessibleName(primaryAction.getText());
        closeAction.getAccessibleContext().setAccessibleName(closeAction.getText());
    }

    private void runPrimaryAction() {
        switch (primaryActionKind) {
            case APPLY -> applyAvailable.run();
            case RETRY -> retry.run();
            case CHECK_NOW -> checkNow.run();
            case NONE -> { }
        }
    }

    private static String sanitizedRuntimeReloadDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "Atualização concluída. Reinicie o aplicativo para usar as novas versões.";
        }
        return detail.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .replaceAll("\\s+", " ").trim();
    }

    private JPanel sourceCard(ExternalSourceState source, ExternalSourcesPhase aggregatePhase) {
        boolean expanded = source.name().equals(expandedSourceName);
        JPanel card = new JPanel(new BorderLayout(14, expanded ? 12 : 0));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, expanded ? 150 : 58));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(78, 78, 78)),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        JLabel name = new JLabel(source.name());
        name.setFont(name.getFont().deriveFont(Font.BOLD, 15f));
        name.setAlignmentX(LEFT_ALIGNMENT);
        Feedback presentation = feedbackFor(source, aggregatePhase);
        JPanel status = status(source, aggregatePhase, presentation);
        status.setAlignmentX(LEFT_ALIGNMENT);
        JPanel titleText = new JPanel();
        titleText.setOpaque(false);
        titleText.setLayout(new BoxLayout(titleText, BoxLayout.Y_AXIS));
        titleText.add(name);
        titleText.add(Box.createVerticalStrut(3));
        titleText.add(status);

        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setOpaque(false);
        header.add(titleText, BorderLayout.CENTER);
        JButton toggle = new JButton();
        toggle.setIcon(new OutlineIcon(expanded ? OutlineIcon.Kind.COLLAPSE : OutlineIcon.Kind.EXPAND,
                20, MUTED));
        toggle.setToolTipText(expanded ? "Ocultar" : "Detalhes");
        toggle.setFocusPainted(false);
        toggle.putClientProperty("JButton.buttonType", "toolBarButton");
        toggle.addActionListener(event -> toggleDetails(source.name()));
        toggle.getAccessibleContext().setAccessibleName((expanded ? "Ocultar" : "Ver")
                + " detalhes de " + source.name());
        header.add(toggle, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        if (!expanded) return card;

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel purpose = new JLabel(purpose(source.name()));
        purpose.setForeground(MUTED);
        purpose.setAlignmentX(LEFT_ALIGNMENT);
        content.add(purpose);
        content.add(Box.createVerticalStrut(8));
        JPanel details = new JPanel(new FlowLayout(FlowLayout.LEFT, 24, 0));
        details.setOpaque(false);
        details.setAlignmentX(LEFT_ALIGNMENT);
        details.add(activeBase(source));
        if (!source.embedded() || source.isCreditPresumedTable()) {
            details.add(detail("Origem da base", friendlyOrigin(source.origin()), source.origin()));
        }
        details.add(detail("Última verificação", format(source.checkedAt())));
        details.add(detail(source.embedded() ? "Incluída em" : "Atualizada em",
                format(source.updatedAt())));
        content.add(details);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private void toggleDetails(String sourceName) {
        expandedSourceName = sourceName.equals(expandedSourceName) ? null : sourceName;
        if (displayedSnapshot != null) {
            rebuildCards(displayedSnapshot.sources(), displayedSnapshot.phase());
        }
    }

    private static JPanel activeBase(ExternalSourceState source) {
        JPanel active = new JPanel();
        active.setOpaque(false);
        active.setLayout(new BoxLayout(active, BoxLayout.Y_AXIS));
        active.setAlignmentX(LEFT_ALIGNMENT);

        JLabel caption = new JLabel("Base ativa");
        caption.setForeground(MUTED);
        caption.setAlignmentX(LEFT_ALIGNMENT);
        JLabel version = new JLabel(source.activeVersion());
        version.setFont(version.getFont().deriveFont(Font.BOLD));
        version.setAlignmentX(LEFT_ALIGNMENT);
        active.add(caption);
        active.add(Box.createVerticalStrut(2));
        active.add(version);
        if (source.embedded()) {
            JLabel embedded = new JLabel("Incluída no aplicativo");
            embedded.setForeground(MUTED);
            embedded.setAlignmentX(LEFT_ALIGNMENT);
            active.add(embedded);
        }
        return active;
    }

    private JPanel status(ExternalSourceState source, ExternalSourcesPhase aggregatePhase,
            Feedback presentation) {
        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        status.setOpaque(false);
        if (source.phase() == ExternalSourcePhase.CHECKING
                || source.phase() == ExternalSourcePhase.APPLYING
                || (aggregatePhase == ExternalSourcesPhase.RELOADING_RUNTIME
                && source.phase() == ExternalSourcePhase.APPLIED)) {
            LoadingSpinner spinner = new LoadingSpinner();
            spinner.setForeground(presentation.color());
            spinner.setRunning(true);
            status.add(spinner);
        }
        // Texto sem quebra estoura a largura do card e empurra o botão de expandir para fora da
        // área visível do JScrollPane (HORIZONTAL_SCROLLBAR_NEVER não deixa recuperá-lo).
        boolean wrap = source.hasUnsupportedSchemaStructure() || source.isCreditPresumedTable();
        String visibleText = wrap
                ? "<html><div style='width:520px'>" + escape(presentation.text()) + "</div></html>"
                : presentation.text();
        JLabel label = new JLabel(visibleText, presentation.icon(), JLabel.LEFT);
        label.setForeground(presentation.color());
        label.getAccessibleContext().setAccessibleName(presentation.text());
        status.add(label);
        return status;
    }

    private static Feedback feedbackFor(ExternalSourceState source,
            ExternalSourcesPhase aggregatePhase) {
        String detail = source.detail();
        if (source.isCreditPresumedTable()) {
            return new Feedback("Tabela atualizada — embarcada nesta versão do aplicativo, sem"
                    + " verificação automática (atualização exige nova versão)", SUCCESS,
                    new OutlineIcon(OutlineIcon.Kind.CORRECT, 18, SUCCESS));
        }
        if (aggregatePhase == ExternalSourcesPhase.RELOADING_RUNTIME
                && source.phase() == ExternalSourcePhase.APPLIED) {
            return new Feedback("Carregando a base atualizada…", WARNING, null);
        }
        if (source.hasUnsupportedSchemaStructure()) {
            return new Feedback(MESSAGES.getString(
                    "externalSources.unsupportedSchemaStructure"), ERROR,
                    new OutlineIcon(OutlineIcon.Kind.ERROR, 18, ERROR));
        }
        return switch (source.phase()) {
            case NOT_CHECKED -> new Feedback("Ainda não verificada", MUTED,
                    new OutlineIcon(OutlineIcon.Kind.DATABASE, 18, MUTED));
            case CHECKING -> new Feedback("Verificando atualização…", WARNING, null);
            case UP_TO_DATE -> new Feedback(detail == null ? "Base já está atualizada" : detail,
                    SUCCESS, new OutlineIcon(OutlineIcon.Kind.CORRECT, 18, SUCCESS));
            case UPDATE_AVAILABLE -> new Feedback(candidateFeedback(source),
                    WARNING, new OutlineIcon(OutlineIcon.Kind.WARNING, 18, WARNING));
            case APPLYING -> new Feedback("Aplicando atualização…", WARNING, null);
            case APPLIED -> new Feedback("Atualização pronta para o próximo boot", SUCCESS,
                    new OutlineIcon(OutlineIcon.Kind.CORRECT, 18, SUCCESS));
            case FAILED -> new Feedback(detail == null ? "Não foi possível consultar esta fonte" : detail,
                    ERROR, new OutlineIcon(OutlineIcon.Kind.ERROR, 18, ERROR));
        };
    }

    private static String candidateFeedback(ExternalSourceState source) {
        String channel = friendlyOrigin(source.candidateOrigin());
        return "Atualização disponível: " + source.candidateVersion()
                + (channel.isBlank() ? "" : " · " + channel);
    }

    private static JPanel detail(String label, String value) {
        return detail(label, value, value);
    }

    private static JPanel detail(String label, String value, String tooltip) {
        JPanel detail = new JPanel();
        detail.setOpaque(false);
        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        JLabel caption = new JLabel(label);
        caption.setForeground(MUTED);
        JLabel content = new JLabel(value == null || value.isBlank() ? "—" : value);
        if (tooltip != null && !tooltip.isBlank()) {
            content.setToolTipText(tooltip);
        }
        detail.add(caption);
        detail.add(Box.createVerticalStrut(3));
        detail.add(content);
        return detail;
    }

    private static String purpose(String sourceName) {
        return switch (sourceName) {
            case "Schemas NF-e/NFC-e" -> "Estrutura de XMLs NF-e e NFC-e";
            case "Tabela CST/cClassTrib" -> "CST e classificação tributária das previsões";
            case "Tabela de crédito presumido (cCredPres)" ->
                    "Indicador de dedução do crédito presumido do IBS/CBS";
            default -> "Base externa";
        };
    }

    private static String friendlyOrigin(String origin) {
        if (origin == null || origin.isBlank()) return "";
        String lower = origin.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("validador-lote-rtc-bases")
                && (lower.contains("github.io") || lower.contains("github.com"))) {
            return "vBaggio/validador-lote-rtc-bases";
        }
        if (lower.contains("dfe-portal.svrs.rs.gov.br")) return "Portal DF-e da SVRS";
        if (lower.contains("nfe.fazenda.gov.br")) return "Portal Nacional da NF-e";
        if (lower.contains("acbr")) return "Projeto ACBr";
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            if (host == null) return origin;
            if ("github.com".equalsIgnoreCase(host)) {
                String[] parts = uri.getPath().split("/");
                if (parts.length >= 3) return parts[1] + "/" + parts[2];
            }
            return host.replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException ignored) {
            return origin;
        }
    }

    private static String format(Instant instant) {
        return instant == null ? "—" : DATE.format(instant);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record Feedback(String text, Color color, OutlineIcon icon) { }

    private enum PrimaryAction { APPLY, RETRY, CHECK_NOW, NONE }
}
