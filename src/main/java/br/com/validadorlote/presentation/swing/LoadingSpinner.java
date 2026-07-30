package br.com.validadorlote.presentation.swing;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Indicador leve de atividade para operações que continuam fora da EDT. */
final class LoadingSpinner extends JComponent {

    private static final int DOTS = 8;
    private final Timer timer;
    private int frame;

    LoadingSpinner() {
        setOpaque(false);
        setVisible(false);
        setPreferredSize(new java.awt.Dimension(20, 20));
        timer = new Timer(90, event -> {
            frame = (frame + 1) % DOTS;
            repaint();
        });
    }

    void setRunning(boolean running) {
        if (running) {
            setVisible(true);
            if (!timer.isRunning()) timer.start();
        } else {
            timer.stop();
            setVisible(false);
        }
    }

    boolean isRunning() {
        return timer.isRunning();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int diameter = Math.max(3, Math.min(getWidth(), getHeight()) / 4);
            double radius = Math.max(3, Math.min(getWidth(), getHeight()) / 2d - diameter / 2d - 1);
            double centerX = getWidth() / 2d;
            double centerY = getHeight() / 2d;
            Color foreground = getForeground();
            for (int index = 0; index < DOTS; index++) {
                int distance = Math.floorMod(index - frame, DOTS);
                int alpha = 45 + (DOTS - distance) * 210 / DOTS;
                g.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), alpha));
                double angle = Math.PI * 2 * index / DOTS - Math.PI / 2;
                int x = (int) Math.round(centerX + Math.cos(angle) * radius - diameter / 2d);
                int y = (int) Math.round(centerY + Math.sin(angle) * radius - diameter / 2d);
                g.fillOval(x, y, diameter, diameter);
            }
        } finally {
            g.dispose();
        }
    }
}
