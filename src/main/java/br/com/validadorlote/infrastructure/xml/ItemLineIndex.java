package br.com.validadorlote.infrastructure.xml;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapeia linha do arquivo → nItem do det que a contém, por faixas fechadas
 * [linha de abertura, linha de fechamento]. Linhas fora de qualquer det (cabeçalho,
 * {@code ide}, {@code emit}, {@code total}) não pertencem a item algum.
 */
public final class ItemLineIndex {

    private static final int RANGE_LENGTH = 3;

    private final List<int[]> ranges;

    private ItemLineIndex(List<int[]> ranges) {
        this.ranges = ranges;
    }

    /** Recebe faixas {@code {linha de abertura do det, linha de fechamento, nItem}}. */
    public static ItemLineIndex of(List<int[]> ranges) {
        List<int[]> copy = new ArrayList<>(ranges.size());
        for (int[] range : ranges) {
            if (range == null || range.length != RANGE_LENGTH) {
                throw new IllegalArgumentException("Faixa de item deve ter " + RANGE_LENGTH
                        + " posições {linha inicial, linha final, nItem}");
            }
            copy.add(range.clone()); // cópia defensiva: o chamador não altera o índice depois
        }
        return new ItemLineIndex(List.copyOf(copy));
    }

    /** nItem do det que contém a linha, ou null se nenhuma faixa a contém. */
    public Integer itemAt(int line) {
        for (int[] range : ranges) {
            if (line >= range[0] && line <= range[1]) return range[2];
        }
        return null;
    }
}
