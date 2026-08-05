package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Converte o contrato público da SVRS para a representação fiscal do {@link FiscalTables}. */
public final class SvrsTableNormalizer {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Preserva todos os indicadores de grupos do contrato fiscal do produto. A validação final
     * pelo {@link FiscalTables} mantém as mesmas guardas usadas pela base embarcada.
     */
    public byte[] normalize(JsonNode raw) {
        if (raw == null || !raw.isArray() || raw.isEmpty()) {
            throw new IllegalStateException("A tabela pública da SVRS deve conter CSTs");
        }
        ArrayNode normalized = JSON.createArrayNode();
        for (JsonNode rawCst : raw) {
            String cst = requiredText(rawCst, "Cst", "CST");
            String cstContext = "CST '" + cst + "'";
            ObjectNode cstNode = normalized.addObject();
            cstNode.put("cst", cst);
            cstNode.put("nome", requiredText(rawCst, "NomeCst", cstContext));
            cstNode.put("exigeGrupo", requiredBoolean(rawCst, "IndExigeTrib", cstContext));
            cstNode.put("exigeReducao", requiredBoolean(rawCst, "IndReducaoAliq", cstContext));
            cstNode.put("exigeDiferimento", requiredBoolean(rawCst, "IndDiferimento", cstContext));
            cstNode.put("exigeMonofasia", requiredBoolean(rawCst, "IndMonofasica", cstContext));
            cstNode.put("exigeReducaoBaseCalculo",
                    requiredBoolean(rawCst, "IndReducaoBc", cstContext));
            cstNode.put("exigeTransferenciaCredito",
                    requiredBoolean(rawCst, "IndTransferenciaCred", cstContext));
            cstNode.put("exigeCreditoPresumidoIbsZfm",
                    requiredBoolean(rawCst, "IndCredPresIbsZfm", cstContext));
            cstNode.put("exigeAjusteCompetencia",
                    requiredBoolean(rawCst, "IndAjusteCompet", cstContext));
            copyDate(cstNode, rawCst, "iniVig", "DthIniVig", cstContext, true);
            copyDate(cstNode, rawCst, "fimVig", "DthFimVig", cstContext, false);
            ArrayNode classifications = cstNode.putArray("classificacoes");
            JsonNode rawClassifications = rawCst.get("ClassificacoesTributarias");
            if (rawClassifications == null || !rawClassifications.isArray()) {
                throw new IllegalStateException(
                        "ClassificacoesTributarias inválida em " + cstContext);
            }
            for (JsonNode rawClassification : rawClassifications) {
                String code = requiredText(rawClassification, "CodClassTrib", cstContext);
                String context = "classificação '" + code + "' do " + cstContext;
                if (!cst.equals(requiredText(rawClassification, "Cst", context))) {
                    throw new IllegalStateException("CST divergente em " + context);
                }
                ObjectNode classification = classifications.addObject();
                classification.put("codigo", code);
                classification.put("nome", requiredText(rawClassification, "NomeReduzido", context));
                classification.put("nfe", requiredBoolean(rawClassification, "IndNfe", context));
                classification.put("nfce", requiredBoolean(rawClassification, "IndNfce", context));
                classification.put("exigeTributacaoRegular",
                        requiredBoolean(rawClassification, "IndTribRegular", context));
                classification.put("permiteCreditoPresumido",
                        requiredBoolean(rawClassification, "IndPermiteCredPres", context));
                classification.put("exigeEstornoCredito",
                        requiredBoolean(rawClassification, "IndEstornoCred", context));
                classification.put("exigeMonoValor",
                        requiredBoolean(rawClassification, "IndMonoVal", context));
                classification.put("exigeMonoRetencao",
                        requiredBoolean(rawClassification, "IndMonoRetem", context));
                classification.put("exigeMonoRetido",
                        requiredBoolean(rawClassification, "IndMonoRet", context));
                classification.put("exigeMonoDiferimento",
                        requiredBoolean(rawClassification, "IndMonoDif", context));
                classification.put("exigePbioDiferenca",
                        requiredBoolean(rawClassification, "IndPbioDiferenca", context));
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
