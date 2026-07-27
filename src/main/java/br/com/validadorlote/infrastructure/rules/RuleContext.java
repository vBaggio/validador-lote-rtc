package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.time.LocalDate;

/** Tudo que uma regra precisa para julgar um item. */
public record RuleContext(FiscalDocument document, ItemTaxGroup item, FiscalTables tables,
        LocalDate operationDate) {}
