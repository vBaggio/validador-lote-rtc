# Design — Validador de Lote RTC

| | |
|---|---|
| **Status** | Aprovada (brainstorm 26/07/2026, Vinícius + Claude) |
| **Data** | 26/07/2026 |
| **Substitui** | SDD de entrada (`tmp/`, não versionado) — premissa central invalidada pela descoberta do contrato |
| **Licença** | GPL-3.0 |
| **Runtime alvo** | Java 21 |

---

## 1. Contexto e objetivo

### 1.1 Problema

A partir de **03/08/2026**, a SEFAZ rejeita NF-e/NFC-e de emitentes em Regime Normal (CRT=3) cujos grupos de IBS/CBS não estejam conformes à NT 2025.002-RTC. As ferramentas oficiais validam **um documento por vez**, via web. Escritórios contábeis com centenas de XMLs precisam saber, **antes de emitir**, o que está fora e **por quê**, de forma acionável e agregada.

### 1.2 Objetivo

Ferramenta desktop, offline, de instalação em um clique para usuário **não-técnico**:

1. Recebe uma pasta ou XMLs individuais de NF-e/NFC-e (drag-and-drop ou escolha)
2. Valida cada documento contra os **schemas XSD oficiais** vigentes (a mesma régua estrutural que a SEFAZ aplica), coletando **todos** os erros de cada documento
3. Agrupa os achados por **causa-raiz** e mostra "N documentos com o mesmo problema"
4. Mantém relatório CSV no núcleo para reativação posterior da interface (D-045)

Na fase 2 (v1), soma a **conferência de valores**: recalcula IBS/CBS de cada item via motor de cálculo oficial (`regime-geral`) e aponta divergências entre declarado e calculado.

### 1.3 Princípios inegociáveis

| Princípio | Consequência |
|---|---|
| Nenhum dado fiscal sai da máquina **por padrão** | Sem telemetria e sem envio de XML, CNPJ ou chave. D-048 permite, no v0, consulta pós-boot de metadados e artefatos normativos: no máximo a cada 24 h ou por ação manual, somente às fontes oficiais e sem impacto no lote corrente. Na v1, o download do motor oficial (se D-012 decidir) e o relatório narrativo por IA (D-014) continuam dependentes de ação explícita — nunca envio automático de dados fiscais |
| A ferramenta nunca decide tributo | Julgamento vem de artefato oficial: XSDs extraídos do pacote da RFB; na v1, números do motor oficial. Nós coletamos, agrupamos e apresentamos |
| Zero pré-requisitos de instalação | Sem Java na máquina, sem Docker; instalador nativo com runtime embarcado |
| Não sugerir chancela oficial | Nome, textos e README explicitam: ferramenta independente, sem vínculo com RFB/SEFAZ |
| Vida útil curta declarada | Simplicidade vence extensibilidade em toda decisão ambígua |

### 1.4 Usuário-alvo

Contador ou responsável fiscal, conhecimento comum de informática. Instala como qualquer programa, arrasta uma pasta, lê resultados em português claro. **Windows é o alvo prioritário**; Linux/macOS best-effort.

---

## 2. A descoberta do contrato (por que este design difere do SDD de entrada)

Em 26/07/2026 a Calculadora de Tributos oficial (v1.2.4, base V0039 de 08/07/2026) foi baixada pelo endpoint oficial, executada e sondada. Artefatos completos em [`docs/calculadora/`](../../calculadora/). Fatos que moldam este design:

1. **Não existe endpoint de diagnóstico fiscal estruturado.** `POST /api/calculadora/xml/validate` faz somente validação de **schema XSD** e devolve `true` — ou **apenas o primeiro erro** (fail-fast), como mensagem técnica Xerces (`cvc-*`) em RFC 7807. Sem código de regra fiscal, sem lista por item/campo.
2. **Os XSDs vigentes vivem dentro do JAR** da Calculadora (`BOOT-INF/classes/xml/`). O espelho GitHub `nfe/rtc-calculadora-offline` está desatualizado e **reprova XML válido atual** (falta `vIBS` em `gIBSCBS`, `gCompraGov`, etc.). Fonte confiável = JAR oficial, obtido pelo endpoint `GET .../calculadora/download/url?platform=default`.
3. **Existe motor de cálculo**: `POST /api/calculadora/regime-geral` (JSON→JSON, memória de cálculo completa) — caminho para conferir **valores** declarados × calculados (v1).
4. **O pacote oficial não contém licença/termo algum** — redistribuir o binário não tem autorização expressa (afeta D-012).
5. Operacional: só `api-regime-geral.jar` (94 MB) é necessário; boot 8–18 s; ~700 MB RAM; ~4 ms por validação; suporta concorrência (8 paralelas ok).
6. Schema da nota completa **exige o elemento** `ds:Signature` (criptografia não conferida); o entrypoint `nota.xsd` aceita as raízes `NFe`, `nfeProc` e `enviNFe`; NFC-e (65) difere apenas pelo tipo do grupo tributário.
7. A validação oficial **não rejeita DOCTYPE** — nós tratamos XML de terceiros como não-confiável (parsing seguro obrigatório).

**Consequência:** o diagnóstico em lote é construído por nós: validação XSD **local** com os schemas oficiais (coletando todos os erros — melhor que o fail-fast oficial) e, na v1, conferência de valores via motor oficial.

---

## 3. Escopo

### 3.1 v0.x — validação estrutural (meta: publicável rápido)

- Varredura recursiva de pasta e aceitação de XML individual (`.xml`)
- Parse de metadados: chave de acesso, emitente/CNPJ, número, série, data, modelo (55/65), raiz (`NFe`/`nfeProc`/`enviNFe`)
- Validação XSD local com **coleta total de erros** por documento (entrypoint `nota.xsd`)
- Mapeamento erro→item: faixas de linha de cada `<det nItem>` indexadas no parse
- Tradução determinística das mensagens `cvc-*` para pt-BR + ação sugerida (tabela em resources; sem correspondência → mensagem oficial; **nunca IA**)
- Assinatura ausente → achado `SIGNATURE_MISSING`; o modo pré-emissão padrão a trata como INFO. O controle para alterná-lo está temporariamente fora da UI (D-045).
- Severidade por tipo: `SCHEMA` → REJECTION; `SIGNATURE_MISSING` → INFO ou REJECTION (toggle); `UNREADABLE` → WARNING
- Agrupamento por causa-raiz + contagem de documentos afetados
- CSV: `causas-raiz.csv` + `achados-detalhados.csv`, UTF-8 com BOM, separador `;` (backend pronto; ação de exportar temporariamente fora da UI, D-045)
  - `causas-raiz.csv`: causa (amigável ou oficial), campo, código XSD, severidade, documentos afetados, ocorrências, ação sugerida
  - `achados-detalhados.csv`: arquivo, chave de acesso, item, campo, código XSD, severidade, linha, coluna, mensagem oficial, mensagem amigável
- UI Swing+FlatLaf escura: importar primeiro e validar sob comando, progresso cancelável na própria grade, documentos como visão principal e problemas do documento selecionado como detalhe; sem exportação visível no momento (D-045)
- Instalador Windows `.msi` (gate); Linux/macOS best-effort

### 3.2 v1 — conferência de valores

- Motor `regime-geral` como processo filho (spawn com runtime embarcado, porta dinâmica, health-check, shutdown gracioso, instância única por lote)
- Mapeamento item do XML → JSON da operação (o maior esforço da fase)
- Diff calculado × declarado por item/campo → achados `VALUE`
- Consultas `dados-abertos` (tabelas oficiais de referência; exigem `?data=AAAA-MM-DD`)
- Contrato RFB isolado em adapter único (`infrastructure/calculator/`)
- Fonte do motor: **decisão pendente D-012**
- **(por último)** Relatório narrativo por IA — **opcional e BYOK**: botão pós-análise; usuário fornece a própria API key; envia **apenas o relatório agregado** (causas, contagens, mensagens — nunca XMLs brutos); redação explicativa dos achados determinísticos, **sem julgamento fiscal** (D-014)

### 3.3 Fora de escopo (todas as fases)

IA como julgamento fiscal ou como etapa automática do fluxo (o relatório narrativo opcional BYOK da v1 é a única forma admitida — D-014); PDF/XLSX; emissão, assinatura ou transmissão; correção automática de XML; cadastro/login/telemetria; internacionalização; auto-update do app (no máximo aviso de versão nova, com opt-out); CLI.

---

## 4. Arquitetura

**Arquitetura em camadas + MVP na apresentação.** O caso de uso de lote é internamente um fluxo em estágios (pipes-and-filters), mas a organização do código segue camadas convencionais.

```
br.com.validadorlote
├── App.java                 // bootstrap, construção manual do grafo (sem DI framework)
├── presentation/            // Swing em MVP: views passivas atrás de interfaces + presenters
│   ├── MainFrame, DropZoneView, ResultsView, ...
│   └── presenter/           // lógica de tela; marshalling para EDT acontece AQUI
├── application/
│   └── ValidateBatchUseCase // orquestra scan → validar → agrupar → relatório
│                            // pool de workers, ProgressListener (callback puro), cancelamento
├── domain/                  // records + regras puras; não importa nada de fora
│   ├── FiscalDocument, Finding, FindingKind, Severity,
│   │   RootCause, RootCauseKey, BatchReport
│   └── RootCauseGrouper
└── infrastructure/
    ├── fs/         FolderScanner
    ├── xml/        XmlMetadataParser, SchemaValidatorEngine,
    │               XsdErrorTranslator, SchemasVersion
    ├── csv/        CsvExporter
    └── calculator/ (v1) CalculatorProcess, CalculatorClient,
                    CalculatorResponseAdapter, OperationMapper, dto/
```

**Regra de dependência (inegociável):** `presentation → application → {domain, infrastructure}`; `infrastructure → domain`; `domain → nada`. `javax.swing`/`java.awt` **só** em `presentation/`. Garantido por teste ArchUnit (test-only). Sem interfaces-porta cerimoniais: application chama infrastructure diretamente — decisão consciente (D-006); o único contrato externo volátil (RFB) fica isolado no adapter da v1.

**Troca de frontend preparada:** views atrás de interface; `ProgressListener` neutro de toolkit; extração futura de um módulo `core` é mecânica porque os imports já são limpos.

### 4.1 Modelo de domínio

```java
enum FindingKind { SCHEMA, SIGNATURE_MISSING, UNREADABLE }   // v1 soma VALUE
enum Severity { REJECTION, WARNING, INFO }

record FiscalDocument(
    Path source, String accessKey, String emitterCnpj,
    String documentNumber, LocalDate issueDate,
    String model,            // "55" | "65"
    String rootElement       // "NFe" | "nfeProc" | "enviNFe"
) {}                         // sem rawXml: nada de 500 XMLs em memória

record Finding(
    Path source, String accessKey, Integer itemNumber,   // null = achado do documento
    FindingKind kind, Severity severity,
    String field,            // elemento violado (ex.: "pCBS"), quando identificável
    String xsdCode,          // ex.: "cvc-pattern-valid"
    String officialMessage,  // Xerces, sem reescrita
    String friendlyMessage,  // tradução pt-BR; null se não mapeada
    Integer line, Integer column   // null quando não aplicável (ex.: UNREADABLE)
) {}

record RootCauseKey(FindingKind kind, String xsdCode, String field) {}

record RootCause(
    RootCauseKey key,
    String friendlyExplanation,   // da tabela de tradução; fallback: officialMessage exemplar
    String suggestedAction,       // determinístico; null se não mapeado
    List<Finding> findings,
    int affectedDocuments
) {}

record BatchReport(
    Instant startedAt, Duration elapsed,
    int documentsScanned, int documentsWithFindings, int documentsUnreadable,
    boolean cancelled,            // true = resultados parciais (CA-9)
    List<RootCause> rootCauses,   // ordenadas por affectedDocuments desc
    String schemasVersion,        // proveniência da base embarcada
    List<DocumentReport> documents, // resultado por documento para a área de trabalho
    List<Path> invalidFiles       // arquivos que não podem integrar a grade
) {}
```

### 4.2 Fluxo do lote (v0)

```
1. Usuário arrasta uma pasta/XML ou o escolhe; pode repetir a operação para compor o lote.
2. FolderScanner encontra `.xml`; XmlMetadataParser faz a leitura segura dos metadados usados na
   grade. Arquivo ilegível é recusado e informado, sem entrar no lote.
3. Usuário revisa a grade e aciona **Validar pendentes**.
4. Worker sequencial, fora da EDT, valida um arquivo por vez (schema + regras), atualizando sua
   linha e o progresso na grade; pode ser interrompido cooperativamente.
5. Para cada documento validado, RootCauseGrouper continua formando o relatório interno/exportável;
   a UI mostra primeiro o documento e, abaixo, os achados desse documento selecionado.
```

No fluxo interativo, o presenter valida cada pendência em worker sequencial e publica o contador na
EDT. O `ProgressListener` continua disponível para a execução completa do caso de uso. A UI nunca
congela: trabalho fora da EDT, presenter faz o marshalling.

### 4.3 Engenharia crítica (vai para conventions.md)

1. `Schema` compilado **uma única vez** (thread-safe); um `Validator` por documento (criação barata). Includes relativos dos XSDs resolvidos via systemId de URL do classpath; `LSResourceResolver` dedicado apenas como contingência se a resolução falhar no empacotado.
2. **Parsing seguro obrigatório** em todos os parsers: `FEATURE_SECURE_PROCESSING`, DOCTYPE proibido, entidades externas desligadas.
3. Validação via `SAXSource` streaming — sem DOM, memória estável.
4. `ErrorHandler` coletor: acumula todos os erros, não lança no primeiro.
5. Tradução de erros é **dado** (resources), não código: `(xsdCode, padrão de campo) → {mensagem pt-BR, ação sugerida}`.

---

## 5. Tratamento de falhas

Regra de ouro: **lote de 500 nunca aborta por 1 arquivo.**

| Situação | Comportamento |
|---|---|
| XML corrompido / não-XML | Não é adicionado à grade; diálogo lista os arquivos recusados |
| Erro inesperado num worker | Capturado por arquivo; documento marcado não-processado; segue |
| Pasta vazia / sem `.xml` | Mensagem informativa, não erro |
| Cancelamento | Pool encerra gracioso; resultados parciais rotulados "análise cancelada" |
| Falha ao gravar CSV | Sem caminho de UI enquanto a exportação estiver suspensa (D-045); o contrato backend preserva a falha para quando a ação voltar |
| (v1) Motor não sobe / timeout | Erro claro + log; retry 1×; documento não-processado; app nunca trava |

---

## 6. Testes

**Leve e dirigido** (D-010): JUnit 5 + AssertJ; Mockito raro; ArchUnit (test-only) para fronteiras. Sem gate de cobertura — cobertura por criticidade, verificada no review de cada bloco:

| Alvo | Foco |
|---|---|
| `RootCauseGrouper` | Chaves, ordenação, contagens — casos sintéticos completos |
| `SchemaValidatorEngine` | Fixtures reais da descoberta (XML aceito pelo motor oficial = fixture canônica; inválidos conhecidos; sem assinatura; `nfeProc`; NFC-e; corrompido; DOCTYPE→rejeição) |
| `XmlMetadataParser` | Chave/CNPJ/modelo; índice linha→item; malformados |
| `XsdErrorTranslator` | cvc conhecidos → pt-BR; desconhecido → fallback |
| `CsvExporter` | Golden file: BOM, `;`, escaping, acentos |
| `ValidateBatchUseCase` | Lote misto, cancelamento, pasta vazia, eventos de progresso |
| Performance | `@Tag("slow")`: 500 XMLs sintéticos < 2 min |

`presentation/` sem teste automatizado (views passivas). Fixtures em `src/test/resources/fixtures/`, semeadas com os payloads reais de `docs/calculadora/`.

---

## 7. Build, empacotamento e distribuição

- **Gradle** (wrapper 8.14.x), plugins `java` + `application`. Dependência de runtime no v0: **FlatLaf apenas**.
- **Schemas**: commitados em `src/main/resources/schemas/` com `schemas-version.properties` (versão do motor, data, URL de origem). Task **`updateSchemas`**: baixa o zip oficial → extrai XSDs do JAR → atualiza resources + proveniência (diff legível em PR). Build e CI **não tocam a rede**.
- **jpackage** com runtime **jlink enxuto** no v0 (`java.desktop`, `java.xml` e o mínimo necessário) → instalador Windows estimado 50–80 MB. Se faltar módulo em runtime, fallback imediato `ALL-MODULE-PATH` (D-008). Na v1, com Spring do motor no processo filho, `ALL-MODULE-PATH` obrigatório.
- **Windows**: `.msi` via WiX no runner. Sem assinatura de código no v0 → README documenta SmartScreen.
- **CI** (`ci.yml`): push/PR → ubuntu, build + testes.
- **Release** (`release.yml`): tag `v*` → matrix `[windows, ubuntu, macos]`; **Windows é gate**; Linux/macOS `continue-on-error`, anexam se passarem. Artefatos na GitHub Release.
- **README top**: o que faz/não faz, screenshot, independência da RFB, privacidade, instruções SmartScreen, versão da base de schemas, GPL-3.0.

---

## 8. Harness e processo de trabalho

### 8.1 Estrutura

```
CLAUDE.md                      // fino: aponta docs/, regras críticas resumidas
docs/
├── context.md                 // o que é, público, princípios, índice
├── architecture.md            // camadas+MVP, regra de dependência, fluxo
├── conventions.md             // código, testes, commits, fronteiras, parsing seguro
├── testing.md                 // estratégia, fixtures, tags
├── decisions.md               // ADR-lite (seed: D-001..D-013)
├── calculadora/               // artefatos da descoberta (contrato, openapi, licença, pares)
└── superpowers/{specs,plans}
.claude/agents/validador-senior-dev.md
.github/workflows/{ci.yml, release.yml}
```

### 8.2 Processo

- **Fable orquestra; subagentes (Opus) executam** blocos de tasks relacionadas
- **Blocos v0 previstos** (detalhamento no plano de implementação): B0 harness+repo → B1 domínio+scan/parse → B2 motor XSD+tradutor+agrupador → B3 use case+CSV → B4 UI → B5 empacotamento+CI+release v0.1
- **1 commit semântico por task**, escopo do bloco: `feat(b2): SchemaValidatorEngine com coleta total`
- **Branch por bloco + PR** (`bloco/2-motor-xsd`); review por bloco antes do merge
- Decisões novas → `decisions.md` no mesmo PR; decisões-chave confirmadas com o Vinícius antes
- Código em inglês; mensagens de UI, documentação e mensagens amigáveis em pt-BR; comentários enxutos; javadoc onde agrega

---

## 9. Registro de decisões (seed do decisions.md)

| # | Decisão | Nota |
|---|---|---|
| D-001 | Gradle + Java 21 | Cravada pelo Vinícius |
| D-002 | Repo público `vBaggio/validador-lote-rtc`, GitHub Actions, GitHub Releases | Cravada |
| D-003 | GPL-3.0 | Confirmada |
| D-004 | Entrega faseada: v0 estrutura local / v1 valores via `regime-geral` | Pivô pós-descoberta |
| D-005 | XSDs oficiais extraídos do JAR da Calculadora, commitados com proveniência; task `updateSchemas` | **Estudo futuro**: automação da atualização |
| D-006 | Camadas + MVP; regra de dependência com ArchUnit; sem CLI no v0; sem DI framework; frontend trocável (views atrás de interface) | |
| D-007 | Swing + FlatLaf confirmada | Trade-off JavaFX documentado: única vantagem real (TreeTableView) neutralizada pelo mestre-detalhe |
| D-008 | jlink enxuto no v0; `ALL-MODULE-PATH` quando o motor entrar (v1) | |
| D-009 | Matrix 3 SOs com Windows como gate; Linux/macOS best-effort | |
| D-010 | Testes leves e dirigidos, sem gate de cobertura | |
| D-011 | Branch por bloco + PR; 1 commit semântico por task; review por bloco | |
| D-012 | **PENDENTE** — Fonte do motor na v1: embutir × download no 1º uso. Decidir no início da v1. Fato relevante: pacote oficial **sem licença** | |
| D-013 | Assinatura ausente = `SIGNATURE_MISSING`/INFO com toggle "XMLs pré-emissão" (default ligado) | |
| D-014 | Relatório narrativo por IA na v1 (última entrega da fase): opcional, botão pós-análise, BYOK (API key do usuário), envia só o relatório agregado, nunca XMLs; narra achados determinísticos, não julga tributo | Detalhamento (provedor, prompt, redação de dados sensíveis) no plano da v1. Ideia registrada: onboarding de primeiro boot pode unificar configuração opcional de credenciais e downloads iniciais (schemas/motor) |

---

## 10. Critérios de aceite (v0)

| # | Critério |
|---|---|
| CA-1 | Instalador `.msi` roda em Windows limpo (sem Java, sem Docker); app abre |
| CA-2 | Lote de 500 XMLs processa sem intervenção; arquivo corrompido no meio não interrompe |
| CA-3 | N ocorrências da mesma causa aparecem como 1 linha com contador de documentos |
| CA-4 | CSVs abrem corretamente no Excel pt-BR (UTF-8 BOM, `;`, acentos) |
| CA-5 | A validação do lote não faz requisição de rede nem transmite XML, CNPJ, chave ou telemetria. A única rede do v0 é a consulta consultiva pós-boot de artefatos normativos, no máximo a cada 24 h ou por ação manual, às fontes configuradas pelo B6 e sem alterar a base do lote corrente (verificável por captura de tráfego) |
| CA-6 | Versão da base de schemas aparece na UI e no CSV |
| CA-7 | 500 arquivos concluem em < 2 min em hardware modesto |
| CA-8 | Toggle pré-emissão separa ruído de assinatura dos erros reais |
| CA-9 | UI não congela durante processamento; cancelamento funciona |

---

## 11. Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| RFB atualizar base de schemas | Falso "conforme" com base velha | `updateSchemas` + release rápida; versão da base visível no relatório (CA-6); estudo futuro de automação (D-005) |
| Tradução de mensagens incompleta no início | UX pior, não incorreta | Fallback = mensagem oficial; tabela evolui por PR |
| jlink podar módulo necessário | App quebra em runtime | Smoke test do instalador no bloco de empacotamento; fallback `ALL-MODULE-PATH` (D-008) |
| SmartScreen assustar usuário | Abandono na instalação | README com passo-a-passo ilustrado (AD herdada: sem assinatura no v0) |
| Ferramenta oficial ganhar suporte a lote | Elimina diferencial | Aceito — MVP com validade declarada |
| (v1) Redistribuição do motor sem licença | Jurídico | D-012 pendente; artefatos de apuração preservados em `docs/calculadora/` |

---

## 12. Não-objetivos

Este projeto **não** pretende: substituir ferramentas oficiais; ser mantido indefinidamente; gerar receita; emitir/assinar/transmitir documentos; dar orientação tributária — reporta o que os artefatos oficiais dizem.

---

## Apêndice — Glossário

| Termo | Significado |
|---|---|
| RTC | Reforma Tributária sobre o Consumo |
| IBS / CBS | Imposto sobre Bens e Serviços / Contribuição sobre Bens e Serviços |
| CST / cClassTrib | Código de Situação Tributária / Classificação Tributária |
| CRT=3 | Regime Normal |
| NT 2025.002 | Nota Técnica dos grupos IBS/CBS na NF-e |
| Calculadora | Calculadora de Tributos da RFB (motor oficial) |
| `cvc-*` | Códigos de erro de validação XSD do parser Xerces |
