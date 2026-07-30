package br.com.validadorlote;

import br.com.validadorlote.application.ValidateBatchUseCase;
import br.com.validadorlote.application.ExternalSourcesUseCase;
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
import br.com.validadorlote.infrastructure.update.ArtifactCheckResult;
import br.com.validadorlote.infrastructure.update.ArtifactRetryPolicy;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCandidate;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCoordinator;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateStateStore;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import br.com.validadorlote.infrastructure.xml.ArtifactManifest;
import br.com.validadorlote.infrastructure.xml.CuratedSchemaUpdater;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.SchemaArtifactStore;
import br.com.validadorlote.infrastructure.xml.SchemasVersion;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import br.com.validadorlote.infrastructure.xml.XsdErrorTranslator;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import br.com.validadorlote.presentation.swing.UiBootstrap;

/** Ponto de entrada: monta o grafo de objetos e entrega à camada de apresentação. */
public final class App {

    private static final String DISABLED_SCHEMA_CHANNEL = "curated-schemas-disabled-v1";
    private static final String DISABLED_SCHEMA_DETAIL =
            "Atualização pelo canal curado está desabilitada nesta versão; "
                    + "a base embarcada continua ativa";

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
        var updateState = ArtifactUpdateStateStore.forCurrentUser();
        var coordinator = updateCoordinator(schemaStore, tableStore, updateState);
        var externalSources = new ExternalSourcesUseCase(coordinator, schemaStore, tableStore);
        UiBootstrap.launch(useCase, schemasVersion, externalSources, coordinator::checkAfterBoot);
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
            FiscalTableArtifactStore tableStore, ArtifactUpdateStateStore updateState) {
        var tables = new SvrsTableUpdater(SafeHttpsClient.forSvrs(), new SvrsTableExtractor(),
                new SvrsTableNormalizer(), tableStore);
        var executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "artifact-updater");
            thread.setDaemon(true);
            return thread;
        });
        return new ArtifactUpdateCoordinator(
                updateActions(Optional.empty(), DISABLED_SCHEMA_CHANNEL, tables),
                ArtifactUpdateCoordinator.DEFAULT_INTERVAL, Clock.systemUTC(), executor, event -> { },
                updateState, ArtifactRetryPolicy.production());
    }

    static List<ArtifactUpdateAction> updateActions(Optional<CuratedSchemaUpdater> schemas,
            String schemaChannelId, SvrsTableUpdater tables) {
        ArtifactUpdateAction schemaAction = new ArtifactUpdateAction() {
            @Override public ArtifactId artifact() { return ArtifactId.NFE_SCHEMAS; }
            @Override public String channelId() { return schemaChannelId; }
            @Override public ArtifactCheckResult check() {
                return schemas.map(CuratedSchemaUpdater::check)
                        .orElseGet(() -> ArtifactCheckResult.upToDate(DISABLED_SCHEMA_DETAIL));
            }
            @Override public ArtifactManifest apply(ArtifactUpdateCandidate candidate) {
                return schemas.orElseThrow(() -> new IllegalStateException(
                        "Canal curado de schemas desabilitado")).apply(candidate);
            }
        };
        ArtifactUpdateAction tableAction = new ArtifactUpdateAction() {
            @Override public ArtifactId artifact() { return ArtifactId.FISCAL_TABLES; }
            @Override public String channelId() { return "svrs-fiscal-table-v1"; }
            @Override public ArtifactCheckResult check() { return tables.check(); }
            @Override public ArtifactManifest apply(ArtifactUpdateCandidate candidate) {
                return tables.apply(candidate);
            }
        };
        return List.of(schemaAction, tableAction);
    }
}
