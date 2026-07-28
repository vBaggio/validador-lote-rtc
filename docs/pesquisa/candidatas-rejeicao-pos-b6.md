# Candidatas a rejeição após o Bloco 6

**Data do estudo:** 28/07/2026
**Escopo:** regras do grupo UB da NT 2025.002 v1.50 aplicáveis à NF-e/NFC-e
(modelos 55/65), excluídas as regras exclusivas de Imposto Seletivo e eventos.
**Resultado:** backlog de estudo; este documento não autoriza nem implementa regra, extração ou
alteração de tabela.

## Resultado executivo

A NT contém 167 regras UB. Excluídas as 10 regras exclusivas de Imposto Seletivo
(`UB01-10/20/30`, `UB02-10`, `UB03-10`, `UB05-10`, `UB06-10`, `UB07-10`, `UB08-10` e
`UB11-10`), restam **157 regras de IBS/CBS**: 85 de presença, 17 de tabela, 48 de cálculo e 7
outras. O Bloco 6 já cobre 11; este estudo tria as outras 146. O fechamento aritmético da triagem
é: `11 entregues + 101 futuras sem cálculo + 45 futuras de cálculo = 157`.

Sem telemetria ou frequência observada, todo rótulo de valor deste documento é **hipótese**. A
priorização usa alcance normativo e reaproveitamento de dados como aproximações, não como prova de
incidência real.

### Shortlist priorizada

| Prioridade | Códigos de rejeição / IDs das regras | Por que entra agora | Pré-requisito limitado |
|---|---|---|---|
| 1 | `1029 / UB22-10`, `1030 / UB22-20`, `1044 / UB40-10`, `1083 / UB40-20`, `1061 / UB59-10`, `1090 / UB59-20` | Um indicador CST já embarcado governa o mesmo par exige/veda nas três esferas; sem exceções. Valor alto **como hipótese** por cobrir seis rejeições com um mecanismo. | Capturar, com escopo de pai, a presença de `gDif` em UF, Município e CBS. |
| 2 | `1032 / UB26-10`, `1007 / UB45-10`, `1028 / UB64-10` | Fecha o lado “informado indevidamente” das regras de redução já existentes. Indicador, grupos e `pRedAliq` já estão disponíveis; a exceção oficial é completa. | Capturar `gCompraGov/pRedutor` em vez de inferi-lo só pela presença de `gCompraGov`. |
| 3 | `1111 / UB24-10`, `1112 / UB43-10`, `1187 / UB62-10` | Veda presença de `gDevTrib` sem depender de tabela: 1111/1112 cobrem 55/65 e 1187 cobre 65. Sem exceções; o alcance é médio–alto **como hipótese** após corrigir a aplicabilidade. | Capturar as três presenças com escopo de pai e aplicar a guarda de modelo por regra. |
| 4 | `1141 / UB82a-10`, `1144 / UB82a-30` | `hasCompraGov` já é extraído; falta apenas a presença do grupo de item. A exceção da 1141 usa `ind_gIBSCBS`, já embarcado. | Capturar `gTribCompraGov` e limitar ao modelo 55. |

A shortlist revisada reúne **14 rejeições em quatro mecanismos**.

As prioridades são independentes de volume de código. Se a promoção de um indicador da SVRS
exigir alterar a ingestão, ela deve manter a proveniência, a vigência por data do fato gerador e as
guardas de estrutura já existentes. “Presente no XML” não basta para recomendar: fonte da condição,
exceções e ativação também precisam estar fechadas.

Monofasia superior (1151/1116), tributação regular (1065/1114) e todo o mecanismo de transferência
(1131/1132/1133/1168/1129) ficam **depois**. Monofasia, tributação regular e o par 1131/1132
dependem de indicadores existentes somente no destilado de pesquisa S-L, sem snapshot oficial
bruto local nem geração reproduzível acompanhada de manifesto; o derivado é indício útil, mas não
fonte apta a sustentar acusação. As três `UB106` não usam o indicador, porém permanecem no mesmo
mecanismo adiado. Transferência ainda tem conflito adicional entre a aplicabilidade 55/65 de
`UB13-44/45` e o XSD da NFC-e.

## Fontes e método

1. O universo veio do Markdown da NT local
   `tmp/NT_2025.002_v1.50_RTC_NF-e_IBS_CBS_IS.md`, SHA-256
   `1a4f47fe7fcee0cf9afbf51629df9818b1fe44cc2e51cde554462f476abf963f`, conferido contra o PDF
   local, SHA-256 `cfc11a45b6ce9b491c2abf11d01865084b7a9dbcb2904e5414ae16e8e31099e3`.
2. O catálogo auxiliar `.superpowers/sdd/nt-regras-catalogo.md`, SHA-256
   `c3702159e823e1724e9e5b738f6dee5d213922a2d9c159444a9888846d4dcb4b`, foi usado para a
   enumeração e as categorias; condição, exceções, datas e pares código/ID foram reconferidos na
   NT.
3. A disponibilidade real foi conferida em `FiscalDocument`, `XmlMetadataParser`,
   `TaxGroupExtractor.ItemTaxGroup`, `CstEntry`, `ClassTribEntry`, `FiscalTables`, no JSON
   embarcado e no artefato de pesquisa da SVRS.
4. Regras foram agrupadas na matriz somente quando compartilham mecanismo, dados e risco. Cada
   código de rejeição aparece antes do ID da regra, no formato **`código / ID`**. Os identificadores
   peculiares do PDF, como `UB84a-10 (campo ref. UB86-10)`, foram preservados; não são códigos de
   rejeição.
5. Recomendação **agora** exige fonte oficial local verificável e todas as exceções/ativações
   disponíveis. Um hash de derivado sem snapshot oficial ou processo reproduzível com manifesto
   não satisfaz esse gate. **Depois** indica lacuna de artefato, menor valor provável ou risco ainda
   desproporcional. **Não recomendar** significa não duplicar o XSD ou não reimplementar cálculo
   fiscal nesta camada.

O catálogo registra uma limitação importante do próprio PDF: algumas células do bloco monofásico
misturam identificador de campo e regra, e `UB99-10/20` carrega duas descrições na mesma célula.
Esse bloco precisa de normalização a partir do PDF oficial antes de qualquer implementação; a
triagem não inventa a separação que o artefato não fornece com segurança.

## Inventário de dados

### Legenda de disponibilidade

- **E — já extraído:** disponível hoje em `FiscalDocument` ou `ItemTaxGroup`.
- **X — presente no XML, mas não extraído:** captura StAX local é possível, porém ainda não existe
  contrato no domínio/extrator.
- **S-E — presente no artefato SVRS local e embarcado:** o runtime já carrega o dado.
- **S-L — indício no destilado SVRS de pesquisa:** existe no arquivo local de pesquisa, mas não
  está embarcado e sua fidelidade não pode ser auditada offline sem o bruto oficial ou uma geração
  reproduzível com manifesto. Sozinho, S-L não autoriza acusação.
- **T — nova tabela oficial:** o dado normativo não existe em artefato local verificável; não pode
  ser hardcoded.
- **C — cálculo:** exige recomposição, tolerância, arredondamento ou cruzamento entre itens.
- **Q — consulta externa:** exigiria rede/BD. Nenhuma das 157 regras UB selecionadas exige consulta
  a BD de NF-e; regras desse tipo estão fora do grupo UB. Atualizar uma tabela oficial em tarefa de
  manutenção não é consulta externa em runtime.

### O que existe hoje

| Origem | Dados verificados |
|---|---|
| **E — documento** | chave, CNPJ emitente, número, data de emissão, modelo, raiz, CRT, `finNFe`, `tpNFDebito`, presença de `gCompraGov` e referências de nota com competência quando datável. |
| **E — item** | `nItem`, presença de `IBSCBS` e `gIBSCBS`, `CST`, `cClassTrib`, `cProdANP`, presença de `gRed` e `pRedAliq` nas três esferas. |
| **S-E — runtime** | 18 CSTs com `IndExigeTrib`, `IndReducaoAliq`, `IndDiferimento` e vigência; 164 classificações com vínculo CST, modelo 55/65, percentuais de redução e vigência. Manifesto: IT 2025.002 v1.60, publicação 23/06/2026, extração 27/07/2026. |
| **S-L — pesquisa** | JSON de SHA-256 `af49785c60cc6c07c7d3e3845a5278bf41e0d46c258ab9d5a4093b9b91af6196`: 18 CSTs, 164 classificações e 4.628 ocorrências de anexos. Além do runtime, contém `IndMonofasica`, `IndReducaoBc`, `IndTransferenciaCred`, `IndCredPresIbsZfm`, `IndAjusteCompet`, `IndTribRegular`, `IndPermiteCredPres`, tipo de alíquota, número de anexo e NCM/NBS. Esses campos são evidência de pesquisa, não fonte apta a rejeição. |
| **X — captura local simples** | `tpNFCredito`, `tpAmb`, `gCompraGov/pRedutor` e presença dos grupos `gIBSCBSMono`, `gTransfCred`, `gDif`, `gDevTrib`, `gTribRegular`, `gTribCompraGov`, `gALCZFMCBS`, `gAjusteCompet`, `gEstornoCred`, `gCredPresOper` e `gCredPresIBSZFM`, além de seus campos internos. |

O JSON bruto de cerca de 4,4 MB citado na pesquisa anterior **não está versionado nem disponível
como arquivo local**. O hash do arquivo de 420 KB prova somente a integridade desse derivado, não
sua fidelidade à fonte oficial. Até existir snapshot oficial local ou geração reproduzível com
manifesto, mesmo os campos presentes nele são S-L e só sustentam estudo; para acusação, a promoção
depende de fechar essa lacuna. Os 27 indicadores de `cClassTrib` descritos pela pesquisa, mas
ausentes desse JSON — por exemplo `ind_gMonoPadrao`, `ind_gMonoReten`, `ind_gMonoRet`,
`ind_gMonoDif`, `ind_gpBioDiferenca` e `ind_gEstornoCred` — são classificados como **T**, não como
disponíveis.

Também não estão disponíveis localmente as tabelas oficiais de combustível monofásico, índice de
mistura, valores de referência/alíquotas por produto, alíquotas futuras da CBS e códigos de crédito
presumido. Os anexos NCM/NBS existentes não resolvem essas lacunas e sua ausência ainda tem
semântica ambígua; não sustentam acusação por si sós.

## Matriz abrangente — regras já entregues

Estas 11 regras fecham o universo, mas não são candidatas a novo trabalho.

| Código / ID | Regra em uma frase | Dados necessários | Disponibilidade | Exceções completas? | Esforço | Risco de falso positivo | Valor provável | Recomendação |
|---|---|---|---|---|---|---|---|---|
| `1115 / UB12-10` | Exige o invólucro `IBSCBS` por item conforme CRT e data. | CRT, data, `IBSCBS`, `finNFe`, referências e combustível. | E; tabela de combustível ausente vira não avaliado. | Sim: devolução/complementar com referência anterior a 2026; combustível monofásico; datas de produção por CRT usadas no Bloco 6. | — | Controlado pelo não avaliado na exceção sem tabela. | Alto (hipótese). | — **já entregue**. |
| `1021 / UB13-20`; `1022 / UB13-30` | Veda ou exige `gIBSCBS` segundo `ind_gIBSCBS`. | CST, `gIBSCBS`, data, `tpNFDebito`. | E + S-E. | Sim: a 1022 não se aplica a `tpNFDebito=07`; a 1021 não tem exceção. | — | Baixo após separar `IBSCBS` de `gIBSCBS`. | Alto (hipótese). | — **já entregues**. |
| `1024 / UB14-20`; `1025 / UB14-25` | Confere vínculo `cClassTrib × CST` e modelo permitido. | CST, `cClassTrib`, modelo e data. | E + S-E. | Sim; sem exceções na NT. Código desconhecido não vira acusação. | — | Baixo para registros encontrados; ausência fica não avaliada. | Alto (hipótese). | — **já entregues**. |
| `1033 / UB26-20`; `1074 / UB45-20`; `1079 / UB64-20` | Exige `gRed` nas três esferas por CST ou compra governamental. | CST, três `gRed`, `gCompraGov`, `gIBSCBS`. | E + S-E. | Sim: não se aplica quando `ind_gIBSCBS=0`. | — | Baixo com escopo correto das três esferas. | Alto (hipótese). | — **já entregues**. |
| `1034 / UB27-10`; `1046 / UB46-10`; `1063 / UB65-10` | Confere `pRedAliq` oficial nas três esferas. | CST, `cClassTrib`, percentuais, compra governamental. | E + S-E. | Sim; compra governamental fica conservadoramente não avaliada quando faltam os dados da exceção. | — | Controlado pelo não avaliado. | Alto (hipótese). | — **já entregues**. |

## Matriz abrangente — presença e tabelas próximas

| Código / ID | Regra em uma frase | Dados necessários | Disponibilidade | Exceções completas? | Esforço | Risco de falso positivo | Valor provável | Recomendação |
|---|---|---|---|---|---|---|---|---|
| `1020 / UB13-10`; `1023 / UB14-10` | Acusa CST ou `cClassTrib` inexistente. | Código, data e conjunto oficial completo vigente. | E + S-E, mas a ausência pode ser só defasagem da base. | Não há exceção textual; falta garantia de completude/versionamento para provar inexistência. | Baixo em código, alto em governança. | **Alto** numa consulta negativa. | Alto (hipótese). | **Depois**; até lá, código ausente da base deve ser `NOT_EVALUATED`. |
| `1151 / UB13-39`; `1116 / UB13-40` | Veda ou exige `gIBSCBSMono` por CST. | CST, grupo monofásico, data, `tpAmb` e `tpNFDebito`. | E + X + S-L (`IndMonofasica`); falta fonte reproduzível/proveniente. | Sim: 1116 não se aplica a `tpNFDebito=07`, entra em produção em 04/01/2027 e fica futura em homologação; 1151 não traz exceção. | Médio–alto até fechar a fonte. | Alto enquanto S-L for a única evidência do indicador; depois, baixo–médio com vigência e ambiente exatos. | Alto (hipótese). | **Depois**; o destilado de pesquisa não sustenta acusação. |
| `1131 / UB13-44`; `1132 / UB13-45` | Veda ou exige `gTransfCred` por CST; a NT declara modelos 55/65. | CST, presença de `gTransfCred`, modelo e data. | E + X + S-L (`IndTransferenciaCred`); falta fonte reproduzível/proveniente. `TTribNFe` admite o grupo, mas `TTribNFCe` não. | O texto não traz exceção ou ativação especial, mas a aplicabilidade 65 conflita com o XSD local. | Alto até fechar fonte e conflito. | Alto no modelo 65 e para qualquer acusação baseada apenas em S-L. | Médio (hipótese). | **Depois**. No modelo 55, exigir fonte oficial reproduzível; no 65, `NOT_EVALUATED` até reconciliação oficial. |
| `1043 / UB14-30`; `1059 / UB14-50` | Compara `pBio` com o índice obrigatório para `620004/620005`. | `cClassTrib`, `pBio`, `cProdANP` e tabela de índice por produto. | E parcial + X + **T**. | Fonte da condição está completa, mas a tabela oficial indicada não está local. | Alto. | Alto sem tabela versionada. | Médio (hipótese; combustível específico). | **Depois**. |
| `1057 / UB14-40` | Exige `finNFe=5` para `cClassTrib=620005`. | `cClassTrib`, `finNFe`, modelo. | E. | Sim; sem exceções. | Baixo, sem nova extração. | Baixo. | Baixo–médio (hipótese; classificação específica). | **Depois**, como quick win após a shortlist. |
| `1202 / UB14-60`; `1200 / UB14-70`; `1201 / UB14-80` | Confere `cClassTrib` com tipo de nota de débito/crédito. | `cClassTrib`, `tpNFDebito`, `tpNFCredito` e mapa da NT. | E + X; mapa oficial está na NT, mas não materializado em recurso. | Sim: os casos “não limitar” são parte do mapa e não podem virar código implícito. | Médio. | Médio se o mapa for hardcoded ou incompleto. | Médio (hipótese). | **Depois**; materializar o mapa oficial com proveniência. |
| `1029 / UB22-10`; `1030 / UB22-20`; `1044 / UB40-10`; `1083 / UB40-20`; `1061 / UB59-10`; `1090 / UB59-20` | Veda ou exige `gDif` em UF, Município e CBS conforme `ind_gDif`. | CST, data e três presenças de `gDif`. | E + S-E + X. | Sim; as seis regras não trazem exceção. | Baixo. | Baixo se a captura respeitar o pai; alto se usar só nome local. | Alto (hipótese). | **Agora**, prioridade 1. |
| `1111 / UB24-10`; `1112 / UB43-10`; `1187 / UB62-10` | Veda `gDevTrib`: IBS/UF e IBS/Município nos modelos 55/65; CBS somente no modelo 65. | Presença de `gDevTrib` por esfera e modelo. | E (modelo) + X. | Sim; sem exceções. | Baixo. | Baixo com escopo de pai e guarda de modelo por regra. | Médio–alto (hipótese; 1111/1112 alcançam ambos os modelos). | **Agora**, prioridade 3. |
| `1188 / UB62a-10` | Exige `pDevTrib` quando há devolução da CBS na NF-e. | `gCBS/gDevTrib` e `pDevTrib`. | X, mas o XSD local `TDevTrib` contém `vDevTrib`, não `pDevTrib`. | A NT não traz exceção; há conflito de artefatos a resolver. | Médio. | **Alto** enquanto NT e XSD local divergem. | Baixo–médio (hipótese). | **Depois**, somente após reconciliação oficial. |
| `1032 / UB26-10`; `1007 / UB45-10`; `1028 / UB64-10` | Veda `gRed` nas três esferas quando `ind_gRed=0`. | CST, três `gRed/pRedAliq` e `gCompraGov/pRedutor`. | E + S-E; `pRedutor` é X. | Sim: grupo é permitido se `pRedutor` foi informado e o `pRedAliq` da esfera é zero. | Baixo. | Baixo–médio; não se pode substituir “`pRedutor` informado” por “grupo presente”. | Alto (hipótese; fecha a simetria das regras existentes). | **Agora**, prioridade 2. |
| `1190 / UB66a-10`; `1192 / UB66c-10` | Exige inscrição SUFRAMA ou processo quando `gALCZFMCBS` aciona a condição. | `gALCZFMCBS`, `tpALCZFMCBS`, `ISUFemit`, `nProcSuframa`. | X. | Sim; 1192 também considera processo preenchido só com zeros como ausente. | Baixo. | Baixo–médio; nicho e escopo documental/item. | Baixo (hipótese). | **Depois**. |
| `1191 / UB66a-20` | Veda `gALCZFMCBS` por NCM ou combinação territorial. | NCM, municípios de emitente/destinatário e relação oficial ALC/ZFM. | X + **T**; a lista aparece na NT, mas não existe como recurso versionado. | A condição é extensa e completa na NT; precisa ser artefato, não tabela hardcoded. | Alto. | Alto por mudança territorial/normativa. | Médio (hipótese). | **Depois**. |
| `1065 / UB68-10`; `1114 / UB68-11` | Veda ou exige `gTribRegular` por `cClassTrib`. | `cClassTrib`, data e presença de `gTribRegular`. | E + X + S-L (`IndTribRegular`); falta fonte reproduzível/proveniente. | Sim; sem exceções. | Médio–alto até fechar a fonte. | Alto enquanto S-L for a única evidência; lookup desconhecido continua não avaliado. | Alto (hipótese). | **Depois**; o destilado de pesquisa não sustenta acusação. |
| `1066 / UB69-10`; `1067 / UB70-10` | Acusa CST/cClassTrib regular inexistente dentro de `gTribRegular`. | `CSTReg`, `cClassTribReg`, data e conjuntos oficiais completos. | X + S-E, com a mesma fragilidade de consulta negativa da 1023. | Sem exceções textuais; falta garantia de completude da base. | Médio. | Alto para “inexistente”. | Médio (hipótese). | **Depois**; ausência deve ser não avaliada. |
| `1141 / UB82a-10`; `1144 / UB82a-30` | Exige ou veda `gTribCompraGov` conforme `gCompraGov`. | Modelo, `gCompraGov`, `gTribCompraGov`, CST e `ind_gIBSCBS`. | E + X + S-E. | Sim: 1141 não se aplica quando `ind_gIBSCBS=0`; 1144 não tem exceção. | Baixo. | Baixo. | Médio–alto (hipótese). | **Agora**, prioridade 4. |

## Matriz abrangente — subgrupos monofásicos

As 40 regras de presença abaixo parecem baratas porque seus grupos já estão no XML. Não são
baratas fiscalmente: dependem de indicadores de `cClassTrib` ausentes dos artefatos locais, têm
transições IBS/CBS diferentes e, em dois casos, exceção por ambiente. Capturar grupos antes de
obter esses indicadores só produziria dados sem condição segura de julgamento.

| Código / ID | Regra em uma frase | Dados necessários | Disponibilidade | Exceções completas? | Esforço | Risco de falso positivo | Valor provável | Recomendação |
|---|---|---|---|---|---|---|---|---|
| `1219 / UB85a-10`; `1220 / UB85a-20`; `1228 / UB90-10`; `1229 / UB90-20` | Alterna IBS monofásico Ad Rem/Ad Valorem na virada de 2029. | Data, `gIBSCBSMono`, grupos Ad Rem/Ad Valorem e quatro indicadores de classificação. | E + X + **T**. | Sim: Ad Rem é vedado até 2028 e exigido depois; Ad Valorem é o inverso. A exigência depende de ao menos um indicador aplicável. | Alto. | Alto sem os quatro indicadores e vigências. | Futuro (hipótese). | **Depois**. |
| `1245 / UB95a-10`; `1246 / UB95a-20`; `1258 / UB100-10`; `1259 / UB100-20` | Alterna CBS monofásica Ad Rem/Ad Valorem na virada de 2027. | Data, `tpAmb`, grupos e quatro indicadores de classificação. | E parcial + X + **T**. | Sim: Ad Rem é vedado até 2026, exceto em homologação, e exigido depois; Ad Valorem é vedado depois de 2026 e exigido antes, com exceção de homologação quando Ad Rem existe. | Alto. | Alto sem ambiente, indicadores e transição exata. | Futuro (hipótese). | **Depois**. |
| `1125 / UB84a-10 (campo ref.: UB86-10)`; `1126 / UB84a-20 (campo ref.: UB86-20)`; `1127 / UB90-10 (campo ref.: UB87-10)`; `1128 / UB90-20 (campo ref.: UB87-20)`; `1108 / UB94-10 (campo ref.: UB88-10)`; `1109 / UB94-20 (campo ref.: UB88-20)`; `1224 / UB89-10`; `1225 / UB89-20` | Veda/exige padrão, retenção, retido anterior e diferença de mistura no IBS Ad Rem. | Presença dos quatro subgrupos e `ind_gMonoPadrao`, `ind_gMonoReten`, `ind_gMonoRet`, `ind_gpBioDiferenca`. | X + **T**. | Não há exceções adicionais; os IDs “campo ref.” são quirks da tabela do PDF e precisam ser preservados. | Alto. | Alto sem tabela local e normalização dos IDs. | Futuro (hipótese). | **Depois**. |
| `1230 / UB91-10`; `1231 / UB91-20`; `1235 / UB92-10`; `1236 / UB92-20`; `1238 / UB93-10`; `1239 / UB93-20`; `1241 / UB94-10`; `1242 / UB94-20` | Mesmo par exige/veda para os quatro subgrupos do IBS Ad Valorem. | Subgrupos e os mesmos quatro indicadores. | X + **T**. | Sim; sem exceções adicionais. | Alto. | Alto sem tabela local. | Futuro (hipótese). | **Depois**. |
| `1247 / UB96-10`; `1248 / UB96-20`; `1250 / UB97-10`; `1251 / UB97-20`; `1253 / UB98-10`; `1254 / UB98-20`; `1148 / UB99-10`; `1149 / UB99-20` | Mesmo par para CBS Ad Rem, incluindo diferimento/diferença de mistura. | Subgrupos, cinco indicadores (`ind_gMonoDif` e os quatro comuns) e data/ambiente para a implantação. | X + **T**. | 1148 entra em produção em 04/01/2027 e é futura em homologação. A célula `UB99` do PDF mistura duas descrições; precisa de fonte normalizada antes de código. | Alto. | **Muito alto** enquanto a célula oficial estiver ambígua. | Futuro (hipótese). | **Depois**. |
| `1260 / UB101-10`; `1261 / UB101-20`; `1263 / UB102-10`; `1264 / UB102-20`; `1266 / UB103-10`; `1267 / UB103-20`; `1269 / UB104-10`; `1270 / UB104-20` | Mesmo par exige/veda para os quatro subgrupos da CBS Ad Valorem. | Subgrupos e quatro indicadores. | X + **T**. | Sim; sem exceções adicionais. | Alto. | Alto sem tabela local. | Futuro (hipótese). | **Depois**. |

## Matriz abrangente — transferência, ajustes e créditos

| Código / ID | Regra em uma frase | Dados necessários | Disponibilidade | Exceções completas? | Esforço | Risco de falso positivo | Valor provável | Recomendação |
|---|---|---|---|---|---|---|---|---|
| `1133 / UB106-30`; `1168 / UB106-31`; `1129 / UB106-40` | No modelo 55, com `gTransfCred`, exige `finNFe=6`, `tpNFDebito` 01/05 e ao menos um valor positivo. | Grupo, finalidade, tipo, modelo e `vIBS/vCBS`. | E parcial + X; `TTribNFe` admite o grupo. | Sim; são exclusivamente modelo 55 e não trazem exceções. “Ou” significa que basta um dos valores ser maior que zero. | Baixo isoladamente; médio no mecanismo completo. | Baixo com guarda explícita de modelo 55 e sem transformar “ou” em exigência dos dois valores. | Médio (hipótese). | **Depois**, junto do mecanismo: as `UB106` não usam o indicador, mas não devem mascarar a lacuna de fonte e o conflito 65 das 1131/1132. |
| `1169 / UB112-10`; `1170 / UB112-20`; `1171 / UB112-30` | Veda/exige `gAjusteCompet` por CST e exige ao menos um valor positivo. | CST, grupo, `vIBS/vCBS`, modelo e data. | E + X + S-L (`IndAjusteCompet`). | Sim; 1170/1171 são modelo 55, enquanto 1169 também lista 65. Sem outras exceções. | Médio. | Baixo–médio; o XSD de NFC-e já limita o grupo. | Baixo–médio (hipótese; CST 811). | **Depois**. |
| `1172 / UB116-10`; `1173 / UB116-20`; `1174 / UB116-30` | Veda/exige `gEstornoCred` por classificação e exige valor positivo. | `cClassTrib`, indicador, grupo, valores e `tpNFDebito`. | E parcial + X + **T** (`ind_gEstornoCred`). | Sim: 1172 e 1174 não se aplicam a `tpNFDebito=07`; 1173 passa a exigir o grupo nesse tipo de débito. | Alto. | Alto sem o indicador oficial local. | Médio (hipótese). | **Depois**. |
| `1049 / UB120-10`; `1138 / UB131-10` | Veda crédito presumido comum ou ZFM na NFC-e. | Modelo e presença dos grupos. | E + X. | Sim; sem exceções. | Baixo. | Baixo, mas o XSD `TTribNFCe` já não oferece esses grupos. | Baixo (hipótese; duplicação estrutural). | **Não recomendar** nesta camada; o XSD já cobre. |
| `1175 / UB120-20` | Veda `gCredPresOper` quando a classificação não permite. | `cClassTrib`, indicador, grupo e `indBemMovelUsado`. | E parcial + X + S-L (`IndPermiteCredPres`, equivalência ainda a confirmar). | Sim: não se aplica a bem móvel usado; indicador 1 permite, não obriga. | Médio–alto. | Alto até confirmar a equivalência nominal do indicador local. | Médio (hipótese). | **Depois**. |
| `1055 / UB122-10`; `1053 / UB123-10`; `1054 / UB123-20`; `1050 / UB127-10`; `1058 / UB127-20` | Valida `cCredPres` e veda/exige subgrupos IBS/CBS. | Código, tabela de crédito, indicadores IBS/CBS, grupos e `indBemMovelUsado`. | X + **T**. | Sim: 1053 e 1050 não se aplicam a bem móvel usado; os pares de exigência não têm essa exceção. | Alto. | Alto sem tabela oficial local e completa. | Médio (hipótese). | **Depois**. |
| `1056 / UB126-10`; `1060 / UB130-10`; `1107 / UB125-10`; `1124 / UB129-10` | Restringe condição suspensiva por ano/código e limita crédito a `vProd`. | Data, `cCredPres`, valores de crédito e `vProd`. | E (data) + X + C simples. | Sim: IBS suspensivo só de 2033 em diante e código 4; CBS só de 2027 em diante e código 4; limites aplicam ao código 4. | Médio. | Médio; depende de identificar o código oficial sem tabela incompleta. | Baixo no horizonte atual (hipótese). | **Depois**. |
| `1134 / UB131-20`; `1135 / UB131-30`; `1158 / UB131-40`; `1159 / UB131-50` | Veda/exige `gCredPresIBSZFM` por CST e por `tpNFCredito=02`. | CST, indicador, grupo, `tpNFCredito`, modelo e data. | E parcial + X + S-L (`IndCredPresIbsZfm`). | Sim; sem exceções nas quatro regras. O tipo de crédito 02 tem horizonte próprio fora do grupo UB. | Médio. | Baixo–médio com vigência e modelo. | Baixo no horizonte atual (hipótese; CST 810/ZFM). | **Depois**. |
| `1160 / UB132-10`; `1136 / UB133-10` | Veda competência futura e tipo ZFM repetido entre itens. | `competApur`, mês atual e `tpCredPresIBSZFM` de todos os itens. | X + C/cross-item. | Sim; sem exceções. | Médio. | Médio por relógio/fuso e cardinalidade entre itens. | Baixo (hipótese). | **Depois**. |

## Matriz abrangente — cálculos

As 45 regras futuras abaixo não são recomendadas para implementação aritmética manual na camada
de rejeição. A direção preferida é o motor oficial da Calculadora, isolado conforme a arquitetura
da v1. “Dados no XML” não torna barato reproduzir tolerância, arredondamento, vigência, tabelas por
produto e todos os ramos.

| Código / ID | Regra em uma frase | Dados necessários | Disponibilidade | Exceções completas? | Esforço | Risco de falso positivo | Valor provável | Recomendação |
|---|---|---|---|---|---|---|---|---|
| `1104 / UB16-10` | Recompõe a base IBS/CBS a partir de todos os componentes do item. | Valores de produto, serviço, frete, seguros, tributos e indicadores ST. | X + C. | Sim: PISST/COFINSST só não são subtraídos quando compõem o total. A NT marca implementação futura aguardando orientação normativa. | Alto. | Muito alto enquanto a própria NT aguarda orientação. | Alto (hipótese). | **Não recomendar** antes de orientação/motor oficial. |
| `1026 / UB18-10`; `1036 / UB37-10`; `1037 / UB56-10`; `1037 / UB56-20` | Confere alíquotas temporais de IBS UF/Município e CBS. | Data, alíquotas, `cClassTrib`, `tpNFCredito`, NCM, municípios e grupo ALC/ZFM; após 2026/2028, tabelas de alíquota. | E parcial + X + S-L + **T** + C. | Sim: tributação regular força zero; `tpNFCredito=04` isenta; CBS 2025/26 inclui exceção territorial/NCM; após 2027 aceita zero com `gALCZFMCBS`; anos futuros dependem de alíquotas publicadas. | Alto. | Muito alto nas exceções territoriais e viradas de vigência. | Alto (hipótese). | **Não recomendar** localmente; usar motor/tabela oficial. |
| `1031 / UB23-10`; `1045 / UB42-10`; `1062 / UB61-10` | Recalcula diferimento em UF, Município e CBS. | Base, alíquota, `pDif`, `vDif` e `pAliqEfet` quando há redução. | X + C. | Sim: usa alíquota efetiva se `gRed` existe; tolerância de ±0,01. | Médio–alto. | Médio–alto por escala/arredondamento. | Médio (hipótese). | **Não recomendar** localmente; motor oficial. |
| `1035 / UB28-10`; `1047 / UB47-10`; `1064 / UB66-10` | Recalcula alíquota efetiva nas três esferas. | Alíquota nominal, `pRedAliq`, `pRedutor` e `pAliqEfet`. | X + C. | Sim: fórmula muda em compra governamental; quatro casas decimais com arredondamento na última. | Médio–alto. | Alto por regra de arredondamento. | Alto (hipótese). | **Não recomendar** localmente; motor oficial. |
| `1041 / UB35-10`; `1052 / UB54-10`; `1069 / UB67-10` | Recalcula os valores de IBS UF/Município e CBS. | Base, alíquota nominal/efetiva, diferimento, devolução e valores declarados. | X + C. | Sim: usa alíquota efetiva com `gRed`; tolerância de ±0,01. | Alto. | Alto por encadeamento com regras anteriores. | Alto (hipótese). | **Não recomendar** localmente; motor oficial. |
| `1150 / UB54a-10`; `1189 / UB63-10` | Soma IBS do item e calcula devolução da CBS. | Valores IBS UF/Município, crédito presumido/dedução; ou base, CBS, devolução e redução. | X + **T** (`indDeduzCredPres`) + C. | Sim: 1150 subtrai crédito só quando o indicador manda; 1189 usa alíquota efetiva com redução e tolerância de ±0,01. | Alto. | Alto sem tabela de crédito presumido. | Médio (hipótese). | **Não recomendar** localmente. |
| `1040 / UB72-10`; `1051 / UB72b-10`; `1068 / UB72d-10` | Recalcula tributação regular nas três esferas. | Base, três alíquotas efetivas regulares e três valores. | X + C. | Sim: tolerância de ±0,01 nas três. | Médio–alto. | Médio–alto. | Médio (hipótese). | **Não recomendar** localmente; motor oficial. |
| `1142 / UB82a-20`; `1218 / UB66e-10` | Confere soma governamental e valor regular CBS em ALC/ZFM. | Seis valores governamentais; ou base, alíquota efetiva regular e valor CBS. | X + C. | Sim: 1142 tolera ±0,04; 1218 não traz exceção/tolerância adicional. | Médio–alto. | Médio–alto. | Baixo–médio (hipótese). | **Não recomendar** localmente. |
| `1221 / UB86c-10`; `1222 / UB87c-10`; `1223 / UB88a-10`; `1226 / UB89a-10`; `1227 / UB89b-10`; `1232 / UB91c-10`; `1233 / UB91e-10`; `1234 / UB91f-10`; `1237 / UB92c-10`; `1240 / UB93a-10`; `1243 / UB94a-10`; `1244 / UB94b-10` | Calcula IBS monofásico Ad Rem/Ad Valorem, retenções e diferenças de mistura. | Quantidades/bases, percentuais, `cProdANP`, `pBio`, alíquotas/Ad Rem, valores de referência e tabelas por produto. | X + **T** + C. | Sim no nível da família: tolerância de ±0,01; ramos específicos de Gasolina C; tabelas de combustível, índice e valores de referência; 1223/1240/1244 têm nota de implementação futura. | Muito alto. | Muito alto. | Futuro (hipótese). | **Não recomendar** sem motor e todas as tabelas oficiais. |
| `1249 / UB96c-10`; `1252 / UB97c-10`; `1255 / UB98a-10`; `1256 / UB99a-10`; `1257 / UB99b-10`; `1262 / UB101c-10`; `1265 / UB102c-10`; `1268 / UB103a-10`; `1271 / UB104a-10`; `1272 / UB104b-10` | Calcula CBS monofásica Ad Rem/Ad Valorem, retenções e diferenças de mistura. | Mesmo conjunto de quantidades, valores de referência, alíquotas e tabelas por produto. | X + **T** + C. | Sim no nível da família: tolerância de ±0,01; ramos Gasolina C; tabelas ainda a publicar; 1255/1268/1272 têm nota futura e `UB99` exige normalização do PDF. | Muito alto. | Muito alto. | Futuro (hipótese). | **Não recomendar** sem motor e fontes normalizadas. |
| `1070 / UB104-10 (campo ref.: UB105a-10)`; `1071 / UB105b-10` | Soma totais monofásicos IBS/CBS do item. | Valores padrão, retidos, diferidos e totais. | X + C. | Sim: tolerância de ±0,01; ambas marcadas como implementação futura. | Alto. | Alto por depender de toda a cadeia anterior. | Futuro (hipótese). | **Não recomendar** antes do motor oficial. |

## Por que as demais não entram na shortlist

- **Consultas negativas a código não são prova de inexistência.** A base embarcada foi extraída em
  27/07/2026, mas não possui contrato que prove ser completa para toda data de emissão. Isso afasta
  1020, 1023, 1066 e 1067 de qualquer acusação agora.
- **“Só capturar um boolean” esconde a dependência fiscal.** Os grupos monofásicos profundos,
  estorno e crédito presumido precisam de indicadores que o artefato local não contém. Criar os
  booleans antes da fonte não reduz o risco.
- **Destilado de pesquisa não é prova fiscal.** Monofasia superior, tributação regular,
  transferência, ajuste e outros mecanismos S-L só podem subir após snapshot oficial versionado ou
  geração reproduzível com manifesto que permita auditar a fidelidade à fonte.
- **Transferência exige duas guardas diferentes.** As `UB106` são exclusivas do modelo 55 e o XSD
  `TTribNFe` admite `gTransfCred`. Já `UB13-44/45` declaram 55/65, mas `TTribNFCe` não oferece o
  grupo; no modelo 65 o resultado deve ser não avaliado até reconciliação oficial.
- **Tabelas inline continuam sendo tabelas fiscais.** Mapas de tipo de nota, áreas incentivadas e
  combustível devem virar recursos oficiais com proveniência e vigência; não constantes Java.
- **Cálculos têm dono melhor.** Mesmo fórmulas curtas dependem de arredondamento, tolerância,
  transição ou tabelas a publicar. Reimplementá-las na camada local disputa autoridade com a
  Calculadora oficial planejada para a v1.
- **Algumas rejeições apenas refinam erro estrutural.** 1049 e 1138 proíbem na NFC-e grupos que o
  XSD local já não admite; o ganho incremental não compensa uma segunda regra.
- **Quick wins de nicho permanecem visíveis.** 1057, 1190 e 1192 exigem só comparação/captura XML
  local simples e têm fonte completa, mas ficaram para depois porque o valor provável é menor —
  ainda uma hipótese — que o dos quatro mecanismos da shortlist. As 1111, 1112 e 1187 subiram para
  agora após a correção de aplicabilidade mostrar que as duas primeiras alcançam 55/65.

## Guardas para qualquer implementação futura

1. Falta de data, código, indicador ou versão de tabela resulta em **não avaliado**, nunca rejeição.
2. Toda consulta à SVRS usa código **e data do fato gerador**; um registro conhecido fora da
   vigência não pode ser tratado como inexistente.
3. A captura StAX precisa conferir ancestrais. `gDif`, `gRed`, `gDevTrib` e nomes monofásicos se
   repetem em esferas diferentes; estado herdado entre itens produz falso positivo.
4. `tpAmb`, modelo e datas de implantação fazem parte da condição, não são metadados opcionais para
   regras com transição.
5. Nova coluna SVRS exige ingestão com validação estrutural, snapshot oficial versionado ou geração
   reproduzível e manifesto de proveniência. Hash de destilado prova integridade, não fidelidade;
   dado visto apenas em pesquisa sobre fonte remota não conta como artefato apto a acusação.
6. Mensagem, código de rejeição e ID da regra permanecem os oficiais. Detalhe amigável é separado;
   nunca corrigir duplicações ou quirks da mensagem por conta própria.
7. Antes de promover qualquer item “agora”, validar fixtures diferenciais contra a SVRS e provar por
   mutação que exceção, escopo de grupo e vigência realmente protegem a regra.
