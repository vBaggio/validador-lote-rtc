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
2. O usuário compõe o lote e pede a validação. Pode optar por simular a vigência das regras: para
   documentos anteriores, o caso de uso usa como data operacional 03/08/2026 no CRT=3 e
   04/01/2027 nos CRTs 1, 2 e 4, sem mudar a data original do XML. `MainPresenter` alerta quando
   essa simulação alcança documento do Simples emitido antes de 2027; então processa somente as
   pendências, sequencialmente e fora da EDT, publicando o estado de cada linha e o progresso na
   view.
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
operacional. O ciclo é `check → prepare → confirm → activate → build → atomic publish`: `check`
adquire e valida; `prepare` grava uma candidata íntegra em staging, sem tocar `current`; uma única
confirmação do usuário autoriza `activate`; então o composition root monta, fora da EDT e do lock,
um novo `ValidationRuntime` completo a partir das referências `current` já íntegras. Somente uma
montagem integral publica a referência de uma vez. O schema verifica manifesto Ed25519,
`releaseSequence`, hash e closure antes de preparar; a tabela fiscal mantém o canal SVRS próprio.
Uma fonte pode falhar sem bloquear a candidata válida da outra: o runtime novo combina a referência
saudável recém-ativada com a última referência íntegra da fonte que falhou. O endpoint e a chave
pública do canal são escolhas embarcadas no `App`; indisponibilidade do canal preserva a última
`current` íntegra ou o fallback embarcado.

`ExternalSourcesUseCase` agrega os eventos em snapshots imutáveis com revisão monotônica e é a
única fonte de estado para presenter, rodapé e diálogo; observadores não são chamados sob lock e
uma entrega obsoleta não pode sobrescrever estado novo na EDT. O mesmo gate admite uma validação
e captura sua `ValidationLease` com o runtime no mesmo lock; assim, a validação inteira usa R1,
mesmo depois da publicação de R2. Reservada uma ativação, não começa worker de validação; depois da
ativação física, a reserva continua até a publicação de R2 ou o fallback. A construção não ocorre
sob o lock nem na EDT. A falha de um listener não impede os demais nem o evento terminal, inclusive
se ela acontecer ao publicar `CHECKING`. A abertura do diálogo application-modal é adiada para o
próximo ciclo da EDT, depois do dreno de snapshots, para que o modal nunca bloqueie a entrega do
estado terminal. Se a montagem de runtime falha após `activate`, `RESTART_REQUIRED` permanece
latched até encerrar o processo: `current` novo é preservado, R1 continua em uso e a candidata não
é reaplicada sem uma consulta fresca. Em sucesso, `UPDATED_AND_IN_USE` libera o gate e informa que
as bases já estão em uso; resultados existentes conservam a identidade de R1 e não são recalculados.

O transporte HTTPS tem prazo único para conexão, resposta e leitura completa do corpo. A leitura é
assíncrona, limitada e cancelável: timeout cancela requisição e assinatura, e corpo acima do limite
é recusado durante o streaming. Uma falha parcial é agregada como `FAILED` mesmo com outra fonte
`UP_TO_DATE`; a tela pode oferecer nova consulta. Se o executor rejeitar o agendamento de uma
ativação, a reserva é desfeita e o presenter informa a falha sem repetir a confirmação.

A tela **Fontes externas** pode forçar a consulta, mas o gate do coordenador recusa duplicação
enquanto ela está em curso. `ExternalSourcesUseCase` só expõe manifestos e estado local ao
presenter; não recebe XMLs, chaves ou CNPJ. O bootstrap monta R1 e uma atualização bem-sucedida
publica R2 atomicamente; nenhum engine mutável é alterado em uma validação em curso. O catálogo
também inventaria a Calculadora para v1, sem
download/execução no v0.

O aviso de versão do aplicativo é um fluxo consultivo separado: depois que a janela fica visível,
ele consulta em background apenas a última release estável do repositório oficial no GitHub. A
resposta é limitada, tem prazo curto e falhas de rede, HTTP, JSON ou navegador são silenciosas.
Ele não compartilha estado, agendamento, confirmação ou ativação com as bases; somente oferece a
página oficial da release quando a versão semântica disponível é superior à instalada.

Na tela **Fontes externas**, o rodapé exibe somente a versão compacta da base e reserva os detalhes
de proveniência para o tooltip. Os cards limitam a largura das colunas para acomodar versões e
origens sem cortar o conteúdo; o diálogo usa decoração controlada pela aplicação e não oferece
minimização durante a atualização; o fechamento fica na barra superior da janela.
