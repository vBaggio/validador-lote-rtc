# Design — Camada de previsão de rejeição (IBS/CBS)

| | |
|---|---|
| **Status** | Aprovada — implementação em andamento |
| **Data** | 27/07/2026 |
| **Complementa** | [`2026-07-26-validador-lote-rtc-design.md`](./2026-07-26-validador-lote-rtc-design.md) |
| **Motivação** | A validação de schema não prevê rejeição: o grupo IBS/CBS é `minOccurs="0"` no XSD |

---

## 1. O problema que esta camada resolve

O v0 valida estrutura contra o XSD oficial. Isso garante que as tags existem e têm formato válido —
**não** que a SEFAZ aceitará o documento.

O caso que expõe a lacuna é o mais comum de todos: uma NF-e de emitente em Regime Normal (CRT=3)
**sem nenhum grupo IBS/CBS** passa limpa no XSD, porque o schema declara o grupo como opcional
(necessário durante a transição e para outros regimes). A partir de **03/08/2026** a SEFAZ rejeita
esse documento pela regra `UB12-10` (rejeição 1115). Nosso relatório diria "tudo certo" para
exatamente as notas que serão recusadas — o falso negativo que destrói a confiança num validador.

Verificado empiricamente: dos 13 XMLs reais analisados, os 12 documentos fiscais passam limpos no
XSD e **nenhum deles tem grupo IBS/CBS**.

### 1.1 O que esta camada NÃO promete

Não substitui a validação oficial e não garante aceitação. A SVRS roda o próprio autorizador em
modo simulação; nós aproximamos. Toda regra que não implementarmos é um falso negativo em
potencial, e o produto precisa dizer isso ao usuário de forma visível — não como isenção de
responsabilidade, mas para que ele continue conferindo o que precisa ser conferido.

**A promessa honesta é triagem em lote**: descobrir em segundos que 380 de 500 notas têm o mesmo
problema sistêmico, para corrigir na origem (o emissor), não nota a nota.

---

## 2. Escopo

**Dentro:** IBS e CBS, modelos 55 (NF-e) e 65 (NFC-e).

**Fora, por decisão:** Imposto Seletivo, demais DFe (CT-e, NFS-e, BP-e, NF3e), regras de eventos
(seção 8 da NT, ~15-25 regras adicionais), e cobertura completa das 277 regras da seção 7.

---

## 3. A estratégia: regra dirigida por tabela, não por código

A NT tem 277 regras formais (129 de presença, 77 de cálculo, 44 de tabela, 27 outras).
Transcrever todas seria um corpo de código que envelhece a cada revisão da NT — foram **13
revisões em 16 meses**.

A alternativa: as tabelas oficiais **já carregam os metadados que governam a maior parte das regras
de presença**. A fonte autoritativa é a tabela CST × cClassTrib da SVRS (§5.5), que traz os
indicadores nomeados pela própria NT:

| Campo | Nível | Distribuição | Regra que governa |
|---|---|---|---|
| `IndExigeTrib` (`ind_gIBSCBS`) | CST | 11 de 18 | **1021**, **1022** — grupo exigido ou proibido |
| `IndReducaoAliq` (`ind_gRed`) | CST | **3** de 18 | **1033**, **1074**, **1079** — grupo de redução ausente |
| `IndDiferimento` (`ind_gDif`) | CST | 2 de 18 | grupos de diferimento |
| `IndNfe` / `IndNfce` | cClassTrib | — | **1025** — cClassTrib não permitida no modelo |
| `PercRedIbs` / `PercRedCbs` | cClassTrib | — | valor de redução divergente do oficial |
| `Anexos[].CodNcmNbs` | cClassTrib | 4.628 entradas | vínculo NCM/NBS × classificação |

> **Atenção — armadilha já cara uma vez.** A Calculadora expõe um campo de nome parecido,
> `possuiPercentualReducao`, que **não** é o `ind_gRed`: ele é por classificação tributária
> (verdadeiro em 60 de 161) enquanto o indicador real é por CST (verdadeiro em 3 de 18). Usar o
> primeiro no lugar do segundo produz falso positivo em escala. Ver §5.5.

**Consequência de projeto:** um punhado de mecanismos genéricos dirigidos por tabela substitui
dezenas de regras codificadas, e uma base nova muda o comportamento sem tocar no código.

---

## 4. Arquitetura

A camada nova entra como um estágio adicional do caso de uso, **depois** da validação de schema.
A regra de dependência do projeto não muda.

```
infrastructure/
├── xml/          (existente) SchemaValidatorEngine, XmlMetadataParser, XsdErrorTranslator
├── tables/       (novo) tabelas oficiais embarcadas + consultas
│   ├── ClassificacaoTributariaTable   cClassTrib → metadados
│   ├── SituacaoTributariaTable        CST
│   ├── NcmTable / NbsTable
│   └── TablesVersion                  proveniência (versão da base, data)
├── rules/        (novo) motor de regras
│   ├── RuleEngine                     avalia as regras contra o documento
│   ├── DocumentRule                   regras de documento (CRT, vigência)
│   └── TableDrivenRule                regras derivadas dos metadados de tabela
└── calculator/   (v1) processo filho, oráculo diferencial de valores
```

### 4.1 Modelo de domínio — o que muda

`FindingKind` ganha valores para distinguir a origem do achado, porque a UI precisa exibir em
camadas (como o validador da SVRS faz) e o usuário precisa saber o que foi verificado:

```java
enum FindingKind {
    SCHEMA, SIGNATURE_MISSING, UNREADABLE,   // existentes
    REJECTION_RULE,                          // regra de presença/tabela — previsão de rejeição
    NOT_EVALUATED                            // faltou dado para emitir veredito
}
```

`Finding` carrega `rejectionCode` e `ruleId` nas rejeições previstas, e `notEvaluatedCause` nos
itens não avaliados. A identidade de agrupamento não é texto nem uma tupla única: a fábrica
`RootCauseKey.from(Finding)` escolhe a chave por camada — schema por `kind + xsdCode + field`,
rejeição por `kind + rejectionCode`, e não avaliado por `kind + notEvaluatedCause` (acrescentando
`ruleId` somente para `RULE_SPECIFIC`).

O `RuleEngine` devolve `RuleEvaluation(findings, itemCount, verifiedItemCount)`. O último contador
inclui somente itens em que alguma regra chegou a veredito (`Conforme` ou `Rejeitado`); ele não
confunde ausência de achado com aprovação.

### 4.2 Fluxo por documento

O primeiro corte aprovado cobre as rejeições **1115, 1021, 1022, 1024, 1025, 1033, 1074, 1079,
1034, 1046 e 1063**. A D-026 registra esse escopo; os demais códigos continuam fora do corte.

```
1. Parse de metadados — AMPLIADO: o parser atual não extrai CRT nem os campos do
   grupo IBS/CBS por item. Ambos são trabalho novo e pré-requisito desta camada.
2. Validação XSD (existente) — coleta total
3. Regras de documento:
   - CRT=3 e data ≥ 03/08/2026 → cada item precisa de IBSCBS   [1115 / UB12-10]
   - grupo informado quando o CST não permite                   [1021]
4. Regras dirigidas por tabela, por item (tudo consultado NA DATA do fato gerador):
   - cClassTrib existe e vigente
   - cClassTrib permitida para o modelo do documento            [1025]
   - cClassTrib pertence ao CST informado
   - grupo de redução presente sse IndReducaoAliq do CST        [1033/1074/1079]
   - percentuais declarados batem com PercRedIbs / PercRedCbs
   - NCM/NBS do item consta nos anexos da classificação
5. (v1) Oráculo diferencial de valores via regime-geral
```

### 4.3 Quatro desfechos por verificação, não dois

Toda regra termina em um de **quatro** estados, e a distinção é decisiva para a confiança:

| Desfecho | Quando | Como aparece |
|---|---|---|
| **Conforme** | verificado e correto | camada aprovada |
| **Não aplicável** | a exigência não vale para o documento (regime ou vigência) | não é aprovação nem achado |
| **Não avaliado** | falta dado para julgar | contado à parte, nunca somado aos aprovados |
| **Rejeitado** | verificado e viola a regra | achado com código e mensagem oficial |

Os desfechos de ausência de veredito existem porque **base velha não é erro do emitente**. Um `cClassTrib` publicado
depois da nossa extração não está na tabela embarcada; tratá-lo como rejeição seria acusar o
usuário de um defeito nosso. O mesmo vale para documento sem CRT legível, ou com CST fora da
tabela. O relatório precisa dizer "não consegui avaliar 12 itens" em vez de aprová-los em silêncio
ou reprová-los injustamente.

### 4.4 Supressão em cascata

Sem hierarquia explícita, um documento vazio gera dezenas de achados repetindo a mesma causa. A
regra é: **só a indisponibilidade de uma precondição suprime as regras que dela dependem no item**.
O motor não usa o desfecho `Rejeitado` como sinal de parada: é a ausência do dado observado — por
exemplo, o invólucro `IBSCBS`, e não a 1115 que ele pode produzir — que corta a cascata. Fora esses
cortes, cada regra ainda pode revelar uma rejeição independente.

```
data de emissão ausente ou invólucro IBSCBS ausente  suprime  as dez regras dependentes
CST ausente                                      suprime  as regras que exigem CST
CST fora da tabela                                suprime  as regras que exigem seus indicadores
cClassTrib ausente ou fora da tabela              suprime  as regras que exigem seus metadados
```

O achado suprimido não é perdido: ele não existe, porque a causa-raiz é a precondição indisponível.
É o mesmo princípio do agrupamento — o contador precisa de uma causa acionável, não de sintomas.

### 4.5 Aplicabilidade por regime e vigência

A 1115 vale para **CRT=3 a partir de 03/08/2026**; para Simples Nacional e MEI (CRT 1, 2 e 4) só em
**04/01/2027**. Consequência prática:

- Documento de CRT diferente de 3, com data anterior a 04/01/2027 → a regra **não se aplica**.
  Isso é distinto de "conforme": o relatório deve deixar claro que a exigência ainda não vigora
  para aquele emitente, senão o contador conclui que está tudo certo e é surpreendido em janeiro.
- Toda consulta a tabela usa a **data do fato gerador do documento**, não a data de hoje. Os
  registros têm `DthIniVig`/`DthFimVig` próprios, e validar um documento de agosto contra a
  vigência de dezembro daria veredito errado.

---

## 5. Artefatos oficiais: um mecanismo único de ingestão

O projeto passa a depender de **três** fontes oficiais externas: os XSDs (do JAR da Calculadora), as
tabelas de dados abertos (API da Calculadora) e a tabela CST × cClassTrib (portal da SVRS). Tratar
cada uma de um jeito produziria três mecanismos ad-hoc, três formatos de proveniência e três modos
de falhar.

**Decisão: um mecanismo só, com a mesma forma para todos.** A task `updateSchemas` já existente
(D-005) vira o molde; as demais seguem o mesmo contrato.

### 5.1 O contrato de ingestão

Cada artefato oficial embarcado obedece às mesmas cinco regras:

1. **Uma task Gradle dedicada**, no grupo `build setup`, fora do build e do CI. Rede **só** aqui —
   a política de que build e CI não tocam a rede continua valendo integralmente.
2. **Destilação na ingestão**: grava-se em resources apenas o que o produto consome, não o bruto.
   O bruto é reproduzível a partir da fonte e não é versionado. (A tabela da SVRS, por exemplo, tem
   4,4 MB brutos e 420 KB destilados.)
3. **Validação de esquema com falha ruidosa**: a task confere que o formato recebido é o esperado e
   **falha alto** se mudou. Isso vale especialmente para a SVRS, cujo JSON está embutido em HTML e
   não é contrato de API — uma mudança de layout precisa quebrar a atualização, nunca produzir
   tabela silenciosamente vazia ou truncada.
4. **Proveniência por artefato**: nesta rodada, schemas e tabelas preservam seus manifestos
   separados e seus consumidores atuais: `resources/schemas/schemas-version.properties` e
   `resources/tables/manifest.properties`. Unificá-los exigiria migrar o contrato de schemas sem
   reduzir o risco fiscal imediato (D-025).
5. **Idempotência e diff legível**: rodar duas vezes sem publicação nova não muda nada; quando muda,
   o diff é revisável em PR.

### 5.2 O manifesto e o aviso de idade

Cada manifesto alimenta a proveniência do artefato que descreve: schemas em
`schemas/schemas-version.properties` e tabela em `tables/manifest.properties`. Eles permanecem
separados nesta rodada; qualquer política conjunta de exibição ou aviso depende de integração que
não faz parte deste corte.

### 5.3 As três fontes

| Artefato | Fonte | Task | Conteúdo |
|---|---|---|---|
| Schemas XSD | JAR da Calculadora (endpoint oficial da RFB) | `updateSchemas` | 14 XSDs de NF-e/NFC-e |
| CST × cClassTrib | Portal SVRS, JSON em `dadosOriginais` | `updateFiscalTables` | 18 CSTs com indicadores, 164 classificações, anexos de NCM/NBS |
| Tabelas de apoio | API de dados abertos da Calculadora | `updateFiscalTables` | NCM, NBS, alíquotas UF/município, fundamentações |

**A verificar na implementação:** há sobreposição entre a cClassTrib da SVRS e a da Calculadora. A
da SVRS é mais completa (traz os indicadores por CST e os anexos de NCM/NBS), mas a da Calculadora
tem campos que ainda não foram comparados um a um — `exigeGrupoDesoneracao`,
`incompativelComSuspensao`, `creditoOperacaoAntecedente`. Antes de eleger uma como primária,
comparar campo a campo e registrar o resultado. Se a da SVRS cobrir tudo, a da Calculadora deixa de
ser necessária para classificação, e restam dela apenas as tabelas de apoio e o papel de oráculo de
cálculo.

### 5.4 Duas visões da mesma tabela, com conteúdos diferentes

A tabela de **situações tributárias** (`/situacoes-tributarias/cbs-ibs`) traz apenas `id`, `codigo`
e `descricao` — nenhum indicador. Os indicadores estão nas duas visões de **classificação
tributária**, que não são equivalentes entre si:

**Consulta em massa** (`/classificacoes-tributarias/cbs-ibs`) — 18 campos por registro, 161
classificações: `possuiPercentualReducao` e os três percentuais, `exigeGrupoDesoneracao`,
`incompativelComSuspensao`, `indicaCreditoPresumidoFornecedor/Adquirente`,
`indicaApropriacaoCreditoAdquirenteCbs/Ibs`, `creditoOperacaoAntecedente`, `nomenclatura`,
`tipoAliquota`, `tiposDfeClassificacao`, `dataAtualizacao`.

**Consulta por DFe** (`/classificacoes-tributarias/cbs-ibs/{siglaDfe}/{cClassTrib}`) — 6 campos,
**três deles inexistentes na consulta em massa**: `validoParaSiglaDfeInformado` (resposta direta à
rejeição 1025), `exigeGrupoTributacaoRegular`, `permiteDiferimento`,
`possibilidadeCreditoPresumido`.

**Consequência de projeto:** a task `updateFiscalTables` precisa materializar **as duas** visões — a em
massa para os metadados gerais, e a por-DFe iterando as classificações válidas para os modelos 55
e 65 (96 e 40 respectivamente). O resultado embarcado é a junção das duas.

### 5.5 Os indicadores por CST vivem em outra tabela

Investigação [INV-1](../../pesquisa/inv-1-tabela-cclasstrib-portal.md) localizou os indicadores que
a NT referencia: eles estão na **Tabela 03 (CST)** do Informe Técnico IT 2025.002, não na tabela de
classificação tributária. São `ind_gIBSCBS`, `ind_gRed`, `ind_gDif`, `ind_gTransfCred` e outros.
Confirmado na NT, que cita literalmente *"o indicador `ind_gRed` da tabela de CST"* (15 ocorrências).

**Correção de uma premissa anterior desta spec:** havia a suposição de que
`possuiPercentualReducao` (da tabela de cClassTrib) cobriria funcionalmente o `ind_gRed`. **Não
cobre.** São granularidades diferentes — `ind_gRed` é por CST (18 registros) e
`possuiPercentualReducao` é por classificação tributária (161 registros), com várias classificações
por CST. O indicador autoritativo para as rejeições 1033, 1074 e 1079 é o de CST.

A Calculadora **não expõe** a Tabela 03: seu endpoint de situações tributárias devolve apenas `id`,
`codigo` e `descricao`. E a obtenção automática esbarra em três obstáculos apurados pela INV-1: o
portal nacional publica só em PDF, a URL do arquivo carrega hash que muda a cada versão, e os
serviços REST da SVRS exigem certificado ICP-Brasil.

**Fonte localizada** — ver [INV-1b](../../pesquisa/inv-1b-fonte-oficial-cst-cclasstrib.md). O
portal da SVRS publica o conjunto completo em JSON público, sem autenticação, em
`https://dfe-portal.svrs.rs.gov.br/DFE/ClassificacaoTributaria` (embutido na página como
`dadosOriginais`): 18 CSTs com os 8 indicadores, 164 cClassTrib aninhadas com 27 indicadores cada,
e 4.628 anexos ligando 1.792 NCMs e 190 NBS às classificações.

Os números confirmam de forma contundente que os campos **não** são intercambiáveis:
`IndReducaoAliq` (o `ind_gRed` real) é verdadeiro em **3** dos 18 CSTs; `possuiPercentualReducao`
da Calculadora é verdadeiro em **60** das 161 classificações. Usar o segundo teria gerado falso
positivo em escala.

**Recomendação (D-025):** ingerir a tabela da SVRS numa task Gradle análoga à `updateSchemas`,
gravando versão destilada em resources — rede só nessa task, nunca no build. Riscos a tratar na
implementação: o JSON está embutido em HTML e não é contrato de API, então a task precisa validar
o esquema e falhar ruidosamente se o layout mudar; e vale verificar antes se
`consumo.tributos.gov.br` expõe o mesmo dado como REST formal, o que seria mais estável.

---

## 6. Versionamento e obsolescência

A Calculadora **não expõe a versão da Nota Técnica** — só `versaoApp` (1.2.4) e `versaoDb`
(V0039), que são versões de dados, não da norma.

Consequência, dita com precisão: a parte dirigida por tabela acompanha a base **sem exigir mudança
de código** — mas nada disso é automático para o usuário final, que só recebe dado novo quando
alguém roda a ingestão e publica versão nova do aplicativo. As duas regras de documento (1115 e
1021) ficam atadas à NT vigente na época em que foram escritas, sem sinal automático de
obsolescência.

Tratamento:

1. **Proveniência sempre visível** — o manifesto de artefatos oficiais (§5.2) aparece na tela e no
   CSV: origem e data de cada base embarcada. O contador julga a procedência. A versão da NT
   importa pouco aqui, porque apenas duas regras vêm dela; o que envelhece de fato são as tabelas.
2. **Aviso por idade, sem rede** — a NT teve 13 revisões em 16 meses (uma a cada ~5 semanas). Base
   com mais de 60 dias exibe aviso de possível desatualização. Não requer conexão.
3. **Verificação online opcional** contra o portal da NF-e — opt-in explícito, pós-MVP, por
   respeito ao princípio de que nada sai da máquina sem ação do usuário.

---

## 7. Apresentação em camadas

O relatório e a UI exibem o resultado **por camada**, deixando explícito o que foi verificado:

```
✓ Leitura do arquivo
✓ Schema XML (base V0039, extraída em 2026-07-26)
✗ Previsão de rejeição (NT 2025.002 v1.50)  — 3 causas, 380 documentos
    1115  IBS/CBS não informado                        380 documentos
    1025  cClassTrib não permitida neste modelo         12 documentos
    1033  Grupo de redução estadual não informado        7 documentos
⊘ Conferência de valores — não executada (requer a Calculadora)
```

O símbolo de camada não executada é tão importante quanto os outros: o usuário precisa distinguir
"verifiquei e está certo" de "não verifiquei".

---

## 8. Testes

Além da estratégia leve e dirigida já vigente:

- **Fixture por regra-alvo**: para cada rejeição implementada, um documento que a dispara e um que
  não dispara. Sem o par, a regra pode estar sempre ligada ou sempre desligada sem ninguém notar.
- **Supressão em cascata**: documento sem grupo IBS/CBS gera **um** achado por item, não a árvore
  inteira de subgrupos ausentes.
- **Tabelas como fixture congelada**: os testes usam uma cópia fixa das tabelas, para não quebrarem
  quando a base for atualizada.
- **Vigência**: o mesmo documento antes e depois de 03/08/2026 produz resultados diferentes; e um
  documento de CRT 1 antes de 04/01/2027 não gera a 1115.
- **Não avaliado**: item com `cClassTrib` ausente da tabela embarcada sai como *não avaliado*,
  nunca como conforme nem como rejeição. É o teste que protege contra acusar o usuário de um
  defeito nosso.
- **Consulta por data do fato gerador**: registro com vigência encerrada não vale para documento
  posterior, e vale para documento anterior.

---

## 9. Decisões pendentes

| # | Decisão | Impacto |
|---|---|---|
| **D-012** | Fonte da Calculadora na v1: embutir × baixar no primeiro uso | Volta à pauta — a conferência de valores exige o motor rodando, e o pacote oficial não tem licença |

---

## 10. Riscos

| Risco | Mitigação |
|---|---|
| Falso negativo por regra não implementada | Exibição em camadas + declaração explícita de escopo; priorização por frequência real |
| NT revisada e regras nossas envelhecem | Proveniência visível + aviso por idade; parte dirigida por tabela não envelhece |
| Transcrição errada da NT (25 mensagens vieram contaminadas por quebra de página na extração) | Cada regra implementada é conferida contra o PDF original, não contra a conversão |
| Mapeamento XML → operação (v1) é lossy | Oráculo diferencial só entra depois das camadas de presença e tabela |
