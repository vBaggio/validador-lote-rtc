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
| atualização de artefatos | retentativa somente para falha transitória, staging sem alterar `current`, sucesso parcial visível, persistência por identidade de canal e preservação da base anterior |
| `SafeHttpsClient` | prazo de conexão, cabeçalhos e corpo; cancelamento no timeout; limite de tamanho aplicado durante streaming |
| `ValidationRuntime`, `MainPresenter` e `ExternalSourcesUseCase` | lease capturada no mesmo gate da admissão, geração/proveniência do resultado, publicação R2 única, validação R1 inalterada, concorrência determinística e nenhum engine misturado |
| `ExternalSourcesUseCase` e `ArtifactUpdateCoordinator` | snapshots monotônicos, listeners tolerantes a falha desde `CHECKING`, confirmação global, admissão atômica validação↔ativação↔recarga, rejeição do executor, fallback de boot latched e recuperação apenas após nova consulta |
| componentes Swing de fontes | atualização na EDT, rodapé compacto com proveniência no tooltip, cards com colunas adaptáveis, diálogo sem minimizar, abertura modal adiada após o dreno, spinner terminal, rolagem/adaptação e fechamento bloqueado durante `APPLYING` |
| aviso de versão do aplicativo | timeout curto e corpo limitado, JSON/release/URL inválidos silenciosos, comparação semântica e deduplicação da versão na sessão |

- Testes **não** asseguram texto integral de mensagem Xerces (localiza por JVM) — asserte `xsdCode`, `field`, `line`.
- Fixtures em `src/test/resources/fixtures/`; semeadas de `docs/calculadora/payloads/`.
- Nomes: `<Classe>Test`, métodos descritivos sem prefixo `test`.
- `@Tag("slow")` fica fora do build padrão; rodar com `./gradlew slowTest`.
- `presentation/` Swing não tem teste automatizado de janelas nativas; presenter e componentes
  isoláveis têm. Escala, corte de conteúdo, ícones e demais aspectos visuais da janela são
  validados manualmente no Windows.
- Atualização de bases não depende de XMLs de lote para rede, retentativa e persistência: esses
  caminhos usam ações/fakes de artefato. A regressão integrada usa stores reais e executor
  controlado para provar R1 → ativação física → R2, inclusive fonte parcial; uma validação já
  admitida conserva R1 e um resultado exibido não muda de geração. Falha posterior a `apply` deve
  preservar `current`, manter R1 e expor `RESTART_REQUIRED`; remover a guarda de admissão, a
  publicação atômica ou a entrega terminal precisa derrubar um teste determinístico. Os testes
  locais do transporte cobrem servidor que envia cabeçalhos e interrompe o corpo; eles não
  substituem o checklist visual manual do Windows.
