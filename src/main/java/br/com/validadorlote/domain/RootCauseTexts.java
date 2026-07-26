package br.com.validadorlote.domain;

import java.util.Optional;

/** Fonte de textos amigáveis por causa-raiz. Implementada na infraestrutura. */
public interface RootCauseTexts {
    Optional<String> explanation(RootCauseKey key);
    Optional<String> action(RootCauseKey key);
}
