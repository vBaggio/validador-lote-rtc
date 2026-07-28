package br.com.validadorlote.domain;

/**
 * Por que um item não pôde ser julgado. Chave estável de agrupamento para a camada
 * {@link FindingKind#NOT_EVALUATED}, equivalente ao que o código de rejeição é para a camada de
 * rejeições: sem ela o relatório só saberia agregar por texto livre, e "não avaliei 380 itens"
 * jamais viraria "300 por CST fora da base, 80 por classificação".
 */
public enum NotEvaluatedCause {

    /** O item não informou CST no grupo IBS/CBS. */
    CST_NOT_INFORMED,

    /** CST informado, ausente da base embarcada para a data do fato gerador. */
    CST_NOT_IN_TABLE,

    /** cClassTrib não informada, ou ausente da base embarcada para a data do fato gerador. */
    CLASS_TRIB_UNAVAILABLE,

    /**
     * Motivo próprio da regra que declinou — modelo fora de NF-e/NFC-e, CRT ilegível, data de
     * emissão ausente, aritmética não coberta. Agregue junto com {@link Finding#ruleId()}: quem
     * emite um achado desta causa sempre identifica a regra que desistiu.
     */
    RULE_SPECIFIC
}
