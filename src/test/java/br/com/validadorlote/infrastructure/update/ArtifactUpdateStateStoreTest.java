package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.infrastructure.xml.ArtifactId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactUpdateStateStoreTest {

    private static final Instant CHECKED_AT = Instant.parse("2026-07-30T12:00:00Z");
    private static final String CHANNEL = "svrs-schemas-documents-v1";

    @TempDir Path temp;

    @Test
    void persistsChannelFailureAndCandidateMetadataWithoutThePayload() {
        ArtifactUpdateStateStore store = new ArtifactUpdateStateStore(temp);
        ArtifactUpdateCandidate candidate = candidate("010e_v1.03");
        store.write(CHANNEL, new ArtifactUpdateEvent(ArtifactId.NFE_SCHEMAS,
                ArtifactUpdateEvent.Status.UPDATE_AVAILABLE, CHECKED_AT, candidate, null,
                "Schemas preparados"));
        Instant failedAt = CHECKED_AT.plusSeconds(20);

        store.write(CHANNEL, new ArtifactUpdateEvent(ArtifactId.NFE_SCHEMAS,
                ArtifactUpdateEvent.Status.FAILED, failedAt, candidate,
                ArtifactFailureKind.LOCAL_STORAGE, "Não foi possível ativar os schemas"));

        ArtifactUpdateStateStore.State saved =
                store.read(ArtifactId.NFE_SCHEMAS, CHANNEL);
        assertThat(saved.channelId()).isEqualTo(CHANNEL);
        assertThat(saved.lastAttemptAt()).isEqualTo(failedAt);
        assertThat(saved.lastSuccessfulCheckAt()).isEqualTo(CHECKED_AT);
        assertThat(saved.result()).isEqualTo(ArtifactUpdateEvent.Status.FAILED);
        assertThat(saved.failureKind()).isEqualTo(ArtifactFailureKind.LOCAL_STORAGE);
        assertThat(saved.candidateVersion()).isEqualTo("010e_v1.03");
    }

    @Test
    void appliedStateClearsThePersistedCandidate() {
        ArtifactUpdateStateStore store = new ArtifactUpdateStateStore(temp);
        ArtifactUpdateCandidate candidate = candidate("010e_v1.03");
        store.write(CHANNEL, new ArtifactUpdateEvent(ArtifactId.NFE_SCHEMAS,
                ArtifactUpdateEvent.Status.UPDATE_AVAILABLE, CHECKED_AT, candidate, null,
                "Schemas preparados"));

        store.write(CHANNEL, new ArtifactUpdateEvent(ArtifactId.NFE_SCHEMAS,
                ArtifactUpdateEvent.Status.APPLIED, CHECKED_AT.plusSeconds(30), candidate, null,
                "Schemas aplicados"));

        ArtifactUpdateStateStore.State saved =
                store.read(ArtifactId.NFE_SCHEMAS, CHANNEL);
        assertThat(saved.result()).isEqualTo(ArtifactUpdateEvent.Status.APPLIED);
        assertThat(saved.candidateVersion()).isNull();
        assertThat(saved.lastSuccessfulCheckAt()).isEqualTo(CHECKED_AT);
    }

    @Test
    void ignoresLegacyStateWithoutAChannelAndMakesItDueNow() throws IOException {
        Path file = temp.resolve("artifacts/update-state.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                NFE_SCHEMAS.lastCheckedAt=2026-07-30T12\\:00\\:00Z
                NFE_SCHEMAS.result=FAILED
                NFE_SCHEMAS.detail=Não foi possível consultar a fonte HTTPS
                """);

        ArtifactUpdateStateStore store = new ArtifactUpdateStateStore(temp);

        assertThat(store.read(ArtifactId.NFE_SCHEMAS, CHANNEL)).isNull();
    }

    @Test
    void ignoresStateWrittenByAnotherChannel() {
        ArtifactUpdateStateStore store = new ArtifactUpdateStateStore(temp);
        store.write("outro-canal", new ArtifactUpdateEvent(ArtifactId.NFE_SCHEMAS,
                ArtifactUpdateEvent.Status.UP_TO_DATE, CHECKED_AT, null, null, "base atual"));

        assertThat(store.read(ArtifactId.NFE_SCHEMAS, CHANNEL)).isNull();
    }

    private static ArtifactUpdateCandidate candidate(String version) {
        return new ArtifactUpdateCandidate(ArtifactId.NFE_SCHEMAS, version,
                "https://dfe-portal.svrs.rs.gov.br/NFe/Documentos", CHECKED_AT,
                "0".repeat(64), "Schemas preparados");
    }
}
