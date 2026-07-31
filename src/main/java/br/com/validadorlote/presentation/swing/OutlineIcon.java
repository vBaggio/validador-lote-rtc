package br.com.validadorlote.presentation.swing;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/** Ícones outline monocromáticos desenhados no vetor para as ações principais. */
final class OutlineIcon implements Icon {

    enum Kind {
        IMPORT, DRAG_DROP, EXPORT, REFRESH, CANCEL, DELETE, CORRECT, ERROR, NEUTRAL, PROGRESS,
        DATABASE, WARNING, COPY, FOLDER
    }

    private final Kind kind;
    private final int size;
    private final Color color;

    OutlineIcon(Kind kind) {
        this(kind, 20);
    }

    OutlineIcon(Kind kind, int size) {
        this(kind, size, null);
    }

    OutlineIcon(Kind kind, int size, Color color) {
        this.kind = kind;
        this.size = size;
        this.color = color;
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color == null ? component.getForeground() : color);
            double scale = size / 24d;
            // O contexto é escalado logo abaixo; compensar o traço evita bordas pesadas nos
            // ícones grandes (especialmente a área de arrastar e soltar).
            g.setStroke(new BasicStroke((float) (1d / scale), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g.translate(x, y);
            g.scale(scale, scale);
            switch (kind) {
                case IMPORT -> importIcon(g);
                case DRAG_DROP -> dragDropIcon(g);
                case EXPORT -> exportIcon(g);
                case REFRESH -> refreshIcon(g);
                case CANCEL -> cancelIcon(g);
                case DELETE -> deleteIcon(g);
                case CORRECT -> correctIcon(g);
                case ERROR -> errorIcon(g);
                case NEUTRAL -> neutralIcon(g);
                case PROGRESS -> progressIcon(g);
                case DATABASE -> databaseIcon(g);
                case WARNING -> warningIcon(g);
                case COPY -> copyIcon(g);
                case FOLDER -> folderIcon(g);
            }
        } finally {
            g.dispose();
        }
    }

    private static void importIcon(Graphics2D g) {
        g.drawRoundRect(3, 15, 18, 6, 2, 2);
        g.drawLine(12, 3, 12, 15);
        g.drawLine(8, 11, 12, 15);
        g.drawLine(16, 11, 12, 15);
    }

    private static void dragDropIcon(Graphics2D g) {
        g.drawLine(12, 3, 12, 15);
        g.drawLine(8, 11, 12, 15);
        g.drawLine(16, 11, 12, 15);
        g.drawRoundRect(4, 16, 16, 5, 2, 2);
        g.drawLine(7, 18, 17, 18);
    }

    private static void exportIcon(Graphics2D g) {
        g.drawRoundRect(3, 15, 18, 6, 2, 2);
        g.drawLine(12, 15, 12, 3);
        g.drawLine(8, 7, 12, 3);
        g.drawLine(16, 7, 12, 3);
    }

    private static void refreshIcon(Graphics2D g) {
        Path2D path = new Path2D.Double();
        path.moveTo(19, 8);
        path.curveTo(17, 3, 9, 2, 5, 7);
        path.curveTo(1, 13, 5, 20, 11, 20);
        path.curveTo(15, 20, 18, 18, 19, 15);
        g.draw(path);
        g.drawLine(19, 8, 19, 3);
        g.drawLine(19, 8, 14, 8);
    }

    private static void cancelIcon(Graphics2D g) {
        g.drawRoundRect(3, 3, 18, 18, 5, 5);
        g.drawLine(8, 8, 16, 16);
        g.drawLine(16, 8, 8, 16);
    }

    private static void deleteIcon(Graphics2D g) {
        g.drawLine(7, 6, 17, 6);
        g.drawLine(10, 3, 14, 3);
        g.drawRoundRect(8, 7, 8, 14, 1, 1);
        g.drawLine(11, 10, 11, 18);
        g.drawLine(13, 10, 13, 18);
    }

    private static void correctIcon(Graphics2D g) {
        g.drawOval(3, 3, 18, 18);
        g.drawLine(7, 12, 10, 15);
        g.drawLine(10, 15, 17, 8);
    }

    private static void errorIcon(Graphics2D g) {
        g.drawOval(3, 3, 18, 18);
        g.drawLine(8, 8, 16, 16);
        g.drawLine(16, 8, 8, 16);
    }

    private static void neutralIcon(Graphics2D g) {
        g.drawOval(3, 3, 18, 18);
        g.drawLine(12, 7, 12, 13);
        g.fillOval(11, 16, 2, 2);
    }

    private static void progressIcon(Graphics2D g) {
        Path2D path = new Path2D.Double();
        path.moveTo(12, 3);
        path.curveTo(17, 3, 21, 7, 21, 12);
        path.curveTo(21, 17, 17, 21, 12, 21);
        path.curveTo(7, 21, 3, 17, 3, 12);
        g.draw(path);
        g.drawLine(12, 3, 16, 3);
        g.drawLine(12, 3, 12, 7);
    }

    private static void databaseIcon(Graphics2D g) {
        g.drawOval(4, 3, 16, 6);
        g.drawLine(4, 6, 4, 18);
        g.drawLine(20, 6, 20, 18);
        g.drawArc(4, 15, 16, 6, 0, -180);
        g.drawArc(4, 9, 16, 6, 0, -180);
    }

    private static void warningIcon(Graphics2D g) {
        Path2D path = new Path2D.Double();
        path.moveTo(12, 3);
        path.lineTo(21, 20);
        path.lineTo(3, 20);
        path.closePath();
        g.draw(path);
        g.drawLine(12, 9, 12, 14);
        g.fillOval(11, 17, 2, 2);
    }

    private static void copyIcon(Graphics2D g) {
        g.drawRoundRect(8, 3, 12, 14, 2, 2);
        g.drawRoundRect(4, 7, 12, 14, 2, 2);
    }

    private static void folderIcon(Graphics2D g) {
        Path2D path = new Path2D.Double();
        path.moveTo(3, 6);
        path.lineTo(10, 6);
        path.lineTo(12, 9);
        path.lineTo(21, 9);
        path.lineTo(21, 20);
        path.lineTo(3, 20);
        path.closePath();
        g.draw(path);
    }
}
