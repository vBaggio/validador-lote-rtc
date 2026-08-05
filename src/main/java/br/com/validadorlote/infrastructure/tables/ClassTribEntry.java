package br.com.validadorlote.infrastructure.tables;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma classificação tributária, com os indicadores por modelo e por grupo.
 *
 * @param exigeTributacaoRegular {@code IndTribRegular} da tabela SVRS
 * @param permiteCreditoPresumido {@code IndPermiteCredPres} da tabela SVRS
 * @param exigeEstornoCredito {@code IndEstornoCred} da tabela SVRS
 * @param exigeMonoValor {@code IndMonoVal} da tabela SVRS
 * @param exigeMonoRetencao {@code IndMonoRetem} da tabela SVRS
 * @param exigeMonoRetido {@code IndMonoRet} da tabela SVRS
 * @param exigeMonoDiferimento {@code IndMonoDif} da tabela SVRS
 * @param exigePbioDiferenca {@code IndPbioDiferenca} da tabela SVRS
 */
public record ClassTribEntry(String codigo, String nome, String cst, boolean nfe, boolean nfce,
        boolean exigeTributacaoRegular, boolean permiteCreditoPresumido,
        boolean exigeEstornoCredito, boolean exigeMonoValor, boolean exigeMonoRetencao,
        boolean exigeMonoRetido, boolean exigeMonoDiferimento, boolean exigePbioDiferenca,
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
