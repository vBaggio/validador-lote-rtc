# Estado atual

> Ponteiro rápido de sessão. Leia isto antes do ledger inteiro. Se este arquivo e o `git log`
> discordarem, o `git log` manda — atualize aqui.

Este arquivo estava desatualizado (B6–B9 registrados como "em execução"/aguardando aceite do dono
havia dias, quando já estavam mergeados em `main` — confirmado por `git log --merges`). Reescrito
em 31/07/2026 a partir do `git log`, não do texto anterior; os parágrafos de blocos fechados foram
podados para uma linha cada, conforme §8 de `workflow.md`.

## Sessão de 04/08/2026 (mais recente)

- **B11 — Indicadores oficiais e grupos condicionais concluído localmente.** Branch
  `bloco/11-indicadores-grupos`; Tasks 1–4 passaram em revisão independente após os fix loops
  documentados no ledger. Foram entregues as 17 RVs de presença, ausência e valor dos grupos
  superiores, com tabela SVRS completa, extração StAX com escopo fiscal estrito e limites
  monofásicos explícitos (D-064/D-065). Por D-065, 1131/1132, 1169 e 1172 ficam deliberadamente
  sem veredito local em NFC-e até schema compatível, embora a NT liste esse modelo. A auditoria de fechamento está no relatório scratch
  `b11-task-5-report.md`; `./gradlew clean test --console=plain` passou localmente. Nenhum push ou
  PR foi feito. Próximo passo: revisão humana e PR da branch; não iniciar subgrupos monofásicos
  profundos antes de schema curado compatível.
- **Árvore na parada:** apenas o pré-plano não rastreado do usuário
  `docs/pesquisa/2026-07-31-pre-plano-calculadora.md`; não foi alterado. HEAD é o commit de handoff
  documental do B11 desta sessão; confirme-o com `git log` antes de retomar.

## Sessão de 31/07/2026

- **Revisão fiscal — totalizações IBS/CBS e crédito presumido.** PR #18 mergeado (`0b3622c`).
  Revisão independente encontrou e corrigiu dois bugs reais de leitura StAX (nome de tag divergente
  do XSD: `vBC`→`vBCIBSCBS` no total; `vIBS`/`vCBS`→`vIBSEstCred`/`vCBSEstCred` em `gEstornoCred`).
  Detalhe completo já registrado no ledger principal (linhas ~900–990).
- **UI do diálogo "Bases de validação" + harness de release.** PR #19 mergeado (`5b57954`).
  Corrigido desalinhamento (componentes em `BoxLayout.Y_AXIS` sem `alignmentX`), botão
  "Detalhes"/"Ocultar" virou flat/só-ícone, "Base ativa" integrado ao grid de detalhes com largura
  proporcional (não mais `GridLayout` de colunas iguais, que truncava "Origem da base").
- **D-062 — fonte única de versão.** `DEVELOPMENT_APP_VERSION` hardcoded (dessincronizava de
  `build.gradle`, prendendo o rótulo/checagem de atualização em `0.1.2` por dois releases) trocado
  por `app-version.properties` gerado por `processResources` a partir de `build.gradle`. Commit
  `e63802c` na branch `chore/release-harness` (ainda sem PR).
- **D-063 — macOS DMG documentado como quebrado em `0.x.x`.** Causa raiz do `jpackage` (rejeita
  `--app-version` iniciando em `0`) diagnosticada e registrada; **não corrigida** — qualquer
  workaround faria a versão exibida no mac divergir da de Windows/Linux. Reavaliar perto de
  `v1.0.0`. Commit `dfdbb1d` na mesma branch, com `docs/operacao-release.md` novo.
- **Releases publicadas:** `v0.1.0` → `v0.1.1` → `v0.1.2` → `v0.2.0` (retagueada uma vez, ver
  `docs/operacao-release.md#5`, sobre o fix de UI que só foi percebido depois do primeiro publish).
  Windows/MSI e Linux/DEB no ar em todas; macOS/DMG falha em todas por D-063.
- **Pré-plano da Calculadora** (`docs/pesquisa/2026-07-31-pre-plano-calculadora.md`, pesquisa de
  outra sessão/ferramenta, ainda **não é decisão aprovada**): recomenda não incorporar a Calculadora
  como camada obrigatória; propõe spike isolado (Fase A) antes de qualquer bloco de produto. Ver
  "Próximos passos" abaixo.

## Blocos fechados (podados)

- **B10 — Upgrade MSI** (`2716b5f`, PR #13): `UpgradeCode` fixo no WiX para que um MSI novo
  substitua o antigo em vez de instalar lado a lado.
- **B9 — Aviso de nova versão do app** (`102840c`, PR #11): worker daemon consulta a última release
  estável via `api.github.com` (orçamento 3s, falha silenciosa), abre modal em versão semântica
  maior; sem download/instalação automática.
- **B8 — Troca atômica do runtime de bases** (`5874dae`, PR #10): `activate → build → atomic
  publish`; R1 nunca é destruído até R2 estar pronto e publicado; fallback de próximo boot
  documentado (D-053, substitui D-048).
- **B7 — Canal próprio de schemas curados** (`c90dd48`, PR #9): bootstrap externo publicado em
  `vBaggio/validador-lote-rtc-bases` (GitHub Pages, canal `nfe-schemas`, chave Ed25519
  `schemas-2026-01`); sem fallback SVRS/ACBr para schemas.
- **B6 — Canal confiável de artefatos externos** (`4468af4`, PR #4, com refinamentos posteriores
  integrados em B7/B8): snapshot único, entrega monotônica, rodapé/spinner/diálogo adaptável.
- **B4 e anteriores** (B0–B5): estrutura local, motor XSD, camada de rejeição, área de trabalho de
  documentos — histórico completo em `git log -p -- .superpowers/sdd/progress.md`.

Planos correspondentes em `docs/superpowers/plans/*.md` (B6, canal-confiavel-schemas;
B7, canal-proprio-schemas-curados; B8, troca-atomica-runtime-bases; B6-refinamento,
fluxo-observavel-atualizacao-bases) **ainda não foram movidos para `done/`** apesar dos blocos
estarem mergeados — débito de arquivamento (§8), não urgente, mas verdadeiro. Antes de mover,
conferir tarefa a tarefa se cada plano fechou por inteiro ou só em parte.

## Débitos abertos conhecidos (não exaustivo — ver `progress.md` para o histórico completo)

- Arquivar os 4 planos de B6–B8 para `docs/superpowers/plans/done/` (checagem tarefa a tarefa antes
  de mover).
- ~~`docs/pesquisa/candidatas-rejeicao-pos-b6.md` tem duas versões a reconciliar~~ — **falso,
  herdado sem checar de uma versão anterior deste arquivo.** `git log --follow` mostra reconciliação
  única em `f91e27a` (28/07/2026); só existe um arquivo. Real: esse documento é a matriz canônica
  das 157 regras UB da NT (32 entregues até o bloco 7) e **está desatualizado em dois pontos**: (1)
  `1150 / UB54a-10` segue listado como "Não recomendar localmente" (linha ~218), mas
  `ItemIbsCompositionRule`/D-061 (31/07/2026) entregou exatamente essa regra como identidade
  aritmética, não recomposição; (2) não cobre a família `W` (totalizações declaradas W35–W60,
  D-060/D-061), que é escopo fora do que o documento declarou ("regras do grupo UB"). Precisa de
  atualização antes de servir de base pra priorizar o próximo bloco.
- 1141/1144 sem fixture de corpus isolado (débito do B6).
- `grupo.xsd` duplicado pode ser removido do repositório (débito de limpeza, sem risco).
- Código 8 do `cCredPres`: `ibsInicio=null` conflaciona "não aplicável" com "vigência não
  informada" — sem efeito hoje (`deduzTotal=false`), mas documentar antes de qualquer atualização
  futura da tabela (ver ledger, linha ~989).
- README não documenta instalação em macOS (o instalador nunca saiu por D-063) — hoje README só
  fala de Windows/Linux, silêncio sobre mac; considerar nota explícita em vez de omissão.
- `ExternalSourcesUseCaseTest.blockedFactoryKeepsTheGateClosedWithoutBlockingPublicationDrain` é
  flaky **pré-existente**: falha 4/4 isolado, passa na suíte completa; confirmado em `origin/main`
  (`5b57954`), não é regressão da sessão de 31/07. Teste de concorrência dependente de ordem.

## Próximos passos propostos (para priorização do dono, nenhum iniciado)

1. **Fechar o harness**: ~~abrir PR~~ feito em `chore/consolida-sessao` (release `v0.2.1`). Resta
   arquivar os planos de B6–B8 em `plans/done/` e atualizar `docs/context.md` (índice + a frase de
   "v1" que hoje promete "conferência de valores via motor oficial" sem qualificar o pré-plano da
   Calculadora).
2. **Calculadora — Fase A (spike)**, só depois do item 1, condicionada às respostas da seção 8 do
   pré-plano (público-alvo, corpus anonimizado, mensagem para dados simulados, setup do usuário,
   limiar de cobertura). Gate A: só segue para Fase B se houver subconjunto real de XMLs
   anonimizados calculável sem inferência de campo.
3. **macOS DMG**: decisão adiada para perto de `v1.0.0` (D-063).
