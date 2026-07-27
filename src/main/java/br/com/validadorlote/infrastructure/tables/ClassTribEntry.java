package br.com.validadorlote.infrastructure.tables;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Uma classificação tributária, com os indicadores por modelo de documento. */
public record ClassTribEntry(String codigo, String nome, String cst, boolean nfe, boolean nfce,
        BigDecimal percRedIbs, BigDecimal percRedCbs, LocalDate iniVig, LocalDate fimVig) {

    boolean vigenteEm(LocalDate data) {
        return (iniVig == null || !data.isBefore(iniVig))
                && (fimVig == null || !data.isAfter(fimVig));
    }

    /** Se a classificação é permitida no modelo informado (55 = NF-e, 65 = NFC-e). */
    public boolean permiteModelo(String modelo) {
        return "65".equals(modelo) ? nfce : nfe;
    }
}
