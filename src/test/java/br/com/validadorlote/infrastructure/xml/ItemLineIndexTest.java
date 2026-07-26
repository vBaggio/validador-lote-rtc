package br.com.validadorlote.infrastructure.xml;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemLineIndexTest {

    @Test
    void mutatingTheCallerArrayDoesNotChangeTheIndex() {
        int[] range = {5, 9, 1};
        var index = ItemLineIndex.of(List.of(range));

        range[0] = 100;
        range[1] = 200;
        range[2] = 42;

        assertThat(index.itemAt(7)).isEqualTo(1);
        assertThat(index.itemAt(150)).isNull();
    }

    @Test
    void rejectsRangeWithWrongLength() {
        List<int[]> ranges = List.of(new int[]{5, 9});

        assertThatThrownBy(() -> ItemLineIndex.of(ranges))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nItem");
    }
}
