package br.com.validadorlote.presentation;

import br.com.validadorlote.domain.FiscalDocument;

import java.math.BigInteger;
import java.util.Comparator;

/** Ordem única usada tanto pela grade quanto pela validação incremental. */
public final class WorkspaceDocumentOrder {

    public static final Comparator<WorkspaceDocument> DISPLAY =
            Comparator.comparing((WorkspaceDocument report) -> emitterSortKey(report.document()),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(report -> sortText(report.document().model()),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(report -> numericKey(report.document().series()),
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(report -> numericKey(report.document().documentNumber()),
                            Comparator.nullsLast(Comparator.naturalOrder()));

    private WorkspaceDocumentOrder() { }

    private static String emitterSortKey(FiscalDocument document) {
        String name = document.emitterName();
        return sortText(name == null || name.isBlank() ? document.emitterCnpj() : name);
    }

    private static String sortText(String value) {
        return value == null || value.isBlank() ? "\uffff" : value;
    }

    private static BigInteger numericKey(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigInteger(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
