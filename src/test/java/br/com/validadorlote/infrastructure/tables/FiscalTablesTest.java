package br.com.validadorlote.infrastructure.tables;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FiscalTablesTest {

    private static FiscalTables tables;
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 3);

    @BeforeAll
    static void load() {
        tables = FiscalTables.load();
    }

    @Test
    void findsCstWithItsIndicators() {
        var cst = tables.cst("011", HOJE).orElseThrow();

        assertThat(cst.exigeGrupo()).isTrue();
        assertThat(cst.exigeReducao()).isTrue();
    }

    @Test
    void onlyThreeCstsRequireReductionGroup() {
        // Guarda contra a confusão com possuiPercentualReducao da Calculadora, que é
        // verdadeiro em 60 de 161 classificações. O indicador real é por CST.
        long comReducao = "000 010 011 200 220 221 222 400 410 510 515 550 620 800 810 811 820 830"
                .lines().flatMap(s -> java.util.Arrays.stream(s.split(" ")))
                .filter(c -> tables.cst(c, HOJE).map(CstEntry::exigeReducao).orElse(false))
                .count();

        assertThat(comReducao).isEqualTo(3);
    }

    @Test
    void findsClassTribWithModelIndicators() {
        var ct = tables.classTrib("000001", HOJE).orElseThrow();

        assertThat(ct.cst()).isEqualTo("000");
        assertThat(ct.nfe()).isTrue();
        assertThat(ct.nfce()).isTrue();
    }

    @Test
    void unknownCodeYieldsEmptyNotException() {
        // Código publicado depois da nossa extração: base velha, não erro do emitente.
        assertThat(tables.classTrib("999999", HOJE)).isEmpty();
        assertThat(tables.cst("999", HOJE)).isEmpty();
    }

    @Test
    void respectsValidityOnTheOperationDate() {
        // Nenhum registro vigente em 2020 — a base começa em 2025.
        assertThat(tables.cst("000", LocalDate.of(2020, 1, 1))).isEmpty();
        assertThat(tables.cst("000", HOJE)).isPresent();
    }

    @Test
    void respectsExactValidityBoundaries() {
        // 220001 tem iniVig=2025-05-05 e fimVig=2026-01-01T00:00:00 — janela real da base,
        // não um caso sintético. vigenteEm usa !isBefore/!isAfter: ambas as bordas são inclusivas.
        var iniVig = LocalDate.of(2025, 5, 5);
        var fimVig = LocalDate.of(2026, 1, 1);

        assertThat(tables.classTrib("220001", iniVig)).isPresent();
        assertThat(tables.classTrib("220001", fimVig)).isPresent();
        assertThat(tables.classTrib("220001", fimVig.plusDays(1))).isEmpty();
    }

    @Test
    void permiteModeloDistinguishesNfeFromNfce() {
        // 000003 tem nfe=true/nfce=false na base real — cobre os dois ramos do ternário
        // ("65" retorna nfce; qualquer outro valor retorna nfe) com um único registro.
        var ct = tables.classTrib("000003", HOJE).orElseThrow();

        assertThat(ct.permiteModelo("55")).isTrue();
        assertThat(ct.permiteModelo("65")).isFalse();

        // Comportamento atual para modelo que não é "55" nem "65": cai no ramo NF-e (mesmo
        // ramo de "55"), porque a implementação testa apenas "65".equals(modelo). Documentado
        // aqui como comportamento vigente, não validado como correto — ver relatório da task.
        assertThat(ct.permiteModelo("01")).isTrue();
    }

    @Test
    void provenanceNamesSourceAndDate() {
        assertThat(tables.provenance()).matches(
                "tabelas de https://dfe-portal\\.svrs\\.rs\\.gov\\.br/DFE/ClassificacaoTributaria, "
                        + "extraídas em \\d{4}-\\d{2}-\\d{2}");
    }
}
