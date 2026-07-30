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

`src/main/resources/schemas/{nfe,nfce}/` — closure embarcada do perfil NF-e 010e_v1.02, com
proveniência e hashes em `schemas-version.properties` (D-047). O runtime de schemas só consulta o
canal próprio curado e assinado de D-051; SVRS e ACBr não são fontes runtime nem fallback.
Entrypoint de validação: `/schemas/nfe/nota.xsd` (declara `NFe`, `nfeProc`, `enviNFe`;
cobre modelos 55 e 65). Includes relativos resolvem via systemId de URL do classpath.
O contrato real da Calculadora (endpoints, quirks) está documentado em
[`calculadora/contrato-validar-xml.md`](./calculadora/contrato-validar-xml.md).

Bases atualizadas ficam em `~/.validador-lote-rtc/artifacts/`, nunca junto da instalação. A
referência local só é usada após hash/estrutura/compilação; falha abre com a base embarcada. Esse
controle detecta corrupção operacional, mas não autentica alterações feitas por outro processo sob
a mesma conta — ver D-046.

O coordenador consulta schemas e tabelas fora da EDT, uma vez após o boot e depois no intervalo
operacional. O ciclo é `check → prepare → confirm → activate → restart`: `check` adquire e valida;
`prepare` grava uma candidata íntegra em staging, sem tocar `current`; uma única confirmação do
usuário autoriza `activate`; e somente o próximo processo carrega as novas bases nos engines. O
schema verifica manifesto Ed25519, `releaseSequence`, hash e closure antes de preparar; a tabela
fiscal mantém o canal SVRS próprio. Uma fonte pode falhar sem bloquear a candidata válida da outra,
sempre preservando a referência ativa anterior. O endpoint e a chave pública do canal são escolhas
embarcadas no `App`; indisponibilidade do canal preserva a última `current` íntegra ou o fallback
embarcado.

`ExternalSourcesUseCase` agrega os eventos em snapshots imutáveis com revisão monotônica e é a
única fonte de estado para presenter, rodapé e diálogo; observadores não são chamados sob lock e
uma entrega obsoleta não pode sobrescrever estado novo na EDT. Consulta pode coexistir com o lote,
mas a admissão de validação e ativação é atômica: reservada uma ativação, não começa worker de
validação; a reserva é liberada também se o executor a recusar. A falha de um listener não impede
os demais nem o evento terminal, inclusive se ela acontecer ao publicar `CHECKING`. A abertura do
diálogo application-modal é adiada para o próximo ciclo da EDT, depois do dreno de snapshots, para
que o modal nunca bloqueie a entrega do estado terminal. Se `activate` já retornou,
`RESTART_REQUIRED` permanece latched até encerrar o processo mesmo que persista/publicar o evento
terminal falhe; a candidata não é reaplicada sem uma consulta fresca.

O transporte HTTPS tem prazo único para conexão, resposta e leitura completa do corpo. A leitura é
assíncrona, limitada e cancelável: timeout cancela requisição e assinatura, e corpo acima do limite
é recusado durante o streaming. Uma falha parcial é agregada como `FAILED` mesmo com outra fonte
`UP_TO_DATE`; a tela pode oferecer nova consulta. Se o executor rejeitar o agendamento de uma
ativação, a reserva é desfeita e o presenter informa a falha sem repetir a confirmação.

A tela **Fontes externas** pode forçar a consulta, mas o gate do coordenador recusa duplicação
enquanto ela está em curso. `ExternalSourcesUseCase` só expõe manifestos e estado local ao
presenter; não recebe XMLs, chaves ou CNPJ. Os engines do lote são montados uma vez no bootstrap e
nunca trocados em memória. O catálogo também inventaria a Calculadora para v1, sem
download/execução no v0.
