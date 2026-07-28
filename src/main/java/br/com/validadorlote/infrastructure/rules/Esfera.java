package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.infrastructure.tables.ClassTribEntry;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.math.BigDecimal;

/**
 * Esfera de tributação em que o grupo de redução de alíquota pode aparecer.
 *
 * <p>A NT escreve as regras de redução três vezes, uma por esfera, com códigos de rejeição
 * distintos: {@code gIBSUF/gRed}, {@code gIBSMun/gRed} e {@code gCBS/gRed}. As regras aqui são
 * espelhadas e parametrizadas por este enum — nunca uma única regra que devolve às vezes um
 * código e às vezes outro, porque isso quebraria a identidade do achado no relatório.
 */
public enum Esfera {
    UF, MUNICIPIO, CBS;

    /** Se o item informou o grupo {@code gRed} desta esfera. */
    boolean informouReducao(ItemTaxGroup item) {
        return switch (this) {
            case UF -> item.hasReducaoUf();
            case MUNICIPIO -> item.hasReducaoMun();
            case CBS -> item.hasReducaoCbs();
        };
    }

    /** {@code pRedAliq} declarado nesta esfera; null quando ilegível. */
    BigDecimal percentualDeclarado(ItemTaxGroup item) {
        return switch (this) {
            case UF -> item.percReducaoUf();
            case MUNICIPIO -> item.percReducaoMun();
            case CBS -> item.percReducaoCbs();
        };
    }

    /**
     * Percentual oficial da classificação para esta esfera. O IBS tem componente estadual e
     * municipal, mas a tabela publica <b>um só</b> {@code PercRedIbs} para os dois; só a CBS tem
     * percentual próprio.
     */
    BigDecimal percentualOficial(ClassTribEntry entry) {
        return this == CBS ? entry.percRedCbs() : entry.percRedIbs();
    }
}
