# Estado atual

> Ponteiro rápido de sessão. Leia isto antes do ledger inteiro. Se este arquivo e o `git log`
> discordarem, o `git log` manda — atualize aqui.

- **Bloco:** B6 — Canal confiável de artefatos externos — implementado em
  `bloco/6-canal-schemas`, a partir de `main`/`origin/main` `efa10cc`. O plano está em
  `docs/superpowers/plans/2026-07-29-canal-confiavel-schemas.md`. **Task 30 concluída e revisada**
  em `974817d`: catálogo local de artefatos e
  `SchemaArtifactStore` transacional, carregamento de filesystem confinado e fallback embarcado.
  D-046 limita hash local a integridade operacional, não autenticidade contra escrita da mesma
  conta. **Task 31 concluída e revisada** em `ebfb4ae`: closure mínima atualizada para 010e_v1.02,
  Portal Nacional como autoridade, ACBr r47146 como transporte identificado e hash canônico da
  closure. **Task 32 concluída e revisada** em `1d0a12c`: atualização SVRS com HTTPS restrito,
  staging/rollback, continuidade de identidades e fallback embarcado. **Task 33 concluída e
  revisada** em `068c868`: aquisição Portal segura, ZIP confinado, estado de consulta persistente
  e agendamento pós-boot. **Task 34 concluída** em `16080b3`: diálogo de Fontes externas,
  consulta manual sem duplicação, atualização válida no próximo boot, política ACBr sem fallback
  automático e tasks Gradle históricas bloqueadas. O fechamento local foi documentado em `e237ef6`,
  mas a revisão adicional identificou que o runtime do app-image omitia `java.net.http` e que a
  spec ainda proibia toda rede em runtime. **Task 35 concluída e revisada:** inclui o
  módulo necessário, teste de regressão e a exceção D-048 na spec. `clean test` passou com 377
  testes; `jpackageImage` passou, o runtime lista `java.net.http` e o launcher permaneceu 12 s sem
  erro de módulo. A revisão independente deu PASS/PASS, sem achados. **Task 36 concluída e
  revisada** nesta task: o catálogo e download público da SVRS substituem o Portal Nacional no
  runtime, com allowlist, staging, parser restrito à seção Schemas e sem downgrade. Hoje a SVRS só
  publica `010b`, logo a tela comunica “pacote compatível mais novo” ausente e mantém `010e` sem
  erro HTTPS. D-049 limita deliberadamente a atualização automática à família `010e`; sucessor
  exige task que revise roots/fixtures. `clean test` (377) e `jpackageImage` passaram e a
  re-revisão foi PASS e o commit foi publicado em `origin/bloco/6-canal-schemas`; próximo passo é
  validação do dono no Windows e, se aprovada, atualizar/abrir o PR. A pesquisa provisória
  está em `docs/pesquisa/2026-07-29-canal-artefatos-externos.md`:
  confirmou o espelho ACBr byte-idêntico à base candidata e detectou que a URL Gradle atual das
  tabelas SVRS retorna 404, enquanto a rota nova exige adaptação e regressão. O escopo cobre
  schemas e tabelas fiscais ativas, além do inventário do motor futuro da Calculadora.
  A validação do dono abriu o refinamento **Tasks 37–41**, planejado em
  `docs/superpowers/plans/2026-07-30-fluxo-observavel-atualizacao-bases.md`: consulta observável,
  staging antes da confirmação, sucesso parcial, ativação bloqueante e diálogo adaptável. O commit
  local `d399af9` é apenas o protótipo visual dos cards e ainda não foi publicado; ele será
  substituído/incorporado na Task 40. **Task 37 concluída e revisada** em `68f05e5`: schemas e
  tabelas agora preparam candidatas sem alterar `current` e ativam somente versão revalidada;
  correção de revisão torna a consulta repetida da tabela idempotente antes da confirmação.
  Suítes focadas e completa passaram, e a revisão independente deu PASS/PASS após um fix loop.
  **Task 38 concluída e revisada** em `a64edf9`: falhas tipadas, retentativa limitada e
  coordenação por canal permitem consulta/aplicação parcial fora da EDT. `./gradlew test` passou
  com 399 testes e revisão PASS/PASS; os dois achados menores estão no ledger. Próximo passo:
  Task 39 — snapshot único e gate da validação.
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
