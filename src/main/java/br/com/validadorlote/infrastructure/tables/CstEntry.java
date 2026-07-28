package br.com.validadorlote.infrastructure.tables;

import java.time.LocalDate;

/** Uma situação tributária com os indicadores que a NT referencia. */
public record CstEntry(String cst, String nome, boolean exigeGrupo, boolean exigeReducao,
        boolean permiteDiferimento, LocalDate iniVig, LocalDate fimVig) {

    boolean vigenteEm(LocalDate data) {
        return (iniVig == null || !data.isBefore(iniVig))
                && (fimVig == null || !data.isAfter(fimVig));
    }
}
