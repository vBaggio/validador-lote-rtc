package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Consulta às tabelas oficiais embarcadas. Toda busca é por código <b>e data do fato gerador</b>:
 * validar um documento de agosto contra a vigência de dezembro daria veredito errado.
 */
public final class FiscalTables {

    private final Map<String, CstEntry> csts;
    private final Map<String, ClassTribEntry> classificacoes;
    private final TablesManifest manifest;

    private FiscalTables(Map<String, CstEntry> csts, Map<String, ClassTribEntry> classificacoes) {
        this.csts = csts;
        this.classificacoes = classificacoes;
        this.manifest = new TablesManifest();
    }

    public static FiscalTables load() {
        try (InputStream in = FiscalTables.class.getResourceAsStream("/tables/cst-cclasstrib.json")) {
            if (in == null) {
                throw new IllegalStateException(
                        "Tabelas ausentes no classpath — rode ./gradlew updateFiscalTables");
            }
            return load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static FiscalTables load(InputStream in) {
        JsonNode root;
        try {
            root = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(in);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("JSON inválido na tabela fiscal", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (root == null || !root.isArray()) {
            throw new IllegalStateException("A tabela fiscal deve ser uma lista JSON de CSTs");
        }
        if (root.isEmpty()) {
            throw new IllegalStateException("A tabela fiscal não pode ser uma lista vazia");
        }

        Map<String, CstEntry> csts = new HashMap<>();
        Map<String, ClassTribEntry> classifications = new HashMap<>();
        for (JsonNode cstNode : root) {
            if (!cstNode.isObject()) {
                throw new IllegalStateException("CST inválido na tabela fiscal");
            }
            String cst = requiredText(cstNode, "cst", "CST");
            String cstContext = "CST '" + cst + "'";
            CstEntry entry = new CstEntry(cst, requiredText(cstNode, "nome", cstContext),
                    requiredBoolean(cstNode, "exigeGrupo", cstContext),
                    requiredBoolean(cstNode, "exigeReducao", cstContext),
                    requiredBoolean(cstNode, "exigeDiferimento", cstContext),
                    requiredDate(cstNode, "iniVig", cstContext),
                    optionalDate(cstNode, "fimVig", cstContext));
            if (csts.putIfAbsent(cst, entry) != null) {
                throw new IllegalStateException("CST duplicado na tabela fiscal: '" + cst + "'");
            }
            for (JsonNode classificationNode : requiredArray(cstNode, "classificacoes", cstContext)) {
                if (!classificationNode.isObject()) {
                    throw new IllegalStateException("Classificação inválida em " + cstContext);
                }
                String code = requiredText(classificationNode, "codigo", cstContext);
                String classificationContext = "classificação '" + code + "' do " + cstContext;
                ClassTribEntry classification = new ClassTribEntry(code,
                        requiredText(classificationNode, "nome", classificationContext), cst,
                        requiredBoolean(classificationNode, "nfe", classificationContext),
                        requiredBoolean(classificationNode, "nfce", classificationContext),
                        optionalDecimal(classificationNode, "percRedIbs", classificationContext),
                        optionalDecimal(classificationNode, "percRedCbs", classificationContext),
                        requiredDate(classificationNode, "iniVig", classificationContext),
                        optionalDate(classificationNode, "fimVig", classificationContext));
                if (classifications.putIfAbsent(code, classification) != null) {
                    throw new IllegalStateException(
                            "Classificação duplicada na tabela fiscal: '" + code + "'");
                }
            }
        }
        return new FiscalTables(csts, classifications);
    }

    private static String requiredText(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("Campo textual obrigatório '" + field
                    + "' inválido em " + context);
        }
        return value.asText();
    }

    private static boolean requiredBoolean(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalStateException("Campo booleano obrigatório '" + field
                    + "' inválido em " + context);
        }
        return value.booleanValue();
    }

    private static JsonNode requiredArray(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalStateException("Campo lista obrigatório '" + field
                    + "' inválido em " + context);
        }
        return value;
    }

    private static LocalDate requiredDate(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().length() < 10) {
            throw new IllegalStateException("Campo de data obrigatório '" + field
                    + "' inválido em " + context);
        }
        return parseDate(value.asText(), field, context);
    }

    private static LocalDate optionalDate(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || (!value.isNull() && !value.isTextual())) {
            throw new IllegalStateException("Campo de data opcional '" + field
                    + "' inválido em " + context);
        }
        return value.isNull() ? null : parseDate(value.asText(), field, context);
    }

    private static LocalDate parseDate(String value, String field, String context) {
        if (value.length() < 10) {
            throw new IllegalStateException("Campo de data '" + field + "' inválido em " + context);
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("Campo de data '" + field + "' inválido em " + context, e);
        }
    }

    private static BigDecimal optionalDecimal(JsonNode node, String field, String context) {
        JsonNode value = node.get(field);
        if (value == null || (!value.isNull() && !value.isNumber())) {
            throw new IllegalStateException("Campo decimal opcional '" + field
                    + "' inválido em " + context);
        }
        return value.isNull() ? null : value.decimalValue();
    }

    public Optional<CstEntry> cst(String codigo, LocalDate data) {
        return Optional.ofNullable(csts.get(codigo)).filter(c -> c.vigenteEm(data));
    }

    public Optional<ClassTribEntry> classTrib(String codigo, LocalDate data) {
        return Optional.ofNullable(classificacoes.get(codigo)).filter(c -> c.vigenteEm(data));
    }

    public String provenance() {
        return manifest.describe();
    }

    /** Contagens estruturais expostas para o diagnóstico de uma base instalada. */
    public int cstCount() {
        return csts.size();
    }

    public int classTribCount() {
        return classificacoes.size();
    }

    Set<String> cstCodes() {
        return Set.copyOf(csts.keySet());
    }

    Set<String> classTribCodes() {
        return Set.copyOf(classificacoes.keySet());
    }
}
