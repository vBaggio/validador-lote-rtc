package br.com.validadorlote;

import br.com.validadorlote.application.ValidateBatchUseCase;
import br.com.validadorlote.domain.RootCauseGrouper;
import br.com.validadorlote.infrastructure.csv.CsvExporter;
import br.com.validadorlote.infrastructure.fs.FolderScanner;
import br.com.validadorlote.infrastructure.rules.RuleEngine;
import br.com.validadorlote.infrastructure.tables.FiscalTableArtifactStore;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.SchemaArtifactStore;
import br.com.validadorlote.infrastructure.xml.SchemasVersion;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import br.com.validadorlote.infrastructure.xml.XsdErrorTranslator;

import java.nio.file.Path;
import br.com.validadorlote.presentation.swing.UiBootstrap;

/** Ponto de entrada: monta o grafo de objetos e entrega à camada de apresentação. */
public final class App {

    private App() {}

    public static void main(String[] args) {
        String schemasVersion = SchemasVersion.read();
        var translator = new XsdErrorTranslator();
        var schemaEngine = schemaEngine(translator, SchemaArtifactStore.forCurrentUser());
        var useCase = new ValidateBatchUseCase(new FolderScanner(), new XmlMetadataParser(),
                new TaxGroupExtractor(), schemaEngine,
                new RuleEngine(fiscalTables(FiscalTableArtifactStore.forCurrentUser())),
                new RootCauseGrouper(), translator, new CsvExporter(), schemasVersion);
        UiBootstrap.launch(useCase, schemasVersion);
    }

    /** Seleciona a base local já verificada, sem comprometer o boot offline. */
    public static SchemaValidatorEngine schemaEngine(XsdErrorTranslator translator, SchemaArtifactStore store) {
        SchemaValidatorEngine local = store.activeEngineOrNull(translator);
        return local == null ? new SchemaValidatorEngine(translator) : local;
    }

    /** Uma tabela local corrompida nunca impede o boot nem substitui a versão embarcada. */
    public static FiscalTables fiscalTables(FiscalTableArtifactStore store) {
        FiscalTables local = store.activeOrNull();
        return local == null ? FiscalTables.load() : local;
    }
}
