package br.com.validadorlote.domain;

import java.time.YearMonth;

/**
 * Uma nota referenciada no grupo {@code NFref} do documento, com a competência de emissão
 * disponível no XML e a precisão que esse dado permite.
 *
 * @param form forma da referência, como o XML a declarou: {@code refNFe}, {@code refNFeSig},
 *             {@code refNF}, {@code refNFP}, {@code refCTe} ou {@code refECF}.
 * @param issuedAt competência de emissão da nota referenciada, ou {@code null} quando a forma
 *             usada não permite datá-la offline. Vem do {@code AAMM} da chave de acesso ou do
 *             campo {@code AAMM} próprio; neste último caso, 20xx é só a representação
 *             normalizada e {@code centuryAmbiguous} impede tratá-la como século declarado.
 * @param centuryAmbiguous {@code true} quando o XML informa apenas {@code AAMM}, fora de uma
 *             chave de acesso, e portanto não declara o século.
 */
public record ReferencedNote(String form, YearMonth issuedAt, boolean centuryAmbiguous) {

    /** Referências eletrônicas e formas sem data não têm a ambiguidade própria do AAMM em papel. */
    public ReferencedNote(String form, YearMonth issuedAt) {
        this(form, issuedAt, false);
    }
}
