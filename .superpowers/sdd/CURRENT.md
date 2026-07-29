# Estado atual

> Ponteiro rápido de sessão. Leia isto antes do ledger inteiro. Se este arquivo e o `git log`
> discordarem, o `git log` manda — atualize aqui.

- **Bloco:** B4 — Interface Swing — **IMPLEMENTADO E REVISADO**, pendente de validação visual do
  dono antes do push/PR. Branch `bloco/4-ui`; não houve push.
- **Commits de task:** `8dccf5e` (MVP), `7892d14` (shell Swing) e `206b92a` (resultados). Suíte
  final verde: `./gradlew clean test --console=plain`, 341 testes, 0 falhas; `git diff --check`
  limpo. Consulte `git log` para o HEAD exato, pois o próximo commit registra este handoff.
- **Entregue:** FlatLaf/Swing, drop ou escolha de pasta, progresso cancelável, mestre-detalhe,
  CSV, toggle pré-emissão. A UI mostra camadas por `FindingKind`, separa rejeições previstas de
  não avaliadas e declara que a conferência de valores não foi executada. Não inventa classificação
  de documento como conforme/aprovado (D-043 e spec §7). D-015 concluída: a guarda ArchUnit de
  `presentation` voltou a ser estrita.
- **Revisões:** Tasks 21, 22 e 23 receberam revisão independente PASS/PASS, sem achados abertos.
- **Pendência para validação humana:** o aplicativo iniciou em sessão gráfica, mas a captura do
  compositor ficou preta. Inspecionar escolha/drop, progresso/cancelamento, seleção mestre-detalhe,
  toggle e exportação. Depois disso, executar Task 24 (push/PR) conforme `workflow.md`.
- **Débitos sem risco fiscal herdados:** custo de I/O (arquivo lido 3x por documento); sem
  classificação por documento no `BatchReport` — escolha deliberada, agora preservada na UI.
