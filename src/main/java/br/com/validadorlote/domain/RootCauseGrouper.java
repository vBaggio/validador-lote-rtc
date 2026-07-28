package br.com.validadorlote.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Agrupa achados pela chave própria de cada camada, determinístico, sem IA.
 *
 * <p>Schema usa código XSD e campo; rejeições usam o código da rejeição; não avaliados usam
 * causa e, quando específica da regra, seu identificador.
 */
public final class RootCauseGrouper {

    public List<RootCause> group(List<Finding> findings, RootCauseTexts texts) {
        Map<RootCauseKey, List<Finding>> byKey = findings.stream().collect(Collectors.groupingBy(
                RootCauseKey::from,
                LinkedHashMap::new, Collectors.toList()));

        return byKey.entrySet().stream()
                .map(e -> toRootCause(e.getKey(), e.getValue(), texts))
                .sorted(Comparator.comparingInt(RootCause::affectedDocuments).reversed()
                        .thenComparing(c -> c.findings().size(), Comparator.reverseOrder()))
                .toList();
    }

    private RootCause toRootCause(RootCauseKey key, List<Finding> group, RootCauseTexts texts) {
        int affected = (int) group.stream().map(Finding::source).distinct().count();
        String explanation = texts.explanation(key).orElseGet(() -> group.stream()
                .map(f -> f.friendlyMessage() != null
                        ? f.friendlyMessage() : f.officialMessage())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(""));
        return new RootCause(key, explanation, texts.action(key).orElse(null),
                group, affected);
    }
}
