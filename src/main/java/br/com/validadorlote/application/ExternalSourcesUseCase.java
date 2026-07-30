package br.com.validadorlote.application;

import br.com.validadorlote.infrastructure.tables.FiscalTableArtifactStore;
import br.com.validadorlote.infrastructure.tables.TablesManifest;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCoordinator;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateEvent;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateStateStore;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import br.com.validadorlote.infrastructure.xml.ArtifactManifest;
import br.com.validadorlote.infrastructure.xml.SchemaArtifactStore;
import br.com.validadorlote.infrastructure.xml.SchemasVersion;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Instant;

/** Consulta e atualiza metadados de artefatos; não toca XMLs nem troca engines já em uso. */
public final class ExternalSourcesUseCase {

    private static final String SVRS_DOCUMENTS = "https://dfe-portal.svrs.rs.gov.br/NFe/Documentos";
    private static final String SVRS = "https://dfe-portal.svrs.rs.gov.br/";
    private static final String CALCULATOR = "https://piloto-cbs.tributos.gov.br/";
    private final ArtifactUpdateCoordinator coordinator;
    private final SchemaArtifactStore schemas;
    private final FiscalTableArtifactStore tables;
    private final ArtifactUpdateStateStore state;
    private final SchemasVersion.Metadata embeddedSchemas = SchemasVersion.metadata();
    private final TablesManifest embeddedTables = new TablesManifest();
    private final Map<ArtifactId, ArtifactUpdateEvent> currentEvents = new ConcurrentHashMap<>();

    public ExternalSourcesUseCase(ArtifactUpdateCoordinator coordinator, SchemaArtifactStore schemas,
            FiscalTableArtifactStore tables, ArtifactUpdateStateStore state) {
        this.coordinator = Objects.requireNonNull(coordinator);
        this.schemas = Objects.requireNonNull(schemas);
        this.tables = Objects.requireNonNull(tables);
        this.state = Objects.requireNonNull(state);
        coordinator.addListener(event -> currentEvents.put(event.artifact(), event));
    }

    public List<ExternalSourceStatus> status() {
        return List.of(status(ArtifactId.NFE_SCHEMAS, "Schemas NF-e/NFC-e",
                        schemas.activeManifestOrNull(), SVRS_DOCUMENTS, embeddedSchemas.profile(),
                        embeddedSchemas.closureSha256(), at(embeddedSchemas.incorporatedAt()), null, false),
                status(ArtifactId.FISCAL_TABLES, "Tabela CST/cClassTrib",
                        tables.activeManifestOrNull(), embeddedTables.source(),
                        "IT " + embeddedTables.referenceVersion(), null, at(embeddedTables.extractedAt()),
                        at(embeddedTables.lastCheckedAt()), false),
                status(ArtifactId.CALCULATOR, "Calculadora de tributos (v1)", null, CALCULATOR, true));
    }

    /** Força consulta em background; falso significa que a consulta atual continua em curso. */
    public boolean checkNow() {
        return coordinator.checkNow();
    }

    public boolean isChecking() {
        return coordinator.isRunning();
    }

    public void observe(Consumer<ArtifactUpdateEvent> listener) {
        coordinator.addListener(listener);
    }

    public void observeCompletion(Runnable listener) {
        coordinator.addCompletionListener(listener);
    }

    private ExternalSourceStatus status(ArtifactId artifact, String name, ArtifactManifest manifest,
            String fallbackOrigin, String embeddedVersion, String embeddedHash, Instant embeddedUpdatedAt,
            Instant embeddedCheckedAt, boolean notApplicable) {
        ArtifactUpdateStateStore.State saved = state.read(artifact);
        ArtifactUpdateEvent event = currentEvents.get(artifact);
        if (notApplicable) {
            return new ExternalSourceStatus(artifact, name, "Não instalado no v0", fallbackOrigin,
                    null, null, saved == null ? null : saved.lastCheckedAt(), null,
                    "Inventariado para a v1; não é baixado nem executado nesta versão.", false);
        }
        return new ExternalSourceStatus(artifact, name,
                manifest == null ? embeddedVersion + " (embarcada)" : manifest.version(),
                manifest == null ? fallbackOrigin : manifest.sourceUrl(),
                manifest == null ? abbreviate(embeddedHash) : abbreviate(manifest.sha256()),
                manifest == null ? embeddedUpdatedAt : manifest.updatedAt(),
                event != null ? event.at() : saved == null ? embeddedCheckedAt : saved.lastCheckedAt(),
                event != null ? event.status().name() : saved == null ? null : saved.result().name(),
                event != null ? event.detail() : saved == null ? null : saved.detail(), manifest != null);
    }

    private ExternalSourceStatus status(ArtifactId artifact, String name, ArtifactManifest manifest,
            String fallbackOrigin, boolean notApplicable) {
        return status(artifact, name, manifest, fallbackOrigin, "Não instalado no v0", null, null,
                null, notApplicable);
    }

    private static String abbreviate(String value) {
        if (value == null) return null;
        return value.substring(0, Math.min(12, value.length())) + "…";
    }

    private static Instant at(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
