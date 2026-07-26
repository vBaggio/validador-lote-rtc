# Convenções

## Código

- Java 21. Records para dados; injeção por construtor; sem DI framework; sem dead code.
- Código em **inglês**; mensagens de UI/erros amigáveis/docs em **pt-BR**.
- Comentários pontuais apenas onde o código não consegue dizer; javadoc em API pública de domínio/application.
- DTO/record de domínio não conhece formato externo (Xerces/CSV/Swing).

## XML (inegociável)

- Todo parser/factory com `FEATURE_SECURE_PROCESSING`; DOCTYPE proibido
  (`disallow-doctype-decl`); `ACCESS_EXTERNAL_DTD`/`ACCESS_EXTERNAL_SCHEMA` vazios;
  StAX com `SUPPORT_DTD=false` e `IS_SUPPORTING_EXTERNAL_ENTITIES=false`.
- `Schema` compilado 1× (thread-safe); `Validator` por documento; validação via SAXSource streaming.
- Mensagens oficiais (`cvc-*`) nunca são reescritas — tradução amigável vem da tabela em
  `resources/messages/xsd-translations.properties`, chaveada por código+campo, locale-independente.

## Fronteiras

- Regra de dependência conforme `architecture.md`, garantida por ArchUnit (`ArchitectureTest`).
- `javax.swing`/`java.awt` só em `presentation/`. EDT-marshalling só no adapter Swing (`UiThread`).

## Git

- Branch por bloco (`bloco/N-nome`), PR por bloco, review antes do merge (merge commit, não squash — preserva 1 commit/task).
- Commits semânticos com escopo do bloco: `feat(b2): ...`, `fix(b3): ...`, `test(b1): ...`, `docs(b0): ...`, `build(b5): ...`.
- Ajuste sequencial no que o último commit (não pushado) entregou → `git commit --amend`, nunca cadeia de fixes.
- Decisão nova → entrada no `decisions.md` no MESMO PR. Decisão-chave → confirmar com o Vinícius antes.

## Erros

- Lote nunca aborta por 1 arquivo: falhas por arquivo viram achado `UNREADABLE` (WARNING).
- Exceções de infraestrutura carregam contexto (arquivo, causa) e mensagem pt-BR quando chegam à UI.
