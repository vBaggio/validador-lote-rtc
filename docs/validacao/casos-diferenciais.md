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
| `r1024-classtrib-incompativel-cst.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, **1024**, 1036, 1026, 1119, 1076, 1080, 1084, 1085, 1091 | **1024 confirmado.** Os demais códigos IBS/CBS são cobertura futura; os códigos cadastrais/assinatura são ruído da fixture. |
| `c1024-classtrib-compativel-cst.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, 1036, 1026, 1119, 1076, 1080, 1084, 1085, 1091 | **Controle aprovado:** não retornou 1024. Os demais códigos são ruído ou cobertura futura. |
| `r1025-classtrib-modelo.xml` | 297, 213, 598, 373, 502, 410, 703, 705, 253, 716, 717, 207, 209, 208, 789, 729, 383, 591, 753, 760, 437, 866, 394, 245, **1025**, 1036, 1026, 1119, 1076, 1080, 1084, 1085, 1091 | **1025 confirmado.** Os códigos NFC-e, IBS/CBS futuros e ruídos cadastrais ficam catalogados separadamente. |
| `c1025-classtrib-permitida-modelo.xml` | 297, 213, 598, 373, 502, 410, 703, 705, 253, 716, 717, 207, 209, 208, 789, 729, 383, 591, 753, 760, 437, 866, 394, 245, 1036, 1026, 1119, 1076, 1080, 1084, 1085, 1091 | **Controle aprovado:** não retornou 1025. Os códigos restantes são ruído, regras de NFC-e fora do bloco ou cobertura IBS/CBS futura. |
| `r1033-reducao-uf-ausente.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, **1033**, 1036, 1026, 1052, 1069, 1119, 1076, 1080, 1084, 1085, 1091 | **1033 confirmado.** Os códigos de cálculo/totais são cobertura futura; os cadastrais/assinatura são ruído da fixture. |
| `c1033-reducao-uf-presente.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, 1041, 1036, 1026, 1052, 1069, 1119, 1076, 1080, 1084, 1085, 1091 | **Controle aprovado:** não retornou 1033. Os códigos restantes são cálculo/total futuro ou ruído da fixture. |
| `r1074-reducao-municipio-ausente.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, 1041, 1036, 1026, **1074**, 1069, 1119, 1076, 1080, 1084, 1085, 1091 | **1074 confirmado.** Os demais códigos são cálculo/total futuro ou ruído da fixture. |
| `c1074-reducao-municipio-presente.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, 1041, 1036, 1026, 1052, 1069, 1119, 1076, 1080, 1084, 1085, 1091 | **Controle aprovado:** não retornou 1074. Os códigos restantes são cálculo/total futuro ou ruído da fixture. |
| `r1079-reducao-cbs-ausente.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, 1041, 1036, 1026, 1052, **1079**, 1119, 1076, 1080, 1084, 1085, 1091 | **1079 confirmado.** Os demais códigos são cálculo/total futuro ou ruído da fixture. |
| `c1079-reducao-cbs-presente.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, 1041, 1036, 1026, 1052, 1069, 1119, 1076, 1080, 1084, 1085, 1091 | **Controle aprovado:** não retornou 1079. Os códigos restantes são cálculo/total futuro ou ruído da fixture. |
| `r1034-percentual-uf-invalido.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, **1034**, 1041, 1036, 1026, 1052, 1069, 1119, 1076, 1080, 1084, 1085, 1091 | **1034 confirmado.** `1041` e os demais códigos são cálculo/total futuro ou ruído da fixture. |
| `c1034-percentual-uf-correto.xml` | 297, 213, 598, 502, 703, 253, 207, 209, 208, 210, 591, 437, 866, 233, 245, 1041, 1036, 1026, 1052, 1069, 1119, 1076, 1080, 1084, 1085, 1091 | **Controle aprovado:** não retornou 1034. Os códigos restantes são cálculo/total futuro ou ruído da fixture. |

#### Catálogo preliminar de códigos observados

| Categoria | Código | Descrição retornada pela SVRS | Tratamento neste gate |
|---|---:|---|---|
| Implementado | 1021 | Grupo IBS/CBS informado indevidamente | Comparar individualmente; divergência é bloqueadora. |
| Implementado | 1022 | Grupo IBS/CBS não informado | Comparar individualmente; divergência é bloqueadora. |
| Implementado | 1033 | Não informado o grupo de redução de alíquota Estadual | Comparar individualmente; divergência é bloqueadora. |
| Implementado | 1074 | Não informado o grupo de redução de alíquota Municipal | Comparar individualmente; divergência é bloqueadora. |
| Implementado | 1079 | Não informado o grupo de redução de alíquota da CBS | Comparar individualmente; divergência é bloqueadora. |
| IBS/CBS fora do bloco | 1026 | Alíquota do IBS da UF inválida | Candidato para estudo posterior; não implementar nesta task. |
| IBS/CBS fora do bloco | 1036 | Alíquota do IBS do Município inválida | Candidato para estudo posterior; não implementar nesta task. |
| IBS/CBS fora do bloco | 1041 | Valor do IBS da UF difere do calculado | Candidato para estudo posterior; não implementar nesta task. |
| IBS/CBS fora do bloco | 1052 | Valor do IBS Municipal difere do calculado | Candidato para estudo posterior; não implementar nesta task. |
| IBS/CBS fora do bloco | 1069 | Valor da CBS difere do calculado | Candidato para estudo posterior; não implementar nesta task. |
| IBS/CBS fora do bloco | 1119 | Total de IBS e CBS não informado | Candidato para estudo posterior; não implementar nesta task. |
| IBS/CBS fora do bloco | 1076 | Total da BC do IBS e da CBS difere da soma dos itens | Candidato para estudo posterior; não implementar nesta task. |
| IBS/CBS fora do bloco | 1080 | Total de IBS UF difere da soma dos itens | Candidato para estudo posterior; não implementar nesta task. |
| IBS/CBS fora do bloco | 1084 | Total de IBS Municipal difere da soma dos itens | Candidato para estudo posterior; não implementar nesta task. |
| IBS/CBS fora do bloco | 1085 | Total do IBS difere da soma do vIBS dos itens | Candidato para estudo posterior; não implementar nesta task. |
| IBS/CBS fora do bloco | 1091 | Total de CBS difere da soma dos itens | Candidato para estudo posterior; não implementar nesta task. |
| Modelo/NFC-e fora do bloco | 373 | Descrição do primeiro item diferente de NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGAÇÃO - SEM VALOR FISCAL | Candidato futuro; não implementar nesta task. |
| Modelo/NFC-e fora do bloco | 410 | UF informada no campo cUF não é atendida pelo Web Service | Candidato futuro; não implementar nesta task. |
| Modelo/NFC-e fora do bloco | 705 | NFC-e com data de entrada/saída | Candidato futuro; não implementar nesta task. |
| Modelo/NFC-e fora do bloco | 716 | NFC-e em operação não destinada a consumidor final | Candidato futuro; não implementar nesta task. |
| Modelo/NFC-e fora do bloco | 717 | NFC-e em operação não presencial | Candidato futuro; não implementar nesta task. |
| Modelo/NFC-e fora do bloco | 789 | NFC-e para destinatário contribuinte de ICMS | Candidato futuro; não implementar nesta task. |
| Modelo/NFC-e fora do bloco | 729 | NFC-e com tag IE do destinatário | Candidato futuro; não implementar nesta task. |
| Modelo/NFC-e fora do bloco | 383 | Item com CSOSN indevido | Candidato futuro; não implementar nesta task. |
| Modelo/NFC-e fora do bloco | 753 | NFC-e com frete | Candidato futuro; não implementar nesta task. |
| Modelo/NFC-e fora do bloco | 760 | NFC-e com dados de cobrança (Fatura, Duplicata) | Candidato futuro; não implementar nesta task. |
| Assinatura/cadastro/geral | 394 | CNPJ da instituição de pagamento inválido [Ocorr:2] | Ruído cadastral da fixture. |
| Assinatura/cadastro/geral | 297 | Assinatura difere do calculado | Ruído esperado da assinatura sintética. |
| Assinatura/cadastro/geral | 213 | CNPJ-Base do Emitente difere do CNPJ-Base do Certificado Digital | Ruído esperado da assinatura sintética. |
| Assinatura/cadastro/geral | 598 | NF-e emitida em ambiente de homologação com Razão Social do destinatário diferente de NF-E EMITIDA EM AMBIENTE DE HOMOLOGAÇÃO - SEM VALOR FISCAL | Ruído da fixture de homologação. |
| Assinatura/cadastro/geral | 502 | Erro na Chave de Acesso - Campo ID não corresponde à concatenação dos campos correspondentes | Ruído da fixture sintética. |
| Assinatura/cadastro/geral | 703 | Data-Hora de Emissão posterior ao horário de recebimento | Ruído da data futura da fixture. |
| Assinatura/cadastro/geral | 253 | Dígito Verificador da chave de acesso composta inválida | Ruído da chave sintética. |
| Assinatura/cadastro/geral | 207 | CNPJ do emitente inválido | Ruído cadastral da fixture. |
| Assinatura/cadastro/geral | 209 | IE do emitente inválida | Ruído cadastral da fixture. |
| Assinatura/cadastro/geral | 208 | CNPJ do destinatário inválido | Ruído cadastral da fixture. |
| Assinatura/cadastro/geral | 210 | IE do destinatário inválida | Ruído cadastral da fixture. |
| Assinatura/cadastro/geral | 591 | Informado CSOSN para emissor que não é do Simples Nacional (CRT diferente de 1 ou 4) | Ruído de coerência da fixture. |
| Assinatura/cadastro/geral | 437 | CNPJ da instituição de pagamento inválido | Ruído cadastral da fixture. |
| Assinatura/cadastro/geral | 866 | Ausência de troco quando o valor dos pagamentos informados for maior que o total da nota | Ruído de valores da fixture. |
| Assinatura/cadastro/geral | 233 | IE do destinatário não cadastrada | Ruído cadastral da fixture. |
| Assinatura/cadastro/geral | 245 | CNPJ Emitente não cadastrado | Ruído cadastral da fixture. |

**Achado confirmado:** o controle `c1022-com-grupo-interno.xml` não retornou 1022/1033/1074/1079.
Logo, a diferença de multiplicidade entre a SVRS e a política local de causa-raiz única está
comprovada: a SVRS reporta 1022+1033+1074+1079 no positivo, enquanto o motor local reporta apenas
1022. Essa decisão precisa ser tomada antes do fechamento do bloco.
