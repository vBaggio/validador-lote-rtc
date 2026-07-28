# Plano de Implementação — Validador de Lote RTC v0 — Blocos B0-B2 (entregues)

> Arquivado em 28/07/2026 ao secar o harness (muitos arquivos pesando a leitura por sessão).
> Conteúdo integral das tasks 1-17, todas mergeadas em `main`. Ver `docs/superpowers/plans/2026-07-26-v0-validador-lote-rtc.md`
> para o Global Constraints vigente e os blocos B3-B5 (em curso/pendentes), e `.superpowers/sdd/progress.md`
> para o ledger resumido de cada task.

---

## Bloco B0 — Fundação e harness (branch `bloco/0-harness`)

### Task 1: Repo público no GitHub + push da main

**Files:** nenhum novo (usa estado atual da `main`: spec + docs/calculadora).

**Interfaces:**
- Produces: repo `github.com/vBaggio/validador-lote-rtc` público com `main` publicada; remote `origin` configurado.

- [ ] **Step 1: Criar repo público e push**

Run: `cd /var/home/vbaggio/Documents/dev/projects/validador-lote-rtc && gh repo create vBaggio/validador-lote-rtc --public --source=. --remote=origin --push`
Expected: `✓ Created repository vBaggio/validador-lote-rtc` e push da `main` com sucesso.

- [ ] **Step 2: Verificar**

Run: `gh repo view vBaggio/validador-lote-rtc --json visibility,defaultBranchRef -q '.visibility + " " + .defaultBranchRef.name'`
Expected: `PUBLIC main`

- [ ] **Step 3: Criar branch do bloco**

Run: `git checkout -b bloco/0-harness`
Expected: `Switched to a new branch 'bloco/0-harness'`

### Task 2: Base Gradle (wrapper + build.gradle)

**Files:**
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (copiados de `../zaporcs-core`, Gradle 8.14.3)
- Create: `settings.gradle`, `build.gradle`, `.gitattributes`

**Interfaces:**
- Produces: `./gradlew test` funcional; task `updateSchemas` (rede, uso manual); deps de teste JUnit/AssertJ/ArchUnit disponíveis.

- [ ] **Step 1: Copiar wrapper do zaporcs-core**

Run:
```bash
cd /var/home/vbaggio/Documents/dev/projects/validador-lote-rtc
cp ../zaporcs-core/gradlew ../zaporcs-core/gradlew.bat .
mkdir -p gradle/wrapper
cp ../zaporcs-core/gradle/wrapper/gradle-wrapper.jar ../zaporcs-core/gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
chmod +x gradlew
```
Expected: arquivos presentes; `grep distributionUrl gradle/wrapper/gradle-wrapper.properties` mostra `gradle-8.14.3-bin.zip`.

- [ ] **Step 2: Criar settings.gradle**

```groovy
rootProject.name = 'validador-lote-rtc'
```

- [ ] **Step 3: Criar build.gradle**

```groovy
plugins {
    id 'java'
    id 'application'
}

group = 'br.com.validadorlote'
version = '0.1.0'

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'com.formdev:flatlaf:3.6'

    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testImplementation 'org.assertj:assertj-core:3.26.3'
    testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

application {
    mainClass = 'br.com.validadorlote.App'
}

test {
    useJUnitPlatform {
        excludeTags 'slow'
    }
}

tasks.register('slowTest', Test) {
    description = 'Roda testes lentos (@Tag("slow")).'
    group = 'verification'
    useJUnitPlatform { includeTags 'slow' }
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
}

// Baixa o pacote oficial da Calculadora e re-extrai os XSDs para resources.
// Uso manual quando a RFB atualizar a base. Rede APENAS aqui — nunca no build.
tasks.register('updateSchemas') {
    description = 'Atualiza src/main/resources/schemas a partir do JAR oficial da Calculadora.'
    group = 'build setup'
    doLast {
        def endpoint = 'https://piloto-cbs.tributos.gov.br/servico/calculadora-consumo/api/calculadora/download/url?platform=default'
        def downloadUrl = new groovy.json.JsonSlurper().parse(new URL(endpoint)).downloadUrl
        def zipFile = layout.buildDirectory.file('calculadora/calculadora.zip').get().asFile
        zipFile.parentFile.mkdirs()
        logger.lifecycle("Baixando ${downloadUrl} (~250 MB)...")
        new URL(downloadUrl).withInputStream { i -> zipFile.withOutputStream { o -> o << i } }
        def unzipDir = layout.buildDirectory.dir('calculadora/unzipped').get().asFile
        copy { from zipTree(zipFile); into unzipDir; include '**/api-regime-geral.jar' }
        def jar = fileTree(unzipDir).matching { include '**/api-regime-geral.jar' }.singleFile
        def schemasDir = file('src/main/resources/schemas')
        delete fileTree(schemasDir) { include 'nfe/**', 'nfce/**' }
        copy {
            from zipTree(jar)
            into schemasDir
            include 'BOOT-INF/classes/xml/nfe/**', 'BOOT-INF/classes/xml/nfce/**'
            eachFile { it.path = it.path.replaceFirst('BOOT-INF/classes/xml/', '') }
            includeEmptyDirs = false
        }
        def props = file('src/main/resources/schemas/schemas-version.properties')
        def old = new Properties()
        if (props.exists()) { props.withInputStream { old.load(it) } }
        props.text = """engineVersion=${old.getProperty('engineVersion', 'ATUALIZAR-MANUALMENTE')}
baseVersion=${old.getProperty('baseVersion', 'ATUALIZAR-MANUALMENTE')}
extractedAt=${new Date().format('yyyy-MM-dd')}
sourceUrl=${downloadUrl}
"""
        logger.lifecycle('Schemas atualizados. CONFIRA engineVersion/baseVersion em schemas-version.properties (endpoint dados-abertos/versao).')
    }
}
```

- [ ] **Step 4: Criar .gitattributes**

```
* text=auto eol=lf
*.bat text eol=crlf
*.jar binary
gradlew text eol=lf
```

- [ ] **Step 5: Verificar build vazio**

Run: `./gradlew test --console=plain`
Expected: `BUILD SUCCESSFUL` (sem testes ainda; primeiro run baixa o Gradle 8.14.3).

- [ ] **Step 6: Commit**

```bash
git add gradlew gradlew.bat gradle/ settings.gradle build.gradle .gitattributes
git commit -m "build(b0): base Gradle 8.14.3 com wrapper, deps de teste e task updateSchemas"
```

### Task 3: Seed dos schemas oficiais + proveniência

**Files:**
- Create: `src/main/resources/schemas/nfe/{nota.xsd,grupo.xsd}`, `src/main/resources/schemas/nfe/originais/{leiauteNFe_v4.00.xsd,tiposBasico_v4.00.xsd,DFeTiposBasicos_v1.00.xsd,nfe_v4.00.xsd,xmldsig-core-schema_v1.01.xsd}` e a árvore equivalente `schemas/nfce/`
- Create: `src/main/resources/schemas/schemas-version.properties`

**Interfaces:**
- Produces: classpath `/schemas/nfe/nota.xsd` (entrypoint de validação, raízes `NFe|nfeProc|enviNFe`) com includes relativos `./originais/...`; `/schemas/schemas-version.properties`.

- [ ] **Step 1: Extrair do JAR vivo da descoberta (caminho preferencial)**

O JAR baixado na descoberta ainda está no scratchpad da sessão:

```bash
JAR=/tmp/claude-1000/-var-home-vbaggio-Documents-dev-projects-validador-lote-rtc/a6614ec7-389d-4636-86c8-befee496a527/scratchpad/discovery/calc-tar/calculadora/api-regime-geral.jar
cd /var/home/vbaggio/Documents/dev/projects/validador-lote-rtc
mkdir -p src/main/resources/schemas
cd src/main/resources/schemas
unzip -o "$JAR" 'BOOT-INF/classes/xml/nfe/*' 'BOOT-INF/classes/xml/nfce/*'
mv BOOT-INF/classes/xml/nfe BOOT-INF/classes/xml/nfce .
rm -rf BOOT-INF
```

**Fallback** (se o JAR não existir mais): `cd /var/home/vbaggio/Documents/dev/projects/validador-lote-rtc && ./gradlew updateSchemas` (baixa ~250 MB) e depois preencha `engineVersion=1.2.4` / `baseVersion=V0039 (2026-07-08)` no properties.

- [ ] **Step 2: Verificar árvore**

Run: `find src/main/resources/schemas -name '*.xsd' | sort`
Expected (14 arquivos):
```
src/main/resources/schemas/nfce/grupo.xsd
src/main/resources/schemas/nfce/nota.xsd
src/main/resources/schemas/nfce/originais/DFeTiposBasicos_v1.00.xsd
src/main/resources/schemas/nfce/originais/leiauteNFe_v4.00.xsd
src/main/resources/schemas/nfce/originais/nfe_v4.00.xsd
src/main/resources/schemas/nfce/originais/tiposBasico_v4.00.xsd
src/main/resources/schemas/nfce/originais/xmldsig-core-schema_v1.01.xsd
src/main/resources/schemas/nfe/grupo.xsd
src/main/resources/schemas/nfe/nota.xsd
src/main/resources/schemas/nfe/originais/DFeTiposBasicos_v1.00.xsd
src/main/resources/schemas/nfe/originais/leiauteNFe_v4.00.xsd
src/main/resources/schemas/nfe/originais/nfe_v4.00.xsd
src/main/resources/schemas/nfe/originais/tiposBasico_v4.00.xsd
src/main/resources/schemas/nfe/originais/xmldsig-core-schema_v1.01.xsd
```

- [ ] **Step 3: Criar schemas-version.properties**

`src/main/resources/schemas/schemas-version.properties`:
```properties
engineVersion=1.2.4
baseVersion=V0039 (2026-07-08)
extractedAt=2026-07-26
sourceUrl=https://obs-13820-calcpr-apr.obsv3.br-df-1.hcs.serpro.gov.br/calculadora.zip
```

- [ ] **Step 4: Commit**

```bash
cd /var/home/vbaggio/Documents/dev/projects/validador-lote-rtc
git add src/main/resources/schemas
git commit -m "feat(b0): schemas XSD oficiais (motor 1.2.4 / base V0039) com proveniência"
```

### Task 4: Documentação canônica do harness

**Files:**
- Create: `CLAUDE.md`, `docs/context.md`, `docs/architecture.md`, `docs/conventions.md`, `docs/testing.md`, `docs/decisions.md`

**Interfaces:**
- Produces: documentação canônica tool-agnostic; `decisions.md` com D-001..D-014.

- [ ] **Step 1: Criar CLAUDE.md**

```markdown
# validador-lote-rtc

Validador desktop offline de lotes de XML NF-e/NFC-e contra os schemas oficiais da
Reforma Tributária do Consumo (IBS/CBS, NT 2025.002). Java 21 + Swing/FlatLaf.

A documentação canônica vive em [`docs/`](./docs/) e é tool-agnostic. Este arquivo só aponta para lá.

## Antes de qualquer tarefa em contexto limpo

Leia [`docs/context.md`](./docs/context.md) — projeto, princípios e índice completo.

## Regras críticas

- **Regra de dependência**: `presentation → application → {domain, infrastructure}`; `infrastructure → domain`; `domain → nada`. `javax.swing`/`java.awt` SÓ em `presentation/`. ArchUnit garante.
- **Parsing XML sempre seguro** (DOCTYPE proibido, sem entidades externas). XML de terceiro é não-confiável.
- **Julgamento fiscal vem de artefato oficial** (schemas da RFB). Nunca criar tabela fiscal hardcoded, nunca reescrever mensagem oficial — traduções ficam em resources.
- **Código em inglês, mensagens/docs em pt-BR.** Comentários enxutos; javadoc onde agrega.
- **1 commit semântico por task**, escopo do bloco (`feat(b2): ...`). Branch por bloco + PR.
- Spec e plano vigentes: [`docs/superpowers/`](./docs/superpowers/). Decisões: [`docs/decisions.md`](./docs/decisions.md).
```

- [ ] **Step 2: Criar docs/context.md**

```markdown
# Contexto

## O que é

**Validador de Lote RTC** — ferramenta desktop, offline e independente (sem vínculo com
RFB/SEFAZ) que valida em lote XMLs de NF-e (modelo 55) e NFC-e (modelo 65) contra os
schemas XSD oficiais da Reforma Tributária do Consumo, agrupa os achados por causa-raiz
e exporta CSV. Público: contadores e responsáveis fiscais, não-técnicos, Windows-first.

A partir de 03/08/2026 a SEFAZ rejeita documentos de emitentes CRT=3 com grupos IBS/CBS
fora da NT 2025.002. As ferramentas oficiais validam 1 documento por vez; esta valida
centenas, coletando TODOS os erros de cada arquivo (o endpoint oficial para no primeiro).

## Princípios

1. **Nenhum dado sai da máquina por padrão** — sem telemetria, sem rede em runtime no v0.
2. **A ferramenta nunca decide tributo** — julgamento vem de artefato oficial (schemas; na v1, motor `regime-geral`).
3. **Zero pré-requisitos** — instalador nativo com runtime embarcado.
4. **Vida útil curta declarada** — simplicidade > extensibilidade.

## Fases

- **v0.x (atual)**: validação estrutural local + agrupamento + CSV + UI + instalador Windows.
- **v1**: conferência de valores via motor oficial (`regime-geral` como processo filho),
  relatório narrativo por IA opcional (BYOK). Ver spec §3.2 e decisões D-012/D-014.

## Índice

1. [`architecture.md`](./architecture.md) — camadas, pacotes, regra de dependência, fluxo
2. [`conventions.md`](./conventions.md) — regras de código, commits, fronteiras
3. [`testing.md`](./testing.md) — estratégia de testes, fixtures, tags
4. [`decisions.md`](./decisions.md) — log ADR-lite (D-001..)
5. [`calculadora/`](./calculadora/) — contrato real da Calculadora RFB (descoberta 26/07/2026)
6. [`superpowers/specs/`](./superpowers/specs/) — spec de design aprovada
7. [`superpowers/plans/`](./superpowers/plans/) — plano de implementação vigente
```

- [ ] **Step 3: Criar docs/architecture.md**

```markdown
# Arquitetura

Arquitetura em **camadas + MVP** na apresentação. O caso de uso de lote é um fluxo em
estágios (scan → parse → validação XSD → agrupamento → relatório).

## Pacotes (`br.com.validadorlote`)

| Pacote | Responsabilidade |
|---|---|
| `App` | bootstrap; constrói o grafo de objetos manualmente (sem DI framework) |
| `presentation/` | Swing em MVP: views passivas atrás de interfaces + `MainPresenter`; EDT só aqui |
| `application/` | `ValidateBatchUseCase`: pool de workers, progresso, cancelamento, montagem do relatório |
| `domain/` | records puros + `RootCauseGrouper` + `FindingReclassifier`; não importa nada de fora |
| `infrastructure/fs` | `FolderScanner` |
| `infrastructure/xml` | `XmlMetadataParser`, `SchemaValidatorEngine`, `XsdErrorTranslator`, `SchemasVersion` |
| `infrastructure/csv` | `CsvExporter` |
| `infrastructure/calculator` | (v1) processo filho do motor oficial, client, adapter |

## Regra de dependência (ArchUnit garante)

`presentation → application → {domain, infrastructure}`; `infrastructure → domain`;
`domain → nada`. `javax.swing`/`java.awt` proibidos fora de `presentation/`.
Sem interfaces-porta cerimoniais (D-006); o único contrato volátil (RFB) isola-se no
adapter da v1. Views atrás de interface + `ProgressListener` neutro = frontend trocável.

## Fluxo do lote (v0)

1. `FolderScanner` varre a pasta recursivamente (`.xml`)
2. Pool fixo (`availableProcessors`); por arquivo: `XmlMetadataParser` (StAX seguro,
   índice linha→item) → falha vira achado `UNREADABLE`; senão `SchemaValidatorEngine`
   (Schema único compilado no boot; `Validator` por documento; `ErrorHandler` coletor)
3. `FindingReclassifier` aplica o modo pré-emissão (assinatura ausente → INFO/REJECTION)
4. `RootCauseGrouper` agrupa por `(kind, xsdCode, field)`, ordena por documentos afetados
5. `BatchReport` → UI (mestre-detalhe) e `CsvExporter` (2 arquivos, UTF-8 BOM, `;`)

## Schemas oficiais

`src/main/resources/schemas/{nfe,nfce}/` — extraídos do JAR oficial da Calculadora
(proveniência em `schemas-version.properties`; atualização via `./gradlew updateSchemas`).
Entrypoint de validação: `/schemas/nfe/nota.xsd` (declara `NFe`, `nfeProc`, `enviNFe`;
cobre modelos 55 e 65). Includes relativos resolvem via systemId de URL do classpath.
O contrato real da Calculadora (endpoints, quirks) está documentado em
[`calculadora/contrato-validar-xml.md`](./calculadora/contrato-validar-xml.md).
```

- [ ] **Step 4: Criar docs/conventions.md**

```markdown
# Convenções

## Código

- Java 21. Records para dados; injeção por construtor; sem DI framework; sem dead code.
- Código em **inglês**; mensagens de UI/erros amigáveis/docs em **pt-BR**.
- Comentários pontuais apenas onde o código não consegue dizer; javadoc em API pública de domínio/application.
- DTO/record de domínio não conhece formato externo (Xerces/CSV/Swing).

## XML (inegociável)

- Todo parser/factory com `FEATURE_SECURE_PROCESSING`; DOCTYPE proibido
  (`disallow-doctype-decl`); `ACCESS_EXTERNAL_DTD`/`ACCESS_EXTERNAL_SCHEMA` vazios;
  StAX com `SUPPORT_DTD=false` e `IS_SUPPORTING_EXTERNAL_ENTITIES=false`.
- `Schema` compilado 1× (thread-safe); `Validator` por documento; validação via SAXSource streaming.
- Mensagens oficiais (`cvc-*`) nunca são reescritas — tradução amigável vem da tabela em
  `resources/messages/xsd-translations.properties`, chaveada por código+campo, locale-independente.

## Fronteiras

- Regra de dependência conforme `architecture.md`, garantida por ArchUnit (`ArchitectureTest`).
- `javax.swing`/`java.awt` só em `presentation/`. EDT-marshalling só no adapter Swing (`UiThread`).

## Git

- Branch por bloco (`bloco/N-nome`), PR por bloco, review antes do merge (merge commit, não squash — preserva 1 commit/task).
- Commits semânticos com escopo do bloco: `feat(b2): ...`, `fix(b3): ...`, `test(b1): ...`, `docs(b0): ...`, `build(b5): ...`.
- Ajuste sequencial no que o último commit (não pushado) entregou → `git commit --amend`, nunca cadeia de fixes.
- Decisão nova → entrada no `decisions.md` no MESMO PR. Decisão-chave → confirmar com o Vinícius antes.

## Erros

- Lote nunca aborta por 1 arquivo: falhas por arquivo viram achado `UNREADABLE` (WARNING).
- Exceções de infraestrutura carregam contexto (arquivo, causa) e mensagem pt-BR quando chegam à UI.
```

- [ ] **Step 5: Criar docs/testing.md**

```markdown
# Testes

Estratégia **leve e dirigida** (D-010): JUnit 5 + AssertJ; ArchUnit para fronteiras; sem
gate de cobertura — cobertura por criticidade, conferida no review de cada bloco.

| Alvo | Foco |
|---|---|
| `RootCauseGrouper`, `FindingReclassifier` | chaves, ordenação, contagens, severidades |
| `SchemaValidatorEngine` | fixtures reais; coleta total (>1 erro por doc); includes resolvidos; DOCTYPE rejeitado |
| `XmlMetadataParser` | metadados, índice linha→item, malformados |
| `XsdErrorTranslator` | códigos conhecidos → pt-BR; desconhecido → fallback |
| `CsvExporter` | golden: BOM, `;`, escaping, acentos, CRLF |
| `ValidateBatchUseCase` | lote misto, cancelamento, pasta vazia, progresso |
| `MainPresenter` | transições de estado com view fake |

- Testes **não** asseguram texto integral de mensagem Xerces (localiza por JVM) — asserte `xsdCode`, `field`, `line`.
- Fixtures em `src/test/resources/fixtures/`; semeadas de `docs/calculadora/payloads/`.
- Nomes: `<Classe>Test`, métodos descritivos sem prefixo `test`.
- `@Tag("slow")` fica fora do build padrão; rodar com `./gradlew slowTest`.
- `presentation/` Swing (views) sem teste automatizado; presenter tem.
```

- [ ] **Step 6: Criar docs/decisions.md**

```markdown
# Decisões

Log ADR-lite. Cada entrada: **Decisão**, contexto curto e consequência. Mais recentes no topo.
Template no fim. Decisões D-001..D-014 nasceram no brainstorm de 26/07/2026 (spec
[`superpowers/specs/2026-07-26-validador-lote-rtc-design.md`](./superpowers/specs/2026-07-26-validador-lote-rtc-design.md)).

## D-014 — Relatório narrativo por IA opcional (BYOK) na v1 (26/07/2026)
Última entrega da v1: botão pós-análise, API key do próprio usuário, envia só o relatório
agregado (nunca XMLs), narra achados determinísticos sem julgar tributo. Ideia registrada:
onboarding de primeiro boot pode unificar credenciais e downloads iniciais.

## D-013 — Assinatura ausente = achado próprio com toggle (26/07/2026)
`SIGNATURE_MISSING` com toggle "XMLs pré-emissão" (default ligado → INFO; desligado →
REJECTION). Público valida antes de emitir; sem isso, 100% dos docs teriam ruído.

## D-012 — PENDENTE: fonte do motor na v1 (26/07/2026)
Embutir × download no 1º uso. Decidir no início da v1. Fato: pacote oficial **sem licença**
(ver [`calculadora/licenca-calculadora.txt`](./calculadora/licenca-calculadora.txt)).

## D-011 — Fluxo git: branch por bloco + PR (26/07/2026)
1 commit semântico por task; review por bloco; merge commit (preserva histórico de tasks).

## D-010 — Testes leves e dirigidos (26/07/2026)
Sem gate de cobertura; criticidade guia (ver `testing.md`). MVP de validade curta.

## D-009 — Matrix 3 SOs com Windows-gate (26/07/2026)
Release exige `.msi` Windows; Linux/macOS `continue-on-error`, anexam se passarem.

## D-008 — jlink enxuto no v0 (26/07/2026)
Sem Spring no v0 → módulos mínimos (~50–80 MB de instalador). Na v1, com motor embarcado,
`ALL-MODULE-PATH`. Fallback imediato se faltar módulo em runtime.

## D-007 — Swing + FlatLaf confirmada (26/07/2026)
Re-julgada (não herdada): runtime dentro do JDK, menor risco de empacotamento. Única
vantagem real do JavaFX (TreeTableView) neutralizada pelo mestre-detalhe.

## D-006 — Camadas + MVP; sem CLI no v0; sem DI framework (26/07/2026)
Regra de dependência com ArchUnit; views atrás de interface; `ProgressListener` neutro;
frontend trocável sem tocar o núcleo. Sem interfaces-porta especulativas.

## D-005 — Schemas oficiais extraídos do JAR da Calculadora, commitados (26/07/2026)
Alinhamento versão motor↔schema garantido; espelho GitHub provou-se desatualizado;
`updateSchemas` re-extrai. Proveniência em `schemas-version.properties`. **Estudo futuro:**
automação da atualização (verificação de nova base pelo app, opt-out).

## D-004 — Entrega faseada (26/07/2026)
v0 = estrutura local (XSD, coleta total); v1 = valores via `regime-geral`. Motivo: endpoint
oficial de validação é XSD-only/fail-fast (descoberta 26/07); estrutura local entrega mais,
mais cedo, sem processo filho.

## D-003 — GPL-3.0 (26/07/2026)

## D-002 — Repo público vBaggio/validador-lote-rtc; Actions; Releases (26/07/2026)

## D-001 — Gradle + Java 21 (26/07/2026)

---

**Template:**
```
## D-0XX — Título curto (DD/MM/AAAA)
Decisão em 1-3 frases. Contexto essencial. Consequência/risco aceito.
```
```

- [ ] **Step 7: Commit**

```bash
git add CLAUDE.md docs/context.md docs/architecture.md docs/conventions.md docs/testing.md docs/decisions.md
git commit -m "docs(b0): documentação canônica do harness (context, architecture, conventions, testing, decisions D-001..D-014)"
```

### Task 5: Agente executor, CI, LICENSE e README inicial

**Files:**
- Create: `.claude/agents/validador-senior-dev.md`, `.github/workflows/ci.yml`, `LICENSE`, `README.md`

**Interfaces:**
- Produces: CI verde em push/PR; agente de desenvolvimento com regras do projeto.

- [ ] **Step 1: Criar .claude/agents/validador-senior-dev.md**

```markdown
---
name: validador-senior-dev
description: Use this agent for ALL development tasks in validador-lote-rtc — features, fixes, refactors and reviews. It enforces the layered architecture, secure XML parsing, official-artifact-only fiscal judgment, and the block/commit workflow.
---

# AGENTE: VALIDADOR-LOTE-RTC SENIOR DEV

## IDENTIDADE
Software Engineer Senior em Java 21, Swing/FlatLaf e processamento de XML fiscal
(NF-e/NFC-e, Reforma Tributária IBS/CBS). Desenvolve o Validador de Lote RTC.

Documentação canônica em `docs/` — leia `docs/context.md` antes de qualquer tarefa em
contexto limpo. Nunca invente comportamento que não está no código, na spec ou na doc.

## LIMITES INEGOCIÁVEIS
1. **Regra de dependência**: `presentation → application → {domain, infrastructure}`;
   `infrastructure → domain`; `domain → nada`. Swing/AWT SÓ em `presentation/`.
2. **Parsing XML seguro SEMPRE** (DOCTYPE proibido, sem entidades externas, secure processing).
3. **Julgamento fiscal só de artefato oficial.** Nunca tabela fiscal hardcoded; nunca
   reescrever mensagem `cvc-*` — traduções na tabela de resources.
4. **`Schema` compilado 1×; `Validator` por documento.** Lote nunca aborta por 1 arquivo.
5. **Código em inglês, mensagens pt-BR.** Records, injeção por construtor, sem dead code.
6. **Testes leves e dirigidos** (`docs/testing.md`); não asserte texto integral Xerces.

## FLUXO DE TRABALHO
- Execução por blocos (branch `bloco/N-nome`, PR por bloco). **1 commit semântico por
  task** com escopo do bloco (`feat(b2): ...`). Antes de entregar: `./gradlew test` verde.
- Bug encontrado → corrigir imediatamente ou registrar achado no relatório da task.
- Decisão nova → `docs/decisions.md` no mesmo PR; decisão-chave → perguntar antes.

## AO ENTREGAR
Relate: o que fez, arquivos tocados, resultado dos testes (comando + saída resumida),
desvios do plano e por quê, achados/débitos.
```

- [ ] **Step 2: Criar .github/workflows/ci.yml**

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Build e testes
        run: ./gradlew build --console=plain
```

- [ ] **Step 3: Baixar LICENSE GPL-3.0**

Run: `curl -fsS https://www.gnu.org/licenses/gpl-3.0.txt -o LICENSE && head -2 LICENSE`
Expected: `GNU GENERAL PUBLIC LICENSE` / `Version 3, 29 June 2007`

- [ ] **Step 4: Criar README.md inicial (versão de construção; o definitivo vem no B5)**

```markdown
# Validador de Lote RTC

> ⚠️ **Em construção** — primeira release em breve.

Ferramenta desktop **offline** e **independente** (sem vínculo com Receita Federal ou
SEFAZ) que valida **em lote** XMLs de NF-e/NFC-e contra os schemas oficiais da Reforma
Tributária do Consumo (grupos IBS/CBS, NT 2025.002), agrupa os problemas por causa-raiz
e exporta relatório CSV.

- **Nenhum dado sai da sua máquina.** Sem cadastro, sem telemetria, sem envio de XML.
- **Todos os erros de cada arquivo** — não só o primeiro.
- Windows primeiro; Linux/macOS best-effort.

Licença: [GPL-3.0](./LICENSE). Decisões e arquitetura: [`docs/`](./docs/).
```

- [ ] **Step 5: Commit**

```bash
git add .claude/agents/validador-senior-dev.md .github/workflows/ci.yml LICENSE README.md
git commit -m "docs(b0): agente executor, workflow de CI, GPL-3.0 e README inicial"
```

### Task 6: Fechamento do Bloco 0 (PR + merge)

**Files:** nenhum novo.

- [ ] **Step 1: Push e PR**

```bash
git push -u origin bloco/0-harness
gh pr create --title "B0: fundação e harness" --body "$(cat <<'EOF'
Bloco 0: base Gradle 8.14.3, schemas oficiais (motor 1.2.4/V0039) com proveniência,
documentação canônica (context/architecture/conventions/testing/decisions D-001..D-014),
agente executor, CI, GPL-3.0 e README inicial.

Conforme spec docs/superpowers/specs/2026-07-26-validador-lote-rtc-design.md.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
Expected: URL do PR criada.

- [ ] **Step 2: Aguardar CI verde**

Run: `gh pr checks --watch`
Expected: check `build` com ✓.

- [ ] **Step 3: GATE DE REVIEW DO BLOCO** — o orquestrador (Fable) revisa o PR antes do merge. Não faça merge sem o review do bloco.

- [ ] **Step 4: Merge (merge commit, preserva 1 commit/task)**

```bash
gh pr merge --merge --delete-branch
git checkout main && git pull
```
Expected: `main` atualizada com os 5 commits do bloco + merge commit.

---
## Bloco B1 — Domínio, varredura e parse (branch `bloco/1-dominio-scan`)

Antes da primeira task: `git checkout main && git pull && git checkout -b bloco/1-dominio-scan`

### Task 7: Records e enums do domínio + FindingReclassifier

**Files:**
- Create: `src/main/java/br/com/validadorlote/domain/FindingKind.java`, `Severity.java`, `FiscalDocument.java`, `Finding.java`, `RootCauseKey.java`, `RootCause.java`, `BatchReport.java`, `RootCauseTexts.java`, `FindingReclassifier.java`
- Test: `src/test/java/br/com/validadorlote/domain/FindingReclassifierTest.java`

**Interfaces:**
- Produces (assinaturas exatas usadas por TODAS as tasks seguintes):
```java
public enum FindingKind { SCHEMA, SIGNATURE_MISSING, UNREADABLE }
public enum Severity { REJECTION, WARNING, INFO }
public record FiscalDocument(Path source, String accessKey, String emitterCnpj,
    String documentNumber, LocalDate issueDate, String model, String rootElement) {}
public record Finding(Path source, String accessKey, Integer itemNumber, FindingKind kind,
    Severity severity, String field, String xsdCode, String officialMessage,
    String friendlyMessage, Integer line, Integer column) { public Finding withSeverity(Severity s); }
public record RootCauseKey(FindingKind kind, String xsdCode, String field) {}
public record RootCause(RootCauseKey key, String friendlyExplanation, String suggestedAction,
    List<Finding> findings, int affectedDocuments) {}
public record BatchReport(Instant startedAt, Duration elapsed, int documentsScanned,
    int documentsWithFindings, int documentsUnreadable, boolean cancelled,
    List<RootCause> rootCauses, String schemasVersion) {}
public interface RootCauseTexts {
    Optional<String> explanation(RootCauseKey key);
    Optional<String> action(RootCauseKey key);
}
public final class FindingReclassifier {
    public static List<Finding> reclassify(List<Finding> findings, boolean preEmissionMode);
}
```

- [ ] **Step 1: Escrever teste que falha**

`src/test/java/br/com/validadorlote/domain/FindingReclassifierTest.java`:
```java
package br.com.validadorlote.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FindingReclassifierTest {

    private Finding finding(FindingKind kind, Severity severity) {
        return new Finding(Path.of("a.xml"), null, null, kind, severity,
                "Signature", "cvc-complex-type.2.4.b", "msg", null, 10, 5);
    }

    @Test
    void preEmissionOnTurnsSignatureMissingIntoInfo() {
        var result = FindingReclassifier.reclassify(
                List.of(finding(FindingKind.SIGNATURE_MISSING, Severity.REJECTION)), true);
        assertThat(result).singleElement()
                .extracting(Finding::severity).isEqualTo(Severity.INFO);
    }

    @Test
    void preEmissionOffTurnsSignatureMissingIntoRejection() {
        var result = FindingReclassifier.reclassify(
                List.of(finding(FindingKind.SIGNATURE_MISSING, Severity.INFO)), false);
        assertThat(result).singleElement()
                .extracting(Finding::severity).isEqualTo(Severity.REJECTION);
    }

    @Test
    void otherKindsAreUntouched() {
        var schema = finding(FindingKind.SCHEMA, Severity.REJECTION);
        var unreadable = finding(FindingKind.UNREADABLE, Severity.WARNING);
        var result = FindingReclassifier.reclassify(List.of(schema, unreadable), true);
        assertThat(result).containsExactly(schema, unreadable);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.domain.*' --console=plain`
Expected: FALHA de compilação (classes não existem).

- [ ] **Step 3: Implementar o domínio**

Cada record/enum em seu arquivo, pacote `br.com.validadorlote.domain`, com javadoc de 1 linha. Conteúdo integral:

`FindingKind.java`:
```java
package br.com.validadorlote.domain;

/** Natureza de um achado. A v1 adiciona VALUE (divergência de valores). */
public enum FindingKind { SCHEMA, SIGNATURE_MISSING, UNREADABLE }
```

`Severity.java`:
```java
package br.com.validadorlote.domain;

/** Severidade de um achado na régua da SEFAZ. */
public enum Severity { REJECTION, WARNING, INFO }
```

`FiscalDocument.java`:
```java
package br.com.validadorlote.domain;

import java.nio.file.Path;
import java.time.LocalDate;

/** Metadados de um documento fiscal lido do disco. Campos null quando não extraíveis. */
public record FiscalDocument(Path source, String accessKey, String emitterCnpj,
        String documentNumber, LocalDate issueDate, String model, String rootElement) {}
```

`Finding.java`:
```java
package br.com.validadorlote.domain;

import java.nio.file.Path;

/** Um problema num documento: um erro de schema, assinatura ausente ou arquivo ilegível. */
public record Finding(Path source, String accessKey, Integer itemNumber, FindingKind kind,
        Severity severity, String field, String xsdCode, String officialMessage,
        String friendlyMessage, Integer line, Integer column) {

    public Finding withSeverity(Severity newSeverity) {
        return new Finding(source, accessKey, itemNumber, kind, newSeverity, field,
                xsdCode, officialMessage, friendlyMessage, line, column);
    }
}
```

`RootCauseKey.java`:
```java
package br.com.validadorlote.domain;

/** Chave de agrupamento por causa-raiz. Campos null participam da igualdade normalmente. */
public record RootCauseKey(FindingKind kind, String xsdCode, String field) {}
```

`RootCause.java`:
```java
package br.com.validadorlote.domain;

import java.util.List;

/** Conjunto de achados que compartilham a mesma causa provável. */
public record RootCause(RootCauseKey key, String friendlyExplanation, String suggestedAction,
        List<Finding> findings, int affectedDocuments) {}
```

`BatchReport.java`:
```java
package br.com.validadorlote.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Resultado completo de uma execução de lote. cancelled=true rotula resultados parciais. */
public record BatchReport(Instant startedAt, Duration elapsed, int documentsScanned,
        int documentsWithFindings, int documentsUnreadable, boolean cancelled,
        List<RootCause> rootCauses, String schemasVersion) {}
```

`RootCauseTexts.java`:
```java
package br.com.validadorlote.domain;

import java.util.Optional;

/** Fonte de textos amigáveis por causa-raiz. Implementada na infraestrutura. */
public interface RootCauseTexts {
    Optional<String> explanation(RootCauseKey key);
    Optional<String> action(RootCauseKey key);
}
```

`FindingReclassifier.java`:
```java
package br.com.validadorlote.domain;

import java.util.List;

/** Aplica o modo pré-emissão: assinatura ausente vira INFO (ligado) ou REJECTION (desligado). */
public final class FindingReclassifier {

    private FindingReclassifier() {}

    public static List<Finding> reclassify(List<Finding> findings, boolean preEmissionMode) {
        Severity signatureSeverity = preEmissionMode ? Severity.INFO : Severity.REJECTION;
        return findings.stream()
                .map(f -> f.kind() == FindingKind.SIGNATURE_MISSING ? f.withSeverity(signatureSeverity) : f)
                .toList();
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.domain.*' --console=plain`
Expected: `BUILD SUCCESSFUL`, 3 testes passando.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/validadorlote/domain src/test/java/br/com/validadorlote/domain
git commit -m "feat(b1): modelo de domínio (records, severidades) e FindingReclassifier"
```

### Task 8: RootCauseGrouper

**Files:**
- Create: `src/main/java/br/com/validadorlote/domain/RootCauseGrouper.java`
- Test: `src/test/java/br/com/validadorlote/domain/RootCauseGrouperTest.java`

**Interfaces:**
- Consumes: `Finding`, `RootCauseKey`, `RootCause`, `RootCauseTexts` (Task 7).
- Produces: `public final class RootCauseGrouper { public List<RootCause> group(List<Finding> findings, RootCauseTexts texts); }` — ordena por `affectedDocuments` desc, depois por total de ocorrências desc.

- [ ] **Step 1: Escrever teste que falha**

```java
package br.com.validadorlote.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RootCauseGrouperTest {

    private static final RootCauseTexts NO_TEXTS = new RootCauseTexts() {
        public Optional<String> explanation(RootCauseKey key) { return Optional.empty(); }
        public Optional<String> action(RootCauseKey key) { return Optional.empty(); }
    };

    private Finding schemaFinding(String file, String field, String code, String message) {
        return new Finding(Path.of(file), null, 1, FindingKind.SCHEMA, Severity.REJECTION,
                field, code, message, null, 10, 1);
    }

    @Test
    void groupsSameKeyAcrossFilesCountingDistinctDocuments() {
        var f1 = schemaFinding("a.xml", "pCBS", "cvc-pattern-valid", "msg oficial");
        var f2 = schemaFinding("b.xml", "pCBS", "cvc-pattern-valid", "msg oficial");
        var f3 = schemaFinding("b.xml", "pCBS", "cvc-pattern-valid", "msg oficial");

        var causes = new RootCauseGrouper().group(List.of(f1, f2, f3), NO_TEXTS);

        assertThat(causes).hasSize(1);
        assertThat(causes.getFirst().affectedDocuments()).isEqualTo(2);
        assertThat(causes.getFirst().findings()).hasSize(3);
        assertThat(causes.getFirst().key())
                .isEqualTo(new RootCauseKey(FindingKind.SCHEMA, "cvc-pattern-valid", "pCBS"));
    }

    @Test
    void ordersByAffectedDocumentsDescThenOccurrencesDesc() {
        var one = schemaFinding("a.xml", "CST", "cvc-enumeration-valid", "m1");
        var many1 = schemaFinding("a.xml", "pCBS", "cvc-pattern-valid", "m2");
        var many2 = schemaFinding("b.xml", "pCBS", "cvc-pattern-valid", "m2");

        var causes = new RootCauseGrouper().group(List.of(one, many1, many2), NO_TEXTS);

        assertThat(causes).extracting(c -> c.key().field()).containsExactly("pCBS", "CST");
    }

    @Test
    void fallsBackToOfficialMessageWhenNoTranslation() {
        var f = schemaFinding("a.xml", "vIBS", "cvc-complex-type.2.4.a", "mensagem xerces");
        var causes = new RootCauseGrouper().group(List.of(f), NO_TEXTS);
        assertThat(causes.getFirst().friendlyExplanation()).isEqualTo("mensagem xerces");
        assertThat(causes.getFirst().suggestedAction()).isNull();
    }

    @Test
    void usesTranslationTextsWhenAvailable() {
        var texts = new RootCauseTexts() {
            public Optional<String> explanation(RootCauseKey key) { return Optional.of("explicação pt-BR"); }
            public Optional<String> action(RootCauseKey key) { return Optional.of("ação pt-BR"); }
        };
        var causes = new RootCauseGrouper()
                .group(List.of(schemaFinding("a.xml", "pCBS", "cvc-pattern-valid", "m")), texts);
        assertThat(causes.getFirst().friendlyExplanation()).isEqualTo("explicação pt-BR");
        assertThat(causes.getFirst().suggestedAction()).isEqualTo("ação pt-BR");
    }

    @Test
    void unreadableFindingsGroupTogetherWithNullCodeAndField() {
        var u1 = new Finding(Path.of("x.xml"), null, null, FindingKind.UNREADABLE,
                Severity.WARNING, null, null, "ilegível", null, null, null);
        var u2 = new Finding(Path.of("y.xml"), null, null, FindingKind.UNREADABLE,
                Severity.WARNING, null, null, "ilegível", null, null, null);
        var causes = new RootCauseGrouper().group(List.of(u1, u2), NO_TEXTS);
        assertThat(causes).hasSize(1);
        assertThat(causes.getFirst().affectedDocuments()).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.domain.RootCauseGrouperTest' --console=plain`
Expected: FALHA de compilação (`RootCauseGrouper` não existe).

- [ ] **Step 3: Implementar**

```java
package br.com.validadorlote.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Agrupa achados por causa-raiz (kind + código XSD + campo), determinístico, sem IA. */
public final class RootCauseGrouper {

    public List<RootCause> group(List<Finding> findings, RootCauseTexts texts) {
        Map<RootCauseKey, List<Finding>> byKey = findings.stream().collect(Collectors.groupingBy(
                f -> new RootCauseKey(f.kind(), f.xsdCode(), f.field()),
                LinkedHashMap::new, Collectors.toList()));

        return byKey.entrySet().stream()
                .map(e -> toRootCause(e.getKey(), e.getValue(), texts))
                .sorted(Comparator.comparingInt(RootCause::affectedDocuments).reversed()
                        .thenComparing(c -> c.findings().size(), Comparator.reverseOrder()))
                .toList();
    }

    private RootCause toRootCause(RootCauseKey key, List<Finding> group, RootCauseTexts texts) {
        int affected = (int) group.stream().map(Finding::source).distinct().count();
        String explanation = texts.explanation(key).orElseGet(() -> group.stream()
                .map(Finding::officialMessage).filter(Objects::nonNull).findFirst().orElse(""));
        return new RootCause(key, explanation, texts.action(key).orElse(null),
                List.copyOf(group), affected);
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.domain.RootCauseGrouperTest' --console=plain`
Expected: 5 testes passando.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/validadorlote/domain/RootCauseGrouper.java src/test/java/br/com/validadorlote/domain/RootCauseGrouperTest.java
git commit -m "feat(b1): RootCauseGrouper com ordenação por documentos afetados"
```

### Task 9: Teste de arquitetura (ArchUnit)

**Files:**
- Test: `src/test/java/br/com/validadorlote/ArchitectureTest.java`

**Interfaces:**
- Consumes: pacotes existentes (`domain`); as regras já cobrem pacotes futuros (`application`, `infrastructure`, `presentation`).

- [ ] **Step 1: Escrever o teste**

```java
package br.com.validadorlote;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "br.com.validadorlote", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainDependsOnNothing = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("..domain..", "java..");

    @ArchTest
    static final ArchRule swingOnlyInPresentation = noClasses()
            .that().resideOutsideOfPackage("..presentation..")
            .should().dependOnClassesThat().resideInAnyPackage("javax.swing..", "java.awt..");

    @ArchTest
    static final ArchRule applicationDoesNotSeePresentation = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..presentation..");

    @ArchTest
    static final ArchRule infrastructureSeesOnlyDomain = noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..presentation..");
}
```

- [ ] **Step 2: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.ArchitectureTest' --console=plain`
Expected: 4 regras passando (só `domain` existe por ora; as demais valem vazias e "armam" a fronteira).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/br/com/validadorlote/ArchitectureTest.java
git commit -m "test(b1): regras de arquitetura com ArchUnit (camadas e fronteira Swing)"
```

### Task 10: FolderScanner

**Files:**
- Create: `src/main/java/br/com/validadorlote/infrastructure/fs/FolderScanner.java`, `ScanException.java`
- Test: `src/test/java/br/com/validadorlote/infrastructure/fs/FolderScannerTest.java`

**Interfaces:**
- Produces: `public final class FolderScanner { public List<Path> scan(Path folder); }` (lança `ScanException` se a pasta não existir/ilegível; lista ordenada, só `.xml` case-insensitive, recursivo).

- [ ] **Step 1: Escrever teste que falha**

```java
package br.com.validadorlote.infrastructure.fs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FolderScannerTest {

    private final FolderScanner scanner = new FolderScanner();

    @Test
    void findsXmlFilesRecursivelyCaseInsensitiveAndSorted(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("b.xml"), "<x/>");
        Files.writeString(dir.resolve("sub/a.XML"), "<x/>");
        Files.writeString(dir.resolve("ignore.txt"), "nada");
        Files.writeString(dir.resolve("sub/ignore.pdf"), "nada");

        var result = scanner.scan(dir);

        assertThat(result).containsExactly(dir.resolve("b.xml"), dir.resolve("sub/a.XML"));
    }

    @Test
    void emptyFolderYieldsEmptyList(@TempDir Path dir) {
        assertThat(scanner.scan(dir)).isEmpty();
    }

    @Test
    void missingFolderThrowsScanException(@TempDir Path dir) {
        assertThatThrownBy(() -> scanner.scan(dir.resolve("nao-existe")))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("não encontrada");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.fs.*' --console=plain`
Expected: FALHA de compilação.

- [ ] **Step 3: Implementar**

`ScanException.java`:
```java
package br.com.validadorlote.infrastructure.fs;

/** Falha ao acessar a pasta de entrada. Mensagem em pt-BR, apta para a UI. */
public class ScanException extends RuntimeException {
    public ScanException(String message) { super(message); }
    public ScanException(String message, Throwable cause) { super(message, cause); }
}
```

`FolderScanner.java`:
```java
package br.com.validadorlote.infrastructure.fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Varre recursivamente uma pasta em busca de arquivos .xml (case-insensitive). */
public final class FolderScanner {

    public List<Path> scan(Path folder) {
        if (!Files.isDirectory(folder)) {
            throw new ScanException("Pasta não encontrada: " + folder);
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new ScanException("Falha ao ler a pasta: " + folder, e);
        }
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.fs.*' --console=plain`
Expected: 3 testes passando (ArchUnit continua verde: `./gradlew test`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/validadorlote/infrastructure/fs src/test/java/br/com/validadorlote/infrastructure/fs
git commit -m "feat(b1): FolderScanner recursivo com filtro .xml"
```

### Task 11: XmlMetadataParser com índice linha→item

**Files:**
- Create: `src/main/java/br/com/validadorlote/infrastructure/xml/XmlMetadataParser.java`, `ParsedMetadata.java`, `ItemLineIndex.java`, `UnreadableXmlException.java`
- Test: `src/test/java/br/com/validadorlote/infrastructure/xml/XmlMetadataParserTest.java`

**Interfaces:**
- Consumes: `FiscalDocument` (Task 7).
- Produces:
```java
public record ParsedMetadata(FiscalDocument document, ItemLineIndex itemIndex) {}
public final class ItemLineIndex {
    public static ItemLineIndex of(List<int[]> startLineToItem); // pares [startLine, nItem], ordenados
    public Integer itemAt(int line);                             // null se antes do 1º det
}
public final class XmlMetadataParser { public ParsedMetadata parse(Path xml); } // lança UnreadableXmlException
public class UnreadableXmlException extends RuntimeException { }
```

- [ ] **Step 1: Escrever teste que falha**

O fixture é construído com `String.join("\n", ...)` para linhas determinísticas (linha 1 = declaração XML; `det nItem=1` abre na linha 7; `det nItem=2` abre na linha 12).

```java
package br.com.validadorlote.infrastructure.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlMetadataParserTest {

    private final XmlMetadataParser parser = new XmlMetadataParser();

    private static final String NFE = String.join("\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",                                    // 1
            "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">",                            // 2
            "  <infNFe versao=\"4.00\" Id=\"NFe35200114200166000187550010000000015123456789\">", // 3
            "    <ide><cUF>35</cUF><mod>55</mod><nNF>15</nNF>",                              // 4
            "      <dhEmi>2026-07-20T10:00:00-03:00</dhEmi></ide>",                          // 5
            "    <emit><CNPJ>14200166000187</CNPJ><xNome>TESTE</xNome></emit>",              // 6
            "    <det nItem=\"1\">",                                                          // 7
            "      <prod><cProd>1</cProd></prod>",                                            // 8
            "      <imposto><IBSCBS><CST>000</CST></IBSCBS></imposto>",                       // 9
            "    </det>",                                                                     // 10
            "    <total/>",                                                                   // 11
            "    <det nItem=\"2\">",                                                          // 12
            "      <prod><cProd>2</cProd></prod>",                                            // 13
            "    </det>",                                                                     // 14
            "  </infNFe>",                                                                    // 15
            "</NFe>");                                                                        // 16

    private Path write(Path dir, String name, String content) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, content);
        return f;
    }

    @Test
    void extractsMetadataFromNfe(@TempDir Path dir) throws IOException {
        var meta = parser.parse(write(dir, "doc.xml", NFE));
        var doc = meta.document();

        assertThat(doc.accessKey()).isEqualTo("35200114200166000187550010000000015123456789");
        assertThat(doc.emitterCnpj()).isEqualTo("14200166000187");
        assertThat(doc.documentNumber()).isEqualTo("15");
        assertThat(doc.issueDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(doc.model()).isEqualTo("55");
        assertThat(doc.rootElement()).isEqualTo("NFe");
    }

    @Test
    void mapsLinesToItems(@TempDir Path dir) throws IOException {
        var meta = parser.parse(write(dir, "doc.xml", NFE));

        assertThat(meta.itemIndex().itemAt(4)).isNull();      // antes do 1º det
        assertThat(meta.itemIndex().itemAt(8)).isEqualTo(1);  // dentro do det 1
        assertThat(meta.itemIndex().itemAt(13)).isEqualTo(2); // dentro do det 2
    }

    @Test
    void acceptsNfeProcRoot(@TempDir Path dir) throws IOException {
        String xml = "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\">" + NFE.substring(NFE.indexOf("<NFe")) + "</nfeProc>";
        var meta = parser.parse(write(dir, "proc.xml", xml.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n", "")));
        assertThat(meta.document().rootElement()).isEqualTo("nfeProc");
        assertThat(meta.document().accessKey()).isEqualTo("35200114200166000187550010000000015123456789");
    }

    @Test
    void malformedXmlThrowsUnreadable(@TempDir Path dir) throws IOException {
        assertThatThrownBy(() -> parser.parse(write(dir, "bad.xml", "<NFe><infNFe>")))
                .isInstanceOf(UnreadableXmlException.class);
    }

    @Test
    void doctypeIsRejected(@TempDir Path dir) throws IOException {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE NFe [<!ENTITY x \"y\">]><NFe/>";
        assertThatThrownBy(() -> parser.parse(write(dir, "dt.xml", xml)))
                .isInstanceOf(UnreadableXmlException.class);
    }

    @Test
    void unknownRootThrowsUnreadable(@TempDir Path dir) throws IOException {
        assertThatThrownBy(() -> parser.parse(write(dir, "other.xml", "<pedido><item/></pedido>")))
                .isInstanceOf(UnreadableXmlException.class)
                .hasMessageContaining("raiz");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.xml.*' --console=plain`
Expected: FALHA de compilação.

- [ ] **Step 3: Implementar**

`UnreadableXmlException.java`:
```java
package br.com.validadorlote.infrastructure.xml;

/** Arquivo que não pôde ser lido como NF-e/NFC-e (corrompido, raiz estranha, DOCTYPE). */
public class UnreadableXmlException extends RuntimeException {
    public UnreadableXmlException(String message) { super(message); }
    public UnreadableXmlException(String message, Throwable cause) { super(message, cause); }
}
```

`ItemLineIndex.java`:
```java
package br.com.validadorlote.infrastructure.xml;

import java.util.List;

/** Mapeia linha do arquivo → nItem do det que a contém (aproximação por linha de abertura). */
public final class ItemLineIndex {

    private final List<int[]> startLineToItem;

    private ItemLineIndex(List<int[]> startLineToItem) {
        this.startLineToItem = List.copyOf(startLineToItem);
    }

    public static ItemLineIndex of(List<int[]> startLineToItem) {
        return new ItemLineIndex(startLineToItem);
    }

    public Integer itemAt(int line) {
        Integer item = null;
        for (int[] pair : startLineToItem) {
            if (pair[0] <= line) item = pair[1];
            else break;
        }
        return item;
    }
}
```

`ParsedMetadata.java`:
```java
package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.FiscalDocument;

/** Resultado do parse de metadados: documento + índice de itens por linha. */
public record ParsedMetadata(FiscalDocument document, ItemLineIndex itemIndex) {}
```

`XmlMetadataParser.java`:
```java
package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.FiscalDocument;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/** Extrai metadados mínimos e o índice linha→item via StAX seguro (sem DTD/entidades externas). */
public final class XmlMetadataParser {

    private static final Set<String> KNOWN_ROOTS = Set.of("NFe", "nfeProc", "enviNFe");

    public ParsedMetadata parse(Path xml) {
        // XMLInputFactory não é thread-safe: uma por chamada (custo irrisório vs I/O).
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (InputStream in = Files.newInputStream(xml)) {
            return read(xml, factory.createXMLStreamReader(in));
        } catch (XMLStreamException | java.io.IOException e) {
            throw new UnreadableXmlException("Arquivo ilegível como XML: " + xml.getFileName(), e);
        }
    }

    private ParsedMetadata read(Path source, XMLStreamReader r) throws XMLStreamException {
        String root = null, accessKey = null, cnpj = null, nNF = null, mod = null;
        LocalDate issueDate = null;
        List<int[]> items = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();

        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = r.getLocalName();
                if (root == null) {
                    if (!KNOWN_ROOTS.contains(name)) {
                        throw new UnreadableXmlException("Elemento raiz não reconhecido: <" + name + ">");
                    }
                    root = name;
                }
                if ("infNFe".equals(name) && accessKey == null) {
                    String id = r.getAttributeValue(null, "Id");
                    if (id != null && id.startsWith("NFe")) accessKey = id.substring(3);
                }
                if ("det".equals(name)) {
                    String nItem = r.getAttributeValue(null, "nItem");
                    if (nItem != null) {
                        items.add(new int[]{r.getLocation().getLineNumber(), Integer.parseInt(nItem)});
                    }
                }
                stack.push(name);
                if (isFirst(stack, "CNPJ", "emit") && cnpj == null) cnpj = r.getElementText().trim();
                else if (isFirst(stack, "nNF", "ide") && nNF == null) nNF = r.getElementText().trim();
                else if (isFirst(stack, "mod", "ide") && mod == null) mod = r.getElementText().trim();
                else if (isFirst(stack, "dhEmi", "ide") && issueDate == null) {
                    String text = r.getElementText().trim();
                    if (text.length() >= 10) issueDate = LocalDate.parse(text.substring(0, 10));
                } else {
                    continue;
                }
                stack.pop(); // getElementText consumiu o END_ELEMENT do elemento atual
            } else if (event == XMLStreamConstants.END_ELEMENT && !stack.isEmpty()) {
                stack.pop();
            }
        }
        return new ParsedMetadata(
                new FiscalDocument(source, accessKey, cnpj, nNF, issueDate, mod, root),
                ItemLineIndex.of(items));
    }

    private boolean isFirst(Deque<String> stack, String element, String parent) {
        var it = stack.iterator();
        return it.hasNext() && it.next().equals(element) && it.hasNext() && it.next().equals(parent);
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.xml.*' --console=plain`
Expected: 6 testes passando. Se `itemAt` divergir de linha, ajuste o TESTE apenas se você tiver contado errado as linhas do fixture — a implementação usa a linha reportada pelo StAX na abertura do `det`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/validadorlote/infrastructure/xml src/test/java/br/com/validadorlote/infrastructure/xml
git commit -m "feat(b1): XmlMetadataParser StAX seguro com índice linha→item"
```

### Task 12: Fechamento do Bloco 1 (PR + merge)

- [ ] **Step 1: Rodar suíte completa**

Run: `./gradlew test --console=plain`
Expected: todos os testes verdes (domain + fs + xml + arquitetura).

- [ ] **Step 2: Push e PR**

```bash
git push -u origin bloco/1-dominio-scan
gh pr create --title "B1: domínio, varredura e parse de metadados" --body "$(cat <<'EOF'
Bloco 1: records do domínio + FindingReclassifier, RootCauseGrouper, regras ArchUnit,
FolderScanner e XmlMetadataParser (StAX seguro, índice linha→item).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
gh pr checks --watch
```
Expected: CI verde.

- [ ] **Step 3: GATE DE REVIEW DO BLOCO** — review do orquestrador antes do merge.

- [ ] **Step 4: Merge**

```bash
gh pr merge --merge --delete-branch
git checkout main && git pull
```

---

## Bloco B2 — Motor XSD, tradutor e fixtures (branch `bloco/2-motor-xsd`)

Antes da primeira task: `git checkout main && git pull && git checkout -b bloco/2-motor-xsd`

### Task 13: XsdErrorTranslator + tabela de traduções

**Files:**
- Create: `src/main/java/br/com/validadorlote/infrastructure/xml/XsdErrorTranslator.java`
- Create: `src/main/resources/messages/xsd-translations.properties`
- Test: `src/test/java/br/com/validadorlote/infrastructure/xml/XsdErrorTranslatorTest.java`

**Interfaces:**
- Consumes: `RootCauseKey`, `RootCauseTexts`, `FindingKind` (Task 7).
- Produces:
```java
public final class XsdErrorTranslator implements RootCauseTexts {
    public record Translation(String message, String action) {}
    public Optional<Translation> translate(FindingKind kind, String xsdCode, String field);
    // RootCauseTexts: explanation/action derivam de translate(key.kind(), key.xsdCode(), key.field())
}
```
- Formato da tabela (properties UTF-8): chave `<xsdCode>.<field>` (específica) com fallback `<xsdCode>` (genérica); `SIGNATURE_MISSING` usa a chave fixa `signature.missing`; valor `mensagem|ação` (ação opcional).

- [ ] **Step 1: Escrever teste que falha**

```java
package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.RootCauseKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XsdErrorTranslatorTest {

    private final XsdErrorTranslator translator = new XsdErrorTranslator();

    @Test
    void specificFieldKeyWinsOverGenericCode() {
        var t = translator.translate(FindingKind.SCHEMA, "cvc-pattern-valid", "pCBS").orElseThrow();
        assertThat(t.message()).contains("pCBS");
        assertThat(t.action()).isNotBlank();
    }

    @Test
    void fallsBackToGenericCodeKey() {
        var t = translator.translate(FindingKind.SCHEMA, "cvc-pattern-valid", "campoInventado").orElseThrow();
        assertThat(t.message()).isNotBlank();
    }

    @Test
    void unknownCodeYieldsEmpty() {
        assertThat(translator.translate(FindingKind.SCHEMA, "cvc-nunca-visto", "x")).isEmpty();
    }

    @Test
    void signatureMissingHasDedicatedText() {
        var t = translator.translate(FindingKind.SIGNATURE_MISSING, null, "Signature").orElseThrow();
        assertThat(t.message().toLowerCase()).contains("assinatura");
    }

    @Test
    void worksAsRootCauseTexts() {
        var key = new RootCauseKey(FindingKind.SCHEMA, "cvc-pattern-valid", "pCBS");
        assertThat(translator.explanation(key)).isPresent();
        assertThat(translator.action(key)).isPresent();
    }

    @Test
    void actionIsOptionalInTable() {
        var t = translator.translate(FindingKind.SCHEMA, "cvc-datatype-valid.1.2.1", "x").orElseThrow();
        assertThat(t.action()).isNull();
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.xml.XsdErrorTranslatorTest' --console=plain`
Expected: FALHA de compilação.

- [ ] **Step 3: Criar a tabela** `src/main/resources/messages/xsd-translations.properties`

Semente inicial — evolui por PR conforme códigos reais aparecerem (fonte opcional de enriquecimento: NT 2025.002 v1.50 em `tmp/` — PDF gigante, ler SOMENTE via subagente dedicado, nunca no contexto principal):

```properties
# Formato: <xsdCode>.<campo>=mensagem|ação   (fallback: <xsdCode>=mensagem|ação)
# NUNCA reescrever a mensagem oficial no código — este arquivo é a única fonte de tradução.

cvc-pattern-valid.pCBS=Alíquota da CBS (pCBS) com formato inválido — o schema exige 2 a 4 casas decimais (ex.: 0.90).|Corrija o campo pCBS do item no seu emissor para o formato aceito (ex.: 0.90).
cvc-pattern-valid.pIBSUF=Alíquota do IBS estadual (pIBSUF) com formato inválido — o schema exige 2 a 4 casas decimais (ex.: 0.05).|Corrija o campo pIBSUF do item para o formato aceito.
cvc-pattern-valid.pIBSMun=Alíquota do IBS municipal (pIBSMun) com formato inválido — o schema exige 2 a 4 casas decimais.|Corrija o campo pIBSMun do item para o formato aceito.
cvc-pattern-valid.cClassTrib=Código de classificação tributária (cClassTrib) com formato inválido — são 6 dígitos (ex.: 000001).|Consulte a tabela oficial de cClassTrib e use um código de 6 dígitos.
cvc-pattern-valid=Valor com formato inválido para o campo apontado na mensagem oficial (casas decimais, tamanho ou padrão).|Compare o valor do campo com o formato exigido pela NT 2025.002.
cvc-enumeration-valid.CST=CST informado não existe na tabela oficial de situações tributárias do IBS/CBS.|Use um CST válido da tabela oficial (ver docs/calculadora/amostras-dados-abertos).
cvc-enumeration-valid=Valor fora da lista de valores permitidos pelo schema oficial.|Confira o valor permitido na mensagem oficial e ajuste no emissor.
cvc-complex-type.2.4.a=Elemento inesperado ou fora de ordem — o schema oficial exige uma sequência específica de elementos.|Confira a ordem dos elementos do grupo (em gIBSCBS a ordem é vBC, gIBSUF, gIBSMun, vIBS, gCBS).
cvc-complex-type.2.4.b=Elemento obrigatório ausente no grupo apontado pela mensagem oficial.|Inclua o(s) elemento(s) listado(s) como esperado(s) na mensagem oficial.
cvc-datatype-valid.1.2.1=Valor incompatível com o tipo do campo (número, data ou texto).
cvc-minLength-valid=Conteúdo mais curto que o mínimo exigido pelo campo.
cvc-maxLength-valid=Conteúdo mais longo que o máximo permitido pelo campo.
cvc-complex-type.4=Atributo obrigatório ausente no elemento apontado.|Inclua o atributo exigido (ex.: nItem em det, Id em infNFe).
signature.missing=Documento sem assinatura digital (elemento Signature ausente) — esperado em XML de pré-emissão.|Se o XML já deveria estar assinado, assine antes de transmitir; se é pré-emissão, mantenha o modo pré-emissão ligado.
```

- [ ] **Step 4: Implementar**

```java
package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.RootCauseKey;
import br.com.validadorlote.domain.RootCauseTexts;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/** Tradução determinística de erros XSD para pt-BR, carregada de resources. */
public final class XsdErrorTranslator implements RootCauseTexts {

    public record Translation(String message, String action) {}

    private static final String RESOURCE = "/messages/xsd-translations.properties";
    private static final String SIGNATURE_KEY = "signature.missing";

    private final Properties table = new Properties();

    public XsdErrorTranslator() {
        try (var reader = new InputStreamReader(
                XsdErrorTranslator.class.getResourceAsStream(RESOURCE), StandardCharsets.UTF_8)) {
            table.load(reader);
        } catch (IOException | NullPointerException e) {
            throw new UncheckedIOException(new IOException("Tabela de traduções ausente: " + RESOURCE, e));
        }
    }

    public Optional<Translation> translate(FindingKind kind, String xsdCode, String field) {
        String raw = switch (kind) {
            case SIGNATURE_MISSING -> table.getProperty(SIGNATURE_KEY);
            default -> lookup(xsdCode, field);
        };
        if (raw == null || raw.isBlank()) return Optional.empty();
        int sep = raw.indexOf('|');
        return Optional.of(sep < 0
                ? new Translation(raw.trim(), null)
                : new Translation(raw.substring(0, sep).trim(), raw.substring(sep + 1).trim()));
    }

    private String lookup(String xsdCode, String field) {
        if (xsdCode == null) return null;
        String specific = field == null ? null : table.getProperty(xsdCode + "." + field);
        return specific != null ? specific : table.getProperty(xsdCode);
    }

    @Override
    public Optional<String> explanation(RootCauseKey key) {
        return translate(key.kind(), key.xsdCode(), key.field()).map(Translation::message);
    }

    @Override
    public Optional<String> action(RootCauseKey key) {
        return translate(key.kind(), key.xsdCode(), key.field()).map(Translation::action)
                .filter(a -> a != null && !a.isBlank());
    }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.xml.XsdErrorTranslatorTest' --console=plain`
Expected: 6 testes passando.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/validadorlote/infrastructure/xml/XsdErrorTranslator.java src/main/resources/messages src/test/java/br/com/validadorlote/infrastructure/xml/XsdErrorTranslatorTest.java
git commit -m "feat(b2): XsdErrorTranslator com tabela pt-BR em resources"
```

### Task 14: SchemaValidatorEngine com coleta total

**Files:**
- Create: `src/main/java/br/com/validadorlote/infrastructure/xml/SchemaValidatorEngine.java`, `SchemasVersion.java`
- Test: `src/test/java/br/com/validadorlote/infrastructure/xml/SchemaValidatorEngineTest.java`
- Create: `src/test/resources/fixtures/nfe-minima-invalida.xml` (cópia de `docs/calculadora/payloads/06-nfe-nota-sem-assinatura.xml`)

**Interfaces:**
- Consumes: `Finding`, `FindingKind`, `Severity` (Task 7); `ParsedMetadata`, `XmlMetadataParser` (Task 11); `XsdErrorTranslator` (Task 13).
- Produces:
```java
public final class SchemaValidatorEngine {
    public SchemaValidatorEngine(XsdErrorTranslator translator); // compila /schemas/nfe/nota.xsd 1x
    public List<Finding> validate(Path xml, ParsedMetadata meta); // coleta TODOS os erros
}
public final class SchemasVersion { public static String read(); } // "motor X / base Y (extração Z)"
```
- Classificação: erro cujo `xsdCode` começa com `cvc-complex-type.2.4` e cuja mensagem contém `Signature` → `FindingKind.SIGNATURE_MISSING` (severidade base INFO; `FindingReclassifier` decide a final). Demais → `SCHEMA`/REJECTION. Falha fatal de parse durante a validação → 1 achado `UNREADABLE`/WARNING.

- [ ] **Step 1: Copiar fixture inválida**

Run: `mkdir -p src/test/resources/fixtures && cp docs/calculadora/payloads/06-nfe-nota-sem-assinatura.xml src/test/resources/fixtures/nfe-minima-invalida.xml`

- [ ] **Step 2: Escrever teste que falha**

Regras dos asserts: NUNCA asserte o texto integral da mensagem Xerces (localiza por JVM). Asserte `xsdCode`, `kind`, `line`, contagens.

```java
package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.FindingKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaValidatorEngineTest {

    private static SchemaValidatorEngine engine;
    private static final XmlMetadataParser parser = new XmlMetadataParser();

    @BeforeAll
    static void compileSchemaOnce() {
        engine = new SchemaValidatorEngine(new XsdErrorTranslator());
    }

    private Path fixture(String name) {
        return Path.of("src/test/resources/fixtures/" + name);
    }

    @Test
    void collectsAllErrorsNotJustTheFirst() {
        Path xml = fixture("nfe-minima-invalida.xml");
        var findings = engine.validate(xml, parser.parse(xml));

        // NFe mínima viola o schema em vários pontos: coleta total exige > 1 achado
        // (o endpoint oficial devolve só o primeiro — nossa vantagem, spec §2).
        assertThat(findings).hasSizeGreaterThan(1);
        assertThat(findings).allSatisfy(f -> {
            assertThat(f.xsdCode()).startsWith("cvc-");
            assertThat(f.line()).isPositive();
            assertThat(f.source()).isEqualTo(xml);
        });
    }

    @Test
    void includesAreResolvedFromClasspath() {
        // Erros de tipos definidos no leiauteNFe (via include) provam a resolução:
        // a NFe mínima dispara cvc-complex-type.2.4.* de tipos do leiaute.
        Path xml = fixture("nfe-minima-invalida.xml");
        var findings = engine.validate(xml, parser.parse(xml));
        assertThat(findings).anySatisfy(f ->
                assertThat(f.xsdCode()).startsWith("cvc-complex-type.2.4"));
    }

    @Test
    void doctypeYieldsSingleUnreadableFinding(@TempDir Path dir) throws IOException {
        Path xml = dir.resolve("doctype.xml");
        Files.writeString(xml,
                "<?xml version=\"1.0\"?><!DOCTYPE NFe [<!ENTITY x \"y\">]>"
                + "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"/>");
        var findings = engine.validate(xml, new ParsedMetadata(
                new br.com.validadorlote.domain.FiscalDocument(xml, null, null, null, null, null, "NFe"),
                ItemLineIndex.of(java.util.List.of())));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(FindingKind.UNREADABLE);
            assertThat(f.officialMessage()).isNotBlank();
        });
    }

    @Test
    void schemasVersionIsReadable() {
        assertThat(SchemasVersion.read()).contains("1.2.4").contains("V0039");
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.xml.SchemaValidatorEngineTest' --console=plain`
Expected: FALHA de compilação.

- [ ] **Step 4: Implementar**

`SchemasVersion.java`:
```java
package br.com.validadorlote.infrastructure.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** Lê a proveniência da base de schemas embarcada (motor, base, data de extração). */
public final class SchemasVersion {

    private SchemasVersion() {}

    public static String read() {
        Properties p = new Properties();
        try (InputStream in = SchemasVersion.class.getResourceAsStream("/schemas/schemas-version.properties")) {
            p.load(in);
        } catch (IOException | NullPointerException e) {
            throw new UncheckedIOException(new IOException("schemas-version.properties ausente", e));
        }
        return "motor " + p.getProperty("engineVersion") + " / base " + p.getProperty("baseVersion")
                + " (extração " + p.getProperty("extractedAt") + ")";
    }
}
```

`SchemaValidatorEngine.java`:
```java
package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.Severity;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida documentos contra o schema oficial (nota.xsd, raízes NFe/nfeProc/enviNFe),
 * coletando todos os erros. Schema compilado uma única vez; Validator por documento.
 */
public final class SchemaValidatorEngine {

    private static final String SCHEMA_RESOURCE = "/schemas/nfe/nota.xsd";

    // Extração do campo a partir da mensagem Xerces (en/pt); primeira que casar vence.
    private static final List<Pattern> FIELD_PATTERNS = List.of(
            Pattern.compile("(?:element|elemento) '(?:\\{[^}]*\\}:?)?([A-Za-z0-9_:]+)'"),
            Pattern.compile("(?:attribute|atributo) '([A-Za-z0-9_]+)'"),
            Pattern.compile("'\\{\"[^\"]*\":([A-Za-z0-9_]+)\\}'"));

    private final Schema schema;
    private final XsdErrorTranslator translator;

    public SchemaValidatorEngine(XsdErrorTranslator translator) {
        this.translator = translator;
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            URL url = SchemaValidatorEngine.class.getResource(SCHEMA_RESOURCE);
            if (url == null) throw new IllegalStateException("Schema ausente no classpath: " + SCHEMA_RESOURCE);
            // newSchema(URL) preserva o systemId → includes relativos (./originais/...) resolvem.
            this.schema = factory.newSchema(url);
        } catch (SAXException e) {
            throw new IllegalStateException("Falha ao preparar o motor de validação XSD", e);
        }
    }

    public List<Finding> validate(Path xml, ParsedMetadata meta) {
        List<Finding> findings = new ArrayList<>();
        ErrorHandler collector = new ErrorHandler() {
            public void warning(SAXParseException e) { /* avisos de parser não são achados */ }
            public void error(SAXParseException e) { findings.add(toFinding(xml, meta, e)); }
            public void fatalError(SAXParseException e) throws SAXException { throw e; }
        };
        try (InputStream in = Files.newInputStream(xml)) {
            XMLReader reader = secureReader();
            InputSource source = new InputSource(in);
            source.setSystemId(xml.toUri().toString());
            Validator validator = schema.newValidator();
            validator.setErrorHandler(collector);
            validator.validate(new SAXSource(reader, source));
        } catch (SAXException | IOException | javax.xml.parsers.ParserConfigurationException e) {
            return List.of(unreadable(xml, meta, e));
        } catch (RuntimeException e) {
            return List.of(unreadable(xml, meta, e));
        }
        return findings;
    }

    // SAXParserFactory não é thread-safe: cada validação monta o seu (custo irrisório).
    private XMLReader secureReader() throws SAXException, javax.xml.parsers.ParserConfigurationException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newSAXParser().getXMLReader();
    }

    private Finding toFinding(Path xml, ParsedMetadata meta, SAXParseException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        String xsdCode = message.startsWith("cvc-") ? message.substring(0, message.indexOf(':')) : null;
        String field = extractField(message);
        FindingKind kind = isSignatureMissing(xsdCode, message)
                ? FindingKind.SIGNATURE_MISSING : FindingKind.SCHEMA;
        if (kind == FindingKind.SIGNATURE_MISSING) field = "Signature";
        Severity severity = kind == FindingKind.SIGNATURE_MISSING ? Severity.INFO : Severity.REJECTION;
        String friendly = translator.translate(kind, xsdCode, field)
                .map(XsdErrorTranslator.Translation::message).orElse(null);
        Integer item = meta.itemIndex().itemAt(e.getLineNumber());
        return new Finding(xml, meta.document().accessKey(), item, kind, severity, field,
                xsdCode, message, friendly, e.getLineNumber(), e.getColumnNumber());
    }

    private boolean isSignatureMissing(String xsdCode, String message) {
        return xsdCode != null && xsdCode.startsWith("cvc-complex-type.2.4") && message.contains("Signature");
    }

    private String extractField(String message) {
        for (Pattern p : FIELD_PATTERNS) {
            Matcher m = p.matcher(message);
            if (m.find()) {
                String captured = m.group(1);
                int colon = captured.lastIndexOf(':');
                return colon >= 0 ? captured.substring(colon + 1) : captured;
            }
        }
        return null;
    }

    private Finding unreadable(Path xml, ParsedMetadata meta, Exception e) {
        String message = Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName());
        return new Finding(xml, meta.document().accessKey(), null, FindingKind.UNREADABLE,
                Severity.WARNING, null, null, message, null, null, null);
    }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.xml.SchemaValidatorEngineTest' --console=plain`
Expected: 4 testes passando.

**Contingência (só se `includesAreResolvedFromClasspath` falhar com "Cannot resolve ... originais/..."):** implemente `LSResourceResolver` que serve `/schemas/nfe/originais/<arquivo>` do classpath e registre com `factory.setResourceResolver(...)` ANTES de `newSchema`; registre a mudança em `docs/decisions.md` no mesmo commit.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/validadorlote/infrastructure/xml src/test/resources/fixtures/nfe-minima-invalida.xml src/test/java/br/com/validadorlote/infrastructure/xml/SchemaValidatorEngineTest.java
git commit -m "feat(b2): SchemaValidatorEngine com coleta total de erros e parsing seguro"
```

### Task 15: Fixtures canônicas válidas (NF-e e NFC-e)

**Files:**
- Create: `src/test/resources/fixtures/nfe-valida.xml`, `src/test/resources/fixtures/nfce-valida.xml`, `src/test/resources/fixtures/nfe-valida-sem-assinatura.xml`
- Test: adicionar casos em `src/test/java/br/com/validadorlote/infrastructure/xml/SchemaValidatorEngineTest.java`

**Interfaces:**
- Produces: fixtures schema-válidas usadas por B3/B4/B5; prova de que assinatura ausente vira `SIGNATURE_MISSING` (e nada mais) num documento no resto válido.

- [ ] **Step 1: Adicionar os testes (falham até a fixture ficar válida)**

```java
    @Test
    void fullyValidNfeYieldsNoFindings() {
        Path xml = fixture("nfe-valida.xml");
        assertThat(engine.validate(xml, parser.parse(xml))).isEmpty();
    }

    @Test
    void fullyValidNfceYieldsNoFindings() {
        Path xml = fixture("nfce-valida.xml");
        assertThat(engine.validate(xml, parser.parse(xml))).isEmpty();
    }

    @Test
    void validDocWithoutSignatureYieldsOnlySignatureMissing() {
        Path xml = fixture("nfe-valida-sem-assinatura.xml");
        var findings = engine.validate(xml, parser.parse(xml));
        assertThat(findings).isNotEmpty();
        assertThat(findings).allSatisfy(f ->
                assertThat(f.kind()).isEqualTo(FindingKind.SIGNATURE_MISSING));
    }
```

- [ ] **Step 2: Criar `nfe-valida.xml` (ponto de partida) e iterar até zero achados**

Ponto de partida (o grupo IBS/CBS vem do payload real aceito pelo motor oficial — `docs/calculadora/payloads/11-grupo-valido-jar.xml`; a assinatura é dummy estrutural — o schema não confere criptografia):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<NFe xmlns="http://www.portalfiscal.inf.br/nfe">
  <infNFe versao="4.00" Id="NFe35260714200166000187550010000000151234567890">
    <ide>
      <cUF>35</cUF><cNF>12345678</cNF><natOp>VENDA</natOp><mod>55</mod><serie>1</serie>
      <nNF>15</nNF><dhEmi>2026-07-20T10:00:00-03:00</dhEmi><tpNF>1</tpNF><idDest>1</idDest>
      <cMunFG>3550308</cMunFG><tpImp>1</tpImp><tpEmis>1</tpEmis><cDV>0</cDV><tpAmb>2</tpAmb>
      <finNFe>1</finNFe><indFinal>1</indFinal><indPres>1</indPres><procEmi>0</procEmi>
      <verProc>1.0</verProc>
    </ide>
    <emit>
      <CNPJ>14200166000187</CNPJ><xNome>EMPRESA TESTE LTDA</xNome>
      <enderEmit>
        <xLgr>RUA TESTE</xLgr><nro>100</nro><xBairro>CENTRO</xBairro><cMun>3550308</cMun>
        <xMun>SAO PAULO</xMun><UF>SP</UF><CEP>01001000</CEP>
      </enderEmit>
      <IE>123456789012</IE><CRT>3</CRT>
    </emit>
    <det nItem="1">
      <prod>
        <cProd>001</cProd><cEAN>SEM GTIN</cEAN><xProd>PRODUTO TESTE</xProd>
        <NCM>61091000</NCM><CFOP>5102</CFOP><uCom>UN</uCom><qCom>1.0000</qCom>
        <vUnCom>200.00</vUnCom><vProd>200.00</vProd><cEANTrib>SEM GTIN</cEANTrib>
        <uTrib>UN</uTrib><qTrib>1.0000</qTrib><vUnTrib>200.00</vUnTrib><indTot>1</indTot>
      </prod>
      <imposto>
        <ICMS><ICMS00><orig>0</orig><CST>00</CST><modBC>3</modBC><vBC>200.00</vBC>
          <pICMS>18.00</pICMS><vICMS>36.00</vICMS></ICMS00></ICMS>
        <PIS><PISNT><CST>07</CST></PISNT></PIS>
        <COFINS><COFINSNT><CST>07</CST></COFINSNT></COFINS>
        <IBSCBS>
          <CST>000</CST><cClassTrib>000001</cClassTrib>
          <gIBSCBS>
            <vBC>200.00</vBC>
            <gIBSUF><pIBSUF>0.05</pIBSUF><vIBSUF>0.10</vIBSUF></gIBSUF>
            <gIBSMun><pIBSMun>0.05</pIBSMun><vIBSMun>0.10</vIBSMun></gIBSMun>
            <vIBS>0.20</vIBS>
            <gCBS><pCBS>0.90</pCBS><vCBS>1.80</vCBS></gCBS>
          </gIBSCBS>
        </IBSCBS>
      </imposto>
    </det>
    <total>
      <ICMSTot>
        <vBC>200.00</vBC><vICMS>36.00</vICMS><vICMSDeson>0.00</vICMSDeson><vFCP>0.00</vFCP>
        <vBCST>0.00</vBCST><vST>0.00</vST><vFCPST>0.00</vFCPST><vFCPSTRet>0.00</vFCPSTRet>
        <vProd>200.00</vProd><vFrete>0.00</vFrete><vSeg>0.00</vSeg><vDesc>0.00</vDesc>
        <vII>0.00</vII><vIPI>0.00</vIPI><vIPIDevol>0.00</vIPIDevol><vPIS>0.00</vPIS>
        <vCOFINS>0.00</vCOFINS><vOutro>0.00</vOutro><vNF>200.00</vNF>
      </ICMSTot>
    </total>
    <transp><modFrete>9</modFrete></transp>
    <pag><detPag><tPag>01</tPag><vPag>200.00</vPag></detPag></pag>
  </infNFe>
  <Signature xmlns="http://www.w3.org/2000/09/xmldsig#">
    <SignedInfo>
      <CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
      <SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1"/>
      <Reference URI="#NFe35260714200166000187550010000000151234567890">
        <Transforms>
          <Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
          <Transform Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
        </Transforms>
        <DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1"/>
        <DigestValue>AAAAAAAAAAAAAAAAAAAAAAAAAAA=</DigestValue>
      </Reference>
    </SignedInfo>
    <SignatureValue>AAAAAAAA</SignatureValue>
    <KeyInfo><X509Data><X509Certificate>AAAAAAAA</X509Certificate></X509Data></KeyInfo>
  </Signature>
</NFe>
```

**Procedimento de iteração (oráculo = nosso motor):** rode `./gradlew test --tests '*SchemaValidatorEngineTest*'`; para cada achado impresso, ajuste a fixture conforme a mensagem oficial (elemento faltante → incluir; ordem → reordenar; formato → corrigir casas decimais). Restrições: (1) NUNCA remova o grupo `IBSCBS` — ele é o objeto do produto e já foi aceito pelo motor oficial; (2) mudanças devem manter o documento plausível (valores coerentes). Se o schema exigir grupo de totais RTC (ex.: `IBSCBSTot` dentro de `total`), inclua com valores coerentes com o item (vBC 200.00, vIBS 0.20, vCBS 1.80). Repita até `fullyValidNfeYieldsNoFindings` passar.

- [ ] **Step 3: Derivar as outras fixtures**

- `nfe-valida-sem-assinatura.xml` = `nfe-valida.xml` sem o bloco `<Signature>...</Signature>`.
- `nfce-valida.xml` = `nfe-valida.xml` com: `mod` 65, `Id` com "65" nas posições 21–22 (`NFe35260714200166000187650010000000151234567890`), `Reference URI` idem, `tpImp` 4, `indFinal` 1, `indPres` 1. Itere igual ao Step 2 se surgirem exigências específicas de NFC-e.

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.xml.SchemaValidatorEngineTest' --console=plain`
Expected: 7 testes passando (4 anteriores + 3 novos).

- [ ] **Step 5: Commit**

```bash
git add src/test/resources/fixtures src/test/java/br/com/validadorlote/infrastructure/xml/SchemaValidatorEngineTest.java
git commit -m "test(b2): fixtures canônicas válidas (NF-e/NFC-e) e caso assinatura ausente"
```

### Task 16: Smoke de performance (500 XMLs)

**Files:**
- Test: `src/test/java/br/com/validadorlote/infrastructure/xml/BatchPerformanceSmokeTest.java`

**Interfaces:**
- Consumes: `SchemaValidatorEngine`, `XmlMetadataParser`, fixtures (Tasks 11/14/15).

- [ ] **Step 1: Escrever o teste (tag slow)**

```java
package br.com.validadorlote.infrastructure.xml;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("slow")
class BatchPerformanceSmokeTest {

    @Test
    void validates500FilesUnderTwoMinutes(@TempDir Path dir) throws IOException {
        String template = Files.readString(Path.of("src/test/resources/fixtures/nfe-valida.xml"));
        for (int i = 0; i < 500; i++) {
            Files.writeString(dir.resolve("doc-" + i + ".xml"),
                    template.replace("<nNF>15</nNF>", "<nNF>" + (100 + i) + "</nNF>"));
        }
        var engine = new SchemaValidatorEngine(new XsdErrorTranslator());
        var parser = new XmlMetadataParser();

        Instant start = Instant.now();
        long totalFindings = 0;
        try (var files = Files.list(dir)) {
            for (Path xml : files.toList()) {
                totalFindings += engine.validate(xml, parser.parse(xml)).size();
            }
        }
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(totalFindings).isZero();
        assertThat(elapsed).isLessThan(Duration.ofMinutes(2));
        System.out.println("500 XMLs em " + elapsed.toMillis() + " ms");
    }
}
```

- [ ] **Step 2: Rodar via slowTest**

Run: `./gradlew slowTest --console=plain`
Expected: PASS com tempo impresso (esperado: segundos). `./gradlew test` NÃO deve executá-lo.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/br/com/validadorlote/infrastructure/xml/BatchPerformanceSmokeTest.java
git commit -m "test(b2): smoke de performance com 500 XMLs (tag slow)"
```

### Task 17: Fechamento do Bloco 2 (PR + merge)

- [ ] **Step 1: Suíte completa + slow**

Run: `./gradlew test slowTest --console=plain`
Expected: tudo verde.

- [ ] **Step 2: Push e PR**

```bash
git push -u origin bloco/2-motor-xsd
gh pr create --title "B2: motor XSD com coleta total, tradutor e fixtures canônicas" --body "$(cat <<'EOF'
Bloco 2: XsdErrorTranslator (tabela pt-BR em resources), SchemaValidatorEngine
(Schema 1x, Validator por doc, coleta total, parsing seguro, classificação de
assinatura ausente), fixtures canônicas NF-e/NFC-e válidas e smoke de 500 XMLs.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
gh pr checks --watch
```

- [ ] **Step 3: GATE DE REVIEW DO BLOCO** — review do orquestrador antes do merge.

- [ ] **Step 4: Merge**

```bash
gh pr merge --merge --delete-branch
git checkout main && git pull
```

---
