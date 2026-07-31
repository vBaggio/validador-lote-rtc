package br.com.validadorlote.presentation;

import br.com.validadorlote.domain.FiscalDocument;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceDocumentOrderTest {

    @Test
    void ordersDocumentsLikeTheyAppearInTheGrid() {
        WorkspaceDocument beta = pending("beta.xml", "Empresa Beta", "20");
        WorkspaceDocument alphaTen = pending("alpha-10.xml", "Empresa Alpha", "10");
        WorkspaceDocument alphaTwo = pending("alpha-2.xml", "Empresa Alpha", "2");

        List<WorkspaceDocument> ordered = List.of(beta, alphaTen, alphaTwo).stream()
                .sorted(WorkspaceDocumentOrder.DISPLAY)
                .toList();

        assertThat(ordered).extracting(item -> item.document().source().getFileName().toString())
                .containsExactly("alpha-2.xml", "alpha-10.xml", "beta.xml");
    }

    private static WorkspaceDocument pending(String file, String emitter, String number) {
        FiscalDocument document = new FiscalDocument(Path.of(file), null, "12345678000190",
                emitter, number, null, "55", "1", "NFe", null, null, null,
                false, null, false, List.of());
        return WorkspaceDocument.pending(document);
    }
}
