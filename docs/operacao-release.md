# Como se corta uma release

Referenciado por [`workflow.md`](./workflow.md). Cobre o processo *deste* repositório (o app);
para o canal de bases (schemas/tabelas), ver
[`operacao-canal-schemas-curados.md`](./operacao-canal-schemas-curados.md) e
[`operacao-atualizacao-bases.md`](./operacao-atualizacao-bases.md).

## 1. Gatilho

`.github/workflows/release.yml` dispara em push de tag `v*` (`vMAJOR.MINOR.PATCH`, sem prefixo
livre — o job valida com regex e falha a build se a tag não bater). Gera três instaladores em
paralelo, cada um `needs: windows` (Windows precisa passar primeiro; Linux e macOS rodam depois,
`continue-on-error: true`):

| Job | SO | Artefato | Gate? |
|---|---|---|---|
| `windows` | windows-latest | `.msi` (WiX) | Sim — se falhar, a release não sobe nenhum artefato |
| `linux` | ubuntu-latest | `.deb` | Não — `continue-on-error: true` |
| `macos` | macos-latest | `.dmg` | Não — `continue-on-error: true`, **sempre falha em `0.x.x`** (§4) |

Cada job roda a suíte de testes (`./gradlew clean test`) antes de empacotar — a versão publicada
sempre passou pela suíte completa naquela plataforma específica.

## 2. Política de versão (semver)

Não há automação de bump; a decisão é humana a cada release, seguindo semver puro:

- **patch** (`0.2.0` → `0.2.1`): correção de bug, sem novo escopo de regra ou tela.
- **minor** (`0.2.0` → `0.3.0`): novo escopo de regra fiscal, nova tela/fluxo, extensão de
  cobertura (ex.: totalizações IBS/CBS entrando na v0.2.0 — feature nova, não fix).
- **major**: reservado para `v1.0.0` — sinaliza saída do estágio "pode cometer erros" do README.
  Também é o gatilho natural para reavaliar o bug do macOS (§4).

Ao dúvida entre patch/minor, é decisão do dono do projeto — não assuma, pergunte.

## 3. Passo a passo

```bash
# 1. Branch mergeada em main (PR normal, suíte verde)
git checkout main && git pull --ff-only

# 2. Tag anotada no commit mergeado
git tag -a vX.Y.Z -m "vX.Y.Z: resumo de uma linha"
git push origin vX.Y.Z

# 3. Acompanhar o workflow
gh run list --workflow=release.yml --limit 1
gh run watch <run-id> --exit-status

# 4. Conferir os assets publicados
gh release view vX.Y.Z
```

`build.gradle` lê a versão de `-PappVersion`, que o workflow passa a partir de
`${GITHUB_REF_NAME#v}` — não precisa (nem deve) editar `version` em `build.gradle` manualmente
para uma release; o default ali (`findProperty('appVersion') ?: '0.2.0'`) é só para build local
(D-062). **Atualize esse default** a cada release para não ficar defasado.

## 4. macOS DMG: conhecido quebrado em `0.x.x` {#macos-dmg-conhecido-quebrado}

O job `macos` falha de forma determinística em toda release `v0.x.x` (confirmado em `v0.1.0`,
`v0.1.1`, `v0.1.2`, `v0.2.0`): `jpackage` recusa `--app-version` cujo primeiro número é `0`
("The first number in an app-version cannot be zero or negative"). Não bloqueia a release
(`continue-on-error: true`), mas **não existe instalador `.dmg` até isso ser resolvido**. Detalhe
e trade-offs da correção em [D-063](./decisions.md).

Não tente "corrigir" isso passando um `--app-version` diferente só para o job macOS sem entender a
consequência: esse valor aparece para o usuário mac (Sobre o app, Finder). Uma versão mac
divergente da versão Windows/Linux é uma regressão de UX pior que a ausência de instalador.

## 5. Corrigir uma release já publicada

Cenário real (v0.2.0, 31/07/2026): a release subiu com um bug que só foi visto depois — o texto do
diálogo estava desalinhado e a versão de dev não tinha atualizado. Duas opções, escolha do dono:

- **Sobrescrever a tag** — apaga a release e a tag antiga, recria no commit corrigido, mesmo
  número de versão. Só é aceitável **antes de distribuição ampla** (projeto solo/early-stage,
  poucas horas desde a publicação, sem usuários que já baixaram e ficariam com metadado
  divergente). Fora dessas condições, é reescrita de histórico público — trate como
  force-push: nunca sem autorização explícita.

  ```bash
  gh release delete vX.Y.Z --yes --cleanup-tag
  git tag -a vX.Y.Z -m "vX.Y.Z: resumo" <novo-commit>
  git push origin vX.Y.Z
  ```

- **Nova tag incremental** (`vX.Y.(Z+1)`) — sempre seguro, é o padrão semver default. Preserva a
  release antiga como histórico real. Prefira esta opção quando em dúvida.

## 6. Assinatura de código

Não há assinatura de código configurada (Windows Authenticode nem Apple Developer ID/notarização)
— decisão original do design v0 (`docs/superpowers/specs/2026-07-26-validador-lote-rtc-design.md`,
"Sem assinatura de código no v0"), reafirmada nesta sessão (31/07/2026): sem certificado
disponível, o Windows exibe aviso do SmartScreen, documentado no README. Se algum dia houver
certificado, o workflow precisa de um passo `signtool`/`codesign` novo — não existe hoje.
