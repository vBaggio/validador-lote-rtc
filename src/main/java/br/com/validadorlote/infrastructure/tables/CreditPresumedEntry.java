package br.com.validadorlote.infrastructure.tables;

import java.time.LocalDate;

/** Registro oficial de {@code cCredPres}, com a vigência usada pela composição IBS do item. */
public record CreditPresumedEntry(String code, boolean deductsTotalValue,
        LocalDate ibsStart, LocalDate ibsEnd) {

    public boolean applicableToIbs(LocalDate date) {
        return date != null && ibsStart != null && !date.isBefore(ibsStart)
                && (ibsEnd == null || !date.isAfter(ibsEnd));
    }
}
