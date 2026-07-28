# Progresso — SDD do v0 (plano 2026-07-26-v0-validador-lote-rtc.md)

Modificação do usuário: relatório + validação manual ao fim de cada bloco, ANTES do PR.

Task 1: complete (repo público criado + main publicada; branch bloco/0-harness)
Task 2: complete (commit d898467, review clean — base Gradle 8.14.3)
  Minor p/ revisão final: build.gradle copy{}/delete{} em doLast (plan-mandated, sem impacto — só relevante se configuration cache for adotado)
Task 3: complete (commit amendado, review clean — 14 XSDs byte-idênticos ao JAR oficial verificado por hash)
  Correções do controlador dobradas na task: *.xsd -text no .gitattributes (fidelidade byte-a-byte);
  header de proveniência no properties + mesmo header no heredoc do updateSchemas (consistência).
Task 4: complete (commit 6d759c8, review clean — 6 docs canônicos, links verificados, D-001..D-014)
Task 5: complete (commit 1cf44d4, review clean — agente, CI, GPL-3.0 verificada por checksum, README inicial)
Task 6: complete (PR #1 mergeado, CI verde) — BLOCO 0 FECHADO
  Fix aplicado por decisão do usuário antes do PR: cache: 'gradle' no setup-java (amend em 1a309b5).

## Bloco 1 (branch bloco/1-dominio-scan, base 220e5d5)

### PARADA — 2026-07-26 (fim de sessão)

Estado: Bloco 0 COMPLETO e mergeado na main (PR #1, CI verde).
Branch `bloco/1-dominio-scan` criada a partir da main, SEM commits ainda.

Retomar em: Bloco 1, Task 7 (records do domínio + FindingReclassifier).
Brief já gerado: .superpowers/sdd/task-7-brief.md

Tasks do Bloco 1: 7 (domínio), 8 (RootCauseGrouper), 9 (ArchUnit), 10 (FolderScanner),
11 (XmlMetadataParser — a mais delicada, despachar com Opus), 12 (relatório + validação + PR).

Protocolo em vigor: 1 subagente por task + revisor independente por task;
relatório e validação manual do usuário ao FIM do bloco, antes do PR.
Task 7: complete (commit amendado, review clean após fix — modelo de domínio + FindingReclassifier)
  Fix do controlador dobrado na task: construtor compacto com List.copyOf em RootCause e BatchReport
  (achado Importante plan-mandated do revisor: listas expostas sem cópia defensiva) + DomainImmutabilityTest.
  Menores aceitos sem ação: teste usa containsExactly em vez de isSameAs; comentário de roadmap em FindingKind.
Task 8: complete (commit amendado, review clean — RootCauseGrouper)
  Fix do controlador dobrado: 2 testes fechando lacunas apontadas pelo revisor (empate real em
  affectedDocuments; explicação vazia sem tradução E sem mensagem oficial).
Tasks 9+10: complete (consolidadas num ciclo por serem enxutas — decisão do usuário de reduzir cerimônia)
  Commits 6909f3f (FolderScanner) + a507ae6 (ArchUnit). Re-review clean após 4 correções:
  UncheckedIOException do walk lazy -> ScanException; teste de ordenação que de fato discrimina
  (verificado por mutação); nova regra presentationDoesNotSeeInfrastructure; allowEmptyShould por
  regra em vez de global (archunit.properties removido) + D-015 reescrita.
  REJEITEI a sugestão do revisor de proibir presentation->domain: quebraria o B4, tipos de domínio
  são o modelo compartilhado do MVP por design (spec).
  PENDÊNCIA REGISTRADA (D-015): remover .allowEmptyShould(true) das 2 regras ao fim do Bloco 4.
Task 11: complete (commit 49a5480, aprovada após 2 rodadas de correção — XmlMetadataParser)
  ACHADO DE SEGURANÇA no plano: SUPPORT_DTD=false do StAX NÃO rejeita DOCTYPE (só deixa de processá-lo,
  ainda emitindo o evento DTD). Parser passou a rejeitar o evento explicitamente. Revisor confirmou
  independentemente com probe de 15 vetores; sem XXE/billion-laughs remanescente. Registrado em D-017.
  Outras correções: exceções cruas -> null; elemento vazio virava "" e bloqueava o valor real;
  índice ganhou faixas fechadas (antes, tudo após o último </det> herdava o item — e IBSCBSTot fica
  em <total>, então erro de totais seria rotulado "item N"); enviNFe multi-nota -> 5 metadados nulos
  (D-016); accessKey="" com Id="NFe"; <dest> no fixture armando o guard de contexto.
  Limitação documentada: D-018 (XML minificado colapsa o índice linha->item).
Task 12: complete (PR #2 mergeado, CI verde) — BLOCO 1 FECHADO
  /code-review medium rodado antes do PR: 5 achados, 4 corrigidos (o mais grave: pastas via symlink
  puladas em silêncio -> lote reportaria "sem problemas" para docs nunca lidos).
  NÃO corrigido de propósito: raiz casada sem namespace — exigir namespace transformaria XML sem
  namespace em "ilegível", quando o melhor é o XSD reprovar com mensagem oficial/linha/coluna.
  Decisão de produto do usuário: arquivos não-NF-e (evento/inutilização/SOAP/outro DFe) por ora só
  precisam ser informados como inválidos — comportamento atual já basta. Classificar a família do
  arquivo fica para pós-MVP.
  PENDÊNCIA DE UX para o Bloco 4: usuário imaginou grid de importados com ícone e botão "remover
  inválidos"; a spec tem drop -> progresso -> tabela de causas. Mostrar a tela antes de construir.

## Bloco 2 (branch bloco/2-motor-xsd, base b46f257)
Task 13: complete (commit 2b3bddf, review clean após fix — XsdErrorTranslator)
  ACHADO FISCAL do revisor: chave cvc-enumeration-valid.CST casava por nome de campo, mas CST existe
  no IBS/CBS (pattern, nunca dispara enumeration) E no ICMS legado (enumeration inline) — a mensagem
  só apareceria no caso em que está errada, mandando o contador à tabela errada. Chave removida.
  Também: teste de acentuação (verificado por mutação p/ Properties.load(InputStream)); NPE como
  controle de fluxo trocada por checagem explícita; cobertura de ação em branco após '|'.
Task 14: complete (commit amendado, aprovada após 3 rodadas — SchemaValidatorEngine)
  Rodada 1: assinatura por substring "Signature" era manipulável (SignatureXpto rebaixava REJECTION
  a INFO); chaves de tradução mortas (cvc-pattern-valid não nomeia elemento) e achados duplicados;
  OOM sem teto. Rodada 2: prefixo .2.4 ainda casava .2.4.a (que LISTA os esperados) — corrigido para
  .2.4.b exato; locale da JVM quebrava a extração inteira (Locale.ENGLISH não funciona, só ROOT).
  Rodada 3 (feita pelo controlador, limite de gasto): OOM por mensagem única gigante escapava do
  catch (Error != Exception) -> recusa preventiva 32 MB + captura de OutOfMemoryError; injeção de
  campo pelo valor -> padrões ancorados + última ocorrência (D-024).
  Decisões: D-019 (resolver de includes), D-020 (assinatura), D-021 (fusão faceta+portador),
  D-022 (tetos + OOM), D-023 (locale ROOT), D-024 (extração ancorada).
Tasks 15+16: complete (commits 30d9517 + a57c4bf) — fixtures e smoke de performance
  Fixtures derivadas dos XMLs REAIS que o usuário forneceu, anonimizadas (a chave de acesso embute
  o CNPJ: precisou ser regerada com DV recalculado). 500 docs em 774 ms (critério: < 2 min).
  VALIDAÇÃO CONTRA OS 13 XMLs REAIS: 12 documentos fiscais passam LIMPOS (zero falso positivo);
  o retEnviNFe é corretamente recusado com o nome do arquivo na mensagem.
  ACHADO DE PRODUTO CRÍTICO: IBSCBS e IBSCBSTot são minOccurs="0" no schema oficial. Uma NF-e de
  CRT=3 SEM NENHUM grupo IBS/CBS passa limpa no XSD — e é exatamente ela que a SEFAZ rejeita a
  partir de 03/08. O v0, como especificado (XSD puro), não cumpre a promessa central nesse caso.
  Levar ao usuário antes do PR.
Task 17: complete (PR #3 mergeado, CI verde) — BLOCO 2 FECHADO
  Validado contra 13 XMLs reais do usuário: 12 documentos fiscais LIMPOS, resíduo SOAP recusado.
MUDANÇA DE ROTEIRO (27/07): spec nova commitada (7367ba4) para a camada de previsão de rejeição.
  Descoberta: XSD declara IBSCBS como minOccurs=0, então CRT=3 sem o grupo passa limpo — e é a
  rejeição 1115/UB12-10 que liga em 03/08/2026. NT tem 277 regras (129 presença, 77 cálculo,
  44 tabela). Estratégia aprovada pelo usuário: regras dirigidas pelas tabelas oficiais da
  Calculadora (161 cClassTrib carregam possuiPercentualReducao, exigeGrupoDesoneracao,
  tiposDfeClassificacao, percentuais) -> ~11 mecanismos em vez de 129 regras codificadas.
  Escopo cortado: só IBS/CBS, modelos 55/65. IS e demais DFe fora.
  LACUNA: tabela de CST da Calculadora não traz ind_gIBSCBS/ind_gRed (estão na planilha do portal).
  PENDENTE: D-012 volta à pauta (valores exigem a Calculadora rodando, pacote sem licença).
  Blocos 3-5 do plano original (use case/CSV, UI, release) precisam ser reordenados após esta spec.

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

### PARADA — 28/07/2026, fim do bloco 3 (Tasks 18 e 19). Pronto para push/PR, aguardando decisão
  de fechamento do dono do projeto (mesmo padrão dos blocos 6 e 7: Task 20 — suíte, relatório,
  liberação).

HEAD: 1836b25 na branch `bloco/3-usecase-csv` (nascida de main pós-bloco-7, commit 4e85fbf).
  Árvore limpa (`./gradlew clean test --console=plain`: BUILD SUCCESSFUL, 332 testes, 0 falhas),
  nada pushado. Duas tasks completas e revisadas independentemente (PASS/PASS nas duas). D-043
  registra a decisão do bloco. Task 20 (fechamento/PR/push) NÃO executada — aguardando liberação
  explícita do dono do projeto. Nenhuma pergunta em aberto sem resposta: as duas ambiguidades que
  o dispatch da coordenação sinalizou (o que significa "estruturalmente válido o bastante para
  extrair" e se `BatchReport` precisa de novos campos) foram resolvidas e documentadas em D-043,
  não deixadas como pendência.
