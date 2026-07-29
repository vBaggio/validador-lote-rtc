package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SvrsTableNormalizerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void normalizesThePublicFieldsConsumedByTheRuleEngine() throws Exception {
        byte[] normalized = new SvrsTableNormalizer().normalize(JSON.readTree(rawTable()));

        FiscalTables tables = FiscalTables.load(new ByteArrayInputStream(normalized));

        assertThat(tables.cst("000", java.time.LocalDate.of(2026, 8, 3))).isPresent();
        assertThat(tables.classTrib("000001", java.time.LocalDate.of(2026, 8, 3))).isPresent();
    }

    @Test
    void rejectsAChangedBooleanContractInsteadOfDefaultingIt() throws Exception {
        String changed = rawTable().replace("\"IndNfe\":true", "\"IndNfe\":\"true\"");

        assertThatThrownBy(() -> new SvrsTableNormalizer().normalize(JSON.readTree(changed)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("IndNfe");
    }

    @Test
    void rejectsDuplicateCodesThroughTheSameFiscalTableGate() throws Exception {
        String duplicate = rawTable().replace("}]}", "},{\"CodClassTrib\":\"000001\",\"Cst\":\"000\",\"NomeReduzido\":\"Duplicada\",\"IndNfe\":true,\"IndNfce\":true,\"PercRedIbs\":0,\"PercRedCbs\":0,\"DthIniVig\":\"2025-05-05\",\"DthFimVig\":null}]}");

        assertThatThrownBy(() -> new SvrsTableNormalizer().normalize(JSON.readTree(duplicate)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("duplicada");
    }

    static String rawTable() {
        return """
                [{"Cst":"000","NomeCst":"Tributação integral","IndExigeTrib":true,
                  "IndReducaoAliq":false,"IndDiferimento":false,"DthIniVig":"2025-05-01",
                  "DthFimVig":null,"ClassificacoesTributarias":[{"CodClassTrib":"000001",
                  "Cst":"000","NomeReduzido":"Tributada","IndNfe":true,"IndNfce":true,
                  "PercRedIbs":0,"PercRedCbs":0,"DthIniVig":"2025-05-05","DthFimVig":null}]}]
                """;
    }
}
