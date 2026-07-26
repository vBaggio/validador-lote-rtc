package br.com.validadorlote.domain;

import java.nio.file.Path;
import java.time.LocalDate;

/** Metadados de um documento fiscal lido do disco. Campos null quando não extraíveis. */
public record FiscalDocument(Path source, String accessKey, String emitterCnpj,
        String documentNumber, LocalDate issueDate, String model, String rootElement) {}
