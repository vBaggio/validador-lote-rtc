package br.com.validadorlote.infrastructure.rules;

/**
 * Desfecho de uma verificação. São quatro e não dois de propósito: aprovar o que não foi
 * verificado, ou rejeitar por falta de dado nosso, destrói a confiança no relatório.
 */
public sealed interface RuleOutcome {

    /** Verificado e correto. */
    record Conforme() implements RuleOutcome {}

    /** A regra não vale para este documento (regime ou vigência). Não é aprovação. */
    record NaoAplicavel(String motivo) implements RuleOutcome {}

    /** Faltou dado para julgar — tipicamente base embarcada mais antiga que o documento. */
    record NaoAvaliado(String motivo) implements RuleOutcome {}

    /** A SEFAZ rejeitaria. A mensagem oficial vem da NT e não é reescrita. */
    record Rejeitado(String rejectionCode, String ruleId, String officialMessage,
            String friendlyMessage) implements RuleOutcome {

        Rejeitado(String rejectionCode, String ruleId, String officialMessage) {
            this(rejectionCode, ruleId, officialMessage, null);
        }
    }
}
