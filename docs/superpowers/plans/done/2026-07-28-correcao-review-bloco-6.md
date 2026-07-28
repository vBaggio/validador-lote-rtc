# Correções do Review do Bloco 6 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corrigir as incoerências encontradas no review das Tasks 1–8 e auditar as onze rejeições implementadas contra a NT 2025.002 v1.50 local.

**Architecture:** A identidade de causa continuará no domínio e ganhará discriminadores explícitos por camada. Mensagens oficiais e explicações locais usarão os dois canais já existentes em `Finding`. A ingestão continuará manual e offline no build normal, mas validará o artefato inteiro tanto na atualização quanto no carregamento.

**Tech Stack:** Java 21, Gradle 8.14.3, JUnit 5, AssertJ, Jackson 2.18.2, Groovy DSL.

## Global Constraints

- Regra de dependência: `presentation → application → {domain, infrastructure}`; `infrastructure → domain`; `domain → nada`.
- `javax.swing` e `java.awt` somente em `presentation/`.
- XML de terceiro sempre com DOCTYPE proibido e entidades externas desabilitadas.
- Julgamento fiscal vem de XSD, NT ou tabela oficial embarcada; nunca de tabela hardcoded em Java.
- Falso positivo é inaceitável; incerteza vira `NOT_EVALUATED`.
- Código em inglês; mensagens e documentação em pt-BR.
- Não iniciar as Tasks 9–11 nem implementar rejeições novas.
- Um commit semântico por task, sem `git push`.
- Fonte normativa da auditoria: `tmp/NT_2025.002_v1.50_RTC_NF-e_IBS_CBS_IS.md`; consultar o PDF homônimo se a extração estiver ambígua.

---

### Task 1: Identidade correta das causas agrupadas

**Files:**
- Modify: `src/main/java/br/com/validadorlote/domain/RootCauseKey.java`
- Modify: `src/main/java/br/com/validadorlote/domain/RootCauseGrouper.java`
- Modify: `src/test/java/br/com/validadorlote/domain/RootCauseGrouperTest.java`

**Interfaces:**
- Consumes: `Finding.kind()`, `xsdCode()`, `field()`, `rejectionCode()`, `notEvaluatedCause()` e `ruleId()`.
- Produces: `RootCauseKey.from(Finding)` e agrupamento estável por identidade fiscal.

- [ ] **Step 1: Acrescentar testes RED para códigos de rejeição**

Adicionar a `RootCauseGrouperTest`:

```java
@Test
void differentRejectionCodesBecomeDifferentRootCauses() {
    var r1115 = Finding.rejection(Path.of("a.xml"), null, 1,
            "1115", "UB12-10", "Rejeição: IBS/CBS não informado", null);
    var r1025 = Finding.rejection(Path.of("b.xml"), null, 1,
            "1025", "UB14-25",
            "Rejeição: cClassTrib do IBS/CBS não permitido neste modelo de DFe", null);

    var causes = new RootCauseGrouper().group(List.of(r1115, r1025), NO_TEXTS);

    assertThat(causes).hasSize(2);
    assertThat(causes).extracting(c -> c.key().rejectionCode())
            .containsExactlyInAnyOrder("1115", "1025");
}
```

- [ ] **Step 2: Acrescentar testes RED para `NOT_EVALUATED`**

```java
@Test
void sharedPreconditionIgnoresRuleIdWhenGrouping() {
    var first = Finding.notEvaluated(Path.of("a.xml"), null, 1,
            NotEvaluatedCause.CST_NOT_IN_TABLE, "UB13-20", "CST 999 ausente");
    var second = Finding.notEvaluated(Path.of("b.xml"), null, 2,
            NotEvaluatedCause.CST_NOT_IN_TABLE, "UB26-20", "CST 998 ausente");

    var causes = new RootCauseGrouper().group(List.of(first, second), NO_TEXTS);

    assertThat(causes).singleElement().satisfies(cause -> {
        assertThat(cause.key().notEvaluatedCause())
                .isEqualTo(NotEvaluatedCause.CST_NOT_IN_TABLE);
        assertThat(cause.key().ruleId()).isNull();
    });
}

@Test
void ruleSpecificCausesUseRuleIdToStaySeparate() {
    var first = Finding.notEvaluated(Path.of("a.xml"), null, 1,
            NotEvaluatedCause.RULE_SPECIFIC, "UB12-10", "CRT ilegível");
    var second = Finding.notEvaluated(Path.of("b.xml"), null, 2,
            NotEvaluatedCause.RULE_SPECIFIC, "UB27-10", "aritmética não coberta");

    var causes = new RootCauseGrouper().group(List.of(first, second), NO_TEXTS);

    assertThat(causes).hasSize(2);
    assertThat(causes).extracting(c -> c.key().ruleId())
            .containsExactlyInAnyOrder("UB12-10", "UB27-10");
}
```

- [ ] **Step 3: Rodar os testes e confirmar a falha correta**

Run:

```bash
./gradlew test --tests 'br.com.validadorlote.domain.RootCauseGrouperTest' --console=plain
```

Expected: `compileTestJava` falha porque `RootCauseKey` ainda não expõe `rejectionCode`,
`notEvaluatedCause` e `ruleId`. A falha comprova que o contrato novo ainda não existe.

- [ ] **Step 4: Implementar a chave explícita**

Substituir `RootCauseKey` por:

```java
public record RootCauseKey(FindingKind kind, String xsdCode, String field,
        String rejectionCode, NotEvaluatedCause notEvaluatedCause, String ruleId) {

    public RootCauseKey(FindingKind kind, String xsdCode, String field) {
        this(kind, xsdCode, field, null, null, null);
    }

    public static RootCauseKey from(Finding finding) {
        return switch (finding.kind()) {
            case SCHEMA -> new RootCauseKey(finding.kind(), finding.xsdCode(),
                    finding.field(), null, null, null);
            case REJECTION_RULE -> new RootCauseKey(finding.kind(), null, null,
                    finding.rejectionCode(), null, null);
            case NOT_EVALUATED -> {
                NotEvaluatedCause cause = finding.notEvaluatedCause();
                String specificRule = cause == NotEvaluatedCause.RULE_SPECIFIC
                        ? finding.ruleId() : null;
                yield new RootCauseKey(finding.kind(), null, null, null,
                        cause, specificRule);
            }
            case SIGNATURE_MISSING, UNREADABLE ->
                    new RootCauseKey(finding.kind(), null, null, null, null, null);
        };
    }
}
```

Em `RootCauseGrouper`, trocar a construção posicional pela referência à fábrica:

```java
findings.stream().collect(Collectors.groupingBy(
        RootCauseKey::from, LinkedHashMap::new, Collectors.toList()));
```

- [ ] **Step 5: Rodar testes focados e suíte de domínio**

Run:

```bash
./gradlew test --tests 'br.com.validadorlote.domain.*' \
  --tests 'br.com.validadorlote.infrastructure.xml.XsdErrorTranslatorTest' \
  --console=plain
```

Expected: todos verdes; os construtores de três argumentos continuam compatíveis.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/validadorlote/domain/RootCauseKey.java \
  src/main/java/br/com/validadorlote/domain/RootCauseGrouper.java \
  src/test/java/br/com/validadorlote/domain/RootCauseGrouperTest.java
git commit -m "fix(b6): separa causas por camada de validacao"
```

---

### Task 2: Separar mensagem oficial de explicação local

**Files:**
- Modify: `src/main/java/br/com/validadorlote/domain/Finding.java`
- Modify: `src/main/java/br/com/validadorlote/domain/RootCauseGrouper.java`
- Modify: `src/main/java/br/com/validadorlote/infrastructure/rules/RuleOutcome.java`
- Modify: `src/main/java/br/com/validadorlote/infrastructure/rules/ClassTribCstRule.java`
- Modify: `src/main/java/br/com/validadorlote/infrastructure/rules/RuleEngine.java`
- Modify: `src/test/java/br/com/validadorlote/domain/FindingTest.java`
- Modify: `src/test/java/br/com/validadorlote/domain/RootCauseGrouperTest.java`
- Modify: `src/test/java/br/com/validadorlote/infrastructure/rules/TableRulesTest.java`
- Modify: `src/test/java/br/com/validadorlote/infrastructure/rules/RuleEngineTest.java`

**Interfaces:**
- Consumes: `Finding.officialMessage`, `Finding.friendlyMessage`, `RuleOutcome.Rejeitado`.
- Produces: mensagem oficial literal da 1024 e explicações locais em `friendlyMessage`.

- [ ] **Step 1: Escrever testes RED do contrato de mensagem**

Em `FindingTest`, substituir a asserção que procura o motivo em `officialMessage` por:

```java
assertThat(f.officialMessage()).isNull();
assertThat(f.friendlyMessage()).contains("999999");
```

Em `TableRulesTest.classTribFromAnotherCstIsRejected`, usar:

```java
assertThat(rejeitado.officialMessage()).isEqualTo(
        "Rejeição: Rejeição: Classificação Tributária do IBS e da CBS "
                + "incompatível com o CST informado");
assertThat(rejeitado.friendlyMessage())
        .contains("011001").contains("011").contains("000");
```

Em `RuleEngineTest`, criar um caso que avalia CST `000` com cClassTrib `011001` e afirmar:

```java
assertThat(achados(doc("3"), item().cst("000").classTrib("011001")))
        .filteredOn(f -> "1024".equals(f.rejectionCode()))
        .singleElement()
        .satisfies(f -> {
            assertThat(f.officialMessage()).startsWith("Rejeição: Rejeição:");
            assertThat(f.friendlyMessage()).contains("011001").contains("CST 011");
        });
```

- [ ] **Step 2: Confirmar RED**

Run:

```bash
./gradlew test --tests 'br.com.validadorlote.domain.FindingTest' \
  --tests 'br.com.validadorlote.infrastructure.rules.TableRulesTest' \
  --tests 'br.com.validadorlote.infrastructure.rules.RuleEngineTest' \
  --console=plain
```

Expected: falhas porque o motivo não avaliado ainda está em `officialMessage`, a 1024 ainda altera
o texto oficial e `Rejeitado` ainda não possui `friendlyMessage`.

- [ ] **Step 3: Ampliar `RuleOutcome.Rejeitado` sem quebrar os demais chamadores**

```java
record Rejeitado(String rejectionCode, String ruleId, String officialMessage,
        String friendlyMessage) implements RuleOutcome {

    Rejeitado(String rejectionCode, String ruleId, String officialMessage) {
        this(rejectionCode, ruleId, officialMessage, null);
    }
}
```

- [ ] **Step 4: Corrigir as fábricas e o transporte pelo motor**

Em `Finding.notEvaluated`, construir:

```java
return new Finding(source, accessKey, item, FindingKind.NOT_EVALUATED, Severity.INFO,
        null, null, null, reason, null, null, null, ruleId, cause);
```

Em `RuleEngine.report`, passar:

```java
rejeitado.officialMessage(), rejeitado.friendlyMessage()
```

Em `RootCauseGrouper`, o fallback será:

```java
String explanation = texts.explanation(key).orElseGet(() -> group.stream()
        .map(f -> f.friendlyMessage() != null
                ? f.friendlyMessage() : f.officialMessage())
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(""));
```

- [ ] **Step 5: Preservar a mensagem e o detalhe da 1024**

Em `ClassTribCstRule`:

```java
private static final String MENSAGEM_OFICIAL =
        "Rejeição: Rejeição: Classificação Tributária do IBS e da CBS "
                + "incompatível com o CST informado";
```

E no resultado divergente:

```java
String detalhe = "cClassTrib " + codigo + " pertence ao CST " + cstDaClassificacao
        + "; o item informou CST " + cst;
return new RuleOutcome.Rejeitado(
        rejectionCode(), ruleId(), MENSAGEM_OFICIAL, detalhe);
```

- [ ] **Step 6: Rodar os testes focados e a suíte**

Run:

```bash
./gradlew test --tests 'br.com.validadorlote.domain.FindingTest' \
  --tests 'br.com.validadorlote.domain.RootCauseGrouperTest' \
  --tests 'br.com.validadorlote.infrastructure.rules.*' \
  --console=plain
```

Expected: todos verdes.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/br/com/validadorlote/domain \
  src/main/java/br/com/validadorlote/infrastructure/rules \
  src/test/java/br/com/validadorlote/domain \
  src/test/java/br/com/validadorlote/infrastructure/rules
git commit -m "fix(b6): preserva proveniencia das mensagens"
```

---

### Task 3: Falhar alto ao carregar tabela fiscal inválida

**Files:**
- Modify: `src/main/java/br/com/validadorlote/infrastructure/tables/FiscalTables.java`
- Modify: `src/test/java/br/com/validadorlote/infrastructure/tables/FiscalTablesTest.java`

**Interfaces:**
- Consumes: `/tables/cst-cclasstrib.json`.
- Produces: `static FiscalTables load(InputStream)` com validação estrita, visível apenas no pacote.

- [ ] **Step 1: Criar fixture JSON mínima no próprio teste**

Adicionar a `FiscalTablesTest`:

```java
private static InputStream json(String body) {
    return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
}

private static final String MINIMAL_TABLE = """
        [{
          "cst": "000",
          "nome": "Tributação integral",
          "exigeGrupo": true,
          "exigeReducao": false,
          "permiteDiferimento": false,
          "iniVig": "2025-05-05T00:00:00",
          "fimVig": null,
          "classificacoes": [{
            "codigo": "000001",
            "nome": "Situações tributadas integralmente",
            "nfe": true,
            "nfce": true,
            "percRedIbs": 0.0,
            "percRedCbs": 0.0,
            "iniVig": "2025-05-05T00:00:00",
            "fimVig": null
          }]
        }]
        """;
```

Importar `ByteArrayInputStream`, `InputStream` e `StandardCharsets`.

- [ ] **Step 2: Escrever testes RED para estrutura, booleano ausente e duplicidade**

```java
@Test
void rootMustBeAnArray() {
    assertThatThrownBy(() -> FiscalTables.load(json("{}")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("lista");
}

@Test
void malformedJsonIsReportedAsInvalidFiscalTable() {
    assertThatThrownBy(() -> FiscalTables.load(json("[{")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JSON");
}

@Test
void missingRequiredBooleanFailsInsteadOfDefaultingToFalse() {
    String malformed = MINIMAL_TABLE.replace("\"nfe\": true,", "");

    assertThatThrownBy(() -> FiscalTables.load(json(malformed)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("nfe")
            .hasMessageContaining("000001");
}

@Test
void duplicateCstFailsInsteadOfOverwritingTheFirstEntry() {
    String duplicated = "[" + MINIMAL_TABLE.substring(1, MINIMAL_TABLE.length() - 1)
            + "," + MINIMAL_TABLE.substring(1);

    assertThatThrownBy(() -> FiscalTables.load(json(duplicated)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CST duplicado")
            .hasMessageContaining("000");
}

@Test
void malformedRequiredDateFailsInsteadOfBecomingOpenEnded() {
    String malformed = MINIMAL_TABLE.replace(
            "2025-05-05T00:00:00", "data-invalida");

    assertThatThrownBy(() -> FiscalTables.load(json(malformed)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("iniVig");
}
```

- [ ] **Step 3: Confirmar RED**

Run:

```bash
./gradlew test --tests \
  'br.com.validadorlote.infrastructure.tables.FiscalTablesTest' --console=plain
```

Expected: `compileTestJava` falha porque `load(InputStream)` não existe.

- [ ] **Step 4: Implementar leitura estrita**

Criar `static FiscalTables load(InputStream in)` e fazer `load()` apenas abrir o recurso e delegar.
Exigir que a raiz seja um array. Erros de parsing, tipo ou conteúdo do recurso devem sair como
`IllegalStateException` contextualizada; `UncheckedIOException` fica reservado a falha de I/O ao
abrir/ler o recurso, não a JSON fiscal sintaticamente inválido.
Usar helpers package-private ou private com estes contratos:

```java
private static String requiredText(JsonNode node, String field, String context)
private static boolean requiredBoolean(JsonNode node, String field, String context)
private static JsonNode requiredArray(JsonNode node, String field, String context)
private static LocalDate requiredDate(JsonNode node, String field, String context)
private static LocalDate optionalDate(JsonNode node, String field, String context)
private static BigDecimal optionalDecimal(JsonNode node, String field, String context)
```

Regras mínimas dos helpers:

```java
JsonNode value = node.get(field);
if (value == null || !value.isBoolean()) {
    throw new IllegalStateException("Campo booleano obrigatório '" + field
            + "' inválido em " + context);
}
```

Datas devem usar o prefixo ISO de dez caracteres e encapsular `DateTimeParseException` em
`IllegalStateException`. `fimVig` e percentuais aceitam somente `null` explícito ou valor do tipo
esperado. Inserções nos mapas usarão `putIfAbsent` e falharão em CST ou classificação duplicada.

- [ ] **Step 5: Rodar testes focados**

Run:

```bash
./gradlew test --tests \
  'br.com.validadorlote.infrastructure.tables.FiscalTablesTest' --console=plain
```

Expected: todos verdes, inclusive os testes que consultam a base real.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/validadorlote/infrastructure/tables/FiscalTables.java \
  src/test/java/br/com/validadorlote/infrastructure/tables/FiscalTablesTest.java
git commit -m "fix(b6): valida tabela fiscal ao carregar"
```

---

### Task 4: Endurecer a atualização e completar a proveniência

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/tables/cst-cclasstrib.json`
- Modify: `src/main/resources/tables/manifest.properties`
- Modify: `src/main/java/br/com/validadorlote/infrastructure/tables/TablesManifest.java`
- Modify: `src/test/java/br/com/validadorlote/infrastructure/tables/FiscalTablesTest.java`

**Interfaces:**
- Consumes: `dadosOriginais` da página oficial SVRS.
- Produces: JSON formatado, validado por inteiro, e manifesto com IT 2025.002 v1.60.

- [ ] **Step 1: Escrever teste RED de proveniência normativa**

Em `FiscalTablesTest`:

```java
@Test
void provenanceNamesTheOfficialPublicationVersion() {
    assertThat(tables.provenance())
            .contains("Informe Técnico 2025.002")
            .contains("v1.60")
            .contains("23/06/2026");
}
```

Adaptar também `provenanceNamesSourceAndDate` para verificar com `contains` a URL e a data de
extração, pois `describe()` passará a começar pela referência normativa.

- [ ] **Step 2: Confirmar RED**

Run:

```bash
./gradlew test --tests \
  'br.com.validadorlote.infrastructure.tables.FiscalTablesTest.provenanceNamesTheOfficialPublicationVersion' \
  --console=plain
```

Expected: falha porque o manifesto atual só descreve URL e data de extração.

- [ ] **Step 3: Ampliar o manifesto e o leitor**

Adicionar ao recurso:

```properties
tables.reference=Informe Técnico 2025.002
tables.referenceVersion=1.60
tables.referencePublishedAt=2026-06-23
```

Em `TablesManifest`, expor getters e formatar:

```java
public String reference() { return props.getProperty("tables.reference"); }
public String referenceVersion() {
    return props.getProperty("tables.referenceVersion");
}
public LocalDate referencePublishedAt() {
    return LocalDate.parse(props.getProperty("tables.referencePublishedAt"));
}

public String describe() {
    return reference() + " v" + referenceVersion()
            + ", publicada em "
            + referencePublishedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            + "; tabelas de " + source() + ", extraídas em "
            + props.getProperty("tables.extractedAt");
}
```

- [ ] **Step 4: Validar todas as linhas no `updateFiscalTables`**

Substituir a verificação do primeiro registro por closures que percorram `bruto.eachWithIndex` e
`ClassificacoesTributarias.eachWithIndex`. Conferir:

```groovy
def requiredBoolean = { map, field, context ->
    if (!map.containsKey(field) || !(map[field] instanceof Boolean)) {
        throw new GradleException(
            "Campo booleano '${field}' inválido em ${context}. Formato mudou.")
    }
}
def requiredText = { map, field, context ->
    if (!map.containsKey(field) || !(map[field] instanceof String)
            || map[field].trim().isEmpty()) {
        throw new GradleException(
            "Campo textual '${field}' inválido em ${context}. Formato mudou.")
    }
}
def validDate = { value, field, context, required ->
    if (value == null && !required) return
    if (!(value instanceof String) || value.length() < 10) {
        throw new GradleException("Data '${field}' inválida em ${context}.")
    }
    try {
        java.time.LocalDate.parse(value.substring(0, 10))
    } catch (java.time.DateTimeException e) {
        throw new GradleException("Data '${field}' inválida em ${context}.", e)
    }
}
```

Validar por CST: `Cst`, `NomeCst`, `IndExigeTrib`, `IndReducaoAliq`, `IndDiferimento`,
`DthIniVig`, `DthFimVig` e a coleção `ClassificacoesTributarias`. Validar por classificação:
`CodClassTrib`, `Cst`, `NomeReduzido`, `IndNfe`, `IndNfce`, `DthIniVig`, `DthFimVig`,
`PercRedIbs` e `PercRedCbs`. Percentuais não nulos precisam ser `Number`; `ct.Cst` precisa ser igual
a `cst.Cst`.

Acrescentar detecção de CST duplicado antes da destilação:

```groovy
def cstsDuplicados = bruto.collect { it.Cst }
        .countBy { it }
        .findAll { codigo, vezes -> vezes > 1 }
        .keySet()
if (!cstsDuplicados.isEmpty()) {
    throw new GradleException("CSTs duplicados: ${cstsDuplicados}.")
}
```

- [ ] **Step 5: Tornar o diff do JSON legível**

Na escrita:

```groovy
arquivoAtual.text = groovy.json.JsonOutput.prettyPrint(
        groovy.json.JsonOutput.toJson(destilado)) + System.lineSeparator()
```

Registrar o hash canônico, formatar mecanicamente o recurso já versionado com `jq` e confirmar que
o conteúdo não mudou:

```bash
table_hash_before=$(jq -S -c . src/main/resources/tables/cst-cclasstrib.json | sha256sum | cut -d' ' -f1)
tmp_json=$(mktemp)
jq . src/main/resources/tables/cst-cclasstrib.json > "$tmp_json"
chmod --reference=src/main/resources/tables/cst-cclasstrib.json "$tmp_json"
mv "$tmp_json" src/main/resources/tables/cst-cclasstrib.json
table_hash_after=$(jq -S -c . src/main/resources/tables/cst-cclasstrib.json | sha256sum | cut -d' ' -f1)
test "$table_hash_before" = "$table_hash_after"
```

O comando `test` deve sair com código zero.

- [ ] **Step 6: Preservar os campos manuais na task**

Carregar o manifesto anterior e escrever `tables.referenceVersion` e
`tables.referencePublishedAt` com os valores existentes. Se ausentes, usar
`ATUALIZAR-MANUALMENTE`; ao final, emitir:

```groovy
logger.lifecycle(
    'Tabela atualizada. CONFIRA tables.referenceVersion e tables.referencePublishedAt no manifesto.')
```

- [ ] **Step 7: Rodar testes e conferir o diff**

Run:

```bash
./gradlew test --tests \
  'br.com.validadorlote.infrastructure.tables.FiscalTablesTest' --console=plain
git diff --check
git diff --stat
```

Expected: testes verdes; JSON multilinha; nenhum whitespace inválido.

- [ ] **Step 8: Commit**

```bash
git add build.gradle src/main/resources/tables \
  src/main/java/br/com/validadorlote/infrastructure/tables/TablesManifest.java \
  src/test/java/br/com/validadorlote/infrastructure/tables/FiscalTablesTest.java
git commit -m "fix(b6): endurece ingestao e proveniencia fiscal"
```

---

### Task 5: Reconciliar decisões e propriedade dos débitos

**Files:**
- Modify: `docs/decisions.md`
- Modify: `docs/workflow.md`
- Modify: `docs/superpowers/specs/2026-07-27-camada-rejeicao-design.md`
- Modify: `docs/superpowers/plans/2026-07-27-camada-rejeicao.md`
- Modify: `.superpowers/sdd/progress.md`

**Interfaces:**
- Consumes: código corrigido nas Tasks 1–4 e brief vigente da Task 9.
- Produces: documentação canônica sem contradições de escopo ou propriedade.

- [ ] **Step 1: Atualizar as decisões**

Registrar D-025 e D-026 antes de D-027:

- D-025: ingestão manual da tabela SVRS, validação integral, manifestos separados por compatibilidade
  com o contrato existente de schemas.
- D-026: primeiro corte terminou com onze códigos, listados nominalmente, em vez dos seis inicialmente
  recomendados.

Reescrever D-033 para dizer que `RootCauseKey.from(Finding)` implementa a chave por camada e que
`friendlyMessage`, não `officialMessage`, contém motivo local. Reescrever D-035 atribuindo
`itemNumber` não único ao bloco de integração/apresentação.

- [ ] **Step 2: Corrigir workflow e ledger**

Em `docs/workflow.md`, substituir a afirmação de que tudo em `.superpowers/` é ignorado por:

```markdown
`progress.md` é o único arquivo versionado de `.superpowers/sdd/`; briefs, relatórios e diffs
continuam locais e descartáveis. `git clean -fdx` remove o scratch, mas preserva o ledger rastreado.
```

Em `.superpowers/sdd/progress.md`, preservar o histórico e acrescentar um adendo datado corrigindo
as linhas antigas: ledger versionado; Task 9 atual é somente fixtures; agregação pertence ao bloco
seguinte.

- [ ] **Step 3: Atualizar spec e plano vigentes**

Mudar a spec do Bloco 6 de “Rascunho para revisão” para “Aprovada — implementação em andamento”.
No plano, substituir textos de fechamento que dizem seis regras pela lista das onze. Registrar que
o sufixo `[nItem: 999]` das mensagens da NT é representado por `Finding.itemNumber`.

- [ ] **Step 4: Verificar que as contradições sumiram**

Run:

```bash
rg -n 'seis rejeições|Cobre 6|Task 9.*agrega|Task 9.*agregação|Tudo em `.superpowers/`' \
  docs .superpowers/sdd/progress.md
rg -n "D-025|D-026|D-033|D-035|D-036" docs/decisions.md
git diff --check
```

Expected: a primeira busca não encontra afirmação vigente contraditória; ocorrências históricas
claramente rotuladas podem permanecer no ledger. A segunda encontra todas as decisões.

- [ ] **Step 5: Commit**

```bash
git add docs/decisions.md docs/workflow.md \
  docs/superpowers/specs/2026-07-27-camada-rejeicao-design.md \
  docs/superpowers/plans/2026-07-27-camada-rejeicao.md \
  .superpowers/sdd/progress.md
git commit -m "docs(b6): reconcilia decisoes e debitos do review"
```

---

### Task 6: Auditar as onze rejeições contra a NT local

**Files:**
- Create: `docs/validacao/auditoria-rejeicoes-bloco-6.md`
- Modify when evidence requires: `src/main/java/br/com/validadorlote/infrastructure/rules/*.java`
- Modify when evidence requires: matching tests under `src/test/java/br/com/validadorlote/infrastructure/rules/`

**Interfaces:**
- Consumes: NT Markdown/PDF em `tmp/`, regras e testes corrigidos.
- Produces: matriz rastreável de conformidade normativa das onze rejeições.

- [ ] **Step 1: Extrair somente as onze regras da fonte local**

Run:

```bash
mkdir -p docs/validacao
rg -n -A18 -B3 \
  "UB12-10|UB13-20|UB13-30|UB14-20|UB14-25|UB26-20|UB27-10|UB45-20|UB46-10|UB64-20|UB65-10" \
  tmp/NT_2025.002_v1.50_RTC_NF-e_IBS_CBS_IS.md
```

Consultar visualmente as páginas correspondentes do PDF com `pdftotext -layout` ou screenshot
somente se a célula Markdown misturar colunas.

- [ ] **Step 2: Conferir a identidade de cada regra**

Para cada par abaixo, comparar `rejectionCode()`, `ruleId()` e mensagem:

```text
1115/UB12-10
1021/UB13-20
1022/UB13-30
1024/UB14-20
1025/UB14-25
1033/UB26-20
1074/UB45-20
1079/UB64-20
1034/UB27-10
1046/UB46-10
1063/UB65-10
```

A mensagem registrada deve ser o texto-base da NT; `[nItem: 999]` fica representado por
`Finding.itemNumber`.

- [ ] **Step 3: Conferir lógica, mapas e exceções**

Preencher uma linha por regra com:

```markdown
| Código/ID | Modelos | Gatilho NT | Exceções/observações | Dados usados no código |
Tabela/indicador | Cobertura | Evidência | Resultado |
```

Verificar especificamente:

- 1115: `IBSCBS`, CRT 3 em 03/08/2026, CRT 1/2/4 em 04/01/2027, duas exceções;
- 1021/1022: `gIBSCBS`, `ind_gIBSCBS`, exceção `tpNFDebito=07`;
- 1024: vínculo aninhado cClassTrib→CST;
- 1025: `IndNfe` para modelo 55 e `IndNfce` para 65;
- 1033/1074/1079: `ind_gRed`, `gCompraGov`, exceção `ind_gIBSCBS=0`, esfera correta;
- 1034/1046/1063: percentual IBS compartilhado por UF/município, percentual CBS, comparação decimal,
  ramo conservador de compra governamental e de `ind_gRed=0`.

- [ ] **Step 4: Corrigir qualquer divergência com novo ciclo RED–GREEN**

Para cada divergência normativa real:

1. escrever teste mínimo que reproduza o gatilho ou exceção;
2. rodar o teste e confirmar a falha;
3. corrigir apenas a regra atingida;
4. rodar testes da classe e do `RuleEngine`;
5. registrar na matriz a mudança e a evidência.

Se não houver divergência, não alterar as regras.

- [ ] **Step 5: Registrar limitações deliberadas**

Na matriz, marcar como `PARCIAL — NOT_EVALUATED`:

- combustível monofásico da Exceção 2 da 1115, enquanto faltar a tabela;
- compra governamental e ramo `ind_gRed=0` das regras percentuais, conforme D-030.

Não chamar esses ramos de “cobertos” nem de “conformes”.

- [ ] **Step 6: Rodar testes focados**

Run:

```bash
./gradlew test --tests 'br.com.validadorlote.infrastructure.rules.*' \
  --tests 'br.com.validadorlote.infrastructure.xml.TaxGroupExtractorTest' \
  --tests 'br.com.validadorlote.infrastructure.xml.XmlMetadataParserTest' \
  --console=plain
```

Expected: todos verdes.

- [ ] **Step 7: Commit**

Se a auditoria não exigir mudança de código:

```bash
git add docs/validacao/auditoria-rejeicoes-bloco-6.md
git commit -m "docs(b6): audita rejeicoes contra a NT 2025.002"
```

Se houver divergência normativa corrigida, incluir somente os arquivos de regra/teste atingidos e
usar:

```bash
git add docs/validacao/auditoria-rejeicoes-bloco-6.md
git add -u src/main/java/br/com/validadorlote/infrastructure/rules \
  src/test/java/br/com/validadorlote/infrastructure/rules
git commit -m "fix(b6): alinha rejeicoes a NT 2025.002"
```

---

### Task 7: Estudar rejeições futuras de alto valor e baixo custo

**Files:**
- Create: `docs/pesquisa/candidatas-rejeicao-pos-b6.md`

**Interfaces:**
- Consumes: grupo UB da NT local, `FiscalDocument`, `ItemTaxGroup`, tabela SVRS bruta/destilada.
- Produces: backlog priorizado; nenhum código de regra.

- [ ] **Step 1: Enumerar as regras UB de NF-e/NFC-e**

Extrair do Markdown local todas as linhas `UB` de modelos 55/65 e excluir regras exclusivas de
Imposto Seletivo e eventos:

```bash
rg -n "^UB|\\| UB" tmp/NT_2025.002_v1.50_RTC_NF-e_IBS_CBS_IS.md \
  .superpowers/sdd/nt-regras-catalogo.md
```

- [ ] **Step 2: Mapear dados já disponíveis**

Inventariar:

```bash
nl -ba src/main/java/br/com/validadorlote/domain/FiscalDocument.java
nl -ba src/main/java/br/com/validadorlote/infrastructure/xml/TaxGroupExtractor.java
nl -ba src/main/java/br/com/validadorlote/infrastructure/tables/CstEntry.java
nl -ba src/main/java/br/com/validadorlote/infrastructure/tables/ClassTribEntry.java
```

Classificar cada campo exigido como `já extraído`, `presente no XML`, `presente na SVRS`,
`nova tabela oficial`, `cálculo` ou `consulta externa`.

- [ ] **Step 3: Aplicar a matriz de priorização**

Usar estas colunas:

```markdown
| Código/ID | Regra em uma frase | Dados necessários | Disponibilidade |
Exceções completas? | Esforço | Risco de falso positivo | Valor provável | Recomendação |
```

Escala:

- esforço baixo: reutiliza dados atuais ou acrescenta captura local simples;
- esforço médio: exige novo indicador na destilação e novos grupos no extractor;
- esforço alto: nova tabela, cálculo, cruzamento entre documentos ou rede;
- recomendação `agora`, `depois` ou `não recomendar`.

Valor provável será rotulado como hipótese quando não houver frequência observada.

- [ ] **Step 4: Conferir candidatas adjacentes**

Avaliar nominalmente, sem presumir que serão recomendadas:

- 1023/UB14-10 — cClassTrib inexistente;
- 1151/UB13-39 e 1116/UB13-40 — grupo monofásico;
- 1131/UB13-44 e 1132/UB13-45 — transferência de crédito;
- 1065/UB68-10 e 1114/UB68-11 — tributação regular;
- demais regras de presença cujo indicador e exceções estejam na SVRS/XML.

Para 1023, registrar que base embarcada possivelmente velha impede acusar inexistência com segurança;
ausência na base continua candidata a `NOT_EVALUATED`, salvo existência de artefato oficial
versionado que elimine o risco.

- [ ] **Step 5: Commit**

```bash
git add docs/pesquisa/candidatas-rejeicao-pos-b6.md
git commit -m "docs(b6): prioriza proximas rejeicoes de baixo custo"
```

---

### Task 8: Verificação integral e review final

**Files:**
- Verify only; corrigir apenas achados comprovados.

**Interfaces:**
- Consumes: todos os commits desta rodada.
- Produces: evidência de suíte, arquitetura, integridade fiscal e árvore limpa.

- [ ] **Step 1: Rodar suíte limpa**

```bash
./gradlew clean test --console=plain
```

Expected: `BUILD SUCCESSFUL`, 0 failures e 0 errors.

- [ ] **Step 2: Confirmar contagens e árvore**

```bash
perl -ne 'if (/<testsuite\b[^>]*\btests="(\d+)"[^>]*\bskipped="(\d+)"[^>]*\bfailures="(\d+)"[^>]*\berrors="(\d+)"/) {$s++; $t+=$1; $sk+=$2; $f+=$3; $e+=$4} END {print "suites=$s tests=$t failures=$f errors=$e skipped=$sk\n"}' \
  build/test-results/test/TEST-*.xml
git diff --check
git status --short --branch
```

Expected: zero falhas/erros, nenhum arquivo staged ou modificação não explicada.

- [ ] **Step 3: Verificar conteúdo fiscal e proveniência**

```bash
jq '{csts:length, classTribs:([.[].classificacoes[]]|length)}' \
  src/main/resources/tables/cst-cclasstrib.json
rg -n "reference|referenceVersion|referencePublishedAt|source|extractedAt" \
  src/main/resources/tables/manifest.properties
```

Expected: 18 CSTs, 164 classificações e referência IT 2025.002 v1.60.

- [ ] **Step 4: Fazer revisão independente**

Usar `superpowers:requesting-code-review` sobre o intervalo:

```bash
BASE_SHA=4ee630b
HEAD_SHA=$(git rev-parse HEAD)
git diff --stat "$BASE_SHA..$HEAD_SHA"
```

O revisor deve conferir os achados originais, testes RED/GREEN registrados, contratos de mensagem,
ingestão, documentação, auditoria normativa e ausência de novas rejeições implementadas.

- [ ] **Step 5: Corrigir achados importantes e repetir a verificação**

Cada correção comportamental recebe teste que falha antes da mudança. Repetir Steps 1–3 até a revisão
não ter achado Critical ou Important.

- [ ] **Step 6: Apresentar o handoff**

Relatar ao usuário:

- commits e arquivos principais;
- contagem fresca de testes;
- resultado das onze regras contra a NT;
- limitações `NOT_EVALUATED`;
- candidatas futuras recomendadas, explicitamente ainda não implementadas;
- confirmação de que Tasks 9–11 permanecem pendentes.
