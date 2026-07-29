# Plano de Implementação — Validador de Lote RTC v0

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir o v0 do Validador de Lote RTC: app desktop Swing que valida em lote XMLs de NF-e/NFC-e contra os schemas XSD oficiais da Reforma Tributária (coletando todos os erros), agrupa por causa-raiz, exporta CSV e é distribuído como instalador Windows via GitHub Releases.

**Architecture:** Arquitetura em camadas + MVP (spec `docs/superpowers/specs/2026-07-26-validador-lote-rtc-design.md`). Regra de dependência: `presentation → application → {domain, infrastructure}`; `infrastructure → domain`; `domain → nada`. Swing só em `presentation/`. Caso de uso de lote roda em pool de workers, coleta total de erros XSD, agrupamento determinístico.

**Tech Stack:** Java 21, Gradle 8.14.3 (wrapper), Swing + FlatLaf 3.6 (única dep de runtime), JUnit 5.11.4 + AssertJ 3.26.3 + ArchUnit 1.3.0 (teste), jpackage, GitHub Actions.

## Global Constraints

- Pacote raiz: `br.com.validadorlote`. Código (classes/métodos/variáveis) em **inglês**; mensagens de UI, erros amigáveis e docs em **pt-BR**.
- Única dependência de runtime: `com.formdev:flatlaf:3.6`. Nenhuma outra sem decisão registrada.
- Parsing XML **sempre** seguro: `FEATURE_SECURE_PROCESSING`, DOCTYPE proibido, entidades externas desligadas (XMLs de terceiros são não-confiáveis).
- `Schema` XSD compilado **uma única vez** por processo; `Validator` criado por documento.
- Comentários enxutos; javadoc onde agrega. Sem dead code.
- Commits semânticos com escopo do bloco: `feat(b2): ...`, `test(b1): ...`, `docs(b0): ...`, `build(b5): ...`. **1 commit por task.** Terminar mensagens com `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- **Ajuste sequencial em algo do último commit ainda não pushado → `git commit --amend`**, nunca cadeia de commits de fix. Depois de pushado, commit novo.
- Branch por bloco: `bloco/N-nome`; PR por bloco; nunca commitar direto na `main` após o Bloco 0 iniciar.
- Testes rodam com `./gradlew test` (exclui tag `slow`). Java 21 já está no PATH (Homebrew). NÃO há `gradle` no PATH — sempre `./gradlew`.
- Diretório do projeto: `/var/home/vbaggio/Documents/dev/projects/validador-lote-rtc`. Identidade git local já configurada.
- Fixtures reais da descoberta vivem em `docs/calculadora/payloads/` e `docs/calculadora/pares/` — usar como semente de fixtures de teste.
- Severidades: `SCHEMA→REJECTION`; `SIGNATURE_MISSING→INFO` (modo pré-emissão ligado) ou `REJECTION` (desligado); `UNREADABLE→WARNING`.

---


## Blocos B0-B2 — entregues

Fundação/harness, domínio+varredura+parse, e motor XSD+tradutor+fixtures. As 17 tasks estão
mergeadas em `main` (PRs #1-#3). Conteúdo integral arquivado em
[`done/2026-07-26-v0-validador-lote-rtc-b0-b2.md`](./done/2026-07-26-v0-validador-lote-rtc-b0-b2.md)
— só abrir se precisar do detalhe original de uma task específica; o resumo por task já está no ledger
(`.superpowers/sdd/progress.md`).

---

## Bloco B3 — Caso de uso e CSV (branch `bloco/3-usecase-csv`)

Antes da primeira task: `git checkout main && git pull && git checkout -b bloco/3-usecase-csv`

### Task 18: CsvExporter

**✅ entregue** (commit `19c5321`, revisão independente PASS/PASS). Ver ledger.

**Files:**
- Create: `src/main/java/br/com/validadorlote/infrastructure/csv/CsvExporter.java`
- Test: `src/test/java/br/com/validadorlote/infrastructure/csv/CsvExporterTest.java`

**Interfaces:**
- Consumes: `BatchReport`, `RootCause`, `Finding` (Task 7).
- Produces: `public final class CsvExporter { public List<Path> export(BatchReport report, Path targetFolder) throws IOException; }` — grava `causas-raiz.csv` e `achados-detalhados.csv`; UTF-8 **com BOM**, separador `;`, quebras CRLF, 1ª linha de contexto iniciada por `#`.
- Colunas `causas-raiz.csv`: `causa;campo;codigo_xsd;severidade;documentos_afetados;ocorrencias;acao_sugerida`
- Colunas `achados-detalhados.csv`: `arquivo;chave_acesso;item;campo;codigo_xsd;severidade;linha;coluna;mensagem_oficial;mensagem_amigavel`

- [ ] **Step 1: Escrever teste que falha**

```java
package br.com.validadorlote.infrastructure.csv;

import br.com.validadorlote.domain.*;
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
                "mensagem; com ponto-e-vírgula e \"aspas\"", "Alíquota inválida", 42, 7);
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
                Severity.WARNING, null, null, "ilegível", null, null, null);
        var cause = new RootCause(new RootCauseKey(FindingKind.UNREADABLE, null, null),
                "Arquivo ilegível", null, List.of(unreadable), 1);
        var report = new BatchReport(Instant.now(), Duration.ZERO, 1, 1, 1, false,
                List.of(cause), "v");

        var written = new CsvExporter().export(report, dir);
        String detail = Files.readString(written.getLast(), StandardCharsets.UTF_8);
        assertThat(detail).contains("x.xml;;;;;WARNING;;;ilegível;");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.csv.*' --console=plain`
Expected: FALHA de compilação.

- [ ] **Step 3: Implementar**

```java
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

    private static final String BOM = "\uFEFF";
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
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.csv.*' --console=plain`
Expected: 3 testes passando.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/validadorlote/infrastructure/csv src/test/java/br/com/validadorlote/infrastructure/csv
git commit -m "feat(b3): CsvExporter com BOM, separador ';' e escaping para Excel pt-BR"
```

### Task 19: ValidateBatchUseCase (orquestração com pool, progresso e cancelamento)

**✅ entregue** (commit `b9b0574`, revisão independente PASS/PASS). Plano estava desatualizado
(escrito antes do `RuleEngine` existir) — ver adendo/D-043 e ledger.

**Files:**
- Create: `src/main/java/br/com/validadorlote/application/ValidateBatchUseCase.java`, `BatchRequest.java`, `ProgressListener.java`, `CancellationToken.java`
- Test: `src/test/java/br/com/validadorlote/application/ValidateBatchUseCaseTest.java`

**Interfaces:**
- Consumes: `FolderScanner`/`ScanException` (Task 10), `XmlMetadataParser`/`UnreadableXmlException` (Task 11), `SchemaValidatorEngine` (Task 14), `RootCauseGrouper`, `RootCauseTexts`, `FindingReclassifier`, records do domínio (Tasks 7–8), `CsvExporter` (Task 18), `SchemasVersion` (Task 14).
- Produces:
```java
public record BatchRequest(Path folder, boolean preEmissionMode) {}
public interface ProgressListener { void onProgress(int processed, int total); }
public final class CancellationToken { public void cancel(); public boolean isCancelled(); }
public final class ValidateBatchUseCase {
    public ValidateBatchUseCase(FolderScanner scanner, XmlMetadataParser parser,
        SchemaValidatorEngine engine, RootCauseGrouper grouper, RootCauseTexts texts,
        CsvExporter csvExporter, String schemasVersion);
    public BatchReport execute(BatchRequest request, ProgressListener listener, CancellationToken token);
    public BatchReport regroup(BatchReport previous, boolean preEmissionMode); // toggle sem revalidar
    public List<Path> exportCsv(BatchReport report, Path targetFolder) throws IOException;
}
```
- Semântica: pool fixo `availableProcessors`; arquivo ilegível vira `Finding` UNREADABLE (nunca aborta o lote); `ScanException` propaga (pasta inválida é erro de entrada, não de lote); cancelamento para de submeter/processar novos arquivos e devolve relatório parcial `cancelled=true`; `documentsWithFindings` conta arquivos distintos com achado; `documentsUnreadable` conta arquivos distintos com achado UNREADABLE.

- [ ] **Step 1: Escrever teste que falha**

```java
package br.com.validadorlote.application;

import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.RootCauseGrouper;
import br.com.validadorlote.domain.Severity;
import br.com.validadorlote.infrastructure.csv.CsvExporter;
import br.com.validadorlote.infrastructure.fs.FolderScanner;
import br.com.validadorlote.infrastructure.fs.ScanException;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import br.com.validadorlote.infrastructure.xml.XsdErrorTranslator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateBatchUseCaseTest {

    private static ValidateBatchUseCase useCase;

    @BeforeAll
    static void setup() {
        var translator = new XsdErrorTranslator();
        useCase = new ValidateBatchUseCase(new FolderScanner(), new XmlMetadataParser(),
                new SchemaValidatorEngine(translator), new RootCauseGrouper(), translator,
                new CsvExporter(), "motor-teste");
    }

    private void copyFixture(Path dir, String fixture, String as) throws IOException {
        Files.copy(Path.of("src/test/resources/fixtures/" + fixture), dir.resolve(as));
    }

    @Test
    void mixedBatchNeverAborts(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida.xml", "ok.xml");
        copyFixture(dir, "nfe-minima-invalida.xml", "ruim.xml");
        Files.writeString(dir.resolve("lixo.xml"), "isto não é xml");

        var report = useCase.execute(new BatchRequest(dir, true), (p, t) -> {}, new CancellationToken());

        assertThat(report.documentsScanned()).isEqualTo(3);
        assertThat(report.documentsUnreadable()).isEqualTo(1);
        assertThat(report.documentsWithFindings()).isEqualTo(2); // ruim + lixo (ok.xml limpo)
        assertThat(report.cancelled()).isFalse();
        assertThat(report.schemasVersion()).isEqualTo("motor-teste");
        assertThat(report.rootCauses()).isNotEmpty();
    }

    @Test
    void progressIsReportedForEveryFile(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida.xml", "a.xml");
        copyFixture(dir, "nfe-valida.xml", "b.xml");
        var events = new CopyOnWriteArrayList<int[]>();

        useCase.execute(new BatchRequest(dir, true),
                (p, t) -> events.add(new int[]{p, t}), new CancellationToken());

        assertThat(events).hasSize(2);
        assertThat(events).allSatisfy(e -> assertThat(e[1]).isEqualTo(2));
        assertThat(events.getLast()[0]).isEqualTo(2);
    }

    @Test
    void emptyFolderYieldsEmptyReportNotError(@TempDir Path dir) {
        var report = useCase.execute(new BatchRequest(dir, true), (p, t) -> {}, new CancellationToken());
        assertThat(report.documentsScanned()).isZero();
        assertThat(report.rootCauses()).isEmpty();
    }

    @Test
    void missingFolderThrowsScanException(@TempDir Path dir) {
        assertThatThrownBy(() -> useCase.execute(
                new BatchRequest(dir.resolve("nao-existe"), true), (p, t) -> {}, new CancellationToken()))
                .isInstanceOf(ScanException.class);
    }

    @Test
    void cancelledBeforeStartYieldsPartialFlaggedReport(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida.xml", "a.xml");
        var token = new CancellationToken();
        token.cancel();

        var report = useCase.execute(new BatchRequest(dir, true), (p, t) -> {}, token);

        assertThat(report.cancelled()).isTrue();
        assertThat(report.documentsScanned()).isEqualTo(1);
    }

    @Test
    void preEmissionModeControlsSignatureSeverity(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida-sem-assinatura.xml", "semass.xml");

        var reportOn = useCase.execute(new BatchRequest(dir, true), (p, t) -> {}, new CancellationToken());
        var signatureOn = reportOn.rootCauses().stream()
                .filter(c -> c.key().kind() == FindingKind.SIGNATURE_MISSING).findFirst().orElseThrow();
        assertThat(signatureOn.findings().getFirst().severity()).isEqualTo(Severity.INFO);

        var reportOff = useCase.regroup(reportOn, false);
        var signatureOff = reportOff.rootCauses().stream()
                .filter(c -> c.key().kind() == FindingKind.SIGNATURE_MISSING).findFirst().orElseThrow();
        assertThat(signatureOff.findings().getFirst().severity()).isEqualTo(Severity.REJECTION);
    }

    @Test
    void exportCsvDelegates(@TempDir Path dir, @TempDir Path out) throws IOException {
        copyFixture(dir, "nfe-minima-invalida.xml", "ruim.xml");
        var report = useCase.execute(new BatchRequest(dir, true), (p, t) -> {}, new CancellationToken());

        List<Path> written = useCase.exportCsv(report, out);

        assertThat(written).allSatisfy(p -> assertThat(p).exists());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.application.*' --console=plain`
Expected: FALHA de compilação.

- [ ] **Step 3: Implementar**

`BatchRequest.java`:
```java
package br.com.validadorlote.application;

import java.nio.file.Path;

/** Entrada de uma execução de lote. */
public record BatchRequest(Path folder, boolean preEmissionMode) {}
```

`ProgressListener.java`:
```java
package br.com.validadorlote.application;

/** Callback de progresso, neutro de toolkit — marshalling de thread é problema do chamador. */
public interface ProgressListener {
    void onProgress(int processed, int total);
}
```

`CancellationToken.java`:
```java
package br.com.validadorlote.application;

import java.util.concurrent.atomic.AtomicBoolean;

/** Sinal cooperativo de cancelamento de um lote em execução. */
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    public void cancel() { cancelled.set(true); }
    public boolean isCancelled() { return cancelled.get(); }
}
```

`ValidateBatchUseCase.java`:
```java
package br.com.validadorlote.application;

import br.com.validadorlote.domain.BatchReport;
import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.FindingReclassifier;
import br.com.validadorlote.domain.RootCauseGrouper;
import br.com.validadorlote.domain.RootCauseTexts;
import br.com.validadorlote.domain.Severity;
import br.com.validadorlote.infrastructure.csv.CsvExporter;
import br.com.validadorlote.infrastructure.fs.FolderScanner;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.UnreadableXmlException;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Orquestra o lote: varre, valida em paralelo, agrupa e monta o relatório. */
public final class ValidateBatchUseCase {

    private final FolderScanner scanner;
    private final XmlMetadataParser parser;
    private final SchemaValidatorEngine engine;
    private final RootCauseGrouper grouper;
    private final RootCauseTexts texts;
    private final CsvExporter csvExporter;
    private final String schemasVersion;

    public ValidateBatchUseCase(FolderScanner scanner, XmlMetadataParser parser,
            SchemaValidatorEngine engine, RootCauseGrouper grouper, RootCauseTexts texts,
            CsvExporter csvExporter, String schemasVersion) {
        this.scanner = scanner;
        this.parser = parser;
        this.engine = engine;
        this.grouper = grouper;
        this.texts = texts;
        this.csvExporter = csvExporter;
        this.schemasVersion = schemasVersion;
    }

    public BatchReport execute(BatchRequest request, ProgressListener listener, CancellationToken token) {
        Instant start = Instant.now();
        List<Path> files = scanner.scan(request.folder());
        List<Finding> findings = new ArrayList<>();
        AtomicInteger processed = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(
                Math.max(1, Runtime.getRuntime().availableProcessors()));
        try {
            CompletionService<List<Finding>> completion = new ExecutorCompletionService<>(pool);
            int submitted = 0;
            for (Path file : files) {
                if (token.isCancelled()) break;
                completion.submit(() -> validateOne(file, token));
                submitted++;
            }
            for (int i = 0; i < submitted; i++) {
                try {
                    findings.addAll(completion.take().get());
                } catch (java.util.concurrent.ExecutionException e) {
                    // validateOne nunca lança; cinto de segurança para nunca abortar o lote
                    findings.add(unexpected(files.get(i), e.getCause()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                listener.onProgress(processed.incrementAndGet(), files.size());
            }
        } finally {
            pool.shutdownNow();
        }

        List<Finding> reclassified = FindingReclassifier.reclassify(findings, request.preEmissionMode());
        return buildReport(start, files.size(), reclassified, token.isCancelled());
    }

    /** Reaplica o modo pré-emissão sobre um relatório existente, sem revalidar arquivos. */
    public BatchReport regroup(BatchReport previous, boolean preEmissionMode) {
        List<Finding> all = previous.rootCauses().stream()
                .flatMap(c -> c.findings().stream()).toList();
        List<Finding> reclassified = FindingReclassifier.reclassify(all, preEmissionMode);
        return new BatchReport(previous.startedAt(), previous.elapsed(), previous.documentsScanned(),
                previous.documentsWithFindings(), previous.documentsUnreadable(), previous.cancelled(),
                grouper.group(reclassified, texts), previous.schemasVersion());
    }

    public List<Path> exportCsv(BatchReport report, Path targetFolder) throws IOException {
        return csvExporter.export(report, targetFolder);
    }

    private List<Finding> validateOne(Path file, CancellationToken token) {
        if (token.isCancelled()) return List.of();
        try {
            var meta = parser.parse(file);
            return engine.validate(file, meta);
        } catch (UnreadableXmlException e) {
            return List.of(new Finding(file, null, null, FindingKind.UNREADABLE, Severity.WARNING,
                    null, null, e.getMessage(), null, null, null));
        } catch (RuntimeException e) {
            return List.of(unexpected(file, e));
        }
    }

    private Finding unexpected(Path file, Throwable e) {
        String message = "Falha inesperada ao processar: "
                + (e == null ? "erro desconhecido" : e.getMessage());
        return new Finding(file, null, null, FindingKind.UNREADABLE, Severity.WARNING,
                null, null, message, null, null, null);
    }

    private BatchReport buildReport(Instant start, int scanned, List<Finding> findings, boolean cancelled) {
        int withFindings = (int) findings.stream().map(Finding::source).distinct().count();
        int unreadable = (int) findings.stream()
                .filter(f -> f.kind() == FindingKind.UNREADABLE)
                .map(Finding::source).distinct().count();
        return new BatchReport(start, Duration.between(start, Instant.now()), scanned,
                withFindings, unreadable, cancelled, grouper.group(findings, texts), schemasVersion);
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --console=plain`
Expected: suíte inteira verde (7 novos testes de application; ArchUnit confirma fronteiras).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/validadorlote/application src/test/java/br/com/validadorlote/application
git commit -m "feat(b3): ValidateBatchUseCase com pool, progresso, cancelamento e export"
```

### Task 20: Fechamento do Bloco 3 (PR + merge)

**Status:** PR #6 aberto, CI verde, push feito. Merge aguardando confirmação (mesmo padrão B6/B7).

- [ ] **Step 1: Suíte completa**

Run: `./gradlew test --console=plain` — Expected: verde.

- [ ] **Step 2: Push e PR**

```bash
git push -u origin bloco/3-usecase-csv
gh pr create --title "B3: caso de uso do lote e exportação CSV" --body "$(cat <<'EOF'
Bloco 3: CsvExporter (BOM, ';', CRLF, escaping) e ValidateBatchUseCase
(pool availableProcessors, progresso, cancelamento cooperativo, regroup
do modo pré-emissão sem revalidação, export delegado).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
gh pr checks --watch
```

- [ ] **Step 3: GATE DE REVIEW DO BLOCO** — review do orquestrador antes do merge.

- [ ] **Step 4: Merge**

```bash
gh pr merge --merge --delete-branch
git checkout main && git pull
```

---

## Bloco B4 — Interface Swing (branch `bloco/4-ui`)

**✅ Encerrado em 29/07/2026, com adendo de produto D-045.** As Tasks 21–23 foram entregues e
revisadas; a validação visual do dono levou a uma revisão deliberada do fluxo, incorporada antes do
merge local. Onde este plano descrever validação imediata, visão primária por causa, toggle
pré-emissão ou exportação na tela, prevalece D-045: o usuário compõe o lote antes de validar, a
grade principal é de documentos e CSV fica temporariamente sem ação de UI. Detalhes e evidência no
ledger. O ícone SVG de runtime também cria requisito para a Task 25: fornecer ícone nativo ao
`jpackage`, sobretudo `.ico` no Windows.

Antes da primeira task: `git checkout main && git pull && git checkout -b bloco/4-ui`

### Task 21: MainPresenter + contratos de view (MVP)

**Files:**
- Create: `src/main/java/br/com/validadorlote/presentation/MainView.java`, `UiThread.java`, `MainPresenter.java`
- Test: `src/test/java/br/com/validadorlote/presentation/MainPresenterTest.java`

**Interfaces:**
- Consumes: `ValidateBatchUseCase`, `BatchRequest`, `CancellationToken` (Task 19), `BatchReport` (Task 7), `ScanException` (Task 10).
- Produces:
```java
public interface UiThread { void execute(Runnable action); }
public interface MainView {
    void showIdle();
    void showRunning(int processed, int total);
    void showResults(BatchReport report);
    void showError(String message);
    void showExportSuccess(java.nio.file.Path folder);
    void showExportError(String message);
}
public final class MainPresenter {
    public MainPresenter(ValidateBatchUseCase useCase, UiThread uiThread, java.util.concurrent.Executor background);
    public void attach(MainView view);       // chama view.showIdle()
    public void folderChosen(Path folder);
    public void cancelRequested();
    public void preEmissionToggled(boolean on);
    public void exportRequested(Path targetFolder);
    public void newAnalysisRequested();
}
```
- Toggle default: **ligado** (pré-emissão). Views Swing implementam `MainView`; o presenter nunca importa Swing.

- [ ] **Step 1: Escrever teste que falha**

```java
package br.com.validadorlote.presentation;

import br.com.validadorlote.application.*;
import br.com.validadorlote.domain.BatchReport;
import br.com.validadorlote.domain.RootCauseGrouper;
import br.com.validadorlote.infrastructure.csv.CsvExporter;
import br.com.validadorlote.infrastructure.fs.FolderScanner;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import br.com.validadorlote.infrastructure.xml.XsdErrorTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MainPresenterTest {

    private final List<String> calls = new ArrayList<>();
    private BatchReport lastResults;
    private MainPresenter presenter;

    private final MainView fakeView = new MainView() {
        public void showIdle() { calls.add("idle"); }
        public void showRunning(int processed, int total) { calls.add("running " + processed + "/" + total); }
        public void showResults(BatchReport report) { calls.add("results"); lastResults = report; }
        public void showError(String message) { calls.add("error: " + message); }
        public void showExportSuccess(Path folder) { calls.add("exportOk"); }
        public void showExportError(String message) { calls.add("exportErr"); }
    };

    @BeforeEach
    void setup() {
        var translator = new XsdErrorTranslator();
        var useCase = new ValidateBatchUseCase(new FolderScanner(), new XmlMetadataParser(),
                new SchemaValidatorEngine(translator), new RootCauseGrouper(), translator,
                new CsvExporter(), "motor-teste");
        // Executor e UiThread síncronos tornam o teste determinístico.
        presenter = new MainPresenter(useCase, Runnable::run, Runnable::run);
        presenter.attach(fakeView);
    }

    @Test
    void attachShowsIdle() {
        assertThat(calls).containsExactly("idle");
    }

    @Test
    void folderChosenRunsBatchAndShowsResults(@TempDir Path dir) throws IOException {
        Files.copy(Path.of("src/test/resources/fixtures/nfe-minima-invalida.xml"), dir.resolve("a.xml"));

        presenter.folderChosen(dir);

        assertThat(calls).contains("results");
        assertThat(lastResults.documentsScanned()).isEqualTo(1);
    }

    @Test
    void scanFailureShowsErrorNotCrash(@TempDir Path dir) {
        presenter.folderChosen(dir.resolve("nao-existe"));
        assertThat(calls).anySatisfy(c -> assertThat(c).startsWith("error:"));
    }

    @Test
    void toggleRegroupsLastReport(@TempDir Path dir) throws IOException {
        Files.copy(Path.of("src/test/resources/fixtures/nfe-valida-sem-assinatura.xml"), dir.resolve("a.xml"));
        presenter.folderChosen(dir);
        int resultsBefore = (int) calls.stream().filter("results"::equals).count();

        presenter.preEmissionToggled(false);

        assertThat(calls.stream().filter("results"::equals).count()).isEqualTo(resultsBefore + 1);
    }

    @Test
    void exportBeforeAnyRunReportsError(@TempDir Path out) {
        presenter.exportRequested(out);
        assertThat(calls).contains("exportErr");
    }

    @Test
    void exportAfterRunSucceeds(@TempDir Path dir, @TempDir Path out) throws IOException {
        Files.copy(Path.of("src/test/resources/fixtures/nfe-minima-invalida.xml"), dir.resolve("a.xml"));
        presenter.folderChosen(dir);

        presenter.exportRequested(out);

        assertThat(calls).contains("exportOk");
        assertThat(out.resolve("causas-raiz.csv")).exists();
    }

    @Test
    void newAnalysisReturnsToIdle(@TempDir Path dir) throws IOException {
        Files.copy(Path.of("src/test/resources/fixtures/nfe-valida.xml"), dir.resolve("a.xml"));
        presenter.folderChosen(dir);

        presenter.newAnalysisRequested();

        assertThat(calls.getLast()).isEqualTo("idle");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.presentation.*' --console=plain`
Expected: FALHA de compilação.

- [ ] **Step 3: Implementar**

`UiThread.java`:
```java
package br.com.validadorlote.presentation;

/** Executa ações na thread de UI. Implementação Swing usa SwingUtilities.invokeLater. */
public interface UiThread {
    void execute(Runnable action);
}
```

`MainView.java`:
```java
package br.com.validadorlote.presentation;

import br.com.validadorlote.domain.BatchReport;

import java.nio.file.Path;

/** Contrato da tela principal. Implementações Swing são passivas (sem lógica). */
public interface MainView {
    void showIdle();
    void showRunning(int processed, int total);
    void showResults(BatchReport report);
    void showError(String message);
    void showExportSuccess(Path folder);
    void showExportError(String message);
}
```

`MainPresenter.java`:
```java
package br.com.validadorlote.presentation;

import br.com.validadorlote.application.BatchRequest;
import br.com.validadorlote.application.CancellationToken;
import br.com.validadorlote.application.ValidateBatchUseCase;
import br.com.validadorlote.domain.BatchReport;
import br.com.validadorlote.infrastructure.fs.ScanException;

import java.nio.file.Path;
import java.util.concurrent.Executor;

/** Lógica da tela principal: dispara o lote em background e publica estados na view. */
public final class MainPresenter {

    private final ValidateBatchUseCase useCase;
    private final UiThread uiThread;
    private final Executor background;

    private MainView view;
    private volatile BatchReport lastReport;
    private volatile CancellationToken currentToken = new CancellationToken();
    private volatile boolean preEmissionMode = true;

    public MainPresenter(ValidateBatchUseCase useCase, UiThread uiThread, Executor background) {
        this.useCase = useCase;
        this.uiThread = uiThread;
        this.background = background;
    }

    public void attach(MainView view) {
        this.view = view;
        view.showIdle();
    }

    public void folderChosen(Path folder) {
        currentToken = new CancellationToken();
        CancellationToken token = currentToken;
        view.showRunning(0, 0);
        background.execute(() -> {
            try {
                BatchReport report = useCase.execute(new BatchRequest(folder, preEmissionMode),
                        (p, t) -> uiThread.execute(() -> view.showRunning(p, t)), token);
                lastReport = report;
                uiThread.execute(() -> view.showResults(report));
            } catch (ScanException e) {
                uiThread.execute(() -> view.showError(e.getMessage()));
            } catch (RuntimeException e) {
                uiThread.execute(() -> view.showError("Erro inesperado: " + e.getMessage()));
            }
        });
    }

    public void cancelRequested() {
        currentToken.cancel();
    }

    public void preEmissionToggled(boolean on) {
        preEmissionMode = on;
        BatchReport report = lastReport;
        if (report != null) {
            lastReport = useCase.regroup(report, on);
            view.showResults(lastReport);
        }
    }

    public void exportRequested(Path targetFolder) {
        BatchReport report = lastReport;
        if (report == null) {
            view.showExportError("Nenhuma análise para exportar.");
            return;
        }
        background.execute(() -> {
            try {
                useCase.exportCsv(report, targetFolder);
                uiThread.execute(() -> view.showExportSuccess(targetFolder));
            } catch (Exception e) {
                uiThread.execute(() -> view.showExportError(
                        "Não foi possível gravar o CSV: " + e.getMessage()));
            }
        });
    }

    public void newAnalysisRequested() {
        currentToken.cancel();
        lastReport = null;
        view.showIdle();
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.presentation.*' --console=plain`
Expected: 7 testes passando.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/validadorlote/presentation src/test/java/br/com/validadorlote/presentation
git commit -m "feat(b4): MainPresenter e contratos de view (MVP, toolkit-agnóstico)"
```

### Task 22: Shell Swing — App, tema, frame e zona de drop

**Files:**
- Create: `src/main/java/br/com/validadorlote/App.java`
- Create: `src/main/java/br/com/validadorlote/presentation/swing/UiBootstrap.java`, `SwingUiThread.java`, `MainFrame.java`, `DropZonePanel.java`, `RunningPanel.java`

**Interfaces:**
- Consumes: `MainPresenter`, `MainView`, `UiThread` (Task 21), `ValidateBatchUseCase` e colaboradores (Task 19), `SchemasVersion` (Task 14).
- Produces: `UiBootstrap.launch(ValidateBatchUseCase, String schemasVersion)` — monta presenter+frame na EDT. `MainFrame implements MainView` com `CardLayout` (cartões `"drop"`, `"running"`, `"results"`; o cartão results chega na Task 23 como placeholder `JPanel`).

- [ ] **Step 1: Implementar App**

```java
package br.com.validadorlote;

import br.com.validadorlote.application.ValidateBatchUseCase;
import br.com.validadorlote.domain.RootCauseGrouper;
import br.com.validadorlote.infrastructure.csv.CsvExporter;
import br.com.validadorlote.infrastructure.fs.FolderScanner;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.SchemasVersion;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import br.com.validadorlote.infrastructure.xml.XsdErrorTranslator;
import br.com.validadorlote.presentation.swing.UiBootstrap;

/** Ponto de entrada: monta o grafo de objetos e entrega à camada de apresentação. */
public final class App {

    private App() {}

    public static void main(String[] args) {
        var translator = new XsdErrorTranslator();
        var useCase = new ValidateBatchUseCase(new FolderScanner(), new XmlMetadataParser(),
                new SchemaValidatorEngine(translator), new RootCauseGrouper(), translator,
                new CsvExporter(), SchemasVersion.read());
        UiBootstrap.launch(useCase, SchemasVersion.read());
    }
}
```

- [ ] **Step 2: Implementar bootstrap e frame**

`SwingUiThread.java`:
```java
package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.presentation.UiThread;

import javax.swing.SwingUtilities;

/** Marshalling para a EDT. */
public final class SwingUiThread implements UiThread {
    @Override
    public void execute(Runnable action) {
        SwingUtilities.invokeLater(action);
    }
}
```

`UiBootstrap.java`:
```java
package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.application.ValidateBatchUseCase;
import br.com.validadorlote.presentation.MainPresenter;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.SwingUtilities;
import java.util.concurrent.Executors;

/** Sobe a UI Swing: tema, presenter, frame — tudo na EDT. */
public final class UiBootstrap {

    private UiBootstrap() {}

    public static void launch(ValidateBatchUseCase useCase, String schemasVersion) {
        FlatLightLaf.setup();
        var presenter = new MainPresenter(useCase, new SwingUiThread(),
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "batch-runner");
                    t.setDaemon(true);
                    return t;
                }));
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(presenter, schemasVersion);
            presenter.attach(frame);
            frame.setVisible(true);
        });
    }
}
```

`DropZonePanel.java`:
```java
package br.com.validadorlote.presentation.swing;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.TransferHandler;
import java.awt.Component;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Zona de arrastar-e-soltar de pasta, com botão alternativo de escolha. */
public final class DropZonePanel extends JPanel {

    public DropZonePanel(Consumer<Path> onFolderChosen) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(60, 40, 60, 40));

        JLabel title = new JLabel("Arraste aqui a pasta com os XMLs");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("NF-e e NFC-e — a análise roda 100% no seu computador");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton choose = new JButton("Escolher pasta...");
        choose.setAlignmentX(Component.CENTER_ALIGNMENT);
        choose.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                onFolderChosen.accept(chooser.getSelectedFile().toPath());
            }
        });

        add(Box.createVerticalGlue());
        add(title);
        add(Box.createVerticalStrut(8));
        add(subtitle);
        add(Box.createVerticalStrut(24));
        add(choose);
        add(Box.createVerticalGlue());

        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    return files.stream().filter(File::isDirectory).findFirst()
                            .map(dir -> { onFolderChosen.accept(dir.toPath()); return true; })
                            .orElse(false);
                } catch (Exception e) {
                    return false;
                }
            }
        });
    }
}
```

`RunningPanel.java`:
```java
package br.com.validadorlote.presentation.swing;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.Component;

/** Progresso do lote com botão de cancelar. */
public final class RunningPanel extends JPanel {

    private final JProgressBar bar = new JProgressBar();
    private final JLabel label = new JLabel("Preparando análise...");

    public RunningPanel(Runnable onCancel) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(80, 60, 80, 60));
        bar.setStringPainted(true);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        bar.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton cancel = new JButton("Cancelar");
        cancel.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancel.addActionListener(e -> onCancel.run());
        add(Box.createVerticalGlue());
        add(label);
        add(Box.createVerticalStrut(12));
        add(bar);
        add(Box.createVerticalStrut(24));
        add(cancel);
        add(Box.createVerticalGlue());
    }

    void update(int processed, int total) {
        if (total > 0) {
            bar.setIndeterminate(false);
            bar.setMaximum(total);
            bar.setValue(processed);
            label.setText("Validando " + processed + " de " + total + " arquivos...");
        } else {
            bar.setIndeterminate(true);
            label.setText("Lendo a pasta...");
        }
    }
}
```

`MainFrame.java` (o cartão de resultados ainda é placeholder; a Task 23 o substitui):
```java
package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.domain.BatchReport;
import br.com.validadorlote.presentation.MainPresenter;
import br.com.validadorlote.presentation.MainView;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.nio.file.Path;

/** Janela principal: view passiva, alterna cartões conforme o presenter manda. */
public final class MainFrame extends JFrame implements MainView {

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final RunningPanel runningPanel;
    private final JPanel resultsPlaceholder = new JPanel();

    public MainFrame(MainPresenter presenter, String schemasVersion) {
        super("Validador de Lote RTC — ferramenta independente (base " + schemasVersion + ")");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        runningPanel = new RunningPanel(presenter::cancelRequested);
        resultsPlaceholder.add(new JLabel("Resultados na próxima task"));
        root.add(new DropZonePanel(presenter::folderChosen), "drop");
        root.add(runningPanel, "running");
        root.add(resultsPlaceholder, "results");
        setContentPane(root);
        setSize(900, 620);
        setLocationRelativeTo(null);
    }

    @Override
    public void showIdle() { cards.show(root, "drop"); }

    @Override
    public void showRunning(int processed, int total) {
        runningPanel.update(processed, total);
        cards.show(root, "running");
    }

    @Override
    public void showResults(BatchReport report) { cards.show(root, "results"); }

    @Override
    public void showError(String message) {
        cards.show(root, "drop");
        JOptionPane.showMessageDialog(this, message, "Erro", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showExportSuccess(Path folder) {
        JOptionPane.showMessageDialog(this, "CSVs gravados em:\n" + folder,
                "Exportação concluída", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showExportError(String message) {
        JOptionPane.showMessageDialog(this, message, "Falha na exportação", JOptionPane.ERROR_MESSAGE);
    }
}
```

- [ ] **Step 3: Compilar, testar e verificar manualmente**

Run: `./gradlew test --console=plain` — Expected: verde (ArchUnit valida a fronteira Swing).
Verificação manual (requer sessão gráfica): `./gradlew run` → janela abre com FlatLaf; arrastar (ou escolher) uma pasta com XMLs → cartão de progresso aparece e volta ao placeholder de resultados. Se o ambiente for headless, registre no relatório da task e deixe a verificação visual para o review do bloco.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/br/com/validadorlote/App.java src/main/java/br/com/validadorlote/presentation/swing
git commit -m "feat(b4): shell Swing com FlatLaf, drop de pasta e progresso cancelável"
```

### Task 23: ResultsPanel mestre-detalhe com toggle e exportação

**Files:**
- Create: `src/main/java/br/com/validadorlote/presentation/swing/ResultsPanel.java`, `CausesTableModel.java`, `FindingsTableModel.java`
- Modify: `src/main/java/br/com/validadorlote/presentation/swing/MainFrame.java` (substituir placeholder)

**Interfaces:**
- Consumes: `BatchReport`, `RootCause`, `Finding` (Task 7); `MainPresenter` (Task 21).
- Produces: `ResultsPanel.show(BatchReport report)` — resumo, tabela de causas (Causa | Campo | Severidade | Docs | Ocorrências | Ação sugerida), detalhe da causa selecionada (Arquivo | Item | Linha | Mensagem), toggle "XMLs pré-emissão", botões "Exportar CSV" e "Nova análise".

- [ ] **Step 1: Implementar os table models**

`CausesTableModel.java`:
```java
package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.domain.RootCause;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/** Tabela de causas-raiz (linhas = causas agrupadas). */
final class CausesTableModel extends AbstractTableModel {

    private static final String[] COLUMNS =
            {"Causa", "Campo", "Severidade", "Documentos", "Ocorrências", "Ação sugerida"};

    private List<RootCause> causes = List.of();

    void setCauses(List<RootCause> causes) {
        this.causes = causes;
        fireTableDataChanged();
    }

    RootCause causeAt(int row) { return causes.get(row); }

    @Override public int getRowCount() { return causes.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int c) { return COLUMNS[c]; }

    @Override
    public Object getValueAt(int row, int col) {
        RootCause c = causes.get(row);
        return switch (col) {
            case 0 -> c.friendlyExplanation();
            case 1 -> c.key().field() == null ? "" : c.key().field();
            case 2 -> c.findings().isEmpty() ? "" : switch (c.findings().getFirst().severity()) {
                case REJECTION -> "Rejeição";
                case WARNING -> "Aviso";
                case INFO -> "Informativo";
            };
            case 3 -> c.affectedDocuments();
            case 4 -> c.findings().size();
            default -> c.suggestedAction() == null ? "" : c.suggestedAction();
        };
    }
}
```

`FindingsTableModel.java`:
```java
package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.domain.Finding;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/** Tabela de achados da causa selecionada. */
final class FindingsTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Arquivo", "Item", "Linha", "Mensagem oficial"};

    private List<Finding> findings = List.of();

    void setFindings(List<Finding> findings) {
        this.findings = findings;
        fireTableDataChanged();
    }

    @Override public int getRowCount() { return findings.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int c) { return COLUMNS[c]; }

    @Override
    public Object getValueAt(int row, int col) {
        Finding f = findings.get(row);
        return switch (col) {
            case 0 -> f.source().getFileName().toString();
            case 1 -> f.itemNumber() == null ? "" : f.itemNumber();
            case 2 -> f.line() == null ? "" : f.line();
            default -> f.officialMessage();
        };
    }
}
```

- [ ] **Step 2: Implementar ResultsPanel**

```java
package br.com.validadorlote.presentation.swing;

import br.com.validadorlote.domain.BatchReport;
import br.com.validadorlote.presentation.MainPresenter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

/** Cartão de resultados: resumo, causas (mestre), achados (detalhe), toggle e ações. */
public final class ResultsPanel extends JPanel {

    private final JLabel summary = new JLabel();
    private final CausesTableModel causesModel = new CausesTableModel();
    private final FindingsTableModel findingsModel = new FindingsTableModel();
    private final JCheckBox preEmission =
            new JCheckBox("XMLs pré-emissão (assinatura ausente vira informativo)", true);

    public ResultsPanel(MainPresenter presenter) {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTable causesTable = new JTable(causesModel);
        causesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JTable findingsTable = new JTable(findingsModel);
        causesTable.getSelectionModel().addListSelectionListener(e -> {
            int row = causesTable.getSelectedRow();
            findingsModel.setFindings(row < 0 ? List.of()
                    : causesModel.causeAt(causesTable.convertRowIndexToModel(row)).findings());
        });

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(causesTable), new JScrollPane(findingsTable));
        split.setResizeWeight(0.6);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(summary);
        top.add(Box.createVerticalStrut(4));
        preEmission.addActionListener(e -> presenter.preEmissionToggled(preEmission.isSelected()));
        top.add(preEmission);

        JButton export = new JButton("Exportar CSV...");
        export.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Escolha a pasta para gravar os CSVs");
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                presenter.exportRequested(chooser.getSelectedFile().toPath());
            }
        });
        JButton newAnalysis = new JButton("Nova análise");
        newAnalysis.addActionListener(e -> presenter.newAnalysisRequested());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(newAnalysis);
        actions.add(export);

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    void show(BatchReport report) {
        String cancelled = report.cancelled() ? "  •  ANÁLISE CANCELADA (parcial)" : "";
        summary.setText(String.format(
                "%d arquivos analisados  •  %d com achados  •  %d ilegíveis  •  base %s%s",
                report.documentsScanned(), report.documentsWithFindings(),
                report.documentsUnreadable(), report.schemasVersion(), cancelled));
        causesModel.setCauses(report.rootCauses());
        findingsModel.setFindings(List.of());
    }
}
```

- [ ] **Step 3: Ligar no MainFrame** (substituir o placeholder)

Em `MainFrame.java`: remova o campo `resultsPlaceholder` e o import de `JLabel`; adicione o campo `private final ResultsPanel resultsPanel;`; no construtor: `resultsPanel = new ResultsPanel(presenter);` e `root.add(resultsPanel, "results");`; em `showResults`: `resultsPanel.show(report); cards.show(root, "results");`.

- [ ] **Step 4: Compilar, testar e verificar manualmente**

Run: `./gradlew test --console=plain` — Expected: verde.
Manual (sessão gráfica, substituído por D-045): importar uma pasta ou XMLs individuais; conferir os
metadados do lote; validar pendentes; observar status e progresso por linha; interromper e retomar
somente os pendentes; selecionar documento para ver seus problemas; remover os válidos. Arquivos
ilegíveis devem ser recusados em diálogo. CSV não faz parte do fluxo visual atual.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/validadorlote/presentation/swing
git commit -m "feat(b4): ResultsPanel mestre-detalhe com toggle pré-emissão e exportação"
```

### Task 24: Fechamento do Bloco 4 (PR + merge)

**✅ Encerrada localmente em 29/07/2026.** A suíte, o lint de diff e o smoke de inicialização foram
reexecutados após D-045. A validação visual do dono foi a fonte dos ajustes. O merge local preserva
o histórico da branch; push/PR remoto continua uma ação separada, quando o dono quiser publicar.

- [ ] **Step 1: Suíte completa**

Run: `./gradlew test --console=plain` — Expected: verde.

- [ ] **Step 2: Push e PR**

```bash
git push -u origin bloco/4-ui
gh pr create --title "B4: interface Swing (MVP) — drop, progresso, resultados, export" --body "$(cat <<'EOF'
Bloco 4: MainPresenter com testes (view fake, executores síncronos), shell Swing
FlatDarkLaf/Roboto, lote importado antes da validação, progresso cancelável no grid e detalhe por
documento. CSV está temporariamente fora da UI (D-045). Swing confinado a presentation/ (ArchUnit).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
gh pr checks --watch
```

- [ ] **Step 3: GATE DE REVIEW DO BLOCO** — review do orquestrador + validação visual do Vinícius (screenshot bem-vindo aqui para o README do B5).

- [ ] **Step 4: Merge**

```bash
gh pr merge --merge --delete-branch
git checkout main && git pull
```

---

## Bloco B5 — Empacotamento, release e README (branch `bloco/5-release`)

Antes da primeira task: `git checkout main && git pull && git checkout -b bloco/5-release`

### Task 25: Tasks Gradle de jpackage (runtime jlink enxuto)

**ADENDO D-045:** além das tasks descritas, preparar o ícone nativo do instalador e passá-lo ao
`jpackage` por `--icon` (no Windows, `.ico`). O SVG em `resources/images/` atende a janela Swing,
mas não é contrato suficiente para o shell/atalho do sistema operacional.

**Files:**
- Modify: `build.gradle` (adicionar ao final)

**Interfaces:**
- Produces: `./gradlew jpackageImage` (app-image, qualquer SO) e `./gradlew jpackageInstaller` (msi no Windows, deb no Linux, dmg no macOS), saída em `build/jpackage/`.

- [ ] **Step 1: Adicionar ao build.gradle**

```groovy
// ---------- Empacotamento (D-008: jlink enxuto no v0) ----------
def jpackageModules = 'java.base,java.desktop,java.xml,java.logging,java.prefs,jdk.unsupported'

def jpackageArgs(String type) {
    def libDir = layout.buildDirectory.dir("install/${project.name}/lib").get().asFile
    return [
            "${System.getProperty('java.home')}/bin/jpackage",
            '--type', type,
            '--name', 'ValidadorLoteRTC',
            '--app-version', version.toString(),
            '--input', libDir.absolutePath,
            '--main-jar', "${project.name}-${version}.jar",
            '--main-class', 'br.com.validadorlote.App',
            '--add-modules', jpackageModules,
            '--dest', layout.buildDirectory.dir('jpackage').get().asFile.absolutePath,
            '--vendor', 'vBaggio',
            '--description', 'Validador de lote de NF-e/NFC-e para a Reforma Tributaria (RTC) - ferramenta independente',
    ]
}

tasks.register('jpackageImage', Exec) {
    group = 'distribution'
    description = 'Gera app-image com runtime embarcado (qualquer SO).'
    dependsOn 'installDist'
    doFirst { delete layout.buildDirectory.dir('jpackage') }
    commandLine jpackageArgs('app-image')
}

tasks.register('jpackageInstaller', Exec) {
    group = 'distribution'
    description = 'Gera o instalador nativo do SO corrente (msi/deb/dmg).'
    dependsOn 'installDist'
    doFirst { delete layout.buildDirectory.dir('jpackage') }
    def os = org.gradle.internal.os.OperatingSystem.current()
    def args = jpackageArgs(os.isWindows() ? 'msi' : os.isMacOsX() ? 'dmg' : 'deb')
    if (os.isWindows()) {
        args += ['--win-menu', '--win-shortcut', '--win-dir-chooser', '--win-per-user-install']
    }
    commandLine args
}
```

- [ ] **Step 2: Validar app-image local (Fedora)**

Run: `./gradlew jpackageImage --console=plain && ls build/jpackage/ValidadorLoteRTC/bin/`
Expected: `BUILD SUCCESSFUL`; diretório com o executável `ValidadorLoteRTC`.
Sanidade do runtime enxuto: `build/jpackage/ValidadorLoteRTC/lib/runtime/bin/java -version 2>&1 | head -1` mostra Java 21. Sessão gráfica disponível → executar `build/jpackage/ValidadorLoteRTC/bin/ValidadorLoteRTC` e repetir o fluxo drop→resultados (isto valida que o jlink enxuto não podou módulo necessário — se quebrar com `NoClassDefFoundError`/`ClassNotFoundException`, adicione o módulo faltante em `jpackageModules` e registre em `docs/decisions.md`).

- [ ] **Step 3: Commit**

```bash
git add build.gradle
git commit -m "build(b5): tasks jpackage com runtime jlink enxuto (msi/deb/dmg)"
```

### Task 26: Workflow de release (matrix com Windows-gate)

**Files:**
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Produces: em push de tag `v*`: job Windows (gate) gera `.msi` e cria a Release; Linux/macOS anexam `.deb`/`.dmg` se passarem (`continue-on-error`).

- [ ] **Step 1: Criar .github/workflows/release.yml**

```yaml
name: Release

on:
  push:
    tags: [ 'v*' ]

permissions:
  contents: write

jobs:
  windows:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Instalar WiX Toolset
        run: choco install wixtoolset -y --no-progress
      - name: Testes e instalador
        shell: bash
        run: ./gradlew test jpackageInstaller --console=plain
      - name: Anexar MSI à Release
        uses: softprops/action-gh-release@v2
        with:
          files: build/jpackage/*.msi

  linux:
    runs-on: ubuntu-latest
    needs: windows
    continue-on-error: true
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Instalador deb
        run: ./gradlew jpackageInstaller --console=plain
      - name: Anexar DEB à Release
        uses: softprops/action-gh-release@v2
        with:
          files: build/jpackage/*.deb

  macos:
    runs-on: macos-latest
    needs: windows
    continue-on-error: true
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Instalador dmg
        run: ./gradlew jpackageInstaller --console=plain
      - name: Anexar DMG à Release
        uses: softprops/action-gh-release@v2
        with:
          files: build/jpackage/*.dmg
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "build(b5): workflow de release com matrix e Windows como gate"
```

### Task 27: README definitivo

**Files:**
- Modify: `README.md` (substituição completa)

- [ ] **Step 1: Reescrever README.md**

```markdown
# Validador de Lote RTC

**Valide centenas de XMLs de NF-e/NFC-e contra as regras da Reforma Tributária — antes
da SEFAZ rejeitar.** Ferramenta desktop, gratuita, de código aberto e **100% offline**.

> ⚖️ **Ferramenta independente.** Este projeto NÃO tem vínculo com a Receita Federal,
> SEFAZ ou qualquer órgão público. Ele aplica os schemas oficiais publicados pela RFB
> e reporta o que eles dizem — a decisão fiscal é sempre sua e do seu contador.

## O problema

Desde **03/08/2026**, NF-e/NFC-e de emitentes em Regime Normal (CRT=3) com grupos de
IBS/CBS fora da NT 2025.002 são **rejeitadas**. As ferramentas oficiais validam um
documento por vez — e param no primeiro erro.

## O que esta ferramenta faz

- Recebe uma **pasta** com XMLs (arraste e solte) e valida **todos** os documentos
- Coleta **todos os erros** de cada arquivo (não só o primeiro)
- **Agrupa por causa-raiz**: "38 documentos com alíquota pCBS em formato inválido" em
  vez de 38 linhas soltas
- Traduz o erro técnico para português claro, com ação sugerida
- A exportação CSV existe no núcleo, mas está temporariamente indisponível na interface; não
  prometê-la como fluxo do usuário até uma task explícita de reativação (D-045)
- Trata XML **pré-emissão** (sem assinatura) sem afogar você em falsos erros

## O que ela NÃO faz

- Não emite, não assina, não transmite documentos
- Não dá orientação tributária — reporta o que os schemas oficiais dizem
- Não corrige seus XMLs automaticamente
- **Não envia nada para a internet**: sem cadastro, sem telemetria, seus XMLs nunca
  saem do seu computador (verificável por captura de tráfego)

## Instalação (Windows)

1. Baixe o instalador `.msi` na [página de Releases](../../releases/latest)
2. Execute o instalador
3. **Aviso do Windows SmartScreen**: como o instalador ainda não tem assinatura digital
   paga, o Windows pode exibir "O Windows protegeu o computador". Clique em
   **Mais informações** → **Executar assim mesmo**. O código é aberto e auditável
   neste repositório.
4. Abra o **Validador de Lote RTC** pelo menu Iniciar

Linux (`.deb`) e macOS (`.dmg`) são publicados como best-effort na mesma página.

## Como usar

1. Arraste uma pasta ou XMLs individuais para a janela do validador
2. Revise a grade de documentos e clique em **Validar pendentes**
3. Selecione um documento para ler seus problemas; remova os válidos para focar no que exige ação
4. Corrija no emissor a partir das mensagens apresentadas

## Base de schemas

A validação usa os **schemas XSD oficiais** extraídos do pacote da Calculadora de
Tributos da RFB. A versão da base embarcada aparece no título da janela e no CSV.
Quando a RFB atualizar a base, uma nova versão desta ferramenta é publicada
(`./gradlew updateSchemas` re-extrai os schemas do pacote oficial).

## Roadmap (v1)

- Conferência de **valores** item a item via motor de cálculo oficial (`regime-geral`)
- Relatório narrativo por IA — opcional, com a **sua** chave de API, enviando apenas o
  resumo agregado (nunca os XMLs)

## Para desenvolvedores

```bash
./gradlew test            # suíte de testes
./gradlew run             # roda a UI
./gradlew slowTest        # smoke de performance (500 XMLs)
./gradlew jpackageImage   # app-image com runtime embarcado
```

Arquitetura, convenções e decisões: [`docs/`](./docs/). Contrato real da Calculadora
RFB (descoberto em execução): [`docs/calculadora/`](./docs/calculadora/).

## Licença

[GPL-3.0](./LICENSE) — livre para usar, estudar, modificar e redistribuir; derivados
devem permanecer abertos.
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs(b5): README definitivo com instalação, SmartScreen e privacidade"
```

### Task 28: Fechamento do Bloco 5 (PR + merge)

- [ ] **Step 1: Push e PR**

```bash
git push -u origin bloco/5-release
gh pr create --title "B5: empacotamento jpackage, workflow de release e README" --body "$(cat <<'EOF'
Bloco 5: tasks jpackage (jlink enxuto, msi/deb/dmg), workflow de release com
Windows como gate (Linux/macOS best-effort) e README definitivo.

Checklist pós-merge (Vinícius): screenshot da UI para o README; smoke do .msi
em Windows real/VM quando disponível.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
gh pr checks --watch
```

- [ ] **Step 2: GATE DE REVIEW DO BLOCO** — review do orquestrador antes do merge.

- [ ] **Step 3: Merge**

```bash
gh pr merge --merge --delete-branch
git checkout main && git pull
```

### Task 29: Release v0.1.0 ⚠️ GATE HUMANO

**Files:** nenhum.

- [ ] **Step 1: CONFIRMAR COM O VINÍCIUS** — a tag publica uma release pública com instaladores. Não prossiga sem confirmação explícita.

- [ ] **Step 2: Tag e push**

```bash
git tag v0.1.0 && git push origin v0.1.0
gh run watch --exit-status
```
Expected: job `windows` verde (gate); `linux`/`macos` anexam se passarem.

- [ ] **Step 3: Verificar release**

Run: `gh release view v0.1.0 --json assets -q '.assets[].name'`
Expected: ao menos `ValidadorLoteRTC-0.1.0.msi`.

- [ ] **Step 4: Smoke final** — baixar o `.msi` numa máquina/VM Windows sem Java, instalar, abrir, validar uma pasta (CA-1). Se indisponível agora, marcar a release como pre-release (`gh release edit v0.1.0 --prerelease`) até o smoke acontecer.

---

## Notas de execução

- **Orquestração**: Fable orquestra; cada task é despachada a um subagente (implementação: modelo Opus) com o contrato de `.claude/agents/validador-senior-dev.md`. Review em dois níveis: por task (spec-compliance + qualidade) e por bloco (PR, gate do orquestrador + Vinícius nas decisões-chave).
- **Fixtures**: a NT 2025.002 v1.50 (PDF em `tmp/`, não versionado) só pode ser lida por subagente dedicado — NUNCA no contexto do orquestrador. Para o v0, os XSDs são o artefato normativo suficiente.
- **Desvios**: qualquer desvio do plano (módulo jlink extra, contingência do resolver, campo de schema inesperado na fixture) é registrado no relatório da task e, se for decisão, em `docs/decisions.md` no mesmo PR.
- **v1 (fora deste plano)**: motor `regime-geral` como processo filho (D-012 pendente), conferência de valores, relatório IA BYOK (D-014). Novo ciclo spec→plano quando o v0 fechar.
