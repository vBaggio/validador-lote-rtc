# Auditoria normativa das rejeições do Bloco 6

## Conclusão

As onze identidades de rejeição, seus gatilhos implementados, campos XML, indicadores e mensagens
foram confrontados individualmente com a NT 2025.002 v1.50 local. A auditoria encontrou e corrigiu
uma divergência na Exceção 1 da 1115: o `AAMM` avulso de `refNF/refNFP` não declara o século e não
pode ser tratado como se viesse de uma chave eletrônica.

O resultado não significa cobertura integral das onze células:

- **7 regras estão conformes no recorte implementado:** 1021, 1022, 1024, 1025, 1033, 1074 e
  1079;
- **4 regras são `PARCIAL — NOT_EVALUATED`:** 1115, porque a Exceção 2 depende da tabela de
  combustíveis monofásicos não embarcada; e 1034, 1046 e 1063, porque compra governamental e o ramo
  `ind_gRed = 0` permanecem deliberadamente sem julgamento (D-030).

`NOT_EVALUATED` não é cobertura nem conformidade: é a declaração explícita de que o motor não
emite veredito naquele ramo.

## Fontes e método

Auditoria feita sobre o código em `e16382ace18c7644f5f8fa832db7e5bee1a2be3b`, sem consulta à web.
As fontes normativas locais, ambas symlinks ignorados pelo Git, foram:

| Artefato | SHA-256 |
|---|---|
| `tmp/NT_2025.002_v1.50_RTC_NF-e_IBS_CBS_IS.md` | `1a4f47fe7fcee0cf9afbf51629df9818b1fe44cc2e51cde554462f476abf963f` |
| `tmp/NT_2025.002_v1.50_RTC_NF-e_IBS_CBS_IS.pdf` | `cfc11a45b6ce9b491c2abf11d01865084b7a9dbcb2904e5414ae16e8e31099e3` |

Procedimento repetível:

1. extrair as onze células do Markdown com `rg -n -A18 -B3`;
2. conferir as colunas misturadas com `pdftotext -layout` no PDF;
3. comparar código, ID UB, modelo, gatilho, exceções, mensagem e dados consumidos;
4. seguir cada dado até `XmlMetadataParser`, `TaxGroupExtractor`, a destilação em `build.gradle` e
   `FiscalTables`;
5. conferir pares de testes positivos/negativos e os ramos conservadores;
6. para a divergência encontrada, confirmar RED com XML real e aplicar a correção mínima;
7. executar a suíte focada indicada no brief.

Referência rápida das células:

| Regras | Markdown | Página impressa do PDF |
|---|---:|---:|
| UB12-10, UB13-20 e UB13-30 | 2373–2407 | 42 |
| UB14-20 e UB14-25 | 2451–2462 | 43 |
| UB26-20 e UB27-10 | 2631–2652 | 46 |
| UB45-20 | 2733–2740 | 47 |
| UB46-10 | 2750–2764 | 48 |
| UB64-20 e UB65-10 | 2945–2966 | 51 |

Todas as células trazem modelos `55/65`. A camada foi desenhada somente para NF-e/NFC-e, e o XSD
embarcado restringe `TMod` a 55 ou 65. A 1025 é a única regra cuja decisão varia entre os dois
modelos; as outras dez têm a mesma lógica para ambos. A futura integração deve preservar esse
limite de entrada, pois as classes, isoladamente, não repetem um guard de modelo em cada regra.

## Identidade e mensagem oficial

O texto abaixo é o texto-base da coluna “Descrição Erro”, preservado literalmente. O sufixo
`[nItem: 999]` não é concatenado à mensagem: o número real segue separado em
`Finding.itemNumber`, por `RuleEngine.java:198-205`.

| Código/ID | Texto-base conferido na NT e no código |
|---|---|
| 1115 / UB12-10 | `Rejeição: IBS/CBS não informado` |
| 1021 / UB13-20 | `Rejeição: Grupo IBS/CBS informado indevidamente` |
| 1022 / UB13-30 | `Rejeição: Grupo IBS/CBS não informado` |
| 1024 / UB14-20 | `Rejeição: Rejeição: Classificação Tributária do IBS e da CBS incompatível com o CST informado` |
| 1025 / UB14-25 | `Rejeição: cClassTrib do IBS/CBS não permitido neste modelo de DFe` |
| 1033 / UB26-20 | `Rejeição: Não informado o grupo de redução de alíquota Estadual` |
| 1074 / UB45-20 | `Rejeição: Não informado o grupo de redução de alíquota Municipal` |
| 1079 / UB64-20 | `Rejeição: Não informado o grupo de redução de alíquota da CBS` |
| 1034 / UB27-10 | `Rejeição: Percentual de redução de alíquota da UF não é válido para este cClassTrib` |
| 1046 / UB46-10 | `Rejeição: Percentual de redução de alíquota do Município não é válido para este cClassTrib` |
| 1063 / UB65-10 | `Rejeição: Percentual de redução de alíquota da CBS não é válido para este cClassTrib` |

O “Rejeição: Rejeição:” da 1024 parece erro editorial, mas está assim também no PDF em layout,
página 43. `ClassTribCstRule` o mantém em `officialMessage`; a explicação local vai separada em
`friendlyMessage`.

## Matriz regra a regra

| Código/ID | Modelos | Gatilho NT | Exceções/observações | Dados usados no código | Tabela/indicador | Cobertura | Evidência | Resultado |
|---|---|---|---|---|---|---|---|---|
| **1115 / UB12-10** | 55/65 | Ausência de `det/imposto/IBSCBS`; em produção, CRT 3 desde 03/08/2026 e CRT 1/2/4 desde 04/01/2027. | Ex. 1: `finNFe=2/4` com referência anterior a 2026. D-028 adota leitura conservadora para qualquer referência datável; referência existente sem data ou com século de papel ambíguo diante do corte vira `NOT_EVALUATED`. Ex. 2: `cProdANP` presente **e** produto na tabela monofásica. A data de homologação de 01/07/2026 não é usada pelo validador de produção. | `FiscalDocument.crt`, `issueDate`, `finNFe`, `references`; `ReferencedNote.centuryAmbiguous`; `ItemTaxGroup.hasIbsCbsGroup`, `cProdANP`. O extrator distingue AAMM de chave e AAMM avulso de papel, além de invólucro e grupo interno. | Sem indicador CST. Ex. 2 requer tabela de combustíveis que não está embarcada. | Gatilho, bordas das duas vigências e Ex. 1 têm pares de teste. XMLs reais com `refNF/refNFP` e `AAMM=9912` provam que o século incerto resulta em `NaoAvaliado`; AAMM de papel inequivocamente anterior ao corte e chaves eletrônicas preservam o comportamento. Ex. 2 **não é julgada**. | NT MD 2373–2391/PDF 42; `GroupRequiredRule`; `DocumentRulesTest`; `XmlMetadataParser`; `XmlMetadataParserTest`; `TaxGroupExtractorTest`. | **PARCIAL — NOT_EVALUATED** (Ex. 2) |
| **1021 / UB13-20** | 55/65 | CST com `ind_gIBSCBS=0` e grupo interno `imposto/IBSCBS/gIBSCBS` informado. | O invólucro `IBSCBS` não é o grupo observado. Sem invólucro, a causa-raiz é 1115. | `hasIbsCbsGroup`, `hasGIbsCbsGroup`, `cst`, data de emissão. | `CstEntry.exigeGrupo`, destilado de `IndExigeTrib` = `ind_gIBSCBS`. | CST 400 com e sem `gIBSCBS`, CST permissivo, CST desconhecido e data ausente; extração distingue `IBSCBS`, `gIBSCBS` e `gIBSCBSMono`. | NT MD 2396–2401/PDF 42; `GroupForbiddenRule.java:16-51`; `DocumentRulesTest.java:230-283`; `TaxGroupExtractor.java:31-40,118-128`; `TaxGroupExtractorTest.java:176-231`. | **CONFORME** |
| **1022 / UB13-30** | 55/65 | CST com `ind_gIBSCBS=1` e `gIBSCBS` ausente. | Não se aplica a `tpNFDebito=07` (Perda em estoque). Sem o invólucro, a causa-raiz é 1115. | `hasIbsCbsGroup`, `hasGIbsCbsGroup`, `cst`, `FiscalDocument.tpNFDebito`, data. | `CstEntry.exigeGrupo` / `IndExigeTrib`. | Grupo ausente/presente, CST proibitivo, exceção 07, invólucro ausente e data ausente. O parser testa a extração de `tpNFDebito`. | NT MD 2402–2407/PDF 42; `GroupRequiredByCstRule.java:14-52`; `DocumentRulesTest.java:288-332`; `XmlMetadataParser.java:162-179,215-229`; `XmlMetadataParserTest.java:227-242`. | **CONFORME** |
| **1024 / UB14-20** | 55/65 | `cClassTrib` informada e incompatível com o CST informado. | Classificação indisponível ou CST ausente não vira acusação. CST presente, mas fora da tabela de CST, não suprime a 1024: a regra o compara diretamente ao CST preservado na `cClassTrib`. O detalhe local não altera a mensagem oficial duplicada da NT. | `ItemTaxGroup.cClassTrib`, `cst`, data. | `FiscalTables.classTrib` devolve `ClassTribEntry.cst`, preservado do aninhamento da fonte; comparação direta com o CST do item. | Par compatível/incompatível, tag ausente, CST ausente, classificação desconhecida e data ausente; motor testa separação entre texto oficial e detalhe. | NT MD 2451–2456/PDF 43; `ClassTribCstRule.java:12-49`; `FiscalTables.java:79-97`; `TableRulesTest.java:93-140`; `RuleEngineTest.java:223-231`. | **CONFORME** |
| **1025 / UB14-25** | 55/65 | `cClassTrib` informada com indicador que veda o modelo: `indNFe=0` para 55 ou `indNFCe=0` para 65. | Modelo ausente/desconhecido e classificação fora da base ficam sem julgamento. | `FiscalDocument.model`, `ItemTaxGroup.cClassTrib`, data. | `ClassTribEntry.nfe/nfce`, destilados de `IndNfe/IndNfce`; `permiteModelo("65")` usa NFC-e e `"55"` usa NF-e. | Mesmo `cClassTrib` testado como permitido em 55 e vedado em 65; modelo nulo/desconhecido, classificação ausente/desconhecida e data ausente. | NT MD 2457–2462/PDF 43; `ClassTribModelRule.java:12-55`; `ClassTribEntry.java:7-18`; `TableRulesTest.java:145-204`; `build.gradle:198-207`. | **CONFORME** |
| **1033 / UB26-20** | 55/65 | `ind_gRed=1` **ou** `gCompraGov` informado, sem `gIBSUF/gRed`. | Não se aplica quando `ind_gIBSCBS=0`; sem `gIBSCBS`, 1022 é a causa-raiz. `gCompraGov` é grupo de documento em `ide`. | `cst`, `hasReducaoUf`, `hasGIbsCbsGroup`, `FiscalDocument.hasCompraGov`, data. | `CstEntry.exigeReducao` = `IndReducaoAliq` / `ind_gRed`; `exigeGrupo` = `IndExigeTrib` / `ind_gIBSCBS`. | CST que exige/não exige redução, grupo presente/ausente, gatilho governamental e exceção literal. O teste da exceção isola o ramo e afirma o motivo. | NT MD 2631–2638/PDF 46; `ReductionGroupRule.java:32-97`; `Esfera.java:19-25`; `TableRulesTest.java:213-305`; `XmlMetadataParserTest.java:245-263`. | **CONFORME** |
| **1074 / UB45-20** | 55/65 | Mesmo gatilho da 1033, sem `gIBSMun/gRed`. | Mesma exceção `ind_gIBSCBS=0` e mesma supressão por 1022. | Os mesmos dados da 1033, escolhendo `hasReducaoMun`. | Os mesmos dois indicadores CST. | A lógica compartilhada é exercitada em detalhe na esfera UF; o teste de parametrização afirma código/ID municipal e o extrator testa isolamento de esfera. | NT MD 2733–2740/PDF 47; `ReductionGroupRule.java:32-97`; `Esfera.java:19-25`; `TableRulesTest.java:224-233`; `TaxGroupExtractorTest.java:89-118`. | **CONFORME** |
| **1079 / UB64-20** | 55/65 | Mesmo gatilho da 1033, sem `gCBS/gRed`. | Mesma exceção `ind_gIBSCBS=0` e mesma supressão por 1022. | Os mesmos dados da 1033, escolhendo `hasReducaoCbs`. | Os mesmos dois indicadores CST. | A lógica compartilhada é exercitada em detalhe na esfera UF; o teste de parametrização afirma código/ID CBS e o extrator testa isolamento de esfera. | NT MD 2945–2952/PDF 51; `ReductionGroupRule.java:32-97`; `Esfera.java:19-25`; `TableRulesTest.java:224-233`; `TaxGroupExtractorTest.java:89-118`. | **CONFORME** |
| **1034 / UB27-10** | 55/65 | `gIBSUF/gRed` informado. Com `ind_gRed=1`, `pRedAliq` deve ser o percentual IBS da `cClassTrib`; com `ind_gRed=0`, aplica-se o teste governamental da célula. | Em qualquer compra governamental e no ramo `ind_gRed=0`, retorna `NaoAvaliado` (D-030). | `hasReducaoUf`, `percReducaoUf`, `cst`, `cClassTrib`, `hasCompraGov`, data. | `CstEntry.exigeReducao`; `ClassTribEntry.percRedIbs`. Comparação por `BigDecimal.compareTo`, sem confundir escala. | Ramo `ind_gRed=1` tem par igual/divergente e teste de escala. Compra governamental e `ind_gRed=0` têm testes que confirmam **ausência de veredito**, não cobertura normativa. | NT MD 2639–2652/PDF 46; `ReductionPercentageRule.java:27-115`; `Esfera.java:28-44`; `TableRulesTest.java:314-421`. | **PARCIAL — NOT_EVALUATED** (`gCompraGov` e `ind_gRed=0`) |
| **1046 / UB46-10** | 55/65 | `gIBSMun/gRed` informado; com `ind_gRed=1`, compara com o mesmo percentual IBS da UF. | Mesma limitação deliberada da 1034. A célula local cita `gIBSUF/gRed/pRedAliq` no segundo ramo, embora o cabeçalho seja municipal; o PDF confirma que não é mistura de colunas. O ramo não é interpretado pelo código atual. | `hasReducaoMun`, `percReducaoMun` e os demais dados compartilhados. | `CstEntry.exigeReducao`; **o mesmo** `ClassTribEntry.percRedIbs` usado pela UF. | Parametrização afirma código/ID municipal; o caso 200025 prova UF e município em 60% enquanto CBS usa 100%. Os ramos adiados continuam sem veredito. | NT MD 2750–2764/PDF 48; `ReductionPercentageRule.java:27-115`; `Esfera.java:28-44`; `TableRulesTest.java:332-359`. | **PARCIAL — NOT_EVALUATED** (`gCompraGov` e `ind_gRed=0`) |
| **1063 / UB65-10** | 55/65 | `gCBS/gRed` informado; com `ind_gRed=1`, compara com o percentual CBS da `cClassTrib`. | Mesma limitação deliberada. A célula também cita `gIBSUF/gRed/pRedAliq` no segundo ramo; o PDF confirma o texto. O ramo não é interpretado pelo código atual. | `hasReducaoCbs`, `percReducaoCbs` e os demais dados compartilhados. | `CstEntry.exigeReducao`; `ClassTribEntry.percRedCbs`, distinto do percentual IBS. | Parametrização afirma código/ID CBS; 200025 prova que CBS 100% não é comparada com IBS 60%. Os ramos adiados continuam sem veredito. | NT MD 2953–2966/PDF 51; `ReductionPercentageRule.java:27-115`; `Esfera.java:28-44`; `TableRulesTest.java:332-359`. | **PARCIAL — NOT_EVALUATED** (`gCompraGov` e `ind_gRed=0`) |

## Rastreabilidade da tabela e dos campos XML

A NT nomeia os indicadores, mas seus valores vêm do artefato oficial embarcado. A cadeia conferida
foi:

| Fonte da SVRS | Destilado | Modelo carregado | Consumidor |
|---|---|---|---|
| `IndExigeTrib` | `exigeGrupo` | `CstEntry.exigeGrupo` | 1021, 1022 e exceção de 1033/1074/1079 |
| `IndReducaoAliq` | `exigeReducao` | `CstEntry.exigeReducao` | 1033/1074/1079 e 1034/1046/1063 |
| `IndNfe`, `IndNfce` | `nfe`, `nfce` | `ClassTribEntry.nfe/nfce` | 1025 |
| aninhamento `ClassificacoesTributarias` sob o CST | `ClassTribEntry.cst` ao carregar | `ClassTribEntry.cst` | 1024 |
| `PercRedIbs`, `PercRedCbs` | `percRedIbs`, `percRedCbs` | `BigDecimal` | 1034/1046 e 1063 |

Evidência: `build.gradle:140-207`, `FiscalTables.java:62-99,167-173`,
`CstEntry.java:6-11` e `ClassTribEntry.java:7-18`. O manifesto declara fonte SVRS, extração em
27/07/2026, Informe Técnico 2025.002 v1.60, 18 CSTs e 164 classificações. A consulta local
confirmou 11 CSTs com `exigeGrupo=true` e apenas 3 com `exigeReducao=true` (`011`, `200`, `515`).
Esta auditoria não refez o download nem comparou o JSON bruto com o portal, pois a fonte de
julgamento solicitada foi local e sem web; ela verificou a cadeia de mapeamento e o artefato
embarcado.

Nos XMLs, `TaxGroupExtractor.java:82-150` separa invólucro, grupo interno, CST, classificação,
`cProdANP`, presença de `gRed` e `pRedAliq` por esfera. `XmlMetadataParser` extrai modelo, data,
CRT, finalidade, tipo de nota de débito, `gCompraGov` em `ide` e todas as referências. Para estas,
preserva a distinção entre AAMM dentro de chave eletrônica e AAMM avulso de documento em papel.
Ambos desabilitam DTD e entidades externas.

## Divergência corrigida

`refNF` e `refNFP` são documentos em papel e seus elementos `AAMM` (XSD linhas 341 e 393)
informam somente ano e mês, sem século. Antes da correção, o parser aplicava a eles a mesma
normalização 20xx das chaves eletrônicas. Assim, `9912` virava dezembro de 2099 e a 1115 podia
rejeitar uma devolução cuja referência também poderia ser de dezembro de 1999.

O domínio agora conserva explicitamente a ambiguidade de século do AAMM avulso. A regra compara
as leituras 19xx e 20xx apenas em relação ao corte oficial de janeiro de 2026:

- se ambas são anteriores ao corte, a Exceção 1 continua aplicável;
- se ficam em lados opostos, o resultado é `NOT_EVALUATED`, nunca rejeição;
- chaves eletrônicas mantêm a política de data anterior.

O ciclo RED–GREEN cobre XML real com `AAMM=9912` nas duas formas e protege também o AAMM de papel
anterior ao corte e a chave eletrônica.

## Limitações e leituras deliberadas

1. **1115, Exceção 2:** a tabela de combustíveis monofásicos não está embarcada. Quando o item sem
   `IBSCBS` chega a essa guarda — após CRT/data tornarem a 1115 exigível e a Exceção 1 não resolver
   o caso — a presença de `cProdANP` leva a `NOT_EVALUATED`, inclusive quando o código talvez não
   pertença à tabela. É falso negativo declarado, não conformidade.
2. **1115, Exceção 1:** D-028 escolhe a leitura que evita acusação: qualquer referência datável
   anterior a 2026 afasta a regra, inclusive `refNF/refNFP`; uma referência não datável leva a
   `NOT_EVALUATED`. Como o AAMM avulso de papel não informa século, qualquer ambiguidade que possa
   mudar a aplicação do corte também leva a `NOT_EVALUATED`.
3. **1034/1046/1063:** compra governamental e o ramo `ind_gRed=0` não são julgados. A presença de
   `gCompraGov` é extraída, mas `gCompraGov/pRedutor` não; D-030 adia essa aritmética para a camada
   de valores.
4. **Texto ambíguo da própria NT:** UB46-10 e UB65-10 referenciam `gIBSUF/gRed/pRedAliq` no ramo
   específico de município/CBS. O PDF em layout confirma o texto do Markdown. Como esse ramo está
   deliberadamente sem julgamento, a implementação atual não inventa uma correção editorial.
5. **Limite de modelos:** o resultado vale para o escopo declarado 55/65. A integração futura não
   deve oferecer estas regras a outros DFe; o único guard interno explícito de modelo é o da 1025.

## Verificação

Comando focado:

```bash
./gradlew test --tests 'br.com.validadorlote.infrastructure.rules.*' \
  --tests 'br.com.validadorlote.infrastructure.xml.TaxGroupExtractorTest' \
  --tests 'br.com.validadorlote.infrastructure.xml.XmlMetadataParserTest' \
  --console=plain
```

Resultado final: `BUILD SUCCESSFUL`, 138 testes focados e 223 testes na suíte completa, todos com
0 falhas, 0 erros e 0 ignorados. A divergência da 1115 passou por RED de compilação, RED
comportamental com quatro falhas e GREEN focado antes das suítes finais. Uma sonda que neutralizou
temporariamente a decisão de século fez os dois testes 9912 da regra falharem; o bloco foi
restaurado antes das execuções finais.
