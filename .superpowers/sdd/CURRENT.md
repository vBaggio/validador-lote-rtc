# Estado atual

> Ponteiro rápido de sessão. Leia isto antes do ledger inteiro. Se este arquivo e o `git log`
> discordarem, o `git log` manda — atualize aqui.

- **Bloco:** B3 — Caso de uso e CSV — **FECHADO**. PR #6 mergeado em `main` (merge commit
  `37f1215`), branch `bloco/3-usecase-csv` deletada (local e remota).
- **HEAD:** `37f1215` na `main`. Árvore limpa, suíte verde (`./gradlew clean test --console=plain`,
  332 testes, 0 falhas).
- **Próximo bloco pendente: B4 — Interface Swing**
  (`docs/superpowers/plans/2026-07-26-v0-validador-lote-rtc.md`, branch `bloco/4-ui`, Tasks 21-24:
  MainPresenter + contratos de view, shell Swing/FlatLaf, ResultsPanel mestre-detalhe, fechamento).
  Ainda não iniciado — nenhuma branch criada.
- **Antes de começar o B4:** ler o brief/plano das Tasks 21-24 e conferir se ficaram desatualizadas
  pelos blocos 6/7 (mesmo padrão da Task 19) — em especial a exibição em camadas (conforme/
  rejeitado/não-avaliado) que os blocos 6/7/3 mencionam repetidamente como pendente para "quem
  consumir a distinção na UI" (D-043). Também é o ponto certo para revisitar D-015
  (`allowEmptyShould(true)` em `presentationDoesNotSeeInfrastructure`, hoje ainda ligado porque
  `presentation/` não existe).
- **Pendências sem risco fiscal, herdadas do bloco 3:** custo de I/O (arquivo lido 3x por
  documento); `BatchReport` sem contadores de conforme/rejeitado/não-avaliado por documento —
  decisão deliberada, adiada para o bloco 4 (D-043).
- **Processo:** D-044 (28/07/2026) — cerimônia proporcional ao risco fiscal (ver
  `docs/workflow.md` §1.1 e §8) já vale para o B4: julgamento fiscal novo (se houver) recebe o
  fluxo completo; wiring de UI/Swing recebe revisão mais leve.
