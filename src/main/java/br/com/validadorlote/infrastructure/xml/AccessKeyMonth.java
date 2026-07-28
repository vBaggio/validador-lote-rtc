package br.com.validadorlote.infrastructure.xml;

import java.time.YearMonth;

/**
 * Decodifica a competência de emissão ({@code AAMM}) codificada numa chave de acesso de 44
 * dígitos, ou num campo {@code AAMM} avulso de referência em papel.
 *
 * <p>Compartilhado entre {@link XmlMetadataParser} ({@code NFref}, no nível do documento) e
 * {@link TaxGroupExtractor} ({@code DFeReferenciado}, no nível do item — D-038): as duas formas
 * de chave usam o mesmo deslocamento e o mesmo {@code AAMM}, e duplicar a leitura em dois lugares
 * arriscaria os dois divergirem numa correção futura.
 */
final class AccessKeyMonth {

    private static final int ACCESS_KEY_LENGTH = 44;
    /** Posições do {@code AAMM} na chave de acesso, conforme a documentação do XSD (linha 322). */
    private static final int KEY_AAMM_START = 2;
    private static final int KEY_AAMM_END = 6;
    private static final int AAMM_LENGTH = 4;
    /**
     * Século usado para normalizar o {@code AAMM}. Para chaves eletrônicas ele é parte da
     * política do formato; no {@code AAMM} avulso de papel a ambiguidade é responsabilidade de
     * quem chama (ver {@code ReferencedNote.centuryAmbiguous}), não desta normalização técnica.
     */
    private static final int NORMALIZED_CENTURY = 2000;

    private AccessKeyMonth() {
    }

    /**
     * Competência extraída das posições 2-5 de uma chave de acesso de 44 dígitos. Chave fora do
     * formato devolve {@code null} — referência que não sabemos datar, não uma data inventada.
     */
    static YearMonth ofAccessKey(String key) {
        if (key == null || key.length() != ACCESS_KEY_LENGTH || !isDigits(key)) return null;
        return ofAamm(key.substring(KEY_AAMM_START, KEY_AAMM_END));
    }

    /** Competência a partir de um {@code AAMM} avulso (campo próprio de {@code refNF}/{@code refNFP}). */
    static YearMonth ofAamm(String aamm) {
        if (aamm == null || aamm.length() != AAMM_LENGTH || !isDigits(aamm)) return null;
        int month = Integer.parseInt(aamm.substring(2));
        if (month < 1 || month > 12) return null;
        return YearMonth.of(NORMALIZED_CENTURY + Integer.parseInt(aamm.substring(0, 2)), month);
    }

    private static boolean isDigits(String value) {
        return value.chars().allMatch(Character::isDigit);
    }
}
