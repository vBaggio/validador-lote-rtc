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

1. `FolderScanner` recebe pasta ou XML individual; `XmlMetadataParser` lê os metadados seguros que
   formam a área de trabalho. XML ilegível não entra na grade e é informado ao usuário.
2. O usuário compõe o lote e pede a validação. `MainPresenter` processa somente as pendências,
   sequencialmente e fora da EDT, publicando o estado de cada linha e o progresso na view.
3. Por arquivo: `XmlMetadataParser` (StAX seguro, índice linha→item),
   `SchemaValidatorEngine` (Schema único compilado no boot; `Validator` por documento;
   `ErrorHandler` coletor) e `RuleEngine`. Cancelamento cooperativo conserva o que já terminou e
   devolve a linha ainda não iniciada ao estado pendente.
4. `FindingReclassifier` aplica o modo pré-emissão padrão (assinatura ausente → INFO).
5. `RootCauseGrouper` agrupa por `RootCauseKey.from(Finding)`: schema usa `(kind, xsdCode, field)`;
   rejeição prevista usa `(kind, rejectionCode)`; não avaliado usa `(kind, notEvaluatedCause)` e
   acrescenta `ruleId` apenas para `RULE_SPECIFIC`; assinatura ausente e XML ilegível usam o próprio
   `kind`. Em seguida, ordena por documentos afetados
6. A UI mostra documentos como visão primária e os problemas do selecionado como detalhe.
   `CsvExporter` continua produzindo 2 arquivos UTF-8 BOM/`;`, mas não possui entrada na UI até
   nova decisão (D-045).

## Schemas oficiais

`src/main/resources/schemas/{nfe,nfce}/` — closure do perfil NF-e 010e_v1.02, com autoridade,
transporte e hashes em `schemas-version.properties` (D-047).
Entrypoint de validação: `/schemas/nfe/nota.xsd` (declara `NFe`, `nfeProc`, `enviNFe`;
cobre modelos 55 e 65). Includes relativos resolvem via systemId de URL do classpath.
O contrato real da Calculadora (endpoints, quirks) está documentado em
[`calculadora/contrato-validar-xml.md`](./calculadora/contrato-validar-xml.md).

Bases atualizadas ficam em `~/.validador-lote-rtc/artifacts/`, nunca junto da instalação. A
referência local só é usada após hash/estrutura/compilação; falha abre com a base embarcada. Esse
controle detecta corrupção operacional, mas não autentica alterações feitas por outro processo sob
a mesma conta — ver D-046.
