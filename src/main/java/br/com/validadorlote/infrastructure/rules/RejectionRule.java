package br.com.validadorlote.infrastructure.rules;

/** Uma regra da NT que prevê rejeição. Implementações são sem estado e reutilizáveis. */
public interface RejectionRule {

    /** Código oficial da rejeição, ex.: "1115". */
    String rejectionCode();

    /** Identificador da regra na NT, ex.: "UB12-10". */
    String ruleId();

    RuleOutcome evaluate(RuleContext ctx);
}
