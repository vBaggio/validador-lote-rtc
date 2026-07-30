package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Converte o contrato público da SVRS para a representação mínima do {@link FiscalTables}. */
public final class SvrsTableNormalizer {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Não preserva anexos ou colunas que o produto ainda não consome. A validação final pelo
     * {@link FiscalTables} mantém as mesmas guardas usadas pela base embarcada.
     */
    public byte[] normalize(JsonNode raw) {
        if (raw == null || !raw.isArray() || raw.isEmpty()) {
            throw new IllegalStateException("A tabela pública da SVRS deve conter CSTs");
        }
        ArrayNode normalized = JSON.createArrayNode();
        for (JsonNode rawCst : raw) {
            String cst = requiredText(rawCst, "Cst", "CST");
            ObjectNode cstNode = normalized.addObject();
            cstNode.put("cst", cst);
            cstNode.put("nome", requiredText(rawCst, "NomeCst", "CST '" + cst + "'"));
            cstNode.put("exigeGrupo", requiredBoolean(rawCst, "IndExigeTrib", "CST '" + cst + "'"));
            cstNode.put("exigeReducao", requiredBoolean(rawCst, "IndReducaoAliq", "CST '" + cst + "'"));
            cstNode.put("exigeDiferimento", requiredBoolean(rawCst, "IndDiferimento", "CST '" + cst + "'"));
            copyDate(cstNode, rawCst, "iniVig", "DthIniVig", "CST '" + cst + "'", true);
            copyDate(cstNode, rawCst, "fimVig", "DthFimVig", "CST '" + cst + "'", false);
            ArrayNode classifications = cstNode.putArray("classificacoes");
            JsonNode rawClassifications = rawCst.get("ClassificacoesTributarias");
            if (rawClassifications == null || !rawClassifications.isArray()) {
                throw new IllegalStateException("ClassificacoesTributarias inválida em CST '" + cst + "'");
            }
            for (JsonNode rawClassification : rawClassifications) {
                String code = requiredText(rawClassification, "CodClassTrib", "CST '" + cst + "'");
                String context = "classificação '" + code + "' do CST '" + cst + "'";
                if (!cst.equals(requiredText(rawClassification, "Cst", context))) {
                    throw new IllegalStateException("CST divergente em " + context);
                }
                ObjectNode classification = classifications.addObject();
                classification.put("codigo", code);
                classification.put("nome", requiredText(rawClassification, "NomeReduzido", context));
                classification.put("nfe", requiredBoolean(rawClassification, "IndNfe", context));
                classification.put("nfce", requiredBoolean(rawClassification, "IndNfce", context));
                copyDecimal(classification, rawClassification, "percRedIbs", "PercRedIbs", context);
                copyDecimal(classification, rawClassification, "percRedCbs", "PercRedCbs", context);
                copyDate(classification, rawClassification, "iniVig", "DthIniVig", context, true);
                copyDate(classification, rawClassification, "fimVig", "DthFimVig", context, false);
            }
        }
        try {
            byte[] bytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(normalized);
            FiscalTables.load(new ByteArrayInputStream(bytes));
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível serializar a tabela da SVRS", e);
        }
    }

    private String requiredText(JsonNode node, String field, String context) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("Campo textual '" + field + "' inválido em " + context);
        }
        return value.asText();
    }

    private boolean requiredBoolean(JsonNode node, String field, String context) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalStateException("Campo booleano '" + field + "' inválido em " + context);
        }
        return value.booleanValue();
    }

    private void copyDecimal(ObjectNode target, JsonNode source, String targetField,
            String sourceField, String context) {
        JsonNode value = source.get(sourceField);
        if (value == null || value.isNull()) target.putNull(targetField);
        else if (value.isNumber()) target.set(targetField, value);
        else throw new IllegalStateException("Campo decimal '" + sourceField + "' inválido em " + context);
    }

    private void copyDate(ObjectNode target, JsonNode source, String targetField,
            String sourceField, String context, boolean required) {
        JsonNode value = source.get(sourceField);
        if (value == null || value.isNull()) {
            if (required) throw new IllegalStateException("Data '" + sourceField + "' ausente em " + context);
            target.putNull(targetField);
        } else if (value.isTextual()) target.put(targetField, value.asText());
        else throw new IllegalStateException("Data '" + sourceField + "' inválida em " + context);
    }
}
