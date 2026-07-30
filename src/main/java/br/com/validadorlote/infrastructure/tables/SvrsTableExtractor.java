package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.regex.Pattern;

/** Extrai o contrato público `dadosOriginais` sem aceitar página parcial como tabela. */
public final class SvrsTableExtractor {
    private static final Pattern ASSIGNMENT = Pattern.compile("\\bdadosOriginais\\s*=");

    public JsonNode extract(String html) {
        var assignment = ASSIGNMENT.matcher(html);
        if (!assignment.find()) {
            throw new IllegalStateException("SVRS não publicou dadosOriginais; formato mudou");
        }
        int start = skipWhitespace(html, assignment.end());
        if (start == html.length() || html.charAt(start) != '[') {
            throw new IllegalStateException("dadosOriginais não é uma lista JSON; formato mudou");
        }
        try {
            JsonNode node = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(html.substring(start, arrayEnd(html, start)));
            if (!node.isArray() || node.isEmpty()) throw new IllegalStateException("dadosOriginais vazio ou inválido");
            return node;
        } catch (IOException e) { throw new IllegalStateException("dadosOriginais inválido", e); }
    }

    private int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        return index;
    }

    /** Localiza o fim da lista sem confundir colchetes dentro de strings JSON. */
    private int arrayEnd(String value, int start) {
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') quoted = true;
            else if (current == '[') depth++;
            else if (current == ']' && --depth == 0) return index + 1;
        }
        throw new IllegalStateException("dadosOriginais não terminou como lista JSON; formato mudou");
    }
}
