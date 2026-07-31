package br.com.validadorlote.infrastructure.tables;

import java.time.LocalDate;

/** Registro oficial de {@code cCredPres}, embarcado com vigências de IBS e CBS. */
public record CreditPresumedEntry(String code, boolean deductsTotalValue,
        LocalDate ibsStart, LocalDate ibsEnd, LocalDate cbsStart, LocalDate cbsEnd) {

    public boolean applicableToIbs(LocalDate date) {
        return date != null && ibsStart != null && !date.isBefore(ibsStart)
                && (ibsEnd == null || !date.isAfter(ibsEnd));
    }

    public boolean applicableToCbs(LocalDate date) {
        return date != null && cbsStart != null && !date.isBefore(cbsStart)
                && (cbsEnd == null || !date.isAfter(cbsEnd));
    }
}
