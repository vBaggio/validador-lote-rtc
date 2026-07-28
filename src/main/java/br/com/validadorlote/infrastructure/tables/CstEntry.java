package br.com.validadorlote.infrastructure.tables;

import java.time.LocalDate;

/**
 * Uma situação tributária com os indicadores que a NT referencia.
 *
 * @param exigeDiferimento {@code ind_gDif} da tabela oficial de CST. Nome escolhido a dessa forma
 *         (e não {@code permiteDiferimento}) porque a NT lê o indicador nos dois sentidos, não só
 *         num: {@code ind_gDif=1} <b>exige</b> o grupo {@code gDif} (rejeições 1030/1044/1061 se
 *         ausente); {@code ind_gDif=0} <b>veda</b> o grupo (rejeições 1029/1083/1090 se presente).
 *         "Permite" sugeriria facultatividade que o indicador não tem — é sempre exigido ou vedado,
 *         nunca opcional (D-041).
 */
public record CstEntry(String cst, String nome, boolean exigeGrupo, boolean exigeReducao,
        boolean exigeDiferimento, LocalDate iniVig, LocalDate fimVig) {

    boolean vigenteEm(LocalDate data) {
        return (iniVig == null || !data.isBefore(iniVig))
                && (fimVig == null || !data.isAfter(fimVig));
    }
}
