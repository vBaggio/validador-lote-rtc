# Design — Indicadores oficiais e grupos condicionais do item (Bloco 11)

| | |
|---|---|
| **Status** | Aprovado para implementação |
| **Data** | 04/08/2026 |
| **Escopo** | Preservar os indicadores oficiais da SVRS e prever as rejeições seguras de presença, ausência e valor dos grupos superiores de IBS/CBS |
| **Não inclui** | Cálculo do tributo, regras dos subgrupos monofásicos profundos, alteração do grupo de totais `IBSCBSTot/gMono` ou atualização de schemas |

## 1. Objetivo

O validador já consulta CST e `cClassTrib`, mas o artefato destilado descarta colunas oficiais que
governam grupos opcionais do item. Isso impede prever rejeições quando um grupo é informado sem
permissão ou omitido apesar de obrigatório.

O Bloco 11 torna essas colunas parte do contrato fiscal, extrai a presença dos grupos no escopo
correto e implementa somente as RVs compatíveis com a NT 2025.002 v1.50 e com o XSD `010e_v1.02`
embarcado. Indicador indisponível nunca equivale a `false`: base incompleta é recusada; CST,
`cClassTrib`, data ou metadado do XML indisponível produz **não avaliado**.

## 2. Contrato da tabela oficial

O normalizador passa a exigir e preservar, além das colunas já consumidas:

- por CST: `IndMonofasica`, `IndReducaoBc`, `IndTransferenciaCred`,
  `IndCredPresIbsZfm` e `IndAjusteCompet`;
- por `cClassTrib`: `IndTribRegular`, `IndPermiteCredPres`, `IndEstornoCred`, `IndMonoVal`,
  `IndMonoRetem`, `IndMonoRet`, `IndMonoDif` e `IndPbioDiferenca`.

Todos são booleanos obrigatórios no contrato público atual. Ausência, `null` ou mudança de tipo
invalida a candidata inteira. Os indicadores entram no fingerprint semântico, para que uma mudança
fiscal sem inclusão ou remoção de códigos ainda seja reconhecida como atualização.

O recurso embarcado será regenerado da resposta oficial reproduzível da SVRS e conferido pelo
normalizador. Snapshot instalado por versão anterior, sem as novas colunas, é incompatível e não
pode ficar ativo parcialmente: engine, proveniência e interface recuam juntos para o recurso
embarcado. O arquivo legado pode permanecer no disco, mas não é apresentado nem consumido.

## 3. Leitura XML

`ItemTaxGroup` passa a distinguir, sempre por item e dentro do escopo de `IBSCBS` apropriado:

- `gIBSCBSMono`, `gTransfCred` e `gAjusteCompet`, alternativas superiores do primeiro `choice`;
- `gEstornoCred`, filho direto do invólucro;
- `gTribRegular`, dentro de `gIBSCBS`;
- os valores IBS/CBS de `gAjusteCompet` e `gEstornoCred`;
- `indBemMovelUsado`, no produto.

`FiscalDocument` passa a preservar `tpAmb` e `tpNFCredito`. Homônimos fora desses escopos não
contam; todo estado é reiniciado ao abrir cada `det`. O parser continua com DTD e entidades externas
desabilitados.

## 4. Regras implementadas

| Indicador/condição | RVs | Grupo | Corte seguro |
|---|---|---|---|
| CST `IndMonofasica` | 1151/1116 | `gIBSCBSMono` | 55/65; 1116 só produção desde 04/01/2027, exceto `tpNFDebito=07`; homologação não avaliada |
| CST `IndTransferenciaCred` | 1131/1132 | `gTransfCred` | modelo 55 |
| `cClassTrib.IndTribRegular` | 1065/1114 | `gTribRegular` | 55/65 |
| CST `IndAjusteCompet` | 1169/1170 | `gAjusteCompet` | modelo 55; inclui 1171 para valores não positivos |
| `cClassTrib.IndEstornoCred` | 1172/1173 | `gEstornoCred` | modelo 55; respeita `tpNFDebito=07`; inclui 1174 para valores não positivos |
| `cClassTrib.IndPermiteCredPres` | 1175 | `gCredPresOper` | modelo 55; indicador permite, não exige; exceção `indBemMovelUsado=1` |
| CST `IndCredPresIbsZfm` | 1134/1135 | `gCredPresIBSZFM` | modelo 55 |
| `tpNFCredito=02` | 1158/1159 | `gCredPresIBSZFM` | modelo 55 |

As mensagens oficiais são transcritas da NT, sem paráfrase. O número do item continua estrutural no
`Finding`, fora do texto-base.

RVs citadas pela NT para modelo 65 cujo grupo não existe no `TTribNFCe` do XSD embarcado não recebem
uma interpretação fiscal paralela neste bloco. O XSD continua reportando a estrutura; a camada de
regras limita 1131/1132, 1169 e 1172 ao modelo 55 para não criar uma contradição impossível de
satisfazer.

## 5. Limite monofásico explícito

1151/1116 julgam apenas a presença do grupo superior `gIBSCBSMono`, comum ao XSD atual e à NT. Os
subgrupos profundos ficam fora: o XSD embarcado usa `gMonoPadrao`, `gMonoReten`, `gMonoRet` e
`gMonoDif`, enquanto a NT v1.50 já descreve ramos separados IBS/CBS Ad Rem e Ad Valorem.

Os indicadores profundos são preservados agora para que uma futura base não perca informação, mas
nenhuma rejeição é emitida a partir deles até a atualização e homologação dos schemas curados. A
célula UB99, que mistura diferimento monofásico e `gpBioDiferenca`, também permanece bloqueada.

## 6. Aceite e mutação

O aceite exige:

1. normalizador recusando remoção ou tipo textual em cada indicador novo;
2. fingerprint mudando com o flip isolado de qualquer indicador;
3. snapshot legado íntegro, porém incompatível, recuando integralmente para a base embarcada;
4. extractor provando presença, escopo e reset por item;
5. matriz `0/1 × ausente/presente` de cada par, além de exceções, modelos e vigências;
6. código/data/metadado desconhecido sem rejeição falsa;
7. sonda de mutação por mecanismo fiscal;
8. `./gradlew clean test --console=plain` verde e revisão independente.
