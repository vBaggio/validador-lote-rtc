package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.RootCauseKey;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class XsdErrorTranslatorTest {

    private final XsdErrorTranslator translator = new XsdErrorTranslator();

    @Test
    void specificFieldKeyWinsOverGenericCode() {
        var t = translator.translate(FindingKind.SCHEMA, "cvc-pattern-valid", "pCBS").orElseThrow();
        assertThat(t.message()).contains("pCBS");
        assertThat(t.action()).isNotBlank();
    }

    @Test
    void fallsBackToGenericCodeKey() {
        var t = translator.translate(FindingKind.SCHEMA, "cvc-pattern-valid", "campoInventado").orElseThrow();
        assertThat(t.message()).isNotBlank();
    }

    @Test
    void unknownCodeYieldsEmpty() {
        assertThat(translator.translate(FindingKind.SCHEMA, "cvc-nunca-visto", "x")).isEmpty();
    }

    @Test
    void signatureMissingHasDedicatedText() {
        var t = translator.translate(FindingKind.SIGNATURE_MISSING, null, "Signature").orElseThrow();
        assertThat(t.message().toLowerCase()).contains("assinatura");
    }

    @Test
    void worksAsRootCauseTexts() {
        var key = new RootCauseKey(FindingKind.SCHEMA, "cvc-pattern-valid", "pCBS");
        assertThat(translator.explanation(key)).isPresent();
        assertThat(translator.action(key)).isPresent();
    }

    @Test
    void actionIsOptionalInTable() {
        var t = translator.translate(FindingKind.SCHEMA, "cvc-datatype-valid.1.2.1", "x").orElseThrow();
        assertThat(t.action()).isNull();
    }

    @Test
    void loadsAccentedTextAsUtf8() {
        var t = translator.translate(FindingKind.SCHEMA, "cvc-pattern-valid", "pCBS").orElseThrow();
        assertThat(t.message()).isEqualTo(
                "Alíquota da CBS (pCBS) com formato inválido — o schema exige 2 a 4 casas "
                        + "decimais (ex.: 0.90).");
    }

    @Test
    void blankActionAfterPipeYieldsEmptyAction() {
        var table = new Properties();
        table.setProperty("test-code", "Mensagem de teste sem ação.|   ");
        var withBlankAction = new XsdErrorTranslator(table);
        var key = new RootCauseKey(FindingKind.SCHEMA, "test-code", null);

        assertThat(withBlankAction.explanation(key)).contains("Mensagem de teste sem ação.");
        assertThat(withBlankAction.action(key)).isEmpty();
    }
}
