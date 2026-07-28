package br.com.validadorlote.infrastructure.csv;

import br.com.validadorlote.domain.BatchReport;
import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.RootCause;
import br.com.validadorlote.domain.RootCauseKey;
import br.com.validadorlote.domain.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExporterTest {

    private BatchReport sampleReport() {
        var finding = new Finding(Path.of("notas/a.xml"), "352607...890", 1, FindingKind.SCHEMA,
                Severity.REJECTION, "pCBS", "cvc-pattern-valid",
                "mensagem; com ponto-e-vírgula e \"aspas\"", "Alíquota inválida", 42, 7,
                null, null, null);
        var cause = new RootCause(new RootCauseKey(FindingKind.SCHEMA, "cvc-pattern-valid", "pCBS"),
                "Alíquota da CBS com formato inválido", "Corrija o pCBS", List.of(finding), 1);
        return new BatchReport(Instant.parse("2026-07-26T12:00:00Z"), Duration.ofSeconds(3),
                10, 1, 0, false, List.of(cause), "motor 1.2.4 / base V0039 (extração 2026-07-26)");
    }

    @Test
    void writesBothFilesWithBomSemicolonAndCrlf(@TempDir Path dir) throws IOException {
        var written = new CsvExporter().export(sampleReport(), dir);

        assertThat(written).containsExactly(
                dir.resolve("causas-raiz.csv"), dir.resolve("achados-detalhados.csv"));

        byte[] causas = Files.readAllBytes(written.getFirst());
        assertThat(causas[0] & 0xFF).isEqualTo(0xEF);
        assertThat(causas[1] & 0xFF).isEqualTo(0xBB);
        assertThat(causas[2] & 0xFF).isEqualTo(0xBF);

        String content = new String(causas, StandardCharsets.UTF_8).substring(1);
        assertThat(content).startsWith("# Validador de Lote RTC");
        assertThat(content).contains("motor 1.2.4");
        assertThat(content).contains("\r\n");
        assertThat(content).contains(
                "causa;campo;codigo_xsd;severidade;documentos_afetados;ocorrencias;acao_sugerida");
        assertThat(content).contains(
                "Alíquota da CBS com formato inválido;pCBS;cvc-pattern-valid;REJECTION;1;1;Corrija o pCBS");
    }

    @Test
    void escapesSeparatorAndQuotesInDetailFile(@TempDir Path dir) throws IOException {
        var written = new CsvExporter().export(sampleReport(), dir);
        String detail = Files.readString(written.getLast(), StandardCharsets.UTF_8);

        assertThat(detail).contains(
                "arquivo;chave_acesso;item;campo;codigo_xsd;severidade;linha;coluna;mensagem_oficial;mensagem_amigavel");
        assertThat(detail).contains("\"mensagem; com ponto-e-vírgula e \"\"aspas\"\"\"");
        assertThat(detail).contains(";42;7;");
    }

    @Test
    void nullFieldsBecomeEmptyCells(@TempDir Path dir) throws IOException {
        var unreadable = new Finding(Path.of("x.xml"), null, null, FindingKind.UNREADABLE,
                Severity.WARNING, null, null, "ilegível", null, null, null, null, null, null);
        var cause = new RootCause(new RootCauseKey(FindingKind.UNREADABLE, null, null),
                "Arquivo ilegível", null, List.of(unreadable), 1);
        var report = new BatchReport(Instant.now(), Duration.ZERO, 1, 1, 1, false,
                List.of(cause), "v");

        var written = new CsvExporter().export(report, dir);
        String detail = Files.readString(written.getLast(), StandardCharsets.UTF_8);
        assertThat(detail).contains("x.xml;;;;;WARNING;;;ilegível;");
    }

    @Test
    void rejectionAndNotEvaluatedFindingsExportWithoutXsdCode(@TempDir Path dir) throws IOException {
        var rejection = Finding.rejection(Path.of("b.xml"), "chave", 2, "1115", "UB12-10",
                "Rejeição: grupo IBS/CBS não informado", "Corrija o item 2");
        var notEvaluated = Finding.notEvaluated(Path.of("c.xml"), "chave2", 3,
                br.com.validadorlote.domain.NotEvaluatedCause.CST_NOT_IN_TABLE, "GroupForbiddenRule",
                "CST fora da base embarcada");
        var cause1 = new RootCause(RootCauseKey.from(rejection), "Grupo ausente", "Corrija",
                List.of(rejection), 1);
        var cause2 = new RootCause(RootCauseKey.from(notEvaluated), "CST fora da base", null,
                List.of(notEvaluated), 1);
        var report = new BatchReport(Instant.now(), Duration.ZERO, 2, 2, 0, false,
                List.of(cause1, cause2), "v");

        var written = new CsvExporter().export(report, dir);
        String detail = Files.readString(written.getLast(), StandardCharsets.UTF_8);
        assertThat(detail).contains("b.xml;chave;2;;;REJECTION;;;Rejeição: grupo IBS/CBS não informado;Corrija o item 2");
        assertThat(detail).contains("c.xml;chave2;3;;;INFO;;;;CST fora da base embarcada");
    }
}
