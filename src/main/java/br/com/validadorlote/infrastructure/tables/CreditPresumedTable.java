package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Consulta à tabela oficial cCredPres instalada; sem entrada vigente não há veredito fiscal. */
public final class CreditPresumedTable {
    private final Map<String, CreditPresumedEntry> entries;

    public CreditPresumedTable(Map<String, CreditPresumedEntry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public static CreditPresumedTable load() {
        try (InputStream in = CreditPresumedTable.class.getResourceAsStream("/tables/ccredpres.json")) {
            if (in == null) throw new IllegalStateException("Tabela cCredPres ausente");
            JsonNode root = new ObjectMapper().readTree(in);
            if (root == null || !root.isArray() || root.isEmpty()) {
                throw new IllegalStateException("Tabela cCredPres inválida");
            }
            Map<String, CreditPresumedEntry> entries = new HashMap<>();
            for (JsonNode node : root) {
                String code = node.path("codigo").asText();
                JsonNode start = node.get("ibsInicio");
                LocalDate ibsStart = start == null || start.isNull() ? null : LocalDate.parse(start.asText());
                if (code.isBlank() || !node.path("deduzTotal").isBoolean()
                        || entries.putIfAbsent(code, new CreditPresumedEntry(code,
                        node.path("deduzTotal").booleanValue(), ibsStart, null)) != null) {
                    throw new IllegalStateException("Entrada cCredPres inválida: " + code);
                }
            }
            return new CreditPresumedTable(entries);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler tabela cCredPres", e);
        }
    }

    public Optional<CreditPresumedEntry> find(String code, LocalDate issueDate) {
        if (code == null || issueDate == null) return Optional.empty();
        return Optional.ofNullable(entries.get(code.trim())).filter(e -> e.applicableToIbs(issueDate));
    }

    int size() {
        return entries.size();
    }
}
