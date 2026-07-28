# Design — Correções do review do Bloco 6

| | |
|---|---|
| **Status** | Aprovado para planejamento |
| **Data** | 28/07/2026 |
| **Escopo** | Corrigir os achados do review das Tasks 1–8 do Bloco 6 |
| **Não inclui** | Fixtures diferenciais da Task 9, gate humano da Task 10, integração no caso de uso ou implementação de novas rejeições |

## 1. Objetivo

Deixar a camada de previsão de rejeição internamente coerente antes da validação diferencial:

- causas agrupadas pela identidade correta de cada camada;
- mensagem oficial preservada literalmente e separada de texto produzido pelo aplicativo;
- ingestão fiscal incapaz de transformar mudança de formato em indicador falso;
- artefatos oficiais legíveis e com proveniência suficiente para auditoria;
- documentação canônica alinhada ao código e aos débitos realmente adiados;
- lógica das onze rejeições novamente conferida contra a NT 2025.002 v1.50 disponível em `tmp/`.

Falso positivo continua sendo o risco dominante. Dado fiscal ausente, inválido ou ambíguo faz a
atualização falhar ou a regra retornar `NOT_EVALUATED`; nunca cria acusação por valor padrão.

## 2. Identidade e agrupamento de causa

`RootCauseKey` continuará explícita, sem chave textual genérica. Ela terá os discriminadores
opcionais necessários às três camadas atuais:

- schema: `kind + xsdCode + field`;
- rejeição prevista: `kind + rejectionCode`;
- não avaliado por precondição compartilhada: `kind + notEvaluatedCause`;
- não avaliado por motivo específico: `kind + notEvaluatedCause + ruleId`.

Uma fábrica `RootCauseKey.from(Finding)` centralizará essa escolha. Para
`NotEvaluatedCause.RULE_SPECIFIC`, `ruleId` participa da chave; nas demais causas ele fica nulo, para
que regras suprimidas pela mesma precondição permaneçam no mesmo balde.

O agrupador usará exclusivamente essa fábrica. Testes cobrirão códigos de rejeição distintos,
precondições compartilhadas e motivos específicos de regras diferentes.

## 3. Proveniência das mensagens

Na camada de rejeição, `officialMessage` só armazenará texto proveniente da NT.
`friendlyMessage` armazenará explicação e diagnóstico produzidos pelo aplicativo. Os contratos das
camadas preexistentes de schema e leitura ficam fora desta correção.

Consequências:

- a 1024 preservará o texto-base literal da NT, inclusive a duplicação
  `Rejeição: Rejeição:` existente na v1.50; o sufixo paramétrico `[nItem: 999]` continuará
  representado estruturalmente por `Finding.itemNumber`, como nas demais regras;
- o detalhe dinâmico sobre o CST da classificação será mantido em `friendlyMessage`;
- achados `NOT_EVALUATED` terão `officialMessage = null` e o motivo em `friendlyMessage`;
- o fallback do agrupador consultará primeiro `friendlyMessage` e depois `officialMessage`, sem
  rotular texto local como oficial.

Não será acrescentado um décimo quinto componente ao `Finding`: os dois canais existentes já
expressam a distinção necessária.

## 4. Ingestão e carga das tabelas fiscais

### 4.1 Atualização

`updateFiscalTables` validará todas as linhas antes de gravar:

- raiz em formato de lista e quantidades mínimas;
- códigos de CST e cClassTrib não vazios e sem duplicidade;
- coleção de classificações presente em todo CST;
- indicadores obrigatórios presentes e booleanos em todas as linhas;
- datas obrigatórias presentes e parseáveis;
- percentuais, quando presentes, numéricos;
- vínculo `cClassTrib.Cst` coerente com o CST pai.

Qualquer divergência encerra a task antes da escrita. O JSON destilado será formatado
deterministicamente, com indentação e quebra de linha final.

### 4.2 Runtime

`FiscalTables` validará o recurso embarcado ao carregá-lo. Campos obrigatórios não usarão defaults
silenciosos de `JsonNode.path(...).asBoolean()` ou `asText()`. Recurso inválido lança
`IllegalStateException` com campo e registro identificados; código fiscal desconhecido em um XML
continua retornando `Optional.empty()`, pois isso é limitação da base, não corrupção do artefato.

Uma sobrecarga interna de carga por `InputStream` permitirá testes com JSON mínimo e malformado sem
alterar o recurso de produção.

## 5. Proveniência dos artefatos

O manifesto da tabela continuará separado do manifesto de schemas nesta rodada. Unificá-los exigiria
migrar `SchemasVersion`, `updateSchemas` e consumidores fora do problema observado; essa mudança não
reduz risco fiscal imediato.

O manifesto da tabela ganhará:

- referência normativa: `Informe Técnico 2025.002`;
- versão da publicação usada: `1.60`;
- data de publicação: `2026-06-23`;
- origem, data de extração e contagens já existentes.

A decisão de manter manifestos separados será registrada, substituindo a promessa não implementada
de manifesto único. A versão e a data de publicação serão campos de conferência manual, preservados
pela task e destacados no log de atualização, como já ocorre com as versões dos schemas. A
atualização continuará manual e revisada em PR.

## 6. Documentação e propriedade dos débitos

Serão reconciliados:

- D-033, para refletir a implementação real da chave por camada e os canais de mensagem;
- D-035, retirando da Task 9 a agregação que seu brief proíbe;
- D-036, removendo afirmações antigas de que `progress.md` é descartável;
- D-025 e D-026, formalizando ingestão e escopo do primeiro corte;
- status da spec do Bloco 6 e textos que ainda dizem seis regras;
- propriedade futura de `itemNumber` ambíguo, deduplicação por documento e mensagens amigáveis.

Esses débitos ficam explicitamente no bloco de integração/apresentação, não na Task 9 de fixtures.

## 7. Auditoria final contra a NT local

Depois das correções, as onze rejeições serão conferidas contra
`tmp/NT_2025.002_v1.50_RTC_NF-e_IBS_CBS_IS.md` e, quando a extração Markdown for ambígua, contra o
PDF homônimo:

| Código | Regra |
|---|---|
| 1115 | UB12-10 |
| 1021 | UB13-20 |
| 1022 | UB13-30 |
| 1024 | UB14-20 |
| 1025 | UB14-25 |
| 1033 | UB26-20 |
| 1074 | UB45-20 |
| 1079 | UB64-20 |
| 1034 | UB27-10 |
| 1046 | UB46-10 |
| 1063 | UB65-10 |

Para cada regra, a auditoria registrará: código, ID, modelos, gatilho, exceções, indicadores,
mensagem oficial, campos XML e cobertura deliberadamente parcial. Divergência normativa bloqueia a
conclusão e vira correção testada; limitação aceita permanece `NOT_EVALUATED` e documentada.

## 8. Estudo de próximas rejeições

Sem implementar regras novas, a mesma leitura da NT produzirá um estudo das rejeições do grupo UB
que podem agregar valor com baixo custo e baixo risco. A triagem considerará:

- relevância para NF-e/NFC-e e para a transição IBS/CBS;
- dados já disponíveis em `FiscalDocument`, `ItemTaxGroup` e `FiscalTables`;
- dados presentes diretamente no XML que exigiriam apenas extração local;
- indicadores já publicados pela tabela SVRS, ainda que não destilados;
- dependência de tabela oficial adicional, cálculo, consulta externa ou cruzamento entre documentos;
- clareza das exceções e risco de falso positivo;
- proximidade com as regras já implementadas, permitindo reutilização sem acoplamento artificial.

O resultado será uma matriz em `docs/pesquisa/candidatas-rejeicao-pos-b6.md`, com código, ID da NT,
condição resumida, dados necessários, disponibilidade atual, esforço relativo, risco fiscal e
recomendação. “Baixo custo” não bastará para recomendar uma regra: fonte oficial e todas as exceções
precisam estar disponíveis. Nenhuma candidata será implementada nesta rodada.

## 9. Testes e aceite

Cada mudança comportamental seguirá RED–GREEN–REFACTOR. O aceite exige:

1. testes novos falhando pelo motivo esperado antes da implementação;
2. testes focados verdes após cada correção;
3. `./gradlew clean test --console=plain` verde;
4. árvore sem resíduos não intencionais;
5. tabela embarcada novamente comparada com a fonte oficial;
6. matriz de auditoria das onze regras sem divergência não resolvida;
7. estudo de candidatas futuras concluído, sem código novo para elas;
8. nenhuma implementação das Tasks 9–11 nesta rodada.
