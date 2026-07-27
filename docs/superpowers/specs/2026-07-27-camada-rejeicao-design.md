# Design — Camada de previsão de rejeição (IBS/CBS)

| | |
|---|---|
| **Status** | Rascunho para revisão |
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

A alternativa: as tabelas oficiais da Calculadora **já carregam os metadados que governam a maior
parte das regras de presença**. Verificado nas 161 classificações tributárias:

| Campo da tabela | Distribuição | Regra que governa |
|---|---|---|
| `possuiPercentualReducao` | 60 sim / 101 não | 1033, 1074, 1079 — grupo de redução ausente |
| `percentualReducaoCbs/IbsUf/IbsMun` | ex.: `011001` → 60/60/60 | valor de redução divergente |
| `exigeGrupoDesoneracao` | 27 sim | grupo de desoneração ausente |
| `incompativelComSuspensao` | 24 sim | incompatibilidade com suspensão |
| `indicaCreditoPresumido*` | 8 e 7 sim | grupo de crédito presumido |
| `nomenclatura` (`NCM`/`NBS`/ambas) | 74 / 24 / 61 | nomenclatura informada indevidamente |
| `tiposDfeClassificacao` | NFe 96 · NFCe 40 | **1025** — cClassTrib não permitida no modelo |

**Consequência de projeto:** onze mecanismos genéricos dirigidos por tabela substituem dezenas de
regras codificadas. Quando a RFB publicar base nova, o comportamento acompanha sem tocar no código.

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
    VALUE_DIVERGENCE                         // valor declarado ≠ calculado (v1, via Calculadora)
}
```

`Finding` ganha um campo opcional `rejectionCode` (ex.: `"1115"`) e `ruleId` (ex.: `"UB12-10"`),
ambos nulos para achados de schema. A chave de causa-raiz passa a considerar o código de rejeição
quando houver — dois documentos com a mesma rejeição agrupam juntos.

`BatchReport` ganha a proveniência das camadas: versão do schema, versão da base de tabelas,
versão da NT transcrita.

### 4.2 Fluxo por documento

```
1. Parse de metadados (existente) — inclui agora CRT e data de emissão
2. Validação XSD (existente) — coleta total
3. Regras de documento:
   - CRT=3 e data ≥ 03/08/2026 → cada item precisa de IBSCBS   [1115 / UB12-10]
   - grupo informado quando não deveria                         [1021]
4. Regras dirigidas por tabela, por item:
   - cClassTrib existe e vale na data
   - cClassTrib permitida para o modelo do documento            [1025]
   - cClassTrib vinculada ao CST informado
   - nomenclatura (NCM × NBS) compatível
   - grupo de redução presente sse possuiPercentualReducao      [1033/1074/1079]
   - percentuais declarados batem com os oficiais
   - desoneração e crédito presumido conforme os flags
5. (v1) Oráculo diferencial de valores via regime-geral
```

**Ordem importa:** um documento sem grupo IBS/CBS não deve gerar dezenas de achados de subgrupo
ausente. Quando a regra 1115 dispara para um item, as regras de subgrupo daquele item são
suprimidas — senão o relatório afoga o usuário repetindo a mesma causa.

---

## 5. Tabelas oficiais: obtenção e proveniência

As tabelas são baixadas da Calculadora e **embarcadas no build**, como já é feito com os XSDs
(D-005): a task `updateTables` consulta os endpoints de dados abertos, grava os JSON em resources
e registra a proveniência. Build e CI continuam sem tocar a rede.

Endpoints usados:
`/dados-abertos/classificacoes-tributarias/cbs-ibs`, `/situacoes-tributarias/cbs-ibs`,
`/ncm`, `/nbs`, `/aliquota-uf`, `/aliquota-municipio`, `/versao`.

### 5.1 Duas visões da mesma tabela, com conteúdos diferentes

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

**Consequência de projeto:** a task `updateTables` precisa materializar **as duas** visões — a em
massa para os metadados gerais, e a por-DFe iterando as classificações válidas para os modelos 55
e 65 (96 e 40 respectivamente). O resultado embarcado é a junção das duas.

### 5.2 Os indicadores por CST vivem em outra tabela

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

Consequência: a parte dirigida por tabela acompanha a base automaticamente; as poucas regras de
documento ficam atadas à versão da NT que transcrevemos, sem sinal automático de obsolescência.

Tratamento:

1. **Proveniência sempre visível** — versão da NT transcrita, versão da base de tabelas e data de
   extração aparecem na tela e no CSV. O contador julga a procedência.
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
- **Vigência**: o mesmo documento antes e depois de 03/08/2026 produz resultados diferentes.

---

## 9. Decisões pendentes

| # | Decisão | Impacto |
|---|---|---|
| **D-012** | Fonte da Calculadora na v1: embutir × baixar no primeiro uso | Volta à pauta — a conferência de valores exige o motor rodando, e o pacote oficial não tem licença |
| **D-025** | Tabela auxiliar `cst-indicators.json` embarcada, atualizada fora do build — recomendação da INV-1 | §5.2 |
| **D-026** | Quais rejeições entram no primeiro corte | Recomendação: 1115, 1021, 1025, 1033, 1074, 1079 |

---

## 10. Riscos

| Risco | Mitigação |
|---|---|
| Falso negativo por regra não implementada | Exibição em camadas + declaração explícita de escopo; priorização por frequência real |
| NT revisada e regras nossas envelhecem | Proveniência visível + aviso por idade; parte dirigida por tabela não envelhece |
| Transcrição errada da NT (25 mensagens vieram contaminadas por quebra de página na extração) | Cada regra implementada é conferida contra o PDF original, não contra a conversão |
| Mapeamento XML → operação (v1) é lossy | Oráculo diferencial só entra depois das camadas de presença e tabela |
