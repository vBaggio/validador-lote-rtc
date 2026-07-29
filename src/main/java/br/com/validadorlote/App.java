package br.com.validadorlote;

import br.com.validadorlote.application.ValidateBatchUseCase;
import br.com.validadorlote.domain.RootCauseGrouper;
import br.com.validadorlote.infrastructure.csv.CsvExporter;
import br.com.validadorlote.infrastructure.fs.FolderScanner;
import br.com.validadorlote.infrastructure.rules.RuleEngine;
import br.com.validadorlote.infrastructure.tables.FiscalTableArtifactStore;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.tables.SafeHttpsClient;
import br.com.validadorlote.infrastructure.tables.SvrsTableExtractor;
import br.com.validadorlote.infrastructure.tables.SvrsTableNormalizer;
import br.com.validadorlote.infrastructure.tables.SvrsTableUpdater;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateAction;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCoordinator;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateStateStore;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import br.com.validadorlote.infrastructure.xml.PortalSchemaCatalogParser;
import br.com.validadorlote.infrastructure.xml.PortalSchemaUpdater;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.SchemaArtifactStore;
import br.com.validadorlote.infrastructure.xml.SchemaZipExtractor;
import br.com.validadorlote.infrastructure.xml.SchemasVersion;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import br.com.validadorlote.infrastructure.xml.XsdErrorTranslator;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executors;
import br.com.validadorlote.presentation.swing.UiBootstrap;

/** Ponto de entrada: monta o grafo de objetos e entrega à camada de apresentação. */
public final class App {

    private App() {}

    public static void main(String[] args) {
        String schemasVersion = SchemasVersion.read();
        var translator = new XsdErrorTranslator();
        var schemaStore = SchemaArtifactStore.forCurrentUser();
        var tableStore = FiscalTableArtifactStore.forCurrentUser();
        var schemaEngine = schemaEngine(translator, schemaStore);
        var useCase = new ValidateBatchUseCase(new FolderScanner(), new XmlMetadataParser(),
                new TaxGroupExtractor(), schemaEngine,
                new RuleEngine(fiscalTables(tableStore)),
                new RootCauseGrouper(), translator, new CsvExporter(), schemasVersion);
        UiBootstrap.launch(useCase, schemasVersion, updateCoordinator(schemaStore, tableStore)::checkAfterBoot);
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

    private static ArtifactUpdateCoordinator updateCoordinator(SchemaArtifactStore schemaStore,
            FiscalTableArtifactStore tableStore) {
        var schemas = new PortalSchemaUpdater(SafeHttpsClient.forNationalPortal(),
                new PortalSchemaCatalogParser(), new SchemaZipExtractor(), schemaStore);
        var tables = new SvrsTableUpdater(SafeHttpsClient.forSvrs(), new SvrsTableExtractor(),
                new SvrsTableNormalizer(), tableStore);
        var executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "artifact-updater");
            thread.setDaemon(true);
            return thread;
        });
        return new ArtifactUpdateCoordinator(List.of(action(ArtifactId.NFE_SCHEMAS, schemas::updateIfNew),
                action(ArtifactId.FISCAL_TABLES, tables::updateIfNew)),
                ArtifactUpdateCoordinator.DEFAULT_INTERVAL, Clock.systemUTC(), executor, event -> { },
                ArtifactUpdateStateStore.forCurrentUser());
    }

    private static ArtifactUpdateAction action(ArtifactId artifact, java.util.function.BooleanSupplier update) {
        return new ArtifactUpdateAction() {
            @Override public ArtifactId artifact() { return artifact; }
            @Override public boolean updateIfNew() { return update.getAsBoolean(); }
        };
    }
}
