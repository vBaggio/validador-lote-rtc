# Contrato real da validação de XML — Calculadora de Tributos da RFB (IBS/CBS/IS)

Apuração: 2026-07-26 · Motor: **api-regime-geral v1.2.4** · Base de dados: **V0039 (2026-07-08)**
Servidor levantado localmente com Java 21 (Homebrew) na porta 18300, context-path `/api`.

---

## 0. ACHADO PRINCIPAL (a pergunta mais importante do projeto)

**O endpoint de validação NÃO devolve uma lista estruturada de achados, nem texto corrido.
Ele devolve um BOOLEAN.**

- Sucesso (XML válido) → **HTTP 200**, `Content-Type: application/json`, corpo literal **`true`**.
- Falha (XML inválido) → **HTTP 422** (ou 400), `Content-Type: application/problem+json`,
  corpo = um **ProblemDetail (RFC 7807)** contendo **UMA única** mensagem de erro (o
  primeiro erro de schema encontrado — o validador é *fail-fast*, para no 1º erro).

Ou seja: a "validação" oficial é **validação de schema XSD** (estrutural + facetas/patterns),
não um relatório de regras de negócio com código-de-regra por item/campo. Quem precisar de
"lista de todos os problemas" terá que iterar/consertar-e-repetir, pois só o primeiro erro volta.

> O endpoint imaginado na tarefa (`/api/calculadora/validar-xml`, com lista de achados) **NÃO
> existe**. O endpoint real é `POST /api/calculadora/xml/validate` e responde boolean.

Evidência de sucesso (pares/11-grupo-valido-jar): `HTTP 200 | application/json | true`
Evidência de falha  (pares/04b-grupo-invalido-ns): `HTTP 422 | application/problem+json | {...cvc-complex-type...}`

---

## 1. Assinatura do endpoint

```
POST /api/calculadora/xml/validate?tipo={tipo}&subtipo={subtipo}
Content-Type: application/xml         (corpo = XML CRU, não multipart, não JSON)
Accept: application/json
Body: <xml cru como string>
```

Fonte: `XMLController.validate(...)` — `consumes=application/xml`, `produces=application/json`,
`@RequestBody String xml`, `@RequestParam TipoDocumento tipo`, `@RequestParam TipoXml subtipo`,
retorno `ResponseEntity<Boolean>`. Confirmado no OpenAPI vivo (`calculadora-openapi.json`).

### Parâmetros de query (ambos OBRIGATÓRIOS)
- `tipo` (enum): `nfe`, `nfce`, `nfse`, `cte`, `cte-simplificado`, `bpe`, `bpe-tm`, `nf3e`
- `subtipo` (enum): `grupo`, `nota`

`subtipo` decide contra QUAL XSD do classpath o XML é validado: `xml/{tipo}/{subtipo}.xsd`
(ex.: `xml/nfe/nota.xsd` ou `xml/nfe/grupo.xsd`). Fonte: `XmlUtil.getSchema()`.

- **`nota`** = valida um **documento fiscal inteiro** (a NF-e/NFC-e completa).
  Raízes aceitas (de `nota.xsd` → inclui `originais/leiauteNFe_v4.00.xsd`):
  `<enviNFe>`, `<nfeProc>` e `<NFe>`.
- **`grupo`** = valida **apenas o fragmento novo da RTC** (os grupos IBS/CBS/IS).
  Raiz aceita: `<infNFe>` reduzido, contendo só `det/imposto/{IS?,IBSCBS}` e `total/{ISTot?,IBSCBSTot?}`.
  **É este o modo relevante para validar "os grupos IBS/CBS" sem precisar da NF-e completa nem de assinatura.**

### Corpo
XML **cru** no corpo (string), `Content-Type: application/xml`. **Namespace obrigatório**:
`xmlns="http://www.portalfiscal.inf.br/nfe"` (sem ele, o schema não reconhece a raiz).

---

## 2. Esquema da RESPOSTA

### 2a. Sucesso — HTTP 200
```
Content-Type: application/json
true
```
(É sempre `true`. Nunca retorna `false`: invalidez é sinalizada por status != 200.)

### 2b. Erro — HTTP 422 (validação de schema falhou) — RFC 7807
```
Content-Type: application/problem+json
{
  "type":     "http://<host>/errors/erro-xml",     // URI; base derivada do host da requisição
  "title":    "Erro de validação de XML",
  "status":   422,
  "detail":   "Erro na validação do XML: Erro na linha {L}, coluna {C}: {mensagem XSD}",
  "instance": "/api/calculadora/xml/validate",
  "timestamp":"2026-07-26T05:04:00.594400034Z"      // propriedade extra (Instant ISO-8601)
}
```
- `detail` embute a mensagem nativa do parser Xerces (`cvc-*`), com **linha e coluna** do XML.
  Ex.: `cvc-complex-type.2.4.a`, `cvc-elt.1.a`, `cvc-pattern-valid`, `cvc-complex-type.2.4.b`.
- Só **o primeiro** erro é reportado (fail-fast).

### 2c. Erro — HTTP 400 (parâmetro tipo/subtipo inválido)
```
{ "type":"http://<host>/errors/tipo-argumento-invalido",
  "title":"Tipo de argumento inválido", "status":400,
  "detail":"Parâmetro inválido [subtipo: XXXX] ",
  "instance":"/api/calculadora/xml/validate", "timestamp":"..." }
```

Mapeamento de exceções (fonte `ApiExceptionHandler` + `ProblemType`):
`ErroXmlException extends ValidacaoException` → **422**, type `.../errors/erro-xml`.
`MethodArgumentTypeMismatch` (enum inválido) → **400**, type `.../errors/tipo-argumento-invalido`.

---

## 3. Respostas às 7 perguntas (com evidência)

**Q1. A resposta é ESTRUTURADA (lista de achados com código/item/campo) ou texto corrido?**
→ **Nenhum dos dois: é BOOLEAN.** 200 = `true`; erro = ProblemDetail RFC 7807 com **um único**
erro de schema em `detail` (texto do Xerces com linha/coluna). Não há lista, nem código-de-regra
por item, nem "campo X do item Y". Evidência: pares/11 (200 `true`) e pares/01,02,04b,05b (422).

**Q2. Valida o schema XSD internamente?**
→ **SIM — é literalmente só isso que faz.** `XmlService.validarXml()` usa
`javax.xml.validation.Validator` contra o XSD. Todos os erros retornados são códigos `cvc-*`
do validador de XML Schema. Inclusive **facetas/patterns**: pares/11 (1ª tentativa) rejeitou
`pCBS=0.9` com `cvc-pattern-valid ... pattern '0|0\.[0-9]{2,4}|...' for type 'TDec_0302_04RTC'`
(exigia `0.90`). Não há validação de cálculo/negócio além do que o XSD codifica (CST em
enumeração, cClassTrib por pattern, escolha de grupo por CST, casas decimais etc.).

**Q3. Aceita XML SEM assinatura digital?**
→ **Depende do `subtipo`:**
  - `subtipo=grupo`: **SIM**, não há assinatura no fragmento (grupo.xsd é só `infNFe` reduzido).
    Foi o caso validado com sucesso (pares/11 → `true`).
  - `subtipo=nota` (NF-e completa): o **elemento** `ds:Signature` é **OBRIGATÓRIO** pelo schema —
    `leiauteNFe_v4.00.xsd` linha 6548 `<xs:element ref="ds:Signature"/>` (minOccurs default = 1),
    dentro do único complexType do intervalo, `TNFe` (linha 25, fecha na 6550). Logo uma NF-e
    "crua" sem o elemento Signature **reprova** no schema. PORÉM o XSD **não** verifica a
    validade CRIPTOGRÁFICA da assinatura — só exige que o elemento exista e esteja bem-formado.
    (O exemplo oficial `nfe-sem-rtc.xml` é um `<enviNFe>` assinado, com `<Signature>` presente.)

**Q4. Aceita `<nfeProc>` (com protocolo) ou apenas `<NFe>`?**
→ **Aceita ambos, e ainda `<enviNFe>`.** `nota.xsd` declara 3 raízes: `enviNFe`, `nfeProc`, `NFe`.
No teste pares/10 (raiz `<nfeProc>`) o parser **reconheceu a raiz** e avançou para a validação de
conteúdo (falhou só mais adiante, em `ide` incompleto), provando que `nfeProc` é raiz válida.

**Q5. Comportamento NFC-e (mod. 65) vs NF-e (mod. 55)?**
→ **Idêntico no mecanismo.** `tipo=nfce` usa `xml/nfce/{grupo,nota}.xsd`. A diferença entre
`nfe/grupo.xsd` e `nfce/grupo.xsd` é só o tipo do IBSCBS: **`TTribNFe`** (NF-e) vs **`TTribNFCe`**
(NFC-e) — e a data do comentário. O mesmo fragmento válido retornou `true` tanto para `nfe`
quanto para `nfce` (pares/11 e pares/12, ambos 200 `true`). `nota.xsd` das duas é igual (só muda
o comentário de data).

**Q6. Licença/termos — há restrição a redistribuição do JAR?**
→ **Não há NENHUM arquivo de licença/EULA/termo** na distribuição (zip, tar, META-INF do JAR,
READMEs, pom.xml sem `<licenses>`). Descrita como "código aberto / beta" da RFB, mas sem
instrumento jurídico que autorize expressamente redistribuir o binário. Detalhe em
`licenca-calculadora.txt`. **Recomendação:** não reempacotar o JAR; orientar download pelo
endpoint oficial.

**Q7. Aguenta requisições concorrentes?**
→ **SIM, com folga.** 8 POSTs `validate` em paralelo → **8× HTTP 200 `true`**, ~4 ms cada,
wall-clock total ~15 ms. Servidor Tomcat (config `server.tomcat.threads.max: 250`). Sem erros,
sem serialização perceptível.

---

## 4. Números operacionais (afetam o UX do produto)

| Métrica | Valor |
|---|---|
| Tamanho do `calculadora.zip` | **253,5 MB** (download ~49 s) |
| `api-regime-geral.jar` (o que interessa) | **93,8 MB** |
| `api-split-payment-simplificado.jar` (não necessário p/ validar NF-e) | 85,0 MB |
| Tempo de boot | **~8 s** (Spring: "Started ... in 7.597 seconds"); pronto p/ 1ª requisição em ~8–18 s de relógio (spin-up da JVM + jar de 93 MB) |
| RAM (RSS do processo java) | **~700–770 MB** (sem `-Xmx`; cresceu 694→772 MB ao longo de ~10 min; provável reduzir com heap capado) |
| Latência de `validate` | ~4 ms por requisição (fragmento pequeno) |
| Porta app / context-path | 18300 `/api` (default do produto: **8080** `/api`) |
| Porta management (health/metrics) | 18301 `/` (default: **9101**); health em `/health`, prometheus em `/metrics` |
| Banco | SQLite **read-only** embutido (`calculadora-pro.db`), profile `offline` |
| Frontend | **SIM** — GUI Angular embutida (nginx, porta 80): `usr/share/nginx/html` (simulador-*.component). Não é necessária para o uso via API. |

Como subir só a API de validação (sem Docker), a partir da tarball extraída:
```
cd calc-tar/calculadora
java -Djava.net.preferIPv4Stack=true -jar api-regime-geral.jar \
     --spring.profiles.active=offline --server.port=18300 --management.server.port=18301
```
(o working dir precisa ser `calc-tar/calculadora` para o datasource relativo
`./calculadora/db/calculadora-pro.db` resolver.)

---

## 5. Quirks / surpresas descobertos

1. **Só o 1º erro volta (fail-fast).** Para "validar em lote" e listar todos os problemas de um
   XML, é preciso corrigir-e-repetir. Não existe modo "colete todos os erros".
2. **Namespace é obrigatório.** Sem `xmlns="http://www.portalfiscal.inf.br/nfe"` a raiz não é
   reconhecida (`cvc-elt.1.a: Cannot find the declaration of element 'infNFe'`). O `exemplo.xml`
   do repositório vem SEM namespace e, por isso, **reprova** como está.
3. **`subtipo=grupo` é o caminho barato.** Valida os grupos IBS/CBS/IS de um item isolado, sem
   NF-e completa e sem assinatura. Ideal para um validador de lote que só quer conferir os grupos
   novos. `<total/>` vazio é válido (ISTot/IBSCBSTot são opcionais).
4. **`type` do ProblemDetail reflete o host da requisição** (ex.: `http://localhost/errors/...`).
   Ao chamar via rede, virá com o host que o cliente usou — não confiar nisso como identificador estável;
   o identificador estável do tipo de erro é o **path final** (`/errors/erro-xml`, `/errors/tipo-argumento-invalido`).
5. **DOCTYPE não é rejeitado na validação da instância** (teste pares/13: um `<!DOCTYPE ...>` passou
   direto para a validação de schema). O `disallow-doctype-decl` do código aplica-se à fábrica do
   XSD, não ao parser da instância. Não confirmei exploração de XXE (minha entidade não era
   referenciada), mas **convém tratar o XML de terceiros como não-confiável** antes de enviar.
6. **A resposta 200 é o JSON `true`** (5 bytes). Trivial de consumir, mas não traz nenhuma
   informação adicional (nem versão do schema usada, nem avisos).
7. Há um endpoint irmão de validação **só para NFS-e**: `POST /calculadora/nfse/indicador-operacao/validate`
   (contrato diferente; fora do escopo NF-e/NFC-e).

---

## 6. Divergências ESPELHO (GitHub, release 18/03/2026) × JAR VIVO (v1.2.4)

O código-fonte espelhado **diverge** do XSD embutido no JAR atual — o **JAR é a fonte da verdade**:

- **`grupo.xsd` mudou.** O do JAR inclui `originais/tiposBasico_v4.00.xsd`, adicionou um
  `ide/gCompraGov` opcional e mudou a cardinalidade de `det` (espelho: `minOccurs=0 maxOccurs=999`;
  JAR: `maxOccurs=990`, obrigatório).
- **A ORDEM dos elementos do grupo IBS mudou.** No JAR, dentro de `gIBSCBS` (tipo `TCIBS`) a
  sequência é `vBC, gIBSUF, gIBSMun, vIBS, gCBS`. O `exemplo.xml` do espelho põe `gCBS` logo após
  `gIBSMun` (sem o `vIBS` no meio) e por isso **reprova** contra o JAR
  (`cvc-complex-type.2.4.a: ... 'gCBS' ... One of 'vIBS' is expected`, pares/03b).
- **`pCBS`/alíquotas exigem 2–4 casas decimais** (`TDec_0302_04RTC`): `0.9` reprova, `0.90` passa.
- O **`exemplo.xml` não é embarcado no JAR** (é só material ilustrativo do fonte) e, como está,
  não valida no motor atual.

**Consequência de design:** o validador de lote deve **extrair os XSDs do JAR corrente**
(`unzip api-regime-geral.jar 'BOOT-INF/classes/xml/**'`) e/ou validar via o endpoint vivo — nunca
assumir que os XSDs/exemplos do espelho GitHub estão atualizados. A tabela de referência (CST,
cClassTrib, NCM) também deve vir da base do JAR (endpoints `dados-abertos`, ver §7), pois muda por data.

---

## 7. Endpoints relacionados úteis (do OpenAPI vivo — ver `calculadora-openapi.json`)

- `GET  /api/calculadora/dados-abertos/versao` → versão do app e da base de dados.
- `POST /api/calculadora/regime-geral` → **motor de cálculo** IBS/CBS/IS (JSON→JSON): dado o item,
  calcula os valores. É o complemento de "validar": para conferir se os VALORES de um XML estão
  certos (não só a estrutura), recalcula-se por aqui e compara-se.
- `POST /api/calculadora/xml/generate?tipo=nfe` (JSON `ROCDomain` → XML) — gera o fragmento de grupo.
- **Tabelas de referência** (todas exigem `?data=AAAA-MM-DD`; `ncm` exige também `?ncm=`):
  - `GET /api/calculadora/dados-abertos/situacoes-tributarias/cbs-ibs?data=...`
    → `[{id, codigo, descricao}]` (lista de CST: 000 "Tributação integral", 010, 011, ...).
  - `GET /api/calculadora/dados-abertos/classificacoes-tributarias/cbs-ibs?data=...`
    → `[{codigo, descricao, tipoAliquota, nomenclatura, descricaoTratamentoTributario, incompativelComSuspensa, ...}]`
      (tabela cClassTrib; ~135 KB).
  - `GET /api/calculadora/dados-abertos/ncm?ncm=22030000&data=...`
    → `{tributadoPeloImpostoSeletivo, capitulo, subitem}`.
  - `GET /api/calculadora/dados-abertos/ufs` → lista de UFs.
  Amostras salvas em `amostras-dados-abertos/`.

## 8. Fluxo de cliente oficial (scripts-python-exemplo.zip)

O pacote traz o padrão de integração ERP recomendado (confirma o contrato acima):
1. `1-regime-geral.py` — calcula tributos (`POST /calculadora/regime-geral`).
2. `2-gerar-xml.py` — gera o XML dos grupos RTC.
3. `3-validar-grupo-xml.py` — `POST .../xml/validate?tipo=nfe&subtipo=grupo`, `Content-Type: application/xml`,
   corpo cru; trata **200 = "XML válido"** e **!=200 = "XML inválido"** (imprime o corpo do ProblemDetail).
4. `4-injetar-xml.py` — injeta (via regex) os grupos IS/IBSCBS e ISTot/IBSCBSTot dentro do
   `<imposto>` e `<total>` de uma NF-e existente (`nfe-sem-rtc.xml`, um `<enviNFe>` assinado).
