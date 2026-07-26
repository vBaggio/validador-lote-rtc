package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.FiscalDocument;

/** Resultado do parse de metadados: documento + índice de itens por linha. */
public record ParsedMetadata(FiscalDocument document, ItemLineIndex itemIndex) {}
