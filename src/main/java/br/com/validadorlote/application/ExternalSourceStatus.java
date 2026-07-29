package br.com.validadorlote.application;

import br.com.validadorlote.infrastructure.xml.ArtifactId;

import java.time.Instant;

/** Resumo auditável de uma fonte externa, sem documentos do lote ou conteúdo baixado. */
public record ExternalSourceStatus(ArtifactId artifact, String name, String activeVersion,
        String origin, String abbreviatedHash, Instant updatedAt, Instant checkedAt,
        String result, String detail, boolean appliesOnNextBoot) {}
