package br.com.validadorlote.application;

import br.com.validadorlote.infrastructure.tables.FiscalTableArtifactStore;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCoordinator;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateStateStore;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import br.com.validadorlote.infrastructure.xml.SchemaArtifactStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalSourcesUseCaseTest {

    @TempDir Path temp;

    @Test
    void reportsEmbeddedProvenanceOnAFreshOfflineInstall() {
        var sources = new ExternalSourcesUseCase(new ArtifactUpdateCoordinator(List.of(), Duration.ofHours(24),
                Clock.systemUTC(), Runnable::run, event -> { }, new ArtifactUpdateStateStore(temp)),
                new SchemaArtifactStore(temp), new FiscalTableArtifactStore(temp),
                new ArtifactUpdateStateStore(temp));

        ExternalSourceStatus schemas = source(sources, ArtifactId.NFE_SCHEMAS);
        assertThat(schemas.activeVersion()).isEqualTo("010e_v1.02 (embarcada)");
        assertThat(schemas.origin()).contains("nfe.fazenda.gov.br");
        assertThat(schemas.abbreviatedHash()).isEqualTo("1c7401d64600…");
        assertThat(schemas.updatedAt()).isEqualTo(Instant.parse("2026-07-29T00:00:00Z"));
        assertThat(schemas.checkedAt()).isNull();

        ExternalSourceStatus tables = source(sources, ArtifactId.FISCAL_TABLES);
        assertThat(tables.activeVersion()).isEqualTo("IT 1.60 (embarcada)");
        assertThat(tables.origin()).contains("dfe-portal.svrs.rs.gov.br");
        assertThat(tables.abbreviatedHash()).isNull();
        assertThat(tables.updatedAt()).isEqualTo(Instant.parse("2026-07-27T00:00:00Z"));
        assertThat(tables.checkedAt()).isEqualTo(Instant.parse("2026-07-29T00:00:00Z"));
    }

    private static ExternalSourceStatus source(ExternalSourcesUseCase sources, ArtifactId artifact) {
        return sources.status().stream().filter(status -> status.artifact() == artifact).findFirst().orElseThrow();
    }
}
