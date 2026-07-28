# validador-lote-rtc

Validador desktop offline de lotes de XML NF-e/NFC-e contra os schemas oficiais da
Reforma Tributária do Consumo (IBS/CBS, NT 2025.002). Java 21 + Swing/FlatLaf.

A documentação canônica vive em [`docs/`](./docs/) e é tool-agnostic. Este arquivo só aponta para lá.

> O projeto é desenvolvido por mais de uma ferramenta. Tudo que vale para todas fica em `docs/`;
> aqui e em [`AGENTS.md`](./AGENTS.md) fica só o que é específico da ferramenta. Adaptador que
> cresce com regra de projeto está com conteúdo no lugar errado.

## Antes de qualquer tarefa em contexto limpo

1. [`docs/context.md`](./docs/context.md) — projeto, princípios e índice completo.
2. [`docs/workflow.md`](./docs/workflow.md) — como se executa trabalho aqui: fluxo de bloco, ledger,
   brief e adendo, verificação por mutação, handoff de sessão.

## Regras críticas

- **Regra de dependência**: `presentation → application → {domain, infrastructure}`; `infrastructure → domain`; `domain → nada`. `javax.swing`/`java.awt` SÓ em `presentation/`. ArchUnit garante.
- **Parsing XML sempre seguro** (DOCTYPE proibido, sem entidades externas). XML de terceiro é não-confiável.
- **Julgamento fiscal vem de artefato oficial** (schemas da RFB, NT, tabela da SVRS embarcada). Nunca criar tabela fiscal hardcoded, nunca reescrever mensagem oficial — traduções ficam em resources.
- **Falso positivo é inaceitável; falso negativo é declarado.** Na dúvida, o desfecho é *não avaliado* — nunca acusação.
- **Código em inglês, mensagens/docs em pt-BR.** Comentários enxutos; javadoc onde agrega.
- **1 commit semântico por task**, escopo do bloco (`feat(b6): ...`). Branch por bloco + PR. Nunca `git push` durante as tasks.
- Spec e plano vigentes: [`docs/superpowers/`](./docs/superpowers/). Decisões: [`docs/decisions.md`](./docs/decisions.md).

## Configuração específica do Claude Code

O agente `validador-senior-dev` (`.claude/agents/`) é o dispatch padrão para trabalho de
desenvolvimento neste repositório.

Não existe `gradle` no PATH — sempre `./gradlew`.
