package br.com.validadorlote.application;

import br.com.validadorlote.domain.FiscalDocument;

import java.nio.file.Path;
import java.util.List;

/** XMLs aceitos para o lote após somente a leitura segura dos metadados. */
public record ImportedBatch(List<FiscalDocument> documents, List<Path> invalidFiles) {
    public ImportedBatch {
        documents = List.copyOf(documents);
        invalidFiles = List.copyOf(invalidFiles);
    }
}
