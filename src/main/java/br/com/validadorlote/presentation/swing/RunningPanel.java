package br.com.validadorlote.presentation.swing;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.Component;

/** Progresso do lote com botão de cancelar. */
public final class RunningPanel extends JPanel {

    private final JProgressBar bar = new JProgressBar();
    private final JLabel label = new JLabel("Preparando análise...");

    public RunningPanel(Runnable onCancel) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(80, 60, 80, 60));
        bar.setStringPainted(true);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        bar.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton cancel = new JButton("Cancelar");
        cancel.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancel.addActionListener(event -> onCancel.run());
        add(Box.createVerticalGlue());
        add(label);
        add(Box.createVerticalStrut(12));
        add(bar);
        add(Box.createVerticalStrut(24));
        add(cancel);
        add(Box.createVerticalGlue());
    }

    void update(int processed, int total) {
        if (total > 0) {
            bar.setIndeterminate(false);
            bar.setMaximum(total);
            bar.setValue(processed);
            label.setText("Validando " + processed + " de " + total + " arquivos...");
        } else {
            bar.setIndeterminate(true);
            label.setText("Lendo a pasta...");
        }
    }
}
