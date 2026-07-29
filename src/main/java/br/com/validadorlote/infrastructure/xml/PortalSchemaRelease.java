package br.com.validadorlote.infrastructure.xml;

import java.net.URI;
import java.time.Instant;

/** Versão NF-e/NFC-e que o Portal Nacional declara como oficial em uso. */
public record PortalSchemaRelease(URI discoveryUrl, URI downloadUrl, String profile,
        Instant publishedAt) {}
