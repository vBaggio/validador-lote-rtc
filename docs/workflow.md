# Como se executa trabalho aqui

Este arquivo registra o **método**. As regras do produto estão em
[`architecture.md`](./architecture.md), [`conventions.md`](./conventions.md) e
[`testing.md`](./testing.md); as decisões, em [`decisions.md`](./decisions.md).

Vale igualmente para qualquer agente ou pessoa que trabalhe no projeto. Ferramenta específica só
aparece em `CLAUDE.md` e `AGENTS.md`, que são adaptadores finos apontando para cá.

## 1. Fluxo de bloco

O trabalho é organizado em **blocos**: um conjunto de tasks com um objetivo comum, uma branch
(`bloco/N-nome`) e um PR ao final.

```
spec aprovada → plano → branch do bloco → task 1 … task N → relatório ao usuário → PR
```

Por task, nesta ordem:

1. **Brief** — o requisito da task, extraído do plano para um arquivo próprio.
2. **Implementador** — escreve o teste que falha, implementa, roda a suíte inteira, commita.
3. **Revisor independente** — quem revisa não é quem implementou. Dois vereditos obrigatórios:
   conformidade com a spec e qualidade da task.
4. **Fix loop** — achados Crítico e Importante voltam ao implementador. Menores viram débito no
   ledger. Cada rodada termina em re-revisão com escopo no diff da correção.
5. **Ledger** — a entrada de conclusão.

**Nunca `git push` durante as tasks.** O push acontece no fechamento do bloco, depois da validação
do usuário. Ajuste sequencial em commit não pushado é `git commit --amend`, nunca uma cadeia de
commits de fix.

**Um commit semântico por task**, com escopo do bloco (`feat(b6): ...`, `test(b6): ...`).

**Decisão nova vai para `docs/decisions.md` no mesmo commit.** Decisão-chave: perguntar antes.

## 2. O ledger é a memória entre sessões

`.superpowers/sdd/progress.md`. É o que sobrevive ao fim de uma sessão, à compactação de contexto e
à troca de agente. Uma entrada por task:

```
Task N (bN): complete (commit <hash>, review clean) — <total> testes.
  <o que foi entregue, em uma ou duas linhas>
  ACHADO: <o que a revisão encontrou e como foi resolvido>
  DÉBITO: <o que ficou aberto e para quando>
```

É obrigatório registrar:

- **achados** da revisão, com o que foi feito a respeito;
- **decisões, com a razão** — a verdadeira, não a mais confortável;
- **débitos**, com o bloco ou task que os herda;
- **divergências julgadas, inclusive as recusadas.** Esta é a que mais importa: sem ela, uma sessão
  nova vê uma escolha estranha e a "corrige", desfazendo trabalho deliberado.

Detalhe de estrutura que confunde quem chega: o **ledger é único e fica na raiz**, cobrindo todos os
blocos, enquanto os **artefatos são por plano**, em `.superpowers/sdd/<plano>/`. É desvio deliberado
do padrão do superpowers, que prevê um ledger por plano — aqui o histórico contínuo entre blocos vale
mais que o isolamento. Não "corrija" isso.

## 3. Brief e adendo

O brief de uma task sai do plano. Quando o plano está desatualizado ou errado — e ele fica, porque é
escrito antes de a realidade aparecer —, quem coordena escreve um **`ADENDO`** ao fim do arquivo do
brief. **O adendo governa** onde colidir com o texto do plano.

O adendo existe porque reescrever o plano a cada descoberta perde o histórico de por que algo mudou.

**Registro honesto, que faz a §4 ser levada a sério:** no bloco 6, o adendo de quem coordenava
**errou em duas das quatro tasks** em que foi usado.

## 4. Conferir a fonte oficial, e parar para perguntar

Todo brief instrui o implementador a **conferir XSD, NT e base embarcada antes de aceitar o que o
adendo afirma**, e a **parar e perguntar** em vez de assumir.

Isto não é formalidade. Os dois casos do bloco 6:

| O que o adendo afirmou | O que a fonte oficial mostrava |
|---|---|
| `refNFP` não é datável | `leiauteNFe_v4.00.xsd:393` traz `<AAMM>` próprio, "AAMM da emissão da NF de produtor" |
| `gCompraGov` é campo de item | `leiauteNFe_v4.00.xsd:499` o declara em `ide`, no mesmo nível de `indPres` e `NFref` — é de documento |

Nos dois casos o implementador conferiu, perguntou, e estava certo. Um agente que assumisse teria
produzido veredito fiscal errado.

A regra do projeto que sustenta isso: **falso positivo é inaceitável; falso negativo é declarado.**
Na dúvida, o desfecho é *não avaliado* — nunca acusação.

## 5. Verificação por mutação

Achado central de regra se prova assim: **comentar o bloco que implementa a regra, rodar o teste, ver
o teste falhar sozinho, restaurar.** Se a suíte continua verde com a implementação apagada, o teste
não protege nada.

No bloco 6 foram quatro sondas, e **duas revelaram teste tautológico**:

- o teste da exceção `ind_gIBSCBS = 0` passava com o bloco inteiro removido, porque dois caminhos
  distintos devolviam o mesmo desfecho e a asserção só olhava o tipo;
- o teste da 1021 afirmava o desfecho para um item construído com a semântica errada, blindando o
  defeito em vez de pegá-lo.

Corolário para escrever teste: **quando o objeto do teste é supressão ou deduplicação, afirme a
contagem, não só o tipo.** Um `allSatisfy` sobre tipo passa com sete duplicatas.

Ao sondar, **restaure o arquivo e confirme `git status` limpo** antes de seguir.

## 6. Handoff de sessão

Os agentes trabalham **um de cada vez**, no mesmo diretório. O protocolo é de entrega, não de
coordenação.

**Ao encerrar**, escreva `### PARADA` no ledger com:

- `HEAD` atual e branch;
- task e rodada em curso;
- o que está commitado e o que não está;
- pendências e perguntas em aberto.

**Ao começar**, leia nesta ordem: [`context.md`](./context.md) → este arquivo → o ledger. Depois
**confira `git log` e `git status` contra o que o ledger afirma**. O ledger pode estar desatualizado;
o git não.

**Regra de árvore limpa:** não encerre sessão com trabalho staged e não commitado. No bloco 6 um
subagente caiu por limite de conta deixando uma task inteira staged — se a sessão tivesse fechado
ali, a seguinte encontraria um estado que o ledger não descrevia.

## 7. Artefatos

```
.superpowers/sdd/
├── progress.md                          o ledger, único, todos os blocos
└── <nome-do-plano>/
    ├── task-N-brief.md                  requisito da task (+ adendos)
    ├── task-N-report.md                 relatório do implementador
    └── review-<base>..<head>.diff       pacote entregue ao revisor
```

Tudo em `.superpowers/` é **gitignored**: local, não versionado, compartilhado entre agentes pelo
sistema de arquivos. Isso significa que `git clean -fdx` destrói o ledger — se acontecer, reconstrua
a partir do `git log`.
