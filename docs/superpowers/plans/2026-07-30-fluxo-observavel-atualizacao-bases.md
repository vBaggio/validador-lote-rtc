# Fluxo observável de atualização de bases — Plano de implementação

> **Para agentes implementadores:** SUB-SKILL OBRIGATÓRIO: use
> `superpowers:subagent-driven-development` (recomendado) ou `superpowers:executing-plans` para
> executar este plano task a task. Os passos usam checkboxes (`- [ ]`) para acompanhamento.

**Objetivo:** consultar schemas e tabelas em segundo plano, preparar candidatas sem alterar a base
ativa, comunicar todos os estados no rodapé e em um diálogo adaptável e ativar as candidatas
válidas somente após confirmação do usuário.

**Arquitetura:** a infraestrutura separa aquisição/preparação de ativação e publica eventos
tipados; `ExternalSourcesUseCase` agrega esses eventos em um snapshot imutável observado pelo
presenter. Rodapé e diálogo Swing apenas renderizam o mesmo snapshot. A consulta pode coexistir com
a validação de documentos, mas confirmação e ativação aguardam o término da validação.

**Stack:** Java 21, Swing, FlatLaf/Roboto, JUnit 5, AssertJ, Gradle Wrapper, `java.net.http`.

## Restrições globais

- Dependências: `presentation → application → {domain, infrastructure}`;
  `infrastructure → domain`; `domain → nada`.
- `javax.swing` e `java.awt` somente em `presentation/`.
- Nenhum XML, chave, CNPJ ou telemetria sai da máquina.
- A base embarcada mantém instalação e primeiro boot offline; o instalador não baixa artefatos.
- Julgamento fiscal continua vindo exclusivamente dos artefatos aprovados.
- Consulta nunca muda a referência ativa; somente uma confirmação explícita permite ativação.
- Uma sessão usa um único conjunto de engines; bases ativadas entram em uso após reinício.
- Sucesso parcial ativa todas as candidatas válidas com uma confirmação global; fonte com falha
  conserva a base anterior.
- Código em inglês; mensagens e documentação em pt-BR.
- Um commit semântico por task, sem `git push`; brief, revisão independente e ledger seguem
  `docs/workflow.md`.
- O commit local `d399af9` é um protótipo visual não publicado. No fechamento, seu conteúdo deve
  ser incorporado à Task 40 e o histórico local deve ficar com um commit semântico da UI, sem
  preservar uma revisão intermediária defeituosa.

---

## Escopo e decisões já aprovadas

1. Depois de a janela principal ficar visível, o app consulta as duas fontes ativas:
   `NFE_SCHEMAS` e `FISCAL_TABLES`.
2. O rodapé mostra spinner e feedback visível durante a consulta.
3. A política de rede usa timeout curto e duas tentativas totais apenas para timeout, falha de
   conexão e HTTP `502`, `503` ou `504`.
4. TLS, HTTP `4xx`, origem não permitida, excesso de tamanho e conteúdo inválido não são
   retentados.
5. Uma candidata é baixada, normalizada/extraída e validada em staging, mas `current` permanece
   apontando para a base anterior.
6. Se houver candidata e nenhuma validação estiver ativa, o aplicativo oferece uma única
   confirmação. Se houver validação, espera seu término/cancelamento.
7. A própria tela **Atualização de bases** mostra a aplicação. Enquanto aplica, é
   application-modal e não pode ser fechada por `X`, `Esc` ou `Alt+F4`.
8. Se uma fonte falhar e outra tiver candidata, o app atualiza a candidata válida; no próximo boot
   consulta novamente a fonte com falha.
9. Depois da ativação, o app pede encerramento/reinício posterior. Relançamento automático e hot
   reload dos engines ficam fora do escopo.
10. A Calculadora permanece inventariada apenas na documentação da v1 e não ocupa um card de
    atualização no v0.

## Fora do escopo

- executar a Calculadora RFB ou validar valores tributários;
- aceitar ACBr como autoridade silenciosa;
- hospedar canal próprio, assinar manifests ou criar serviço externo;
- habilitar família de schema posterior à `010e` sem homologação própria;
- validar eventos, serviços ou roots além de `NFe`, `nfeProc` e `enviNFe`;
- relançar automaticamente o executável;
- restaurar CSV;
- corrigir drag-and-drop do Windows e zebramento do grid — são débitos da área de documentos.

## Mapa de arquivos

### Contrato e persistência

- Criar `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateCandidate.java`:
  metadados da versão já preparada e ainda inativa.
- Criar `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactCheckResult.java`:
  resultado `UP_TO_DATE` ou `UPDATE_AVAILABLE`.
- Criar `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactFailureKind.java`:
  categorias estáveis de falha.
- Criar `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateException.java`:
  falha tipada e indicação de retentativa.
- Criar `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactRetryPolicy.java`:
  duas tentativas e atraso injetável fora da EDT.
- Modificar `ArtifactUpdateAction`, `ArtifactUpdateEvent`, `ArtifactUpdateCoordinator` e
  `ArtifactUpdateStateStore`: novo ciclo `check → candidate → apply`.

### Aquisição e armazenamento

- Modificar `SchemaArtifactStore` e `FiscalTableArtifactStore`: `prepare(...)` valida e grava uma
  versão inativa; `activate(version)` troca `current` atomicamente.
- Modificar `SvrsSchemaUpdater` e `SvrsTableUpdater`: `check()` prepara candidata;
  `apply(candidate)` ativa.
- Modificar `SafeHttpsClient`: timeout de conexão/requisição e falhas tipadas.
- Modificar `App`: montar ações e política novas sem acrescentar serviço ou dependência.

### Estado da aplicação

- Criar `src/main/java/br/com/validadorlote/application/ExternalSourcePhase.java`.
- Criar `src/main/java/br/com/validadorlote/application/ExternalSourcesPhase.java`.
- Criar `src/main/java/br/com/validadorlote/application/ExternalSourceState.java`.
- Criar `src/main/java/br/com/validadorlote/application/ExternalSourcesSnapshot.java`.
- Modificar `ExternalSourcesUseCase`: única fonte observável de estado.
- Remover `ExternalSourceStatus` quando todos os consumidores migrarem.

### Apresentação

- Modificar `MainPresenter` e `MainView`: gate de validação, confirmação, abertura do diálogo e
  restart required.
- Criar `src/main/java/br/com/validadorlote/presentation/swing/ExternalSourcesStatusBar.java`:
  rodapé compartilhado.
- Criar `src/main/java/br/com/validadorlote/presentation/swing/LoadingSpinner.java`: animação por
  `javax.swing.Timer`.
- Criar `src/main/java/br/com/validadorlote/presentation/swing/ExternalSourcesPanel.java`: conteúdo
  testável dos cards e ações, sem depender de uma janela nativa.
- Modificar `OutlineIcon`: ícones vetoriais de banco/base, aviso e retry; nenhum glifo Unicode.
- Reescrever `ExternalSourcesDialog`: cards, rolagem, modalidade e bloqueio de fechamento.
- Modificar `MainFrame`: compor status bar e encaminhar snapshot/diálogos.

---

### Task 37 — Preparação e ativação separadas

**Arquivos:**

- Criar: `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateCandidate.java`
- Criar: `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactCheckResult.java`
- Modificar: `src/main/java/br/com/validadorlote/infrastructure/xml/SchemaArtifactStore.java`
- Modificar: `src/main/java/br/com/validadorlote/infrastructure/xml/SvrsSchemaUpdater.java`
- Modificar: `src/main/java/br/com/validadorlote/infrastructure/tables/FiscalTableArtifactStore.java`
- Modificar: `src/main/java/br/com/validadorlote/infrastructure/tables/SvrsTableUpdater.java`
- Testar: `src/test/java/br/com/validadorlote/infrastructure/xml/SchemaArtifactStoreTest.java`
- Testar: `src/test/java/br/com/validadorlote/infrastructure/xml/SvrsSchemaUpdaterTest.java`
- Testar: `src/test/java/br/com/validadorlote/infrastructure/tables/FiscalTableArtifactStoreTest.java`
- Testar: `src/test/java/br/com/validadorlote/infrastructure/tables/SvrsTableUpdaterTest.java`

**Interfaces:**

- Produz:

```java
public record ArtifactUpdateCandidate(
        ArtifactId artifact,
        String version,
        String sourceUrl,
        Instant publishedAt,
        String sha256,
        String detail) {}

public record ArtifactCheckResult(Status status, ArtifactUpdateCandidate candidate, String detail) {
    public enum Status { UP_TO_DATE, UPDATE_AVAILABLE }
    public static ArtifactCheckResult upToDate(String detail);
    public static ArtifactCheckResult available(ArtifactUpdateCandidate candidate, String detail);
}
```

- Produz em `SchemaArtifactStore`:

```java
ArtifactManifest prepare(
        Path candidate,
        String version,
        String sourceUrl,
        Instant publishedAt);
ArtifactManifest prepare(
        Path candidate,
        String version,
        String discoveryUrl,
        String sourceUrl,
        Instant publishedAt);
ArtifactManifest activate(String version);
```

- Produz em `FiscalTableArtifactStore`:

```java
ArtifactManifest prepare(
        byte[] candidate,
        String version,
        String sourceUrl,
        Instant publishedAt);
ArtifactManifest activate(String version);
```

- Produz em ambos os updaters:

```java
ArtifactCheckResult check();
ArtifactManifest apply(ArtifactUpdateCandidate candidate);
```

- Regra: `prepare` grava uma versão íntegra em `versions/<version>`, mas não escreve `current`.
  `activate` relê manifesto/hash/formato, então troca somente `current` por movimento atômico.

- [ ] **Passo 1: escrever testes vermelhos dos stores**

Adicionar em `SchemaArtifactStoreTest`:

```java
@Test
void prepareKeepsCurrentAndActivatePublishesThePreparedSchemas() {
    SchemaArtifactStore store = new SchemaArtifactStore(temp);
    Path candidate = copyEmbedded("candidate");
    Instant publishedAt = Instant.parse("2026-07-30T12:00:00Z");
    ArtifactManifest prepared = store.prepare(candidate, "candidate-v2",
            "https://dfe-portal.svrs.rs.gov.br/NFe/Documentos",
            "https://dfe-portal.svrs.rs.gov.br/NFE/DownloadArquivoEstatico?Arquivo=x.zip",
            publishedAt);

    assertThat(store.activeManifestOrNull()).isNull();
    assertThat(prepared.version()).isEqualTo("candidate-v2");

    store.activate("candidate-v2");

    assertThat(store.activeManifestOrNull().version()).isEqualTo("candidate-v2");
}

@Test
void activateRejectsPreparedSchemasChangedAfterValidation() throws IOException {
    SchemaArtifactStore store = new SchemaArtifactStore(temp);
    Path candidate = copyEmbedded("candidate");
    store.prepare(candidate, "candidate-v2", "https://dfe-portal.svrs.rs.gov.br/x",
            Instant.parse("2026-07-30T12:00:00Z"));
    Files.writeString(temp.resolve("artifacts/NFE_SCHEMAS/versions/candidate-v2/nota.xsd"),
            "<corrompido/>");

    assertThatThrownBy(() -> store.activate("candidate-v2"))
            .isInstanceOf(IllegalStateException.class);
    assertThat(store.activeManifestOrNull()).isNull();
}
```

Adicionar em `FiscalTableArtifactStoreTest`, usando o JSON válido já carregado pelos testes:

```java
@Test
void prepareKeepsCurrentAndActivatePublishesThePreparedTable() {
    FiscalTableArtifactStore store = new FiscalTableArtifactStore(temp);
    byte[] candidate = embedded();
    ArtifactManifest prepared = store.prepare(candidate, "candidate-v2",
            "https://dfe-portal.svrs.rs.gov.br/DFE/TabelaClassificacaoTributaria",
            Instant.parse("2026-07-30T12:00:00Z"));

    assertThat(store.activeManifestOrNull()).isNull();
    assertThat(prepared.version()).isEqualTo("candidate-v2");

    store.activate("candidate-v2");

    assertThat(store.activeManifestOrNull().version()).isEqualTo("candidate-v2");
}

@Test
void activateRejectsPreparedTableChangedAfterValidation() throws IOException {
    FiscalTableArtifactStore store = new FiscalTableArtifactStore(temp);
    store.prepare(embedded(), "candidate-v2", "https://dfe-portal.svrs.rs.gov.br/x",
            Instant.parse("2026-07-30T12:00:00Z"));
    Files.writeString(temp.resolve(
            "artifacts/FISCAL_TABLES/versions/candidate-v2/cst-cclasstrib.json"), "{}");

    assertThatThrownBy(() -> store.activate("candidate-v2"))
            .isInstanceOf(IllegalStateException.class);
    assertThat(store.activeManifestOrNull()).isNull();
}
```

- [ ] **Passo 2: confirmar a falha**

Executar:

```bash
./gradlew test --tests '*SchemaArtifactStoreTest' --tests '*FiscalTableArtifactStoreTest' --console=plain
```

Esperado: falha de compilação porque `prepare` e `activate` ainda não existem.

- [ ] **Passo 3: implementar `prepare` e `activate` nos stores**

Extrair a lógica atual de `install` sem reduzir nenhuma guarda:

```java
public ArtifactManifest install(Path candidate, String version, String discoveryUrl,
        String sourceUrl, Instant publishedAt) {
    ArtifactManifest prepared =
            prepare(candidate, version, discoveryUrl, sourceUrl, publishedAt);
    return activate(prepared.version());
}

public ArtifactManifest install(byte[] candidate, String version, String sourceUrl,
        Instant publishedAt) {
    ArtifactManifest prepared = prepare(candidate, version, sourceUrl, publishedAt);
    return activate(prepared.version());
}
```

Em `activate`, resolver `versions/<version>` de forma confinada, recusar symlink, reler manifesto,
recalcular hash, recompilar XSD/recarregar tabela e só então chamar `replaceCurrent(version)`.
Quando a versão preparada já existir com o mesmo manifesto/hash, reutilizá-la; divergência de
conteúdo com o mesmo nome deve falhar.

- [ ] **Passo 4: executar os testes dos stores**

```bash
./gradlew test --tests '*SchemaArtifactStoreTest' --tests '*FiscalTableArtifactStoreTest' --console=plain
```

Esperado: PASS.

- [ ] **Passo 5: escrever testes vermelhos dos updaters**

Cobrir consulta sem ativação e ativação explícita:

```java
@Test
void checkStagesNewReleaseWithoutChangingTheActiveVersion() {
    ArtifactCheckResult result = updater.check();

    assertThat(result.status()).isEqualTo(ArtifactCheckResult.Status.UPDATE_AVAILABLE);
    assertThat(result.candidate().version()).isEqualTo(expectedVersion);
    assertThat(store.activeManifestOrNull()).isNull();
}

@Test
void applyActivatesExactlyTheCandidateReturnedByCheck() {
    ArtifactUpdateCandidate candidate = updater.check().candidate();

    updater.apply(candidate);

    assertThat(store.activeManifestOrNull().version()).isEqualTo(candidate.version());
}
```

Manter os controles já existentes de catálogo anterior, ZIP vazio/hostil, tabela idêntica e
regressão de cobertura.

- [ ] **Passo 6: confirmar a falha**

```bash
./gradlew test --tests '*SvrsSchemaUpdaterTest' --tests '*SvrsTableUpdaterTest' --console=plain
```

Esperado: falha de compilação porque `check()` e `apply(...)` ainda não existem.

- [ ] **Passo 7: implementar candidatos nos updaters**

`check()` deve baixar e validar por `store.prepare`, retornar `UP_TO_DATE` quando não houver versão
mais nova e construir `ArtifactUpdateCandidate` a partir do manifesto preparado. `apply(...)`
deve recusar `candidate.artifact()` incorreto e chamar `store.activate(candidate.version())`.
Manter `updateIfNew()` apenas como adaptador temporário para o coordenador atual:

```java
public ArtifactUpdateResult updateIfNew() {
    ArtifactCheckResult checked = check();
    if (checked.status() == ArtifactCheckResult.Status.UP_TO_DATE) {
        return ArtifactUpdateResult.unchanged(checked.detail());
    }
    apply(checked.candidate());
    return ArtifactUpdateResult.updated(checked.detail());
}
```

- [ ] **Passo 8: executar testes focados e suíte**

```bash
./gradlew test --tests '*SchemaArtifactStoreTest' \
  --tests '*FiscalTableArtifactStoreTest' \
  --tests '*SvrsSchemaUpdaterTest' \
  --tests '*SvrsTableUpdaterTest' --console=plain
./gradlew test --console=plain
```

Esperado: PASS em ambos os comandos.

- [ ] **Passo 9: criar brief, relatório, revisão e commit da Task 37**

Seguir `docs/workflow.md`. Depois do PASS/PASS independente:

```bash
git add src/main/java/br/com/validadorlote/infrastructure \
  src/test/java/br/com/validadorlote/infrastructure \
  .superpowers/sdd/progress.md .superpowers/sdd/CURRENT.md
git commit -m "feat(b6): separa preparo e ativação das bases"
```

---

### Task 38 — Falhas tipadas, retentativa e coordenador observável

**Arquivos:**

- Criar: `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactFailureKind.java`
- Criar: `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateException.java`
- Criar: `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactRetryPolicy.java`
- Modificar: `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateAction.java`
- Modificar: `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateEvent.java`
- Modificar: `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateCoordinator.java`
- Modificar: `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateStateStore.java`
- Modificar: `src/main/java/br/com/validadorlote/infrastructure/tables/SafeHttpsClient.java`
- Modificar: `src/main/java/br/com/validadorlote/App.java`
- Remover após migração: `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateResult.java`
- Testar: `src/test/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateCoordinatorTest.java`
- Testar: `src/test/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateStateStoreTest.java`
- Testar: `src/test/java/br/com/validadorlote/infrastructure/tables/SafeHttpsClientTest.java`
- Testar: `src/test/java/br/com/validadorlote/AppTest.java`

**Interfaces:**

- Consome: `ArtifactCheckResult` e `ArtifactUpdateCandidate` da Task 37.
- Produz:

```java
public interface ArtifactUpdateAction {
    ArtifactId artifact();
    String channelId();
    ArtifactCheckResult check();
    ArtifactManifest apply(ArtifactUpdateCandidate candidate);
}

public enum ArtifactFailureKind {
    CONNECTION, SECURE_CONNECTION, TEMPORARY_HTTP, REJECTED_HTTP,
    INVALID_CONTENT, LOCAL_STORAGE, INTERRUPTED, UNKNOWN
}

public final class ArtifactUpdateException extends RuntimeException {
    public ArtifactFailureKind kind();
    public boolean retryable();
}

public final class ArtifactRetryPolicy {
    public ArtifactRetryPolicy(
            int maxAttempts,
            Duration delay,
            Consumer<Duration> sleeper);
    public static ArtifactRetryPolicy production();
    public <T> T execute(Supplier<T> operation);
}

public record ArtifactUpdateEvent(
        ArtifactId artifact,
        Status status,
        Instant at,
        ArtifactUpdateCandidate candidate,
        ArtifactFailureKind failureKind,
        String detail) {
    public enum Status {
        CHECKING, UP_TO_DATE, UPDATE_AVAILABLE, APPLYING, APPLIED, FAILED
    }
}
```

- `ArtifactUpdateCoordinator` conserva:

```java
void checkAfterBoot();
boolean checkNow();
boolean applyAvailable();
boolean isRunning();
void addListener(Consumer<ArtifactUpdateEvent> listener);
void addCompletionListener(Runnable listener);
ArtifactUpdateStateStore.State state(ArtifactId artifact);
```

- `ArtifactUpdateStateStore`:

```java
State read(ArtifactId artifact, String expectedChannelId);
void write(String channelId, ArtifactUpdateEvent event);
```

- [ ] **Passo 1: escrever testes vermelhos de falha HTTP**

Adicionar em `SafeHttpsClientTest`:

```java
@Test
void classifiesTransientServerFailureAsRetryable() {
    SafeHttpsClient client = client((uri, timeout) ->
            response(503, uri, Map.of(), ""));

    assertThatThrownBy(() -> client.getBytes(SVRS))
            .isInstanceOfSatisfying(ArtifactUpdateException.class, failure -> {
                assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.TEMPORARY_HTTP);
                assertThat(failure.retryable()).isTrue();
            });
}

@Test
void classifiesTlsAndClientErrorsAsNonRetryable() {
    SafeHttpsClient tls = client((uri, timeout) -> {
        throw new SSLHandshakeException("certificate");
    });
    assertThatThrownBy(() -> tls.getBytes(SVRS))
            .isInstanceOfSatisfying(ArtifactUpdateException.class, failure -> {
                assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.SECURE_CONNECTION);
                assertThat(failure.retryable()).isFalse();
            });

    SafeHttpsClient missing = client((uri, timeout) ->
            response(404, uri, Map.of(), ""));
    assertThatThrownBy(() -> missing.getBytes(SVRS))
            .isInstanceOfSatisfying(ArtifactUpdateException.class, failure -> {
                assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.REJECTED_HTTP);
                assertThat(failure.retryable()).isFalse();
            });
}
```

- [ ] **Passo 2: confirmar a falha**

```bash
./gradlew test --tests '*SafeHttpsClientTest' --console=plain
```

Esperado: falha de compilação pelos tipos novos.

- [ ] **Passo 3: implementar falhas tipadas e timeouts**

Criar `ArtifactUpdateException` com fábricas estáticas para cada categoria. Em
`SafeHttpsClient.JdkTransport`, configurar:

```java
HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
```

Usar timeout de requisição de 10 segundos. Mapear `SSLHandshakeException`/`SSLException` para
`SECURE_CONNECTION`, `HttpTimeoutException` para `CONNECTION`, `502/503/504` para
`TEMPORARY_HTTP` e demais não-2xx para `REJECTED_HTTP`. Preservar as mensagens seguras, allowlist,
limites e redirects atuais.

- [ ] **Passo 4: executar o teste HTTP**

```bash
./gradlew test --tests '*SafeHttpsClientTest' --console=plain
```

Esperado: PASS.

- [ ] **Passo 5: escrever testes vermelhos do coordenador**

Cobrir retentativa, parcial, aplicação e exclusão mútua:

```java
@Test
void retriesOneTransientFailureButDoesNotRetryInvalidContent() {
    transientAction.failOnceThenReturn(upToDate);
    coordinator.checkNow();
    assertThat(transientAction.checkCalls()).isEqualTo(2);

    invalidAction.alwaysFails(ArtifactFailureKind.INVALID_CONTENT, false);
    coordinator.checkNow();
    assertThat(invalidAction.checkCalls()).isEqualTo(1);
}

@Test
void partialCheckKeepsCandidateAndApplyContinuesAfterAnotherSourceFailed() {
    coordinator.checkNow();
    assertThat(events).extracting(ArtifactUpdateEvent::status)
            .contains(ArtifactUpdateEvent.Status.UPDATE_AVAILABLE,
                    ArtifactUpdateEvent.Status.FAILED);

    coordinator.applyAvailable();

    assertThat(successfulAction.applyCalls()).isEqualTo(1);
    assertThat(failingAction.applyCalls()).isZero();
}
```

Adicionar também:

```java
@Test
void rejectsCheckAndApplyWhileAnotherOperationIsRunning() {
    assertThat(coordinator.checkNow()).isTrue();
    assertThat(coordinator.checkNow()).isFalse();
    assertThat(coordinator.applyAvailable()).isFalse();
}

@Test
void interruptedRetryStopsImmediatelyAndPreservesTheFlag() {
    Thread.currentThread().interrupt();
    try {
        assertThatThrownBy(() -> retryPolicy.execute(alwaysTransientFailure))
                .isInstanceOf(ArtifactUpdateException.class)
                .satisfies(failure -> assertThat(Thread.currentThread().isInterrupted()).isTrue());
    } finally {
        Thread.interrupted();
    }
}
```

- [ ] **Passo 6: escrever testes vermelhos da persistência e migração**

```java
@Test
void ignoresLegacyFailureFromAnotherChannelAndMakesItDueNow() {
    writeLegacyState("NFE_SCHEMAS", "FAILED", "Não foi possível consultar a fonte HTTPS");

    assertThat(store.read(ArtifactId.NFE_SCHEMAS, "svrs-schemas-v1")).isNull();
}

@Test
void failedAndAvailableStatesAreDueOnNextBootButSuccessWaitsTheInterval() {
    assertThat(isDue(savedFailed)).isTrue();
    assertThat(isDue(savedAvailable)).isTrue();
    assertThat(isDue(savedUpToDateBefore24Hours)).isFalse();
}
```

O novo `State` deve ter a assinatura:

```java
public record State(
        String channelId,
        Instant lastAttemptAt,
        Instant lastSuccessfulCheckAt,
        ArtifactUpdateEvent.Status result,
        String detail,
        ArtifactFailureKind failureKind,
        String candidateVersion) {}
```

- [ ] **Passo 7: confirmar as falhas**

```bash
./gradlew test --tests '*ArtifactUpdateCoordinatorTest' \
  --tests '*ArtifactUpdateStateStoreTest' --console=plain
```

Esperado: falha de compilação/asserção com o contrato antigo.

- [ ] **Passo 8: implementar política, coordenador e store**

`ArtifactRetryPolicy` deve receber `maxAttempts`, `Duration delay` e
`Consumer<Duration> sleeper`; produção usa duas tentativas, 300 ms e `Thread.sleep`, testes usam
no-op. O coordenador guarda as candidatas numa `ConcurrentHashMap<ArtifactId,
ArtifactUpdateCandidate>`, consulta ações sequencialmente e continua após falha individual.

Persistir `channelId` com cada evento terminal. `state(artifact)` deve chamar
`store.read(artifact, action.channelId())`; canal ausente/divergente retorna `null`. A agenda de
24 horas vale somente para `UP_TO_DATE` e `APPLIED`; `FAILED` e `UPDATE_AVAILABLE` são elegíveis no
próximo boot.

Durante `applyAvailable()`, publicar `APPLYING`/`APPLIED` ou `FAILED` por candidata e continuar para
as demais. Remover a candidata somente após `APPLIED`; se a ativação falhar, conservar o objeto
para uma nova consulta completa, não para repetição cega da ativação.

- [ ] **Passo 9: migrar ações e wiring**

Em `App`, adaptar schemas e tabelas a `ArtifactUpdateAction`, com IDs estáveis:

```java
"svrs-schemas-documents-v1"
"svrs-fiscal-table-v1"
```

Usar `ArtifactRetryPolicy.production()`. Remover o adaptador `Supplier<ArtifactUpdateResult>` e,
quando não houver mais referência, remover `ArtifactUpdateResult` e os `updateIfNew()` temporários.

- [ ] **Passo 10: executar testes focados e suíte**

```bash
./gradlew test --tests '*ArtifactUpdateCoordinatorTest' \
  --tests '*ArtifactUpdateStateStoreTest' \
  --tests '*SafeHttpsClientTest' \
  --tests '*AppTest' --console=plain
./gradlew test --console=plain
```

Esperado: PASS.

- [ ] **Passo 11: criar brief, relatório, revisão e commit da Task 38**

Depois do fix loop e PASS/PASS:

```bash
git add src/main/java/br/com/validadorlote/infrastructure \
  src/main/java/br/com/validadorlote/App.java \
  src/test/java/br/com/validadorlote/infrastructure \
  src/test/java/br/com/validadorlote/AppTest.java \
  .superpowers/sdd/progress.md .superpowers/sdd/CURRENT.md
git commit -m "feat(b6): coordena consulta e ativação resilientes"
```

---

### Task 39 — Snapshot único e gate da validação

**Arquivos:**

- Criar: `src/main/java/br/com/validadorlote/application/ExternalSourcePhase.java`
- Criar: `src/main/java/br/com/validadorlote/application/ExternalSourcesPhase.java`
- Criar: `src/main/java/br/com/validadorlote/application/ExternalSourceState.java`
- Criar: `src/main/java/br/com/validadorlote/application/ExternalSourcesSnapshot.java`
- Modificar: `src/main/java/br/com/validadorlote/application/ExternalSourcesUseCase.java`
- Remover: `src/main/java/br/com/validadorlote/application/ExternalSourceStatus.java`
- Modificar: `src/main/java/br/com/validadorlote/presentation/MainPresenter.java`
- Modificar: `src/main/java/br/com/validadorlote/presentation/MainView.java`
- Testar: `src/test/java/br/com/validadorlote/application/ExternalSourcesUseCaseTest.java`
- Testar: `src/test/java/br/com/validadorlote/presentation/MainPresenterTest.java`

**Interfaces:**

- Consome: eventos/coordenador da Task 38 e manifests ativos dos stores.
- Produz:

```java
public enum ExternalSourcePhase {
    NOT_CHECKED, CHECKING, UP_TO_DATE, UPDATE_AVAILABLE, APPLYING, APPLIED, FAILED
}

public enum ExternalSourcesPhase {
    IDLE, CHECKING, UP_TO_DATE, UPDATES_AVAILABLE,
    WAITING_FOR_VALIDATION, APPLYING, RESTART_REQUIRED, FAILED
}

public record ExternalSourceState(
        ArtifactId artifact,
        String name,
        String activeVersion,
        String origin,
        String abbreviatedHash,
        Instant updatedAt,
        Instant checkedAt,
        ExternalSourcePhase phase,
        String detail,
        ArtifactFailureKind failureKind,
        String candidateVersion) {}

public record ExternalSourcesSnapshot(
        ExternalSourcesPhase phase,
        List<ExternalSourceState> sources,
        int availableCount,
        int failedCount,
        boolean validationActive,
        long revision) {}
```

- `ExternalSourcesUseCase`:

```java
ExternalSourcesSnapshot snapshot();
void observe(Consumer<ExternalSourcesSnapshot> observer);
boolean checkNow();
boolean applyAvailable();
void validationStateChanged(boolean active);
```

- `MainView`:

```java
void showExternalSources(ExternalSourcesSnapshot snapshot);
void openExternalSourcesDialog();
boolean confirmExternalSourcesUpdate(ExternalSourcesSnapshot snapshot);
void showRestartRequired(ExternalSourcesSnapshot snapshot);
```

- [ ] **Passo 1: escrever testes vermelhos do agregado**

Adicionar em `ExternalSourcesUseCaseTest`:

```java
@Test
void exposesPartialSuccessWithoutHidingTheAvailableCandidate() {
    coordinatorPublishes(schemaAvailable, tableFailed);

    ExternalSourcesSnapshot snapshot = sources.snapshot();

    assertThat(snapshot.phase()).isEqualTo(ExternalSourcesPhase.UPDATES_AVAILABLE);
    assertThat(snapshot.availableCount()).isOne();
    assertThat(snapshot.failedCount()).isOne();
}

@Test
void validationTurnsAvailableIntoWaitingAndRestoresItWhenValidationEnds() {
    coordinatorPublishes(schemaAvailable);

    sources.validationStateChanged(true);
    assertThat(sources.snapshot().phase())
            .isEqualTo(ExternalSourcesPhase.WAITING_FOR_VALIDATION);

    sources.validationStateChanged(false);
    assertThat(sources.snapshot().phase())
            .isEqualTo(ExternalSourcesPhase.UPDATES_AVAILABLE);
}
```

Adicionar:

```java
@Test
void publishesTheSameImmutableRevisionToEveryObserver() {
    List<ExternalSourcesSnapshot> first = new ArrayList<>();
    List<ExternalSourcesSnapshot> second = new ArrayList<>();
    sources.observe(first::add);
    sources.observe(second::add);

    coordinatorPublishes(schemaChecking);

    assertThat(first.getLast()).isEqualTo(second.getLast());
    assertThatThrownBy(() -> first.getLast().sources().clear())
            .isInstanceOf(UnsupportedOperationException.class);
}

@Test
void initialSnapshotContainsOnlyTheTwoActiveV0Sources() {
    assertThat(sources.snapshot().sources())
            .extracting(ExternalSourceState::artifact)
            .containsExactly(ArtifactId.NFE_SCHEMAS, ArtifactId.FISCAL_TABLES);
}
```

- [ ] **Passo 2: confirmar a falha**

```bash
./gradlew test --tests '*ExternalSourcesUseCaseTest' --console=plain
```

Esperado: falha de compilação pelos novos tipos.

- [ ] **Passo 3: implementar o snapshot no caso de uso**

Trocar `currentEvents` por estado sincronizado que derive sempre uma cópia imutável. Incrementar
`revision` em toda mudança publicada. Agregar nesta precedência:

```text
APPLYING
RESTART_REQUIRED (ao menos um APPLIED)
CHECKING
WAITING_FOR_VALIDATION (candidata + validação ativa)
UPDATES_AVAILABLE (candidata)
FAILED (todas as fontes terminaram em falha)
UP_TO_DATE (todas as fontes consultadas, sem candidata/falha)
IDLE
```

Em sucesso parcial com candidata, conservar `UPDATES_AVAILABLE`; com pelo menos uma base aplicada,
conservar `RESTART_REQUIRED` mesmo que outra fonte falhe.

- [ ] **Passo 4: executar testes do caso de uso**

```bash
./gradlew test --tests '*ExternalSourcesUseCaseTest' --console=plain
```

Esperado: PASS.

- [ ] **Passo 5: escrever testes vermelhos do presenter**

Adicionar a `MainPresenterTest`:

```java
@Test
void doesNotOfferUpdateDuringValidationAndOffersItWhenValidationFinishes() {
    presenter.validateRequested();
    sourcesPublish(updateAvailable);
    assertThat(calls).doesNotContain("confirm-update");

    completeQueuedValidation();
    assertThat(calls).containsExactlyOnce("confirm-update");
}

@Test
void acceptedUpdateOpensTheSharedDialogAndStartsApplication() {
    fakeView.acceptUpdate = true;
    sourcesPublish(updateAvailable);

    assertThat(calls).containsSubsequence("confirm-update", "open-sources");
    assertThat(fakeCoordinator.applyCalls()).isOne();
}
```

Adicionar:

```java
@Test
void declinedRevisionIsNotOfferedAgainButANewRevisionIs() {
    fakeView.acceptUpdate = false;
    sourcesPublish(updateAvailableAtRevision(10));
    sourcesPublish(updateAvailableAtRevision(10));
    assertThat(calls).filteredOn("confirm-update"::equals).hasSize(1);

    sourcesPublish(updateAvailableAtRevision(11));
    assertThat(calls).filteredOn("confirm-update"::equals).hasSize(2);
}

@Test
void cancellationAlsoReleasesThePendingUpdatePromptThroughUiThread() {
    presenter.validateRequested();
    sourcesPublish(updateAvailable);
    presenter.cancelRequested();
    completeQueuedValidation();

    assertThat(uiThread.executions()).isPositive();
    assertThat(calls).contains("confirm-update");
}
```

- [ ] **Passo 6: confirmar a falha**

```bash
./gradlew test --tests '*MainPresenterTest' --console=plain
```

Esperado: falha de compilação/asserção com a view antiga.

- [ ] **Passo 7: implementar gate e confirmação no presenter**

Ao entrar/sair de validação, chamar `externalSources.validationStateChanged(validating)`. Observar
snapshots no `attach` e sempre publicar pela `UiThread`. Guardar a última `revision` oferecida para
não abrir o mesmo prompt repetidamente.

Quando `confirmExternalSourcesUpdate(snapshot)` retornar `true`, chamar
`externalSources.applyAvailable()`. Ao receber `APPLYING`, abrir o diálogo se ainda não estiver
aberto. Ao receber pela primeira vez `RESTART_REQUIRED`, pedir encerramento/reinício posterior.

- [ ] **Passo 8: executar testes focados e arquitetura**

```bash
./gradlew test --tests '*ExternalSourcesUseCaseTest' \
  --tests '*MainPresenterTest' \
  --tests '*ArchitectureTest' --console=plain
./gradlew test --console=plain
```

Esperado: PASS; nenhuma classe Swing fora de `presentation`.

- [ ] **Passo 9: criar brief, relatório, revisão e commit da Task 39**

Depois de PASS/PASS:

```bash
git add src/main/java/br/com/validadorlote/application \
  src/main/java/br/com/validadorlote/presentation/MainPresenter.java \
  src/main/java/br/com/validadorlote/presentation/MainView.java \
  src/test/java/br/com/validadorlote/application \
  src/test/java/br/com/validadorlote/presentation/MainPresenterTest.java \
  .superpowers/sdd/progress.md .superpowers/sdd/CURRENT.md
git commit -m "feat(b6): compartilha estado das bases com a validacao"
```

---

### Task 40 — Rodapé e diálogo adaptável

**Arquivos:**

- Criar: `src/main/java/br/com/validadorlote/presentation/swing/ExternalSourcesStatusBar.java`
- Criar: `src/main/java/br/com/validadorlote/presentation/swing/LoadingSpinner.java`
- Criar: `src/main/java/br/com/validadorlote/presentation/swing/ExternalSourcesPanel.java`
- Modificar: `src/main/java/br/com/validadorlote/presentation/swing/OutlineIcon.java`
- Modificar: `src/main/java/br/com/validadorlote/presentation/swing/ExternalSourcesDialog.java`
- Modificar: `src/main/java/br/com/validadorlote/presentation/swing/MainFrame.java`
- Testar: `src/test/java/br/com/validadorlote/presentation/swing/ExternalSourcesStatusBarTest.java`
- Testar: `src/test/java/br/com/validadorlote/presentation/swing/ExternalSourcesPanelTest.java`
- Testar: `src/test/java/br/com/validadorlote/presentation/swing/ExternalSourcesDialogTest.java`
- Testar: `src/test/java/br/com/validadorlote/presentation/swing/OutlineIconTest.java`

**Interfaces:**

- Consome: `ExternalSourcesSnapshot` da Task 39.
- `ExternalSourcesStatusBar`:

```java
ExternalSourcesStatusBar(
        String applicationVersion,
        String schemasVersion,
        Runnable openSources,
        Runnable retry);
void showSnapshot(ExternalSourcesSnapshot snapshot);
```

- `ExternalSourcesDialog`:

```java
ExternalSourcesDialog(
        Window owner,
        Runnable checkNow,
        Runnable applyAvailable,
        Runnable retry,
        Runnable closeApplication);
void showSnapshot(ExternalSourcesSnapshot snapshot);
void open();
boolean isOpen();
static boolean canClose(ExternalSourcesPhase phase);
```

- `ExternalSourcesPanel`:

```java
ExternalSourcesPanel(
        Runnable checkNow,
        Runnable applyAvailable,
        Runnable retry,
        Runnable closeApplication);
void showSnapshot(ExternalSourcesSnapshot snapshot);
```

- [ ] **Passo 1: escrever testes vermelhos do rodapé**

Em teste executado na EDT:

```java
@Test
void footerRendersCheckingAvailablePartialAndFailedStates() {
    statusBar.showSnapshot(checking);
    assertThat(statusBar.statusText()).isEqualTo("Consultando atualizações das bases…");
    assertThat(statusBar.isSpinnerRunning()).isTrue();

    statusBar.showSnapshot(partialAvailable);
    assertThat(statusBar.statusText())
            .isEqualTo("Atualização disponível · 1 fonte não respondeu");

    statusBar.showSnapshot(failed);
    assertThat(statusBar.isRetryVisible()).isTrue();
}
```

Adicionar:

```java
@Test
void retryIsAnIconOnlyActionWithTooltipAndOneCallbackPerClick() {
    AtomicInteger retries = new AtomicInteger();
    ExternalSourcesStatusBar bar = statusBar(retries::incrementAndGet);
    bar.showSnapshot(failed);

    assertThat(bar.retryText()).isEmpty();
    assertThat(bar.retryTooltip()).isEqualTo("Tentar consultar as bases novamente");
    bar.clickRetry();
    assertThat(retries).hasValue(1);
}
```

- [ ] **Passo 2: confirmar a falha**

```bash
./gradlew test --tests '*ExternalSourcesStatusBarTest' --console=plain
```

Esperado: falha de compilação porque o componente ainda não existe.

- [ ] **Passo 3: implementar rodapé e spinner**

`LoadingSpinner` deve desenhar oito pontos com alfa variável em `paintComponent` e avançar um frame
a cada 90 ms por `javax.swing.Timer`. `setRunning(false)` deve parar o timer e ocultar o componente.

O rodapé deve combinar, da esquerda para a direita, versão discreta, estado visível e ações de
bases. Textos obrigatórios:

```text
CHECKING: Consultando atualizações das bases…
UP_TO_DATE: Bases verificadas e atualizadas
UPDATES_AVAILABLE: Atualizações de bases disponíveis
WAITING_FOR_VALIDATION: Atualização disponível · aguardando o fim da validação
FAILED: Não foi possível consultar as bases
RESTART_REQUIRED: Bases atualizadas · reinicie para usar as novas versões
```

Para parcial, acrescentar ` · 1 fonte não respondeu`. Usar texto + ícone + cor; nunca somente cor.

- [ ] **Passo 4: executar testes do rodapé**

```bash
./gradlew test --tests '*ExternalSourcesStatusBarTest' --console=plain
```

Esperado: PASS.

- [ ] **Passo 5: escrever testes vermelhos do diálogo e ícones**

```java
@Test
void panelContainsOnlyActiveV0SourcesAndUsesScrollPane() {
    panel.showSnapshot(snapshotWithSchemasTablesAndNoCalculator);

    assertThat(panel.sourceCardCount()).isEqualTo(2);
    assertThat(findComponent(panel, JScrollPane.class)).isPresent();
}

@Test
void applyingDisablesEveryActionAndTheDialogPolicyRefusesClose() {
    panel.showSnapshot(applying);

    assertThat(panel.enabledActionCount()).isZero();
    assertThat(ExternalSourcesDialog.canClose(ExternalSourcesPhase.APPLYING)).isFalse();
    assertThat(ExternalSourcesDialog.canClose(ExternalSourcesPhase.RESTART_REQUIRED)).isTrue();
}

@Test
void operationalIconsAreDrawnVectorsAndNotUnicodeText() {
    assertThat(allOperationalLabels(panel))
            .noneMatch(label -> label.getText().matches(".*[●○◌].*"));
}
```

Adicionar:

```java
@Test
void actionsFollowTheSharedSnapshot() {
    panel.showSnapshot(idle);
    assertThat(panel.visibleActions()).containsExactly("Verificar agora", "Fechar");

    panel.showSnapshot(updateAvailable);
    assertThat(panel.visibleActions()).containsExactly("Atualizar agora", "Fechar");

    panel.showSnapshot(failed);
    assertThat(panel.visibleActions()).containsExactly("Tentar novamente", "Fechar");

    panel.showSnapshot(restartRequired);
    assertThat(ExternalSourcesDialog.canClose(restartRequired.phase())).isTrue();
}
```

- [ ] **Passo 6: confirmar a falha**

```bash
./gradlew test --tests '*ExternalSourcesPanelTest' \
  --tests '*ExternalSourcesDialogTest' \
  --tests '*OutlineIconTest' --console=plain
```

Esperado: falha com a janela fixa/modeless e os glifos atuais.

- [ ] **Passo 7: reescrever diálogo e ícones**

Usar `Dialog.ModalityType.APPLICATION_MODAL`, `setResizable(false)` e `pack()`. Colocar os cards
num `JScrollPane` sem borda. Depois do `pack`, limitar altura e largura a 85% da área útil retornada
por `GraphicsConfiguration`, descontando `Toolkit.getScreenInsets(...)`.

Construir cards, feedback e ações em `ExternalSourcesPanel`; o `JDialog` apenas hospeda esse painel
e aplica modalidade/tamanho/fechamento. Substituir `●`, `○` e `◌` por `OutlineIcon` (`DATABASE`,
`CORRECT`, `WARNING`, `ERROR`, `REFRESH`) e `LoadingSpinner`. Remover o card da Calculadora; no
máximo manter uma nota curta no rodapé do diálogo.

Ao entrar em `APPLYING`, usar `DO_NOTHING_ON_CLOSE`, remover o binding de `Esc`, desabilitar todos
os botões e manter progresso por card. Nos estados terminais, restaurar `HIDE_ON_CLOSE` e `Esc`.

O botão de atualização só chama `applyAvailable`; a operação roda no executor do coordenador. O
diálogo permanece aberto e renderiza os snapshots recebidos.

- [ ] **Passo 8: integrar com `MainFrame`**

Substituir o footer montado inline por `ExternalSourcesStatusBar`. A ação “Bases e atualizações”
deve chamar o presenter, que publica snapshot e pede abertura do diálogo. `showExternalSources`
atualiza ambos os componentes com o mesmo objeto.

Implementar confirmação com:

```text
Há atualizações disponíveis para as bases de validação.
Deseja atualizar agora?
```

Em parcial:

```text
Há atualização disponível. Uma das fontes não respondeu e continuará usando a base atual.
Deseja atualizar o que foi verificado?
```

Após aplicação, oferecer `Fechar aplicativo` e `Reiniciar depois`, sem relançamento automático.

- [ ] **Passo 9: executar testes focados, suíte e imagem**

```bash
./gradlew test --tests '*ExternalSourcesStatusBarTest' \
  --tests '*ExternalSourcesPanelTest' \
  --tests '*ExternalSourcesDialogTest' \
  --tests '*OutlineIconTest' \
  --tests '*MainPresenterTest' --console=plain
./gradlew clean test --console=plain
./gradlew jpackageImage --console=plain
```

Esperado: todos PASS; a imagem empacotada é gerada.

- [ ] **Passo 10: inspeção visual local**

Abrir a imagem gerada e conferir:

```text
[ ] spinner anima sem travar a janela
[ ] rodapé permanece legível em janela maximizada
[ ] diálogo tem somente fechar, sem maximizar/minimizar
[ ] cards de schemas e tabela cabem ou rolam
[ ] nenhum símbolo substituto aparece
[ ] consulta permite fechar o diálogo
[ ] aplicação impede fechar o diálogo
[ ] parcial não afirma que todas as bases foram atualizadas
```

- [ ] **Passo 11: criar brief, relatório, revisão e commit da Task 40**

Depois de PASS/PASS e fix loop:

```bash
git add src/main/java/br/com/validadorlote/presentation \
  src/test/java/br/com/validadorlote/presentation \
  .superpowers/sdd/progress.md .superpowers/sdd/CURRENT.md
git commit -m "feat(b6): apresenta atualizacao de bases com clareza"
```

Antes de publicar o bloco, incorporar/squashar o conteúdo útil de `d399af9` neste commit, mantendo
um único commit semântico para a task visual.

---

### Task 41 — Decisão, documentação e fechamento técnico

**Arquivos:**

- Modificar: `docs/decisions.md`
- Modificar: `docs/context.md`
- Modificar: `docs/architecture.md`
- Modificar: `docs/testing.md`
- Modificar: `docs/pesquisa/2026-07-29-canal-artefatos-externos.md`
- Modificar: `docs/superpowers/plans/2026-07-29-canal-confiavel-schemas.md`
- Modificar: `.superpowers/sdd/progress.md`
- Modificar: `.superpowers/sdd/CURRENT.md`

**Interfaces:**

- Consome o comportamento entregue nas Tasks 37–40.
- Não altera contratos de runtime.

- [ ] **Passo 1: registrar a decisão D-050**

Adicionar no topo de `docs/decisions.md`:

```markdown
## D-050 — Consulta prepara; usuário ativa; engines mudam após reinício (30/07/2026)

Schemas e tabelas são consultados e validados independentemente em staging. Uma confirmação global
ativa todas as candidatas válidas; falha de uma fonte preserva sua base anterior sem impedir a
outra. Consulta pode coexistir com validação de documentos, mas confirmação e ativação aguardam o
fim do lote. Rodapé e diálogo observam o mesmo snapshot, e engines só carregam as novas bases no
reinício para que uma sessão nunca misture referências.
```

- [ ] **Passo 2: alinhar documentação canônica e harness**

Em `context.md`, declarar que a rede consulta bases e a ativação é confirmada. Em
`architecture.md`, documentar `check → prepare → confirm → activate → restart`. Em `testing.md`,
adicionar os cenários de retry, parcial, persistência e EDT. Na pesquisa, registrar que o antigo
erro HTTPS persistido pertencia ao canal anterior e foi invalidado pela identidade do canal.

Atualizar o plano B6 e o ledger com commits, revisões, testes e divergências julgadas. Remover do
`CURRENT.md` a classificação de `d399af9` como protótipo depois de o histórico ser consolidado.

- [ ] **Passo 3: fazer a auto-revisão da spec**

Conferir um a um:

```text
[ ] consulta automática após janela visível
[ ] spinner e estado no rodapé
[ ] duas tentativas somente em falha transitória
[ ] erro vermelho + retry
[ ] sucesso parcial preservado
[ ] prompt adiado durante validação
[ ] uma confirmação global
[ ] diálogo compartilhando snapshot
[ ] aplicação sem fechamento
[ ] base ativa preservada em falha
[ ] restart required
[ ] sem Calculadora no runtime
[ ] sem infra externa nova
```

- [ ] **Passo 4: executar verificação final**

```bash
./gradlew clean test --console=plain
./gradlew jpackageImage --console=plain
git diff --check
git status --short
```

Esperado: suíte e imagem PASS; `git diff --check` sem saída; somente os arquivos esperados da task
aparecem antes do commit.

- [ ] **Passo 5: executar smoke real das fontes**

Iniciar a imagem empacotada com rede disponível e confirmar no log/estado local:

```text
[ ] https://dfe-portal.svrs.rs.gov.br/NFe/Documentos consultada
[ ] https://dfe-portal.svrs.rs.gov.br/DFE/TabelaClassificacaoTributaria consultada
[ ] schema 010b não causa downgrade de 010e
[ ] tabela igual retorna UP_TO_DATE
[ ] nenhum XML do lote participa das requisições
```

- [ ] **Passo 6: preparar checklist do Windows limpo**

Entregar ao dono estes testes manuais:

```text
[ ] escala 100%, 125% e 150%
[ ] diálogo sem conteúdo cortado
[ ] ícones presentes
[ ] consulta automática e retry
[ ] sucesso parcial
[ ] validação simultânea e prompt posterior
[ ] fechamento bloqueado durante aplicação
[ ] reinício carregando a versão ativada
```

- [ ] **Passo 7: revisão final e commit da Task 41**

Depois de revisão independente PASS/PASS:

```bash
git add docs .superpowers/sdd/progress.md .superpowers/sdd/CURRENT.md
git commit -m "docs(b6): registra fluxo confirmado de atualizacao"
```

Não fazer push. A publicação/PR só acontece depois do smoke do dono no Windows e autorização
explícita, conforme `docs/workflow.md`.

---

## Critérios de aceite finais

### Refinamentos posteriores ao plano-base (histórico de execução)

As revisões de integração após a Task 41 revelaram três janelas que o plano-base não explicitava.
Elas foram tratadas como tasks adicionais antes do fechamento, sem alterar julgamento fiscal,
fontes ou política de ativação:

- **Task 42** (`0ada78b`): admissão atômica validação↔ativação, evento terminal resiliente a
  listener e latch de reinício quando a ativação física vence a persistência.
- **Task 43** (`7fa9a73`): abertura do diálogo modal adiada para depois do dreno de snapshots na
  EDT, evitando bloquear os próprios eventos terminais.
- **Task 44** (`0d5750c`): `CHECKING` terminal sob listener defeituoso, falha parcial visível,
  prazo/cancelamento da leitura de corpo HTTP e feedback para rejeição do executor.
- **Task 45**: fechamento documental destas garantias. O checklist visual manual de Windows segue
  pendente; nenhuma dessas tasks é evidência de inspeção em DPI real.

- o erro HTTPS persistido pelo canal antigo desaparece e não adia uma consulta nova;
- usuário sempre distingue consulta, base atual, candidata, falha, aplicação e reinício pendente;
- rodapé e diálogo nunca divergem porque recebem o mesmo snapshot;
- consulta e preparação nunca alteram `current`;
- confirmação e ativação nunca ocorrem durante validação de documentos;
- sucesso parcial atualiza o que foi verificado sem escolhas técnicas por fonte;
- falha mantém a base anterior e torna a fonte elegível no próximo boot;
- nenhuma rede ou operação de artefato roda na EDT;
- diálogo funciona em Windows/DPI alto, tem scroll e não usa glifos Unicode operacionais;
- `clean test`, `jpackageImage`, ArchUnit e smoke real das duas URLs passam;
- nenhuma infraestrutura externa, telemetria ou ampliação de julgamento fiscal é introduzida.
