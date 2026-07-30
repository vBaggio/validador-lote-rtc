# Testes

Estratégia **leve e dirigida** (D-010): JUnit 5 + AssertJ; ArchUnit para fronteiras; sem
gate de cobertura — cobertura por criticidade, conferida no review de cada bloco.

| Alvo | Foco |
|---|---|
| `RootCauseGrouper`, `FindingReclassifier` | chaves, ordenação, contagens, severidades |
| `SchemaValidatorEngine` | fixtures reais; coleta total (>1 erro por doc); includes resolvidos; DOCTYPE rejeitado |
| `XmlMetadataParser` | metadados, índice linha→item, malformados |
| `XsdErrorTranslator` | códigos conhecidos → pt-BR; desconhecido → fallback |
| `CsvExporter` | golden: BOM, `;`, escaping, acentos, CRLF |
| `ValidateBatchUseCase` | lote misto, cancelamento, pasta/XML individual, progresso e documentos recusados |
| `MainPresenter` | importação sem validação, estados incrementais, cancelamento e manutenção do lote com view fake |
| atualização de artefatos | retentativa somente para falha transitória, staging sem alterar `current`, sucesso parcial, persistência por identidade de canal e preservação da base anterior |
| `ExternalSourcesUseCase` e `ArtifactUpdateCoordinator` | snapshots monotônicos, listeners tolerantes a falha, confirmação global, admissão atômica validação↔ativação, latch de reinício e recuperação apenas após nova consulta |
| componentes Swing de fontes | atualização na EDT, rodapé e diálogo com o mesmo snapshot, spinner terminal, rolagem/adaptação e fechamento bloqueado durante `APPLYING` |

- Testes **não** asseguram texto integral de mensagem Xerces (localiza por JVM) — asserte `xsdCode`, `field`, `line`.
- Fixtures em `src/test/resources/fixtures/`; semeadas de `docs/calculadora/payloads/`.
- Nomes: `<Classe>Test`, métodos descritivos sem prefixo `test`.
- `@Tag("slow")` fica fora do build padrão; rodar com `./gradlew slowTest`.
- `presentation/` Swing não tem teste automatizado de janelas nativas; presenter e componentes
  isoláveis têm. Escala, corte de conteúdo, ícones e demais aspectos visuais da janela são
  validados manualmente no Windows.
- Atualização de bases não é exercitada com XMLs de lote: os testes usam ações/fakes de artefato e
  confirmam que rede, retentativa e persistência não dependem de documento fiscal. Falha posterior
  a `apply` deve continuar expondo o aviso e `RESTART_REQUIRED`; remover a guarda de admissão ou a
  entrega terminal precisa derrubar um teste determinístico.
