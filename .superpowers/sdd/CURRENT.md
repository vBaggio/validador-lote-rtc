# Estado atual

> Ponteiro rápido de sessão. Leia isto antes do ledger inteiro. Se este arquivo e o `git log`
> discordarem, o `git log` manda — atualize aqui.

- **Bloco:** B6 — Canal confiável de artefatos externos — está implementado em
  `bloco/6-canal-schemas`; o plano-base é
  `docs/superpowers/plans/2026-07-29-canal-confiavel-schemas.md` e o refinamento entregue está em
  `docs/superpowers/plans/2026-07-30-fluxo-observavel-atualizacao-bases.md`. As Tasks 30–36 seguem
  registradas no ledger. **Task 37** (`6c007e0`, revisão PASS/PASS) separou `prepare` e `activate`;
  **Task 38** (`e3997d1`, PASS/PASS) trouxe falhas tipadas, duas tentativas transitórias e
  coordenação parcial; **Task 39** (`774c117`, PASS/PASS) criou snapshot único, entrega monotônica,
  gate e latch; **Task 40** (`0d7ed20`, PASS/PASS) entregou rodapé, spinner e diálogo adaptável;
  e a **Task 42** adicional (`0ada78b`, PASS/PASS após fix loop) fechou a admissão atômica entre
  validação e ativação, a recuperação depois de falha terminal e o isolamento de listener com
  falha. `d399af9` não faz parte do histórico consolidado. A Task 41 documenta o fechamento;
  `clean test`, `jpackageImage` e smoke seguro das fontes são as verificações finais. O smoke
  visual manual no Windows (DPI, diálogo e reinício) permanece pendente e é gate do dono antes de
  publicação/PR; não fazer push, PR ou merge sem autorização explícita.
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
- **Task 27 concluída:** `8e06e66`, revisão independente PASS. README descreve a área de trabalho
  atual, privacidade, limites e instalação condicional sem prometer CSV ou release publicada.
- **Task 28 concluída:** PR #7 passou no check `build` e foi mesclado em
  `96f501f`. A Task 29 (tag/release pública) permanece gate humano separado e não foi iniciada.
- **Débitos herdados:** CSV continua no backend sem rota na UI até task explícita; custo de I/O
  (leitura até 3× por documento) permanece sem risco fiscal; pendência de ícone `.ico`/equivalente
  já foi resolvida na Task 25.
