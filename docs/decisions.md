# Decisões

Log ADR-lite. Cada entrada: **Decisão**, contexto curto e consequência. Mais recentes no topo.
Template no fim. Decisões D-001..D-014 nasceram no brainstorm de 26/07/2026 (spec
[`superpowers/specs/2026-07-26-validador-lote-rtc-design.md`](./superpowers/specs/2026-07-26-validador-lote-rtc-design.md)).

## D-052 — Resultados guardam a geração imutável do runtime que os produziu (30/07/2026)

Cada validação usa um `ValidationRuntime` imutável, composto pelo caso de uso e por
`RuntimeBases` legível (versão e proveniência de schemas e tabela, além da geração). Ao concluir
um documento, o presenter grava esse valor no `WorkspaceDocument`; pendente, validação cancelada
e falha sem resultado não recebem identidade. Uma troca posterior de runtime não recalcula,
reetiqueta nem consulta estado global para alterar resultados já exibidos.

`ValidationRuntimeFactory` é o dono thread-safe da sequência: emite gerações estritamente
crescentes e evita que o caminho de composição produza uma regressão manual. O contrato antecede
a publicação atômica: nesta etapa ele conserva os construtores legados com uma identidade
provisória, e a composição definitiva com as bases ativas será responsabilidade do composition
root. A consequência é que a UI sempre poderá mostrar a proveniência que realmente gerou cada
achado, mesmo quando a sessão passar a aceitar uma geração posterior.

## D-051 — Schemas runtime vêm de canal próprio, curado e assinado (30/07/2026)

O runtime de schemas NF-e/NFC-e aceita somente releases completas do canal público próprio,
curadas e assinadas pelo projeto. O rótulo “pacote mais recente” de uma fonte externa não prova
closure, compatibilidade nem vigência; a curadoria revisa o diff e compila a closure antes de
publicá-la. A base embarcada e a última `current` íntegra permanecem os fallbacks. Esta decisão
substitui D-047 e D-049 **somente no canal runtime de schemas**: SVRS continua fonte de pesquisa e
proveniência, e a tabela fiscal conserva seu canal SVRS independente.

O manifesto assinado com Ed25519 autentica o conteúdo aprovado, não apenas o host HTTPS. A
`releaseSequence` estritamente crescente é a ordem anti-rollback; `publishedAt` é auditoria, não
critério de confiança. Assinatura inválida, hash divergente, redirect não permitido, ZIP inseguro,
estrutura incompatível, sequência menor ou sequência igual com identidade assinada divergente
falham antes de substituir `current`. Sequência igual só significa “em dia” quando hash do ZIP e
identidade canônica de `signed` coincidem com a release ativa.

O ACBr é evidência manual de curadoria, nunca transporte nem fallback runtime. Para verificar que
a revisão observada toca o diretório relevante, o curador executa exatamente:

```bash
svn log --xml -v -l 1 https://svn.code.sf.net/p/acbr/code/trunk2/Exemplos/ACBrDFe/Schemas/NFe/
```

Uma revisão em outra área do ACBr não prova mudança nesse diretório. O bootstrap inicial foi
publicado em `vBaggio/validador-lote-rtc-bases`: endpoint GitHub Pages, ZIP, `stable.json`,
`keyId` `schemas-2026-01` e chave pública Ed25519 foram revisados e embarcados. A manutenção do
canal continua exigindo revisão humana de cada nova release; não há fallback para SVRS.

## D-050 — Consulta prepara; usuário ativa; engines mudam após reinício (30/07/2026)

Schemas e tabelas são consultados e validados independentemente em staging. Uma confirmação global
ativa todas as candidatas válidas; falha de uma fonte preserva sua base anterior sem impedir a
outra. Consulta pode coexistir com validação de documentos, mas confirmação e ativação aguardam o
fim do lote. Rodapé e diálogo observam o mesmo snapshot, e engines só carregam as novas bases no
reinício para que uma sessão nunca misture referências.

A exclusão entre ativação e validação é uma **admissão atômica**, não uma suposição baseada no
próximo evento visual: ao reservar uma aplicação, nenhuma nova validação inicia até a operação
terminar, inclusive se o executor recusar o trabalho. Snapshots têm revisão monotônica; entrega
fora de ordem e callbacks de observadores com falha não podem fazer a interface regredir nem ficar
em `APPLYING` sem evento terminal.

A abertura do diálogo application-modal é enfileirada para um ciclo posterior da EDT. Assim, ela
não reentra nem bloqueia a drenagem síncrona que entrega o snapshot `APPLYING` e seus terminais;
o mesmo snapshot segue sendo a única entrada do rodapé e do diálogo. Falha de listener já no
evento `CHECKING` também é terminalizada e não deixa o coordenador ocupado: a fonte mostra o erro
recuperável e pode receber uma nova consulta.

O prazo HTTP cobre conexão, cabeçalhos **e o corpo inteiro**. Ao expirar, a leitura assíncrona do
corpo e a requisição são canceladas; exceder o limite de tamanho também cancela a assinatura antes
de manter o restante da resposta em memória. Falha de uma fonte fica visível mesmo se a outra está
em dia, e a rejeição do executor ao iniciar a ativação vira feedback recuperável, sem novo prompt
automático nem operação fantasma.

O retorno bem-sucedido de `apply` significa que a referência física `current` já mudou. Por isso o
reinício fica latched até o processo encerrar mesmo se persistir ou publicar o evento terminal
falhar; a falha continua visível, a candidata não é reaplicada cegamente e uma nova tentativa exige
consulta fresca. Essa assimetria deliberada privilegia continuidade e transparência: uma fonte que
falha conserva a última base íntegra, enquanto uma ativação consumada jamais é escondida do usuário.

## D-049 — SVRS como pesquisa histórica de schemas; canal runtime substituído por D-051 (30/07/2026)

O Portal de Documentos da SVRS foi investigado como fonte de pesquisa: `NFE/Documentos` lista
pacotes e `NFE/DownloadArquivoEstatico` entrega ZIPs HTTPS. Ele **não** é canal operacional nem
fallback runtime para schemas desde D-051. A descoberta continua útil para comparar disponibilidade
e documentar proveniência, sem autorizar download ou ativação pelo aplicativo.

Essa mudança não confunde disponibilidade com vigência. Em 30/07/2026, a SVRS ainda lista como
pacote completo mais novo o `PL_010b_NT2025_002_v1.30`, anterior ao perfil `010e_v1.02` embarcado.
Logo, uma entrada só é candidata se declarar um perfil NF-e/NFC-e compatível e estritamente mais
novo que a base ativa; pacote antigo, nome inesperado, ZIP vazio ou closure inválida é consulta
sem atualização, nunca downgrade. A aplicação continua com a última base íntegra.

Nesta versão, “compatível” significa exclusivamente a família `010e`. Uma futura família, como
`010f`, não é silenciosamente promovida por ordenação de nome: exige curadoria e task de manutenção
para conferir roots, closure, fixtures e vigência antes de uma release assinada do canal próprio.

O SVN do ACBr continua espelho técnico para inspeção humana. Ele não declara vigência nem perfil
oficial, portanto não ativa schemas automaticamente. O canal próprio com manifesto assinado e
promoção humana, então considerado futuro, é a política adotada por D-051. A base embarcada já é o
fallback offline aprovado.

## D-048 — Atualização externa é consultiva no lote; sem fallback automático para schemas (29/07/2026)

O rodapé abre a tela discreta **Fontes externas**, que mostra somente metadados locais de schemas,
tabelas e da Calculadora futura: versão/snapshot ativo, origem, hash abreviado, datas de atualização
e consulta e resultado recuperável. A ação manual força a mesma rotina de background do boot, mas
o coordenador aceita somente uma execução por vez. Ela não mostra nem transmite XML, chave, CNPJ
ou conteúdo da área de trabalho; falha é estado consultável, nunca modal que interrompe o lote.

Uma candidata que passa o canal autorizado é instalada para uso no **próximo boot**. O lote atual
conserva os engines que foram montados no bootstrap, impedindo que documentos de uma mesma sessão
recebam bases diferentes. Para schemas, D-051 define o canal curado e assinado; ACBr e SVRS servem
somente para pesquisa/proveniência e não autorizam fallback automático, transporte SVN silencioso
ou ativação local.

As tasks Gradle históricas de sobrescrever resources ficaram bloqueadas de propósito. Elas não são
um caminho de atualização do usuário: qualquer nova base embarcada é manutenção de release, feita
em staging, validada e revisada por diff antes de alterar `src/main/resources`. A Calculadora é
apenas inventário para a v1 no catálogo; não é baixada, executada nem fonte de schemas no v0.

## D-047 — Proveniência da closure embarcada 010e_v1.02; runtime substituído por D-051 (29/07/2026)

O Portal Nacional lista `010e_v1.02` (NT 2025.002 v1.40, NT 2026.002/003), publicado em
10/07/2026, como versão oficial em uso. Seu download não pôde ser recuperado diretamente por
502/captcha. A closure mínima usada pelo produto foi transportada do SVN ACBr r47146 (13/07/2026),
com hashes gravados no manifesto; ACBr é espelho técnico, não autoridade. Portanto o repositório
registra a vigência do Portal e a identidade do payload ACBr, sem alegar equivalência byte a byte
ao ZIP oficial. É registro da base embarcada, não política de aquisição runtime: D-051 a substitui
somente nesse ponto. A atualização preserva somente NFe/nfeProc/enviNFe e não muda regras de
negócio nem a decisão D-040 sobre modelos.

## D-046 — Catálogo local detecta corrupção operacional, não autentica escrita da mesma conta (29/07/2026)

O canal de artefatos instala candidatos por staging, compila a árvore XSD com resolver confinado e
guarda manifesto/hash para auditoria e detecção de corrupção acidental. No boot, uma base local só
é aberta se a referência, a árvore e a compilação permanecerem válidas; qualquer falha devolve a
base embarcada. Isso não autentica um payload contra quem possui escrita na mesma área de dados:
essa pessoa pode alterar XSD e manifesto juntos. Sem assinatura verificável do publicador ou
keystore fora dessa permissão, malware local está fora do modelo de ameaça. A aquisição posterior
continua responsável por TLS, allowlist e proveniência da fonte; hash local jamais é alegado como
prova de autoria.

## D-045 — Área de trabalho antes da validação; documentos como visão primária (29/07/2026)

A tela deixa de validar no instante em que recebe a seleção. Importar uma pasta ou XML individual
faz somente a leitura segura dos metadados mínimos e forma um **lote de trabalho**; o usuário pode
adicionar mais arquivos, excluir linhas, limpar o lote e só então acionar **Validar pendentes**.
Durante a execução sequencial em background, a linha corrente muda para "validando", a barra de
progresso e o contador avançam na própria área de trabalho, o botão principal é substituído por
"Interromper" e toda mutação do lote (incluindo drag-and-drop) fica bloqueada. O cancelamento é
cooperativo e conserva os resultados já obtidos; linhas não iniciadas permanecem pendentes.

**A visão principal passa a ser documento, não causa agrupada.** A grade superior, maior, mostra
status, chave, emitente/CNPJ, modelo, série, número e explicação; sua ordem padrão é emitente,
modelo, série e número. A grade inferior mostra os problemas do documento selecionado. Isso atende
à tarefa operacional real: localizar e corrigir documentos, sem perder o detalhe de cada achado.
As cores/ícones são estado de interface, não nova decisão fiscal: cinza = pendente, azul = em
validação, verde = sem achado, vermelho = rejeição, amarelo = atenção e branco = não avaliado.
"Remover válidos" preserva os documentos que ainda exigem atenção.

XML que falha na leitura segura não é inserido na grade e é informado em diálogo após a importação;
se ficar ilegível antes da validação, recebe o mesmo tratamento. A tela usa FlatDarkLaf, Roboto,
janela maximizada e rodapé discreto; a permanência em modo escuro é escolha de produto, não
preferência do SO. O controle visual de pré-emissão foi retirado por ora; o modo padrão continua
ligado no caso de uso, sem mudar classificação fiscal.

**CSV permanece no backend, mas está deliberadamente indisponível na UI.** O botão foi removido
porque o formato/uso precisa de nova revisão; não prometer exportação no README ou fluxo de uso até
uma task explícita reativá-la. Isto não remove o `CsvExporter` nem altera seus contratos/testes.
Para o empacotamento, o SVG do ícone da janela não basta: a Task 25 deve gerar/usar o ícone nativo
adequado ao `jpackage` (ao menos `.ico` no Windows).

## D-044 — Cerimônia proporcional ao risco fiscal; harness seco (28/07/2026)
O fluxo de bloco completo (brief, revisor relendo a fonte, sonda de mutação, ledger extenso) fica
reservado para código de **julgamento fiscal** (regra de rejeição, tabela, indicador, mensagem
oficial). Código de orquestração/plumbing (wiring, exportador, caso de uso que só invoca engines já
vetadas) recebe revisão mais leve, sem reabrir NT/XSD; doc/config/refactor mecânico continua sem
processo. O mesmo critério guia o modelo do implementador (caro para julgamento/ambiguidade,
barato para padrão repetido). Tasks mecânicas da mesma natureza (ex.: N instâncias do mesmo
mecanismo) se fundem em uma. Motivo: o processo pesava igual para todo código, e o custo real do
projeto está concentrado na releitura de arquivos inteiros e na redescoberta de estado entre
sessões, não na prosa da resposta do agente (avaliado e descartado: comprimir a prosa de saída,
tipo "Caveman", não ataca esse custo). Harness também secado: `docs/superpowers/plans/done/`
recebe planos/blocos já mergeados por inteiro (movidos, não apagados); `.superpowers/sdd/CURRENT.md`
(agora versionado, com exceção no `.gitignore`) é o ponteiro rápido de bloco/task/branch/próximo
passo, lido antes do ledger completo; o ledger teve os blocos 0-2 (fechados, sem judgment em aberto)
compactados para um parágrafo cada, com achados que já viraram D-0XX citados por número em vez de
repetidos. Registrado em `docs/workflow.md` §1.1 e §8.

## D-043 — `ValidateBatchUseCase` liga o `RuleEngine` ao lote sem gate por schema; `BatchReport` não ganha contador de desfecho ainda (28/07/2026)

Bloco 3 (Task 19), plano escrito antes dos blocos 6/7 existirem — quando a única fonte de achado
era `SchemaValidatorEngine`. Hoje há também um `RuleEngine` completo (30 rejeições/não-avaliados da
NT), sem nenhum consumidor de produção. Duas decisões:

**1. Schema e `RuleEngine` rodam sempre juntos, sem um gatear o outro.** Por arquivo, se
`XmlMetadataParser.parse` tiver sucesso, tanto `SchemaValidatorEngine` quanto
`TaxGroupExtractor`+`RuleEngine` rodam — mesmo quando o schema já encontrou erro. Os dois leitores
de metadado (`XmlMetadataParser`, `TaxGroupExtractor`) são StAX tolerantes por contrato (campo
ilegível vira `null`, nunca inventam dado), e o `RuleEngine` já devolve `NaoAvaliado`, nunca
`Rejeitado`, quando falta um dado que uma regra pressupõe (cascata de `Precondition`, D-032).
Verificado no código, não suposto: `GroupRequiredRule` (a raiz da cascata) lê `crt` nulo e para em
`NaoAvaliado` antes de qualquer exceção; o mesmo vale para `CompraGovForbiddenInNfceRule` com
`model` nulo. O caso mais extremo — `enviNFe` multi-nota, que zera `crt`/`finNFe`/`issueDate`/
`model` inteiros por documento (D-016) — cai exatamente nesse caminho: confissão (`NaoAvaliado`),
nunca acusação. Condicionar a segunda fonte à ausência de erro de schema esconderia rejeições
genuínas de documentos que passam "quase" no XSD, sem ganho de segurança correspondente.

**2. `BatchReport` não ganha contadores de conforme/rejeitado/não-avaliado nesta task.**
`documentsWithFindings` e `documentsUnreadable` já são genéricos (contam por `Finding::source` e por
`kind == UNREADABLE`, respectivamente) e passam a cobrir achados de `REJECTION_RULE`/
`NOT_EVALUATED` sem qualquer mudança de código. O que ficaria de fora — uma classificação **por
documento** em três baldes para a UI mestre-detalhe — é decisão de apresentação (que bucket um
documento com só achados `NOT_EVALUATED` cai?) que este bloco não precisa resolver para entregar o
que faltava (ligar o motor ao lote), e que arrisca fixar semântica errada num record caro de mudar
sem o consumidor (a tela do bloco 4) para validar contra caso real. Registrado como decisão
explícita — não reabrir como lacuna esquecida; é do bloco 4.

**Verificação por mutação:** comentada a chamada `ruleEngine.evaluate(...)` dentro de
`validateOne`; `ValidateBatchUseCaseTest#rejectionRuleFindingsAreWiredIntoTheReport` caiu sozinho,
os demais 8 testes da classe continuaram verdes. Restaurado, suíte completa (332 testes)
reconfirmada verde.

## D-042 — `pRedutor` vira campo de documento; a exceção binária da 1032/1007/1028 é decidida sem aritmética (28/07/2026)

Implementa o mecanismo 2 de `candidatas-rejeicao-pos-b6.md` (brief `task-gred-indevido`): as três
rejeições "grupo de Redução de Alíquota informado indevidamente" (1032/UB26-10, 1007/UB45-10,
1028/UB64-10) — o lado "informado" que espelha `ReductionGroupRule` (1033/1074/1079, lado
"ausente"). Nova classe `ReductionGroupForbiddenRule`, três instâncias em `RuleEngine.BINDINGS`
com a mesma precondição `CST_PRESENT`+`CST_IN_TABLE` das outras regras de `ind_gRed`.

**1. `pRedutor` é campo de `FiscalDocument`, não de `ItemTaxGroup`.** Confirmado no XSD
(`DFeTiposBasicos_v1.00.xsd:1144-1163`, tipo `TCompraGov`): filho direto de `gCompraGov`
(sequence `tpEnteGov, pRedutor, tpOperGov`), que por sua vez é filho de `infNFe/ide`
(`leiauteNFe_v4.00.xsd:499`) — mesmo nível de `hasCompraGov`, e não do item. `XmlMetadataParser`
ganhou a captura (mesmo padrão de `finNFe`/`tpNFDebito`: `isFirst(stack, "pRedutor",
"gCompraGov")`, convertido para `BigDecimal` com o mesmo contrato de "ilegível vira null" que
`TaxGroupExtractor.decimal()` já usa). Conferido que "pRedutor" não colide com outro elemento: o
`gTribCompraGov` do item (`TTribCompraGov`, `DFeTiposBasicos_v1.00.xsd:1097`) não tem campo de
mesmo nome — só `pAliqIBSUF/pAliqIBSMun/pAliqCBS` e seus `vTrib*`. `FiscalDocument` ganhou
`pRedutorCompraGov` (`BigDecimal`) ao lado de `hasCompraGov`.

**2. A exceção é decidida com dois fatos brutos, sem aritmética.** Texto literal da NT (conferido
em `tmp/NT_2025.002_v1.50_RTC_NF-e_IBS_CBS_IS.md`, item UB26-10 55/65, idêntico nas três esferas):
"Exceção: Percentual de redução da alíquota em compra governamental (tag: `gCompraGov/pRedutor`)
informado e `gIBSUF/gRed/pRedAliq` igual a zero." Os dois fatos — `pRedutor` legível e `pRedAliq`
da esfera igual a zero — não envolvem comparação contra `cClassTrib` nem contra a fórmula de
`pAliqEfet`; por isso a decisão fica inteira em `ReductionGroupForbiddenRule`, sem tocar
`ReductionPercentageRule` nem abrir a aritmética completa de compra governamental (que continua
fora do escopo, débito da D-030).

**3. Leitura do caso intermediário: os dois fatos confirmados valem `Conforme`, não `NaoAvaliado`.**
O brief citava a leitura conservadora de D-030 ("sem capturar `pRedutor`, o caso sai como
`NaoAvaliado`") como piso, mas pedia para decidir agora que o dado é capturado. Com os dois fatos
literais da exceção confirmados — `pRedutor` legível e `pRedAliq=0` na esfera —, não sobra
ambiguidade: a NT descreve a exceção como presença/valor, não como cálculo, e não há dado faltando
para hesitar. `GroupForbiddenRule` (1021) e `DiferimentoForbiddenRule` (1029/1083/1090) já
resolvem seus pares "ausência aqui é conformidade, não omissão" da mesma forma — `Conforme`, não
um terceiro estado por cautela extra. `NaoAvaliado` fica reservado para quando falta um dos dois
fatos (pRedAliq ilegível, ou `gCompraGov` presente com `pRedutor` ilegível): nesses casos, sim, não
dá para confirmar a exceção, e a leitura nunca vira `Rejeitado`.

**4. Sem o gate de `ind_gIBSCBS=0` que `ReductionGroupRule` tem.** `ReductionGroupRule` isenta CST
com `ind_gIBSCBS=0` por cláusula literal própria da UB26-20. O texto da UB26-10 (citado no brief)
não traz essa cláusula, e o par estrutural mais próximo — `DiferimentoForbiddenRule`, mesma forma
"forbidden" sobre indicador de CST — também não tem esse gate (confirmado: a NT não traz exceção
alguma para 1029/1083/1090). Seguido o padrão do par mais próximo em vez de importar a exceção da
`ReductionGroupRule` sem base textual.

**5. Dado reaproveitável, não reimplementável, quando a aritmética de compra governamental
entrar.** `pRedutorCompraGov` é o mesmo dado bruto que a família 1034/1046/1063
(`ReductionPercentageRule`) já deixou como débito documentado (D-030: `pAliqEfet = pAliq ×
(1 - pRedAliq/100) × (1 - pRedutor/100)`). Capturá-lo aqui não implementa essa fórmula — só a
presença/valor bruto, para a pergunta binária desta task. Quando a aritmética entrar, o campo já
existe em `FiscalDocument`; não precisa de nova extração.

**Verificação por mutação** (sonda do brief: comentada a checagem de `pRedutor`/`pRedAliq=0`, suíte
alvo `TableRulesTest` rodada, três testes caíram —
`governmentPurchaseWithZeroPercentageAndReadableRedutorIsTheExceptionConfirmed`,
`governmentPurchaseWithoutReadableRedutorIsNotEvaluated`,
`governmentPurchaseWithUnreadablePercentageIsNotEvaluated` —, arquivo restaurado, `git status`
limpo). Confirma que o teste positivo da exceção protege o caso de compra governamental legítima,
não só o óbvio.

## D-041 — Bloco 7: dezesseis rejeições de presença por indicador CST e por modelo (28/07/2026)

Implementa os quatro mecanismos do brief `task-presenca-indicador-modelo` (mecanismos 1, 3, 4 e 5
de `candidatas-rejeicao-pos-b6.md`): diferimento por indicador CST (1029/1030/1044/1061/1083/1090),
devolução de tributo proibida (1111/1112/1187), `gTribCompraGov` (1141/1144) e grupo proibido no
modelo 65 (1006/1049/1138/1165/708, confirmado empiricamente por D-040).

**1. `permiteDiferimento` → `exigeDiferimento`: mapeamento já estava correto, só o nome mentia.**
O brief pediu para conferir antes de usar. Conferido: `cst-cclasstrib.json` mapeia o campo
diretamente do `IndDiferimento` bruto da SVRS (`docs/pesquisa/dados/cst-cclasstrib-svrs.json`),
sem inversão — `true` só em 510 e 515 (os dois CSTs de "Diferimento"), exatamente onde `IndDiferimento`
é `true` na fonte. O valor está certo; o nome sugeria "opcional quando true, ausente quando false",
mas a NT lê o indicador nos dois sentidos (exige quando =1, veda quando =0) — a mesma forma binária
de `exigeGrupo`/`exigeReducao`, que já usam o prefixo "exige". Renomeado para `exigeDiferimento`
neste commit (`CstEntry`, `FiscalTables`, `cst-cclasstrib.json`, teste), sem mudar nenhum valor.
Teste novo (`FiscalTablesTest#onlyTwoCstsRequireTheDeferralGroup`) afirma a contagem contra a base
real, no mesmo espírito de `onlyThreeCstsRequireReductionGroup`.

**2. Classe genérica cobre 7 das 8 instâncias do catálogo do brief, não 8.** `PresenceForbiddenRule`
(presença de uma tag/grupo, opcionalmente restrita a um modelo) serve 1111, 1112, 1187, 1049, 1138,
1165 e 708 — todas `RejectionRule` (item). 1006 segue a mesma forma mas é `DocumentRejectionRule`
(`gCompraGov` é de `ide`, D-030): unificar as duas interfaces custaria mais em acoplamento do que
economiza para uma única instância, e o próprio brief já resolvia isso no passo 6 ("1006 é de
documento"). `CompraGovForbiddenInNfceRule` fica separada, mesma forma, interface diferente.

**3. 1138 e 1165 disparam sempre juntas — não é redundância a evitar, é a estrutura do XSD.**
`tpCredPresIBSZFM` é campo **obrigatório** dentro de `gCredPresIBSZFM` (`TCredPresIBSZFM`,
`DFeTiposBasicos_v1.00.xsd:1274`, sem `minOccurs="0"`). Não existe XML XSD-válido com o grupo e sem
o campo. As duas continuam capturadas por booleans independentes (`hasCredPresIbsZfm` e
`hasTpCredPresIbsZfm`, por instrução direta do brief — "não reaproveite o boolean de 1138 por
suposição"), e a fixture de corpus (`r1138-credpresibszfm-nfce.xml`) afirma as duas rejeições juntas
em vez de fingir isolamento impossível.

**4. 1141/1144 não entram no corpus de fixtures — cobertura só por unidade.** Isolar uma delas
exige, ao mesmo tempo, um CST com `ind_gIBSCBS=1` (para a exceção da 1141 não afastar a acusação) e
`gCompraGov=true` no documento. Mas `ReductionGroupRule` (D-030) já tem gatilho próprio: sob compra
governamental, `gRed` passa a ser exigido mesmo com `ind_gRed=0`. Duas saídas, as duas ruins: omitir
`gRed` dispara 1033/1074/1079 de verdade (não é o que a fixture quer isolar); incluir `gRed` faz
`ReductionPercentageRule` devolver `NaoAvaliado` **incondicionalmente** nas três esferas (o cálculo
sob compra governamental depende de `gCompraGov/pRedutor`, fora do escopo, D-030) — três achados
extras em toda fixture de 1141/1144, positiva ou controle. Nenhuma combinação XSD-válida escapa
disso. Ficam cobertas só em `TableRulesTest` (16 testes dirigidos, incluindo a exceção do
`ind_gIBSCBS=0` e a fronteira 1141/1144), sem fixture de corpus — registrado aqui para não ser lido
como esquecimento numa sessão futura.

**5. Wiring no `RuleEngine`.** As seis regras de diferimento entram em `BINDINGS` com
`CST_PRESENT`+`CST_IN_TABLE` (mesma precondição de `GroupForbiddenRule`/`GroupRequiredByCstRule`,
cujo padrão seguem). As sete instâncias de `PresenceForbiddenRule` e `ComprasGovComposicaoForbiddenRule`
entram sem precondição — nenhuma consulta à tabela oficial. `ComprasGovComposicaoRequiredRule` (1141)
entra com `CST_PRESENT`+`CST_IN_TABLE`, pela exceção. `CompraGovForbiddenInNfceRule` (1006) entra em
`DOCUMENT_RULES`, junto de 1118/1119.

**Verificação por mutação** (seis sondas, todas capturadas — comentado, suíte alvo falhou, restaurado,
`git status` limpo): decisão final de `PresenceForbiddenRule.evaluate` (10 testes caem); decisão
final de `DiferimentoRequiredRule.evaluate` (3 testes); leitura de `exigeDiferimento` em
`FiscalTables` (8 testes); decisão final de `ComprasGovComposicaoRequiredRule.evaluate` (1 teste);
captura de `gDif` no `TaxGroupExtractor` (2 testes); decisão final de
`CompraGovForbiddenInNfceRule.evaluate` (2 testes).

## D-040 — Validado: o schema não é model-aware; `grupo.xsd` é scaffolding morta, não meio-caminho (28/07/2026)

Achado da reconciliação de `docs/pesquisa/candidatas-rejeicao-pos-b6.md` (D-039), aprofundado aqui
por pedido explícito de validação. Registra o estado real, para não ser reaberto como surpresa numa
sessão futura nem "corrigido" na direção errada.

**Confirmado empiricamente, não só por leitura de código.** O dono do projeto validou um XML
sintético — NFC-e (`mod=65`) estruturalmente válido (`xmllint --schema nfe/nota.xsd`, sem erro),
com `gCredPresOper` preenchido — direto no validador oficial da SVRS
(<https://dfe-portal.svrs.rs.gov.br/NFE/ValidadorNfe>). Resultado: **"Schema XML: Nenhum erro
encontrado"** e, na seção separada "Regras de Negócio", **1049 — "Não é permitido o uso de Crédito
Presumido na NFC-e modelo 65 [nItem:1]"**. Prova direta de que a própria SEFAZ resolve isso na
camada de regras de negócio, não na estrutural — a hipótese abaixo deixa de ser dedução e passa a
ser fato observado.

**O que está confirmado, lendo o código, não supondo:**

1. `SchemaValidatorEngine.SCHEMA_DIR` (`SchemaValidatorEngine.java:46`) está fixo em
   `"/schemas/nfe/"`. Todo documento — `mod=55` ou `mod=65` — é validado contra `nfe/nota.xsd`.
   Não há nenhuma ramificação por modelo no motor de schema.
2. `nfe/originais/` e `nfce/originais/` são **byte-idênticos** (`diff` vazio nos dois arquivos
   grandes, `DFeTiposBasicos_v1.00.xsd` e `leiauteNFe_v4.00.xsd`) — são a mesma extração da
   Calculadora, duplicada nas duas pastas, e o `IBSCBS` do item é sempre tipado como `TTribNFe`
   (que permite `gTransfCred`, `gAjusteCompet`, `gEstornoCred`, `gCredPresOper`,
   `gCredPresIBSZFM`) em ambas — nunca como `TTribNFCe` (que não permite nenhum desses grupos,
   restando só `gIBSCBS`/`gIBSCBSMono`).
3. **`grupo.xsd` não é meio-caminho de correção — é scaffolding morta do commit fundador**
   (`e4c768a`, bloco 0, 26/07/2026). Existem duas cópias, `nfe/grupo.xsd` e `nfce/grupo.xsd`,
   ambas com o comentário de autoria "Luis Augusto, 02/07/2025" (predata o projeto — veio junto da
   extração de schemas, não foi escrito por um agente daqui). Nenhuma das duas é incluída por
   nenhum `nota.xsd`; nenhuma é referenciada em `src/main/java`. E mesmo que fossem: cada
   `grupo.xsd` declara um `infNFe` **parcial** — só `det`/`total` (e, na versão NF-e, um `ide` com
   só `gCompraGov`) — sem `emit`, `dest`, nem o resto da nota. Usá-lo no lugar do `infNFe` completo
   de `leiauteNFe_v4.00.xsd` derrubaria a validação de qualquer documento real, não a tornaria mais
   estrita. Não é um fix quase pronto; é um experimento anterior à decisão de usar a extração oficial
   completa (a mesma decisão que fundamenta D-005), abandonado sem limpeza.

**Por que a correção não é "tornar o motor de schema model-aware".** Restringir corretamente
`TTribNFCe` via XSD exigiria reescrever a árvore inteira de `infNFe` para o modelo 65 (XSD 1.0, sem
`xs:redefine`/`xs:override` civilizados no caminho do Xerces já em uso) só para trocar um tipo
aninhado várias camadas abaixo — alto custo, alto risco de divergir da extração oficial que D-005
já garante correta. **A correção certa é a mais barata, e já está escolhida**: implementar as seis
rejeições de "grupo proibido no modelo 65" na camada de regras — 1006, 1049, 1138, 1165, 708 e 1187,
já priorizadas como mecanismo 5 em `candidatas-rejeicao-pos-b6.md` — em vez de mexer no motor XSD.
O mecanismo (`DocumentRejectionRule`/`RejectionRule` + `model` já em `FiscalDocument`) já existe.

**Consequência aceita, até essa implementação:** hoje, um documento `mod=65` com `gTransfCred`,
`gAjusteCompet`, `gEstornoCred`, `gCredPresOper`, `gCredPresIBSZFM`, `gCompraGov` ou
`DFeReferenciado` (elementos que a norma proíbe em NFC-e) **passa na validação estrutural** —
falso negativo silencioso, não falso positivo: o validador não acusa nada, mas também não pega o
erro que a SEFAZ pegaria. Não é urgente do jeito que o gap de XSD desatualizado (auditoria de
28/07/2026, produção 03/08/2026) é — é lacuna de cobertura, não acusação indevida — mas é real e
tem correção barata já mapeada.

**Débito de limpeza, sem risco:** `grupo.xsd` (as duas cópias) pode ser removido do repositório
sem afetar nada em runtime — nada o referencia. Não removido nesta sessão por não ser o pedido; fica
registrado para quando alguém mexer nesta árvore de novo.

## D-039 — 1118/1119 introduzem `DocumentRejectionRule`, avaliada por documento, não por item (28/07/2026)

Task fora do plano original, priorizada em `docs/pesquisa/candidatas-rejeicao-pos-b6.md` §"Lote 1"
por ser a única família do catálogo pós-bloco-6 que é presença pura, sem aritmética. Texto literal
conferido no PDF `NT_2025.002_v1.50` (p. 72, Grupo W03), não só no brief:

> **W34-10** (1118): *"Se grupo de totais do IBS e da CBS (tag: total/IBSCBSTot) informado:
> Nenhum item possui IBS / CBS informado (id: UB12, tag: IBSCBS)"* → Rejeição: Total de IBS e CBS
> informado indevidamente.
>
> **W34-20** (1119): *"Se grupo de totais do IBS e da CBS (tag: total/IBSCBSTot) não informado:
> Pelo menos um item possui IBS / CBS informado (id: UB12, tag: IBSCBS)"* → Rejeição: Total de IBS
> e CBS não informado.

Sem exceção e sem gatilho de vigência nas duas linhas da tabela. A remissão "(id: UB12, tag:
IBSCBS)" resolve a ambiguidade que o brief sinalizava: é o mesmo elemento que a UB12-10 (1115)
observa — o **invólucro** `IBSCBS`, não o `gIBSCBS` interno (D-027) — logo as duas regras leem
`ItemTaxGroup.hasIbsCbsGroup()`.

**Encaixe no motor.** As onze regras do primeiro corte (D-026) são todas por item:
`RejectionRule.evaluate(RuleContext)`, e `RuleContext` carrega um único item. 1118/1119 comparam
duas presenças de escopos diferentes — o grupo de totais (documento) contra a presença do
invólucro em **qualquer** item da lista — e não cabem nesse contrato sem distorcê-lo. Considerei
forçá-las a receber `RuleContext` mesmo assim (com a lista de itens pendurada nele) e descartei:
isso obrigaria as onze regras existentes e todo teste que constrói `RuleContext` a carregar uma
lista que nunca leem, só para duas regras novas. Criei uma segunda interface,
`DocumentRejectionRule.evaluate(FiscalDocument, List<ItemTaxGroup>)`, e uma segunda (pequena)
lista de bindings no `RuleEngine`, avaliada uma vez por documento em `RuleEngine.evaluate`, fora do
laço por item e fora da cascata de `Precondition` (que é toda sobre disponibilidade de dado de
item — CST, cClassTrib — e não tem o que dizer sobre uma comparação de presença entre documento e
itens). O achado gerado carrega `itemNumber = null`: não é de item nenhum, e inventar um item para
carregá-lo mentiria sobre a causa. Não altera `verifiedItemCount`, que é contagem de item.

**Pré-requisito do `TaxGroupExtractor`.** `docs/pesquisa/auditoria-regras-e-leitura.md §4.2`
encontrou uma colisão de nome latente: `Esfera.of(String)` decide a esfera de um `gRed`/`pRedAliq`
só pelo nome local (`gIBSUF`, `gIBSMun`, `gCBS`), e `total/IBSCBSTot/gCBS` tem o mesmo nome local
que abre a esfera CBS do item. `TaxGroupExtractor` ganhou um booleano `emDet` (mesmo padrão de
`emIbsCbs`) e a abertura de esfera só é aceita quando `emDet` é verdadeiro.

**Achado da sonda de mutação, honesto:** o cenário literal do brief — `total` antes de `det` na
ordem do documento, com `gCBS` de totais tentando abrir a esfera CBS — **não produz nenhuma
diferença observável** em `extract()`, com ou sem a guarda. A razão é uma proteção independente já
existente: o caso `"det"` do laço zera `esfera`, `redUf`, `redMun`, `redCbs`, `pUf`, `pMun`, `pCbs`
como primeiro efeito da própria abertura do item, antes de qualquer filho ser lido — nenhum estado
sujo por algo anterior (dentro ou fora de `det`) sobrevive a essa reinicialização. A guarda `emDet`
continua correta e vale manter: fecha a lacuna que a auditoria nomeou, documenta a intenção no
código e protege uma extensão futura que leia `total/IBSCBSTot` **através deste mesmo extractor**
(hoje não é o caso — 1118/1119 leem a presença via `XmlMetadataParser`, mesmo padrão de
`hasCompraGov`, por instrução explícita do brief). Mas ela é defesa em profundidade, não a correção
de um caminho hoje alcançável, e o teste
`TaxGroupExtractorTest#totalBeforeDetDoesNotPolluteTheFollowingItem` documenta isso
explicitamente — sem fingir ser prova de mutação do que não se prova. `FiscalDocument.hasIbsCbsTot`
(capturado em `XmlMetadataParser`, mesmo padrão de `hasCompraGov`) é o dado que sustenta 1118/1119.

**Consequência nas fixtures.** Nenhuma fixture de `src/test/resources/fixtures/` (canônica ou do
corpus de rejeição) tinha `total/IBSCBSTot`. Com 1119 implementada, todo item com `IBSCBS`
informado sem o total correspondente passa a ser genuinamente rejeitado — o que já era visível em
`docs/validacao/casos-diferenciais.md`, onde a SVRS retorna 1119 em quase todas as fixtures do
Bloco 6 e o documento registra "1119 fica fora do escopo atual". Passou a estar no escopo: as 22
fixtures cujo item tem o invólucro (`nfe-valida.xml` e 21 arquivos de `fixtures/rejeicao/`, todos
exceto `r1115-sem-grupo.xml`, cujo item não tem invólucro nenhum) ganharam
`<IBSCBSTot><vBCIBSCBS>0.00</vBCIBSCBS></IBSCBSTot>` em `total` — o único filho obrigatório do tipo
`TIBSCBSMonoTot` (`DFeTiposBasicos_v1.00.xsd:515`), presença mínima válida contra o XSD. Nenhuma
delas testa valor de `IBSCBSTot` (fora do escopo desta task, e do produto: `vBCIBSCBS`/`gIBS` etc.
pertencem à v1, W35 em diante), então `0.00` uniforme não interfere em nenhuma asserção existente.

**Fora do escopo desta task**, catalogado em `docs/pesquisa/candidatas-rejeicao-pos-b6.md`: as
demais regras de totais (W35 em diante) exigem soma e pertencem ao motor `regime-geral` da v1.

## D-038 — Exceção 1 da 1115 passa a ler `DFeReferenciado`, por item, além de `NFref` (28/07/2026)

Achado de auditoria independente (`docs/pesquisa/auditoria-regras-e-leitura.md §2`), fora do plano
original do bloco: a partir de 01/09/2026 a NT v1.40 muda **onde** a devolução referencia a nota
original. A regra de validação VC02-14, Observação 1, é literal: *"Fica proibido o referenciamento
da Nota na tag `refNFe` na devolução, devendo referenciar no grupo `DFeReferenciado`"* — produção
própria, no cronograma da NT (p. 5), separada da vigência da própria 1115 (D-028).

`DFeReferenciado` é **item**, não documento: confirmado no XSD embarcado,
`leiauteNFe_v4.00.xsd:5285-5309`, dentro de `det/prod`, irmão de `vItem` — ao contrário de `NFref`,
que fica em `ide`, no nível do documento. `chaveAcesso` usa o mesmo tipo `TChNFe` de `refNFe`
(chave de 44 dígitos, mesmo deslocamento de `AAMM` nas posições 2-5), então a decodificação de
competência é a mesma; para não duplicá-la entre `XmlMetadataParser` (documento) e
`TaxGroupExtractor` (item), ela foi extraída para o utilitário compartilhado
`AccessKeyMonth`.

**Consequência sem a correção:** a partir de 01/09/2026, uma devolução emitida **corretamente** —
referenciando só em `DFeReferenciado`, como a norma passa a exigir — chegaria à Exceção 1 com
`document().references()` vazio (`NFref` não foi usado), a exceção não encontraria referência
alguma, e a regra seguiria até `Rejeitado` 1115: falso positivo na regra que motiva o bloco
inteiro, contra quem fez certo.

**Decisão:** `ItemTaxGroup` ganhou o campo `dfeReferenciado` (um `ReferencedNote`, já que o XSD
permite no máximo uma ocorrência por item); `GroupRequiredRule.excecaoDeDevolucaoOuComplementar`
passou a avaliar a **união** das duas fontes (`NFref` do documento e `DFeReferenciado` do item),
não uma substituindo a outra — a VC02-14 só proíbe `refNFe` **na devolução**; documentos que ainda
usam `NFref` (complementar, ou devolução emitida antes da produção da VC02-14) continuam cobertos.
Caso intermediário decidido na direção que não acusa (mesmo padrão de D-028/D-029): item com
`DFeReferenciado` presente mas `chaveAcesso` não decodável (formato inesperado, ausente, vazia ou
com conteúdo misto) produz uma referência **sem data**, que a exceção trata como `NaoAvaliado`,
nunca `Rejeitado` — base incompleta é limitação nossa, não defeito do emitente. Documento sem
nenhuma das duas fontes segue o curso normal da regra, comportamento existente que não regride.

**Fora do escopo desta task**, catalogado em `docs/pesquisa/candidatas-rejeicao-pos-b6.md`: a
VC02-05 (referenciamento simultâneo em nota e item) e a rejeição por devolução sem referência
nenhuma. Esta entrada só evita o falso positivo; não adiciona rejeição nova.

**Verificação por mutação:** o bloco que une as duas fontes em
`GroupRequiredRule.referenciasDaExcecao` foi comentado (fixado em `ctx.document().references()`),
e os três testes que exercitam `DFeReferenciado` (`DocumentRulesTest`) falharam isoladamente; os
demais 235 continuaram verdes. Restaurado e reconfirmado verde.

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
