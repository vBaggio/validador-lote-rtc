# Troca atômica do runtime de bases — Plano de implementação

> **Para agentes implementadores:** executar este plano pelo fluxo de `docs/workflow.md`:
> branch `bloco/8-troca-runtime-bases`, brief por task, TDD, um implementador, revisão
> independente, fix loop, re-revisão e ledger. Não fazer push, PR ou merge antes do aceite
> runtime do dono.

**Objetivo:** depois de uma atualização bem-sucedida, fazer schemas e tabela fiscal entrarem em
uso na mesma sessão, sem reinício obrigatório, sem misturar bases entre documentos e sem recalcular
resultados já exibidos.

**Arquitetura:** o processo continua a preparar e ativar artefatos em disco de forma segura. A
novidade é um `ValidationRuntime` imutável, que reúne um `ValidateBatchUseCase`, seu
`SchemaValidatorEngine`, `RuleEngine` e a identidade das bases que o compõem. O mesmo lock que
admite validação/ativação reserva uma troca de runtime. Após a ativação física, ele monta o novo
runtime fora da EDT e sem lock; somente se a montagem integral vencer ele publica a referência de
uma vez. Cada validação recebe uma lease do runtime no instante da admissão e conserva essa
identidade até o resultado ser mostrado.

**Stack:** Java 21, Swing/FlatLaf, JUnit 5, AssertJ, Gradle Wrapper. Não há mudança de artefato
fiscal, protocolo de rede ou regra de julgamento fiscal neste bloco.

## Restrições e invariantes globais

- Dependências permanecem `presentation → application → {domain, infrastructure}`;
  `infrastructure → domain`; `domain → nada`; Swing/AWT apenas em `presentation`.
- A troca não ocorre na EDT e nenhum XML, chave, CNPJ ou dado de lote participa dela.
- `check → prepare → confirmação → activate em disco` permanece igual. `current` só muda depois
  dos gates já existentes de integridade, assinatura/hash/estrutura e anti-rollback.
- Há no máximo uma destas operações por vez: validação, ativação física ou construção/publicação
  de runtime. Uma consulta pode continuar independente, desde que não apague/contradiga a operação
  de troca em curso.
- A admissão de validação e a reserva da ativação/troca usam **o mesmo lock**. Não pode existir o
  intervalo entre “validação permitida” e “runtime capturado”.
- Uma validação já admitida usa somente a lease capturada; resultados não são recalculados,
  reclassificados nem reetiquetados após uma troca posterior.
- Runtime novo é coerente: schemas, `FiscalTables`, `RuleEngine`, `ValidateBatchUseCase`,
  proveniência e versões vêm da mesma leitura das referências `current` íntegras após ativação.
  Não publicar schema novo com `RuleEngine` antigo, ou o inverso.
- Falha de montagem em memória **não** desfaz uma ativação física válida: o runtime antigo segue
  atendendo a sessão e a UI informa que a base foi ativada em disco, mas só será usada no próximo
  boot. Esse estado fica latched até encerrar o processo; não há tentativa cega de reconstrução.
- Falha de uma fonte continua parcial: a fonte saudável pode ser ativada; a montagem usa a base
  saudável nova mais a última íntegra da fonte que falhou. Se essa montagem falhar, aplica-se a
  regra de fallback acima.
- Código em inglês; mensagens, planos e roteiro manual em pt-BR. Um commit semântico por task com
  escopo `b8`; nada de push durante as tasks.

## Modelo de estado alvo

```text
validação admitida ──lease(runtime R)──> resultados marcados R
        │
        └── fim/cancelamento ──> gate livre

candidatas confirmadas ──> ativação em disco ──> construir R' fora da EDT
                                                │
                              sucesso ─────────┴──> publicar R' atomicamente → "Bases atualizadas e já em uso"
                              falha ──────────────> manter R → "Base ativada em disco; será usada no próximo boot"
```

Estados agregados propostos em `ExternalSourcesPhase`:

| Estado | Significado | Validação nova |
|---|---|---|
| `WAITING_FOR_VALIDATION` | há candidata confirmada, lote ainda usa a lease atual | já admitida continua; nova depende do gate atual |
| `APPLYING` | referência em disco sendo ativada | recusada com feedback visível |
| `RELOADING_RUNTIME` | runtime candidato está sendo montado fora da EDT | recusada com feedback visível |
| `UPDATED_AND_IN_USE` | todos os artefatos ativados nesta operação que puderam compor o runtime já estão em uso | liberada com a nova lease |
| `RESTART_REQUIRED` | fallback excepcional: ativação em disco venceu, construção/publicação em memória falhou | o runtime anterior continua; reinício recomendado |

`RESTART_REQUIRED` deixa de ser o sucesso normal. Os cards conservam o resultado individual da
fonte; a fase agregada pode ser `UPDATED_AND_IN_USE` com `failedCount > 0`, deixando explícita a
falha parcial sem esconder a base que já entrou em uso.

## Sequência de execução e revisão

Para **cada** task abaixo:

1. criar `.superpowers/sdd/2026-07-30-troca-atomica-runtime-bases/task-N-brief.md`, copiando os
   contratos e os testes desta task; adicionar `ADENDO` somente se uma descoberta concreta exigir;
2. escrever primeiro o teste determinístico vermelho e rodar somente a classe indicada;
3. implementar o mínimo, rodar o teste focado, `./gradlew test --console=plain` e a sonda de
   mutação onde indicada; criar um único commit `feat(b8): ...`, `fix(b8): ...` ou `docs(b8): ...`;
4. preparar diff para revisor independente. Ele emite dois vereditos: conformidade deste plano e
   qualidade/concorrência/arquitetura. Achados Crítico/Importante retornam ao implementador,
   recebem teste de regressão e re-revisão; menores entram no ledger como débito;
5. registrar o commit, testes, achados, decisão e divergências no ledger único e atualizar
   `CURRENT.md`/`### PARADA` ao trocar de sessão.

## Task 46 — Contrato de runtime imutável e identidade dos resultados

**Status:** concluída em `f949b89`; revisão independente PASS/PASS após dois fix loops.

**Propósito:** tornar explícito o conjunto que uma validação usa e impedir que uma referência
mutável alcance um documento já em processamento ou exibido.

**Arquivos previstos:**

- Criar `src/main/java/br/com/validadorlote/application/ValidationRuntime.java`.
- Criar `src/main/java/br/com/validadorlote/application/RuntimeBases.java` e, se necessário,
  `ValidationRuntimeFactory.java`/`ValidationLease.java`.
- Modificar `WorkspaceDocument`, `MainPresenter` e seus testes.
- Modificar `docs/decisions.md` com **D-052**.
- Criar/alterar testes em `application` e `presentation` sem Swing nativo.

**Contrato alvo:**

```java
public record RuntimeBases(String schemaVersion, String schemaProvenance,
                           String tableVersion, String tableProvenance, long generation) { }

public record ValidationRuntime(ValidateBatchUseCase useCase, RuntimeBases bases) { }

public interface ValidationRuntimeFactory {
    ValidationRuntime build(); // chamado somente fora da EDT e fora do stateLock
}
```

O nome final pode ser ajustado no brief, mas deve preservar: imutabilidade, geração monotônica e
identidade legível de schema e tabela. `WorkspaceDocument` guarda `RuntimeBases` junto do resultado
daquele documento (nulo apenas enquanto `PENDING`/`VALIDATING`); a UI pode exibir a proveniência no
detalhe/tooltip, mas nunca substitui o valor salvo por uma geração posterior.

- [ ] **TDD — escrever testes vermelhos.** Cobrir: (a) dois `ValidationRuntime` com engines/fake
  use cases distinguíveis têm gerações diferentes; (b) concluir documento com R1 e publicar R2 não
  altera R1 nem seus achados; (c) cancelar/falhar uma validação não anexa identidade a um resultado
  inexistente; (d) a geração é parte do snapshot de resultado, não lida de uma variável global na
  renderização.
- [ ] **Implementar o modelo mínimo.** Não tornar `ValidateBatchUseCase` mutável e não trocar seus
  engines por setter. Introduzir o overload/construtor necessário no presenter mantendo o caminho
  de testes sem fontes externas.
- [ ] **Sonda de mutação.** Remover temporariamente o armazenamento de `RuntimeBases` em
  `WorkspaceDocument`; o teste de preservação deve falhar sozinho. Restaurar e conferir árvore
  limpa antes do commit.
- [ ] **Verificar e commitar.**

```bash
./gradlew test --tests '*MainPresenterTest' --console=plain
./gradlew test --console=plain
git commit -m "feat(b8): identifica runtime dos resultados do lote"
```

**Revisão obrigatória:** confirmar que nenhum resultado existente é mutado/revalidado durante a
troca e que `presentation` não constrói infraestrutura.

## Task 47 — Gate único, leases e publicação atômica do runtime

**Status:** concluída em `1d98ec9`; revisão independente PASS/PASS após um fix loop crítico.

**Propósito:** substituir o booleano de admissão por um protocolo que capture a lease da validação
e mantenha a reserva da ativação até o runtime novo ser publicado ou falhar.

**Arquivos previstos:**

- Modificar `ExternalSourcesUseCase`, `ExternalSourcesPhase`, `ExternalSourcesSnapshot` e seus
  testes.
- Criar um tipo de resultado de troca, se isso deixar o contrato explícito.
- Modificar `MainPresenter` e `MainPresenterTest`.

**Contrato alvo:** a operação equivalente a `tryStartValidation()` devolve uma lease (ou vazio),
capturada dentro de `stateLock`; `validationFinished(lease)` só libera a lease correspondente. A
aplicação reserva o mesmo lock antes de agendar o coordenador e o mantém até o resultado terminal
da troca em memória. A construção do candidato acontece depois de soltar o lock; a publicação da
referência acontece novamente sob lock em uma única atribuição. Nenhum callback de observador ou
construção pode ocorrer sob o lock.

- [ ] **TDD — testes de concorrência determinísticos com latches/barreiras.**
  1. Enquanto uma validação R1 está bloqueada, confirmar atualização produz
     `WAITING_FOR_VALIDATION`; ao terminar R1, a ativação reserva o gate antes de qualquer R2.
  2. Entre a chamada de admissão e a captura da lease, uma ativação concorrente não pode publicar
     R2: a validação recebe integralmente R1 **ou** é recusada; nunca recebe engines misturados.
  3. Durante `APPLYING` e `RELOADING_RUNTIME`, tentativas repetidas de validar são recusadas sem
     iniciar worker, e a liberação terminal permite exatamente uma nova validação.
  4. Duas confirmações/consultas concorrentes não constroem dois runtimes, nem publicam revisões de
     snapshot fora de ordem.
  5. Executor que rejeita ativação ou construção libera a reserva e entrega estado terminal, sem
     deixar o presenter em “aguarde” permanente.
- [ ] **Implementar o protocolo.** Usar os mesmos `stateLock`, fila de snapshots e regra de
  revisão monotônica existentes; não introduzir `sleep`, polling ou bloqueio da EDT. Separar
  claramente `activationReserved`, `runtimeReloading` e `validationLease` para que evento
  `CHECKING` de uma consulta posterior não limpe uma reserva de troca.
- [ ] **Sonda de mutação.** Remover a checagem da reserva antes de devolver a lease; o teste da
  barreira deve falhar sozinho.
- [ ] **Verificar e commitar.**

```bash
./gradlew test --tests '*ExternalSourcesUseCaseTest' --tests '*MainPresenterTest' --console=plain
./gradlew test --console=plain
git commit -m "feat(b8): protege troca de runtime pelo gate de validacao"
```

**Revisão obrigatória:** examinar ordem de aquisição/liberação, visibilidade entre threads,
callbacks que lançam exceção e se a consulta posterior pode invalidar a operação em curso.

## Task 48 — Construção coerente pós-ativação e fallback de memória

**Status:** concluída em `1b1f5db`; revisão independente PASS/PASS após dois fix loops.

**Propósito:** ligar a ativação física já validada à montagem de um conjunto totalmente novo de
engines/casos de uso, sem reabrir staging nem desmontar o runtime que está atendendo.

**Arquivos previstos:**

- Modificar `App` (composition root) para montar `ValidationRuntime` tanto no boot quanto após
  ativação, sempre pelos `SchemaArtifactStore` e `FiscalTableArtifactStore` ativos.
- Modificar `ExternalSourcesUseCase` e testes.
- Adicionar testes em `AppTest` e/ou uma classe de teste de fábrica de runtime.
- Se necessário, extrair da `App` uma factory concreta em `application`/infra sem inverter a
  dependência de camadas.

**Implementação exigida:**

1. A factory monta, fora da EDT, `SchemaValidatorEngine` pela `current` íntegra ou fallback
   embarcado, `FiscalTables` pela `current` íntegra ou fallback embarcado, `RuleEngine`,
   `ValidateBatchUseCase`, proveniência e `RuntimeBases` novos. Ela não reutiliza o engine ou
   `RuleEngine` do runtime anterior.
2. Depois de `ArtifactUpdateCoordinator` terminar as aplicações, o caso de uso detecta se ao menos
   uma fonte foi realmente `APPLIED`. Só então entra em `RELOADING_RUNTIME` e chama a factory no
   executor de atualização (ou outro executor de background explicitamente injetado). Nunca usar
   listener da EDT nem segurar `stateLock` durante compilação XSD.
3. Em sucesso, publicar R' uma única vez sob o gate, normalizar o estado das fontes aplicadas como
   “em uso”, limpar o pedido de reinício e emitir `UPDATED_AND_IN_USE`.
4. Em falha de factory, manter exatamente a referência R anterior, preservar os `current` já
   ativados, registrar uma falha recuperável/diagnosticável e emitir `RESTART_REQUIRED` com texto
   inequívoco: “A base foi ativada em disco, mas não pôde ser carregada agora; reinicie o
   aplicativo para usá-la.” Não apagar candidata, não reativar nem tentar montar de novo na mesma
   sessão. A falha de uma fonte antes de ativar não impede montar o conjunto coerente que combina
   as referências íntegras disponíveis.

- [ ] **TDD — testes vermelhos.** Cobrir: schema e tabela candidatos são observados pela factory
  somente depois dos dois `current` de sucesso; uma falha parcial de aplicação compõe R' com a
  base íntegra antiga da fonte falha; factory bloqueada não bloqueia EDT/snapshots; factory que
  lança deixa R1 publicado, marca fallback de boot e libera o gate; e uma segunda atualização não
  reexecuta a factory enquanto o fallback está latched.
- [ ] **Testes de preservação de armazenamento.** Simular falha de compilação/montagem após
  `activate`: afirmar que o manifesto `current` novo permanece em disco, R1 atende uma validação
  subsequente e o próximo bootstrap conseguiria tentar a leitura de `current` normalmente.
- [ ] **Sonda de mutação.** Substituir a atribuição de publicação de R' por no-op; o teste que
  valida após `UPDATED_AND_IN_USE` deve continuar usando R1 e falhar sozinho.
- [ ] **Verificar e commitar.**

```bash
./gradlew test --tests '*AppTest' --tests '*ExternalSourcesUseCaseTest' --console=plain
./gradlew test --console=plain
git commit -m "feat(b8): troca engines apos ativacao das bases"
```

**Revisão obrigatória:** conferir que só o composition root monta infraestrutura; que a factory
não usa fonte remota, staging ou referência parcialmente escrita; e que a exceção nunca descarta
R1 nem converte conteúdo fiscal em decisão nova.

## Task 49 — Transição de UI e mensagens sem reinício normal

**Propósito:** refletir o runtime realmente publicado, sem regressão do modal, spinner, acessibilidade
ou feedback de falha.

**Arquivos previstos:**

- Modificar `MainPresenter`, `MainView`, `ExternalSourcesStatusBar`, `ExternalSourcesPanel`,
  `ExternalSourcesDialog`, `MainFrame` e seus testes.
- Modificar `messages.properties` se houver mensagem centralizada.

- [ ] **TDD — presenter.** O fluxo terminal bem-sucedido apresenta uma única vez o feedback
  “Bases atualizadas e já em uso”, não chama `showRestartRequired`, fecha/desbloqueia o modal só
  após `UPDATED_AND_IN_USE`, e a próxima ação de validar recebe R'. Resultados já na grade mantêm
  a etiqueta R1. A transição de fallback chama o feedback de reinício com a mensagem de disco,
  mas não afirma que a base já está em uso.
- [ ] **TDD — componentes Swing isolados.** `RELOADING_RUNTIME` mantém spinner sem ícone
  duplicado e bloqueia ações/fechamento como `APPLYING`; `UPDATED_AND_IN_USE` apresenta sucesso,
  fecha a ação de encerrar e oferece apenas continuar/verificar; `RESTART_REQUIRED` fica reservado
  ao fallback de memória. Verificar nomes acessíveis, rolagem e que nenhum botão decide estado por
  texto visível.
- [ ] **Implementar.** Remover o latch `restartRequiredShown` do caminho normal e substituí-lo por
  controle de revisão do sucesso em uso. Manter a abertura modal adiada para o próximo ciclo da
  EDT e não chamar factory, `apply`, ou validação na EDT.
- [ ] **Verificar e commitar.**

```bash
./gradlew test --tests '*MainPresenterTest' --tests '*ExternalSourcesPanelTest' \
  --tests '*ExternalSourcesStatusBarTest' --tests '*ExternalSourcesDialogTest' --console=plain
./gradlew test --console=plain
git commit -m "feat(b8): informa bases atualizadas em uso"
```

**Revisão obrigatória:** conferir ordem de snapshots/diálogo terminal, ausência de prompt duplicado,
mensagem honesta no fallback e que nenhuma ação de tela depende de `getText()`.

## Task 50 — Documentação operacional, regressão integrada e aceite runtime

**Propósito:** substituir formalmente o ciclo que terminava em reinício e fechar o bloco com provas
reproduzíveis, sem ocultar o fallback excepcional.

**Arquivos previstos:**

- Modificar `docs/context.md`, `docs/architecture.md`, `docs/operacao-atualizacao-bases.md`,
  `docs/operacao-canal-schemas-curados.md`, `docs/testing.md` e `docs/decisions.md` se a Task 46
  ainda não tiver registrado D-052.
- Atualizar `README.md` apenas se ele prometer reinício.
- Atualizar `.superpowers/sdd/CURRENT.md` e `progress.md` no fechamento.

- [ ] **Atualizar contrato e falhas.** Trocar a descrição `activate → restart` por
  `activate → build → atomic publish`; documentar o fallback de boot, a associação de resultados
  à geração e a semântica de sucesso parcial.
- [ ] **Criar regressões integradas.** Em teste com executor controlado, cobrir a linha completa:
  R1 valida um documento, candidatas são preparadas/confirmadas, R2 é montado fora da EDT, uma
  nova validação usa R2 e o resultado de R1 ainda referencia R1. Cobrir também a factory que
  falha depois do `current` novo e o estado terminal recuperável.
- [ ] **Revisão final de bloco.** Revisor independente percorre invariantes, diff, provas de
  mutação, ArchUnit e documentos; Crítico/Importante obrigam fix loop e re-revisão. Registrar
  menores no ledger.
- [ ] **Verificações finais antes do aceite humano.**

```bash
./gradlew clean test --console=plain
./gradlew jpackageImage --console=plain
git diff --check
```

Commit:

```bash
git commit -m "docs(b8): registra troca atomica de runtime"
```

## Critérios de aceite runtime manual

Executar em imagem/instalação limpa e repetir em Windows 100%, 125% e 150% de DPI. Preparar uma
release de schema/tabela de teste que altere de maneira observável a versão/proveniência, sem usar
XML de produção.

1. Abrir o aplicativo, importar dois XMLs e validar o primeiro. Registrar no detalhe/tooltip do
   resultado a geração e versões R1; ele deve continuar visível e inalterado.
2. Forçar/aguardar candidatas das duas fontes e confirmar atualização. Durante uma validação R1
   deliberadamente longa, conferir **Aguardando validação**; cancelar ou terminar o lote e conferir
   que só então a aplicação começa.
3. Em **Aplicando** e **Carregando bases atualizadas**, tentar validar repetidamente, fechar o
   diálogo por X/Esc/Alt+F4 e clicar ações: não pode iniciar worker, fechar o modal ou congelar a
   janela. Rodapé e diálogo devem avançar na mesma ordem.
4. Após sucesso, observar literalmente **“Bases atualizadas e já em uso”**, sem pedido de reinício.
   Validar o segundo XML sem reiniciar; ele deve mostrar R2. Conferir que o primeiro continua com
   R1 e não foi recalculado.
5. Repetir com uma fonte indisponível e outra candidata válida. A fonte falha fica explícita; a
   saudável entra em uso se a factory puder compor o conjunto com a referência íntegra restante.
6. Induzir falha de montagem (fixture/schema inválido que só alcance a factory após ativação).
   Confirmar que `current` novo existe em disco, a sessão ainda valida com R1, o usuário vê a
   mensagem de que o uso ocorrerá no próximo boot e não há spinner/gate preso. Reiniciar e
   confirmar que a base em disco é a que o boot tenta carregar.
7. Enquanto a factory está bloqueada em teste, clicar validar e consultar: não pode haver mistura,
   deadlock, publicação fora de ordem ou nova factory. Ao terminal, exatamente uma nova validação é
   admitida.
8. Rodar nova consulta sem candidata; conferir que não reaplica nem remonta runtime. Fazer uma
   consulta manual repetida durante outra operação e confirmar uma única execução.

## Aceite para fechamento do bloco

- [ ] toda validação usa uma lease imutável de um runtime completo;
- [ ] não há janela entre admissão e captura da lease que permita engines de gerações diferentes;
- [ ] nenhum resultado já exibido muda de base, achado ou geração após atualização;
- [ ] construção/publicação de R' ocorre fora da EDT e não sob `stateLock`;
- [ ] sucesso publica R' uma única vez, libera o gate e mostra “Bases atualizadas e já em uso”;
- [ ] falha de construção preserva R1, preserva `current` novo, comunica o fallback de próximo boot
  e não deixa operação presa;
- [ ] falha parcial não apaga a candidata/fonte saudável nem mistura runtime;
- [ ] executor/listener/consulta concorrente não quebram a ordem de snapshots nem deixam limbo;
- [ ] testes de mutação das guardas e publicação falham com a proteção removida;
- [ ] `clean test`, `jpackageImage` e `git diff --check` passam;
- [ ] roteiro manual foi executado e registrado pelo dono antes de push/PR/merge.
