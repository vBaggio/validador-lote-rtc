package br.com.validadorlote.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainImmutabilityTest {

    private Finding finding() {
        return new Finding(Path.of("a.xml"), null, null, FindingKind.SCHEMA, Severity.REJECTION,
                "pCBS", "cvc-pattern-valid", "msg", null, 1, 1, null, null);
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
    void batchReportIsolatesRootCausesFromCallerMutation() {
        var mutable = new ArrayList<>(List.of(cause(List.of(finding()))));
        var report = new BatchReport(Instant.now(), Duration.ZERO, 1, 1, 0, false, mutable, "v");

        mutable.clear();

        assertThat(report.rootCauses()).hasSize(1);
    }
}
