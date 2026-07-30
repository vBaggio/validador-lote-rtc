# Estado atual

> Ponteiro rápido de sessão. Leia isto antes do ledger inteiro. Se este arquivo e o `git log`
> discordarem, o `git log` manda — atualize aqui.

- **Bloco:** B7 — Canal próprio de schemas curados — está tecnicamente completo em
  `bloco/7-canal-proprio-schemas`; plano em
  `docs/superpowers/plans/2026-07-30-canal-proprio-schemas-curados.md`. Tasks 1–5 e a correção
  integrada (`12f5821`) passaram em revisão ampla PASS/PASS. O bootstrap externo foi publicado em
  `vBaggio/validador-lote-rtc-bases`: GitHub Pages, canal `nfe-schemas`, release `010e_v1.02-r2`, `keyId`
  `schemas-2026-01` e chave pública Ed25519 agora são configuração embarcada do app. Não há
  fallback SVRS/ACBr para schemas; indisponibilidade preserva `current` ou a base embarcada. O
  smoke runtime foi validado pelo dono (registro em `tmp/runtime-smoke-canal-curado-2026-07-30.md`).
  O dono autorizou push, PR e merge após a documentação das pendências pós-entrega; consultar
  `docs/operacao-canal-schemas-curados.md` antes de publicar a branch.

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
  falha. A **Task 43** (`7fa9a73`) desacoplou a abertura modal do dreno de snapshots na EDT; a
  **Task 44** (`0d5750c`) fechou listener defeituoso em `CHECKING`, falha parcial visível, prazo e
  cancelamento de corpo HTTP e feedback de executor rejeitado; e a **Task 41** (`944e932`) mais a
  **Task 45** registram o fechamento documental. `d399af9` não faz parte do histórico
  consolidado. A Task 45 aguarda revisão independente; `clean test`, `jpackageImage` e
  `git diff --check` são as verificações locais exigidas. O smoke visual manual no Windows (DPI,
  diálogo e reinício) permanece pendente e é gate do dono antes de publicação/PR; não fazer push,
  PR ou merge sem autorização explícita.
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
