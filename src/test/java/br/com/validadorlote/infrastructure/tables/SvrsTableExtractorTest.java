package br.com.validadorlote.infrastructure.tables;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SvrsTableExtractorTest {

    @Test
    void rejectsMissingEmptyAndTruncatedPayload() {
        var extractor = new SvrsTableExtractor();

        assertThatThrownBy(() -> extractor.extract("<html/>"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("dadosOriginais");
        assertThatThrownBy(() -> extractor.extract("dadosOriginais = [];"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("vazio");
        assertThatThrownBy(() -> extractor.extract("dadosOriginais = [{\"Cst\":\"000\"};"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("não terminou");
    }

    @Test
    void extractsOnlyTheCompleteJsonArrayEvenWhenAStringContainsBrackets() {
        String html = "dadosOriginais = [{\"Cst\":\"000\",\"Nome\":\"[texto]\"}]; var x = [];";

        assertThat(new SvrsTableExtractor().extract(html).get(0).get("Cst").asText()).isEqualTo("000");
    }
}
