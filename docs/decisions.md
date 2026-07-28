# Decisões

Log ADR-lite. Cada entrada: **Decisão**, contexto curto e consequência. Mais recentes no topo.
Template no fim. Decisões D-001..D-014 nasceram no brainstorm de 26/07/2026 (spec
[`superpowers/specs/2026-07-26-validador-lote-rtc-design.md`](./superpowers/specs/2026-07-26-validador-lote-rtc-design.md)).

## D-037 — 1022 mantém causa-raiz única; a multiplicidade da SVRS não é reproduzida (28/07/2026)

O gate humano da Task 10 (`docs/validacao/casos-diferenciais.md`) mediu, contra o validador
oficial da SVRS, que um item sem `gIBSCBS` obrigatório dispara **quatro** códigos simultâneos —
1022, 1033, 1074 e 1079 — enquanto o motor local, por design de causa-raiz única (D-032/D-034),
reporta só o 1022 e suprime os três seguintes.

Não é falso positivo: o controle equivalente (`c1022-com-grupo-interno.xml`, com o grupo presente)
não dispara nenhum dos quatro nos dois lados, e o critério de aceite do bloco — "nenhum documento
aprovado pela SVRS é reprovado por nós" — está preservado. É divergência de **multiplicidade**, não
de veredito.

**Decisão:** manter a causa-raiz única. Reproduzir a multiplicidade da SVRS exigiria reabrir o
motor (Task 8) para emitir 1033/1074/1079 mesmo quando suprimidas pela ausência de `gIBSCBS` —
indo contra a política já registrada em D-032 de que "no máximo um achado por causa-raiz por
item" é o comportamento pretendido, não um efeito colateral a corrigir. A SVRS relatar mais
códigos não torna a política errada: ela relata **sintomas** de uma causa que nós já identificamos
e nomeamos.

**Consequência aceita:** quem comparar o relatório local linha a linha com o retorno da SVRS para
o mesmo documento vê menos códigos do nosso lado — é esperado, não é bug. Registrado aqui para que
uma sessão futura não "corrija" a supressão achando que é uma lacuna de cobertura.

## D-036 — Ledger do SDD versionado; o resto de `.superpowers/` é scratch (27/07/2026)

`.superpowers/` estava inteiramente no `.gitignore`. Não foi escolha do projeto: é a convenção do
superpowers, que trata o diretório como scratch descartável — o próprio skill avisa que
`git clean -fdx` o destrói.

Revisto por conteúdo, o diretório tem 1,3 MB de naturezas diferentes: 588 KB de diffs de revisão
(100% regeneráveis com `git diff base..head`), 224 KB de briefs e 228 KB de relatórios de task
(derivados do plano e efêmeros), e 28 KB de ledger — que é memória de decisão, achado e débito, e
**não existe em nenhum outro lugar**.

**Decisão:** versionar apenas `.superpowers/sdd/progress.md`; manter o restante ignorado. Exigiu
regra no `.gitignore` da raiz e negação no `.gitignore` aninhado que o tooling cria em
`.superpowers/sdd/` (`*` + `!progress.md`), porque o aninhado vence para os arquivos dele.

**Consequência aceita:** o ledger aparece nos diffs de PR. É ruído pequeno em troca de o histórico
de decisão sobreviver a `git clean`, viajar entre máquinas e ficar revisável. Se o tooling
sobrescrever o `.gitignore` aninhado numa atualização do superpowers, a negação precisa ser
recolocada.

## D-035 — `itemNumber` nulável e não único é débito da integração/apresentação, não do motor (27/07/2026)
`TaxGroupExtractor` insere todo item com `nItem` ilegível com `itemNumber = null` (decisão certa:
descartá-lo o faria sumir do relatório). Consequência que só aparece agora: num documento com
**dois** itens sem `nItem`, um deles com achado, os achados não distinguem qual item é qual.
O `RuleEngine` avalia cada item independentemente e não se confunde — o problema é de quem **agrupa
por item** a partir da lista de `Finding`.

O débito pertence ao bloco seguinte, de integração/apresentação: ele deverá decidir se a agregação
assume o pior caso (todos os itens sem `nItem` viram um balde só, rotulado como tal), ou se o
`Finding` passa a carregar o índice posicional do item além do `nItem` declarado. A segunda opção
exige decidir antes se esse índice é informação que o relatório pode exibir sem induzir a erro, já
que o documento não o declarou. A Task 9 permanece restrita a fixtures diferenciais.

## D-034 — O gate de `gCompraGov` **não** entra no motor, e a razão é o risco futuro (27/07/2026)
Documento com `gCompraGov` e `gRed` nas três esferas produz três achados "não avaliado" (1034, 1046
e 1063) pela mesma causa: a aritmética da D-030 não está coberta. É a única duplicata de causa que
sobrou depois da cascata.
Considerei uma quarta precondição no `RuleEngine` para colapsá-la, e **não** a implementei. Registro
a razão verdadeira, porque a primeira que me ocorreu não se sustenta: alegar que compra governamental
é "conhecimento interno das regras" é fraco — `hasCompraGov()` é dado de **documento**, exatamente
como `issueDate`, e uma precondição sobre ele seria estruturalmente idêntica às três existentes.
O que decide é outra coisa: as três precondições atuais são de **disponibilidade de dado** (o código
existe na base?), e a falta delas é permanente enquanto o dado faltar. Um gate de `gCompraGov` seria
de **cobertura de implementação**, e some quando a D-030 entrar. Um gate assim, esquecido no motor
depois que a aritmética for implementada, passaria a suprimir **rejeições reais** em silêncio —
falso negativo invisível, o pior desfecho que este projeto admite. Três achados redundantes hoje
custam menos que esse risco.
Consequência aceita: até a D-030 entrar, nota governamental com redução nas três esferas gera três
"não avaliado" em vez de um. Quando a D-030 for implementada, esta entrada é o lugar de reencontrar
o assunto — e aí a duplicata desaparece sozinha, sem gate nenhum para remover.

## D-033 — `RootCauseKey.from(Finding)` define a chave por camada; mensagem local fica em `friendlyMessage` (27/07/2026)
`RootCauseKey.from(Finding)` centraliza a identidade de agrupamento sem casar texto: schema usa
`kind + xsdCode + field`; rejeição prevista usa `kind + rejectionCode`; não avaliado por
precondição compartilhada usa `kind + notEvaluatedCause`; e não avaliado por motivo específico
acrescenta `ruleId`. Para `NotEvaluatedCause.RULE_SPECIFIC`, somente esse `ruleId` participa da
chave; nas demais causas ele fica nulo para manter no mesmo balde as regras suprimidas pela mesma
precondição. `SIGNATURE_MISSING` e `UNREADABLE` são identificados pelo próprio `kind`.

Na camada de rejeição, `officialMessage` armazena somente o texto vindo da NT. Explicação,
diagnóstico ou detalhe produzido localmente fica em `friendlyMessage`, que é também o primeiro
fallback do agrupador para a explicação. Essa separação preserva a mensagem oficial e impede que
texto local seja apresentado como se viesse do artefato fiscal.

## D-032 — A cascata corta por fato observado, não pelo desfecho da regra-mãe (27/07/2026)
O plano da Task 8 mandava interromper a avaliação do item quando a 1115 devolvesse `Rejeitado` **ou**
`NaoAvaliado`. Está errado: a 1115 também devolve `NaoAvaliado` quando o **CRT do emitente** é
ilegível ou não é um dos previstos na NT — e nesse caso o item pode ter invólucro, CST e
classificação perfeitamente avaliáveis pelas outras dez regras. Cortar ali perderia rejeições reais
por uma causa que nada tem a ver com elas.
Decisão: o corte de nível 1 observa o **fato** `!item.hasIbsCbsGroup()`, que é o que a spec §4.4
escreve ("grupo IBSCBS ausente"), e não o desfecho de ninguém.
No mesmo movimento, acrescentei um corte que a spec **não** lista: `issueDate == null`. Não está na
§4.4, mas o requisito de "no máximo um achado por causa-raiz por item" o exige — sem a data do fato
gerador as onze regras devolvem `NaoAvaliado`, oito delas com a mesma frase, porque toda consulta à
tabela é por vigência.
Consequência: a supressão é declarativa (cada regra diz de que dado depende) e a invariante que a
sustenta — regra suprimida nunca chega a veredito, nem `Rejeitado` nem `Conforme` — tem teste
próprio que percorre os bindings, em vez de depender de leitura à mão.

## D-031 — O motor devolve `RuleEvaluation`, não `List<Finding>` (27/07/2026)
Contar achados não basta para o relatório dizer a verdade. O caso concreto é o da spec §4.5:
documento de CRT=1 antes de 04/01/2027 produz **zero** achados porque a exigência ainda não vigora —
e um relatório que só conta achados diria "tudo certo", que é exatamente a conclusão que faz o
contador ser surpreendido em janeiro.
Decisão: `RuleEngine.evaluate` devolve `RuleEvaluation(findings, itemCount, verifiedItemCount)`,
onde `verifiedItemCount` conta os itens em que ao menos uma regra chegou a veredito (`Conforme` ou
`Rejeitado`). As demais contagens a camada de relatório deriva dos próprios `Finding`, que carregam
`itemNumber` e `kind` — com a ressalva da D-035.

## D-030 — Compra governamental é gatilho, mas sua aritmética fica para depois (27/07/2026)
O grupo `gCompraGov` é de **documento**, não de item: o XSD o declara em `infNFe/ide/gCompraGov`
(`leiauteNFe_v4.00.xsd:499`) e a NT o lista com pai B01 (`ide`). Por isso ele entrou em
`FiscalDocument` (via `XmlMetadataParser`), e não em `ItemTaxGroup`.
Ele muda duas famílias de regra em direções opostas:
1. **Regras de grupo (1033/1074/1079).** O gatilho literal da UB26-20 e irmãs é "se CST possui
   `ind_gRed = 1`, **ou** foi informado o grupo de compras governamentais": sob compra governamental
   o `gRed` é exigido mesmo com `ind_gRed = 0`. Implementado, respeitando a exceção também literal
   de que a regra não se aplica a CST com `ind_gIBSCBS = 0`.
2. **Regras de percentual (1034/1046/1063).** A NT observa que "no caso de Compra Governamental, o
   grupo `gRed` deve ser informado e `pRedAliq` deve ser igual a zero, mesmo que o CST possua
   indicador que veda o preenchimento". Ou seja, o percentual esperado passa a ser **zero**, não o
   da tabela. Sem detectar `gCompraGov`, toda nota governamental legítima com `pRedAliq = 0` seria
   comparada contra os 60% da tabela e acusada — falso positivo em escala.
Decisão: item de documento com `gCompraGov` sai como `NaoAvaliado` nas regras de percentual, com o
motivo dizendo que a aritmética de compra governamental (que envolve `gCompraGov/pRedutor`) não
está coberta. O mesmo vale para o ramo `ind_gRed = 0` da UB27-10, que só é julgável com o
`pRedutor` em mãos.
**Implementação futura:** cobrir a aritmética de compra governamental junto com a camada de
valores da v1 (`pAliqEfet = pAliq × (1 - pRedAliq/100) × (1 - pRedutor/100)`), que é onde o
`pRedutor` passa a ser usado de fato.
Consequência aceita: falso negativo declarado nas notas governamentais, em vez de falso positivo
nelas — a direção que o projeto sempre escolhe.

## D-029 — Exceção 2 da UB12-10 (combustível monofásico) sai como não avaliada (27/07/2026)
A UB12-10 tem uma segunda exceção: a exigência do grupo IBS/CBS não se aplica quando o item
informa `cProdANP` **e** o produto consta da Tabela de Combustíveis Sujeitos à Tributação
Monofásica. A tabela é publicada no Portal Nacional da NF-e (aba "Documentos", opção "Diversos")
e **não está embarcada** — nossa base oficial hoje é só a de CST × cClassTrib da SVRS.
Sem a tabela não dá para saber se o produto está nela. Item com `cProdANP` informado sai como
`NaoAvaliado`, com o motivo dizendo qual tabela falta. Nunca `Rejeitado`: base incompleta é
limitação nossa, não defeito do emitente, e é exatamente para isso que existe o terceiro
desfecho. **Implementação futura:** ingerir a Tabela de Combustíveis pelo mesmo caminho de
`updateFiscalTables` e trocar o `NaoAvaliado` por julgamento real.
Consequência aceita: falso negativo declarado. Um item de combustível que de fato deveria trazer
o grupo e não traz não é acusado — mas aparece no relatório como não avaliado, com a razão, em
vez de sumir em silêncio.

## D-028 — Exceção 1 da UB12-10 decidida offline pelo `AAMM` da chave referenciada (27/07/2026)
A UB12-10 não se aplica a NF-e de devolução (`finNFe=4`) ou complementar (`finNFe=2`) que
referencia NF-e emitida antes de 2026. Sem isso, em agosto de 2026 **toda devolução de mercadoria
vendida em 2025** — operação rotineira — sairia como rejeição 1115: falso positivo em escala na
regra que motiva o bloco inteiro.
A exceção é determinável sem rede: a chave de acesso referenciada carrega o `AAMM` da emissão nas
posições 2-5 (documentado no próprio XSD, `leiauteNFe_v4.00.xsd` linha 322), e `refNF` traz um
campo `AAMM` explícito. `XmlMetadataParser` passa a extrair `finNFe`, `tpNFDebito` e a lista de
`NFref` (até 999 ocorrências, todas relevantes — basta uma anterior a 2026).
Três leituras registradas, todas na direção que não acusa: (1) a oração "que referencia NFe com
data de emissão anterior a 2026" é gramaticalmente ambígua entre qualificar só a complementar ou
as duas finalidades — adotamos as duas, porque a leitura oposta reintroduz o falso positivo;
(2) ao rigor da letra só `refNFe` e `refNFeSig` são "NFe", já que `refNF` e `refNFP` são documentos
em papel — ainda assim **qualquer referência datável** anterior a 2026 aciona a exceção, e os dois
usam o campo `AAMM` próprio que o XSD declara (linhas 341 e 393); (3) `refCTe` e `refECF` ficam
como **referência não datável** e produzem `NaoAvaliado`, não acusação — a chave de CT-e até tem
`AAMM` no mesmo deslocamento, mas é documento de transporte e alargar mais o escopo não compensa
a margem. Documento com `finNFe` 2 ou 4 e **nenhuma** `NFref` segue o curso normal da regra: a
exceção exige uma referência, e o autorizador vai aplicar exatamente esse teste.
Consequência: `FiscalDocument` ganhou `finNFe`, `tpNFDebito` e `references` (cópia imutável e nunca
nula), e o domínio ganhou o record `ReferencedNote`. Chave fora do formato, mês impossível
(`AAMM=2599`) ou referência em papel sem o campo `AAMM` não viram data inventada — viram referência
não datável, que a regra reporta como não avaliada em vez de acusar.

## D-026 — Primeiro corte cobre onze rejeições da NT (28/07/2026)

O primeiro recorte foi inicialmente recomendado com seis códigos. A leitura integral da NT e as
correções aprovadas do Bloco 6 levaram-no a onze: **1115, 1021, 1022, 1024, 1025, 1033, 1074, 1079,
1034, 1046 e 1063**. São os códigos que o motor atual prevê; os demais continuam explicitamente
fora do corte.

**Consequência:** documentação, validação diferencial e apresentação devem declarar esses onze
códigos nominalmente, sem tratar as divergências de percentual como categoria sem código oficial.

## D-025 — Tabela SVRS com ingestão manual, validação integral e manifesto separado (28/07/2026)

A tabela CST × cClassTrib da SVRS é atualizada manualmente e revisada em PR. Antes de qualquer
gravação, a ingestão valida integralmente o artefato que compõe o corte: estrutura, códigos,
indicadores, vigências, percentuais e vínculos entre CST e classificações. Mudança de formato ou
campo ausente encerra a atualização ruidosamente; não recebe valor padrão fiscal.

O manifesto da tabela permanece separado do manifesto de schemas. Unificá-los exigiria migrar o
contrato e os consumidores já existentes de schemas, sem reduzir o risco fiscal imediato desta
camada.

## D-027 — 1021 e 1022 observam o grupo interno `gIBSCBS`, não o invólucro `IBSCBS` (27/07/2026)
`IBSCBS` e `gIBSCBS` são elementos diferentes e confundi-los custou uma rodada de revisão. No tipo
`TTribNFe` do XSD oficial (`DFeTiposBasicos_v1.00.xsd:248`) o invólucro `IBSCBS` é
`sequence[CST, cClassTrib, indDoacao?, choice minOccurs="0"{gIBSCBS | gIBSCBSMono | gTransfCred |
gAjusteCompet}, …]`: ele **carrega o CST**, logo existe sempre que o item declara situação
tributária. O `gIBSCBS` é uma das alternativas opcionais de dentro dele.
A NT é literal sobre qual regra olha qual: a **UB12-10** cita `det/imposto/IBSCBS` (o invólucro) e
a **UB13-20/UB13-30** citam `imposto/IBSCBS/gIBSCBS` (o grupo interno). Cada alternativa do
`choice` tem indicador e par de regras próprios (`ind_gIBSCBSMono` → 1151/1116; `ind_gTransfCred`
→ 1131/1132), o que confirma que "grupo informado" na 1021 é o `gIBSCBS` especificamente, nunca
"alguma alternativa do choice".
Decisão: a 1115 observa o invólucro; a 1021 e a 1022 observam o grupo interno. `ItemTaxGroup` tem
dois campos distintos (`hasIbsCbsGroup` e `hasGIbsCbsGroup`) e o javadoc das três regras diz qual
elemento cada uma observa.
Motivo: a leitura anterior fazia todo item de isenção ou imunidade **corretamente emitido** virar
acusação — 7 dos 18 CSTs (400, 410, 620, 800, 810, 811, 820), justamente os que proíbem o grupo
detalhado. Consequência: item de CST proibitivo sem `gIBSCBS` sai `Conforme` (verificado e
aprovado), e não `NaoAplicavel`.

## D-023 — Locale das mensagens de validação fixado em `Locale.ROOT` (26/07/2026)
As mensagens do Xerces são **localizadas**: o JDK embarca `XMLSchemaMessages_de`, `_ja` e
outras. Como a extração do campo (`field`) é feita por regex sobre esse texto, o motor herdava
o idioma da JVM do usuário e, fora de en/pt, **parava de funcionar inteiro**: medido sob
`de_DE`, o achado de `pCBS` saía com `field=null` e mensagem genérica — ou seja, as chaves
específicas da tabela de tradução (`cvc-pattern-valid.pCBS` e irmãs, D-021) voltavam a morrer e
a coluna "campo" do relatório ficava vazia, **silenciosamente**. Não era defeito de teste: era
defeito de produto na máquina de qualquer usuário com locale não-latino.
O motor passa a fixar a propriedade `http://apache.org/xml/properties/locale` no `Validator`
(e na `SchemaFactory`) com `Locale.ROOT`. Fato não-óbvio, verificado empiricamente:
**`Locale.ENGLISH` não serve** — não existe bundle `_en`, então o `ResourceBundle` cai no
locale padrão da JVM e as mensagens voltam ao alemão. Só `Locale.ROOT`, que resolve direto no
bundle base, entrega inglês de forma estável. Se o ambiente não reconhecer a propriedade, a
falha é engolida e a validação segue (as regex em português permanecem como rede de segurança):
mensagem no idioma da máquina é ruim, abortar a validação por causa disso é pior.
Consequência: o texto **oficial** exibido ao usuário é sempre inglês, mesmo numa máquina pt-BR.
Aceitável porque a mensagem que ele lê primeiro é a amigável, que vem da nossa tabela em pt-BR;
o texto oficial é material de conferência, e em inglês ele é ainda o mesmo que a SEFAZ e os
fóruns citam. Exceção deliberada: os erros de **má-formação** (fatais, vindos do `XMLReader`)
não têm o locale fixado — não passam por extração alguma e não têm tradução na tabela, então o
usuário só ganha em recebê-los no idioma da própria máquina.
`build.gradle` repassa `-Duser.language`/`-Duser.country` para a JVM de teste (que é forkada e
não os herda), de modo que `./gradlew test -Duser.language=de -Duser.country=DE` reproduza o
cenário. Suíte verificada verde sob `de_DE` e `ja_JP`.

## D-022 — Teto de 5.000 achados por documento, com aviso de truncamento (26/07/2026)
Um `enviNFe` de 4 MB com notas repetidamente inválidas produz ~195 mil achados (medido).
Acumular tudo estoura a heap: com `-Xmx64m` o motor morria de `OutOfMemoryError` **dentro** de
`validate()` e — por ser `Error`, não `Exception` — escapava do `catch` por arquivo e derrubava
o lote inteiro, quebrando a garantia "500 arquivos nunca abortam por causa de um". Ao atingir
5.000 achados o motor interrompe a validação **daquele documento** (exceção interna própria,
capturada antes do catch genérico, que preserva os achados já coletados) e anexa um último
achado `SCHEMA`/`WARNING` sem `xsdCode` avisando em pt-BR que a listagem foi truncada.
O número: acima de alguns milhares o relatório já não é acionável — o contador tem de corrigir
a causa sistêmica e revalidar —, e 5.000 achados custam poucos MB. O aviso vai em
`officialMessage` **e** em `friendlyMessage` porque é de `officialMessage` que o
`RootCauseGrouper` tira a explicação quando não há tradução por código; sem isso a causa-raiz
apareceria vazia.

**Correção posterior — o teto de contagem sozinho não bastava.** Ele limita quantos achados são
retidos, não quantos *bytes*: o Xerces bufferiza o valor de um tipo simples **inteiro** antes de
qualquer teto nosso agir, então um único `<cUF>` de 30 MB estourava a heap dentro do próprio
`validate()` e o lote morria do mesmo jeito (reproduzido a `-Xmx48m`, com o arquivo seguinte
nunca processado). Foram somadas três defesas, cada uma cobrindo o que a outra não alcança:
teto de texto acumulado (2 milhões de caracteres) e por mensagem isolada (8.000, com o corte
anunciado no texto); **recusa preventiva de arquivos acima de 32 MB** (uma ordem de grandeza
acima do maior documento fiscal plausível — `enviNFe` de lote fica nos poucos MB), que evita o
estouro em vez de remediá-lo; e **captura de `OutOfMemoryError`** no `validate()`, convertendo em
achado `UNREADABLE`, como rede de segurança para o que escapar das duas primeiras. Só
`OutOfMemoryError` é tratado: os demais `Error` indicam defeito nosso, não do arquivo do usuário.

## D-024 — Nome do campo extraído por padrão ancorado, vencendo a última ocorrência (26/07/2026)
O Xerces interpola o **valor rejeitado antes** de nomear o campo (`The value 'X' of element 'Y'
is not valid`), e o valor é conteúdo que o autor do XML escolhe. Com extração pela primeira
ocorrência, um `<cNF>element 'pCBS'</cNF>` fazia o achado sair rotulado `pCBS` — e, pior que o
rótulo, puxava da tabela a mensagem amigável **e a ação recomendada** de pCBS, instruindo o
contador a corrigir um campo sem defeito. Severidade e classificação de assinatura nunca foram
afetadas (são decididas por código + nome qualificado, não pelo campo). Os padrões passaram a ser
ancorados no texto fixo que cerca o nome em cada formato de mensagem, e vence a **última**
ocorrência: depois do nome verdadeiro só vem texto do Xerces, fora do alcance de quem escreve o
XML. Nos formatos estruturais (`.2.4.a`, `.2.4.b`) o nome vem da árvore do documento, que não
admite aspas nem chaves, então lá a ordem é indiferente — e o campo certo é o elemento
**infrator**, não o esperado.

**Correção (26/07/2026): contagem sozinha não protegia a heap.** O teto limitava *quantos*
achados, não *quanto texto* — e o Xerces cita o valor rejeitado **inteiro** em cada mensagem.
Reproduzido: um XML de 23 MB com 1.200 notas, cada uma com um valor inválido de 20 KB, gera só
**1.200 achados** (muito abaixo de 5.000) e ainda assim ~24 MB de texto retido; a `-Xmx48m` o
motor morria de `OutOfMemoryError` — exatamente o modo de falha que D-022 dizia ter resolvido.
Foram acrescentados dois limites, em paralelo ao de contagem:
- **Orçamento de texto acumulado**, 2.000.000 de caracteres (~2 MB) por documento. Ao estourar,
  o motor para de acumular do mesmo modo que no teto de contagem, com um aviso de truncamento
  próprio ("caracteres acumulados"). Para mensagens de tamanho normal (~200 caracteres) esse
  orçamento daria ~10.000 achados, bem acima do teto de 5.000: só documentos com valores
  anormalmente grandes esbarram nele, e o teto de contagem continua sendo o que age no caso
  patológico comum (medido: 5.000 achados ≈ 910 mil caracteres).
- **Teto de 8.000 caracteres por mensagem isolada.** O maior valor legítimo da NF-e é `infCpl`
  (5.000 caracteres), que cabe com folga. O corte é anunciado **dentro do próprio texto**
  ("mensagem oficial cortada pelo validador em N caracteres"), para não parecer que o Xerces
  escreveu aquilo — a regra de nunca reescrever mensagem oficial vale também para não fingir
  integridade. Código, classificação e campo são extraídos da mensagem **íntegra**; só o texto
  retido é cortado.

Verificado a `-Xmx48m`: o arquivo de 23 MB conclui com 705 achados + aviso de truncamento por
volume, e o **segundo arquivo do lote é processado** — sem os limites, o mesmo cenário morre de
`OutOfMemoryError`, que não é `Exception` e escapa do `catch` por arquivo. Risco aceito: os
tetos são por documento, não por lote — 500 documentos no teto ainda somam muito. Quando existir
o orquestrador de lote, avaliar um teto agregado.

## D-021 — Faceta + portador fundidos num único achado (26/07/2026)
Para um valor inválido o Xerces emite **dois** erros na mesma linha/coluna: o de faceta
(`cvc-pattern-valid`, que descreve a regra violada e tem tradução, mas **não** nomeia o campo)
e o "portador" (`cvc-type.3.1.3` para elemento, `cvc-attribute.3` para atributo, que nomeia o
campo mas **não** tem tradução). O efeito era duplo: o relatório mostrava duas causas-raiz para
um único erro, uma delas com o texto cru do Xerces em inglês; e as chaves específicas
`cvc-pattern-valid.pCBS`, `.pIBSUF`, `.pIBSMun`, `.cClassTrib` eram inalcançáveis, porque o
achado que tinha o código não tinha o campo e vice-versa. O motor passa a fundir o par quando o
portador vem imediatamente depois de uma faceta na mesma posição: fica o `xsdCode` da faceta e
o `field` do portador. As **duas** mensagens oficiais são preservadas na íntegra, concatenadas
com `" | "` — nenhuma é descartada nem reescrita, e o usuário continua podendo conferir o texto
original. Consequência: `<pCBS>9.9.9</pCBS>` sai como 1 achado com a explicação específica de
pCBS, no lugar de 2 achados com a genérica.

## D-020 — `SIGNATURE_MISSING` exige código `.2.4.b` **e** `ds:Signature` entre os esperados (26/07/2026)
`SIGNATURE_MISSING` é a única classificação que o `FindingReclassifier` pode rebaixar a `INFO`
em modo pré-emissão — ou seja, é o único caminho pelo qual um achado **some do relatório**.
Por isso a regra que a atribui tem de ser estreita, e chegou à forma atual em duas etapas.

**Primeira correção — casar o nome qualificado, não a substring.** A regra original
(`cvc-complex-type.2.4*` + mensagem **contendo** `Signature`) classificava por busca em texto
que o **autor do XML controla**: um elemento batizado `SignatureXpto` dentro de `infNFe` gera
uma mensagem que contém "Signature" e saía como `SIGNATURE_MISSING`/`INFO`. Passou-se a exigir
o nome **qualificado** `"http://www.w3.org/2000/09/xmldsig#":Signature` na enumeração de
esperados, **delimitado** (seguido de `}` ou `,`) — o que exclui `SignedInfo`, `SignatureValue`,
`SignatureMethod` e homônimos de outro namespace.

**Segunda correção — prender o código em `.2.4.b`.** A primeira correção manteve o casamento
por **prefixo** `cvc-complex-type.2.4`, e isso ainda incluía o `.2.4.a`. Só que `.a` (e `.d`)
significam *"conteúdo proibido encontrado"* e enumeram os esperados apenas para orientar; a
enumeração naquela posição contém o QName da assinatura. Dois vetores reproduzidos:
- `<lixoEstrutural/>` como **irmão de `infNFe`** (dentro de `NFe`) → `.2.4.a` listando a
  assinatura entre os esperados → classificado `SIGNATURE_MISSING`/`INFO`, e o **erro
  estrutural sumia do relatório** em modo pré-emissão;
- `<Signature/>` com o **namespace errado** (bug trivial de emissor: herda `nfe` em vez de
  `xmldsig#`) → mesma classificação, ou seja, assinatura **presente e defeituosa** reportada
  como ausente e rebaixada.

A regra agora exige o código **exatamente** `cvc-complex-type.2.4.b` — *conteúdo incompleto*, a
única variante que significa "faltou elemento" — **e** o QName delimitado entre os esperados.
Verificado que os casos de ausência genuína produzem `.2.4.b`.

Consequência real, sem promessa maior do que o código entrega: o rebaixamento a `INFO` só
alcança o erro em que o Xerces diz que o conteúdo está incompleto **e** aponta a `ds:Signature`
como o que falta. Elemento estranho em qualquer posição, assinatura em namespace errado e
assinatura presente porém incompleta (`SignedInfo` faltando) permanecem `SCHEMA`/`REJECTION`.
Isto vale para o julgamento **estrutural** que o XSD alcança: o motor não verifica criptografia,
então uma assinatura bem-formada porém inválida (certificado errado, digest que não confere)
passa sem achado — quem julga isso é a SEFAZ, não o schema.

## D-019 — Includes do XSD resolvidos por `LSResourceResolver` do classpath (26/07/2026)
`FEATURE_SECURE_PROCESSING` zera `accessExternalSchema`, então o `xs:include` relativo de
`nota.xsd` (`./originais/leiauteNFe_v4.00.xsd`) falha mesmo apontando para um arquivo local —
confirmado: *"Failed to read schema document 'leiauteNFe_v4.00.xsd', because 'file' access is
not allowed"*. Em vez de reabrir o acesso externo (`ACCESS_EXTERNAL_SCHEMA=file,jar`), o
`SchemaValidatorEngine` registra um `LSResourceResolver` que serve os XSDs do classpath pelo
**nome do arquivo**, procurando em `/schemas/nfe/originais/` e `/schemas/nfe/`. Os 14 XSDs
oficiais têm nomes únicos na base embarcada. Consequência: o motor não faz acesso externo
algum, e a resolução independe do protocolo — funciona igual em `build/resources` (`file:`) e
dentro do JAR distribuído (`jar:`), onde `file` sequer se aplica. Risco aceito: se uma base
futura da RFB trouxer dois XSDs de mesmo nome em pastas diferentes, o primeiro diretório da
lista vence; `updateSchemas` deve ser conferido nesse caso.

## D-018 — Índice linha→item não resolve XML minificado (26/07/2026)
`ItemLineIndex` mapeia achado→item por **linha** (faixa `[linha de abertura, linha de
fechamento]` de cada `det`). Num XML minificado numa única linha — formato que vários ERPs
emitem —, abertura e fechamento de todo `det` colapsam para a mesma linha do documento
inteiro, e qualquer achado (inclusive um no grupo `IBSCBSTot`, que fica em `<total>`, fora de
qualquer `det`) é rotulado como pertencente ao primeiro item. É o mesmo sintoma que a faixa
por linha corrigiu para XML formatado (achado atribuído ao item errado), sobrevivendo aqui
por outra via: já não é campo capturado errado, é granularidade de linha insuficiente.
Resolver exigiria indexar por coluna (offset dentro da linha), não por linha — fora do escopo
do v0. Consequência aceita: relatório de XML minificado com múltiplos itens pode citar o
item 1 para um erro que é de outro item, ou de fora de qualquer item.

## D-017 — DOCTYPE é rejeitado, não apenas ignorado (26/07/2026)
XML de terceiro com declaração `<!DOCTYPE>` vira `UnreadableXmlException` em vez de ser
lido normalmente. Fato técnico não-óbvio que motiva a decisão: `XMLInputFactory.SUPPORT_DTD
= false` **não rejeita** o DOCTYPE — apenas deixa de processar as declarações internas. O
leitor ainda emite o evento `XMLStreamConstants.DTD` e segue lendo o documento até o fim.
Sem uma rejeição explícita no laço de eventos, um arquivo com DOCTYPE era parseado como se
nada houvesse; as duas propriedades de segurança sozinhas não fecham o caso. Por isso
`XmlMetadataParser` lança ao ver o evento `DTD`.
Divergência deliberada em relação à Calculadora oficial da RFB, que aceita DOCTYPE: aqui a
entrada é um lote de arquivos de origem arbitrária, e nenhuma NF-e legítima precisa de DTD.
Consequência aceita: um XML válido que traga DOCTYPE decorativo é reportado como ilegível
em vez de validado.

## D-016 — `enviNFe` multi-nota: metadados nulos no v0 (26/07/2026)
Lote `enviNFe` com mais de um `infNFe` é aceito e validado normalmente contra o schema, mas
o v0 não desmembra o lote em documentos individuais. Como `accessKey`, `emitterCnpj`,
`documentNumber`, `model` e `issueDate` só poderiam vir da **primeira** nota, atribuí-los ao
arquivo inteiro produziria relatório enganoso (chave da nota 1 num achado da nota 3). Os
cinco campos ficam nulos — nulo é melhor que errado. `rootElement` continua preenchido e o
índice linha→item permanece válido (as faixas estão em ordem de documento), perdendo-se
apenas de qual nota o item é. Desmembramento fica para quando houver demanda real.

## D-015 — ArchUnit: `allowEmptyShould(true)` por regra, não global (26/07/2026)
Desde o ArchUnit 1.3.0, regras `noClasses()/classes()...that()` falham por padrão quando
nenhuma classe casa com o `that()` (ex.: pacote `application`/`presentation` ainda
inexistente). Isso contradiz a intenção de D-006 (regras "armadas" para camadas futuras,
passando vazias até existirem). Primeira tentativa desligou o default globalmente via
`archRule.failOnEmptyShould=false` em `src/test/resources/archunit.properties` — descartada
em revisão: uma config global mascara silenciosamente qualquer regra que deixe de casar
classes no futuro (renomeação de pacote, refactor incompleto), inclusive as fundamentais
como `domainDependsOnNothing`. Trocado por `.allowEmptyShould(true)` encadeado apenas nas
duas regras hoje vazias (`applicationDoesNotSeePresentation`,
`presentationDoesNotSeeInfrastructure`), com comentário no código marcando a permissão como
temporária. Risco aceito: essas duas regras não acusam nada até os pacotes existirem — por
isso a permissão fica restrita a elas, e as demais permanecem estritas. Ação futura: remover
as duas chamadas `.allowEmptyShould(true)` quando `application` e `presentation` existirem,
ao final do bloco da interface.

## D-014 — Relatório narrativo por IA opcional (BYOK) na v1 (26/07/2026)
Última entrega da v1: botão pós-análise, API key do próprio usuário, envia só o relatório
agregado (nunca XMLs), narra achados determinísticos sem julgar tributo. Ideia registrada:
onboarding de primeiro boot pode unificar credenciais e downloads iniciais.

## D-013 — Assinatura ausente = achado próprio com toggle (26/07/2026)
`SIGNATURE_MISSING` com toggle "XMLs pré-emissão" (default ligado → INFO; desligado →
REJECTION). Público valida antes de emitir; sem isso, 100% dos docs teriam ruído.

## D-012 — PENDENTE: fonte do motor na v1 (26/07/2026)
Embutir × download no 1º uso. Decidir no início da v1. Fato: pacote oficial **sem licença**
(ver [`calculadora/licenca-calculadora.txt`](./calculadora/licenca-calculadora.txt)).

## D-011 — Fluxo git: branch por bloco + PR (26/07/2026)
1 commit semântico por task; review por bloco; merge commit (preserva histórico de tasks).

## D-010 — Testes leves e dirigidos (26/07/2026)
Sem gate de cobertura; criticidade guia (ver `testing.md`). MVP de validade curta.

## D-009 — Matrix 3 SOs com Windows-gate (26/07/2026)
Release exige `.msi` Windows; Linux/macOS `continue-on-error`, anexam se passarem.

## D-008 — jlink enxuto no v0 (26/07/2026)
Sem Spring no v0 → módulos mínimos (~50–80 MB de instalador). Na v1, com motor embarcado,
`ALL-MODULE-PATH`. Fallback imediato se faltar módulo em runtime.

## D-007 — Swing + FlatLaf confirmada (26/07/2026)
Re-julgada (não herdada): runtime dentro do JDK, menor risco de empacotamento. Única
vantagem real do JavaFX (TreeTableView) neutralizada pelo mestre-detalhe.

## D-006 — Camadas + MVP; sem CLI no v0; sem DI framework (26/07/2026)
Regra de dependência com ArchUnit; views atrás de interface; `ProgressListener` neutro;
frontend trocável sem tocar o núcleo. Sem interfaces-porta especulativas.

## D-005 — Schemas oficiais extraídos do JAR da Calculadora, commitados (26/07/2026)
Alinhamento versão motor↔schema garantido; espelho GitHub provou-se desatualizado;
`updateSchemas` re-extrai. Proveniência em `schemas-version.properties`. **Estudo futuro:**
automação da atualização (verificação de nova base pelo app, opt-out).

Fidelidade byte-a-byte é parte da decisão: `.gitattributes` marca `*.xsd -text` para que a
normalização de fim de linha não altere o artefato normativo (4 dos 14 XSDs são publicados
pela RFB com CRLF). Os 14 arquivos commitados foram verificados por SHA-256 contra as
entradas do JAR oficial. O cabeçalho de `schemas-version.properties` aponta para
`./gradlew updateSchemas` porque a `sourceUrl` registrada é uma URL pré-assinada que expira.

## D-004 — Entrega faseada (26/07/2026)
v0 = estrutura local (XSD, coleta total); v1 = valores via `regime-geral`. Motivo: endpoint
oficial de validação é XSD-only/fail-fast (descoberta 26/07); estrutura local entrega mais,
mais cedo, sem processo filho.

## D-003 — GPL-3.0 (26/07/2026)

## D-002 — Repo público vBaggio/validador-lote-rtc; Actions; Releases (26/07/2026)

## D-001 — Gradle + Java 21 (26/07/2026)

---

**Template:**
```
## D-0XX — Título curto (DD/MM/AAAA)
Decisão em 1-3 frases. Contexto essencial. Consequência/risco aceito.
```
