package br.com.validadorlote.infrastructure.csv;

import br.com.validadorlote.domain.BatchReport;
import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.RootCause;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Exporta o relatório em 2 CSVs prontos para Excel pt-BR (UTF-8 BOM, ';', CRLF). */
public final class CsvExporter {

    private static final String BOM = "﻿";
    private static final String CRLF = "\r\n";

    public List<Path> export(BatchReport report, Path targetFolder) throws IOException {
        Files.createDirectories(targetFolder);
        Path causas = targetFolder.resolve("causas-raiz.csv");
        Path achados = targetFolder.resolve("achados-detalhados.csv");
        writeCauses(report, causas);
        writeFindings(report, achados);
        return List.of(causas, achados);
    }

    private void writeCauses(BatchReport report, Path file) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(BOM + contextLine(report));
            w.write("causa;campo;codigo_xsd;severidade;documentos_afetados;ocorrencias;acao_sugerida" + CRLF);
            for (RootCause c : report.rootCauses()) {
                w.write(row(c.friendlyExplanation(), c.key().field(),
                        c.key().xsdCode(), severityOf(c), String.valueOf(c.affectedDocuments()),
                        String.valueOf(c.findings().size()), c.suggestedAction()));
            }
        }
    }

    private void writeFindings(BatchReport report, Path file) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(BOM + contextLine(report));
            w.write("arquivo;chave_acesso;item;campo;codigo_xsd;severidade;linha;coluna;"
                    + "mensagem_oficial;mensagem_amigavel" + CRLF);
            for (Finding f : allFindings(report)) {
                w.write(row(f.source().toString(), f.accessKey(), toCell(f.itemNumber()),
                        f.field(), f.xsdCode(), f.severity().name(), toCell(f.line()),
                        toCell(f.column()), f.officialMessage(), f.friendlyMessage()));
            }
        }
    }

    private List<Finding> allFindings(BatchReport report) {
        return report.rootCauses().stream().flatMap(c -> c.findings().stream()).toList();
    }

    private String contextLine(BatchReport report) {
        String status = report.cancelled() ? " (ANÁLISE CANCELADA — resultados parciais)" : "";
        return "# Validador de Lote RTC — base de schemas: " + report.schemasVersion()
                + " — gerado em " + report.startedAt().atZone(ZoneId.systemDefault()).toLocalDate()
                + status + CRLF;
    }

    private String severityOf(RootCause c) {
        return c.findings().isEmpty() ? "" : c.findings().getFirst().severity().name();
    }

    private String toCell(Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    private String row(String... cells) {
        return Stream.of(cells).map(this::escape).collect(Collectors.joining(";")) + CRLF;
    }

    private String escape(String cell) {
        if (cell == null) return "";
        if (cell.contains(";") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
            return "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }
}
