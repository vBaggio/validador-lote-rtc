package br.com.validadorlote.presentation.swing;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Rectangle;

import static org.assertj.core.api.Assertions.assertThat;

class MainFrameSizingTest {

    @Test
    void keepsTheComfortableSizeAt1366By768InOneHundredPercentScale() {
        Rectangle workArea = new Rectangle(0, 0, 1366, 728);

        assertThat(MainFrame.minimumWindowSize(workArea)).isEqualTo(new Dimension(1000, 660));
        assertThat(MainFrame.initialWindowSize(workArea)).isEqualTo(new Dimension(1180, 700));
    }

    @Test
    void fitsInsideTheLogicalWorkAreaAt1366By768InOneHundredTwentyFivePercentScale() {
        Rectangle logicalWorkArea = new Rectangle(0, 0, 1093, 576);

        Dimension minimum = MainFrame.minimumWindowSize(logicalWorkArea);
        Dimension initial = MainFrame.initialWindowSize(logicalWorkArea);

        assertThat(minimum).isEqualTo(new Dimension(1000, 552));
        assertThat(initial).isEqualTo(new Dimension(1053, 552));
        assertThat(initial.width).isLessThan(logicalWorkArea.width);
        assertThat(initial.height).isLessThan(logicalWorkArea.height);
    }
}
