# Estado atual

> Ponteiro rápido de sessão. Leia isto antes do ledger inteiro. Se este arquivo e o `git log`
> discordarem, o `git log` manda — atualize aqui.

- **Bloco:** B5 — Empacotamento, release e README — **Task 25 em preparação**. Branch
  `bloco/5-release`, criada de `main` local em `0dff1b2`; nada foi enviado ao remoto.
- **B4 fechado:** merge local `0dff1b2` inclui Tasks 21–23 e o refinamento final `1fb7132`.
  D-045 substituiu deliberadamente o fluxo de validação imediata: área de trabalho de documentos,
  validação explícita e incremental, tema escuro/Roboto e CSV fora da interface. Suíte final
  `./gradlew clean test --console=plain`: 342 testes, 0 falhas; `git diff --check` limpo.
- **Próximo passo:** executar a Task 25 conforme o brief scratch
  `.superpowers/sdd/2026-07-26-v0-validador-lote-rtc/task-25-brief.md`: tasks `jpackage`, smoke
  de app-image e ícone nativo para instalador (adendo D-045). É plumbing/build sem novo julgamento
  fiscal, mas seguirá brief, implementação, revisão independente e fix loop.
- **Débitos herdados:** CSV continua no backend sem rota na UI até task explícita; custo de I/O
  (leitura até 3× por documento) permanece sem risco fiscal; pendência de ícone `.ico`/equivalente
  é tratada na Task 25.
