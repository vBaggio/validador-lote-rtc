package br.com.validadorlote.presentation.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** Marca vetorial do aplicativo, usada pela janela até o empacotador gerar o ícone nativo. */
final class AppIcon {

    private static final Color BLACK = new Color(16, 16, 16);
    private static final Color WHITE = new Color(245, 245, 245);
    private static final Color GREEN = new Color(84, 210, 123);

    private AppIcon() {}

    static BufferedImage image() {
        int size = 256;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BLACK);
            g.fillRoundRect(8, 8, 240, 240, 54, 54);
            g.setColor(WHITE);
            g.setStroke(new BasicStroke(13, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D document = new Path2D.Double();
            document.moveTo(70, 48);
            document.lineTo(143, 48);
            document.lineTo(186, 91);
            document.lineTo(186, 183);
            document.quadTo(186, 208, 161, 208);
            document.lineTo(70, 208);
            document.quadTo(45, 208, 45, 183);
            document.lineTo(45, 73);
            document.quadTo(45, 48, 70, 48);
            g.draw(document);
            g.drawLine(143, 48, 143, 91);
            g.drawLine(143, 91, 186, 91);
            g.drawLine(76, 118, 141, 118);
            g.drawLine(76, 145, 123, 145);

            Path2D shield = new Path2D.Double();
            shield.moveTo(165, 139);
            shield.curveTo(180, 150, 194, 150, 208, 139);
            shield.lineTo(208, 170);
            shield.curveTo(208, 196, 190, 210, 165, 219);
            shield.curveTo(140, 210, 122, 196, 122, 170);
            shield.lineTo(122, 139);
            shield.curveTo(136, 150, 150, 150, 165, 139);
            shield.closePath();
            g.setColor(BLACK);
            g.fill(shield);
            g.setColor(GREEN);
            g.setStroke(new BasicStroke(11, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(shield);
            g.drawLine(148, 174, 160, 186);
            g.drawLine(160, 186, 185, 158);
        } finally {
            g.dispose();
        }
        return image;
    }
}
