# Decisões

Log ADR-lite. Cada entrada: **Decisão**, contexto curto e consequência. Mais recentes no topo.
Template no fim. Decisões D-001..D-014 nasceram no brainstorm de 26/07/2026 (spec
[`superpowers/specs/2026-07-26-validador-lote-rtc-design.md`](./superpowers/specs/2026-07-26-validador-lote-rtc-design.md)).

## D-018 — Índice linha→item não resolve XML minificado (26/07/2026)
`ItemLineIndex` mapeia achado→item por **linha** (faixa `[linha de abertura, linha de
fechamento]` de cada `det`). Num XML minificado numa única linha — formato que vários ERPs
emitem —, abertura e fechamento de todo `det` colapsam para a mesma linha do documento
inteiro, e qualquer achado (inclusive um no grupo `IBSCBSTot`, que fica em `<total>`, fora de
qualquer `det`) é rotulado como pertencente ao primeiro item. É o mesmo sintoma que a faixa
por linha corrigiu para XML formatado (achado atribuído ao item errado), sobrevivendo aqui
por outra via: já não é campo capturado errado, é granularidade de linha insuficiente.
Resolver exigiria indexar por coluna (offset dentro da linha), não por linha — fora do escopo
do v0. Consequência aceita: relatório de XML minificado com múltiplos itens pode citar o
item 1 para um erro que é de outro item, ou de fora de qualquer item.

## D-017 — DOCTYPE é rejeitado, não apenas ignorado (26/07/2026)
XML de terceiro com declaração `<!DOCTYPE>` vira `UnreadableXmlException` em vez de ser
lido normalmente. Fato técnico não-óbvio que motiva a decisão: `XMLInputFactory.SUPPORT_DTD
= false` **não rejeita** o DOCTYPE — apenas deixa de processar as declarações internas. O
leitor ainda emite o evento `XMLStreamConstants.DTD` e segue lendo o documento até o fim.
Sem uma rejeição explícita no laço de eventos, um arquivo com DOCTYPE era parseado como se
nada houvesse; as duas propriedades de segurança sozinhas não fecham o caso. Por isso
`XmlMetadataParser` lança ao ver o evento `DTD`.
Divergência deliberada em relação à Calculadora oficial da RFB, que aceita DOCTYPE: aqui a
entrada é um lote de arquivos de origem arbitrária, e nenhuma NF-e legítima precisa de DTD.
Consequência aceita: um XML válido que traga DOCTYPE decorativo é reportado como ilegível
em vez de validado.

## D-016 — `enviNFe` multi-nota: metadados nulos no v0 (26/07/2026)
Lote `enviNFe` com mais de um `infNFe` é aceito e validado normalmente contra o schema, mas
o v0 não desmembra o lote em documentos individuais. Como `accessKey`, `emitterCnpj`,
`documentNumber`, `model` e `issueDate` só poderiam vir da **primeira** nota, atribuí-los ao
arquivo inteiro produziria relatório enganoso (chave da nota 1 num achado da nota 3). Os
cinco campos ficam nulos — nulo é melhor que errado. `rootElement` continua preenchido e o
índice linha→item permanece válido (as faixas estão em ordem de documento), perdendo-se
apenas de qual nota o item é. Desmembramento fica para quando houver demanda real.

## D-015 — ArchUnit: `allowEmptyShould(true)` por regra, não global (26/07/2026)
Desde o ArchUnit 1.3.0, regras `noClasses()/classes()...that()` falham por padrão quando
nenhuma classe casa com o `that()` (ex.: pacote `application`/`presentation` ainda
inexistente). Isso contradiz a intenção de D-006 (regras "armadas" para camadas futuras,
passando vazias até existirem). Primeira tentativa desligou o default globalmente via
`archRule.failOnEmptyShould=false` em `src/test/resources/archunit.properties` — descartada
em revisão: uma config global mascara silenciosamente qualquer regra que deixe de casar
classes no futuro (renomeação de pacote, refactor incompleto), inclusive as fundamentais
como `domainDependsOnNothing`. Trocado por `.allowEmptyShould(true)` encadeado apenas nas
duas regras hoje vazias (`applicationDoesNotSeePresentation`,
`presentationDoesNotSeeInfrastructure`), com comentário no código marcando a permissão como
temporária. Risco aceito: essas duas regras não acusam nada até os pacotes existirem — por
isso a permissão fica restrita a elas, e as demais permanecem estritas. Ação futura: remover
as duas chamadas `.allowEmptyShould(true)` quando `application` e `presentation` existirem,
ao final do bloco da interface.

## D-014 — Relatório narrativo por IA opcional (BYOK) na v1 (26/07/2026)
Última entrega da v1: botão pós-análise, API key do próprio usuário, envia só o relatório
agregado (nunca XMLs), narra achados determinísticos sem julgar tributo. Ideia registrada:
onboarding de primeiro boot pode unificar credenciais e downloads iniciais.

## D-013 — Assinatura ausente = achado próprio com toggle (26/07/2026)
`SIGNATURE_MISSING` com toggle "XMLs pré-emissão" (default ligado → INFO; desligado →
REJECTION). Público valida antes de emitir; sem isso, 100% dos docs teriam ruído.

## D-012 — PENDENTE: fonte do motor na v1 (26/07/2026)
Embutir × download no 1º uso. Decidir no início da v1. Fato: pacote oficial **sem licença**
(ver [`calculadora/licenca-calculadora.txt`](./calculadora/licenca-calculadora.txt)).

## D-011 — Fluxo git: branch por bloco + PR (26/07/2026)
1 commit semântico por task; review por bloco; merge commit (preserva histórico de tasks).

## D-010 — Testes leves e dirigidos (26/07/2026)
Sem gate de cobertura; criticidade guia (ver `testing.md`). MVP de validade curta.

## D-009 — Matrix 3 SOs com Windows-gate (26/07/2026)
Release exige `.msi` Windows; Linux/macOS `continue-on-error`, anexam se passarem.

## D-008 — jlink enxuto no v0 (26/07/2026)
Sem Spring no v0 → módulos mínimos (~50–80 MB de instalador). Na v1, com motor embarcado,
`ALL-MODULE-PATH`. Fallback imediato se faltar módulo em runtime.

## D-007 — Swing + FlatLaf confirmada (26/07/2026)
Re-julgada (não herdada): runtime dentro do JDK, menor risco de empacotamento. Única
vantagem real do JavaFX (TreeTableView) neutralizada pelo mestre-detalhe.

## D-006 — Camadas + MVP; sem CLI no v0; sem DI framework (26/07/2026)
Regra de dependência com ArchUnit; views atrás de interface; `ProgressListener` neutro;
frontend trocável sem tocar o núcleo. Sem interfaces-porta especulativas.

## D-005 — Schemas oficiais extraídos do JAR da Calculadora, commitados (26/07/2026)
Alinhamento versão motor↔schema garantido; espelho GitHub provou-se desatualizado;
`updateSchemas` re-extrai. Proveniência em `schemas-version.properties`. **Estudo futuro:**
automação da atualização (verificação de nova base pelo app, opt-out).

Fidelidade byte-a-byte é parte da decisão: `.gitattributes` marca `*.xsd -text` para que a
normalização de fim de linha não altere o artefato normativo (4 dos 14 XSDs são publicados
pela RFB com CRLF). Os 14 arquivos commitados foram verificados por SHA-256 contra as
entradas do JAR oficial. O cabeçalho de `schemas-version.properties` aponta para
`./gradlew updateSchemas` porque a `sourceUrl` registrada é uma URL pré-assinada que expira.

## D-004 — Entrega faseada (26/07/2026)
v0 = estrutura local (XSD, coleta total); v1 = valores via `regime-geral`. Motivo: endpoint
oficial de validação é XSD-only/fail-fast (descoberta 26/07); estrutura local entrega mais,
mais cedo, sem processo filho.

## D-003 — GPL-3.0 (26/07/2026)

## D-002 — Repo público vBaggio/validador-lote-rtc; Actions; Releases (26/07/2026)

## D-001 — Gradle + Java 21 (26/07/2026)

---

**Template:**
```
## D-0XX — Título curto (DD/MM/AAAA)
Decisão em 1-3 frases. Contexto essencial. Consequência/risco aceito.
```
