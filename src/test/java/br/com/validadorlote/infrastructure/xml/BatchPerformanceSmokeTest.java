package br.com.validadorlote.infrastructure.xml;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke de desempenho do lote (CA-7: 500 arquivos em menos de 2 minutos). Fica fora do build
 * padrão porque gera 500 arquivos em disco; roda com {@code ./gradlew slowTest}.
 */
@Tag("slow")
class BatchPerformanceSmokeTest {

    private static final int DOCUMENTS = 500;

    @Test
    void validatesFiveHundredDocumentsWellUnderTheBudget(@TempDir Path dir) throws IOException {
        String template = Files.readString(Path.of("src/test/resources/fixtures/nfe-valida.xml"));
        for (int i = 0; i < DOCUMENTS; i++) {
            Files.writeString(dir.resolve("doc-" + i + ".xml"),
                    template.replace("<nNF>100</nNF>", "<nNF>" + (1000 + i) + "</nNF>"));
        }
        // Schema compilado uma vez, como em produção: recompilar por arquivo custaria dezenas de
        // segundos no lote e é exatamente o erro que este smoke existe para flagrar.
        var engine = new SchemaValidatorEngine(new XsdErrorTranslator());
        var parser = new XmlMetadataParser();

        Instant start = Instant.now();
        long findings = 0;
        try (var files = Files.list(dir)) {
            for (Path xml : files.toList()) {
                findings += engine.validate(xml, parser.parse(xml)).size();
            }
        }
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(findings).as("documento válido replicado não pode gerar achado").isZero();
        assertThat(elapsed).isLessThan(Duration.ofMinutes(2));
        System.out.println(DOCUMENTS + " documentos em " + elapsed.toMillis() + " ms");
    }
}
