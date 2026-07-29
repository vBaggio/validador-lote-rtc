# Estado atual

> Ponteiro rápido de sessão. Leia isto antes do ledger inteiro. Se este arquivo e o `git log`
> discordarem, o `git log` manda — atualize aqui.

- **Bloco:** B5 — Empacotamento, release e README — **Task 27 em preparação**. Branch
  `bloco/5-release`, criada de `main` local em `0dff1b2`; nada foi enviado ao remoto.
- **B4 fechado:** merge local `0dff1b2` inclui Tasks 21–23 e o refinamento final `1fb7132`.
  D-045 substituiu deliberadamente o fluxo de validação imediata: área de trabalho de documentos,
  validação explícita e incremental, tema escuro/Roboto e CSV fora da interface. Suíte final
  `./gradlew clean test --console=plain`: 342 testes, 0 falhas; `git diff --check` limpo.
- **Task 25 concluída:** `983ed90`, revisão independente PASS após fix loop. `clean test` (342)
  e `jpackageImage` passaram; runtime Java 21 e launcher sem falta de classes. Linux escolhe RPM
  ou DEB conforme a distribuição e explica pré-requisito ausente; ícones `.ico`/`.png`/`.icns`
  cobrem Windows/Linux/macOS. O Fedora atual não tem `rpmbuild`, portanto não gera instalador local.
- **Task 26 concluída:** `d45ed3a`, revisão independente PASS. Workflow de tag `v*`, Windows/MSI
  como gate e Linux/macOS best-effort, com pré-requisitos DEB do Ubuntu. A execução real depende da
  primeira tag publicada no GitHub; YAML e suíte local foram validados.
- **Próximo passo:** executar a Task 27 conforme o brief scratch
  `.superpowers/sdd/2026-07-26-v0-validador-lote-rtc/task-27-brief.md`: README de usuário final
  alinhado a D-045 e ao modo atual de instalação. É documentação sem julgamento fiscal e seguirá
  brief, implementação e revisão independente.
- **Débitos herdados:** CSV continua no backend sem rota na UI até task explícita; custo de I/O
  (leitura até 3× por documento) permanece sem risco fiscal; pendência de ícone `.ico`/equivalente
  já foi resolvida na Task 25.
