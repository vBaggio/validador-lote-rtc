package br.com.validadorlote.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainImmutabilityTest {

    private FiscalDocument document(List<ReferencedNote> references) {
        return new FiscalDocument(Path.of("a.xml"), null, null, null, null, null, "NFe", "3",
                "4", null, false, null, false, references);
    }

    private Finding finding() {
        return new Finding(Path.of("a.xml"), null, null, FindingKind.SCHEMA, Severity.REJECTION,
                "pCBS", "cvc-pattern-valid", "msg", null, 1, 1, null, null, null);
    }

    private RootCause cause(List<Finding> findings) {
        return new RootCause(new RootCauseKey(FindingKind.SCHEMA, "cvc-pattern-valid", "pCBS"),
                "explicação", null, findings, 1);
    }

    @Test
    void rootCauseIsolatesFindingsFromCallerMutation() {
        var mutable = new ArrayList<>(List.of(finding()));
        var rootCause = cause(mutable);

        mutable.clear();

        assertThat(rootCause.findings()).hasSize(1);
    }

    @Test
    void fiscalDocumentIsolatesReferencesFromCallerMutation() {
        var mutable = new ArrayList<>(List.of(new ReferencedNote("refNFe", YearMonth.of(2025, 12))));
        var doc = document(mutable);

        mutable.clear();

        assertThat(doc.references()).hasSize(1);
    }

    @Test
    void fiscalDocumentTreatsNullReferencesAsEmpty() {
        // As regras iteram sobre references() sem guard de null.
        assertThat(document(null).references()).isEmpty();
    }

    @Test
    void fiscalDocumentReferencesCannotBeMutatedByCaller() {
        assertThatThrownBy(() -> document(List.of()).references()
                .add(new ReferencedNote("refNFe", null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void batchReportIsolatesRootCausesFromCallerMutation() {
        var mutable = new ArrayList<>(List.of(cause(List.of(finding()))));
        var report = new BatchReport(Instant.now(), Duration.ZERO, 1, 1, 0, false, mutable, "v");

        mutable.clear();

        assertThat(report.rootCauses()).hasSize(1);
    }
}
