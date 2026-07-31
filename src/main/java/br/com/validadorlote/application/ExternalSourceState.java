package br.com.validadorlote.application;

import br.com.validadorlote.infrastructure.update.ArtifactFailureKind;
import br.com.validadorlote.infrastructure.xml.ArtifactId;

import java.time.Instant;
import java.util.Objects;

/** Estado auditável e imutável de uma base externa, sem conteúdo baixado. */
public record ExternalSourceState(
        ArtifactId artifact,
        String name,
        String activeVersion,
        boolean embedded,
        String origin,
        String candidateOrigin,
        String abbreviatedHash,
        Instant updatedAt,
        Instant checkedAt,
        ExternalSourcePhase phase,
        String detail,
        ArtifactFailureKind failureKind,
        String candidateVersion) {

    public ExternalSourceState {
        Objects.requireNonNull(artifact);
        Objects.requireNonNull(name);
        Objects.requireNonNull(activeVersion);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(phase);
    }

    /** Identifica o item de inventário futuro sem expor sua identidade à camada Swing. */
    public boolean isCalculator() {
        return artifact == ArtifactId.CALCULATOR;
    }

    /** Evita que a apresentação dependa diretamente do vocabulário de infraestrutura. */
    public boolean hasUnsupportedSchemaStructure() {
        return failureKind == ArtifactFailureKind.UNSUPPORTED_SCHEMA_STRUCTURE;
    }
}
