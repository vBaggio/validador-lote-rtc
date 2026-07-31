package br.com.validadorlote.application;

import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.RootCauseGrouper;
import br.com.validadorlote.domain.Severity;
import br.com.validadorlote.infrastructure.csv.CsvExporter;
import br.com.validadorlote.infrastructure.fs.FolderScanner;
import br.com.validadorlote.infrastructure.fs.ScanException;
import br.com.validadorlote.infrastructure.rules.RuleEngine;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.SchemaValidatorEngine;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor;
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
                new TaxGroupExtractor(), new SchemaValidatorEngine(translator),
                new RuleEngine(FiscalTables.load()), new RootCauseGrouper(), translator,
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
        assertThat(report.documents()).hasSize(2);
        assertThat(report.invalidFiles()).containsExactly(dir.resolve("lixo.xml"));
        assertThat(report.documents()).anySatisfy(document -> {
            assertThat(document.document().source()).isEqualTo(dir.resolve("ok.xml"));
            assertThat(document.document().accessKey()).isNotBlank();
            assertThat(document.document().emitterName()).isNotBlank();
            assertThat(document.document().series()).isNotBlank();
        });
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

    @Test
    void rejectionRuleFindingsAreWiredIntoTheReport(@TempDir Path dir) throws IOException {
        copyFixture(dir, "rejeicao/r1115-sem-grupo.xml", "r1115.xml");

        var report = useCase.execute(new BatchRequest(dir, true), (p, t) -> {}, new CancellationToken());

        assertThat(report.rootCauses()).anySatisfy(c -> {
            assertThat(c.key().kind()).isEqualTo(FindingKind.REJECTION_RULE);
            assertThat(c.key().rejectionCode()).isEqualTo("1115");
        });
        assertThat(report.documentsWithFindings()).isEqualTo(1);
    }

    @Test
    void simulationCanEvaluateAJulySampleWithTheAugustRules(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-crt3-sem-ibscbs.xml", "amostra-julho.xml");
        Path sample = dir.resolve("amostra-julho.xml");

        var byEmissionDate = useCase.validateDocument(sample, true, false, new CancellationToken());
        var simulatedAugust = useCase.validateDocument(sample, true, true, new CancellationToken());

        assertThat(byEmissionDate.findings()).noneMatch(
                finding -> "1115".equals(finding.rejectionCode()));
        assertThat(simulatedAugust.findings()).anySatisfy(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.REJECTION_RULE);
            assertThat(finding.rejectionCode()).isEqualTo("1115");
        });
        assertThat(simulatedAugust.document().issueDate())
                .isEqualTo(java.time.LocalDate.of(2026, 7, 26));
    }

    @Test
    void simulationUsesTheSimplesNacionalEffectiveDate(@TempDir Path dir) throws IOException {
        copyFixture(dir, "rejeicao/c1115-simples-sem-grupo.xml", "simples-2026.xml");
        Path sample = dir.resolve("simples-2026.xml");

        var byEmissionDate = useCase.validateDocument(sample, true, false, new CancellationToken());
        var simulatedValidity = useCase.validateDocument(sample, true, true, new CancellationToken());

        assertThat(byEmissionDate.findings()).noneMatch(
                finding -> "1115".equals(finding.rejectionCode()));
        assertThat(simulatedValidity.findings()).anySatisfy(
                finding -> assertThat(finding.rejectionCode()).isEqualTo("1115"));
    }

    @Test
    void ruleEngineNeverFlagsTheCanonicalDocument(@TempDir Path dir) throws IOException {
        copyFixture(dir, "nfe-valida.xml", "ok.xml");

        var report = useCase.execute(new BatchRequest(dir, true), (p, t) -> {}, new CancellationToken());

        assertThat(report.rootCauses()).isEmpty();
        assertThat(report.documentsWithFindings()).isZero();
    }
}
