package br.com.validadorlote.application;

import java.nio.file.Path;

/** Entrada de uma execução de lote. */
public record BatchRequest(Path folder, boolean preEmissionMode) {}
