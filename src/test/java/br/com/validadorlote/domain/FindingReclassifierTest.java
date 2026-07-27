package br.com.validadorlote.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FindingReclassifierTest {

    private Finding finding(FindingKind kind, Severity severity) {
        return new Finding(Path.of("a.xml"), null, null, kind, severity,
                "Signature", "cvc-complex-type.2.4.b", "msg", null, 10, 5, null, null, null);
    }

    @Test
    void preEmissionOnTurnsSignatureMissingIntoInfo() {
        var result = FindingReclassifier.reclassify(
                List.of(finding(FindingKind.SIGNATURE_MISSING, Severity.REJECTION)), true);
        assertThat(result).singleElement()
                .extracting(Finding::severity).isEqualTo(Severity.INFO);
    }

    @Test
    void preEmissionOffTurnsSignatureMissingIntoRejection() {
        var result = FindingReclassifier.reclassify(
                List.of(finding(FindingKind.SIGNATURE_MISSING, Severity.INFO)), false);
        assertThat(result).singleElement()
                .extracting(Finding::severity).isEqualTo(Severity.REJECTION);
    }

    @Test
    void otherKindsAreUntouched() {
        var schema = finding(FindingKind.SCHEMA, Severity.REJECTION);
        var unreadable = finding(FindingKind.UNREADABLE, Severity.WARNING);
        var result = FindingReclassifier.reclassify(List.of(schema, unreadable), true);
        assertThat(result).containsExactly(schema, unreadable);
    }
}
