package br.com.validadorlote.infrastructure.xml;

import java.net.URI;
import java.time.Instant;

/** Entrada de pacote de schemas publicada explicitamente no catálogo da SVRS. */
public record SvrsSchemaRelease(URI discoveryUrl, URI downloadUrl, String profile, Instant publishedAt) {}
