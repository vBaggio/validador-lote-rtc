# Canal próprio de schemas curados Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** substituir a descoberta runtime de schemas da SVRS por um canal público, curado e assinado pelo projeto, que prepare somente uma closure íntegra e mantenha a última base funcional quando a atualização não puder ser usada.

**Architecture:** o repositório público de bases publica um manifesto estático assinado e ZIPs imutáveis; o aplicativo confia somente na chave pública embarcada, não no host nem no relógio. O cliente compara uma sequência monotônica do canal com a base ativa, verifica assinatura, hash e closure XSD antes de preparar a candidata; a ativação continua explícita, atômica e só vale após reinício. ACBr passa a ser insumo de curadoria humana, nunca transporte runtime.

**Tech Stack:** Java 21 (`Signature`/Ed25519, `MessageDigest`, `java.net.http`), Jackson 2.18, Swing/FlatLaf, JUnit 5, AssertJ, GitHub Pages ou conteúdo estático HTTPS.

## Global Constraints

- Respeitar `presentation → application → {domain, infrastructure}`; `javax.swing`/`java.awt` apenas em `presentation`.
- Nenhum XML, chave, CNPJ ou conteúdo de lote deixa a máquina; a consulta alcança somente manifesto e ZIP públicos allowlisted.
- A base embarcada e a última `current` íntegra são fallbacks. Nunca sobrescrever, apagar ou ativar `current` antes de a candidata passar todos os gates.
- Manter `check → prepare → confirm → activate → restart`, uma operação por vez, snapshots monotônicos e intervalo automático de 4 horas; consulta manual ignora a janela.
- Não inferir regra fiscal. A validação estrutural é a compilação real de `nota.xsd` e seus imports/includes; mudança incompatível termina como não suportada, nunca como aceitação silenciosa.
- Manifesto exige `releaseSequence` crescente. `publishedAt` é auditoria e exibição, não critério de ordenação nem confiança: relógio e timestamps podem regredir.
- Não implementar atualização automática do aplicativo, runtime SVN, fallback para SVRS/ACBr ou publicação automática do repositório neste bloco.
- Um commit semântico por task (`feat(b7): ...`, `test(b7): ...`, `docs(b7): ...`); sem push, PR ou merge durante as tasks.

---

## Contrato do canal externo e bootstrap humano

O canal é um repositório público separado, sugerido como `vBaggio/validador-lote-rtc-bases`. Ele não contém XML de usuários, somente uma árvore curada de XSDs NF-e/NFC-e. O aplicativo consulta um único endpoint estável, por exemplo:

```
https://vbaggio.github.io/validador-lote-rtc-bases/stable.json
```

O endpoint pode ser GitHub Pages ou um arquivo raw com URL estável. Cada ZIP fica em URL HTTPS imutável da mesma allowlist definida no aplicativo. O manifesto assinado, em UTF-8, tem esta forma canônica (a assinatura é Base64 do conteúdo de `signed`):

```json
{
  "format": 1,
  "keyId": "schemas-2026-01",
  "signed": {
    "artifact": "NFE_SCHEMAS",
    "releaseSequence": 1,
    "version": "rtc-2026.07.30-1",
    "publishedAt": "2026-07-30T12:00:00Z",
    "minimumAppVersion": "0.1.0",
    "zipUrl": "https://.../schemas-rtc-2026.07.30-1.zip",
    "zipSha256": "<64 hex minúsculos>",
    "sourceProvenance": [
      { "name": "ACBr", "url": "https://svn.code.sf.net/.../Schemas/NFe/", "revision": "47435" }
    ]
  },
  "signature": "<Base64 Ed25519 sobre a serialização JSON canônica de signed>"
}
```

`version` é identificador humano e de diretório; `releaseSequence` é o anti-rollback. A serialização assinada será os bytes UTF-8 produzidos por `ObjectMapper` com campos ordenados alfabeticamente e mapas ordenados por chave; arrays conservam a ordem. O gerador do repositório deve usar exatamente a mesma regra e publicar um fixture de verificação. A chave privada fica fora deste projeto (secret/local seguro do curador); o aplicativo embute apenas a chave pública X.509 Base64 e aceita exclusivamente `keyId` conhecido. Alterar uma release exige nova sequência e novo ZIP, nunca editar o arquivo já publicado.

Antes da Task 1, o dono cria o repositório público, gera um par Ed25519, protege a chave privada e publica `stable.json`, um ZIP de fixture e sua assinatura. Essa ação externa não é automatizada pelo aplicativo nem presume permissão de criação de repositório. Até haver endpoint e chave pública reais, os testes usam fixture local e a configuração de produção permanece desabilitada de forma explícita — nunca aponta para URL inventada.

### Task 1: Contrato assinado, parser estrito e verificador Ed25519

**Files:**
- Create: `src/main/java/br/com/validadorlote/infrastructure/xml/CuratedSchemaChannelManifest.java`
- Create: `src/main/java/br/com/validadorlote/infrastructure/xml/CuratedSchemaManifestParser.java`
- Create: `src/main/java/br/com/validadorlote/infrastructure/xml/Ed25519ManifestVerifier.java`
- Create: `src/test/java/br/com/validadorlote/infrastructure/xml/CuratedSchemaManifestParserTest.java`
- Create: `src/test/resources/fixtures/update/curated-schemas/valid-manifest.json`
- Create: `src/test/resources/fixtures/update/curated-schemas/invalid-signature.json`
- Modify: `build.gradle`

**Interfaces:**
- Produces `record CuratedSchemaChannelManifest(int format, String keyId, SignedRelease signed, String signature)` and `record SignedRelease(ArtifactId artifact, long releaseSequence, String version, Instant publishedAt, String minimumAppVersion, URI zipUrl, String zipSha256, List<SourceProvenance> sourceProvenance)`.
- Produces `CuratedSchemaChannelManifest CuratedSchemaManifestParser.parse(byte[] document)` and `byte[] canonicalSignedBytes(CuratedSchemaChannelManifest manifest)`.
- Produces `void Ed25519ManifestVerifier.verify(String keyId, byte[] signedBytes, String signatureBase64)`; throws `ArtifactUpdateException.invalidContent` for unknown key, malformed Base64 or invalid signature.

- [ ] **Step 1: Write the failing parser and cryptographic tests**

```java
@Test
void acceptsOnlyAWellFormedManifestWhoseSignatureMatchesItsCanonicalSignedPayload() {
    var manifest = parser.parse(fixture("valid-manifest.json"));

    verifier.verify(manifest.keyId(), parser.canonicalSignedBytes(manifest), manifest.signature());

    assertThat(manifest.signed().releaseSequence()).isEqualTo(7L);
    assertThat(manifest.signed().zipSha256()).matches("[0-9a-f]{64}");
}

@Test
void rejectsAChangedSignedFieldEvenWhenTheOuterJsonIsValid() {
    var manifest = parser.parse(fixture("invalid-signature.json"));

    assertThatThrownBy(() -> verifier.verify(manifest.keyId(),
            parser.canonicalSignedBytes(manifest), manifest.signature()))
        .isInstanceOf(ArtifactUpdateException.class)
        .extracting(e -> ((ArtifactUpdateException) e).kind())
        .isEqualTo(ArtifactFailureKind.INVALID_CONTENT);
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew test --tests '*CuratedSchemaManifestParserTest' --console=plain`

Expected: FAIL because the parser/verifier types do not exist.

- [ ] **Step 3: Implement strict parsing and verification**

Use the existing Jackson dependency with `FAIL_ON_UNKNOWN_PROPERTIES`, reject missing/null fields, `format != 1`, nonpositive sequence, invalid identifier/version, non-HTTPS ZIP, non-64-lowercase-hex hash and empty provenance. Serialize only `signed` through an `ObjectMapper` configured with `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` and `SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS`. Decode the embedded X.509 public key through `KeyFactory.getInstance("Ed25519")` and verify with `Signature.getInstance("Ed25519")`. Do not accept an unverified `keyId`, algorithm selected by the document, or any `zipUrl` host from the document until the updater applies its allowlist.

- [ ] **Step 4: Add boundary tests and run the focused suite**

Add tests for unknown field, duplicate/unknown key id, malformed Base64, invalid signature, negative sequence, HTTP URL and changed ordering of object fields yielding the same canonical bytes.

Run: `./gradlew test --tests '*CuratedSchemaManifestParserTest' --console=plain`

Expected: PASS.

- [ ] **Step 5: Run architecture and complete suite, then commit**

Run: `./gradlew test --console=plain`

Expected: PASS, including ArchUnit.

```bash
git add build.gradle src/main/java/br/com/validadorlote/infrastructure/xml/CuratedSchemaChannelManifest.java src/main/java/br/com/validadorlote/infrastructure/xml/CuratedSchemaManifestParser.java src/main/java/br/com/validadorlote/infrastructure/xml/Ed25519ManifestVerifier.java src/test/java/br/com/validadorlote/infrastructure/xml/CuratedSchemaManifestParserTest.java src/test/resources/fixtures/update/curated-schemas/
git commit -m "feat(b7): valida manifesto assinado de schemas"
```

### Task 2: Preparação de closure curada e proteção contra rollback

**Files:**
- Modify: `src/main/java/br/com/validadorlote/infrastructure/xml/SchemaArtifactStore.java`
- Modify: `src/main/java/br/com/validadorlote/infrastructure/xml/ArtifactManifest.java`
- Modify: `src/test/java/br/com/validadorlote/infrastructure/xml/SchemaArtifactStoreTest.java`

**Interfaces:**
- Extends the prepared manifest with `long releaseSequence`, `String channelId` and immutable provenance text suitable for UI/audit.
- Produces `ArtifactManifest SchemaArtifactStore.prepare(Path candidate, CuratedSchemaChannelManifest.SignedRelease release, String channelId, String discoveryUrl)`.
- Rejects a release whose sequence is not greater than the active manifest from the same `channelId`; permits a first curated release over embedded fallback.

- [ ] **Step 1: Write failing store tests**

```java
@Test
void preservesCurrentWhenPreparedReleaseHasNoHigherSequence() {
    store.prepare(validTree(), release(7), CHANNEL, MANIFEST_URI.toString());
    store.activate("rtc-7");

    assertThatThrownBy(() -> store.prepare(validTree(), release(7), CHANNEL, MANIFEST_URI.toString()))
        .hasMessageContaining("sequência");
    assertThat(store.activeManifestOrNull().version()).isEqualTo("rtc-7");
}

@Test
void rejectsClosureWithoutTheRealNotaEntrypointWithoutChangingCurrent() {
    store.install(validTree(), "rtc-7", SOURCE, NOW);

    assertThatThrownBy(() -> store.prepare(treeWithoutNotaXsd(), release(8), CHANNEL, MANIFEST_URI.toString()));
    assertThat(store.activeManifestOrNull().version()).isEqualTo("rtc-7");
}
```

- [ ] **Step 2: Run focused tests to verify failure**

Run: `./gradlew test --tests '*SchemaArtifactStoreTest' --console=plain`

Expected: FAIL because release sequence/channel are not persisted or enforced.

- [ ] **Step 3: Implement metadata persistence and structural gate**

Extend `manifest.properties` read/write compatibly: old manifests without curated fields remain readable as legacy active bases. Before copying, preserve current symlink/path defenses; after copy, require only `.xsd` files and build `SchemaValidatorEngine` from the copied root. Catch the engine's schema compilation failure and translate it to `ArtifactUpdateException.invalidContent("A estrutura dos schemas mais recentes não é suportada por esta versão do aplicativo", cause)`. Preserve staging cleanup in all cases. Compare release sequences only if the active manifest has the same `channelId`; version labels are never ordered lexicographically.

- [ ] **Step 4: Add integrity tests and run focused suite**

Test lower sequence, same sequence with distinct ZIP/hash, corrupt prepared tree before `activate`, and unknown legacy manifest. In every failure assert the prior `current` stays usable and no partial `versions/<new>` directory remains.

Run: `./gradlew test --tests '*SchemaArtifactStoreTest' --console=plain`

Expected: PASS.

- [ ] **Step 5: Run suite and commit**

Run: `./gradlew test --console=plain`

Expected: PASS.

```bash
git add src/main/java/br/com/validadorlote/infrastructure/xml/ArtifactManifest.java src/main/java/br/com/validadorlote/infrastructure/xml/SchemaArtifactStore.java src/test/java/br/com/validadorlote/infrastructure/xml/SchemaArtifactStoreTest.java
git commit -m "feat(b7): protege schemas curados contra rollback"
```

### Task 3: Aquisição do canal próprio sem confiança em host ou ZIP

**Files:**
- Create: `src/main/java/br/com/validadorlote/infrastructure/xml/CuratedSchemaUpdater.java`
- Modify: `src/main/java/br/com/validadorlote/infrastructure/tables/SafeHttpsClient.java`
- Create: `src/test/java/br/com/validadorlote/infrastructure/xml/CuratedSchemaUpdaterTest.java`
- Modify: `src/test/java/br/com/validadorlote/infrastructure/tables/SafeHttpsClientTest.java`

**Interfaces:**
- Produces `CuratedSchemaUpdater(SafeHttpsClient manifestHttps, SafeHttpsClient zipHttps, CuratedSchemaManifestParser parser, Ed25519ManifestVerifier verifier, SchemaZipExtractor zip, SchemaArtifactStore store, String channelId, URI manifestUri, String appVersion)`.
- `ArtifactCheckResult check()` verifies before extraction and `ArtifactManifest apply(ArtifactUpdateCandidate candidate)` only activates the prepared version.
- Adds `SafeHttpsClient.forCuratedSchemaManifest(Set<String> hosts)` and `forCuratedSchemaZip(Set<String> hosts)` with independent byte limits.

- [ ] **Step 1: Write failing updater tests with a fake HTTPS transport**

```java
@Test
void preparesOnlyASignedNewerZipWhoseHashMatchesTheManifest() {
    transport.respond(MANIFEST_URI, signedManifest(sequence(8), sha256(validZip())));
    transport.respond(ZIP_URI, validZip());

    var result = updater.check();

    assertThat(result.status()).isEqualTo(ArtifactCheckResult.Status.UPDATE_AVAILABLE);
    assertThat(store.activeManifestOrNull()).isNull();
    assertThat(store.preparedManifest(result.candidate().version()).releaseSequence()).isEqualTo(8);
}

@Test
void neverExtractsTamperedZipAndKeepsThePreviousBase() {
    installAndActivateRelease(7);
    transport.respond(MANIFEST_URI, signedManifest(sequence(8), HASH_OF_GOOD_ZIP));
    transport.respond(ZIP_URI, tamperedZip());

    assertThatThrownBy(updater::check).hasMessageContaining("hash");
    assertThat(store.activeManifestOrNull().version()).isEqualTo("rtc-7");
}
```

- [ ] **Step 2: Run focused test to verify it fails**

Run: `./gradlew test --tests '*CuratedSchemaUpdaterTest' --console=plain`

Expected: FAIL because `CuratedSchemaUpdater` does not exist.

- [ ] **Step 3: Implement acquisition in this exact order**

1. Download `stable.json` only from the configured manifest URL and manifest-host allowlist.
2. Strictly parse and verify Ed25519 signature.
3. Reject `artifact != NFE_SCHEMAS`, `minimumAppVersion` greater than `BuildVersion.current()` and sequence not newer than the active same-channel release. The first two return a typed non-transient incompatibility; do not download ZIP.
4. Check that `zipUrl` host belongs to an independently configured ZIP-host allowlist.
5. Download bytes under `SCHEMA_MAX_BYTES`, calculate SHA-256 on the exact downloaded bytes and compare in constant time with manifest hash.
6. Extract through the existing zip traversal/size protections, call `store.prepare`, then delete extraction staging in `finally`.

Return `upToDate` only for an equal active sequence. A lower signed sequence is a visible invalid-content/rollback failure, not up-to-date. Keep all `SafeHttpsClient` redirect, timeout and body cancellation behavior.

- [ ] **Step 4: Add hostile-input and compatibility tests**

Cover altered signature, unknown public key, redirect to non-allowlisted host, oversized body, hash mismatch, rollback sequence, malformed ZIP and `minimumAppVersion` newer than app. Assert no ZIP extraction for the first five cases and `current` remains unchanged for all cases.

Run: `./gradlew test --tests '*CuratedSchemaUpdaterTest' --tests '*SafeHttpsClientTest' --console=plain`

Expected: PASS.

- [ ] **Step 5: Run suite and commit**

Run: `./gradlew test --console=plain`

Expected: PASS.

```bash
git add src/main/java/br/com/validadorlote/infrastructure/xml/CuratedSchemaUpdater.java src/main/java/br/com/validadorlote/infrastructure/tables/SafeHttpsClient.java src/test/java/br/com/validadorlote/infrastructure/xml/CuratedSchemaUpdaterTest.java src/test/java/br/com/validadorlote/infrastructure/tables/SafeHttpsClientTest.java
git commit -m "feat(b7): baixa schemas somente do canal curado"
```

### Task 4: Integrar o canal, preservar tabelas e expor incompatibilidade sem limbo

**Files:**
- Modify: `src/main/java/br/com/validadorlote/App.java`
- Modify: `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactFailureKind.java`
- Modify: `src/main/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateCoordinator.java`
- Modify: `src/main/java/br/com/validadorlote/application/ExternalSourcesUseCase.java`
- Modify: `src/main/java/br/com/validadorlote/application/ExternalSourceState.java`
- Modify: `src/main/java/br/com/validadorlote/presentation/swing/ExternalSourcesPanel.java`
- Modify: `src/main/resources/messages.properties`
- Modify: `src/test/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateCoordinatorTest.java`
- Modify: `src/test/java/br/com/validadorlote/application/ExternalSourcesUseCaseTest.java`
- Modify: `src/test/java/br/com/validadorlote/presentation/swing/ExternalSourcesPanelTest.java`

**Interfaces:**
- Adds `ArtifactFailureKind.UNSUPPORTED_SCHEMA_STRUCTURE` and maps it to a stable, user-readable state distinct from network failure.
- Replaces `SvrsSchemaUpdater` wiring in `App` with `CuratedSchemaUpdater`; fiscal table wiring remains unchanged.
- `ExternalSourceState` exposes failure kind/detail so presentation can state that the base ativa foi mantida and an app update is recommended.

- [ ] **Step 1: Write failing coordinator/use-case/UI tests**

```java
@Test
void incompatibleSchemasRemainVisibleWithoutBlockingAnIndependentTableCandidate() {
    schemas.checkBehavior = () -> { throw unsupportedStructure(); };
    tables.checkBehavior = () -> ArtifactCheckResult.available(tableCandidate(), "Tabela pronta");

    coordinator.checkNow();

    assertThat(coordinator.state(NFE_SCHEMAS).failureKind())
        .isEqualTo(UNSUPPORTED_SCHEMA_STRUCTURE);
    assertThat(coordinator.applyAvailable()).isTrue();
}

@Test
void panelExplainsThatTheCurrentSchemasContinueInUseAndAppUpdateIsRequired() {
    panel.render(snapshotWithUnsupportedSchemaStructure());

    assertThat(panel.text()).contains("base atual foi mantida", "atualize o aplicativo");
}
```

- [ ] **Step 2: Run focused tests to verify failure**

Run: `./gradlew test --tests '*ArtifactUpdateCoordinatorTest' --tests '*ExternalSourcesUseCaseTest' --tests '*ExternalSourcesPanelTest' --console=plain`

Expected: FAIL because the typed failure and rendering do not exist.

- [ ] **Step 3: Implement typed flow and production wiring**

Add the new failure kind as non-transient. It must retain prior state/audit metadata, clear no active base, not create an activation candidate and not block a valid fiscal-table candidate. In `App`, use real endpoint/key configuration only when Task 0 bootstrap is populated; otherwise register the schema action as disabled with one explicit visible detail, retaining embedded schemas. Do not leave SVRS as hidden fallback for schemas. The UI copy must say, in pt-BR: a newer base was found, its structure is not supported by this app version, the current/embedded base remains active, and the user should update the app; validations that do not depend on the newer schema continue normally.

- [ ] **Step 4: Add concurrency/regression tests**

Run a check that returns unsupported structure while another manual check is attempted; assert the existing one-operation gate still rejects overlap and eventually releases. Assert a later valid manifest can be checked after the failure. Assert no restart-required latch appears for rejected schemas, while it still appears when tables activate.

Run: `./gradlew test --tests '*ArtifactUpdateCoordinatorTest' --tests '*ExternalSourcesUseCaseTest' --tests '*ExternalSourcesPanelTest' --console=plain`

Expected: PASS.

- [ ] **Step 5: Run suite and commit**

Run: `./gradlew test --console=plain`

Expected: PASS.

```bash
git add src/main/java/br/com/validadorlote/App.java src/main/java/br/com/validadorlote/infrastructure/update/ArtifactFailureKind.java src/main/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateCoordinator.java src/main/java/br/com/validadorlote/application/ExternalSourcesUseCase.java src/main/java/br/com/validadorlote/application/ExternalSourceState.java src/main/java/br/com/validadorlote/presentation/swing/ExternalSourcesPanel.java src/main/resources/messages.properties src/test/java/br/com/validadorlote/infrastructure/update/ArtifactUpdateCoordinatorTest.java src/test/java/br/com/validadorlote/application/ExternalSourcesUseCaseTest.java src/test/java/br/com/validadorlote/presentation/swing/ExternalSourcesPanelTest.java
git commit -m "feat(b7): informa schemas curados incompativeis"
```

### Task 5: Decisão, operação de curadoria e aceite de runtime

**Files:**
- Modify: `docs/decisions.md`
- Modify: `docs/architecture.md`
- Modify: `docs/operacao-atualizacao-bases.md`
- Create: `docs/operacao-canal-schemas-curados.md`
- Modify: `docs/context.md`
- Modify: `.superpowers/sdd/CURRENT.md`
- Modify: `.superpowers/sdd/progress.md`

**Interfaces:**
- Supersedes D-047/D-049 only for the runtime schema channel; SVRS remains provenance/research and fiscal tables retain their existing channel.
- Documents the external repository release checklist and the client runtime acceptance script.

- [ ] **Step 1: Write the decision and curators' release contract**

Record a new decision explaining why completeness and curation prevail over “latest package” labels, why Ed25519 plus sequence is required, and why ACBr is manual evidence only. Include the precise ACBr inspection command for curators:

```bash
svn log --xml -v -l 1 https://svn.code.sf.net/p/acbr/code/trunk2/Exemplos/ACBrDFe/Schemas/NFe/
```

The curator must inspect changed paths, review the XSD diff, compile the closure with this project, assign a strictly greater `releaseSequence`, produce ZIP/hash/manifest/signature, open review on the bases repo, and preserve provenance revision. A revision elsewhere in ACBr is not evidence that this schema directory changed.

- [ ] **Step 2: Document runtime acceptance scenarios**

Document exact expected outcomes for:

1. offline/timeout: embedded or previous active schemas validate as before; visible recoverable failure; no restart;
2. valid newer release: candidate appears, confirmation activates it, success appears above the bases dialog, restart is requested, and next boot loads the new closure;
3. ZIP hash/signature/redirect/ZIP traversal failure: no candidate, no staging/current mutation, visible invalid-source failure;
4. signed release with broken/incompatible closure or `minimumAppVersion` too new: clear “estrutura não suportada” feedback, prior base stays active, table updates still proceed, no restart latch for schemas;
5. replay/lower sequence: explicit rollback rejection and prior base remains active;
6. concurrent clicks/boot timer: one operation only, status continues observable, later check releases after terminal state;
7. delete `~/.validador-lote-rtc/artifacts/NFE_SCHEMAS` and state file: next manual consultation downloads/prepares the signed current release, without relying on embedded version comparison.

- [ ] **Step 3: Verify documentation links and commands**

Run: `rg -n 'D-049|SVRS.*schemas|canal curado|releaseSequence|ACBr' docs README.md`

Expected: no documentation says SVRS is a runtime schema fallback after the new decision; links from `context.md` resolve.

- [ ] **Step 4: Run full technical verification**

Run: `./gradlew clean test --console=plain && ./gradlew jpackageImage --console=plain && git diff --check`

Expected: all tests pass, package image builds and no whitespace errors.

- [ ] **Step 5: Commit and update ledger**

```bash
git add docs/decisions.md docs/architecture.md docs/operacao-atualizacao-bases.md docs/operacao-canal-schemas-curados.md docs/context.md .superpowers/sdd/CURRENT.md .superpowers/sdd/progress.md
git commit -m "docs(b7): operacionaliza canal curado de schemas"
```

## Execution order and review gates

Execute Tasks 1–5 in order. Each task gets a brief, independent implementation review and fix loop before the next task. Tasks 1–3 are security/integrity gates: do not wire production UI until their tests and review pass. Task 4 is the only task that changes runtime source selection. Task 5 is complete only after the external repository has a real reviewed bootstrap release; otherwise record the endpoint/key publication as a human gate and leave schema runtime consultation explicitly disabled rather than silently using SVRS.

## Self-review

- **Spec coverage:** own public repository, manual curation, safe timestamp replacement (`releaseSequence`), schemas as `current`, compatibility/structure alert, no user limbo, continued independent validations/tables, ACBr directory-specific capability, low-risk fallbacks, concurrency and acceptance tests are covered by Tasks 1–5.
- **Deliberate scope boundary:** automatic app update and automated ACBr/SVN runtime discovery are explicitly deferred. The former needs a separate security/release plan; the latter adds a fragile transport dependency without increasing the trust of the curated artifact.
- **Failure invariant:** every failed step occurs before `current` is replaced. After activation, the existing coordinator's restart latch prevents the running engines from mixing bases.
- **Placeholder/type scan:** the interfaces used by later tasks are defined by earlier tasks; implementation must adjust exact source paths only if the project changes them during the preceding reviewed task.
