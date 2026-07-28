# Auditoria das regras implementadas e da leitura do XML

**Data:** 28/07/2026
**Escopo:** as onze rejeições da camada do bloco 6, o motor que as orquestra, e o `TaxGroupExtractor`
que as alimenta.
**Referências conferidas:** texto verbatim das regras no PDF `NT 2025.002 v1.50` (não na conversão em
Markdown) e o XSD embarcado, percorrido programaticamente.
**Estado auditado:** worktree `bloco/6-camada-rejeicao`, commit `c384f57`.
**Natureza:** pesquisa e parecer. Nenhuma alteração de código.

Esta é uma **segunda leitura independente** da norma. O valor dela não está em desconfiar da
primeira — está em que erro de transcrição fiscal é o tipo de defeito que revisão pela mesma cabeça
não pega.

---

## Sumário do parecer

As onze regras estão **corretas contra o texto da NT**. As constantes de vigência, as exceções
literais e a escolha entre invólucro e grupo interno foram todas conferidas e batem. O código é
cuidadoso num grau incomum: as decisões difíceis estão documentadas no ponto onde foram tomadas, e
as ambiguidades da norma foram resolvidas na direção que não acusa.

Os achados são estes:

| # | Achado | Gravidade | Onde |
|---|---|---|---|
| 1 | A Exceção 1 da UB12-10 **para de funcionar em 01/09/2026** e a regra passa a acusar devolução legítima | **alta, com data certa** | §2 |
| 2 | `total/IBSCBSTot/gCBS` colide com o nome que o extractor usa para abrir a esfera CBS; hoje é inofensivo por acidente de ordenação | média — vira ativo ao implementar W34-10/20 | §4.2 |
| 3 | A dupla "Rejeição: Rejeição:" na mensagem da 1024 **é literal da NT**; parece defeito e não é | informativo — merece comentário no código | §3.4 |
| 4 | A invariante que sustenta o `verifiedItemCount` é frágil por construção; hoje está protegida só por teste | baixa | §5.1 |
| 5 | Toda a leitura do item é feita por nome local sem qualificação de caminho; o XSD atual perdoa, o próximo pode não perdoar | estrutural | §4.1 |

---

## 1. As onze regras, uma a uma

| Código | Regra | Constantes e exceções | Veredito |
|---|---|---|---|
| **1115** | UB12-10 — grupo IBS/CBS ausente | 03/08/2026 (CRT 3), 04/01/2027 (CRT 1/2/4); Exceção 1 (devolução/complementar de nota anterior a 2026), Exceção 2 (`cProdANP`) | **correta hoje**, quebra em 01/09/2026 — §2 |
| **1021** | UB13-20 — `gIBSCBS` informado com `ind_gIBSCBS = 0` | sem exceção na NT | correta |
| **1022** | UB13-30 — `gIBSCBS` ausente com `ind_gIBSCBS = 1` | exceção `tpNFDebito = 07` | correta |
| **1024** | UB14-20 — `cClassTrib` incompatível com o CST | sem exceção | correta — §3.4 |
| **1025** | UB14-25 — `cClassTrib` não permitida no modelo | sem exceção | correta |
| **1033 / 1074 / 1079** | UB26-20 / UB45-20 / UB64-20 — `gRed` ausente | gatilho extra `gCompraGov`; exceção `ind_gIBSCBS = 0` | corretas — §3.2 |
| **1034 / 1046 / 1063** | UB27-10 / UB46-10 / UB65-10 — `pRedAliq` divergente do oficial | ramo `ind_gRed = 0` declarado fora de escopo (D-030) | corretas — §3.3 |

### 1.1 As constantes de vigência da 1115, conferidas no PDF

É a verificação que mais importava, porque um erro aqui liga ou desliga a regra principal do produto
na data errada. O texto da UB12-10 diz, literalmente:

> **Observação 2:** implementação em produção para NFe com data de emissão maior ou igual a
> **03/08/2026** e emitente com CRT 3=Regime Normal.
>
> **Observação 3:** implementação em produção para emitente com CRT com 1=Simples Nacional,
> 2=Simples Nacional, excesso sublimite de receita bruta ou 4=Simples Nacional - Microempreendedor
> Individual – MEI a partir **04/01/2027**.

O código declara exatamente `VIGENCIA_CRT3 = 2026-08-03` e `VIGENCIA_SIMPLES = 2027-01-04`, e trata
CRT fora de {1,2,3,4} como *não avaliado* em vez de escolher um dos dois ramos. Confere.

Vale registrar o que está logo abaixo dessas observações, na p. 7 da NT, porque delimita a promessa
do produto:

> As orientações para CRT=1, CRT=2, CRT=4 e Tributação Monofásica **serão publicadas em NT futura**.

Ou seja: a data de 04/01/2027 é o que se sabe hoje, e a norma já avisa que o detalhamento desses
regimes ainda vai mudar. Não é motivo para alterar nada agora; é motivo para que o aviso de idade da
base (quando existir) trate o Simples como território provisório.

---

## 2. O achado com data certa: a Exceção 1 da UB12-10 quebra em 01/09/2026

Este é o único defeito funcional que encontrei, e ele ainda não aconteceu.

### 2.1 O que a regra faz hoje

A Exceção 1 da UB12-10 protege a devolução e a complementar que referenciam nota anterior a 2026 —
não faz sentido exigir IBS/CBS num item que espelha uma operação de antes da Reforma. O código a
resolve varrendo `document.references()`, que é alimentado pelo grupo **`NFref`**, no nível da nota:

```java
for (ReferencedNote referencia : ctx.document().references()) {
    if (referencia.issuedAt() == null) { semData.add(referencia.form()); }
    else if (referencia.issuedAt().isBefore(CORTE_EXCECAO_1)) { return NaoAplicavel(...); }
}
```

Correto para o leiaute de hoje, e resolvido offline pelo `AAMM` da chave — sem rede, como manda o
projeto.

### 2.2 O que muda

A NT v1.40 traz, no cronograma (p. 5), uma linha com data de produção própria:

> Na devolução, o referenciamento passa a ser realizado **exclusivamente** no grupo
> "DFeReferenciado" (regra de validação VC02-14) — produção **01/09/2026**

E a VC02-14 confirma na Observação 1: *"Fica proibido o referenciamento da Nota na tag `refNFe` na
devolução, devendo referenciar no grupo `DFeReferenciado`"*.

`DFeReferenciado` é **nível de item**, não de nota. Ele não aparece em `NFref` e portanto não entra
em `document.references()` — o `XmlMetadataParser` não o lê, e o `TaxGroupExtractor` também não.

### 2.3 A consequência, em uma frase

A partir de 01/09/2026, uma NF-e de devolução emitida **corretamente** — referenciando no grupo novo,
como a norma passa a exigir — chega à regra com `references()` vazio. A exceção não encontra a
referência de que depende, devolve `null`, e a regra segue o curso normal até **`Rejeitado` 1115**.

É falso positivo, na regra principal do produto, contra o emitente que fez certo. E é pior que o
comum: a nota está certa **porque** obedeceu à mudança que nós não acompanhamos.

### 2.4 Tamanho e forma do problema

Atinge devolução de nota anterior a 2026, emitida a partir de 01/09/2026. A população encolhe com o
tempo, mas não é desprezível no segundo semestre de 2026 — devolução de mercadoria vendida em 2025 é
rotina de varejo e de indústria.

O consolo é que o conserto é o mesmo trabalho que a
[`candidatas-rejeicao-pos-b6.md`](./candidatas-rejeicao-pos-b6.md) já recomenda por outro motivo:
capturar a presença e a chave de `DFeReferenciado` por item (candidatas 321/VC02-14, 1010/VC02-05,
708/VC02-04). O que esta auditoria acrescenta é que **aquilo deixou de ser candidata de backlog e
virou pré-requisito de correção**, com prazo. Sugiro subir do "depois" para o "agora" no backlog, e
tratar o caso intermediário — devolução sem `NFref` **e** sem `DFeReferenciado` capturado — como
*não avaliado*, nunca como rejeição, enquanto a captura não existir.

### 2.5 Um segundo efeito, menor, na mesma mudança

O `VC02-14` também explica por que a leitura ampla da Exceção 1 documentada em D-028 (aceitar
`refNF`/`refNFP`, não só `refNFe`) foi a escolha certa: a norma está migrando o referenciamento, e
qualquer leitura estrita do formato da referência envelhece junto com o leiaute. A decisão registrada
no javadoc se sustenta.

---

## 3. Conferência das exceções literais

### 3.1 `tpNFDebito = 07` na UB13-30 (1022)

A NT põe a exceção **só** na UB13-30, não na UB13-20. O código faz exatamente isso: existe em
`GroupRequiredByCstRule` e não existe em `GroupForbiddenRule`. A assimetria está certa.

Detalhe de ordem que vale elogiar: a exceção é testada **antes** da consulta à tabela. Um item com
`tpNFDebito = 07` cujo CST não está na base sai como *não aplicável* em vez de *não avaliado* — o
desfecho mais informativo dos dois, e o correto, porque a exceção afasta a regra independentemente
do que a tabela diga.

### 3.2 O gatilho extra de `gCompraGov` nas 1033/1074/1079

A UB26-20 tem uma estrutura que é fácil de ler errado:

> Se CST possui indicador que exige o uso de Redução de Alíquota (`ind_gRed = 1`), **ou foi informado
> o grupo de compras governamentais** (`gCompraGov`): […] **Exceção:** a regra não se aplica para CST
> que possui indicador que não permite a informação do IBS/CBS (`ind_gIBSCBS = 0`).

São dois gatilhos alternativos e uma exceção que corta os dois. O código implementa nessa ordem
exata: primeiro a exceção `ind_gIBSCBS = 0`, depois `!exigeReducao && !compraGov → não aplicável`. A
consequência prática está certa — documento de compra governamental exige `gRed` mesmo quando o CST
não exigiria, salvo se o CST proibir o IBS/CBS por completo.

Conferi também o caso de fronteira: documento com `gCompraGov` e item com CST 400 (Isenção,
`ind_gIBSCBS = 0`) cai na exceção e sai como não aplicável. É o desfecho correto e não óbvio.

### 3.3 O ramo `ind_gRed = 0` das 1034/1046/1063, declarado fora de escopo

A UB27-10 tem dois ramos; o segundo depende de `gCompraGov/pRedutor`, que não é capturado. A D-030
resolve mandando esses casos para *não avaliado*. Está certo como decisão.

O que vale registrar é o **tamanho** da renúncia, que o código não diz e o relatório precisa dizer:
`ReductionPercentageRule` devolve *não avaliado* para **todo item de todo documento** que informe
`gCompraGov`, mesmo os que seriam conformes. Para um fornecedor de ente público, isso é o lote
inteiro sem cobertura nas três regras de percentual. Tratado em
[`auditoria-artefatos-oficiais.md`](./auditoria-artefatos-oficiais.md) §5, onde o padrão se repete.

Registro à parte, útil para quem for escrever fixture: a base tem **um** caso em 164 onde o
percentual do IBS difere o da CBS — `200025` (Prouni), 60% contra 100%. `Esfera.percentualOficial`
mapeia certo. É o único teste que separa uma implementação correta de uma que usa um percentual só.

### 3.4 A dupla "Rejeição: Rejeição:" na 1024 é da NT, não do código

`ClassTribCstRule` declara:

```java
private static final String MENSAGEM_OFICIAL =
        "Rejeição: Rejeição: Classificação Tributária do IBS e da CBS incompatível com o CST informado";
```

Parece defeito de transcrição. **Não é.** A coluna "Descrição Erro" da NT, na linha da UB14-20,
traz literalmente `Rejeição: Rejeição: Classificação Tributária do IBS e da CBS incompatível com o
CST informado`. A duplicação é um erro de digitação da norma, e reproduzi-la é o comportamento certo
segundo a regra do projeto de nunca reescrever mensagem oficial.

**Parecer:** merece um comentário de uma linha no código. Sem ele, é praticamente certo que alguém —
uma revisão futura, um linter, uma ferramenta — vá "corrigir" e, sem perceber, quebrar a
correspondência com o texto oficial. Um comentário previne uma regressão silenciosa por um custo de
dez palavras.

---

## 4. A leitura do XML contra o XSD

Percorri o XSD embarcado a partir de `det/imposto/IBSCBS`, resolvendo tipos nomeados, para responder
uma pergunta só: **os nomes em que o `TaxGroupExtractor` se apoia são inequívocos?**

### 4.1 Dentro do item, sim — e a margem é menor do que parece

| Nome | Caminhos sob `det/imposto/IBSCBS` |
|---|---|
| `CST` | 1 — `IBSCBS/CST` |
| `cClassTrib` | 1 — `IBSCBS/cClassTrib` |
| `gIBSCBS` | 1 |
| `gIBSUF` / `gIBSMun` / `gCBS` | 1 cada |
| `gRed` | 3 — uma por esfera |
| `pRedAliq` | 3 — uma por esfera, sempre dentro de `gRed` |
| `gDif` / `gDevTrib` | 3 cada — uma por esfera |

O resultado valida três decisões do extractor:

1. **A guarda `emIbsCbs && cst == null` é suficiente.** `CST` ocorre uma única vez dentro do
   invólucro. O `gTribRegular` usa `CSTReg` e `cClassTribReg`, nomes distintos — não colidem.
2. **A máquina de estados por esfera espelha o schema exatamente.** `gRed`, `gDif` e `gDevTrib`
   existem nas três esferas e em lugar nenhum mais; `pRedAliq` só existe dentro de `gRed`. Não há
   como um `gRed` "fora de esfera" existir num documento válido, e o reset de `esfera` no fechamento
   já cobre o documento inválido.
3. **O candidato `gDif` do backlog é seguro pelo mesmo argumento.** A distribuição é idêntica à do
   `gRed`, o que confirma o custo estimado — uma linha no `switch`.

Confirmação adicional, útil para entender por que as guardas de nulo são deferência e não omissão. A
estrutura de `TTribNFe` é:

```
IBSCBS (minOccurs=0, máx. 1 por det)
 ├─ CST            obrigatório
 ├─ cClassTrib     obrigatório
 ├─ indDoacao      opcional
 ├─ choice (minOccurs=0):  gIBSCBS | gIBSCBSMono | gTransfCred | gAjusteCompet
 ├─ gEstornoCred   opcional
 └─ choice (minOccurs=0):  gCredPresOper | gCredPresIBSZFM
```

Duas leituras saem daí:

- **As guardas de nulo estão certas.** Uma vez presente o invólucro, `CST` e `cClassTrib` são
  obrigatórios no schema. Quando as regras tratam `cst == null` como *não avaliado*, não estão
  deixando buraco: estão cedendo o caso a quem reporta melhor — o validador de schema, com linha,
  coluna e mensagem oficial.
- **O `choice` é um fato de projeto para o backlog.** `gIBSCBS`, `gIBSCBSMono`, `gTransfCred` e
  `gAjusteCompet` são **mutuamente exclusivos**. Quem for implementar as candidatas 1131/1132
  (transferência de crédito) e 1169/1170 (ajuste de competência) precisa saber que a presença de
  qualquer uma delas implica ausência de `gIBSCBS` — e portanto que essas regras não convivem com as
  de redução e diferimento no mesmo item. É informação que muda o desenho da cascata, e que não está
  na NT: está só no schema.

### 4.2 Fora do item, não — `total/IBSCBSTot/gCBS`

O grupo de totais da nota é do tipo `TIBSCBSMonoTot`, e seus filhos diretos são:

```
total/IBSCBSTot/vBCIBSCBS
total/IBSCBSTot/gIBS
total/IBSCBSTot/gCBS      ← mesmo nome local que o abridor de esfera do item
total/IBSCBSTot/gMono
total/IBSCBSTot/gEstornoCred
```

O `TaxGroupExtractor` decide a esfera por nome local puro:

```java
static Esfera of(String element) {
    return switch (element) {
        case "gIBSUF" -> UF;
        case "gIBSMun" -> MUN;
        case "gCBS" -> CBS;
        default -> null;
    };
}
```

Ao passar por `total/IBSCBSTot/gCBS`, o extractor **abre a esfera CBS fora de qualquer item**.

Hoje isso não corrompe nada, e é importante ser preciso sobre o porquê, porque a razão não é uma
invariante — são duas coincidências do leiaute atual:

1. `total` vem depois do último `</det>`, e o item só é emitido no fechamento de `det`. Estado sujo
   depois do último item não chega a virar dado.
2. A subárvore de totais não contém `gRed` nem `pRedAliq`. Só a variável `esfera` é suja; nada é
   lido a partir dela.

Nenhuma das duas está garantida por nada — nem por teste, nem por comentário, nem pelo schema.

**E há um gatilho concreto para isso deixar de ser teórico.** O primeiro lote recomendado em
[`candidatas-rejeicao-pos-b6.md`](./candidatas-rejeicao-pos-b6.md) são as rejeições **1118/1119**,
que exigem justamente detectar a presença de `total/IBSCBSTot`. Implementá-las é colocar o leitor
dentro da subárvore de totais **de propósito** — o momento exato em que uma colisão latente vira
ativa, e o momento em que alguém pode adicionar um booleano de `gCBS` achando que fala do item.

**Parecer:** antes de implementar 1118/1119, qualificar a leitura por contexto. Não precisa de
caminho completo; basta o extractor saber se está dentro de `det` ou dentro de `total` — um booleano,
o mesmo padrão que `emIbsCbs` já usa. O custo é uma linha; o benefício é que a correção deixa de
depender de duas coincidências que ninguém documentou.

### 4.3 A subárvore monofásica não colide — mas é a do leiaute antigo

Percorri os 26 nós de `gIBSCBSMono` e nenhum reusa nome do extractor. A leitura está segura.

Só que o `gIBSCBSMono` embarcado é o **anterior** à reformulação da NT v1.50 — `gMonoPadrao` /
`gMonoReten` / `gMonoRet` / `gMonoDif` chapados, sem a divisão Ad Rem / Ad Valorem que as regras
UB85a a UB104 pressupõem. Ou seja: esta seção da auditoria confirma segurança contra um schema que já
não é o vigente. Ver [`auditoria-artefatos-oficiais.md`](./auditoria-artefatos-oficiais.md) §1 — e
tomar este parágrafo como mais um argumento de que a fonte dos XSDs precisa mudar antes de qualquer
trabalho sério em monofásico.

---

## 5. O motor de regras

A supressão em cascata está bem construída. Vale destacar duas coisas.

### 5.1 A invariante do `verifiedItemCount` é frágil por construção

Quando uma precondição falta, o motor **ainda chama** `binding.rule().evaluate(ctx)` para obter a
mensagem, mas não incrementa `verified`:

```java
} else if (!reported.contains(cause) && report(out, ctx, binding.rule(),
        binding.rule().evaluate(ctx), cause.cause())) {
    reported.add(cause);
}
```

Isso repousa numa invariante real — regra suprimida nunca chega a veredito — mas repousa *só nela*.
Se uma regra futura, em algum caminho, devolvesse `Rejeitado` sem a precondição que declarou exigir,
o motor reportaria a rejeição e **não** contaria o item como verificado. O relatório ficaria
internamente inconsistente: acusação registrada, item contado como não verificado.

O javadoc diz o que protege — `RuleEngineTest#suppressedRuleNeverReachesAVerdict`, que percorre
`BINDINGS` em vez de confiar em leitura à mão. É a escolha certa e é honesta. O registro que faço aqui
é só de rastreabilidade: a corretude do contador está fora do código de produção, num teste. Quem
mexer na cascata precisa saber disso, e o javadoc já diz — o que faltava era alguém de fora confirmar
que a invariante existe mesmo e não é só afirmada. Confirmo: percorri os onze `evaluate` e nenhum
devolve `Rejeitado` num caminho onde a precondição declarada esteja ausente.

### 5.2 A ordem de `Precondition` é a ordem de causa-raiz, e isso está certo

`CST_PRESENT → CST_IN_TABLE → CLASS_TRIB_IN_TABLE`, e `rootCause` varre nessa ordem. Um item sem CST
e sem cClassTrib gera **um** achado — CST ausente — e não dois. Confere com o §4.4 do design.

Um detalhe que parece redundância e não é: `missingPreconditions` marca `CST_IN_TABLE` também quando
o CST é nulo. Nenhuma regra hoje exige `CST_IN_TABLE` sem exigir `CST_PRESENT`, então o roteamento
não muda. Mas o conjunto passa a descrever *o que falta* em vez de *o que roteia*, e uma regra futura
que dependesse só da tabela seria roteada certo. O comentário no código já explica; concordo com a
escolha.

---

## 6. Recomendações

1. **Antes de 01/09/2026** — capturar `DFeReferenciado` no nível do item e usá-lo na Exceção 1 da
   UB12-10. Até lá, devolução sem referência localizável deve sair como *não avaliado*, nunca como
   rejeição. É o único defeito funcional desta auditoria, e tem prazo. (§2)
2. **Antes de implementar 1118/1119** — qualificar a leitura do extractor por contexto (`det` vs
   `total`), para que a colisão de `gCBS` deixe de depender de coincidência de ordenação. (§4.2)
3. **Comentar a dupla "Rejeição: Rejeição:"** na 1024, para que ninguém a "conserte". (§3.4)
4. **Relatar a renúncia de `gCompraGov` de forma relativa ao lote**, não como contador agregado.
   (§3.3)
5. **Não iniciar trabalho de monofásico** enquanto os XSDs forem os pré-v1.50. (§4.3)

---

## 7. O que não auditei

- Os testes. Li o que o javadoc afirma sobre eles e confirmei a invariante correspondente lendo os
  `evaluate`, mas não avaliei cobertura nem qualidade das fixtures.
- As camadas de apresentação e de aplicação. `RootCauseGrouper`, `FindingReclassifier` e o relatório
  ficaram fora — as duas primeiras acabaram de ser tocadas pelas correções do review e mereceriam
  leitura própria, depois que assentarem.
- O `XmlMetadataParser`. Auditei o `TaxGroupExtractor` contra o XSD; a extração de metadados do
  documento (CRT, modelo, `NFref`) merece o mesmo tratamento, e o achado da §2 sugere que é por lá
  que passa a correção mais urgente.
- Desempenho. O extractor abre uma `XMLInputFactory` por arquivo, com justificativa registrada
  ("custo irrisório vs I/O"). Plausível, não medido — e lote de 500 notas é o caso de uso declarado.
