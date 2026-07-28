# Auditoria dos artefatos oficiais embarcados

**Data:** 28/07/2026
**Escopo:** os dois artefatos que o produto embarca e trata como verdade — os XSDs em
`src/main/resources/schemas/` e a tabela CST × cClassTrib em `src/main/resources/tables/`.
**Estado auditado:** worktree `bloco/6-camada-rejeicao`, commit `c384f57`.
**Natureza:** pesquisa e parecer. Nenhuma alteração de código.

Este documento existe porque o princípio do projeto é que *julgamento fiscal vem de artefato
oficial*. Isso transfere o risco: quando o artefato está errado ou velho, o erro não aparece como
bug — aparece como acusação contra o usuário, com aparência de verdade oficial. Auditar o artefato é
auditar o produto.

---

## Sumário do parecer

| # | Achado | Gravidade | Onde |
|---|---|---|---|
| 1 | Os XSDs embarcados estão **uma revisão da NT atrás** e não conhecem quatro campos que entram em produção em **03/08/2026** | **alta** | §1 |
| 2 | A fonte escolhida para os XSDs (JAR da Calculadora) **não é a autoridade do leiaute da NF-e**, e a evidência empírica é direta | **alta** | §1.3 |
| 3 | O manifesto de proveniência está **partido em dois arquivos** e o aviso de idade previsto no design não tem como funcionar | média | §2 |
| 4 | `fimVig` é tratado como **inclusivo**, e a base tem exatamente um caso real onde isso decide errado | média | §3.1 |
| 5 | O modelo temporal é **uma versão por código**; republicação de um código com nova vigência **derruba o carregamento** | média | §3.2 |
| 6 | A tabela publica um único `PercRedIbs` para UF e Município, e há **um** caso onde IBS e CBS divergem — o código acerta | informativo (confirmação) | §3.3 |
| 7 | A NT diz **onde** está a Tabela de Combustíveis Monofásicos; ela não é inobtenível como a D-029 supõe | média | §4 |

---

## 1. Os XSDs embarcados estão atrás da NT que o produto valida

### 1.1 O que foi medido

Procurei nos XSDs embarcados os campos criados pela **NT v1.40**, cuja entrada em produção é
**03/08/2026** — daqui a seis dias — e os da **v1.50** (produção 03/11/2026):

| Campo | Criado em | Produção | Presente no XSD embarcado? |
|---|---|---|---|
| `gALCZFMCBS` / `tpALCZFMCBS` / `nProcSuframa` (UB66a) | v1.40 | 03/08/2026 | **não** |
| `cIndOp` (B25d) | v1.40 | 03/08/2026 | **não** |
| `refDFeAnt` (BB05) | v1.40 | 03/08/2026 | **não** |
| `ISUFEmit` (C22) | v1.40 | 03/08/2026 | **não** |
| `gIBSMonoAdRem`, `gIBSMonoAdValorem`, `gCBSMonoAdRem`, `gCBSMonoAdValorem`, `gpBioDiferenca` | v1.50 | 03/11/2026 | **não** |
| `tpNFCredito`, `tpNFDebito`, `gTransfCred`, `gAjusteCompet`, `gEstornoCred`, `gCredPresIBSZFM` | v1.30 | 10/11/2025 | sim |

O leiaute monofásico embarcado é o **anterior** à reformulação da v1.50 — `gIBSCBSMono` com
`gMonoPadrao` / `gMonoReten` / `gMonoRet` / `gMonoDif` chapados, sem a divisão Ad Rem / Ad Valorem
que a v1.50 introduziu e que as regras UB85a a UB104 pressupõem.

Conclusão: **os XSDs correspondem à NT v1.30/v1.35**. A NT que o projeto usa como fonte é a v1.50.

### 1.2 Por que isso é grave, e é grave de um jeito específico

O produto apresenta a validação de schema como a camada mais confiável — é a que traz linha, coluna
e mensagem oficial. Um XSD velho não produz falso negativo silencioso; produz **falso positivo com
autoridade**. A partir de 03/08/2026, uma NF-e legítima que use `cIndOp`, `ISUFEmit`, `refDFeAnt` ou
`gALCZFMCBS` será reprovada por erro estrutural, e o relatório vai atribuir ao emitente um defeito
que é nosso.

Isso viola diretamente a regra do projeto — *falso positivo é inaceitável* — e viola no ponto onde
o usuário tem menos condições de desconfiar.

A população atingida é estreita e identificável: emitentes de área incentivada (ZFM/ALC),
fornecedores de ente governamental, e quem informar o novo código de local da operação. Estreita não
é a mesma coisa que inofensiva: é exatamente o contador que abre o lote de um cliente da ZFM e vê
tudo vermelho.

Observação de detalhe, para quem for implementar as regras C22: **a NT escreve `ISUFemit` e o XSD
escreve `ISUFEmit`**. XML é sensível a maiúsculas. Vale a tag, não a prosa.

### 1.3 A causa não é a data da extração — é a fonte

O ponto mais importante desta seção é que **a extração está em dia**. `schemas-version.properties`
registra `extractedAt=2026-07-26`, e `baseVersion=V0039 (2026-07-08)`. Rodar `updateSchemas` de novo
não resolve nada.

A causa é que a task baixa os XSDs de dentro do **JAR da Calculadora de Tributos da RFB**
(`api-regime-geral.jar`, em `BOOT-INF/classes/xml/`). A Calculadora é um motor de cálculo; os XSDs
que ela carrega servem ao contrato de entrada *dela*, e acompanham a régua de release *dela*
(`engineVersion 1.2.4`, `baseVersion V0039`) — não o calendário de publicação do leiaute da NF-e.

A evidência é direta e está na própria máquina. O pacote do **Portal Nacional da NF-e** já baixado em
`tmp/Schemas/NFe/` (26/07/2026, mesmo dia da extração embarcada) **tem** `gALCZFMCBS`, `cIndOp`,
`refDFeAnt` e `ISUFEmit`. E o arquivo compartilhado é 30% maior:

```
tmp/Schemas/NFe/DFeTiposBasicos_v1.00.xsd                       64.114 bytes   (tem v1.40)
src/main/resources/schemas/nfe/originais/DFeTiposBasicos_v1.00.xsd  49.310 bytes   (não tem)
```

Duas cópias do mesmo arquivo, com o mesmo nome e a mesma versão declarada (`version="1.0"`), obtidas
no mesmo dia, de fontes diferentes, com conteúdos diferentes por uma revisão inteira da NT.

**Parecer:** a D-005 escolheu a fonte errada, por um motivo compreensível — o JAR resolvia o problema
de download automatizável e versionado. Mas o critério que importa não é a conveniência da ingestão,
é **quem é a autoridade do artefato**. Para o leiaute da NF-e/NFC-e, a autoridade é o Portal Nacional.
A Calculadora continua sendo a autoridade certa para o que ela é: o oráculo de cálculo da v1.

Recomendo revisitar a D-005 com essa evidência. Se houver trava real no download do Portal (o nome do
arquivo carrega versão que muda a cada publicação — o mesmo obstáculo que a INV-1 já registrou para o
PDF da NT), a alternativa honesta não é manter a fonte errada: é **declarar a versão do leiaute que o
produto suporta** e recusar-se a julgar documento que use campo desconhecido, em vez de reprová-lo.

Nota de detalhe: `nfe/originais/` e `nfce/originais/` embarcam o **mesmo** `DFeTiposBasicos_v1.00.xsd`
byte a byte (5 arquivos cada, um deles duplicado). Não é defeito, mas quem for corrigir a fonte precisa
corrigir os dois lados.

### 1.4 A conta de risco, em uma linha

Cada revisão da NT tem levado ~5 semanas. O produto já nasce com uma de atraso estrutural, porque a
fonte não acompanha o calendário da norma. Não é dívida que se paga rodando a task de novo.

---

## 2. Proveniência: dois manifestos, e o aviso de idade sem como funcionar

O design §5.1 e §5.2 estabelece **um** manifesto único (`resources/officialdata/manifest.properties`)
descrevendo todos os artefatos, e um aviso de base desatualizada que considera **o mais antigo**
deles.

O que existe hoje:

| Arquivo | Descreve | Consumido por |
|---|---|---|
| `resources/tables/manifest.properties` | só as tabelas | `TablesManifest` → `FiscalTables.provenance()` |
| `resources/schemas/schemas-version.properties` | só os schemas | **ninguém em runtime** |

`TablesManifest` lê apenas as chaves `tables.*`. `provenance()` devolve *"Informe Técnico 2025.002
v1.60, publicada em 23/06/2026; tabelas de …, extraídas em 2026-07-27"*. Os schemas não aparecem.

Duas consequências, e a segunda é a que interessa:

1. A tela e o CSV vão exibir proveniência **parcial**. O contador lê "base de 27/07/2026" e conclui,
   razoavelmente, que tudo que está embarcado é daquela data.
2. **O aviso de idade não pode funcionar como projetado.** Ele deve considerar o artefato mais
   antigo; o objeto que o implementaria só enxerga um dos dois. E — pela §1 — o artefato cuja idade
   realmente importa é justamente o que está fora do manifesto.

Há ainda um descompasso menor de nomenclatura que vale registrar antes que vire confusão: o
manifesto das tabelas diz `reference=Informe Técnico 2025.002` e `referenceVersion=1.60`, enquanto a
NT embarcada em `tmp/` é a **NT** 2025.002 **v1.50**. São dois documentos distintos com numeração
parecida — o IT (tabelas) e a NT (leiaute e regras) — e a proveniência exibida ao usuário precisa
deixar claro qual é qual, senão "v1.60" vai ser lido como "mais novo que a NT v1.50" quando não é
comparável.

**Parecer:** unificar os dois manifestos é barato e resolve os três problemas de uma vez. Enquanto
não for unificado, o aviso de idade não deve ser implementado — um aviso que só olha metade dos
artefatos é pior que nenhum, porque produz confiança onde não deve.

---

## 3. A tabela CST × cClassTrib

Aqui a notícia é boa: a ingestão é sólida, valida esquema, falha alto, e o `FiscalTables` valida de
novo no carregamento com mensagens específicas. Os achados abaixo são de **semântica temporal**, não
de robustez.

### 3.1 `fimVig` é tratado como inclusivo, e há um caso real onde isso decide errado

```java
boolean vigenteEm(LocalDate data) {
    return (iniVig == null || !data.isBefore(iniVig))
        && (fimVig == null || !data.isAfter(fimVig));
}
```

`iniVig` inclusivo está certo. `fimVig` inclusivo é que é a questão — e a base traz o caso que
decide:

| Código | CST | Início | Fim | Nome |
|---|---|---|---|---|
| `220001` | 220 | 2025-05-05 | **2026-01-01** | Incorporação imobiliária — regime especial |
| `220002` | 220 | 2025-05-05 | **2026-01-01** | Incorporação imobiliária — regime especial |
| `220003` | 220 | 2025-05-05 | **2026-01-01** | Alienação de imóvel por parcelamento do solo |
| `221002` | 221 | **2026-01-01** | — | Incorporação imobiliária — regime especial |
| `221003` | 221 | **2026-01-01** | — | Incorporação imobiliária — regime especial |
| `221004` | 221 | **2026-01-01** | — | Alienação de imóvel por parcelamento do solo |

São **os mesmos três casos de negócio**, migrados do CST 220 para o CST 221 na virada do ano. Os
nomes batem um a um. O `DthFimVig` original é `2026-01-01T00:00:00` — meia-noite, o instante exato em
que os sucessores começam.

A leitura correta de um par assim é meio-aberta: `[iniVig, fimVig)`. O código, ao truncar o timestamp
para `LocalDate` e comparar com `!data.isAfter(fimVig)`, torna o intervalo fechado — e no dia
**01/01/2026** considera vigentes, simultaneamente, os três códigos que acabaram e os três que
começaram.

Impacto prático: um documento emitido em 01/01/2026 com `cClassTrib 220001` é julgado contra uma
classificação que já não valia. É um dia, três códigos, um setor. Mas é o **único** ponto em toda a
base onde a máquina de vigência decide alguma coisa, e ela decide errado nele. E o defeito não é
visível por inspeção: só aparece quando alguém compara o `fimVig` de um registro com o `iniVig` do
sucessor, que é o que esta auditoria fez.

**Parecer:** tratar `fimVig` como exclusivo. Se houver dúvida sobre a convenção da SVRS, o par
220/221 acima é a evidência — não há outra leitura em que a sucessão faça sentido.

### 3.2 Uma versão por código: o modelo temporal não sustenta o que promete

`FiscalTables` indexa em `HashMap<String, CstEntry>` e `HashMap<String, ClassTribEntry>`, com
`putIfAbsent` que **lança exceção** em código repetido:

```java
if (classifications.putIfAbsent(code, classification) != null) {
    throw new IllegalStateException("Classificação duplicada na tabela fiscal: '" + code + "'");
}
```

O design §4.2 promete que "tudo é consultado **na data do fato gerador**", e o §4.5 explica por quê:
"os registros têm `DthIniVig`/`DthFimVig` próprios, e validar um documento de agosto contra a
vigência de dezembro daria veredito errado". Essa promessa só se realiza se **o mesmo código puder
existir em mais de uma janela**.

Na estrutura atual ele não pode. A consulta por data consegue dizer apenas *"este código está vigente
nesta data?"* — nunca *"qual era a regra deste código naquela data?"*. Para o histórico, os dois não
são a mesma pergunta.

E há um modo de falha desagradável: se a SVRS republicar um código com nova vigência — encerrando a
janela antiga e abrindo outra, exatamente o que ela fez com o par 220/221, só que reaproveitando o
código — a ingestão vai gravar as duas entradas e o **carregamento vai lançar exceção na inicialização
do aplicativo**. A guarda que hoje protege contra base corrompida vira, nesse cenário, uma pane
causada por publicação legítima.

Hoje isso não acontece: a SVRS trocou o código em vez de reaproveitá-lo. O parecer não é "corrija
agora"; é **registrar que a estrutura assume um comportamento da fonte que a fonte não garante**, e
que a promessa de consulta histórica no design está além do que a implementação entrega. Chave
`(código, janela)` com busca por data resolveria os dois de uma vez.

### 3.3 Confirmações — o que auditei e está certo

Registro porque auditoria que só lista defeito não informa onde a confiança é legítima.

- **`PercRedIbs` serve UF e Município; só a CBS tem percentual próprio.** Confirmado: a tabela
  publica exatamente dois campos. `Esfera.percentualOficial` mapeia `CBS → percRedCbs` e
  `UF/MUNICIPIO → percRedIbs`. Está certo, e importa: existe **um** registro em 164 onde os dois
  divergem — `200025`, "Fornecimento dos serviços de educação relacionados ao Prouni", com
  `percRedIbs = 60` e `percRedCbs = 100`. Uma implementação que usasse um só percentual para as três
  esferas passaria em todos os testes menos nesse. O código acerta.
- **Sem nulos disfarçados de zero.** No bruto da SVRS, `PercRedIbs` e `PercRedCbs` não têm um único
  `null` — os 105 zeros são zeros de verdade. O ramo `oficial == null` em `ReductionPercentageRule`
  é, hoje, código morto defensivo. Vale mantê-lo, mas ninguém deve contar com ele para distinguir
  "sem redução" de "não publicado": essa distinção **não existe** na fonte.
- **`011004` — "Concursos e prognósticos"** é a única classificação sob um CST que exige `gRed`
  (`ind_gRed = 1`) cujo percentual oficial é zero. É um caso legítimo e desconfortável: o grupo é
  obrigatório e o percentual esperado é `0`. Quem escrever fixture para as regras de redução deveria
  incluir esse caso — é o que separa "a regra lê a tabela" de "a regra assume que redução é > 0".
  Note-se que a NT já passou por essa discussão: a v1.33 criou UB26-15/UB45-15/UB64-15 para permitir
  `gRed` só com alíquota maior que zero, e a v1.34 **desabilitou as três**.
- **Nenhum código de cClassTrib se repete entre CSTs**, nenhum CST está sem classificação, e todos
  os 18 CSTs e 164 classificações têm `iniVig`. As guardas de ingestão cobrem o que precisam cobrir.
- **`permiteDiferimento` está destilado e nenhuma regra o usa.** É capacidade ociosa, tratada no
  backlog em [`candidatas-rejeicao-pos-b6.md`](./candidatas-rejeicao-pos-b6.md) §4 (lote 3), junto
  com a ressalva de que o nome inverte a leitura da NT.

---

## 4. A Tabela de Combustíveis Monofásicos não é inobtenível

A D-029 trata a Exceção 2 da UB12-10 como dependente de "tabela que não temos", e por isso todo item
com `cProdANP` sai como *não avaliado*.

A própria NT diz onde ela está, na Observação 4 da UB12-10:

> **Observação 4:** Tabela de Combustíveis Sujeitos à Tributação Monofásica publicada na aba
> "Documentos", opção "Diversos" do **Portal Nacional da Nota Fiscal Eletrônica**.

Ou seja: publicada, pública, em local nomeado pela norma. Não verifiquei o formato do arquivo nem se
o download é automatizável — pode muito bem esbarrar no mesmo obstáculo da URL com hash que a INV-1
registrou. Mas a premissa registrada hoje ("não temos") merece ser reformulada para o que de fato se
sabe: *"não foi buscada"*.

Isso importa mais do que parece, por causa de como a cegueira se distribui — ver §5.

---

## 5. O que amarra tudo: as cegueiras declaradas se concentram por perfil de emitente

O produto trata *não avaliado* como desfecho honesto, e está certo. Mas a auditoria mostra que esses
desfechos **não caem de forma difusa sobre o lote** — eles se concentram em populações inteiras:

| Cegueira | Gatilho | Quem fica sem cobertura |
|---|---|---|
| Exceção 2 da UB12-10 (D-029) | qualquer item com `cProdANP` | **distribuidora de combustível: 100% do lote** para a 1115 |
| Ramo de compra governamental (D-030) | documento com `gCompraGov` | **fornecedor de ente público: 100% do lote** para 1034/1046/1063 |
| XSD sem os campos da v1.40 (§1) | `gALCZFMCBS`, `ISUFEmit`, `cIndOp`, `refDFeAnt` | **emitente de ZFM/ALC e de compra governamental**, a partir de 03/08/2026 — e aqui não é cegueira, é acusação |

O padrão é o mesmo nas três linhas, e é o oposto do que a intuição sugere. Um contador que roda o
validador em 500 notas de um cliente qualquer tem cobertura alta. O mesmo contador rodando em 500
notas de uma distribuidora de combustível tem cobertura **zero** na regra principal — e o número
agregado de "não avaliados" no rodapé não vai contar isso para ele de forma acionável.

**Parecer, e é a recomendação de produto mais importante deste documento:** o relatório precisa dizer
a cobertura **em relação ao lote apresentado**, não em absoluto. Algo como *"a 1115 não pôde ser
avaliada em 500 de 500 itens porque todos informam `cProdANP`"* é uma frase que o usuário entende e
sobre a qual ele consegue agir. Um contador de não avaliados no rodapé é uma frase que ele ignora.

É a mesma disciplina de causa-raiz que a camada de rejeição já aplica aos achados — aplicada às
lacunas.

---

## 6. Recomendações, em ordem de urgência

1. **Antes de 03/08/2026** — decidir o que fazer com os XSDs. Trocar a fonte para o Portal Nacional
   é o caminho certo; se não der tempo, declarar a versão do leiaute suportada e não reprovar
   documento por campo desconhecido é o mínimo que evita acusar o inocente. (§1)
2. **Unificar os dois manifestos** antes de implementar qualquer aviso de idade. (§2)
3. **Tratar `fimVig` como exclusivo**, com o par 220/221 como fixture de regressão. (§3.1)
4. **Registrar como decisão consciente** que o modelo temporal é de uma versão por código, e que
   republicação com reuso de código derruba o carregamento. (§3.2)
5. **Reformular a D-029**: a tabela de combustíveis é publicada em local nomeado pela NT; a premissa
   correta é "não foi buscada", não "não existe". (§4)
6. **Relatar cobertura relativa ao lote**, não em absoluto. (§5)

---

## 7. Método e limites

Tudo aqui é verificável com os arquivos do repositório: os XSDs foram percorridos por um walker de
`complexType` que resolve tipos nomeados e inline; a tabela foi medida no destilado
(`src/main/resources/tables/cst-cclasstrib.json`) e conferida contra o bruto
(`docs/pesquisa/dados/cst-cclasstrib-svrs.json`); as datas de produção vieram do cronograma da NT
(p. 4-5) e as exceções, do texto verbatim das regras no PDF.

O que **não** verifiquei, e portanto não afirmo: se o download do pacote do Portal Nacional é
automatizável nos moldes da §5.1 do design; se a SVRS reaproveita códigos de cClassTrib ao
republicar; e o formato da Tabela de Combustíveis Monofásicos. As três são perguntas de investigação,
não de leitura de arquivo.
