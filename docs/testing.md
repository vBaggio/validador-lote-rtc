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
| `ValidateBatchUseCase` | lote misto, cancelamento, pasta vazia, progresso |
| `MainPresenter` | transições de estado com view fake |

- Testes **não** asseguram texto integral de mensagem Xerces (localiza por JVM) — asserte `xsdCode`, `field`, `line`.
- Fixtures em `src/test/resources/fixtures/`; semeadas de `docs/calculadora/payloads/`.
- Nomes: `<Classe>Test`, métodos descritivos sem prefixo `test`.
- `@Tag("slow")` fica fora do build padrão; rodar com `./gradlew slowTest`.
- `presentation/` Swing (views) sem teste automatizado; presenter tem.
