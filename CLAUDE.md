# validador-lote-rtc

Validador desktop offline de lotes de XML NF-e/NFC-e contra os schemas oficiais da
Reforma Tributária do Consumo (IBS/CBS, NT 2025.002). Java 21 + Swing/FlatLaf.

A documentação canônica vive em [`docs/`](./docs/) e é tool-agnostic. Este arquivo só aponta para lá.

## Antes de qualquer tarefa em contexto limpo

Leia [`docs/context.md`](./docs/context.md) — projeto, princípios e índice completo.

## Regras críticas

- **Regra de dependência**: `presentation → application → {domain, infrastructure}`; `infrastructure → domain`; `domain → nada`. `javax.swing`/`java.awt` SÓ em `presentation/`. ArchUnit garante.
- **Parsing XML sempre seguro** (DOCTYPE proibido, sem entidades externas). XML de terceiro é não-confiável.
- **Julgamento fiscal vem de artefato oficial** (schemas da RFB). Nunca criar tabela fiscal hardcoded, nunca reescrever mensagem oficial — traduções ficam em resources.
- **Código em inglês, mensagens/docs em pt-BR.** Comentários enxutos; javadoc onde agrega.
- **1 commit semântico por task**, escopo do bloco (`feat(b2): ...`). Branch por bloco + PR.
- Spec e plano vigentes: [`docs/superpowers/`](./docs/superpowers/). Decisões: [`docs/decisions.md`](./docs/decisions.md).
