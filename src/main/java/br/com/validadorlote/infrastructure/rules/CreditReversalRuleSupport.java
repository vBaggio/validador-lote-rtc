package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;

import java.util.Set;

/** Leitura conservadora da exceção {@code tpNFDebito=07} compartilhada pelas RVs de estorno. */
final class CreditReversalRuleSupport {

    enum StockLoss { YES, NO, UNKNOWN }

    private static final Set<String> DEBIT_TYPES =
            Set.of("01", "02", "03", "04", "05", "06", "07", "08");

    private CreditReversalRuleSupport() {}

    static StockLoss stockLoss(FiscalDocument document) {
        String debitType = document.tpNFDebito();
        if ("07".equals(debitType)) {
            return StockLoss.YES;
        }
        if (debitType != null) {
            return DEBIT_TYPES.contains(debitType) ? StockLoss.NO : StockLoss.UNKNOWN;
        }
        String purpose = document.finNFe();
        if (purpose == null || "6".equals(purpose)) {
            return StockLoss.UNKNOWN;
        }
        return Set.of("1", "2", "3", "4", "5").contains(purpose)
                ? StockLoss.NO : StockLoss.UNKNOWN;
    }
}
