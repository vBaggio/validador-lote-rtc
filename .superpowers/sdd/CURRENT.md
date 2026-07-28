# Estado atual

> Ponteiro rápido de sessão. Leia isto antes do ledger inteiro. Se este arquivo e o `git log`
> discordarem, o `git log` manda — atualize aqui.

- **Bloco:** B3 — Caso de uso e CSV (`docs/superpowers/plans/2026-07-26-v0-validador-lote-rtc.md`,
  Bloco B3).
- **Branch:** `bloco/3-usecase-csv` (HEAD `23c8c16`).
- **Tasks:** 18 (CsvExporter) e 19 (ValidateBatchUseCase) entregues, revisadas, suíte verde
  (332 testes). Task 20 (fechamento): PR #6 aberto, CI verde, push feito.
- **Próximo passo:** aguardando o dono do projeto autorizar o merge do PR #6
  (`gh pr merge 6 --merge --delete-branch` — bloqueado pelo classificador de auto mode, precisa
  rodar fora do agente ou o dono liberar a ação).
- **Depois do merge:** próximo bloco pendente é **B4 — Interface Swing**
  (`docs/superpowers/plans/2026-07-26-v0-validador-lote-rtc.md`, branch `bloco/4-ui`, Tasks 21-24).
- **Pendências sem risco fiscal:** custo de I/O (arquivo lido 3x por documento, bloco 3);
  `BatchReport` não tem contadores de conforme/rejeitado/não-avaliado por documento — decisão
  adiada para o bloco 4, que é quem consome essa distinção (D-043).
