package br.com.validadorlote.infrastructure.tables;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CreditPresumedTableTest {

    @Test
    void embeddedOfficialSnapshotContainsTheDeductionIndicatorAndIbsValidity() {
        CreditPresumedTable table = CreditPresumedTable.load();

        assertThat(table.size()).isEqualTo(13);
        assertThat(table.find("4", LocalDate.of(2027, 1, 1)))
                .get().extracting(CreditPresumedEntry::deductsTotalValue).isEqualTo(true);
        assertThat(table.find("4", LocalDate.of(2026, 12, 31))).isEmpty();
        assertThat(table.find("1", LocalDate.of(2027, 1, 1)))
                .get().extracting(CreditPresumedEntry::deductsTotalValue).isEqualTo(false);
    }
}
