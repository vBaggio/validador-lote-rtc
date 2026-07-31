package br.com.validadorlote.presentation.swing;

import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class ResultsPanelTest {

    @Test
    void zebraTableAlternatesSubtlyAndKeepsTheSelectionColor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ResultsPanel.ZebraTable table = new ResultsPanel.ZebraTable(
                    new DefaultTableModel(new Object[][] {{"a"}, {"b"}}, new Object[] {"Valor"}));
            table.setBackground(new Color(40, 40, 40));
            table.setSelectionBackground(new Color(30, 90, 160));

            Color first = table.prepareRenderer(table.getDefaultRenderer(Object.class), 0, 0)
                    .getBackground();
            Color second = table.prepareRenderer(table.getDefaultRenderer(Object.class), 1, 0)
                    .getBackground();

            table.setRowSelectionInterval(1, 1);
            Color selected = table.prepareRenderer(table.getDefaultRenderer(Object.class), 1, 0)
                    .getBackground();

            assertThat(first).isEqualTo(new Color(40, 40, 40));
            assertThat(second).isEqualTo(new Color(45, 45, 45));
            assertThat(selected).isEqualTo(new Color(30, 90, 160));
        });
    }
}
