# Validação diferencial contra o validador oficial

Este corpus cobre as onze rejeições previstas no bloco 6. Cada caso positivo é uma NF-e ou
NFC-e válida no XSD oficial e deve produzir **somente** o código indicado no motor local; seu
controle imediato é XSD-válido e não produz achado local. A comparação com a SVRS continua sendo
o gate humano da Task 10 — esta página registra a expectativa, não inventa uma confirmação.

**Endereço do gate:** <https://dfe-portal.svrs.rs.gov.br/NFE/ValidadorNfe>

## Critérios de aceite

1. Nenhum XML que a SVRS aceite pode ser rejeitado pelo validador local.
2. Para as regras cobertas, a rejeição de negócio da SVRS deve coincidir com a expectativa abaixo.
3. Rejeição fora do escopo do bloco deve ser registrada como cobertura futura; não é motivo para
   alterar uma regra sem antes entender a fonte oficial.

## Matriz

| Par / arquivo | Mutação mínima conferida na base oficial | Esperado local | Esperado SVRS | Conferido em |
|---|---|---|---|---|
| Referência | `nfe-valida.xml` canônico | nenhum achado | sem rejeição IBS/CBS | |
| 1115 positivo | `r1115-sem-grupo.xml`: CRT 3, emissão em 03/08/2026, sem `IBSCBS` | 1115 | 1115 | |
| 1115 controle | `c1115-com-grupo.xml`: mesmo cenário, com `IBSCBS` | nenhum achado | sem 1115 | |
| 1021 positivo | `r1021-grupo-indevido.xml`: CST 410 / cClassTrib 410001, com `gIBSCBS` | 1021 | 1021 | |
| 1021 controle | `c1021-sem-grupo-interno.xml`: mesmo CST/classificação, sem `gIBSCBS` | nenhum achado | sem 1021 | |
| 1022 positivo | `r1022-grupo-obrigatorio-ausente.xml`: CST 200 / cClassTrib 200030, sem `gIBSCBS` | 1022 | 1022 | |
| 1022 controle | `c1022-com-grupo-interno.xml`: mesmo CST/classificação, com grupo e reduções | nenhum achado | sem 1022 | |
| 1024 positivo | `r1024-classtrib-incompativel-cst.xml`: CST 000 / cClassTrib 200030 | 1024 | 1024 | |
| 1024 controle | `c1024-classtrib-compativel-cst.xml`: CST 000 / cClassTrib 000001 | nenhum achado | sem 1024 | |
| 1025 positivo | `r1025-classtrib-modelo.xml`: NFC-e 65 / cClassTrib 000003, vedada para NFC-e | 1025 | 1025 | |
| 1025 controle | `c1025-classtrib-permitida-modelo.xml`: NFC-e 65 / cClassTrib 000001 | nenhum achado | sem 1025 | |
| 1033 positivo | `r1033-reducao-uf-ausente.xml`: CST 200 / 200030, sem `gRed` da UF | 1033 | 1033 | |
| 1033 controle | `c1033-reducao-uf-presente.xml`: mesmo caso, com `gRed` da UF = 60% | nenhum achado | sem 1033 | |
| 1074 positivo | `r1074-reducao-municipio-ausente.xml`: CST 200 / 200030, sem `gRed` municipal | 1074 | 1074 | |
| 1074 controle | `c1074-reducao-municipio-presente.xml`: mesmo caso, com `gRed` municipal = 60% | nenhum achado | sem 1074 | |
| 1079 positivo | `r1079-reducao-cbs-ausente.xml`: CST 200 / 200030, sem `gRed` da CBS | 1079 | 1079 | |
| 1079 controle | `c1079-reducao-cbs-presente.xml`: mesmo caso, com `gRed` da CBS = 60% | nenhum achado | sem 1079 | |
| 1034 positivo | `r1034-percentual-uf-invalido.xml`: redução UF = 59,99%; demais = 60% | 1034 | 1034 | |
| 1034 controle | `c1034-percentual-uf-correto.xml`: redução UF = 60% | nenhum achado | sem 1034 | |
| 1046 positivo | `r1046-percentual-municipio-invalido.xml`: redução municipal = 59,99%; demais = 60% | 1046 | 1046 | |
| 1046 controle | `c1046-percentual-municipio-correto.xml`: redução municipal = 60% | nenhum achado | sem 1046 | |
| 1063 positivo | `r1063-percentual-cbs-invalido.xml`: redução CBS = 59,99%; demais = 60% | 1063 | 1063 | |
| 1063 controle | `c1063-percentual-cbs-correto.xml`: redução CBS = 60% | nenhum achado | sem 1063 | |

As escolhas de tabela relevantes foram conferidas no artefato embarcado
`src/main/resources/tables/cst-cclasstrib.json`: CST 410 não admite `gIBSCBS` e a classificação
410001 é permitida em NF-e; CST 200 exige grupo e redução, e a classificação 200030 é permitida
em NF-e/NFC-e com 60% de redução de IBS e CBS. Isso evita as duas armadilhas do plano original:
CST 400 não isola a 1021 em NF-e e CST 011 não é permitido nos dois modelos.

## Como executar o gate humano

As assinaturas das fixtures são sintéticas: elas tornam o XML estruturalmente válido, mas não
substituem a assinatura digital aceita pelo autorizador. Para cada par, validar uma versão assinada
equivalente na SVRS, anotar na coluna final os códigos da seção de regras de negócio e registrar
qualquer divergência abaixo. A Task 10 só termina após essa conferência.

## Divergências encontradas

### Evidências já executadas

As duas primeiras execuções foram feitas com as fixtures sintéticas. O parser e o schema foram
aceitos pela SVRS; a assinatura inválida e os códigos cadastrais são ruído conhecido deste corpus.

| Arquivo | Códigos retornados pela SVRS | Leitura para o bloco 6 |
|---|---|---|
| `c1021-sem-grupo-interno.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, 1119 | **Controle aprovado:** não retornou 1021. `1119` fica fora do escopo atual. |
| `r1021-grupo-indevido.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, **1021**, 1036, 1026, 1119, 1076, 1080, 1084, 1085, 1091 | **1021 confirmado.** Os demais códigos são cobertura futura ou ruído. |
| `r1022-grupo-obrigatorio-ausente.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, **1022**, **1033**, **1074**, **1079**, 1119 | **1022 confirmado, mas há divergência:** a SVRS também acusa as três reduções, enquanto o motor local suprime essas regras quando falta `gIBSCBS`. |
| `c1022-com-grupo-interno.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, 1041, 1036, 1026, 1052, 1069, 1119, 1076, 1080, 1084, 1085, 1091 | **Controle aprovado para o conjunto:** não retornou 1022/1033/1074/1079. Os códigos IBS/CBS restantes são cálculos/totais fora do escopo. |

#### Catálogo preliminar de códigos observados

| Categoria | Códigos | Tratamento neste gate |
|---|---|---|
| Implementados no bloco 6 | 1021, 1022, 1033, 1074, 1079 | Comparar individualmente com o resultado local; divergências são bloqueadoras. |
| IBS/CBS fora do bloco atual | 1026, 1036, 1041, 1052, 1069, 1119, 1076, 1080, 1084, 1085, 1091 | Registrar como candidatos para estudo posterior; não implementar nesta task. |
| Assinatura, cadastro e consistência geral | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245 | Ruído esperado das fixtures sintéticas; não comparar com a camada de rejeições IBS/CBS. |

**Achado confirmado:** o controle `c1022-com-grupo-interno.xml` não retornou 1022/1033/1074/1079.
Logo, a diferença de multiplicidade entre a SVRS e a política local de causa-raiz única está
comprovada: a SVRS reporta 1022+1033+1074+1079 no positivo, enquanto o motor local reporta apenas
1022. Essa decisão precisa ser tomada antes do fechamento do bloco.
