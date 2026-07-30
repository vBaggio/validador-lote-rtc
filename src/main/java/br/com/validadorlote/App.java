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
import br.com.validadorlote.infrastructure.xml.CuratedSchemaManifestParser;
import br.com.validadorlote.infrastructure.xml.CuratedSchemaUpdater;
import br.com.validadorlote.infrastructure.xml.Ed25519ManifestVerifier;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.SchemaArtifactStore;
import br.com.validadorlote.infrastructure.xml.SchemasVersion;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import br.com.validadorlote.infrastructure.xml.XsdErrorTranslator;

import java.nio.file.Path;
import java.net.URI;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import br.com.validadorlote.presentation.swing.UiBootstrap;

/** Ponto de entrada: monta o grafo de objetos e entrega à camada de apresentação. */
public final class App {

    static final String SCHEMA_CHANNEL_ID = "curated-schemas-stable-v1";
    static final URI SCHEMA_MANIFEST_URI = URI.create(
            "https://vbaggio.github.io/validador-lote-rtc-bases/stable.json");
    private static final String SCHEMA_KEY_ID = "schemas-2026-01";
    private static final String SCHEMA_PUBLIC_KEY =
            "MCowBQYDK2VwAyEA20h//V2xUUkgSm+K7WjWLjWaXmmm6i6AB71DPBooSpQ=";
    private static final String APP_VERSION = "0.1.0";

    private App() {}

    public static void main(String[] args) {
        var translator = new XsdErrorTranslator();
        var schemaStore = SchemaArtifactStore.forCurrentUser();
        var tableStore = FiscalTableArtifactStore.forCurrentUser();
        var schemasRuntime = schemaRuntime(translator, schemaStore);
        var useCase = new ValidateBatchUseCase(new FolderScanner(), new XmlMetadataParser(),
                new TaxGroupExtractor(), schemasRuntime.engine(),
                new RuleEngine(fiscalTables(tableStore)),
                new RootCauseGrouper(), translator, new CsvExporter(),
                schemasRuntime.provenance());
        var updateState = ArtifactUpdateStateStore.forCurrentUser();
        var coordinator = updateCoordinator(schemaStore, tableStore, updateState,
                schemasRuntime.activeManifest());
        var externalSources = new ExternalSourcesUseCase(coordinator, schemaStore, tableStore);
        UiBootstrap.launch(useCase, schemasRuntime.provenance(), externalSources,
                coordinator::checkAfterBoot);
    }

    /** Seleciona a base local já verificada, sem comprometer o boot offline. */
    public static SchemaValidatorEngine schemaEngine(XsdErrorTranslator translator, SchemaArtifactStore store) {
        return schemaRuntime(translator, store).engine();
    }

    /** Seleciona engine e proveniência da mesma base íntegra carregada no boot. */
    static SchemaRuntime schemaRuntime(XsdErrorTranslator translator, SchemaArtifactStore store) {
        SchemaValidatorEngine local = store.activeEngineOrNull(translator);
        ArtifactManifest active = local == null ? null : store.activeManifestOrNull();
        if (local != null && active != null) {
            return new SchemaRuntime(local, activeProvenance(active), Optional.of(active));
        }
        return new SchemaRuntime(new SchemaValidatorEngine(translator), SchemasVersion.read(),
                Optional.empty());
    }

    /** Uma tabela local corrompida nunca impede o boot nem substitui a versão embarcada. */
    public static FiscalTables fiscalTables(FiscalTableArtifactStore store) {
        FiscalTables local = store.activeOrNull();
        return local == null ? FiscalTables.load() : local;
    }

    private static ArtifactUpdateCoordinator updateCoordinator(SchemaArtifactStore schemaStore,
            FiscalTableArtifactStore tableStore, ArtifactUpdateStateStore updateState,
            Optional<ArtifactManifest> activeSchemas) {
        var tables = new SvrsTableUpdater(SafeHttpsClient.forSvrs(), new SvrsTableExtractor(),
                new SvrsTableNormalizer(), tableStore);
        var executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "artifact-updater");
            thread.setDaemon(true);
            return thread;
        });
        return new ArtifactUpdateCoordinator(
                updateActions(schemaUpdater(schemaStore), SCHEMA_CHANNEL_ID, tables, activeSchemas),
                ArtifactUpdateCoordinator.DEFAULT_INTERVAL, Clock.systemUTC(), executor, event -> { },
                updateState, ArtifactRetryPolicy.production());
    }

    /** Constrói o único canal runtime confiado; chave e hosts são escolhidos pelo aplicativo. */
    static Optional<CuratedSchemaUpdater> schemaUpdater(SchemaArtifactStore store) {
        return Optional.of(new CuratedSchemaUpdater(
                SafeHttpsClient.forCuratedSchemaManifest(Set.of(SCHEMA_MANIFEST_URI.getHost())),
                SafeHttpsClient.forCuratedSchemaZip(Set.of(SCHEMA_MANIFEST_URI.getHost())),
                new CuratedSchemaManifestParser(),
                schemaManifestVerifier(),
                new br.com.validadorlote.infrastructure.xml.SchemaZipExtractor(), store,
                SCHEMA_CHANNEL_ID, SCHEMA_MANIFEST_URI, APP_VERSION));
    }

    /** Verificador do único trust anchor de manifests de schemas publicado pelo aplicativo. */
    static Ed25519ManifestVerifier schemaManifestVerifier() {
        return new Ed25519ManifestVerifier(Map.of(SCHEMA_KEY_ID, SCHEMA_PUBLIC_KEY));
    }

    static List<ArtifactUpdateAction> updateActions(Optional<CuratedSchemaUpdater> schemas,
            String schemaChannelId, SvrsTableUpdater tables,
            Optional<ArtifactManifest> activeSchemas) {
        ArtifactUpdateAction schemaAction = new ArtifactUpdateAction() {
            @Override public ArtifactId artifact() { return ArtifactId.NFE_SCHEMAS; }
            @Override public String channelId() { return schemaChannelId; }
            @Override public ArtifactCheckResult check() {
                return schemas.map(CuratedSchemaUpdater::check)
                        .orElseGet(() -> ArtifactCheckResult.upToDate(
                                disabledSchemaDetail(activeSchemas)));
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

    private static String activeProvenance(ArtifactManifest manifest) {
        String source = manifest.provenance().lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .collect(java.util.stream.Collectors.joining("; "));
        if (source.isEmpty()) {
            source = manifest.sourceUrl();
        }
        String channel = manifest.releaseSequence() > 0 ? "canal curado; " : "";
        return "schemas " + manifest.version() + " (" + channel + "publicado em "
                + manifest.publishedAt().atZone(ZoneOffset.UTC).toLocalDate()
                + "; " + source + ")";
    }

    private static String disabledSchemaDetail(Optional<ArtifactManifest> activeSchemas) {
        return activeSchemas
                .map(active -> "Atualização pelo canal curado está desabilitada nesta versão; "
                        + (active.releaseSequence() > 0 ? "a base curada ativa "
                                : "a base local ativa ")
                        + active.version() + " continua em uso")
                .orElse("Atualização pelo canal curado está desabilitada nesta versão; "
                        + "a base embarcada continua ativa");
    }

    record SchemaRuntime(SchemaValidatorEngine engine, String provenance,
            Optional<ArtifactManifest> activeManifest) { }
}
