# Estado atual

> Ponteiro rápido de sessão. Leia isto antes do ledger inteiro. Se este arquivo e o `git log`
> discordarem, o `git log` manda — atualize aqui.

- **Bloco:** B6 — Canal confiável de artefatos externos — **planejado em**
  `bloco/6-canal-schemas`, a partir de `main`/`origin/main` `efa10cc`. O plano está em
  `docs/superpowers/plans/2026-07-29-canal-confiavel-schemas.md`. **Task 30 concluída e revisada**
  em `f95bec4`: catálogo local de artefatos e
  `SchemaArtifactStore` transacional, carregamento de filesystem confinado e fallback embarcado.
  D-046 limita hash local a integridade operacional, não autenticidade contra escrita da mesma
  conta. Próximo: Task 31, atualização/proveniência da árvore NF-e/NFC-e. A pesquisa provisória
  está em `docs/pesquisa/2026-07-29-canal-artefatos-externos.md`:
  confirmou o espelho ACBr byte-idêntico à base candidata e detectou que a URL Gradle atual das
  tabelas SVRS retorna 404, enquanto a rota nova exige adaptação e regressão. O escopo cobre
  schemas e tabelas fiscais ativas, além do inventário do motor futuro da Calculadora.
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
