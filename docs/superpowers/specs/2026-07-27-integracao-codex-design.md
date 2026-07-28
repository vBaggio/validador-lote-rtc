# Design — Harness e progresso compartilhados entre Claude Code e Codex

| | |
|---|---|
| **Status** | Rascunho para revisão |
| **Data** | 27/07/2026 |
| **Motivação** | O projeto passa a ser desenvolvido por dois agentes; hoje só um deles enxerga as instruções e o método |

---

## 1. O problema

O projeto passa a ser desenvolvido por Claude Code e Codex, alternadamente, no mesmo checkout.
Hoje as instruções do projeto vivem em `CLAUDE.md`, que só o Claude Code lê. O Codex chegaria sem
as regras críticas — camadas, parsing seguro, julgamento fiscal só de artefato oficial, um commit
por task.

O transporte do progresso, esse já funciona: `.superpowers/sdd/` é gitignored, mas os dois agentes
compartilham o mesmo diretório de trabalho, então o ledger e os artefatos das tasks são visíveis
para ambos pelo sistema de arquivos.

### 1.1 A lacuna que não é óbvia

A assimetria séria não é de instruções — é de **método**. O modo como este projeto executa um bloco
não está escrito em lugar nenhum: vive no skill do superpowers e no contexto da sessão em curso.

Uma sessão nova teria o *conteúdo* do ledger sem o *método* que o produziu: não saberia que briefs
recebem adendo quando o plano está errado, que o adendo do controlador já errou duas vezes em
quatro tasks, que achado central de regra se prova por mutação, nem que o implementador deve parar
e perguntar em vez de assumir.

Essa última regra tem resultado medido. Nas duas vezes em que o adendo do controlador afirmou algo
falso sobre o layout oficial — `refNFP` tratado como não datável quando o XSD traz `AAMM` próprio, e
`gCompraGov` descrito como campo de item quando é de documento —, quem perguntou estava certo. Perder
essa regra na troca de agente custa corretude fiscal, não conveniência.

---

## 2. Escopo

**Dentro:** paridade de instruções entre as duas ferramentas e registro do método de trabalho em
documentação tool-agnostic.

**Fora, por decisão:**

- **Versionar `.superpowers/`.** Não é necessário com diretório compartilhado. É a única defesa
  contra um `git clean -fdx` destruir o ledger, mas essa é decisão separada desta integração.
- **Traduzir `.claude/agents/` para um formato de agente do Codex.** O superpowers já resolve o
  dispatch lá; duplicar definição de agente é exatamente a divergência que este desenho evita.
- **Mexer no bloco 6 em andamento** ou no plano vigente.

---

## 3. Princípio: fonte única, adaptadores finos

O desenho estende o que o `CLAUDE.md` já pratica — ele próprio declara que a documentação canônica
vive em `docs/` e é tool-agnostic, e que o arquivo só aponta para lá. A integração aplica a mesma
ideia à segunda ferramenta em vez de inventar estrutura nova.

```
docs/                    canônico, tool-agnostic, versionado
├── context.md           (existe) índice + princípios
├── architecture.md      (existe) camadas e regra de dependência
├── conventions.md       (existe) código, commits, fronteiras
├── testing.md           (existe) estratégia de testes
├── decisions.md         (existe) log ADR-lite
└── workflow.md          NOVO — como se executa trabalho aqui

CLAUDE.md                adaptador Claude Code  ─┐ ambos ≤ 25 linhas,
AGENTS.md                adaptador Codex        ─┘ ambos apontam para docs/

.claude/agents/validador-senior-dev.md
                         invólucro fino → docs/workflow.md
```

**A regra que impede divergência:** o que vale para as duas ferramentas vive em `docs/`; no
adaptador fica só o delta específico da ferramenta. Adaptador que cresce com regra de projeto está
com conteúdo no lugar errado.

### 3.1 Por que dois arquivos e não um symlink

Um symlink `AGENTS.md → CLAUDE.md` garantiria zero divergência, mas os dois adaptadores
**legitimamente diferem**:

- O Codex precisa de `[features] multi_agent = true` em `~/.codex/config.toml` para habilitar
  `spawn_agent`, que o `subagent-driven-development` exige, e tem orientação própria sobre fechar o
  subagente revisor ao fim da revisão.
- O Claude Code não precisa de nenhum dos dois.

Um symlink obrigaria cada ferramenta a carregar instrução de configuração da outra.

### 3.2 O agente custom

`.claude/agents/validador-senior-dev.md` mantém frontmatter e identidade — é o que faz o dispatch
funcionar. O que sai é a repetição das regras do projeto, substituída por referência a
`docs/workflow.md` e `docs/context.md`. Assim o conteúdo deixa de ficar preso a um formato que o
Codex não lê.

---

## 4. `docs/workflow.md` — o método, não as regras do produto

As regras do produto já estão em `conventions.md` e `architecture.md`. Este arquivo registra como se
trabalha. Sete seções:

| # | Seção | Conteúdo |
|---|---|---|
| 1 | Fluxo de bloco | branch por bloco; uma task por vez; implementador → revisor independente → fix loop → ledger → relatório → PR. Nunca `git push` durante as tasks |
| 2 | O ledger | `.superpowers/sdd/progress.md`: formato das entradas e o que é obrigatório registrar — achados, decisões com a razão, débitos, e divergências julgadas **inclusive as recusadas** |
| 3 | Brief e adendo | O brief sai do plano; quando o plano está errado, o controlador escreve um `ADENDO` ao fim do brief, e o adendo governa |
| 4 | Conferir a fonte e perguntar | Todo brief manda o implementador conferir XSD, NT e base embarcada antes de aceitar o adendo, e **parar e perguntar** em vez de assumir |
| 5 | Verificação por mutação | Achado central de regra se prova comentando o bloco, vendo o teste falhar sozinho e restaurando |
| 6 | Handoff de sessão | Entrada `### PARADA` no ledger ao encerrar; ordem de leitura ao começar |
| 7 | Artefatos | `.superpowers/sdd/<plano>/task-N-brief.md`, `-report.md`, `review-<base>..<head>.diff` |

Sobre a §7, um detalhe que confunde quem chega: o **ledger é único e fica na raiz**
(`.superpowers/sdd/progress.md`), cobrindo todos os blocos do projeto, enquanto os **artefatos são
por plano**, em subdiretório. É desvio deliberado do padrão do skill, que prevê um ledger por plano —
aqui o histórico contínuo entre blocos vale mais do que o isolamento, e o `workflow.md` precisa dizer
isso para ninguém "corrigir" a estrutura.

Duas seções carregam **registro histórico honesto**, e é isso que as torna úteis em vez de
decorativas:

- A §3 registra que o adendo do controlador errou em **duas das quatro tasks** do bloco 6.
- A §4 traz os dois casos concretos (`refNFP`, `gCompraGov`) como evidência de que a regra funciona.
- A §5 registra que **quatro sondas** de mutação rodaram no bloco 6, e que **duas revelaram teste
  tautológico** — teste que continuava verde com a implementação apagada.

---

## 5. Handoff entre sessões

Os dois agentes rodam **um de cada vez**, no mesmo diretório. Não há escrita concorrente, então o
protocolo é de entrega, não de coordenação.

**Ao encerrar**, quem estava trabalhando escreve `### PARADA` no ledger com: `HEAD` atual, task e
rodada em curso, o que está commitado e o que não está, pendências e perguntas abertas. A convenção
já existe desde o bloco 1; passa a ser escrita.

**Ao começar**, a ordem de leitura é `docs/context.md` → `docs/workflow.md` → ledger, e então
**conferir `git log` e `git status` contra o que o ledger afirma**. O ledger pode estar
desatualizado; o git não.

**Regra de árvore limpa:** não encerrar sessão com trabalho staged e não commitado. Vem de
experiência do bloco 6, em que um subagente caiu por limite de conta deixando uma task inteira
staged — se a sessão tivesse fechado ali, a seguinte encontraria um estado que o ledger não
descrevia.

---

## 6. Como saber que funcionou

Não é checklist. Abrir uma sessão no Codex e pedir *"retome o bloco 6"*.

**Passa** se ele chegar na Task 9 sabendo o estado, as pendências e o método sem nenhuma explicação
adicional.

**Falha** se precisar perguntar algo que já está decidido — e a pergunta indica exatamente qual
seção do `workflow.md` está faltando.

---

## 7. Riscos

| Risco | Mitigação |
|---|---|
| Os dois adaptadores divergem com o tempo | Regra explícita de que regra de projeto vive em `docs/`; adaptador que cresce é sinal de conteúdo no lugar errado |
| Versões diferentes do superpowers nas duas ferramentas mudam o comportamento dos skills | Fora do controle deste desenho; registrar a versão em uso no ledger quando o comportamento surpreender |
| `git clean -fdx` destrói o ledger e os artefatos | Não mitigado por decisão (§2). Reavaliar se o histórico do ledger passar a ser crítico |
| O `workflow.md` envelhece e passa a descrever um método que ninguém segue | Ele é lido no início de toda sessão; divergência aparece rápido. Atualizar faz parte de fechar um bloco |
