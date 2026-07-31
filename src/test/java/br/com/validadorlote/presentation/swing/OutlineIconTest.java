package br.com.validadorlote.presentation.swing;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutlineIconTest {

    @Test
    void operationalIconsPaintAsVectors() throws Exception {
        ExternalSourcesStatusBarTest.runOnEdt(() -> {
            for (OutlineIcon.Kind kind : List.of(OutlineIcon.Kind.DATABASE, OutlineIcon.Kind.CORRECT,
                    OutlineIcon.Kind.WARNING, OutlineIcon.Kind.ERROR, OutlineIcon.Kind.REFRESH,
                    OutlineIcon.Kind.CANCEL)) {
                BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                try {
                    new OutlineIcon(kind, 24, Color.WHITE).paintIcon(new JLabel(), graphics, 0, 0);
                } finally {
                    graphics.dispose();
                }
                assertThat(hasVisiblePixel(image)).as(kind.name()).isTrue();
            }
        });
    }

    private static boolean hasVisiblePixel(BufferedImage image) {
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if ((image.getRGB(x, y) >>> 24) != 0) return true;
            }
        }
        return false;
    }
}
