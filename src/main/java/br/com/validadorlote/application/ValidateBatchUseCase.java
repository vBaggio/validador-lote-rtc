package br.com.validadorlote.application;

import br.com.validadorlote.domain.BatchReport;
import br.com.validadorlote.domain.DocumentReport;
import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.FindingReclassifier;
import br.com.validadorlote.domain.RootCauseGrouper;
import br.com.validadorlote.domain.RootCauseTexts;
import br.com.validadorlote.domain.Severity;
import br.com.validadorlote.infrastructure.csv.CsvExporter;
import br.com.validadorlote.infrastructure.fs.FolderScanner;
import br.com.validadorlote.infrastructure.rules.RuleEngine;
import br.com.validadorlote.infrastructure.xml.ParsedMetadata;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;
import br.com.validadorlote.infrastructure.xml.UnreadableXmlException;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orquestra o lote: varre, valida em paralelo (schema + regras de rejeição da NT) e agrupa.
 *
 * <p>Por arquivo, três fontes de achado, nesta ordem: {@link XmlMetadataParser} (metadados; falha
 * vira achado {@code UNREADABLE} e nenhuma das outras duas roda — sem documento não há o que
 * avaliar), {@link SchemaValidatorEngine} (schema/assinatura) e {@link TaxGroupExtractor} +
 * {@link RuleEngine} (rejeição prevista/não-avaliado). As duas últimas rodam sempre que o parse
 * teve sucesso, mesmo quando o schema já encontrou erro: nenhuma delas inventa dado a partir de um
 * documento incompleto — ver o adendo da Task 19 em {@code .superpowers/sdd/}.
 */
public final class ValidateBatchUseCase {

    private static final LocalDate NORMAL_REGIME_VALIDATION_START = LocalDate.of(2026, 8, 3);
    private static final LocalDate SIMPLES_VALIDATION_START = LocalDate.of(2027, 1, 4);

    private final FolderScanner scanner;
    private final XmlMetadataParser parser;
    private final TaxGroupExtractor taxGroupExtractor;
    private final SchemaValidatorEngine schemaEngine;
    private final RuleEngine ruleEngine;
    private final RootCauseGrouper grouper;
    private final RootCauseTexts texts;
    private final CsvExporter csvExporter;
    private final String schemasVersion;

    public ValidateBatchUseCase(FolderScanner scanner, XmlMetadataParser parser,
            TaxGroupExtractor taxGroupExtractor, SchemaValidatorEngine schemaEngine,
            RuleEngine ruleEngine, RootCauseGrouper grouper, RootCauseTexts texts,
            CsvExporter csvExporter, String schemasVersion) {
        this.scanner = scanner;
        this.parser = parser;
        this.taxGroupExtractor = taxGroupExtractor;
        this.schemaEngine = schemaEngine;
        this.ruleEngine = ruleEngine;
        this.grouper = grouper;
        this.texts = texts;
        this.csvExporter = csvExporter;
        this.schemasVersion = schemasVersion;
    }

    public BatchReport execute(BatchRequest request, ProgressListener listener, CancellationToken token) {
        Instant start = Instant.now();
        List<Path> files = scanner.scan(request.folder());
        List<DocumentValidation> validations = new ArrayList<>();
        AtomicInteger processed = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(
                Math.max(1, Runtime.getRuntime().availableProcessors()));
        try {
            CompletionService<DocumentValidation> completion = new ExecutorCompletionService<>(pool);
            Map<Future<DocumentValidation>, Path> fileOf = new HashMap<>();
            int submitted = 0;
            for (Path file : files) {
                if (token.isCancelled()) break;
                fileOf.put(completion.submit(() -> validateOne(file, token,
                        request.considerRulesEffectiveDate())), file);
                submitted++;
            }
            for (int i = 0; i < submitted; i++) {
                try {
                    Future<DocumentValidation> future = completion.take();
                    try {
                        validations.add(future.get());
                    } catch (java.util.concurrent.ExecutionException e) {
                        // validateOne nunca lança RuntimeException; cinto de segurança contra
                        // Error (ex.: StackOverflowError) para nunca abortar o lote. fileOf
                        // identifica o arquivo certo mesmo fora de ordem de submissão —
                        // CompletionService entrega por ordem de conclusão, não de submissão.
                        validations.add(invalid(fileOf.get(future), unexpected(fileOf.get(future),
                                e.getCause())));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                listener.onProgress(processed.incrementAndGet(), files.size());
            }
        } finally {
            pool.shutdownNow();
        }

        return buildReport(start, files.size(), validations, request.preEmissionMode(),
                token.isCancelled());
    }

    /** Importa XMLs para a grade sem executar schema nem regras fiscais. */
    public ImportedBatch importDocuments(Path input) {
        return importDocuments(input, true);
    }

    /** Importa XMLs, incluindo subpastas somente quando solicitado pela interface. */
    public ImportedBatch importDocuments(Path input, boolean includeSubfolders) {
        List<FiscalDocument> documents = new ArrayList<>();
        List<Path> invalidFiles = new ArrayList<>();
        for (Path file : scanner.scan(input, includeSubfolders)) {
            try {
                documents.add(parser.parse(file).document());
            } catch (RuntimeException e) {
                invalidFiles.add(file);
            }
        }
        return new ImportedBatch(documents, invalidFiles);
    }

    /** Valida um único XML, para que a interface publique o resultado incrementalmente. */
    public DocumentValidationResult validateDocument(Path file, boolean preEmissionMode,
            CancellationToken token) {
        return validateDocument(file, preEmissionMode, true, token);
    }

    /**
     * Valida um XML preservando sua data original, com opção de aplicar a referência mínima da
     * vigência RTC às regras fiscais.
     */
    public DocumentValidationResult validateDocument(Path file, boolean preEmissionMode,
            boolean considerRulesEffectiveDate, CancellationToken token) {
        DocumentValidation validation = validateOne(file, token, considerRulesEffectiveDate);
        if (isInvalid(validation)) {
            return new DocumentValidationResult(null, validation.findings());
        }
        return new DocumentValidationResult(validation.document(),
                FindingReclassifier.reclassify(validation.findings(), preEmissionMode));
    }

    /** Monta o relatório exportável a partir dos documentos já processados na sessão. */
    public BatchReport reportOf(List<DocumentReport> documents, int scanned, boolean cancelled) {
        List<Finding> findings = documents.stream().flatMap(document -> document.findings().stream())
                .toList();
        int withFindings = (int) findings.stream().map(Finding::source).distinct().count();
        int unreadable = (int) findings.stream().filter(f -> f.kind() == FindingKind.UNREADABLE)
                .map(Finding::source).distinct().count();
        return new BatchReport(Instant.now(), Duration.ZERO, scanned, withFindings, unreadable,
                cancelled, grouper.group(findings, texts), schemasVersion, documents, List.of());
    }

    /** Reaplica o modo pré-emissão sobre um relatório existente, sem revalidar arquivos. */
    public BatchReport regroup(BatchReport previous, boolean preEmissionMode) {
        List<Finding> all = previous.rootCauses().stream()
                .flatMap(c -> c.findings().stream()).toList();
        List<Finding> reclassified = FindingReclassifier.reclassify(all, preEmissionMode);
        Map<Path, List<Finding>> bySource = reclassified.stream().collect(java.util.stream.Collectors
                .groupingBy(Finding::source));
        List<DocumentReport> documents = previous.documents().stream()
                .map(document -> new DocumentReport(document.document(),
                        bySource.getOrDefault(document.document().source(), List.of())))
                .toList();
        return new BatchReport(previous.startedAt(), previous.elapsed(), previous.documentsScanned(),
                previous.documentsWithFindings(), previous.documentsUnreadable(), previous.cancelled(),
                grouper.group(reclassified, texts), previous.schemasVersion(), documents,
                previous.invalidFiles());
    }

    public List<Path> exportCsv(BatchReport report, Path targetFolder) throws IOException {
        return csvExporter.export(report, targetFolder);
    }

    /**
     * As três fontes de achado de um arquivo. Cada uma tem seu próprio {@code try/catch}: uma
     * falha inesperada numa não descarta o que a outra já apurou.
     */
    private DocumentValidation validateOne(Path file, CancellationToken token,
            boolean considerRulesEffectiveDate) {
        if (token.isCancelled()) return new DocumentValidation(null, List.of());
        ParsedMetadata meta;
        try {
            meta = parser.parse(file);
        } catch (UnreadableXmlException e) {
            return invalid(file, unreadable(file, e.getMessage()));
        } catch (RuntimeException e) {
            return invalid(file, unexpected(file, e));
        }

        List<Finding> findings = new ArrayList<>();
        try {
            findings.addAll(schemaEngine.validate(file, meta));
        } catch (RuntimeException e) {
            // SchemaValidatorEngine documenta que nunca lança; cinto de segurança mesmo assim.
            findings.add(unexpected(file, e));
        }
        try {
            List<ItemTaxGroup> items = taxGroupExtractor.extract(file);
            LocalDate operationDate = operationDate(meta.document(), considerRulesEffectiveDate);
            findings.addAll(ruleEngine.evaluate(meta.document(), items, operationDate).findings());
        } catch (UnreadableXmlException e) {
            findings.add(unreadable(file,
                    "Falha ao ler grupos IBS/CBS para avaliação de regras: " + e.getMessage()));
        } catch (RuntimeException e) {
            findings.add(unexpected(file, e));
        }
        return new DocumentValidation(meta.document(), findings);
    }

    private static LocalDate operationDate(FiscalDocument document,
            boolean considerRulesEffectiveDate) {
        LocalDate issueDate = document.issueDate();
        if (!considerRulesEffectiveDate || issueDate == null) {
            return issueDate;
        }
        LocalDate effectiveDate = switch (document.crt() == null ? "" : document.crt().trim()) {
            case "1", "2", "4" -> SIMPLES_VALIDATION_START;
            default -> NORMAL_REGIME_VALIDATION_START;
        };
        return issueDate.isBefore(effectiveDate) ? effectiveDate : issueDate;
    }

    private Finding unreadable(Path file, String message) {
        return new Finding(file, null, null, FindingKind.UNREADABLE, Severity.WARNING,
                null, null, message, null, null, null, null, null, null);
    }

    private Finding unexpected(Path file, Throwable e) {
        String message = "Falha inesperada ao processar: "
                + (e == null ? "erro desconhecido" : e.getMessage());
        return new Finding(file, null, null, FindingKind.UNREADABLE, Severity.WARNING,
                null, null, message, null, null, null, null, null, null);
    }

    private BatchReport buildReport(Instant start, int scanned, List<DocumentValidation> validations,
            boolean preEmissionMode, boolean cancelled) {
        List<DocumentReport> documents = validations.stream()
                .filter(validation -> validation.document() != null && !isInvalid(validation))
                .sorted(Comparator.comparing(validation -> validation.document().source()))
                .map(validation -> new DocumentReport(validation.document(),
                        FindingReclassifier.reclassify(validation.findings(), preEmissionMode)))
                .toList();
        List<Path> invalidFiles = validations.stream()
                .filter(this::isInvalid)
                .flatMap(validation -> validation.findings().stream())
                .map(Finding::source)
                .distinct()
                .toList();
        List<Finding> findings = documents.stream().flatMap(document -> document.findings().stream())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        validations.stream().filter(validation -> validation.document() == null)
                .flatMap(validation -> validation.findings().stream()).forEach(findings::add);
        int withFindings = (int) findings.stream().map(Finding::source).distinct().count();
        int unreadable = (int) findings.stream()
                .filter(f -> f.kind() == FindingKind.UNREADABLE)
                .map(Finding::source).distinct().count();
        return new BatchReport(start, Duration.between(start, Instant.now()), scanned,
                withFindings, unreadable, cancelled, grouper.group(findings, texts), schemasVersion,
                documents, invalidFiles);
    }

    private DocumentValidation invalid(Path file, Finding finding) {
        return new DocumentValidation(null, List.of(finding));
    }

    private boolean isInvalid(DocumentValidation validation) {
        return validation.document() == null || validation.findings().stream()
                .anyMatch(finding -> finding.kind() == FindingKind.UNREADABLE);
    }

    private record DocumentValidation(FiscalDocument document, List<Finding> findings) {}
}
