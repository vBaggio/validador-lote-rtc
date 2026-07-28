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
| `infrastructure/rules` | `RuleEngine`, `RuleOutcome` e regras de previsão de rejeição da NT |
| `infrastructure/tables` | `FiscalTables`, entradas CST/cClassTrib e `TablesManifest` das tabelas embarcadas |
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
4. `RootCauseGrouper` agrupa por `RootCauseKey.from(Finding)`: schema usa `(kind, xsdCode, field)`;
   rejeição prevista usa `(kind, rejectionCode)`; não avaliado usa `(kind, notEvaluatedCause)` e
   acrescenta `ruleId` apenas para `RULE_SPECIFIC`; assinatura ausente e XML ilegível usam o próprio
   `kind`. Em seguida, ordena por documentos afetados
5. `BatchReport` → UI (mestre-detalhe) e `CsvExporter` (2 arquivos, UTF-8 BOM, `;`)

## Schemas oficiais

`src/main/resources/schemas/{nfe,nfce}/` — extraídos do JAR oficial da Calculadora
(proveniência em `schemas-version.properties`; atualização via `./gradlew updateSchemas`).
Entrypoint de validação: `/schemas/nfe/nota.xsd` (declara `NFe`, `nfeProc`, `enviNFe`;
cobre modelos 55 e 65). Includes relativos resolvem via systemId de URL do classpath.
O contrato real da Calculadora (endpoints, quirks) está documentado em
[`calculadora/contrato-validar-xml.md`](./calculadora/contrato-validar-xml.md).
