# Indicadores oficiais e grupos condicionais — Plano de implementação

**Objetivo:** cobrir as rejeições seguras de grupos superiores permitidos ou exigidos pelos
indicadores oficiais de CST e `cClassTrib`, sem antecipar o cálculo da Calculadora nem interpretar
o leiaute monofásico incompatível.

**Arquitetura:** a tabela SVRS continua sendo a autoridade dos indicadores. O pipeline destila e
valida o contrato completo; a extração XML só descreve o que foi declarado; regras pequenas e
parametrizadas combinam os dois artefatos por data, modelo e exceções oficiais.

**Stack:** Java 21, StAX seguro, Jackson, JUnit 5, AssertJ e Gradle Wrapper.

## Restrições globais

- preservar a dependência `presentation → application → {domain, infrastructure}`;
- não usar default fiscal para indicador ausente;
- manter mensagem oficial literal e item como dado estrutural;
- limitar qualquer regra à compatibilidade demonstrada entre NT, tabela e XSD;
- seguir RED–GREEN–REFACTOR, revisão independente e um commit semântico por task;
- não tocar no arquivo local não rastreado da pesquisa da Calculadora e não fazer `git push`.

## Task 1 — Contrato completo da tabela e fallback atômico

**Arquivos principais:** `CstEntry`, `ClassTribEntry`, `FiscalTables`, `SvrsTableNormalizer`,
`FiscalTableArtifactStore`, bootstrap/runtime da aplicação, recurso JSON e testes correspondentes.

- [ ] escrever testes vermelhos para cada coluna nova, fingerprint e snapshot legado;
- [ ] preservar os 13 indicadores novos como booleanos obrigatórios;
- [ ] incluir todos no fingerprint semântico;
- [ ] regenerar o recurso embarcado a partir da resposta oficial normalizada;
- [ ] impedir divergência entre tabela realmente carregada, manifesto e interface;
- [ ] rodar testes focados, mutação de campo e suíte completa;
- [ ] revisar e registrar o ledger fiscal.

## Task 2 — Metadados e presença dos grupos no XML

**Arquivos principais:** `FiscalDocument`, `XmlMetadataParser`, `TaxGroupExtractor` e testes.

- [ ] escrever testes vermelhos para `tpAmb`, `tpNFCredito`, `indBemMovelUsado` e cinco grupos;
- [ ] extrair cada presença apenas no escopo oficial;
- [ ] capturar valores de ajuste/estorno sem assumir zero;
- [ ] provar reset entre itens e rejeição de homônimos fora do escopo;
- [ ] rodar testes focados, sonda por `case` e suíte completa;
- [ ] revisar.

## Task 3 — Regras governadas por CST

**RVs:** 1151, 1116, 1131, 1132, 1169, 1170, 1171, 1134, 1135, 1158 e 1159.

- [ ] reler as células UB13-39/40/44/45, UB112-10/20/30 e UB131-20/30/40/50;
- [ ] escrever a matriz vermelha `indicador × presença`, guardas de modelo e exceções;
- [ ] implementar pares parametrizados sem esconder IDs e mensagens individuais;
- [ ] fazer 1116 depender de produção e da data de início, sem acusar homologação;
- [ ] restringir transferência e ajuste a NF-e enquanto persistir o conflito de XSD;
- [ ] executar mutação por mecanismo, testes focados e suíte completa;
- [ ] revisar e registrar o ledger fiscal.

## Task 4 — Regras governadas por cClassTrib

**RVs:** 1065, 1114, 1172, 1173, 1174 e 1175.

- [ ] reler as células UB68-10/11 e UB116-10/20/30 e UB120-20;
- [ ] escrever matrizes vermelhas, incluindo `tpNFDebito=07` e `indBemMovelUsado=1`;
- [ ] implementar presença/ausência e valores positivos sem converter ausência em zero;
- [ ] garantir que `IndPermiteCredPres=true` não torne o grupo obrigatório;
- [ ] executar mutação por mecanismo, testes focados e suíte completa;
- [ ] revisar e registrar o ledger fiscal.

## Task 5 — Auditoria final e handoff

- [ ] conferir as 17 RVs contra a NT e os campos contra a tabela SVRS atual;
- [ ] confirmar que os subgrupos monofásicos profundos não produzem veredito;
- [ ] validar os XMLs sintéticos-base da Calculadora sem confundir `IBSCBSTot/gMono` com item;
- [ ] rodar `./gradlew clean test --console=plain`;
- [ ] atualizar decisões, ledger, progresso e `CURRENT.md`;
- [ ] obter revisão independente final, sem push.
