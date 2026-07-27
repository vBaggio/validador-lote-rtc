package br.com.validadorlote.domain;

import java.time.YearMonth;

/**
 * Uma nota referenciada no grupo {@code NFref} do documento, com a competência de emissão dela
 * quando ela é determinável sem consulta externa.
 *
 * @param form forma da referência, como o XML a declarou: {@code refNFe}, {@code refNFeSig},
 *             {@code refNF}, {@code refNFP}, {@code refCTe} ou {@code refECF}.
 * @param issuedAt competência de emissão da nota referenciada, ou {@code null} quando a forma
 *             usada não permite datá-la offline. Nunca deduzida: vem do {@code AAMM} da chave de
 *             acesso referenciada ou do campo {@code AAMM} próprio da referência.
 */
public record ReferencedNote(String form, YearMonth issuedAt) {}
