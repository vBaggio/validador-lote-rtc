# Progresso — SDD do v0 (plano 2026-07-26-v0-validador-lote-rtc.md)

> Podado em 28/07/2026 para secar o harness (D-044). Blocos fechados e mergeados têm o histórico
> tarefa-a-tarefa compactado aqui; achados que viraram decisão permanente estão em
> `docs/decisions.md` pelo número D-0XX, não duplicados. Nada foi perdido: o texto completo
> original está no histórico do git deste arquivo (`git log -p -- .superpowers/sdd/progress.md`).

## Bloco 6 (canal de artefatos externos, branch `bloco/6-canal-schemas`)

Task 30 (b6): complete (commit 974817d, revisão independente PASS após 2 fix loops) — catálogo
local e instalação transacional da primeira base de schemas. Candidata só atualiza `current` depois
de staging, cópia sem symlink/extras e compilação confinada; corrupção/erro de abertura volta à base
embarcada. ACHADO: manifesto+hash no diretório do usuário não autenticam malware sob a mesma conta;
D-046 declara o modelo de ameaça e restringe a promessa a integridade operacional. DÉBITO/Task 31:
proveniência/assinatura da aquisição oficial e atualização da base NF-e/NFC-e.

Task 31 (b6): complete (commit ebfb4ae, revisão independente PASS após fix loop) — atualiza a
closure NF-e/NFC-e para 010e_v1.02 e torna a proveniência auditável: Portal Nacional é autoridade,
ACBr r47146 é transporte explicitamente limitado; manifesto lista paths/hashes e hash canônico,
testado contra os bytes embarcados. ACHADO: README ainda apontava a Calculadora e o manifesto não
identificava a closure completa; ambos corrigidos. DÉBITO/Task 32: tabelas SVRS cuja URL antiga já
retorna 404 precisam do mesmo armazenamento transacional.

Task 32 (b6): complete (commit 1d0a12c, revisão independente PASS após fix loop) — normaliza a
página atual SVRS, atualiza tabelas em staging via HTTPS allowlist/limites e carrega a última tabela
íntegra com fallback. ACHADO: cobertura por cardinalidade aceitava troca de códigos; corrigida para
interseção >=80% de CST e cClassTrib, com mutação. Manifesto/Gradle apontam para a rota vigente e
separam última consulta de extração. Próximo: rotina de atualização e UI.

Task 33 (b6): complete (commit 068c868, revisão independente PASS após fix loop) — descobre só o
perfil 010e ativo do Portal, baixa ZIP confinado e agenda atualização após a UI. ACHADOS: grafia
oficial `v.1.02` não era reconhecida e intervalo 24h era só memória; normalização canônica e estado
persistente corrigem ambos. ZIP com atributo symlink também é rejeitado/testado. Próximo: UI/ACBr.

Fechamento local (b6): complete (commit e237ef6, revisão transversal PASS após fix loop) — B6
fechado localmente. Verificação final: `clean test` 376/0, `jpackageImage` e `git diff --check`
verdes. ACHADOS de fechamento: docs de privacidade omitiram consulta automática e a UI offline
omitia proveniência embarcada; corrigidos. Pesquisa marcou 010e_v1.01 como superada e harness foi
reconciliado com os commits amendados. A revisão adicional autorizada depois identificou a omissão
de `java.net.http` no runtime jlink e a spec histórica ainda sem a exceção D-048; Task 35 corrige
ambos antes do PR.

Task 35 (b6): complete (commit de correção da Task 35, revisão independente PASS/PASS) — adiciona
`java.net.http` ao runtime jpackage e um teste que falha se o módulo sair da configuração.
`clean test` passou com 377 testes; `jpackageImage` passou, o runtime lista o módulo e o launcher
ficou ativo por 12 s sem `NoClassDefFoundError`. A spec passa a declarar a exceção D-048 e CA-5
mede o comportamento real: nenhuma rede/dado fiscal no lote e somente consulta normativa pós-boot
consultiva. ACHADO CRÍTICO corrigido: o launcher da imagem falhava antes do Swing pela ausência do
módulo HTTP. ACHADO MÉDIO corrigido: a spec histórica contradizia D-048. A revisão confirmou
configuração, artefato gerado, smoke e coerência documental, sem novos débitos.

Task 36 (b6): complete (commit da Task 36, revisão independente PASS após fix loop) — substitui o
canal runtime de schemas do Portal Nacional pelo catálogo público e download estático da SVRS. Só
um pacote `010e` estritamente mais novo pode ser instalado; o pacote `010b` hoje publicado resulta
em “base local mantida”, sem downgrade e sem falso erro HTTPS. O parser limita-se à seção Schemas,
o URL é construído com nome publicado/allowlist e o resultado persistido aparece na tela Fontes
externas; erro de transporte identifica o host. ACHADOS DA REVISÃO: família sucessora não pode ser
aceita por ordenação; D-049 declara `010e` como limite deliberado e exige task/fixtures para uma
família nova. `clean test` (377), `jpackageImage` e re-revisão ficaram verdes. DÉBITOS: espelho
próprio versionado/assinado, promoção humana, Curadoria dos XMLs IBS/CBS externos, Calculadora e
demais DF-e ficam fora deste bloco, registrados na pesquisa de artefatos.

### PARADA — 30/07/2026, Task 36 concluída

Task 36 publicada em `origin/bloco/6-canal-schemas` por autorização do dono; árvore limpa. O fix
do canal SVRS está implementado, testado e revisado. Próximo passo: validação do dono no Windows e,
se aprovada, atualizar/abrir o PR do B6.

## Bloco 0 (harness) — PR #1 mergeado
6 tasks. Repo, Gradle, 14 XSDs oficiais (byte-idênticos ao JAR, D-005), docs canônicos (D-001..D-014),
agente + CI + GPL-3.0 + README.

## Bloco 1 (domínio, varredura, parse) — PR #2 mergeado
6 tasks (7-12). Domínio imutável (List.copyOf) + FindingReclassifier; RootCauseGrouper; ArchUnit
(D-015: `allowEmptyShould` nas 2 regras vazias até `application`/`presentation` existirem);
FolderScanner (symlink não pulado em silêncio, achado do /code-review pré-PR); XmlMetadataParser —
ACHADO DE SEGURANÇA: `SUPPORT_DTD=false` do StAX não rejeita DOCTYPE sozinho, precisou de rejeição
explícita do evento (D-017); enviNFe multi-nota zera 5 metadados (D-016); D-018 (XML minificado
colapsa índice linha→item). Decisão de produto: arquivo não-NF-e só precisa ser "inválido", não
classificado por família (pós-MVP). PENDÊNCIA UX bloco 4: mostrar a tela mestre-detalhe antes de
construir.

## Bloco 2 (motor XSD, tradutor, fixtures) — PR #3 mergeado
5 tasks (13-17). XsdErrorTranslator (achado fiscal: chave `cvc-enumeration-valid.CST` casava com o
CST errado — ICMS legado, não IBS/CBS — removida). SchemaValidatorEngine, 3 rodadas de revisão:
assinatura por substring manipulável, chaves de tradução mortas, OOM sem teto, locale ROOT
(D-019..D-024). Fixtures dos 13 XMLs reais do usuário: 12 limpos, 0 falso positivo.
**ACHADO DE PRODUTO CRÍTICO que mudou o roteiro:** `IBSCBS`/`IBSCBSTot` são `minOccurs="0"` no XSD
oficial — uma NF-e CRT=3 SEM NENHUM grupo IBS/CBS passa limpa no schema, e é exatamente essa a
rejeição 1115/UB12-10 que a SEFAZ liga em 03/08/2026. XSD puro não cumpre a promessa central nesse
caso. Isso motivou a spec nova de 27/07 (camada de previsão de rejeição, blocos 6/7) e reordenou os
blocos 3-5 do plano original para depois dela.

## Bloco 6 (branch bloco/6-camada-rejeicao, base 7cfae91)
Task 1 (b6): complete (commit f3a3998, review clean) — 74 testes. Domínio com 4 desfechos.
  Menores p/ fechamento do bloco (plan-mandated, ambos em FindingTest):
  - notEvaluatedIsNeitherApprovedNorRejected não prova a invariante de agregação (pertence à task
    de contadores no BatchReport, que está no bloco seguinte)
  - schemaFindingsKeepNullRejectionFields é tautológico: constrói o Finding e verifica o que passou
Task 2 (b6): complete (commit ea7b89b) — ingestão da tabela SVRS: 18 CSTs, 164 classificações,
  exigeReducao em 011/200/515 (bate com a verificação manual). Arquivo de 40 KB.
  NOTA: os 40 KB (contra os 420 KB da amostra de pesquisa) se explicam pelos ANEXOS de NCM/NBS,
  que a destilação do plano não inclui — coerente com o escopo (vínculo NCM ficou fora do primeiro
  corte), mas a ingestão precisará ser modificada quando quisermos essa trava.
Tasks 2+3 (b6): complete (commits 0d74f12 + 0df66b4, review + fix) — 82 testes.
  ACHADO da revisão: a validação da ingestão não alcançava o nível da classificação. Se a SVRS
  renomear CodClassTrib, as 164 classificações ficam com código nulo, colidem na mesma chave do
  mapa e sobra UMA — e as guardas de contagem não pegam, porque o número de registros não muda.
  Corrigido com validação estrutural (nenhum código nulo/vazio/duplicado) + comparação com a base
  embarcada (falha se a nova extração trouxer menos de 80% do que já tínhamos). 12 pontos de falha
  ruidosa contra 3 antes. Também: testes de permiteModelo e das bordas exatas de vigência.
Task 4 (b6): complete (commit 8f247e0) — 93 testes. Parse de CRT + TaxGroupExtractor.
  ACHADO REAL no código do plano: a variável de contexto de esfera (gIBSUF/gIBSMun/gCBS) nunca era
  zerada — nem no fechamento da esfera nem na abertura de novo det. Um gRed fora de esfera herdaria
  a última vista, INCLUSIVE a do item anterior -> acusaria redução na esfera errada (veredito fiscal
  incorreto). Corrigido com enum + reset nos dois pontos, verificado por mutação.
  Débitos registrados pelo agente: (1) getElementText estoura em conteúdo misto e derruba o arquivo,
  enquanto o XmlMetadataParser devolve null no mesmo caso — assimetria a resolver; (2) nItem inválido
  descarta o item, que fica sem avaliação de regra nenhuma; (3) crt nulo PRECISA virar "não avaliado"
  na camada de regras (Task 6 trata).

Task 5 (b6): complete (commit a5538a1, review clean) — 96 testes. Contrato RuleOutcome/RuleContext/RejectionRule.
  Menor deferido (plan-mandated): 2 dos 3 testes de RuleOutcomeTest são construir-e-ler-de-volta; a
  garantia real vem do sealed+record da linguagem. Mesma classe do menor da Task 1.
  ⚠️ do revisor resolvido pelo controlador: a fiação de operationDate é da Task 8 (RuleEngine passa
  doc.issueDate() ao montar o RuleContext) — não é lacuna desta task.

Task 6 (b6): complete (commit 7b42bbe, rodada 1/5 — 7 achados ADDRESSED, 0 abertos, re-review clean)
  149 testes verdes a partir de clean (eram 93 no início da task). Escopo muito maior que o do plano.
  ENTREGUE: 1115 com as duas exceções da UB12-10, 1021 corrigida, 1022 nova, 3 débitos da Task 4,
  D-027/D-028/D-029 em docs/decisions.md.
  Verificado por mutação (3 sondas): invólucro vs grupo interno na 1021; guard de data nula;
  refNFP no conjunto errado. Revisor confirmou as três por inspeção independente.

  CONTRATOS NOVOS que as Tasks 7 e 8 consomem (mudou POSIÇÃO, não só tipo — construção posicional):
  ItemTaxGroup(Integer itemNumber, boolean hasIbsCbsGroup, boolean hasGIbsCbsGroup, String cst,
    String cClassTrib, String cProdANP, boolean hasReducaoUf, boolean hasReducaoMun,
    boolean hasReducaoCbs, BigDecimal percReducaoUf, BigDecimal percReducaoMun, BigDecimal percReducaoCbs)
  FiscalDocument(Path source, String accessKey, String emitterCnpj, String documentNumber,
    LocalDate issueDate, String model, String rootElement, String crt, String finNFe,
    String tpNFDebito, List<ReferencedNote> references)   // references nunca nula, List.copyOf
  ReferencedNote(String form, YearMonth issuedAt)         // issuedAt null = referência não datável
  O CÓDIGO DE TESTE DO PLANO nas Tasks 7 e 8 constrói esses records com a aridade ANTIGA — precisa
  ser adaptado, não copiado.

  LIÇÃO A PROPAGAR: toda regra que chama tables.cst()/classTrib() precisa de guard de data nula
  ANTES da chamada — CstEntry.vigenteEm(null) estoura NPE e os 18 CSTs têm iniVig não nulo.

  Menores deferidos (do re-review, nenhum bloqueia): século da chave fixo em 2000, então AAMM "9912"
  vira 2099 e não aciona a exceção (direção que acusa; impacto desprezível, NF-e existe desde 2006);
  1022 sem teste na janela pré-vigência (comportamento correto — a UB13-30 condiciona ao indicador
  do CST, não à data — mas não exercitado); duas funções `normalizado` vizinhas com contratos
  diferentes; D-024 fora de ordem cronológica em decisions.md (pré-existente).

Task 6 (b6) — histórico da 1a entrega (commit 12c69d6, substituído por amend):
  1a entrega: GroupRequiredRule (1115) + GroupForbiddenRule (1021) + os 3 débitos da Task 4.
  Débito 1 resolvido com DESVIO justificado e verificado: getElementText lança deixando o reader
  parado no START_ELEMENT do filho, então try/catch dessincronizaria a máquina de estados (um filho
  chamado gRed ou IBSCBS produziria veredito fiscal falso). Trocado por varredura explícita até o
  END_ELEMENT correspondente. Revisor confirmou por sondas: aninhamento profundo, mesmo nome
  aninhado, filho hostil, CDATA, truncamento — sem laço infinito, contrato igual ao XmlMetadataParser.
  Débito 2: ItemTaxGroup.itemNumber virou Integer nulável (Finding.itemNumber já era Integer).
  Débito 3 (crt nulo -> NaoAvaliado): entregue, com teste de CRT em branco a mais.

  ACHADO CRÍTICO DA REVISÃO, confirmado contra a NT e o XSD oficial pelo controlador:
  <IBSCBS> e <gIBSCBS> são elementos DIFERENTES. TTribNFe (DFeTiposBasicos_v1.00.xsd:248) é
  sequence[CST, cClassTrib, indDoacao?, choice minOccurs=0 {gIBSCBS|gIBSCBSMono|gTransfCred|...}].
  O invólucro carrega o CST, logo existe sempre que há CST; o interno é opcional.
  NT linha 2373: UB12-10/1115 observa "tag: det/imposto/IBSCBS" (o invólucro).
  NT linha 2396: UB13-20/1021 observa "grupo: imposto/IBSCBS/gIBSCBS" (o interno), via ind_gIBSCBS.
  A 1021 entregue keiava no invólucro -> acusava TODO item de isenção/imunidade corretamente
  emitido, em 7 dos 18 CSTs (400, 410, 620, 800, 810, 811, 820). Falso positivo em escala.
  O teste do plano consagrava a semântica errada. A 1115 NÃO é afetada — observa o elemento certo.

  ACHADO MAIOR, do controlador lendo a NT na íntegra: a UB12-10 tem DUAS EXCEÇÕES não implementadas.
  Exceção 1 (NT 2374-2376): não se aplica a finNFe=4 (devolução) ou finNFe=2 (complementar) que
  referencia NFe anterior a 2026. Determinável OFFLINE: AAMM são as posições 2-5 da chave de 44
  dígitos (documentado no próprio XSD, leiauteNFe_v4.00.xsd:322; refNF tem AAMM explícito).
  Sem isso, toda devolução de nota de 2025 processada em ago/2026 vira rejeição 1115 — falso
  positivo na regra que motiva o bloco inteiro.
  Exceção 2 (NT 2377-2379): não se aplica a item com cProdANP presente na Tabela de Combustíveis
  Monofásicos, que NÃO está embarcada (Portal Nacional, aba Documentos > Diversos).

  DECISÕES DO USUÁRIO (27/07): implementar a Exceção 1 por inteiro (offline, indispensável);
  Exceção 2 vira NaoAvaliado + lista de implementações futuras; incluir as duas regras "quase de
  graça" — 1022 (UB13-30, espelho da 1021, exceção tpNFDebito=07) na Task 6, e 1024 (UB14-20,
  cClassTrib x CST) na Task 7.
  A registrar em docs/decisions.md: D-027 (gIBSCBS vs IBSCBS), D-028 (Exceção 1 offline),
  D-029 (Exceção 2 como não avaliado / futura). Maior registrado hoje é D-024.

  Demais achados da revisão em correção: NPE alcançável em GroupForbiddenRule quando issueDate é
  nulo (CstEntry.vigenteEm(null) estoura; XmlMetadataParser devolve null com dhEmi ilegível);
  CRT desconhecido caindo no ramo do Simples e virando acusação em 04/01/2027; borda de vigência
  do Simples sem teste no ramo positivo; CRT sem trim; data ISO em mensagem pt-BR.

  PENDÊNCIA PARA A TASK 7: incluir a 1024 (UB14-20) — ClassTribEntry já carrega o cst, então o
  vínculo está pronto. NÃO implementar 1151/1116/1131/1132: dependem de indicadores por CST
  (ind_gIBSCBSMono, ind_gTransfCred) que a destilação da Task 2 não trouxe.

Task 7 (b6): EM CURSO (commit c9e290e, rodada de correção 1/5 despachada) — 186 testes verdes.
  INCIDENTE DE PROCESSO: o implementador foi cortado por limite de gasto da conta ANTES de commitar.
  Trabalho estava completo e verde; o controlador verificou (clean test, 186/0/0) e commitou para
  não perder. Relatório do agente existe em task-7-report.md (untracked). A revisão foi a primeira
  leitura crítica do código — feita com rigor correspondente, e aprovou.
  ENTREGUE: 1024 (UB14-20, nova), 1025, 1033/1074/1079 com gatilho gCompraGov e exceção
  ind_gIBSCBS=0, e 1034/1046/1063 substituindo o "PERC-RED" que o plano INVENTAVA.

  2a VEZ QUE O ADENDO DO CONTROLADOR ERROU E O IMPLEMENTADOR ACERTOU: mandei extrair gCompraGov
  por item; ele está em infNFe/ide/gCompraGov (leiauteNFe_v4.00.xsd:499, mesmo nível de indPres e
  NFref), logo é de DOCUMENTO. Foi para FiscalDocument.hasCompraGov. Confirmado no XSD.
  Padrão a manter: instruir o subagente a conferir o XSD/NT e PERGUNTAR em vez de assumir.

  ACHADO IMPORTANTE da revisão (provado por mutação): o teste da exceção ind_gIBSCBS=0 é tautológico
  — o item usa semGrupoInterno(), então DOIS caminhos devolvem NaoAplicavel (a exceção e a delegação
  à 1022), e a asserção só olha o tipo. Removendo o bloco ReductionGroupRule:80-83 a suíte inteira
  continua verde. Implementação certa, teste não a protege. Em correção.

  Menores em correção: ramo inalcançável em ClassTribModelRule.normalizado(); cst==null sem teste
  em duas regras; docs/superpowers/plans/...md:1475 ainda mostra o "PERC-RED" inventado (o adendo
  vive em .superpowers/, fora de docs/, então quem lê só o plano vê o código falso).
  Menores REJEITADOS pelo controlador com razão registrada: (3) falso negativo do ramo ind_gRed=0
  segue a política conservadora; (4) detalhe acrescido à mensagem da 1024 foi pedido pelo adendo —
  a tensão com "mensagem oficial não se reescreve" vira sugestão de campo `detail` na Task 8.

Task 7 (b6): complete (commit 161f483, rodada 1/5 — todos os achados ADDRESSED, re-review clean)
  188 testes. Mutação repetida pelo implementador E reproduzida pelo revisor: com
  ReductionGroupRule:80-83 comentado, agora falha exatamente 1 teste
  (cstThatForbidsTheIbsCbsGroupIsExemptEvenUnderGovernmentPurchase), sem dano colateral.
  Menores 3 e 4 rejeitados pelo controlador com razão registrada (ver acima).
  MENOR 2 revelou-se meio-falso: o guard de null em ClassTribModelRule TEVE de ficar, porque
  Set.of("55","65").contains(null) lança NPE por contrato dos Set imutáveis do Java 9+. Só o
  trim()/isBlank() eram redundantes. Removê-lo teria trocado NaoAvaliado correto por exceção que
  derrubaria o arquivo do lote.

Task 8 (b6): complete (commit 555969f, rodada 1/5 — 2 Importantes + 6 Menores ADDRESSED, re-review clean)
  206 testes. Finding ganhou NotEvaluatedCause (14o componente); todo achado NOT_EVALUATED carrega
  chave estável e agregável sem casar texto. D-031..D-035 em docs/decisions.md.
  RULING DO CONTROLADOR sobre pergunta do implementador: "cobrir as causas" NÃO significa "cada uma
  com valor distinto". O balde grosso está aceito — data ausente, CRT ilegível e a Exceção 2 de
  combustível compartilham (RULE_SPECIFIC, UB12-10). Separá-las exigiria RuleOutcome.NaoAvaliado
  carregar causa declarada, tocando as 7 classes de regra ao fim de um bloco já muito maior que o
  planejado. Fica em D-033 para o bloco da apresentação em camadas. REVISITAR LÁ.
  Invariante "regra suprimida nunca chega a veredito" agora tem teste, verificado por mutação pelo
  implementador E reproduzido pelo revisor (declarar CST_PRESENT na ClassTribModelRule faz falhar).

Task 8 (b6) — histórico da 1a entrega (commit 8311438, substituído por amend):
  INCIDENTE: o 1o revisor foi cortado por stall de stream ao anunciar a sonda de mutação. Verifiquei
  que a árvore ficou limpa (não chegou a mutar). Controlador rodou a sonda e re-despachou a revisão
  com escopo reduzido.
  SONDA DO CONTROLADOR: rootCause() devolvendo null sempre (cascata desligada) derruba 5 testes,
  todos os de contagem. Restaurado, verde.
  ENTREGUE: RuleEngine com supressão em cascata por PRECONDIÇÃO nomeada (CST_PRESENT,
  CST_IN_TABLE, CLASS_TRIB_IN_TABLE), causa-raiz pela ordem de declaração do enum. Retorno mudou de
  List<Finding> para RuleEvaluation(findings, itemCount, verifiedItemCount).

  DIVERGÊNCIAS DO IMPLEMENTADOR, julgadas pelo mérito e MANTIDAS:
  (1) corte de raiz sobre o FATO !hasIbsCbsGroup(), não sobre o desfecho da 1115 — porque a 1115
      também devolve NaoAvaliado com CRT ilegível, e aí as outras 10 regras ainda são aplicáveis.
      Revisor: "o plano é que estava errado". A spec §4.4 escreve o gatilho como "grupo ausente".
  (2) corte extra por issueDate nula (não está na spec) — sem data as 11 regras dizem NaoAvaliado,
      8 com a mesma frase.
  (3) nível 2 da spec NÃO suprime 1033/1074/1079, porque ReductionGroupRule não lê cClassTrib —
      suprimi-la ali seria falso negativo. Desvio correto no mérito, não declarado no relatório.

  VERIFICAÇÃO IMPORTANTE do revisor: percorreu BINDINGS regra a regra e confirmou que para toda
  combinação (precondição faltante × regra que a declara) a regra só alcança NaoAvaliado ou
  NaoAplicavel — nunca Rejeitado nem Conforme. A cascata não esconde rejeição.

  CONFERIDO NA NT PELO CONTROLADOR: nenhuma regra de tabela (UB13-20, UB13-30, UB14-20, UB14-25,
  UB26-20, UB45-20, UB64-20, UB27-10, UB46-10, UB65-10) tem observação de implementação escalonada.
  Só UB12-10 (1115) e UB13-40 (1116) têm data. Não há falso positivo de vigência nas regras de tabela.

  DÉBITOS PARA A TASK 9 (bloco seguinte), registrados aqui porque a Task 9 não existe neste plano:
  - itemNumber é nulável E NÃO ÚNICO (TaxGroupExtractor:145 põe null em todo item com nItem
    ilegível). Derivar contagem por item a partir dos Finding não fecha nesse caso.
  - causa de documento (data nula, CRT ilegível) vira N achados, um por item. Documento de 500 itens
    sem data gera 500 NOT_EVALUATED idênticos — é o despejo de sintomas um nível acima.
  - friendlyMessage das rejeições vai null: não existe tabela de tradução para códigos de rejeição
    em src/main/resources/messages/. Débito de UX.

### PARADA — 27/07/2026, fim de sessão. Próximo: Task 9.

HEAD: fd609c6 na branch bloco/6-camada-rejeicao. Árvore limpa, 206 testes verdes, NADA pushado.
Tasks 5, 6, 7 e 8 completas e revisadas. Task 9 é o próximo passo.

O BRIEF DA TASK 9 JÁ ESTÁ PRONTO em .superpowers/sdd/2026-07-27-camada-rejeicao/task-9-brief.md,
com um ADENDO que corrige duas escolhas fiscalmente erradas do plano e acrescenta um requisito que
o plano não tinha. Leia o adendo inteiro antes de agir. Em resumo:
  - CST 400 e cClassTrib 011001 (que o plano manda usar) têm ZERO classificações válidas em NF-e
    modelo 55 — as fixtures do plano disparariam a 1025 junto e sujariam o caso.
  - Matriz substituta já verificada contra a base embarcada: 1021->CST 410/410001;
    1022->000/000001 sem gIBSCBS; 1024->000/410001; 1025->000/000002; 1033-1079->200/200002;
    percentuais->200/200002 com pRedAliq divergente (oficial 100.0).
  - REQUISITO NOVO E CRÍTICO: as fixtures precisam ser VÁLIDAS CONTRA O XSD. Se não forem, a SVRS
    para na validação estrutural e nunca chega às regras de negócio — e a Task 10, que é o gate
    humano do bloco, não produz informação nenhuma.

Task 10 é GATE HUMANO do dono do projeto: rodar as fixtures no validador da SVRS
(https://dfe-portal.svrs.rs.gov.br/NFE/ValidadorNfe) e preencher docs/validacao/casos-diferenciais.md.

Duas tentativas de despachar a Task 9 caíram por limite de gasto mensal da conta. Nenhuma deixou
resíduo na árvore (verificado). Se cair de novo, verifique `git status` antes de seguir.

## Integração Codex (27/07/2026) — commits 4285b93 e d0e4203, mergeados em main e no bloco 6

O projeto passa a ser trabalhado por Claude Code E Codex, UM DE CADA VEZ, no mesmo diretório.
Spec: docs/superpowers/specs/2026-07-27-integracao-codex-design.md
Método agora escrito em docs/workflow.md (fluxo de bloco, ledger, brief+adendo, conferir a fonte,
verificação por mutação, handoff). CLAUDE.md e AGENTS.md são adaptadores finos apontando para docs/.

Codex precisa de [features] multi_agent = true em ~/.codex/config.toml.
NÃO versionamos .superpowers/ (opção descartada) — git clean -fdx ainda destrói este ledger.

### Decisões de produto desta sessão que não estão em outro arquivo

COBERTURA: o grupo UB da NT tem 163 códigos de rejeição distintos. As seis do primeiro corte são
4% disso. Com a camada de valores (regime-geral) na v1, os ~46 de natureza aritmética passam a ser
cobertos pelo oráculo, chegando a ~32%. Cobertura completa não é meta realista.
  A distribuição dos 163: 49 presença, 46 cálculo, 12 tabela, 56 não classificadas (buraco que
  ninguém analisou regra a regra).

POSICIONAMENTO: "triagem em lote", nunca "garantia de aceitação". A confiança não vem da cobertura
— vem da honestidade sobre ela. Uma ferramenta que declara o que verificou é confiável com 4%;
uma que sugere completude é perigosa com 90%.

DECISÃO DO USUÁRIO (27/07): a versão publicada precisa já passar confiança. NÃO liberamos para
ajustar conforme a demanda. Por isso a Task 10 (validação diferencial contra a SVRS) é gate de
aceite, não sugestão. Polimento vem depois da entrega, não no lugar dela.

INFERÊNCIA NÃO MEDIDA: acreditamos que a 1115 domina os eventos reais de rejeição porque os 12 XMLs
reais que temos não têm grupo IBS/CBS. Isso é UM emitente, não é medição. Não tratar como fato.

### ADENDO — 28/07/2026 (reconciliação documental da Task 5 do fluxo de correção)

Este adendo preserva as entradas históricas acima e corrige sua propriedade atual:

- `.superpowers/sdd/progress.md` é versionado; briefs, relatórios e diffs permanecem scratch local.
- A Task 9 permanece restrita a fixtures diferenciais. O agrupamento de causas e seus contadores
  pertencem ao bloco seguinte, de integração/apresentação.
- Os débitos de `itemNumber` não único, deduplicação de causas de documento e mensagens amigáveis
  das rejeições pertencem ao mesmo bloco de integração/apresentação.

Task 9 (b6): complete (commit 17c4c6d, revisão local limpa) — 228 testes, 0 falhas.
  Entregue corpus diferencial XSD-válido: 11 positivos isolados (1115, 1021, 1022, 1024, 1025,
  1033, 1074, 1079, 1034, 1046 e 1063), seus 11 controles limpos e roteiro em
  docs/validacao/casos-diferenciais.md. O teste verifica XSD, entrada extraída, isolamento do
  código e ausência de achados nos controles.
  ACHADO: o controle inicial da 1022 ainda ficou em CST 000 e passava como limpo pelo motivo
  errado; corrigido para 200/200030 com reduções e protegido por asserção das pré-condições do par.
  DECISÃO: 1021 usa 410/410001 e reduções usam 200/200030 (60%); CST 400 e 011 do plano antigo
  não isolam as regras nos modelos NF-e/NFC-e segundo a tabela oficial embarcada.
  VERIFICAÇÃO: mutação de 59,99% para 60% no positivo da 1034 derruba a asserção de entrada e a
  de rejeição; restauração volta a verde.
  DÉBITO: Task 10 é gate humano — assinar equivalentes e confrontar a matriz com a SVRS antes de
  fechar o bloco. Nada foi enviado ao remoto.

### PARADA — 28/07/2026, após Task 9.

HEAD: 17c4c6d na branch bloco/6-camada-rejeicao. Árvore limpa, 228 testes verdes, nada pushado.
Próximo: Task 10, gate humano SVRS; aguardar o dono do projeto executar ou delegar a validação.

Task 10 (b6): evidência humana registrada até aqui (último commit edd9c22; sem push).
  A SVRS aceitou parser e schema em todos os 22 XMLs. Os 11 positivos confirmaram os códigos
  implementados: 1115, 1021, 1022, 1024, 1025, 1033, 1074, 1079, 1034, 1046 e 1063. Os 11
  controles não retornaram a rejeição-alvo correspondente.
  EVIDÊNCIA: resultados e descrições literais estão em docs/validacao/casos-diferenciais.md,
  seção "Evidências já executadas". A assinatura sintética, erros cadastrais e inconsistências
  gerais das fixtures foram classificados como ruído; códigos de cálculo/totais não implementados
  foram catalogados, sem implementação nesta task.
  ACHADO IMPORTANTE: no positivo r1022 a SVRS retorna 1022 + 1033 + 1074 + 1079, enquanto o
  motor local retorna somente 1022 por política deliberada de causa-raiz única quando falta
  gIBSCBS. O controle c1022 não retorna nenhum dos quatro; a divergência de multiplicidade está
  comprovada. Não alterar código sem decisão explícita sobre essa política.
  CANDIDATO NOVO: 1064 — "Valor da Alíquota Efetiva da CBS calculado incorretamente", observado
  no positivo da 1063. Outros candidatos IBS/CBS já catalogados: 1026, 1036, 1041, 1052, 1069,
  1119, 1076, 1080, 1084, 1085 e 1091. A fixture NFC-e de 1025 também revelou candidatos gerais
  de NFC-e (373, 410, 705, 716, 717, 789, 729, 383, 753, 760) e ruídos cadastrais.
  DÉBITO: decidir se o bloco deve preservar a causa-raiz única local ou reproduzir a multiplicidade
  observada na SVRS para 1022; depois atualizar teste, spec/decisão e código em uma task de correção.

### PARADA — 28/07/2026, handoff para troca de agente.

HEAD: edd9c22 na branch bloco/6-camada-rejeicao. Árvore Git limpa, nada pushado. Última suíte
  completa conhecida: 228 testes, 0 falhas. Alterações desde Task 9 são documentais, registrando
  o gate SVRS e o catálogo de códigos; nenhum código de produção foi alterado.
  PRÓXIMO AGENTE: ler docs/context.md, docs/workflow.md, este ledger e
  docs/validacao/casos-diferenciais.md; conferir git status/log; começar pela divergência 1022,
  sem implementar candidatos 1026/1036/1041/1052/1064/1069/1076/1080/1084/1085/1091/1119 antes
  de decisão do dono do projeto. Task 10 não deve ser declarada "sem divergências".

Task 10 (b6): complete — decisão do dono do projeto sobre a divergência de multiplicidade da 1022
  registrada em D-037: mantém-se a causa-raiz única (política existente desde D-032/D-034), não se
  reproduz a multiplicidade da SVRS. Critério de aceite do bloco ("SVRS aprova → não reprovamos")
  segue satisfeito; nenhum código de produção mudou por causa desta task.

  Nesta mesma sessão, uma segunda leitura independente da NT (docs/pesquisa/auditoria-regras-e-
  leitura.md e docs/pesquisa/auditoria-artefatos-oficiais.md, commit e0fde5f) achou um defeito
  funcional COM PRAZO, fora do escopo do gate SVRS: a Exceção 1 da 1115/UB12-10 (devolução de nota
  anterior a 2026) para de funcionar em 01/09/2026, quando a NT v1.40 migra o referenciamento de
  devolução para o grupo `DFeReferenciado` (item), que hoje não é lido. A partir dessa data,
  devolução emitida CORRETAMENTE pela norma nova vira falso positivo na regra principal do bloco.
  DECISÃO DO DONO DO PROJETO: corrigir agora, como task nova do bloco 6, antes do fechamento — não
  vira débito para bloco futuro. Ver task-referenciamento-devolucao-brief.md.

  A mesma auditoria também achou candidatos 1118/1119 (coerência entre item e total do grupo
  IBS/CBS) como os únicos do catálogo pós-b6 que são presença pura, sem aritmética — e portanto
  compatíveis com o escopo estrutural do v0.x. DECISÃO DO DONO DO PROJETO: incluir também como task
  nova do bloco 6, com o pré-requisito descrito em auditoria-regras-e-leitura.md §4.2 (qualificar a
  leitura do TaxGroupExtractor por contexto det/total antes de implementar, para não herdar a
  colisão de nome `gCBS` entre item e total). Ver task-totais-ibscbs-brief.md.

  DÉBITO REGISTRADO, NÃO RESOLVIDO NESTA SESSÃO: os XSDs embarcados (fonte: JAR da Calculadora RFB)
  estão uma revisão atrás da NT — faltam campos da v1.40 (`gALCZFMCBS`, `cIndOp`, `refDFeAnt`,
  `ISUFEmit`) que entram em produção em 03/08/2026, a 6 dias desta sessão. É falso positivo
  estrutural iminente, mas é escopo do bloco 2 (motor XSD), não do bloco 6 — revisitar D-005.
  Reportado ao dono do projeto; ação fica fora deste bloco por decisão dele.

Task nova (b6): complete (commit 4f31297, revisão independente PASS/PASS) — 238 testes, 0 falhas.
  ENTREGUE: Exceção 1 da 1115 passa a ler `DFeReferenciado` (item) além de `NFref` (documento),
  decisão D-038. `AccessKeyMonth` extraído como utilitário compartilhado da decodificação de AAMM,
  sem duplicar lógica entre `XmlMetadataParser` e `TaxGroupExtractor`.
  VERIFICAÇÃO POR MUTAÇÃO, duas vezes: o implementador comentou a leitura de `dfeReferenciado` na
  regra (3 testes específicos caem, 235 continuam verdes); o revisor, independentemente, removeu o
  reset de `det` no extractor (1 teste de vazamento entre itens cai sozinho) e deslocou
  `KEY_AAMM_START/END` em `AccessKeyMonth` (cai o teste novo E dois testes pré-existentes de
  `XmlMetadataParserTest` — confirma que a extração do utilitário não regrediu o comportamento
  documento). Ambas as sondas restauradas, árvore limpa.
  DÉBITO MENOR (revisão): D-038 e o brief afirmam que VC02-05/1010, 321/VC02-14 e 708/VC02-04 já
  estão catalogados em `docs/pesquisa/candidatas-rejeicao-pos-b6.md` — o arquivo commitado (8fb24d7)
  não contém essas referências; a afirmação veio de `auditoria-regras-e-leitura.md §2.4` sem
  conferência contra o artefato real. Não é risco fiscal (nada de VC02-05/321 foi implementado).
  Relacionado à divergência de conteúdo do próprio `candidatas-rejeicao-pos-b6.md` entre a versão
  commitada no bloco e uma versão mais extensa que ficou untracked no worktree principal — reportado
  ao dono do projeto, não reconciliado nesta sessão.

Task nova (b6): complete (commit efe058a, revisão independente PASS/PASS) — 251 testes, 0 falhas.
  ENTREGUE: 1118/W34-10 e 1119/W34-20 (coerência entre o invólucro `IBSCBS` do item e
  `total/IBSCBSTot` do documento), texto oficial conferido linha a linha no PDF da NT por
  implementador E revisor, independentemente. `hasIbsCbsTot` em `FiscalDocument`, no padrão de
  `hasCompraGov`. Fix de contexto (`emDet`) no `TaxGroupExtractor`, pré-requisito do brief.
  ARQUITETURA NOVA: `DocumentRejectionRule` — segunda família de regra, por documento, fora da
  cascata de `Precondition` (que é toda sobre disponibilidade de dado por item). Achados carregam
  `itemNumber = null` e não afetam `verifiedItemCount`. Revisor confirmou por leitura e mutação que
  a separação não vaza para o contador de itens verificados.
  DIVERGÊNCIA JULGADA E CONFIRMADA (não corrigida): tanto o implementador quanto o revisor,
  independentemente, com sondas de mutação diferentes, confirmaram que o fix `emDet` do extractor é
  hoje INERTE — o reset incondicional de estado na abertura de `det` já elimina qualquer vazamento
  de `total/IBSCBSTot`, com ou sem a guarda. Mantido mesmo assim por ser defesa em profundidade
  exigida pelo brief como pré-requisito obrigatório; registrado para que ninguém reabra a discussão
  achando que há um bug não corrigido — não há, o fix é cinto e suspensório.
  ACHADO MENOR (recorrente pela 2ª vez): brief, javadoc e D-039 citam
  `docs/pesquisa/candidatas-rejeicao-pos-b6.md` "Lote 1" como origem de 1118/1119 — o arquivo
  commitado (8fb24d7) não contém essa seção; a citação vem da versão mais extensa e não commitada.
  Mesma causa-raiz da divergência já registrada na task anterior. Não é risco fiscal.
  DÉBITO PARA O FECHAMENTO DO BLOCO: reconciliar as duas versões de `candidatas-rejeicao-pos-b6.md`
  (ou substituir a commitada pela mais extensa, com decisão explícita) antes do PR, para que as
  citações no código deixem de apontar para conteúdo que não existe na branch.

### PARADA — 28/07/2026, fim de sessão. Bloco pronto para a Task 11, aguardando o dono do projeto.

HEAD: c448ad5 na branch bloco/6-camada-rejeicao. Árvore Git limpa, nada pushado.
  `./gradlew clean test --console=plain`: BUILD SUCCESSFUL, 251 testes.

O que esta sessão fez, em ordem: fechou a Task 10 (D-037, causa-raiz única mantida para a 1022);
  commitou as duas auditorias órfãs de sessão anterior (e0fde5f); implementou e revisou
  independentemente duas tasks extras decididas pelo dono do projeto a partir dos achados de
  auditoria — correção da Exceção 1 da 1115/DFeReferenciado (4f31297, D-038) e as rejeições
  1118/1119 de coerência de totais (efe058a, D-039). Todas as quatro revisões independentes desta
  sessão (duas por task) deram PASS/PASS.

PENDÊNCIAS ANTES DA TASK 11 (fechamento/push/PR), nenhuma bloqueadora para o merge em si:
  1. Reconciliar `docs/pesquisa/candidatas-rejeicao-pos-b6.md` — existe uma versão divergente e mais
     extensa, untracked no worktree principal (fora deste worktree), nunca commitada em nenhum
     branch. A versão commitada aqui (8fb24d7) não tem a seção "Lote 1" que o código desta sessão
     cita duas vezes (D-039, javadoc de `TotalGroupForbiddenRule`). Reportado ao dono do projeto;
     decisão dele qual versão prevalece, ou se as duas se fundem.
  2. Débito XSD desatualizado (D-005, ver auditoria-artefatos-oficiais.md) — falso positivo
     estrutural a partir de 03/08/2026, fora do escopo do bloco 6, ação do dono do projeto.

PRÓXIMO PASSO: Task 11 do plano (`docs/superpowers/plans/2026-07-27-camada-rejeicao.md:1980`) —
  suíte completa (feito acima), relatório do bloco ao dono do projeto, aguardar liberação explícita
  antes de qualquer `git push`/PR. Não fazer push nem abrir PR sem essa liberação.

Task 11 (b6): Steps 1-3 completos, liberados pelo dono do projeto. Suíte verde (251 testes), push
  para `origin/bloco/6-camada-rejeicao`, PR #4 aberto
  (https://github.com/vBaggio/validador-lote-rtc/pull/4), CI (`build`) passou. Merge (Step 4) NÃO
  executado — aguardando confirmação separada do dono do projeto.

Reconciliação (b6): `docs/pesquisa/candidatas-rejeicao-pos-b6.md` (commit f91e27a) — a pendência
  registrada acima foi resolvida. As duas versões divergentes (a commitada nesta sessão anterior; a
  do Codex, produzida durante a implementação dele e nunca commitada, untracked no worktree
  principal) foram fundidas usando a NT e o código atual como critério de desempate. ACHADO NOVO,
  fora do escopo de qualquer um dos dois documentos originais: `SchemaValidatorEngine.SCHEMA_DIR`
  está fixo em `/schemas/nfe/` — todo documento, NF-e ou NFC-e, é validado contra o schema
  permissivo da NF-e. O `nfce/grupo.xsd` que restringiria corretamente `TTribNFCe` (sem
  `gCredPresOper`/`gCredPresIBSZFM`) existe no repositório e nunca é carregado. Isso invalidava a
  alegação "XSD já cobre" que a versão commitada usava para descartar 1049/1138 — reabertas como
  candidatas, junto com 1006/1165/708 (que só a versão do Codex tinha). Cópia divergente removida
  do worktree principal (não versionada, sem perda: conteúdo já fundido).
  DÉBITO NOVO, fora do bloco 6: o motor de schema (bloco 2) não diferencia modelo 55 de 65 em
  nenhum ponto — pode afetar mais códigos além de 1049/1138. Reportado ao dono do projeto.

Task nova (b7): complete (commit b6fef09, revisão independente PASS/PASS) — 303 testes, 0 falhas.
  Bloco novo (`bloco/7-cobertura-adicional`), brief `task-presenca-indicador-modelo-brief.md`,
  16 códigos em 4 mecanismos: diferimento por indicador CST (1029/1030/1044/1061/1083/1090),
  devolução de tributo proibida (1111/1112/1187), gTribCompraGov (1141/1144) e grupo proibido no
  modelo 65 (1006/1049/1138/1165/708, confirmado empiricamente por D-040). Decisão registrada:
  D-041.
  ACHADO: `permiteDiferimento` já estava mapeado corretamente (direto do `IndDiferimento` bruto da
  SVRS, sem inversão — só 510/515 verdadeiro) — o nome que mentia, sugerindo facultatividade que o
  indicador não tem. Renomeado para `exigeDiferimento`, sem mudar nenhum valor.
  ACHADO: 1138 e 1165 disparam sempre juntas em qualquer XML XSD-válido — `tpCredPresIBSZFM` é
  campo obrigatório dentro de `gCredPresIBSZFM`, não há como isolar. Uma fixture cobre as duas.
  DÉBITO: 1141/1144 sem fixture de corpus — isolar exige CST com `ind_gIBSCBS=1` e
  `gCompraGov=true` ao mesmo tempo, o que aciona o gatilho de compra governamental de
  `ReductionGroupRule`/`ReductionPercentageRule` (D-030) e contamina o achado com 3 findings extras
  em qualquer combinação XSD-válida. Cobertos só por unidade (`TableRulesTest`, 16 testes). Detalhe
  completo em D-041.
  DÉBITO: `docs/pesquisa/candidatas-rejeicao-pos-b6.md` não foi atualizado para mover os quatro
  mecanismos de "candidata" para "entregue" — fora do escopo explícito do brief, fica para quem
  mexer nesse documento de novo.
  Seis sondas de mutação, todas capturadas (classe genérica `PresenceForbiddenRule`, leitura de
  `exigeDiferimento`, `DiferimentoRequiredRule`, `ComprasGovComposicaoRequiredRule`,
  `TaxGroupExtractor` captura de `gDif`, `CompraGovForbiddenInNfceRule`) — `git status` limpo após
  cada uma.
  REVISÃO INDEPENDENTE: PASS/PASS. Revisor conferiu na fonte (não aceitou o relato): `IndDiferimento`
  bruto da SVRS batendo 1:1 com `exigeDiferimento` destilado (só CST 510/515); as 7 instâncias de
  `PresenceForbiddenRule` com o modelo certo (55/65 sem restrição para 1111/1112, "65" para as
  demais); `tpCredPresIBSZFM` obrigatório dentro de `gCredPresIBSZFM` no XSD, confirmando que 1138 e
  1165 não isolam. Duas sondas próprias (exceção `ind_gIBSCBS=0` da 1141; roteamento de precondição
  das regras de diferimento no `RuleEngine`), ambas capturadas. Achado Menor: linha órfã duplicada
  neste ledger — corrigida no mesmo commit que registra esta entrada.

Task nova (b7): complete (commit 6ff9811, revisão independente PASS/PASS) — 319 testes, 0 falhas.
  ENTREGUE: 1032/UB26-10, 1007/UB45-10, 1028/UB64-10 ("gRed informado indevidamente", espelho do
  lado ausente já implementado). `pRedutorCompraGov` capturado em `FiscalDocument` (documento,
  filho de `gCompraGov`, confirmado no XSD). Decisão D-042.
  DECISÃO JULGADA: quando a exceção de compra governamental se confirma (pRedutor legível e
  pRedAliq=0 na esfera), o desfecho é `Conforme`, não `NaoAvaliado` — os dois fatos exigidos pela NT
  estão confirmados, sem ambiguidade residual, mesmo padrão de `GroupForbiddenRule`. `NaoAvaliado`
  fica só para quando falta um dos dois fatos.
  ASSIMETRIA CONFIRMADA, NÃO CORRIGIDA: ao contrário da regra irmã (lado "ausente",
  UB26-20/UB45-20/UB64-20, que tem exceção `ind_gIBSCBS=0`), o lado "indevido"
  (UB26-10/UB45-10/UB64-10) desta task NÃO tem esse gate na NT — conferido literal por
  implementador E revisor, independentemente, direto no texto das seis regras. Não é omissão.
  Suíte completa reconfirmada verde (319 testes) após a revisão.

### PARADA — 28/07/2026, fim do bloco 7 (implementação). Pronto para push/PR, aguardando decisão
  de fechamento do dono do projeto (mesmo padrão do bloco 6: Task 11 — suíte, relatório, liberação).

HEAD: 6ff9811 na branch bloco/7-cobertura-adicional (nascida de main pós-bloco-6, commit 7fa3a2c).
  Árvore limpa, 319 testes verdes, nada pushado ainda. Duas tasks completas e revisadas
  independentemente (PASS/PASS nas duas), 19 códigos de rejeição novos: 1029, 1030, 1044, 1061,
  1083, 1090 (diferimento), 1111, 1112, 1187 (devolução de tributo proibida), 1141, 1144
  (gTribCompraGov), 1006, 1049, 1138, 1165, 708 (grupo proibido no modelo 65), 1032, 1007, 1028
  (gRed indevido). Débitos abertos, sem risco fiscal: `candidatas-rejeicao-pos-b6.md` não atualizado
  para mover os mecanismos entregues; 1141/1144 sem fixture de corpus (cobertos por unidade, ver
  D-041).

Task 18 (b3): complete (commit 19c5321, revisão independente PASS/PASS) — 323 testes.
  CsvExporter (BOM, ';', CRLF, escaping) implementado a partir do plano (linha 2191). ADENDO
  registrado no brief: o exemplo do plano usava o construtor de 12 argumentos de `Finding`, de
  antes dos blocos 6/7 (hoje 15 componentes, com rejectionCode/ruleId/notEvaluatedCause). Contrato
  público, colunas e formato do CSV não mudam. Teste extra cobrindo achado de REJECTION_RULE e
  NOT_EVALUATED (sem xsdCode).
  Sonda de mutação: `escape()` esvaziado (devolve a célula sem tratar `;`/aspas), o teste de
  escaping caiu sozinho; restaurado, `git status` limpo.

Task 19 (b3): complete (commit 1836b25, revisão independente PASS/PASS) — 332 testes.
  ENTREGUE: `ValidateBatchUseCase` (+ `BatchRequest`, `ProgressListener`, `CancellationToken`) liga
  o `RuleEngine` (blocos 6/7, até então sem consumidor de produção) ao fluxo real do lote, ao lado
  do `SchemaValidatorEngine`. Ver D-043 para a decisão completa; resumo:
  DECISÃO 1: as duas fontes (schema; extração+regras) rodam sempre que o parse de metadados tiver
  sucesso, mesmo com erro de schema já encontrado — os leitores StAX são tolerantes por contrato e
  o `RuleEngine` nunca rejeita por dado ausente (cascata de `Precondition`, D-032). Verificado no
  código (não suposto) que `GroupRequiredRule` e `CompraGovForbiddenInNfceRule` respondem
  `NaoAvaliado` a `crt`/`model` nulos, inclusive no caso mais extremo (enviNFe multi-nota, D-016).
  DECISÃO 2: `BatchReport` NÃO ganhou contadores novos (conforme/rejeitado/não-avaliado) —
  `documentsWithFindings`/`documentsUnreadable` já são genéricos por `Finding::source`/`kind` e já
  cobrem achados de `REJECTION_RULE`/`NOT_EVALUATED` sem mudança de código. Classificação por
  documento em três desfechos fica para o bloco 4 (UI mestre-detalhe), que tem o consumidor real
  para validar a semântica contra um caso concreto.
  ArchUnit (D-015): `applicationDoesNotSeePresentation` perdeu `allowEmptyShould(true)` — pacote
  `application` agora tem classes reais. `presentationDoesNotSeeInfrastructure` mantém (bloco 4
  ainda não existe).
  Sonda de mutação: `ruleEngine.evaluate(...)` comentado dentro de `validateOne`;
  `rejectionRuleFindingsAreWiredIntoTheReport` caiu sozinho, os demais 8 testes da classe
  continuaram verdes; restaurado, `git status` limpo.
  DÉBITO, sem risco fiscal: cada arquivo agora é lido do disco até 3× por documento (metadados,
  schema, extração de grupos) — já era 2× antes deste bloco (RejectionFixturesTest já exercitava o
  mesmo padrão em teste). Custo de I/O, não de corretude; candidato a otimização futura se o
  desempenho em lotes grandes se mostrar um problema real.

Task 20 (b3): review do orquestrador antes do fechamento encontrou um achado real no
  `ValidateBatchUseCase.execute`: o laço que drena o `CompletionService` usava o índice `i` da
  submissão (`files.get(i)`) para nomear o arquivo no `Finding` de erro inesperado, mas
  `CompletionService.take()` entrega pela ordem de **conclusão**, não de submissão — sob execução
  paralela um erro (uma exceção não-`RuntimeException`, ex. `Error`, já que `validateOne` captura
  `RuntimeException` internamente) seria atribuído ao arquivo errado. Corrigido: um
  `Map<Future<List<Finding>>, Path>` (`fileOf`) associa cada tarefa submetida ao seu arquivo, e o
  `catch (ExecutionException)` consulta esse mapa pela própria `Future` em vez do índice. Dobrado
  no mesmo commit da Task 19 (`--soft reset` + recommit, não pushado ainda — sem cadeia de fix
  commits). Sem teste automatizado novo: os colaboradores de `validateOne` são construídos
  concretos (sem seam de injeção de falha por arquivo), então forçar esse caminho end-to-end exigiria
  refactor fora do escopo deste achado; verificado por leitura e reprodução mental do agendamento,
  não por mutação. Suíte completa reconfirmada verde (332 testes) após a correção.

### PARADA — 28/07/2026, fim do bloco 3. Suíte verde, revisão do orquestrador feita e achado
  corrigido. Pronto para push + PR (Task 20), aguardando confirmação do dono do projeto antes do
  merge.

Task 21 (b4): complete (commit 8dccf5e, revisão independente PASS/PASS) — 341 testes.
  ENTREGUE: contratos `MainView`/`UiThread` e `MainPresenter` toolkit-agnóstico. Análise e CSV
  executam no executor de background; progresso, resultados e erros desse trabalho atravessam
  `UiThread`. Toggle pré-emissão reutiliza `ValidateBatchUseCase.regroup` (teste apaga o XML antes
  do toggle) e cancelamento/nova análise invalidam callbacks obsoletos pelo token.
  ADENDO: o construtor do caso de uso no plano estava obsoleto desde B3; os testes usam
  `TaxGroupExtractor` e `RuleEngine` atuais. `BatchReport` segue intacto — a apresentação em
  camadas será resolvida na Task 23, sem classificar silenciosamente documentos como conformes.
  REVISÃO INDEPENDENTE: PASS/PASS; conferiu confinamento sem Swing/AWT/infraestrutura, EDT dos
  callbacks de background, cancelamento, erros e exportação. Sem achados ou débitos.

Task 22 (b4): complete (commit 7892d14, revisão independente PASS/PASS) — 341 testes.
  ENTREGUE: `App` monta o grafo atual (inclusive `TaxGroupExtractor` e `RuleEngine`), shell
  Swing/FlatLaf com EDT, `CardLayout`, escolha/drop de pasta e progresso cancelável. A versão dos
  schemas é lida uma vez e reaproveitada no caso de uso e título. D-015 concluída: a permissão
  temporária `allowEmptyShould(true)` saiu da regra ArchUnit de `presentation`.
  REVISÃO INDEPENDENTE: PASS/PASS; confirmou fronteiras, ciclo do executor daemon, EDT, DnD seguro
  e caminhos de erro. Sem achados ou débitos. Inspeção humana dos fluxos de janela/drop/cancelamento
  fica para a verificação visual do fechamento do bloco; a UI apenas iniciou em sessão gráfica.

Task 23 (b4): complete (commit 206b92a, revisão independente PASS/PASS) — 341 testes.
  ENTREGUE: ResultsPanel mestre-detalhe substitui o placeholder, com coluna `Camada` derivada só de
  `FindingKind`, resumo de leitura/schema/previsão, toggle, nova análise e exportação. A camada de
  previsão separa causas previstas das não avaliadas; o detalhe marca a razão de `NOT_EVALUATED`
  como explicação local e preserva mensagem oficial quando existe. A conferência de valores é
  declarada explicitamente não executada (requer Calculadora).
  DECISÃO DE APRESENTAÇÃO: nenhum documento ou camada sem achado recebe rótulo de conforme/aprovado.
  A UI exibe a origem dos achados já apurados e a limitação da camada de valores, sem inferir um
  veredito que o `BatchReport` não carrega (D-043 e spec de camadas §7).
  REVISÃO INDEPENDENTE: PASS/PASS; confirmou passividade, EDT, dados nulos/vazios, sem novo
  julgamento fiscal e sem reordenação/reagrupamento no Swing. Sem achados ou débitos.

### PARADA — 28/07/2026, B4 implementado e revisado; aguardando validação visual do dono.

Branch `bloco/4-ui`; commits de task `8dccf5e`, `7892d14`, `206b92a`. A suíte final
`./gradlew clean test --console=plain` está verde (341 testes), `git diff --check` limpo. As três
tasks de implementação passaram em revisão independente PASS/PASS. O ambiente gráfico iniciou a
aplicação, mas a captura do compositor ficou preta: falta inspeção humana de escolha/drop,
progresso/cancelamento, mestre-detalhe, toggle e exportação antes de push/PR. Não houve push.
Próximo passo, após validação do usuário: fechar o bloco com push e PR (Task 24), então atualizar
`CURRENT.md` conforme o merge.

Task 24 (b4): complete (merge local `0dff1b2`) — a validação visual do dono conduziu o refinamento
final `1fb7132`, coberto por 342 testes verdes em `./gradlew clean test --console=plain` e
`git diff --check` limpo. D-045 registra a divergência deliberada do plano: importar forma um lote
de documentos e não valida automaticamente; a validação é sequencial, incremental e cancelável na
grade; documentos são a visão primária e problemas do selecionado são o detalhe; XML ilegível é
recusado fora da grade. FlatDarkLaf/Roboto, ícone de janela e layout maximizado são acabamento de
produto. CSV foi retirado da apresentação, mantendo `CsvExporter`/testes no núcleo; README e B5
foram ajustados para não prometê-lo. ACHADO DE FECHAMENTO: cancelamento entre a marcação de uma
linha e seu processamento poderia removê-la como inválida; corrigido para devolvê-la a pendente.
Também foram removidos os contratos/tela mortos do antigo fluxo de progresso. Sem julgamento
fiscal novo; B4 foi mesclado **localmente**, sem push/PR remoto por escolha do dono.

Task 25 (b5): em preparação — brief local inclui o adendo D-045: `jpackage` precisa receber ícone
nativo (especialmente `.ico` no Windows), pois o SVG atual só cobre a janela Swing. Próximo fluxo:
brief → implementador → revisão independente de build/plumbing → fix loop → commit.

Task 25 (b5): complete (commit `983ed90`, revisão independente PASS após 2 fix loops) — tasks
`jpackageImage`/`jpackageInstaller` e runtime jlink explícito; ícones nativos `.ico` (Windows),
`.png` (Linux) e `.icns` (macOS). `./gradlew clean test --console=plain` verde (342); app-image
gerado, runtime Java 21 confirmado e launcher iniciado sem falta de classes. ACHADOS DA REVISÃO:
(1) Fedora não pode assumir DEB: seleção agora escolhe RPM/DEB por distribuição e verifica as
ferramentas antes do `jpackage`; no Fedora atual falta `rpmbuild`, portanto a falha é explícita e
não mascarada. (2) macOS recebeu `.icns`. (3) DEB também exige `fakeroot`, incluído na guarda e na
mensagem. Sem mudança fiscal; relatório scratch registra o smoke e a limitação local.

Task 26 (b5): em preparação — workflow de release, Windows como gate, conforme plano. Exige brief,
implementação e revisão independente de CI/configuração.

Task 26 (b5): complete (commit `d45ed3a`, revisão independente PASS) — workflow de release em
tags `v*`, `contents: write`, Windows/MSI como gate obrigatório e Linux/DEB + macOS/DMG como
best-effort após Windows. Ubuntu instala/verifica `dpkg`, `dpkg-deb` e `fakeroot`, compatível com
a guarda da Task 25. YAML validado localmente e `./gradlew clean test --console=plain` verde (342).
Limitação honesta: actionlint não estava instalado e execução completa depende da primeira tag no
GitHub; nenhum push/tag foi feito.

Task 27 (b5): em preparação — README definitivo deve refletir D-045: pasta/XML individual,
importar antes de validar, detalhe por documento e CSV sem ação de UI. Exige brief, implementação e
revisão independente de documentação.

Task 27 (b5): complete (commit `8e06e66`, revisão independente PASS) — README de usuário final
em pt-BR, fiel a D-045: importação antes de validar, estados/progresso/interrupção, detalhe do
documento e remoção de válidos. Declara análise local, independência fiscal, ausência temporária de
CSV na UI e instalação/release condicionais sem prometer artefato não publicado. Links, comandos
Gradle e licença conferidos; `./gradlew clean test --console=plain` verde (342).

Task 28 (b5): pendente de autorização externa — push/PR/checks/merge remoto. B5 está completo em
código e documentação; não criar PR, não enviar branch e não mergear remoto sem comando explícito
do dono. Task 29 (tag/release pública) continua gate humano posterior.

Task 28 (b5): complete — autorização do dono recebida; branch publicada, PR #7 aberto com B4+B5,
check `build` verde e mergeado em `main` no commit `96f501f`. `main` local foi sincronizado com
`origin/main`. Task 29 permanece o único gate: tag/release pública e smoke de MSI em Windows real;
não iniciada sem confirmação explícita para publicar a versão.

Task 34 (b6): complete (commit `16080b3`, revisão pendente da Task 35) — 376 testes.
  ENTREGUE: rodapé abre diálogo não modal **Fontes externas**, com estado por artefato, origem,
  versão/snapshot, hash abreviado, atualização, consulta e aviso recuperável. “Verificar agora”
  usa o mesmo coordenador em background, força uma consulta mas é protegido por gate atômico:
  cliques repetidos durante a execução não geram download duplicado. Os engines usados pelo lote
  não mudam em memória; candidata instalada só vale no próximo boot. D-048 fixa Portal como
  autoridade e ACBr somente como espelho de inspeção/disponibilidade, sem fallback automático ou
  SVN silencioso. `updateSchemas`/`updateFiscalTables` foram bloqueadas antes de rede/escrita;
  base embarcada só muda por manutenção de release em staging e diff revisado.
  Sonda de mutação: removido o gate `compareAndSet`; o teste de clique duplicado caiu sozinho;
  restauração e `./gradlew clean test --console=plain` (376) verdes. DÉBITO: retenção/poda de
  versões e telemetria local de espelho ACBr permanecem fora do escopo, sem autorizar ativação.

Task 37 (b6): complete (commit `6c007e0`, revisão independente PASS/PASS após 1 fix loop) —
separa preparação de ativação para schemas e tabelas: `prepare` mantém `current`, `activate`
revalida a versão confinada antes do movimento atômico e os updaters expõem `check`/`apply` com
candidata tipada. ACHADO: `publishedAt` volátil impedia repetir a preparação da tabela; a
identidade estável reutiliza a candidata íntegra antes da confirmação. Sonda do guard de
`artifactId` derrubou os dois testes negativos.

Task 38 (b6): complete (commit `e3997d1`, revisão independente PASS/PASS) — falhas HTTP tipadas,
timeout, duas tentativas somente transitórias, aplicação parcial e persistência por identidade de
canal. A remoção de `action.apply` derrubou dois testes. DIVERGÊNCIA JULGADA: falha de ativação
mantém a candidata para diagnóstico, mas exige nova consulta antes de nova aplicação; não há
repetição cega.

Task 39 (b6): complete (commit `774c117`, revisão independente PASS/PASS após 1 fix loop) —
snapshot imutável único, fila de publicação monotônica sem observador sob lock, descarte de revisão
obsoleta na EDT, gate de validação e latch de reinício por sessão. ACHADOS corrigidos: snapshots
concorrentes fora de ordem, prompt duplicado e `RESTART_REQUIRED` apagável por consulta posterior.
A sonda que removia a conservação de `CHECKING` voltou a abrir confirmações intermediárias e caiu.

Task 40 (b6): complete (commit `0d7ed20`, revisão independente PASS/PASS após fix loop) — rodapé,
spinner e diálogo adaptável compartilham o snapshot; o modal bloqueia fechamento em `APPLYING`.
ACHADOS corrigidos: timers de spinner órfãos e filtro da Calculadora por texto em vez de identidade
estável. `clean test` (424), `jpackageImage` e `git diff --check` passaram. DIVERGÊNCIA: a captura
gráfica do ambiente retornou framebuffer preto; a inspeção visual de Windows/DPI permanece manual.

Task 42 (b6): complete (commit `0ada78b`, revisão independente PASS/PASS após 1 fix loop) —
endurece a orquestração autorizada pelo dono: a admissão atômica impede validação entre reserva e
início de ativação; snapshots preservam reinício se `current` mudou antes de falhar persistência;
e listeners/completion listeners que falham não deixam a fonte em `APPLYING`. A falha terminal
bloqueia reaplicação até consulta fresca e mantém a base anterior quando `apply` não venceu.
Sondas das guardas de admissão e transição terminal derrubaram testes determinísticos. `clean test`
passou com 434 testes, `ArchitectureTest` e `git diff --check` verdes.

Task 41 (b6): complete (commit `944e932`) — D-050, arquitetura, estratégia de testes, pesquisa e
harness registraram o ciclo confirmado e as garantias até a Task 42. Os refinamentos posteriores
foram registrados pela Task 45; o commit não é evidência de inspeção visual no Windows.

Task 43 (b6): complete (commit `7fa9a73`) — o presenter adia a abertura do diálogo
application-modal para o próximo ciclo da EDT. Assim, o modal não reentra nem bloqueia o dreno
síncrono do snapshot `APPLYING` e dos eventos terminais. Teste determinístico segura a sequência.

Task 44 (b6): complete (commit `0d5750c`) — listener defeituoso já em `CHECKING` produz término e
permite nova consulta; falha parcial permanece visível com a outra fonte em dia; a leitura HTTP do
corpo tem prazo, cancelamento e limite em streaming; e rejeição do executor ao aplicar vira erro
recuperável sem loop de prompt. Os testes determinísticos incluem servidor que envia cabeçalhos e
interrompe o corpo.

Task 45 (b6): implementada no commit semântico local, revisão pendente — D-050, contexto,
arquitetura, testes e planos registram as garantias das Tasks 43/44 e seus hashes finais: T37
`6c007e0`, T38 `e3997d1`, T39 `774c117`, T40 `0d7ed20`, T42 `0ada78b`, T43 `7fa9a73`, T44
`0d5750c`, T41 `944e932`. DÉBITO/GATE HUMANO: checklist visual do Windows (100%, 125%, 150%,
rolagem, ícones, consulta/retry, parcial, bloqueio de fechamento e reinício) permanece pendente;
não publicar, abrir PR ou fazer merge sem o dono.

### PARADA — 30/07/2026, fechamento técnico do refinamento B6

HEAD e árvore devem ser conferidos contra o git após o commit documental. Tasks 37–45 estão
implementadas localmente; a Task 45 aguarda revisão independente. Nada deve ser enviado ao remoto.
Depois da revisão, o próximo passo autorizado é somente o smoke manual do dono no Windows e a
decisão explícita de publicação/PR.

Task 1 (b7): complete (commit `44e6d70`, revisão independente PASS/PASS após 1 fix loop) —
458 testes. ENTREGUE: contrato JSON estrito, canonicalização de `signed` e verificação Ed25519
somente por `keyId`/chave pública injetados. ACHADO: `version` aceitava segmentos de diretório
especiais/bordas inseguras e `minimumAppVersion` podia escapar do comparador; os formatos foram
fechados e cobertos, inclusive contra coerção escalar e assinatura alterada.

Task 2 (b7): complete (commit `ab603e9`, revisão independente PASS/PASS após 1 fix loop) —
470 testes. ENTREGUE: `releaseSequence`, canal, proveniência, hash do ZIP e identidade canônica
assinada persistidos; `prepare`/`activate` revalidam integridade e anti-rollback sem tocar
`current` em falha. ACHADOS: uma release antiga já preparada ainda podia ser ativada sobre outra
mais nova, e sequências iguais distintas eram ambíguas; a ativação passou a recomparar a base
ativa e a idempotência exige versão, ZIP e identidade assinada iguais.

Task 3 (b7): complete (commit `94a5790`, revisão independente PASS/PASS após 1 fix loop) —
488 testes. ENTREGUE: aquisição somente por manifesto assinado e políticas HTTPS independentes,
com gates de artefato/app/sequência, hash constante, extração segura e preparo sem ativação.
ACHADO: falhas locais do staging/cleanup eram classificadas como conteúdo hostil ou mascaravam a
falha primária; agora são `LOCAL_STORAGE`, e cleanup secundário fica suprimido.

Task 4 (b7): complete (commit `854c3f4`, revisão independente PASS/PASS após 1 fix loop) —
494 testes. ENTREGUE: runtime de schemas saiu da SVRS, incompatibilidade ganhou estado próprio e
não bloqueia tabela fiscal; sem bootstrap real, o canal fica visivelmente desabilitado. ACHADO:
o card da base embarcada ainda atribuía origem à antiga página SVRS; passou a usar a proveniência
de `schemas-version.properties`.

Task 5 (b7): implementação local complete (commit `0ab189c`; revisão ampla com correções em
re-revisão) — D-051 e os guias registram curadoria manual, Ed25519, `releaseSequence`, checklist
de publicação e aceite runtime. D-047/D-049 foram substituídas somente no transporte runtime de
schemas; tabela fiscal continua no SVRS e SVRS/ACBr são apenas pesquisa/proveniência.
REVISÃO AMPLA `d569f0f..0ab189c`: quatro achados Important foram corrigidos no fechamento local:
(1) sequência ativa igual só é `UP_TO_DATE` quando `zipSha256` e SHA-256 canônico de `signed`
coincidem; divergência vira `INVALID_CONTENT`; (2) o boot seleciona engine e proveniência da mesma
`current` íntegra para rodapé e `BatchReport`, e o texto desabilitado distingue base curada/local
da embarcada; (3) este ledger e `CURRENT.md` passaram a registrar Tasks 1–5 e a parada; (4)
README/contexto passaram de 24 horas/fontes oficiais para 4 horas, canal curado e SVRS da tabela.
O fechamento roda 497 testes. Bootstrap externo concluído em 30/07/2026: repositório público
`vBaggio/validador-lote-rtc-bases`, endpoint GitHub Pages, `keyId` `schemas-2026-01`, chave pública
Ed25519 e release `010e_v1.02-r2` no canal `nfe-schemas` foram publicados e conferidos contra a
canonicalização do cliente; o ZIP carrega a árvore XSD completa.
O aplicativo passa a consultar somente esse canal; não há fallback SVRS/ACBr.

### PARADA — 30/07/2026, entrega B7 validada em runtime

Branch `bloco/7-canal-proprio-schemas`; o bootstrap do repositório de bases, a configuração do
cliente e o smoke runtime manual foram verificados. O dono confirmou a primeira instalação da
release `010e_v1.02-r2`, ativação confirmada e uso após reinício; detalhes locais em
`tmp/runtime-smoke-canal-curado-2026-07-30.md`. As pendências evolutivas e o gate visual do
instalador Windows foram registrados em `docs/operacao-canal-schemas-curados.md`. Em 30/07/2026,
o dono autorizou push, abertura de PR e merge do aplicativo.

Task 46 (b8): complete (commit `f949b89`, revisão independente PASS/PASS após 2 fix loops) —
introduz `ValidationRuntime`/`RuntimeBases` imutáveis, factory thread-safe de gerações e associa a
identidade da base ao resultado concluído do documento. ACHADOS IMPORTANTES corrigidos: o tipo
agora proíbe bases em `PENDING`/`VALIDATING`, limpa identidade ao retornar a pendente e exige
identidade em resultado terminal; a geração passou a ter dono monotônico; e os testes atravessam
`VALIDATING` antes de cancelar ou lançar, provando que não há geração residual. TDD, suíte completa
e duas sondas de mutação passaram; D-052 registra que troca posterior não recalcula nem
reetiqueta resultados.

### PARADA — 30/07/2026, Task 46 B8 concluída

HEAD `f949b89` na branch `bloco/8-troca-runtime-bases`. Task 46 está commitada, revisada e árvore
limpa. Próxima task: 47 — substituir a admissão booleana por leases capturadas sob o mesmo gate da
ativação e preparar os testes de concorrência. Não há push, PR ou merge autorizado durante o bloco.

Task 47 (b8): complete (commit `1d98ec9`, revisão independente PASS/PASS após 1 fix loop crítico)
— introduz lease opaca capturada sob o mesmo lock da reserva de ativação e liberação por identidade
no presenter. ACHADOS CRÍTICOS corrigidos: observer que lança no dreno não prende lease/reserva; e
completion atrasado recebe ticket e não pode liberar ativação posterior. Barreiras determinísticas,
sonda de mutação, suíte completa e `git diff --check` passaram. A mudança concreta de runtime ainda
é responsabilidade da Task 48.

### PARADA — 30/07/2026, Task 47 B8 concluída

HEAD `1d98ec9` na branch `bloco/8-troca-runtime-bases`. Tasks 46–47 revisadas e árvore limpa.
Próxima: Task 48 — factory do grafo completo, construção fora da EDT e publicação de R2 com fallback
latched para boot. Sem push/PR/merge durante o bloco.

Task 48 (b8): complete (commit `1b1f5db`, revisão independente PASS/PASS após 2 fix loops) —
factory no composition root recompõe schema, tabelas, regras e caso de uso fora da EDT/stateLock e
publica R2 sob o ticket do gate. ACHADOS IMPORTANTES corrigidos: testes agora usam stores reais
para provar schema R2+tabela R1 em falha parcial e `current` R2 + lease R1 + boot seguinte no
fallback; o snapshot expõe detalhe sanitizado da recarga sem marcar a fonte física APPLIED como
falha. Mutação, suíte completa, `jpackageImage` e `git diff --check` passaram.

### PARADA — 30/07/2026, Task 48 B8 concluída

HEAD `1b1f5db` na branch `bloco/8-troca-runtime-bases`. Tasks 46–48 revisadas e árvore limpa.
Próxima: Task 49 — transição de UI para `UPDATED_AND_IN_USE` e fallback de reinício excepcional.

Task 49 (b8): complete (commit `f4316b7`, revisão independente PASS/PASS após 1 fix loop) —
sucesso normal agora informa bases já em uso, sem pedir reinício; fallback conserva detalhe
sanitizado e reinício excepcional. ACHADO IMPORTANTE corrigido: em `RELOADING_RUNTIME`, cards
APPLIED exibem carregamento neutro/spinner, nunca “próximo boot”; o texto fica exclusivo de
`RESTART_REQUIRED`. Mutação, suíte e `git diff --check` passaram.

### PARADA — 30/07/2026, Task 49 B8 concluída

HEAD `f4316b7` na branch `bloco/8-troca-runtime-bases`. Tasks 46–49 revisadas e árvore limpa.
Próxima: Task 50 — documentação operacional, regressão integrada e roteiro de aceite runtime.
