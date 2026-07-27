# Decisões

Log ADR-lite. Cada entrada: **Decisão**, contexto curto e consequência. Mais recentes no topo.
Template no fim. Decisões D-001..D-014 nasceram no brainstorm de 26/07/2026 (spec
[`superpowers/specs/2026-07-26-validador-lote-rtc-design.md`](./superpowers/specs/2026-07-26-validador-lote-rtc-design.md)).

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
