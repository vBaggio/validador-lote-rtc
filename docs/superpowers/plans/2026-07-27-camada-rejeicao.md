# Camada de previsão de rejeição (IBS/CBS) — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fazer o validador prever as rejeições da SEFAZ que a validação de schema não alcança, começando pelas seis que dominam a virada de 03/08/2026, com a corretude provada por comparação contra o validador oficial da SVRS.

**Architecture:** Uma camada nova de regras entra depois da validação XSD, dirigida pelos metadados das tabelas oficiais em vez de regras transcritas em código. Duas regras são de documento (dependem de CRT e vigência); as demais derivam de indicadores publicados por CST e por classificação tributária. Toda verificação termina em um de três desfechos — conforme, rejeição prevista ou **não avaliado** —, e o terceiro existe para nunca acusar o usuário de uma limitação nossa.

**Tech Stack:** Java 21, Gradle 8.14.3, JUnit 5 + AssertJ + ArchUnit, Jackson (nova dependência, para ler as tabelas embarcadas).

## Global Constraints

- Pacote raiz `br.com.validadorlote`. Código em **inglês**; mensagens de UI, erros amigáveis e docs em **pt-BR**.
- Regra de dependência: `presentation → application → {domain, infrastructure}`; `infrastructure → domain`; `domain → nada`. Garantida por `ArchitectureTest`.
- **Julgamento fiscal vem de artefato oficial.** Nenhuma tabela fiscal hardcoded em Java; nenhuma mensagem oficial reescrita em código.
- **Falso positivo é inaceitável; falso negativo é declarado.** Na dúvida, o desfecho é *não avaliado* — nunca rejeição.
- Toda consulta a tabela usa a **data do fato gerador do documento**, nunca a data corrente.
- Parsing XML sempre seguro: sem DTD, sem entidades externas.
- Rede **só** em tasks de ingestão do grupo `build setup`. Build e CI nunca tocam a rede.
- Comentários enxutos, javadoc onde agrega, sem dead code.
- **1 commit semântico por task**, escopo `b6`: `feat(b6): ...`, `test(b6): ...`. Terminar com `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Ajuste sequencial no último commit não pushado → `git commit --amend`, nunca cadeia de fixes.
- Branch do bloco: `bloco/6-camada-rejeicao`. **Nunca `git push` durante as tasks** — o push acontece no fechamento, após validação do usuário.
- Testes com `./gradlew test`. Não existe `gradle` no PATH — sempre o wrapper.

## Armadilha que já custou caro

A Calculadora expõe `possuiPercentualReducao` (por classificação tributária, verdadeiro em 60 de 161). **Não** é o `ind_gRed` da NT, que é por CST e verdadeiro em **3 de 18**. Usar o primeiro produz falso positivo em escala. O indicador correto é `IndReducaoAliq`, da tabela da SVRS.

---

## Estrutura de arquivos

```
src/main/java/br/com/validadorlote/
├── domain/
│   ├── FindingKind.java              MODIFICAR: + REJECTION_RULE, NOT_EVALUATED
│   ├── Finding.java                  MODIFICAR: + rejectionCode, ruleId
│   ├── FiscalDocument.java           MODIFICAR: + crt
│   └── BatchReport.java              MODIFICAR: + contadores dos 3 desfechos
└── infrastructure/
    ├── xml/
    │   ├── XmlMetadataParser.java    MODIFICAR: extrair CRT e grupos IBS/CBS
    │   └── TaxGroupExtractor.java    CRIAR: grupo IBS/CBS por item
    ├── tables/
    │   ├── FiscalTables.java         CRIAR: fachada de consulta, vigência por data
    │   ├── CstEntry.java             CRIAR: record do CST + indicadores
    │   ├── ClassTribEntry.java       CRIAR: record da classificação
    │   └── TablesManifest.java       CRIAR: proveniência
    └── rules/
        ├── RejectionRule.java        CRIAR: contrato de regra
        ├── RuleOutcome.java          CRIAR: os 3 desfechos
        ├── RuleEngine.java           CRIAR: orquestra + supressão em cascata
        └── rules/*.java              CRIAR: uma classe por regra
```

---

## Task 1: Domínio — os três desfechos e a identidade da rejeição

**Files:**
- Modify: `src/main/java/br/com/validadorlote/domain/FindingKind.java`
- Modify: `src/main/java/br/com/validadorlote/domain/Finding.java`
- Test: `src/test/java/br/com/validadorlote/domain/FindingTest.java` (criar)

**Interfaces:**
- Produces:
```java
enum FindingKind { SCHEMA, SIGNATURE_MISSING, UNREADABLE, REJECTION_RULE, NOT_EVALUATED }
record Finding(Path source, String accessKey, Integer itemNumber, FindingKind kind,
    Severity severity, String field, String xsdCode, String officialMessage,
    String friendlyMessage, Integer line, Integer column,
    String rejectionCode, String ruleId) {
    Finding withSeverity(Severity s);
    static Finding rejection(Path source, String accessKey, Integer item, String rejectionCode,
        String ruleId, String officialMessage, String friendlyMessage);
    static Finding notEvaluated(Path source, String accessKey, Integer item, String reason);
}
```

- [ ] **Step 1: Escrever o teste que falha**

`src/test/java/br/com/validadorlote/domain/FindingTest.java`:
```java
package br.com.validadorlote.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FindingTest {

    @Test
    void rejectionCarriesCodeAndRuleId() {
        var f = Finding.rejection(Path.of("a.xml"), "352607...", 1, "1115", "UB12-10",
                "Rejeição: IBS/CBS não informado", "O item não tem o grupo IBS/CBS.");

        assertThat(f.kind()).isEqualTo(FindingKind.REJECTION_RULE);
        assertThat(f.severity()).isEqualTo(Severity.REJECTION);
        assertThat(f.rejectionCode()).isEqualTo("1115");
        assertThat(f.ruleId()).isEqualTo("UB12-10");
        assertThat(f.xsdCode()).isNull();
    }

    @Test
    void notEvaluatedIsNeitherApprovedNorRejected() {
        // Base velha não é erro do emitente: acusar seria culpá-lo por limitação nossa.
        var f = Finding.notEvaluated(Path.of("a.xml"), null, 2,
                "cClassTrib 999999 não consta na base embarcada");

        assertThat(f.kind()).isEqualTo(FindingKind.NOT_EVALUATED);
        assertThat(f.severity()).isEqualTo(Severity.INFO);
        assertThat(f.rejectionCode()).isNull();
        assertThat(f.officialMessage()).contains("999999");
    }

    @Test
    void schemaFindingsKeepNullRejectionFields() {
        var f = new Finding(Path.of("a.xml"), null, 1, FindingKind.SCHEMA, Severity.REJECTION,
                "pCBS", "cvc-pattern-valid", "msg", null, 10, 5, null, null);

        assertThat(f.rejectionCode()).isNull();
        assertThat(f.ruleId()).isNull();
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.domain.FindingTest' --console=plain`
Expected: falha de compilação — `Finding.rejection` não existe.

- [ ] **Step 3: Implementar**

`FindingKind.java` — acrescentar dois valores ao enum existente:
```java
package br.com.validadorlote.domain;

/** Natureza de um achado. */
public enum FindingKind {
    SCHEMA,
    SIGNATURE_MISSING,
    UNREADABLE,
    /** Regra de negócio da NT: a SEFAZ rejeitaria este documento. */
    REJECTION_RULE,
    /** Faltou dado para julgar. Nunca somado aos conformes nem aos rejeitados. */
    NOT_EVALUATED
}
```

`Finding.java` — acrescentar os dois campos ao fim do record e as duas fábricas:
```java
package br.com.validadorlote.domain;

import java.nio.file.Path;

/** Um problema num documento. rejectionCode/ruleId só existem em achados de regra da NT. */
public record Finding(Path source, String accessKey, Integer itemNumber, FindingKind kind,
        Severity severity, String field, String xsdCode, String officialMessage,
        String friendlyMessage, Integer line, Integer column,
        String rejectionCode, String ruleId) {

    public Finding withSeverity(Severity newSeverity) {
        return new Finding(source, accessKey, itemNumber, kind, newSeverity, field, xsdCode,
                officialMessage, friendlyMessage, line, column, rejectionCode, ruleId);
    }

    /** Rejeição prevista: a mensagem oficial vem da NT e não é reescrita. */
    public static Finding rejection(Path source, String accessKey, Integer item,
            String rejectionCode, String ruleId, String officialMessage, String friendlyMessage) {
        return new Finding(source, accessKey, item, FindingKind.REJECTION_RULE, Severity.REJECTION,
                null, null, officialMessage, friendlyMessage, null, null, rejectionCode, ruleId);
    }

    /** Não foi possível julgar — falta dado na base embarcada, não é defeito do documento. */
    public static Finding notEvaluated(Path source, String accessKey, Integer item, String reason) {
        return new Finding(source, accessKey, item, FindingKind.NOT_EVALUATED, Severity.INFO,
                null, null, reason, null, null, null, null, null);
    }
}
```

- [ ] **Step 4: Corrigir os chamadores existentes**

O construtor ganhou dois parâmetros. Rode `./gradlew compileJava compileTestJava --console=plain` e acrescente `, null, null` ao fim de cada chamada de `new Finding(...)` que o compilador apontar. Os arquivos afetados são `SchemaValidatorEngine.java` e os testes de domínio, agrupador e motor XSD.

- [ ] **Step 5: Rodar a suíte inteira**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL — os 71 testes anteriores continuam verdes, mais 3 novos.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/validadorlote/domain src/test/java/br/com/validadorlote/domain \
        src/main/java/br/com/validadorlote/infrastructure/xml src/test/java/br/com/validadorlote/infrastructure/xml
git commit -m "feat(b6): três desfechos por verificação e identidade da rejeição no domínio"
```

---

## Task 2: Ingestão da tabela CST × cClassTrib

**Files:**
- Modify: `build.gradle`
- Create: `src/main/resources/tables/cst-cclasstrib.json` (gerado pela task)
- Create: `src/main/resources/tables/manifest.properties`

**Interfaces:**
- Produces: task Gradle `updateFiscalTables`; recursos embarcados com proveniência.

- [ ] **Step 1: Acrescentar a task ao build.gradle**

```groovy
// Ingestão da tabela oficial CST x cClassTrib publicada pela SVRS. Uso manual quando a base
// mudar. Rede APENAS aqui — build e CI nunca tocam a rede (mesma política de updateSchemas).
tasks.register('updateFiscalTables') {
    description = 'Atualiza src/main/resources/tables a partir do portal da SVRS.'
    group = 'build setup'
    doLast {
        def url = 'https://dfe-portal.svrs.rs.gov.br/DFE/ClassificacaoTributaria'
        logger.lifecycle("Baixando ${url} ...")
        def html = new URL(url).getText('UTF-8')

        def m = (html =~ /dadosOriginais\s*=\s*(\[.*?\]);/)
        if (!m.find()) {
            throw new GradleException(
                'Não encontrei dadosOriginais na página da SVRS. O layout mudou — ' +
                'a extração precisa ser revista antes de atualizar a base.')
        }
        def bruto = new groovy.json.JsonSlurper().parseText(m.group(1))

        // Falha ruidosa: base vazia ou sem os indicadores é pior que base velha.
        if (bruto.size() < 10) {
            throw new GradleException("Esperava ao menos 10 CSTs, vieram ${bruto.size()}.")
        }
        def obrigatorios = ['Cst', 'IndExigeTrib', 'IndReducaoAliq', 'ClassificacoesTributarias']
        obrigatorios.each { campo ->
            if (!bruto[0].containsKey(campo)) {
                throw new GradleException("Campo '${campo}' ausente no primeiro CST. Formato mudou.")
            }
        }

        def destilado = bruto.collect { cst ->
            [
                cst              : cst.Cst,
                nome             : cst.NomeCst,
                exigeGrupo       : cst.IndExigeTrib,
                exigeReducao     : cst.IndReducaoAliq,
                permiteDiferimento: cst.IndDiferimento,
                iniVig           : cst.DthIniVig,
                fimVig           : cst.DthFimVig,
                classificacoes   : (cst.ClassificacoesTributarias ?: []).collect { ct ->
                    [
                        codigo   : ct.CodClassTrib,
                        nome     : ct.NomeReduzido,
                        nfe      : ct.IndNfe,
                        nfce     : ct.IndNfce,
                        percRedIbs: ct.PercRedIbs,
                        percRedCbs: ct.PercRedCbs,
                        iniVig   : ct.DthIniVig,
                        fimVig   : ct.DthFimVig
                    ]
                }
            ]
        }
        def totalCt = destilado.sum { it.classificacoes.size() }
        if (totalCt < 100) {
            throw new GradleException("Esperava ao menos 100 classificações, vieram ${totalCt}.")
        }

        def dir = file('src/main/resources/tables')
        dir.mkdirs()
        new File(dir, 'cst-cclasstrib.json').text =
                groovy.json.JsonOutput.toJson(destilado)

        def props = new File(dir, 'manifest.properties')
        props.text = """# Proveniência dos artefatos oficiais embarcados.
# Atualize com: ./gradlew updateSchemas updateFiscalTables
tables.source=${url}
tables.extractedAt=${new Date().format('yyyy-MM-dd')}
tables.cstCount=${destilado.size()}
tables.classTribCount=${totalCt}
"""
        logger.lifecycle("Gravados ${destilado.size()} CSTs e ${totalCt} classificações.")
    }
}
```

- [ ] **Step 2: Executar a ingestão**

Run: `./gradlew updateFiscalTables --console=plain`
Expected: `Gravados 18 CSTs e 164 classificações.`

- [ ] **Step 3: Conferir o resultado**

Run: `python3 -c "import json;d=json.load(open('src/main/resources/tables/cst-cclasstrib.json'));print(len(d),'CSTs');print([c['cst'] for c in d if c['exigeReducao']])"`
Expected: `18 CSTs` e a lista `['011', '200', '515']` — os três que exigem grupo de redução.

- [ ] **Step 4: Commit**

```bash
git add build.gradle src/main/resources/tables
git commit -m "feat(b6): ingestão da tabela oficial CST x cClassTrib da SVRS"
```

---

## Task 3: Consulta às tabelas com vigência por data

**Files:**
- Create: `src/main/java/br/com/validadorlote/infrastructure/tables/CstEntry.java`
- Create: `src/main/java/br/com/validadorlote/infrastructure/tables/ClassTribEntry.java`
- Create: `src/main/java/br/com/validadorlote/infrastructure/tables/FiscalTables.java`
- Create: `src/main/java/br/com/validadorlote/infrastructure/tables/TablesManifest.java`
- Modify: `build.gradle` (dependência Jackson)
- Test: `src/test/java/br/com/validadorlote/infrastructure/tables/FiscalTablesTest.java`

**Interfaces:**
- Produces:
```java
record CstEntry(String cst, String nome, boolean exigeGrupo, boolean exigeReducao,
    boolean permiteDiferimento, LocalDate iniVig, LocalDate fimVig) {}
record ClassTribEntry(String codigo, String nome, String cst, boolean nfe, boolean nfce,
    BigDecimal percRedIbs, BigDecimal percRedCbs, LocalDate iniVig, LocalDate fimVig) {}
final class FiscalTables {
    static FiscalTables load();                                       // do classpath
    Optional<CstEntry> cst(String codigo, LocalDate data);
    Optional<ClassTribEntry> classTrib(String codigo, LocalDate data);
    String provenance();                                              // texto para o relatório
}
```

- [ ] **Step 1: Acrescentar Jackson ao build.gradle**

Na seção `dependencies`, após a linha do FlatLaf:
```groovy
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.18.2'
```

- [ ] **Step 2: Escrever o teste que falha**

```java
package br.com.validadorlote.infrastructure.tables;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FiscalTablesTest {

    private static FiscalTables tables;
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 3);

    @BeforeAll
    static void load() {
        tables = FiscalTables.load();
    }

    @Test
    void findsCstWithItsIndicators() {
        var cst = tables.cst("011", HOJE).orElseThrow();

        assertThat(cst.exigeGrupo()).isTrue();
        assertThat(cst.exigeReducao()).isTrue();
    }

    @Test
    void onlyThreeCstsRequireReductionGroup() {
        // Guarda contra a confusão com possuiPercentualReducao da Calculadora, que é
        // verdadeiro em 60 de 161 classificações. O indicador real é por CST.
        long comReducao = "000 010 011 200 220 221 222 400 410 510 515 550 620 800 810 811 820 830"
                .lines().flatMap(s -> java.util.Arrays.stream(s.split(" ")))
                .filter(c -> tables.cst(c, HOJE).map(CstEntry::exigeReducao).orElse(false))
                .count();

        assertThat(comReducao).isEqualTo(3);
    }

    @Test
    void findsClassTribWithModelIndicators() {
        var ct = tables.classTrib("000001", HOJE).orElseThrow();

        assertThat(ct.cst()).isEqualTo("000");
        assertThat(ct.nfe()).isTrue();
        assertThat(ct.nfce()).isTrue();
    }

    @Test
    void unknownCodeYieldsEmptyNotException() {
        // Código publicado depois da nossa extração: base velha, não erro do emitente.
        assertThat(tables.classTrib("999999", HOJE)).isEmpty();
        assertThat(tables.cst("999", HOJE)).isEmpty();
    }

    @Test
    void respectsValidityOnTheOperationDate() {
        // Nenhum registro vigente em 2020 — a base começa em 2025.
        assertThat(tables.cst("000", LocalDate.of(2020, 1, 1))).isEmpty();
        assertThat(tables.cst("000", HOJE)).isPresent();
    }

    @Test
    void provenanceNamesSourceAndDate() {
        assertThat(tables.provenance()).contains("svrs").contains("2026");
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.tables.*' --console=plain`
Expected: falha de compilação.

- [ ] **Step 4: Implementar**

`CstEntry.java`:
```java
package br.com.validadorlote.infrastructure.tables;

import java.time.LocalDate;

/** Uma situação tributária com os indicadores que a NT referencia. */
public record CstEntry(String cst, String nome, boolean exigeGrupo, boolean exigeReducao,
        boolean permiteDiferimento, LocalDate iniVig, LocalDate fimVig) {

    boolean vigenteEm(LocalDate data) {
        return (iniVig == null || !data.isBefore(iniVig))
                && (fimVig == null || !data.isAfter(fimVig));
    }
}
```

`ClassTribEntry.java`:
```java
package br.com.validadorlote.infrastructure.tables;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Uma classificação tributária, com os indicadores por modelo de documento. */
public record ClassTribEntry(String codigo, String nome, String cst, boolean nfe, boolean nfce,
        BigDecimal percRedIbs, BigDecimal percRedCbs, LocalDate iniVig, LocalDate fimVig) {

    boolean vigenteEm(LocalDate data) {
        return (iniVig == null || !data.isBefore(iniVig))
                && (fimVig == null || !data.isAfter(fimVig));
    }

    /** Se a classificação é permitida no modelo informado (55 = NF-e, 65 = NFC-e). */
    public boolean permiteModelo(String modelo) {
        return "65".equals(modelo) ? nfce : nfe;
    }
}
```

`TablesManifest.java`:
```java
package br.com.validadorlote.infrastructure.tables;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Properties;

/** Proveniência das tabelas embarcadas: de onde vieram e quando. */
public final class TablesManifest {

    private final Properties props = new Properties();

    TablesManifest() {
        try (InputStream in = TablesManifest.class.getResourceAsStream("/tables/manifest.properties")) {
            if (in == null) {
                throw new IllegalStateException("manifest.properties ausente — rode ./gradlew updateFiscalTables");
            }
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String source() { return props.getProperty("tables.source", "desconhecida"); }

    public LocalDate extractedAt() {
        String v = props.getProperty("tables.extractedAt");
        return v == null ? null : LocalDate.parse(v);
    }

    public String describe() {
        return "tabelas de " + source() + ", extraídas em " + props.getProperty("tables.extractedAt");
    }
}
```

`FiscalTables.java`:
```java
package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Consulta às tabelas oficiais embarcadas. Toda busca é por código <b>e data do fato gerador</b>:
 * validar um documento de agosto contra a vigência de dezembro daria veredito errado.
 */
public final class FiscalTables {

    private final Map<String, CstEntry> csts;
    private final Map<String, ClassTribEntry> classificacoes;
    private final TablesManifest manifest;

    private FiscalTables(Map<String, CstEntry> csts, Map<String, ClassTribEntry> classificacoes) {
        this.csts = csts;
        this.classificacoes = classificacoes;
        this.manifest = new TablesManifest();
    }

    public static FiscalTables load() {
        try (InputStream in = FiscalTables.class.getResourceAsStream("/tables/cst-cclasstrib.json")) {
            if (in == null) {
                throw new IllegalStateException(
                        "Tabelas ausentes no classpath — rode ./gradlew updateFiscalTables");
            }
            JsonNode raiz = new ObjectMapper().readTree(in);
            Map<String, CstEntry> csts = new HashMap<>();
            Map<String, ClassTribEntry> cts = new HashMap<>();
            for (JsonNode c : raiz) {
                String cst = c.path("cst").asText();
                csts.put(cst, new CstEntry(cst, c.path("nome").asText(),
                        c.path("exigeGrupo").asBoolean(), c.path("exigeReducao").asBoolean(),
                        c.path("permiteDiferimento").asBoolean(),
                        data(c, "iniVig"), data(c, "fimVig")));
                for (JsonNode ct : c.path("classificacoes")) {
                    String codigo = ct.path("codigo").asText();
                    cts.put(codigo, new ClassTribEntry(codigo, ct.path("nome").asText(), cst,
                            ct.path("nfe").asBoolean(), ct.path("nfce").asBoolean(),
                            decimal(ct, "percRedIbs"), decimal(ct, "percRedCbs"),
                            data(ct, "iniVig"), data(ct, "fimVig")));
                }
            }
            return new FiscalTables(csts, cts);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static LocalDate data(JsonNode no, String campo) {
        String v = no.path(campo).asText(null);
        return (v == null || v.isBlank() || "null".equals(v)) ? null : LocalDate.parse(v.substring(0, 10));
    }

    private static BigDecimal decimal(JsonNode no, String campo) {
        return no.path(campo).isMissingNode() || no.path(campo).isNull()
                ? null : no.path(campo).decimalValue();
    }

    public Optional<CstEntry> cst(String codigo, LocalDate data) {
        return Optional.ofNullable(csts.get(codigo)).filter(c -> c.vigenteEm(data));
    }

    public Optional<ClassTribEntry> classTrib(String codigo, LocalDate data) {
        return Optional.ofNullable(classificacoes.get(codigo)).filter(c -> c.vigenteEm(data));
    }

    public String provenance() {
        return manifest.describe();
    }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.tables.*' --console=plain`
Expected: 6 testes passando.

- [ ] **Step 6: Commit**

```bash
git add build.gradle src/main/java/br/com/validadorlote/infrastructure/tables src/test/java/br/com/validadorlote/infrastructure/tables
git commit -m "feat(b6): consulta às tabelas oficiais com vigência por data do fato gerador"
```

---

## Task 4: Parse do CRT e dos grupos IBS/CBS por item

**Files:**
- Modify: `src/main/java/br/com/validadorlote/domain/FiscalDocument.java`
- Modify: `src/main/java/br/com/validadorlote/infrastructure/xml/XmlMetadataParser.java`
- Create: `src/main/java/br/com/validadorlote/infrastructure/xml/TaxGroupExtractor.java`
- Test: `src/test/java/br/com/validadorlote/infrastructure/xml/TaxGroupExtractorTest.java`
- Test fixture: `src/test/resources/fixtures/nfe-crt3-sem-ibscbs.xml`

**Interfaces:**
- Consumes: `FiscalDocument` (Task 1).
- Produces:
```java
record FiscalDocument(Path source, String accessKey, String emitterCnpj, String documentNumber,
    LocalDate issueDate, String model, String rootElement, String crt) {}
record ItemTaxGroup(int itemNumber, boolean hasIbsCbsGroup, String cst, String cClassTrib,
    boolean hasReducaoUf, boolean hasReducaoMun, boolean hasReducaoCbs,
    BigDecimal percReducaoUf, BigDecimal percReducaoMun, BigDecimal percReducaoCbs) {}
final class TaxGroupExtractor { List<ItemTaxGroup> extract(Path xml); }
```

- [ ] **Step 1: Criar a fixture do caso dominante**

`src/test/resources/fixtures/nfe-crt3-sem-ibscbs.xml` — copie `nfe-valida.xml` e **remova todo o
bloco `<IBSCBS>...</IBSCBS>`**, mantendo `<CRT>3</CRT>`. É o documento que a SEFAZ rejeitará a
partir de 03/08/2026 e que hoje passa limpo no schema.

Run: `python3 - <<'EOF'
import re, pathlib
base = pathlib.Path('src/test/resources/fixtures/nfe-valida.xml').read_text()
sem = re.sub(r'<IBSCBS>.*?</IBSCBS>', '', base, flags=re.S)
pathlib.Path('src/test/resources/fixtures/nfe-crt3-sem-ibscbs.xml').write_text(sem)
print('IBSCBS presente?', 'IBSCBS' in sem, '| CRT:', re.search(r'<CRT>(\d)</CRT>', sem).group(1))
EOF`
Expected: `IBSCBS presente? False | CRT: 3`

- [ ] **Step 2: Escrever o teste que falha**

```java
package br.com.validadorlote.infrastructure.xml;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TaxGroupExtractorTest {

    private final TaxGroupExtractor extractor = new TaxGroupExtractor();
    private final XmlMetadataParser parser = new XmlMetadataParser();

    private Path fixture(String nome) {
        return Path.of("src/test/resources/fixtures/" + nome);
    }

    @Test
    void extractsCrtFromDocument() {
        var doc = parser.parse(fixture("nfe-valida.xml")).document();
        assertThat(doc.crt()).isEqualTo("3");
    }

    @Test
    void readsIbsCbsGroupOfEachItem() {
        var grupos = extractor.extract(fixture("nfe-valida.xml"));

        assertThat(grupos).singleElement().satisfies(g -> {
            assertThat(g.itemNumber()).isEqualTo(1);
            assertThat(g.hasIbsCbsGroup()).isTrue();
            assertThat(g.cst()).isEqualTo("000");
            assertThat(g.cClassTrib()).isEqualTo("000001");
        });
    }

    @Test
    void detectsItemWithoutTheGroup() {
        // O caso dominante de 03/08: CRT=3 e nenhum grupo IBS/CBS.
        var grupos = extractor.extract(fixture("nfe-crt3-sem-ibscbs.xml"));

        assertThat(grupos).singleElement().satisfies(g -> {
            assertThat(g.hasIbsCbsGroup()).isFalse();
            assertThat(g.cst()).isNull();
            assertThat(g.cClassTrib()).isNull();
        });
    }

    @Test
    void readsReductionSubgroupsWhenPresent() {
        var g = extractor.extract(fixture("nfe-valida.xml")).getFirst();

        // A fixture canônica não tem gRed — os subgrupos precisam sair como ausentes,
        // não como null ambíguo.
        assertThat(g.hasReducaoUf()).isFalse();
        assertThat(g.hasReducaoMun()).isFalse();
        assertThat(g.hasReducaoCbs()).isFalse();
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.xml.TaxGroupExtractorTest' --console=plain`
Expected: falha de compilação.

- [ ] **Step 4: Implementar**

`FiscalDocument.java` — acrescentar `crt` ao fim do record:
```java
public record FiscalDocument(Path source, String accessKey, String emitterCnpj,
        String documentNumber, LocalDate issueDate, String model, String rootElement,
        String crt) {}
```

Em `XmlMetadataParser`, acrescentar `crt` ao conjunto de campos extraídos, seguindo exatamente o
mesmo padrão já usado para `emitterCnpj`: o elemento é `CRT`, o pai imediato é `emit`, e o valor
entra na construção do `FiscalDocument`. Os campos ausentes continuam virando `null`.

`TaxGroupExtractor.java`:
```java
package br.com.validadorlote.infrastructure.xml;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lê o grupo IBS/CBS de cada item. Separado do {@link XmlMetadataParser} porque este extrai
 * identificação do documento, enquanto aqui interessa o conteúdo tributário por item.
 */
public final class TaxGroupExtractor {

    /** Um item e o que ele declarou de IBS/CBS. Campos nulos quando o grupo não existe. */
    public record ItemTaxGroup(int itemNumber, boolean hasIbsCbsGroup, String cst, String cClassTrib,
            boolean hasReducaoUf, boolean hasReducaoMun, boolean hasReducaoCbs,
            BigDecimal percReducaoUf, BigDecimal percReducaoMun, BigDecimal percReducaoCbs) {}

    public List<ItemTaxGroup> extract(Path xml) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (InputStream in = Files.newInputStream(xml)) {
            return read(factory.createXMLStreamReader(in));
        } catch (XMLStreamException | IOException e) {
            throw new UnreadableXmlException("Falha ao ler grupos IBS/CBS: " + xml.getFileName(), e);
        }
    }

    private List<ItemTaxGroup> read(XMLStreamReader r) throws XMLStreamException {
        List<ItemTaxGroup> itens = new ArrayList<>();
        Integer nItem = null;
        boolean emIbsCbs = false, temGrupo = false;
        boolean redUf = false, redMun = false, redCbs = false;
        String cst = null, classTrib = null;
        BigDecimal pUf = null, pMun = null, pCbs = null;
        String contexto = null;

        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                String nome = r.getLocalName();
                switch (nome) {
                    case "det" -> {
                        String n = r.getAttributeValue(null, "nItem");
                        nItem = n == null ? null : parseItem(n);
                        emIbsCbs = temGrupo = redUf = redMun = redCbs = false;
                        cst = classTrib = null;
                        pUf = pMun = pCbs = null;
                    }
                    case "IBSCBS" -> { emIbsCbs = true; temGrupo = true; }
                    case "gIBSUF" -> contexto = "UF";
                    case "gIBSMun" -> contexto = "MUN";
                    case "gCBS" -> contexto = "CBS";
                    case "gRed" -> {
                        if ("UF".equals(contexto)) redUf = true;
                        else if ("MUN".equals(contexto)) redMun = true;
                        else if ("CBS".equals(contexto)) redCbs = true;
                    }
                    case "CST" -> { if (emIbsCbs && cst == null) cst = texto(r); }
                    case "cClassTrib" -> { if (emIbsCbs && classTrib == null) classTrib = texto(r); }
                    case "pRedAliq" -> {
                        BigDecimal v = decimal(texto(r));
                        if ("UF".equals(contexto)) pUf = v;
                        else if ("MUN".equals(contexto)) pMun = v;
                        else if ("CBS".equals(contexto)) pCbs = v;
                    }
                    default -> { }
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if ("IBSCBS".equals(r.getLocalName())) emIbsCbs = false;
                if ("det".equals(r.getLocalName()) && nItem != null) {
                    itens.add(new ItemTaxGroup(nItem, temGrupo, cst, classTrib,
                            redUf, redMun, redCbs, pUf, pMun, pCbs));
                    nItem = null;
                }
            }
        }
        return itens;
    }

    private String texto(XMLStreamReader r) throws XMLStreamException {
        String t = r.getElementText();
        return (t == null || t.isBlank()) ? null : t.trim();
    }

    private Integer parseItem(String v) {
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;   // nItem inválido: o XSD reporta; aqui não derruba a leitura
        }
    }

    private BigDecimal decimal(String v) {
        try {
            return v == null ? null : new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

- [ ] **Step 5: Corrigir os chamadores do FiscalDocument**

Run: `./gradlew compileJava compileTestJava --console=plain` e acrescente o argumento `crt` (ou
`null` nos testes que não o exercitam) onde o compilador apontar.

- [ ] **Step 6: Rodar a suíte inteira**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/br/com/validadorlote src/test/java/br/com/validadorlote src/test/resources/fixtures
git commit -m "feat(b6): parse do CRT e dos grupos IBS/CBS por item"
```

---

## Task 5: Contrato de regra e os três desfechos

**Files:**
- Create: `src/main/java/br/com/validadorlote/infrastructure/rules/RuleOutcome.java`
- Create: `src/main/java/br/com/validadorlote/infrastructure/rules/RuleContext.java`
- Create: `src/main/java/br/com/validadorlote/infrastructure/rules/RejectionRule.java`
- Test: `src/test/java/br/com/validadorlote/infrastructure/rules/RuleOutcomeTest.java`

**Interfaces:**
- Consumes: `Finding` (Task 1), `FiscalTables` (Task 3), `ItemTaxGroup` (Task 4).
- Produces:
```java
sealed interface RuleOutcome {
    record Conforme() implements RuleOutcome {}
    record NaoAplicavel(String motivo) implements RuleOutcome {}
    record NaoAvaliado(String motivo) implements RuleOutcome {}
    record Rejeitado(String rejectionCode, String ruleId, String officialMessage) implements RuleOutcome {}
}
record RuleContext(FiscalDocument document, ItemTaxGroup item, FiscalTables tables, LocalDate operationDate) {}
interface RejectionRule {
    String rejectionCode();
    String ruleId();
    RuleOutcome evaluate(RuleContext ctx);
}
```

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.validadorlote.infrastructure.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleOutcomeTest {

    @Test
    void naoAvaliadoIsDistinctFromConforme() {
        // A distinção é a espinha dorsal da confiança: aprovar sem verificar é mentir.
        RuleOutcome conforme = new RuleOutcome.Conforme();
        RuleOutcome naoAvaliado = new RuleOutcome.NaoAvaliado("cClassTrib fora da base");

        assertThat(conforme).isNotEqualTo(naoAvaliado);
        assertThat(naoAvaliado).isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    @Test
    void naoAplicavelCarriesReason() {
        RuleOutcome r = new RuleOutcome.NaoAplicavel("CRT=1: exigência vigora só em 04/01/2027");
        assertThat(((RuleOutcome.NaoAplicavel) r).motivo()).contains("2027");
    }

    @Test
    void rejeitadoCarriesOfficialIdentity() {
        RuleOutcome r = new RuleOutcome.Rejeitado("1115", "UB12-10",
                "Rejeição: IBS/CBS não informado");
        var rej = (RuleOutcome.Rejeitado) r;
        assertThat(rej.rejectionCode()).isEqualTo("1115");
        assertThat(rej.ruleId()).isEqualTo("UB12-10");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.rules.RuleOutcomeTest' --console=plain`
Expected: falha de compilação.

- [ ] **Step 3: Implementar**

`RuleOutcome.java`:
```java
package br.com.validadorlote.infrastructure.rules;

/**
 * Desfecho de uma verificação. São quatro e não dois de propósito: aprovar o que não foi
 * verificado, ou rejeitar por falta de dado nosso, destrói a confiança no relatório.
 */
public sealed interface RuleOutcome {

    /** Verificado e correto. */
    record Conforme() implements RuleOutcome {}

    /** A regra não vale para este documento (regime ou vigência). Não é aprovação. */
    record NaoAplicavel(String motivo) implements RuleOutcome {}

    /** Faltou dado para julgar — tipicamente base embarcada mais antiga que o documento. */
    record NaoAvaliado(String motivo) implements RuleOutcome {}

    /** A SEFAZ rejeitaria. A mensagem oficial vem da NT e não é reescrita. */
    record Rejeitado(String rejectionCode, String ruleId, String officialMessage)
            implements RuleOutcome {}
}
```

`RuleContext.java`:
```java
package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.time.LocalDate;

/** Tudo que uma regra precisa para julgar um item. */
public record RuleContext(FiscalDocument document, ItemTaxGroup item, FiscalTables tables,
        LocalDate operationDate) {}
```

`RejectionRule.java`:
```java
package br.com.validadorlote.infrastructure.rules;

/** Uma regra da NT que prevê rejeição. Implementações são sem estado e reutilizáveis. */
public interface RejectionRule {

    /** Código oficial da rejeição, ex.: "1115". */
    String rejectionCode();

    /** Identificador da regra na NT, ex.: "UB12-10". */
    String ruleId();

    RuleOutcome evaluate(RuleContext ctx);
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.rules.RuleOutcomeTest' --console=plain`
Expected: 3 testes passando.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/validadorlote/infrastructure/rules src/test/java/br/com/validadorlote/infrastructure/rules
git commit -m "feat(b6): contrato de regra de rejeição com quatro desfechos"
```

---

## Task 6: Regras de documento — 1115 e 1021

**Files:**
- Create: `src/main/java/br/com/validadorlote/infrastructure/rules/GroupRequiredRule.java`
- Create: `src/main/java/br/com/validadorlote/infrastructure/rules/GroupForbiddenRule.java`
- Test: `src/test/java/br/com/validadorlote/infrastructure/rules/DocumentRulesTest.java`

**Interfaces:**
- Consumes: `RejectionRule`, `RuleContext`, `RuleOutcome` (Task 5).
- Produces: `GroupRequiredRule` (1115 / UB12-10) e `GroupForbiddenRule` (1021 / UB13-20).

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentRulesTest {

    private static FiscalTables tables;
    private static final LocalDate VIGENTE = LocalDate.of(2026, 8, 3);
    private static final LocalDate ANTES = LocalDate.of(2026, 8, 2);

    @BeforeAll
    static void load() { tables = FiscalTables.load(); }

    private FiscalDocument doc(String crt, LocalDate data) {
        return new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                data, "55", "NFe", crt);
    }

    private ItemTaxGroup item(boolean temGrupo, String cst) {
        return new ItemTaxGroup(1, temGrupo, cst, temGrupo ? "000001" : null,
                false, false, false, null, null, null);
    }

    // ---- 1115: grupo obrigatório ----

    @Test
    void crt3WithoutGroupOnOrAfterTheDeadlineIsRejected() {
        var out = new GroupRequiredRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(false, null), tables, VIGENTE));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).rejectionCode()).isEqualTo("1115");
    }

    @Test
    void crt3WithoutGroupBeforeTheDeadlineIsNotApplicable() {
        var out = new GroupRequiredRule().evaluate(
                new RuleContext(doc("3", ANTES), item(false, null), tables, ANTES));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void simplesNacionalIsNotApplicableUntil2027() {
        // Dizer "conforme" aqui faria o contador ser surpreendido em janeiro.
        var out = new GroupRequiredRule().evaluate(
                new RuleContext(doc("1", VIGENTE), item(false, null), tables, VIGENTE));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAplicavel.class);
        assertThat(((RuleOutcome.NaoAplicavel) out).motivo()).contains("2027");
    }

    @Test
    void crt3WithGroupIsConforme() {
        var out = new GroupRequiredRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(true, "000"), tables, VIGENTE));

        assertThat(out).isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void absentCrtIsNotEvaluatedNotRejected() {
        // Sem CRT não dá para saber se a exigência vale. Acusar seria chutar.
        var out = new GroupRequiredRule().evaluate(
                new RuleContext(doc(null, VIGENTE), item(false, null), tables, VIGENTE));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    // ---- 1021: grupo proibido ----

    @Test
    void groupInformedWhenCstForbidsIsRejected() {
        // CST 400 (Isenção) tem IndExigeTrib = false: o grupo não deve vir preenchido.
        var out = new GroupForbiddenRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(true, "400"), tables, VIGENTE));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).rejectionCode()).isEqualTo("1021");
    }

    @Test
    void groupInformedWhenCstRequiresIsConforme() {
        var out = new GroupForbiddenRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(true, "000"), tables, VIGENTE));

        assertThat(out).isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void unknownCstIsNotEvaluated() {
        var out = new GroupForbiddenRule().evaluate(
                new RuleContext(doc("3", VIGENTE), item(true, "999"), tables, VIGENTE));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.rules.DocumentRulesTest' --console=plain`
Expected: falha de compilação.

- [ ] **Step 3: Implementar**

`GroupRequiredRule.java`:
```java
package br.com.validadorlote.infrastructure.rules;

import java.time.LocalDate;

/**
 * Rejeição 1115 (UB12-10): o grupo IBS/CBS é obrigatório em cada item.
 *
 * <p>É a regra-mãe da virada: o XSD declara o grupo como opcional, então um documento sem ele
 * passa na validação estrutural e é recusado pela SEFAZ. As datas são escalonadas por regime.
 */
public final class GroupRequiredRule implements RejectionRule {

    /** Regime Normal: exigência em produção. */
    private static final LocalDate VIGENCIA_CRT3 = LocalDate.of(2026, 8, 3);
    /** Simples Nacional, excesso de sublimite e MEI. */
    private static final LocalDate VIGENCIA_SIMPLES = LocalDate.of(2027, 1, 4);

    private static final String MENSAGEM_OFICIAL = "Rejeição: IBS/CBS não informado";

    @Override public String rejectionCode() { return "1115"; }

    @Override public String ruleId() { return "UB12-10"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        String crt = ctx.document().crt();
        if (crt == null || crt.isBlank()) {
            return new RuleOutcome.NaoAvaliado(
                    "CRT do emitente não encontrado no documento: não dá para saber se a "
                    + "exigência de IBS/CBS já vale para ele.");
        }
        LocalDate vigencia = "3".equals(crt) ? VIGENCIA_CRT3 : VIGENCIA_SIMPLES;
        if (ctx.operationDate() == null) {
            return new RuleOutcome.NaoAvaliado("Data de emissão não encontrada no documento.");
        }
        if (ctx.operationDate().isBefore(vigencia)) {
            return new RuleOutcome.NaoAplicavel(String.format(
                    "Para CRT=%s a exigência do grupo IBS/CBS vigora a partir de %s.",
                    crt, vigencia));
        }
        return ctx.item().hasIbsCbsGroup()
                ? new RuleOutcome.Conforme()
                : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), MENSAGEM_OFICIAL);
    }
}
```

`GroupForbiddenRule.java`:
```java
package br.com.validadorlote.infrastructure.rules;

/**
 * Rejeição 1021 (UB13-20): grupo IBS/CBS informado indevidamente.
 *
 * <p>Governada pelo indicador {@code IndExigeTrib} da tabela de CST: quando ele é falso, o CST
 * não admite tributação detalhada e o grupo não deve vir preenchido.
 */
public final class GroupForbiddenRule implements RejectionRule {

    private static final String MENSAGEM_OFICIAL = "Rejeição: Grupo IBS/CBS informado indevidamente";

    @Override public String rejectionCode() { return "1021"; }

    @Override public String ruleId() { return "UB13-20"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        if (!ctx.item().hasIbsCbsGroup()) {
            return new RuleOutcome.NaoAplicavel("Item sem grupo IBS/CBS.");
        }
        String cst = ctx.item().cst();
        if (cst == null) {
            return new RuleOutcome.NaoAvaliado("CST não informado no grupo IBS/CBS do item.");
        }
        return ctx.tables().cst(cst, ctx.operationDate())
                .map(entry -> entry.exigeGrupo()
                        ? (RuleOutcome) new RuleOutcome.Conforme()
                        : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), MENSAGEM_OFICIAL))
                .orElseGet(() -> new RuleOutcome.NaoAvaliado(
                        "CST " + cst + " não consta na base embarcada para a data do documento."));
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.rules.DocumentRulesTest' --console=plain`
Expected: 8 testes passando.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/validadorlote/infrastructure/rules src/test/java/br/com/validadorlote/infrastructure/rules
git commit -m "feat(b6): regras 1115 e 1021 com vigência escalonada por regime"
```

---

## Task 7: Regras de tabela — 1025, 1033, 1074, 1079 e percentuais

**Files:**
- Create: `src/main/java/br/com/validadorlote/infrastructure/rules/ClassTribModelRule.java`
- Create: `src/main/java/br/com/validadorlote/infrastructure/rules/ReductionGroupRule.java`
- Create: `src/main/java/br/com/validadorlote/infrastructure/rules/ReductionPercentageRule.java`
- Test: `src/test/java/br/com/validadorlote/infrastructure/rules/TableRulesTest.java`

**Interfaces:**
- Consumes: `RejectionRule`, `RuleContext`, `RuleOutcome` (Task 5), `FiscalTables` (Task 3).
- Produces:
```java
final class ClassTribModelRule implements RejectionRule {}                      // 1025 / UB14-25
final class ReductionGroupRule implements RejectionRule {
    enum Esfera { UF, MUNICIPIO, CBS }
    ReductionGroupRule(Esfera esfera);                                          // 1033 / 1074 / 1079
}
final class ReductionPercentageRule implements RejectionRule {}                 // divergência de percentual
```

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TableRulesTest {

    private static FiscalTables tables;
    private static final LocalDate DATA = LocalDate.of(2026, 8, 3);

    @BeforeAll
    static void load() { tables = FiscalTables.load(); }

    private RuleContext ctx(String modelo, String cst, String classTrib,
            boolean redUf, boolean redMun, boolean redCbs,
            BigDecimal pUf, BigDecimal pMun, BigDecimal pCbs) {
        var doc = new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                DATA, modelo, "NFe", "3");
        var item = new ItemTaxGroup(1, true, cst, classTrib, redUf, redMun, redCbs, pUf, pMun, pCbs);
        return new RuleContext(doc, item, tables, DATA);
    }

    // ---- 1025: cClassTrib permitida no modelo ----

    @Test
    void classTribAllowedInModelIsConforme() {
        var out = new ClassTribModelRule().evaluate(
                ctx("55", "000", "000001", false, false, false, null, null, null));
        assertThat(out).isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void unknownClassTribIsNotEvaluated() {
        // Código publicado depois da nossa extração: culpa da base, não do emitente.
        var out = new ClassTribModelRule().evaluate(
                ctx("55", "000", "999999", false, false, false, null, null, null));
        assertThat(out).isInstanceOf(RuleOutcome.NaoAvaliado.class);
    }

    // ---- 1033/1074/1079: grupo de redução ----

    @Test
    void cstRequiringReductionWithoutTheGroupIsRejected() {
        // CST 011 tem IndReducaoAliq = true.
        var out = new ReductionGroupRule(ReductionGroupRule.Esfera.UF).evaluate(
                ctx("55", "011", "011001", false, false, false, null, null, null));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).rejectionCode()).isEqualTo("1033");
    }

    @Test
    void eachSphereHasItsOwnRejectionCode() {
        var semGrupo = ctx("55", "011", "011001", false, false, false, null, null, null);

        assertThat(((RuleOutcome.Rejeitado) new ReductionGroupRule(ReductionGroupRule.Esfera.MUNICIPIO)
                .evaluate(semGrupo)).rejectionCode()).isEqualTo("1074");
        assertThat(((RuleOutcome.Rejeitado) new ReductionGroupRule(ReductionGroupRule.Esfera.CBS)
                .evaluate(semGrupo)).rejectionCode()).isEqualTo("1079");
    }

    @Test
    void cstNotRequiringReductionIsNotApplicable() {
        // CST 000 tem IndReducaoAliq = false — ausência do grupo é correta.
        // Este é o teste que protege contra o falso positivo em escala: se alguém trocar o
        // indicador por CST pelo possuiPercentualReducao da Calculadora, este caso quebra.
        var out = new ReductionGroupRule(ReductionGroupRule.Esfera.UF).evaluate(
                ctx("55", "000", "000001", false, false, false, null, null, null));

        assertThat(out).isInstanceOf(RuleOutcome.NaoAplicavel.class);
    }

    @Test
    void cstRequiringReductionWithTheGroupIsConforme() {
        var out = new ReductionGroupRule(ReductionGroupRule.Esfera.UF).evaluate(
                ctx("55", "011", "011001", true, false, false, new BigDecimal("60.0"), null, null));
        assertThat(out).isInstanceOf(RuleOutcome.Conforme.class);
    }

    // ---- percentual divergente ----

    @Test
    void declaredPercentageMatchingTheOfficialIsConforme() {
        var out = new ReductionPercentageRule().evaluate(
                ctx("55", "011", "011001", true, true, true,
                        new BigDecimal("60.0"), new BigDecimal("60.0"), new BigDecimal("60.0")));
        assertThat(out).isInstanceOf(RuleOutcome.Conforme.class);
    }

    @Test
    void declaredPercentageDivergingFromOfficialIsRejected() {
        var out = new ReductionPercentageRule().evaluate(
                ctx("55", "011", "011001", true, true, true,
                        new BigDecimal("40.0"), new BigDecimal("60.0"), new BigDecimal("60.0")));

        assertThat(out).isInstanceOf(RuleOutcome.Rejeitado.class);
        assertThat(((RuleOutcome.Rejeitado) out).officialMessage()).contains("40").contains("60");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.rules.TableRulesTest' --console=plain`
Expected: falha de compilação.

- [ ] **Step 3: Implementar**

`ClassTribModelRule.java`:
```java
package br.com.validadorlote.infrastructure.rules;

/**
 * Rejeição 1025 (UB14-25): a classificação tributária informada não é permitida no modelo do
 * documento. Governada pelos indicadores {@code IndNfe} e {@code IndNfce} da tabela oficial.
 */
public final class ClassTribModelRule implements RejectionRule {

    private static final String MENSAGEM_OFICIAL =
            "Rejeição: cClassTrib do IBS/CBS não permitido neste modelo de DFe";

    @Override public String rejectionCode() { return "1025"; }

    @Override public String ruleId() { return "UB14-25"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        String codigo = ctx.item().cClassTrib();
        if (codigo == null) {
            return new RuleOutcome.NaoAplicavel("Item sem cClassTrib.");
        }
        return ctx.tables().classTrib(codigo, ctx.operationDate())
                .map(entry -> entry.permiteModelo(ctx.document().model())
                        ? (RuleOutcome) new RuleOutcome.Conforme()
                        : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), MENSAGEM_OFICIAL))
                .orElseGet(() -> new RuleOutcome.NaoAvaliado("cClassTrib " + codigo
                        + " não consta na base embarcada para a data do documento."));
    }
}
```

`ReductionGroupRule.java`:
```java
package br.com.validadorlote.infrastructure.rules;

/**
 * Rejeições 1033, 1074 e 1079: grupo de redução de alíquota ausente quando o CST o exige.
 *
 * <p>São três regras espelhadas, uma por esfera. O indicador é {@code IndReducaoAliq} da tabela
 * de <b>CST</b> — verdadeiro em apenas 3 dos 18. Não confundir com {@code possuiPercentualReducao}
 * da Calculadora, que é por classificação tributária e verdadeiro em 60 de 161: usar aquele aqui
 * geraria falso positivo em escala.
 */
public final class ReductionGroupRule implements RejectionRule {

    public enum Esfera {
        UF("1033", "UB26-20", "Estadual"),
        MUNICIPIO("1074", "UB45-20", "Municipal"),
        CBS("1079", "UB64-20", "da CBS");

        private final String codigo;
        private final String regra;
        private final String nome;

        Esfera(String codigo, String regra, String nome) {
            this.codigo = codigo;
            this.regra = regra;
            this.nome = nome;
        }
    }

    private final Esfera esfera;

    public ReductionGroupRule(Esfera esfera) {
        this.esfera = esfera;
    }

    @Override public String rejectionCode() { return esfera.codigo; }

    @Override public String ruleId() { return esfera.regra; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        String cst = ctx.item().cst();
        if (cst == null) {
            return new RuleOutcome.NaoAplicavel("Item sem CST no grupo IBS/CBS.");
        }
        var entry = ctx.tables().cst(cst, ctx.operationDate());
        if (entry.isEmpty()) {
            return new RuleOutcome.NaoAvaliado("CST " + cst + " não consta na base embarcada.");
        }
        if (!entry.get().exigeReducao()) {
            return new RuleOutcome.NaoAplicavel("CST " + cst + " não exige grupo de redução.");
        }
        return informouGrupo(ctx)
                ? new RuleOutcome.Conforme()
                : new RuleOutcome.Rejeitado(rejectionCode(), ruleId(),
                        "Rejeição: Não informado o grupo de redução de alíquota " + esfera.nome);
    }

    private boolean informouGrupo(RuleContext ctx) {
        return switch (esfera) {
            case UF -> ctx.item().hasReducaoUf();
            case MUNICIPIO -> ctx.item().hasReducaoMun();
            case CBS -> ctx.item().hasReducaoCbs();
        };
    }
}
```

`ReductionPercentageRule.java`:
```java
package br.com.validadorlote.infrastructure.rules;

import java.math.BigDecimal;

/**
 * Percentual de redução divergente do oficial. Não é código de rejeição da NT: é conferência
 * possível porque a tabela publica {@code PercRedIbs} e {@code PercRedCbs}, e um percentual
 * errado leva a valor errado, que a SEFAZ recusa.
 */
public final class ReductionPercentageRule implements RejectionRule {

    @Override public String rejectionCode() { return "PERC-RED"; }

    @Override public String ruleId() { return "—"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        String codigo = ctx.item().cClassTrib();
        if (codigo == null) {
            return new RuleOutcome.NaoAplicavel("Item sem cClassTrib.");
        }
        var entry = ctx.tables().classTrib(codigo, ctx.operationDate());
        if (entry.isEmpty()) {
            return new RuleOutcome.NaoAvaliado("cClassTrib " + codigo + " não consta na base.");
        }
        var ct = entry.get();
        RuleOutcome uf = compara("estadual", ctx.item().percReducaoUf(), ct.percRedIbs());
        if (uf != null) return uf;
        RuleOutcome mun = compara("municipal", ctx.item().percReducaoMun(), ct.percRedIbs());
        if (mun != null) return mun;
        RuleOutcome cbs = compara("da CBS", ctx.item().percReducaoCbs(), ct.percRedCbs());
        if (cbs != null) return cbs;
        return new RuleOutcome.Conforme();
    }

    /** Devolve o achado quando diverge; null quando confere ou quando não há o que comparar. */
    private RuleOutcome compara(String esfera, BigDecimal declarado, BigDecimal oficial) {
        if (declarado == null || oficial == null) {
            return null;
        }
        if (declarado.compareTo(oficial) == 0) {
            return null;
        }
        return new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), String.format(
                "Percentual de redução %s declarado como %s; o oficial para esta classificação é %s.",
                esfera, declarado.toPlainString(), oficial.toPlainString()));
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.rules.TableRulesTest' --console=plain`
Expected: 8 testes passando.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/validadorlote/infrastructure/rules src/test/java/br/com/validadorlote/infrastructure/rules
git commit -m "feat(b6): regras 1025, 1033, 1074, 1079 e divergência de percentual"
```

---

## Task 8: Motor de regras com supressão em cascata

**Files:**
- Create: `src/main/java/br/com/validadorlote/infrastructure/rules/RuleEngine.java`
- Test: `src/test/java/br/com/validadorlote/infrastructure/rules/RuleEngineTest.java`

**Interfaces:**
- Consumes: todas as regras (Tasks 6 e 7), `Finding` (Task 1), `TaxGroupExtractor` (Task 4).
- Produces: `final class RuleEngine { RuleEngine(FiscalTables tables); List<Finding> evaluate(FiscalDocument doc, List<ItemTaxGroup> itens); }`

- [ ] **Step 1: Escrever o teste que falha**

```java
package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FindingKind;
import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private static RuleEngine engine;
    private static final LocalDate DATA = LocalDate.of(2026, 8, 3);

    @BeforeAll
    static void setup() { engine = new RuleEngine(FiscalTables.load()); }

    private FiscalDocument doc(String crt) {
        return new FiscalDocument(Path.of("a.xml"), "chave", "14200166000187", "100",
                DATA, "55", "NFe", crt);
    }

    @Test
    void missingGroupProducesExactlyOneFindingPerItem() {
        // Supressão em cascata: sem o grupo, todas as regras de subgrupo daquele item
        // são suprimidas. Sem isso o relatório repete a mesma causa dez vezes.
        var itens = List.of(new ItemTaxGroup(1, false, null, null,
                false, false, false, null, null, null));

        var achados = engine.evaluate(doc("3"), itens);

        assertThat(achados).singleElement().satisfies(f -> {
            assertThat(f.rejectionCode()).isEqualTo("1115");
            assertThat(f.itemNumber()).isEqualTo(1);
        });
    }

    @Test
    void conformeDocumentProducesNoFindings() {
        var itens = List.of(new ItemTaxGroup(1, true, "000", "000001",
                false, false, false, null, null, null));

        assertThat(engine.evaluate(doc("3"), itens)).isEmpty();
    }

    @Test
    void notEvaluatedBecomesItsOwnFindingKind() {
        // CST fora da base: nem aprovado nem rejeitado.
        var itens = List.of(new ItemTaxGroup(1, true, "999", "000001",
                false, false, false, null, null, null));

        var achados = engine.evaluate(doc("3"), itens);

        assertThat(achados).isNotEmpty();
        assertThat(achados).allSatisfy(f ->
                assertThat(f.kind()).isEqualTo(FindingKind.NOT_EVALUATED));
    }

    @Test
    void notApplicableProducesNoFinding() {
        // CRT=1 antes de 2027: a regra não vale, e isso não é achado.
        var itens = List.of(new ItemTaxGroup(1, false, null, null,
                false, false, false, null, null, null));

        var achados = engine.evaluate(doc("1"), itens);

        assertThat(achados).noneSatisfy(f ->
                assertThat(f.rejectionCode()).isEqualTo("1115"));
    }

    @Test
    void eachItemIsEvaluatedIndependently() {
        var itens = List.of(
                new ItemTaxGroup(1, true, "000", "000001", false, false, false, null, null, null),
                new ItemTaxGroup(2, false, null, null, false, false, false, null, null, null));

        var achados = engine.evaluate(doc("3"), itens);

        assertThat(achados).singleElement()
                .satisfies(f -> assertThat(f.itemNumber()).isEqualTo(2));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.rules.RuleEngineTest' --console=plain`
Expected: falha de compilação.

- [ ] **Step 3: Implementar**

```java
package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.domain.FiscalDocument;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor.ItemTaxGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Avalia as regras de rejeição sobre um documento.
 *
 * <p>A ordem não é cosmética: quando o grupo IBS/CBS está ausente, todas as regras que dependem
 * dele são suprimidas naquele item. Sem isso, um documento vazio geraria dezenas de achados
 * repetindo a mesma causa, e o relatório deixaria de ser acionável.
 */
public final class RuleEngine {

    private final FiscalTables tables;
    private final GroupRequiredRule grupoObrigatorio = new GroupRequiredRule();
    private final List<RejectionRule> dependentesDoGrupo = List.of(
            new GroupForbiddenRule(),
            new ClassTribModelRule(),
            new ReductionGroupRule(ReductionGroupRule.Esfera.UF),
            new ReductionGroupRule(ReductionGroupRule.Esfera.MUNICIPIO),
            new ReductionGroupRule(ReductionGroupRule.Esfera.CBS),
            new ReductionPercentageRule());

    public RuleEngine(FiscalTables tables) {
        this.tables = tables;
    }

    public List<Finding> evaluate(FiscalDocument doc, List<ItemTaxGroup> itens) {
        List<Finding> achados = new ArrayList<>();
        for (ItemTaxGroup item : itens) {
            var ctx = new RuleContext(doc, item, tables, doc.issueDate());

            RuleOutcome raiz = grupoObrigatorio.evaluate(ctx);
            adicionar(achados, doc, item, grupoObrigatorio, raiz);
            if (raiz instanceof RuleOutcome.Rejeitado || raiz instanceof RuleOutcome.NaoAvaliado) {
                continue;   // causa-raiz encontrada: o resto do item é sintoma
            }
            for (RejectionRule regra : dependentesDoGrupo) {
                adicionar(achados, doc, item, regra, regra.evaluate(ctx));
            }
        }
        return achados;
    }

    private void adicionar(List<Finding> achados, FiscalDocument doc, ItemTaxGroup item,
            RejectionRule regra, RuleOutcome desfecho) {
        if (desfecho instanceof RuleOutcome.Rejeitado r) {
            achados.add(Finding.rejection(doc.source(), doc.accessKey(), item.itemNumber(),
                    r.rejectionCode(), r.ruleId(), r.officialMessage(), null));
        } else if (desfecho instanceof RuleOutcome.NaoAvaliado n) {
            achados.add(Finding.notEvaluated(doc.source(), doc.accessKey(), item.itemNumber(),
                    n.motivo()));
        }
        // Conforme e NaoAplicavel não geram achado: o primeiro é o esperado, o segundo é
        // ausência de exigência — e nenhum dos dois é problema do usuário.
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.rules.RuleEngineTest' --console=plain`
Expected: 5 testes passando.

- [ ] **Step 5: Rodar a suíte inteira**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/br/com/validadorlote/infrastructure/rules src/test/java/br/com/validadorlote/infrastructure/rules
git commit -m "feat(b6): motor de regras com supressão em cascata por causa-raiz"
```

---

## Task 9: Casos de teste para a validação diferencial

**Files:**
- Create: `src/test/resources/fixtures/rejeicao/*.xml` (seis casos)
- Create: `docs/validacao/casos-diferenciais.md`
- Test: `src/test/java/br/com/validadorlote/infrastructure/rules/RejectionFixturesTest.java`

**Interfaces:**
- Consumes: `RuleEngine` (Task 8), `TaxGroupExtractor` (Task 4).
- Produces: um par de documentos por regra — um que dispara, um que não — e o registro de qual
  resultado esperamos do validador oficial.

- [ ] **Step 1: Gerar os seis casos a partir da fixture canônica**

```bash
python3 - <<'EOF'
import re, pathlib
base = pathlib.Path('src/test/resources/fixtures/nfe-valida.xml').read_text()
d = pathlib.Path('src/test/resources/fixtures/rejeicao'); d.mkdir(parents=True, exist_ok=True)

# 1115 — CRT=3 sem grupo IBS/CBS
(d/'r1115-sem-grupo.xml').write_text(re.sub(r'<IBSCBS>.*?</IBSCBS>', '', base, flags=re.S))

# 1021 — grupo presente com CST que não admite tributação (400 = Isenção)
(d/'r1021-grupo-indevido.xml').write_text(base.replace('<CST>000</CST>', '<CST>400</CST>'))

# 1025 — cClassTrib de outro modelo (000002 é exclusiva de NFSe/DUIMP, não vale em NF-e 55)
(d/'r1025-classtrib-modelo.xml').write_text(base.replace('<cClassTrib>000001</cClassTrib>',
                                                          '<cClassTrib>000002</cClassTrib>'))

# 1033/1074/1079 — CST que exige redução (011) sem os grupos gRed
red = base.replace('<CST>000</CST>', '<CST>011</CST>') \
          .replace('<cClassTrib>000001</cClassTrib>', '<cClassTrib>011001</cClassTrib>')
(d/'r1033-1074-1079-sem-reducao.xml').write_text(red)

print('gerados:', sorted(p.name for p in d.glob('*.xml')))
EOF
```
Expected: quatro arquivos listados (o de redução cobre as três esferas de uma vez).

- [ ] **Step 2: Escrever o teste que amarra cada fixture ao seu código**

```java
package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.Finding;
import br.com.validadorlote.infrastructure.tables.FiscalTables;
import br.com.validadorlote.infrastructure.xml.TaxGroupExtractor;
import br.com.validadorlote.infrastructure.xml.XmlMetadataParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RejectionFixturesTest {

    private static RuleEngine engine;
    private static final XmlMetadataParser parser = new XmlMetadataParser();
    private static final TaxGroupExtractor extractor = new TaxGroupExtractor();

    @BeforeAll
    static void setup() { engine = new RuleEngine(FiscalTables.load()); }

    private List<Finding> avaliar(String nome) {
        Path xml = Path.of("src/test/resources/fixtures/rejeicao/" + nome);
        return engine.evaluate(parser.parse(xml).document(), extractor.extract(xml));
    }

    private List<String> codigos(String nome) {
        return avaliar(nome).stream().map(Finding::rejectionCode).filter(c -> c != null).toList();
    }

    @Test
    void fixture1115FiresOnlyItsOwnCode() {
        assertThat(codigos("r1115-sem-grupo.xml")).containsExactly("1115");
    }

    @Test
    void fixture1021FiresItsCode() {
        assertThat(codigos("r1021-grupo-indevido.xml")).contains("1021");
    }

    @Test
    void fixture1025FiresItsCode() {
        assertThat(codigos("r1025-classtrib-modelo.xml")).contains("1025");
    }

    @Test
    void reductionFixtureFiresAllThreeSpheres() {
        assertThat(codigos("r1033-1074-1079-sem-reducao.xml"))
                .contains("1033", "1074", "1079");
    }

    @Test
    void canonicalValidDocumentFiresNothing() {
        // A barra dura: ausência de falso positivo. Se este teste quebra, não liberamos.
        Path xml = Path.of("src/test/resources/fixtures/nfe-valida.xml");
        assertThat(engine.evaluate(parser.parse(xml).document(), extractor.extract(xml))).isEmpty();
    }
}
```

- [ ] **Step 3: Rodar e ajustar as fixtures até baterem**

Run: `./gradlew test --tests 'br.com.validadorlote.infrastructure.rules.RejectionFixturesTest' --console=plain`

Se um caso não disparar o código esperado, **investigue antes de mexer**: pode ser que o
`cClassTrib` escolhido não tenha a propriedade suposta na tabela real. Confirme consultando a base
embarcada antes de trocar a fixture:
```bash
python3 -c "
import json; d=json.load(open('src/main/resources/tables/cst-cclasstrib.json'))
for c in d:
    if c['cst'] in ('000','011','400'):
        print(c['cst'], 'exigeGrupo=', c['exigeGrupo'], 'exigeReducao=', c['exigeReducao'],
              '| classificações:', [x['codigo'] for x in c['classificacoes']][:5])"
```

- [ ] **Step 4: Registrar o roteiro da validação diferencial**

`docs/validacao/casos-diferenciais.md`:
```markdown
# Validação diferencial contra o validador oficial

O validador de mensagens da SVRS roda o próprio autorizador em modo simulação. Comparar nosso
resultado com o dele é o que transforma "achamos que está certo" em "conferimos contra a fonte".

**Endereço:** https://dfe-portal.svrs.rs.gov.br/NFE/ValidadorNfe

## Critérios de aceite

1. **Nenhum documento aprovado pela SVRS pode ser reprovado por nós.** É a barra dura: falso
   positivo destrói a confiança e não tem volta.
2. **Para as regras que cobrimos, o resultado tem que bater.** Se a SVRS acusa 1115 e nós não, a
   regra está errada.
3. **O que a SVRS acusa fora do nosso escopo** é registrado como cobertura futura, não como falha.

## Casos

| Arquivo | Esperado nosso | Esperado SVRS | Conferido em |
|---|---|---|---|
| `nfe-valida.xml` | nenhum achado | sem rejeição de IBS/CBS | |
| `rejeicao/r1115-sem-grupo.xml` | 1115 | 1115 | |
| `rejeicao/r1021-grupo-indevido.xml` | 1021 | 1021 | |
| `rejeicao/r1025-classtrib-modelo.xml` | 1025 | 1025 | |
| `rejeicao/r1033-1074-1079-sem-reducao.xml` | 1033, 1074, 1079 | idem | |

## Como conferir

O validador exige XML assinado para chegar às regras de negócio. As fixtures têm assinatura
sintética, então a SVRS acusará assinatura inválida — isso é esperado e **não invalida** a
comparação das regras de IBS/CBS, que são avaliadas em separado.

Para cada arquivo: colar o conteúdo, validar, anotar os códigos que a SVRS reportou na seção
"Regras de Negócio" e preencher a coluna "Conferido em" com a data.

## Divergências encontradas

_(preencher durante a execução — cada divergência vira correção antes da release)_
```

- [ ] **Step 5: Commit**

```bash
git add src/test/resources/fixtures/rejeicao src/test/java/br/com/validadorlote/infrastructure/rules/RejectionFixturesTest.java docs/validacao
git commit -m "test(b6): casos de teste por regra e roteiro da validação diferencial"
```

---

## Task 10: Validação diferencial — GATE HUMANO

**Files:**
- Modify: `docs/validacao/casos-diferenciais.md`

Esta task não tem código. É o gate que justifica publicar com cobertura parcial.

- [ ] **Step 1: Rodar cada fixture no validador oficial**

Para cada um dos cinco arquivos da tabela, colar no validador da SVRS, validar, e anotar os
códigos reportados na seção "Regras de Negócio".

- [ ] **Step 2: Preencher a tabela de conferência**

Registrar o que a SVRS reportou e a data. Onde houver divergência, descrever na seção
"Divergências encontradas" com o código esperado, o obtido e a hipótese.

- [ ] **Step 3: Corrigir as divergências**

Cada divergência é correção antes da release. **Falso positivo nosso é bloqueador**: se a SVRS
aprova e nós reprovamos, a regra vai para "não avaliado" até ser entendida.

- [ ] **Step 4: PARE E REPORTE**

Apresentar ao dono do projeto: a tabela preenchida, as divergências e o que foi corrigido. A
liberação para o fechamento do bloco é decisão dele.

---

## Task 11: Fechamento do bloco

- [ ] **Step 1: Suíte completa**

Run: `./gradlew clean test --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Relatório do bloco e validação do usuário**

Apresentar: commits, contagem de testes, resultado da validação diferencial, decisões registradas
e pendências. **Aguardar liberação antes do push.**

- [ ] **Step 3: Push e PR (após liberação)**

```bash
git push -u origin bloco/6-camada-rejeicao
gh pr create --title "B6: camada de previsão de rejeição (IBS/CBS)" --body "$(cat <<'EOF'
Camada de previsão de rejeição, conforme a spec
`docs/superpowers/specs/2026-07-27-camada-rejeicao-design.md`.

## Entregas

- Domínio com quatro desfechos por verificação, incluindo **não avaliado** — que existe para
  nunca acusar o usuário de uma limitação da nossa base
- Ingestão da tabela oficial CST × cClassTrib da SVRS, com falha ruidosa se o formato mudar
- Consulta com vigência pela data do fato gerador, nunca pela data corrente
- Seis rejeições: 1115, 1021, 1025, 1033, 1074, 1079, mais divergência de percentual
- Motor com supressão em cascata: uma causa-raiz por item, não a árvore de sintomas

## Validação

Conferido contra o validador oficial da SVRS (ver `docs/validacao/casos-diferenciais.md`).
Critério de aceite: nenhum documento aprovado pela SVRS é reprovado por nós.

## Escopo

Cobre 6 dos 163 códigos de rejeição do grupo UB. A honestidade sobre isso é parte do produto:
a exibição em camadas declara o que foi e o que não foi verificado.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
gh pr checks --watch
```

- [ ] **Step 4: Merge**

```bash
gh pr merge --merge --delete-branch
git checkout main && git pull
```

---

## Notas de execução

- **Antes da Task 1:** `git checkout main && git pull && git checkout -b bloco/6-camada-rejeicao`
- **A Task 2 acessa a rede** (portal da SVRS). É a única. Se a página estiver fora do ar, a task
  falha ruidosamente — não improvise fonte alternativa sem registrar decisão.
- **Ordem de dependência:** Task 1 → 3 e 4 (paralelas em tese, sequenciais na prática) → 5 → 6 e 7
  → 8 → 9 → 10.
- **A regra que mais importa é a 1115.** Se algo tiver que ser cortado por tempo, corta-se o
  percentual (Task 7), nunca ela.
- **O que este plano NÃO integra, de propósito.** Ao fim dele o motor de regras está pronto e
  testado, mas **nada o chama ainda**: o `ValidateBatchUseCase` não existe (era o bloco 3 do plano
  original, nunca executado). A ligação do motor ao lote, os contadores dos três desfechos no
  `BatchReport` e a exibição em camadas pertencem ao bloco seguinte. Isso é sequenciamento, não
  esquecimento — mas quem executar este plano precisa saber que o produto ainda não roda a camada
  de ponta a ponta ao final dele.
- **Pendências que seguem abertas:** D-012 (fonte da Calculadora, só relevante na camada de
  valores), vínculo NCM × cClassTrib (viável com os anexos da tabela, fora deste corte), aviso de
  base desatualizada (§6 da spec, pertence à camada de apresentação), e as demais 157 rejeições do
  grupo UB.
