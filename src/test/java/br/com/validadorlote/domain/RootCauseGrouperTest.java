package br.com.validadorlote.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RootCauseGrouperTest {

    private static final RootCauseTexts NO_TEXTS = new RootCauseTexts() {
        public Optional<String> explanation(RootCauseKey key) { return Optional.empty(); }
        public Optional<String> action(RootCauseKey key) { return Optional.empty(); }
    };

    private Finding schemaFinding(String file, String field, String code, String message) {
        return new Finding(Path.of(file), null, 1, FindingKind.SCHEMA, Severity.REJECTION,
                field, code, message, null, 10, 1, null, null, null);
    }

    @Test
    void groupsSameKeyAcrossFilesCountingDistinctDocuments() {
        var f1 = schemaFinding("a.xml", "pCBS", "cvc-pattern-valid", "msg oficial");
        var f2 = schemaFinding("b.xml", "pCBS", "cvc-pattern-valid", "msg oficial");
        var f3 = schemaFinding("b.xml", "pCBS", "cvc-pattern-valid", "msg oficial");

        var causes = new RootCauseGrouper().group(List.of(f1, f2, f3), NO_TEXTS);

        assertThat(causes).hasSize(1);
        assertThat(causes.getFirst().affectedDocuments()).isEqualTo(2);
        assertThat(causes.getFirst().findings()).hasSize(3);
        assertThat(causes.getFirst().key())
                .isEqualTo(new RootCauseKey(FindingKind.SCHEMA, "cvc-pattern-valid", "pCBS"));
    }

    @Test
    void ordersByAffectedDocumentsDescThenOccurrencesDesc() {
        var one = schemaFinding("a.xml", "CST", "cvc-enumeration-valid", "m1");
        var many1 = schemaFinding("a.xml", "pCBS", "cvc-pattern-valid", "m2");
        var many2 = schemaFinding("b.xml", "pCBS", "cvc-pattern-valid", "m2");

        var causes = new RootCauseGrouper().group(List.of(one, many1, many2), NO_TEXTS);

        assertThat(causes).extracting(c -> c.key().field()).containsExactly("pCBS", "CST");
    }

    @Test
    void breaksTieOnEqualAffectedDocumentsByOccurrencesDesc() {
        var few1 = schemaFinding("a.xml", "CST", "cvc-enumeration-valid", "m1");
        var few2 = schemaFinding("b.xml", "CST", "cvc-enumeration-valid", "m1");
        var many1 = schemaFinding("a.xml", "pCBS", "cvc-pattern-valid", "m2");
        var many2 = schemaFinding("a.xml", "pCBS", "cvc-pattern-valid", "m2");
        var many3 = schemaFinding("b.xml", "pCBS", "cvc-pattern-valid", "m2");

        var causes = new RootCauseGrouper()
                .group(List.of(few1, few2, many1, many2, many3), NO_TEXTS);

        assertThat(causes).extracting(c -> c.affectedDocuments()).containsExactly(2, 2);
        assertThat(causes).extracting(c -> c.key().field()).containsExactly("pCBS", "CST");
    }

    @Test
    void fallsBackToOfficialMessageWhenNoTranslation() {
        var f = schemaFinding("a.xml", "vIBS", "cvc-complex-type.2.4.a", "mensagem xerces");
        var causes = new RootCauseGrouper().group(List.of(f), NO_TEXTS);
        assertThat(causes.getFirst().friendlyExplanation()).isEqualTo("mensagem xerces");
        assertThat(causes.getFirst().suggestedAction()).isNull();
    }

    @Test
    void explanationIsEmptyWhenNeitherTranslationNorOfficialMessageExists() {
        var f = new Finding(Path.of("a.xml"), null, null, FindingKind.UNREADABLE,
                Severity.WARNING, null, null, null, null, null, null, null, null, null);

        var causes = new RootCauseGrouper().group(List.of(f), NO_TEXTS);

        assertThat(causes.getFirst().friendlyExplanation()).isEmpty();
        assertThat(causes.getFirst().suggestedAction()).isNull();
    }

    @Test
    void usesTranslationTextsWhenAvailable() {
        var texts = new RootCauseTexts() {
            public Optional<String> explanation(RootCauseKey key) { return Optional.of("explicação pt-BR"); }
            public Optional<String> action(RootCauseKey key) { return Optional.of("ação pt-BR"); }
        };
        var causes = new RootCauseGrouper()
                .group(List.of(schemaFinding("a.xml", "pCBS", "cvc-pattern-valid", "m")), texts);
        assertThat(causes.getFirst().friendlyExplanation()).isEqualTo("explicação pt-BR");
        assertThat(causes.getFirst().suggestedAction()).isEqualTo("ação pt-BR");
    }

    @Test
    void unreadableFindingsGroupTogetherWithNullCodeAndField() {
        var u1 = new Finding(Path.of("x.xml"), null, null, FindingKind.UNREADABLE,
                Severity.WARNING, null, null, "ilegível", null, null, null, null, null, null);
        var u2 = new Finding(Path.of("y.xml"), null, null, FindingKind.UNREADABLE,
                Severity.WARNING, null, null, "ilegível", null, null, null, null, null, null);
        var causes = new RootCauseGrouper().group(List.of(u1, u2), NO_TEXTS);
        assertThat(causes).hasSize(1);
        assertThat(causes.getFirst().affectedDocuments()).isEqualTo(2);
    }
}
