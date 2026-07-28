# Tarefas paralelas

Trabalho que pode ser executado **em paralelo** à linha principal de implementação, sem risco de
conflito. Destinado a outro agente ou a outra sessão.

**Antes de começar qualquer tarefa daqui**, leia [`../context.md`](../context.md) e
[`../conventions.md`](../conventions.md). O harness do projeto vale integralmente: código em
inglês, mensagens e docs em pt-BR, comentários enxutos, 1 commit semântico por tarefa.

Todas as tarefas aqui são **investigação ou desenho** e entregam **arquivos novos** — nenhuma toca
em código de produção, então não há risco de conflito com a linha principal.

## Prioridade

| Ordem | Tarefa | Por quê agora |
|---|---|---|
| 1 | **UX-1** | A UI ainda não foi implementada. Este desenho precisa existir **antes** de eu planejar o bloco de interface, senão planejo em cima de suposição e refaço depois. É a única com dependência de tempo. |
| 2 | **MAP-1** | Cruza a NT com o que a Calculadora oferece. É o que converte "129 regras de presença" em "N regras que conseguimos de fato cobrir" — o número que decide a D-026 e dimensiona o plano. |
| 3 | **INV-1** | Resolve a decisão D-025, que está bloqueando a implementação da regra 1022. |
| 4 | **INV-2** | Pergunta direta do dono do projeto; define se a trava NCM entra no escopo ou vira pendência. |
| 5 | **UX-2** | Útil, sem urgência — os textos entram junto com a implementação das regras. |
| 6 | **INV-3** | Menor urgência: já existe fallback (aviso por idade) que funciona sem rede. |

## Regras de convivência (para não colidir com a linha principal)

- **Não altere** nada em `src/main/java/br/com/validadorlote/` nem em `src/test/java/`. A linha
  principal está mexendo nesses diretórios.
- **Não altere** `build.gradle`, `docs/decisions.md` nem as specs existentes em
  `docs/superpowers/specs/`. Se sua investigação produzir uma decisão, **escreva a recomendação no
  seu próprio arquivo de entrega** e sinalize; quem integra decide.
- **Crie arquivos novos** nos caminhos indicados em cada tarefa. Entregar num arquivo novo nunca dá
  conflito de merge.
- Trabalhe numa branch própria por tarefa: `investiga/<slug>` ou `ux/<slug>`.
- Se precisar da Calculadora, suba e **derrube ao terminar** (ela consome ~700 MB de RAM):
  ```
  cd ~/Downloads/calculadora
  docker run -d --name calc-<seu-slug> --rm -p 8080:8080 -w /calculadora calculadora bash start.sh
  # ao terminar:
  docker stop calc-<seu-slug>
  ```
  A API fica em `http://localhost:8080/api/calculadora`. O boot leva de 8 a 20 segundos.
- **Cuidado com busca em arquivo grande.** A NT em markdown tem 281 KB e o catálogo é extenso;
  `grep -oE` com alternância e quantificadores causou backtracking catastrófico e travou a máquina.
  Prefira `grep -F` (literal), `grep -n` com padrão simples, ou processe com Python lendo linha a
  linha.

---

## Contexto mínimo do produto

Validador desktop offline que lê uma pasta de XMLs de NF-e/NFC-e e prevê quais seriam rejeitados
pela SEFAZ. Hoje entrega validação de schema (pronta, mergeada). Está sendo desenhada a camada de
previsão de rejeição — ver [`specs/2026-07-27-camada-rejeicao-design.md`](./specs/2026-07-27-camada-rejeicao-design.md).

Fatos que essas tarefas assumem como estabelecidos:

- O grupo `IBSCBS` é `minOccurs="0"` no XSD, então uma NF-e de CRT=3 sem o grupo **passa** na
  validação de schema e **será rejeitada** pela SEFAZ a partir de 03/08/2026 (regra `UB12-10`,
  rejeição 1115).
- A NT 2025.002 v1.50 tem 277 regras formais na seção 7. Catálogo extraído em
  `.superpowers/sdd/nt-regras-catalogo.md` e resumo em `.superpowers/sdd/nt-regras-resumo.md`
  (arquivos locais, fora do git).
- A estratégia adotada é derivar regras das **tabelas oficiais** da Calculadora em vez de
  transcrevê-las em código.

---

## MAP-1 — Cruzar as regras da NT com o que a Calculadora oferece

**Por quê:** sabemos que a NT tem 277 regras (129 de presença, 77 de cálculo, 44 de tabela) e
sabemos, separadamente, o que a Calculadora expõe. **Ninguém cruzou as duas coisas.** Sem esse
cruzamento, a escolha de quais rejeições implementar primeiro é palpite, e o plano de implementação
não tem como ser dimensionado.

**Objetivo:** para cada regra de IBS/CBS da NT, dizer **como conseguiríamos prevê-la** — ou que não
conseguimos.

### Escopo

Apenas as regras de **IBS/CBS** — na prática o **Grupo UB** do catálogo (167 das 277) mais as poucas
de outros grupos que citem IBS/CBS. Imposto Seletivo, demais DFe e regras de evento estão fora do
escopo do produto e **não devem ser analisadas**.

Se o volume ainda ficar grande, priorize por completude no que é `PRESENCA` e `TABELA` — são as
categorias que entram primeiro — e diga claramente o que ficou de fora.

### Insumos (todos já existem localmente)

- Catálogo das regras: `.superpowers/sdd/nt-regras-catalogo.md`
- Resumo com armadilhas da extração: `.superpowers/sdd/nt-regras-resumo.md`
- NT em markdown: `tmp/NT_2025.002_v1.50_RTC_NF-e_IBS_CBS_IS.md` (fonte de verdade quando o
  catálogo estiver ambíguo — 25 mensagens vieram contaminadas por quebra de página na extração)
- Contrato da Calculadora: `docs/calculadora/contrato-validar-xml.md` e
  `docs/calculadora/calculadora-openapi.json`
- Campos das tabelas: §5.1 da spec
  [`specs/2026-07-27-camada-rejeicao-design.md`](./specs/2026-07-27-camada-rejeicao-design.md)
- A Calculadora rodando, para confirmar empiricamente quando houver dúvida

### Veredito por regra

Classifique **cada** regra analisada em exatamente um destes:

| Veredito | Significa |
|---|---|
| `TABELA` | Derivável dos metadados das tabelas oficiais embarcadas. **Diga qual campo** (ex.: `possuiPercentualReducao`). |
| `CALCULADORA` | Precisa da Calculadora rodando — cálculo via `regime-geral`, ou erro `REG-*` que ela já emite. **Diga qual endpoint ou código.** |
| `NOSSO_CODIGO` | Só com o XML em mãos: presença de grupo, CRT, vigência, coerência interna. **Descreva a condição em uma frase.** |
| `FALTA_DADO` | Conseguiríamos, mas falta uma tabela ou indicador que não temos. **Diga o que falta.** |
| `INVIAVEL` | Depende de informação que não está no XML nem em fonte disponível (ex.: cadastro do contribuinte, histórico de notas anteriores). |

### Entregar em

`docs/pesquisa/map-1-regras-versus-calculadora.md`, com:

1. **Tabela regra a regra**: `ID | Código de rejeição | Categoria | Veredito | Como (campo/endpoint/condição) | Observação`
2. **Contagem por veredito** — este é o número que dimensiona o plano.
3. **Os 10 primeiros candidatos a implementar**, ordenados por relação valor/esforço. Considere que
   a rejeição que mais vai ocorrer a partir de 03/08 é a 1115 (emitente CRT=3 que simplesmente não
   emite IBS/CBS) e que regras cuja causa é a mesma agrupam numa linha só do relatório.
4. **Recomendação objetiva para a D-026** — quais rejeições entram no primeiro corte, e por quê.
5. **Lista do que é `INVIAVEL` ou `FALTA_DADO`**, para virar pendência registrada.

### Cuidados

- **Não confie só no catálogo.** Quando uma regra parecer decisiva, confira o texto no markdown da
  NT. O catálogo é derivado e teve trechos contaminados.
- Quando afirmar que a Calculadora cobre algo, **confirme empiricamente** se puder — mande a
  chamada e cole a resposta. Uma cobertura afirmada e não testada vale pouco.
- **Não inflacione a coluna `TABELA`.** É tentador marcar tudo como derivável; se o campo não
  existir de fato, marque `FALTA_DADO`. Um `INVIAVEL` honesto vale mais que um `TABELA` otimista
  que quebra na implementação.

---

## INV-1 — Tabela oficial de cClassTrib do portal NF-e

**Por quê:** as regras `UB13-30` (rejeição 1022) e as de grupo de redução referenciam indicadores
por CST (`ind_gIBSCBS`, `ind_gRed`) que **não** existem em nenhuma das visões da Calculadora. A
tabela oficial completa é publicada no portal da NF-e, mas não sabemos em que formato nem se há URL
estável.

**Objetivo:** descobrir se dá para obter essa tabela de forma programática e reprodutível.

**O que investigar:**
- Onde exatamente o portal publica a Tabela de Classificação Tributária (aba "Documentos" → opção
  "Diversos", segundo a NT). Anotar a URL.
- Formato do arquivo (XLSX, CSV, PDF?), tamanho, e se traz colunas de indicador por CST.
- Se a URL é estável ou versionada por data. Se muda a cada publicação, existe índice?
- Comparar as colunas dessa tabela com os campos que a Calculadora expõe (listados na §5.1 da
  spec). O que ela tem a mais?

**Entregar em:** `docs/pesquisa/inv-1-tabela-cclasstrib-portal.md`

Com: URL(s) encontradas, formato, amostra das colunas, e uma recomendação objetiva — dá para
automatizar o download numa task de build, ou vai precisar de atualização manual?

**Não faça:** não baixe a tabela para dentro de `src/main/resources/`. Só investigue e relate.

---

## INV-2 — A trava NCM × cClassTrib

**Por quê:** sabemos que existe uma trava ligando NCM à classificação tributária (o código de erro
`REG-011 — NCM não vinculada` existe na Calculadora), mas nos testes que fizemos ela **não
disparou**: `cClassTrib` 200001 e 200002 (alíquota zero, nomenclatura NCM) calcularam normalmente
com um NCM de camiseta (61091000), que claramente não pertence a elas.

O que **disparou** foi `REG-007 — Erro de nomenclatura` ("classificação só se aplica a NBS"), que é
a trava NCM-vs-NBS, não o vínculo NCM-específico.

**Objetivo:** determinar se a Calculadora enforce o vínculo NCM × cClassTrib e sob quais condições.

**O que investigar:**
- Encontrar alguma combinação que faça `REG-011` disparar. Sugestões: classificações de cesta
  básica, medicamentos, dispositivos médicos — tratamentos que a lei vincula a listas de NCM.
- Verificar se existe endpoint de NCM aplicável por classificação. Já testamos e deu 404:
  `/dados-abertos/ncm-aplicaveis` e `/dados-abertos/classificacoes-tributarias/ncm`. Confirmar se
  há outro caminho (o Swagger fica em `http://localhost:8080/api/swagger-ui/index.html`).
- Ver o que a NT diz: procurar no catálogo em `.superpowers/sdd/nt-regras-catalogo.md` por regras
  que citem NCM junto de classificação. **Use `grep -F 'NCM'` ou Python — não regex complexa.**
- Se a Calculadora não expõe o vínculo, verificar se a tabela do portal (INV-1) expõe.

**Entregar em:** `docs/pesquisa/inv-2-vinculo-ncm-cclasstrib.md`

Com: o que dispara e o que não dispara (com os payloads exatos usados), qual é a regra na NT, e a
conclusão — conseguimos validar isso offline, só com a Calculadora rodando, ou é inviável no v0?

**Se concluir que é inviável, tudo bem** — registre claramente e recomende deixar como pendência.
Uma lacuna declarada vale mais que uma implementação que finge cobrir.

---

## INV-3 — Como saber a versão vigente da Nota Técnica

**Por quê:** a Calculadora expõe `versaoApp` e `versaoDb`, mas **não** a versão da NT. Nossas
regras de documento ficam atadas à versão transcrita sem sinal de obsolescência. A NT teve 13
revisões em 16 meses.

**Objetivo:** achar uma forma programática de descobrir a versão vigente da NT.

**O que investigar:**
- O portal da NF-e publica índice de Notas Técnicas — existe URL estável, RSS, ou página
  previsível de onde extrair "versão mais recente da NT 2025.002"?
- Existe correlação entre `versaoDb` da Calculadora e versão da NT? (comparar o histórico de
  `descricaoVersaoDb` com as datas de revisão da NT)
- Alguma API pública da RFB/SEFAZ que exponha isso?

**Entregar em:** `docs/pesquisa/inv-3-versao-vigente-nt.md`

Com: recomendação concreta para a §6 da spec — dá para verificar online de forma barata e opt-in,
ou ficamos só com o aviso por idade?

---

## UX-1 — Tela de resultados em camadas

**Por quê:** o validador da SVRS apresenta o resultado em camadas (parser → schema → certificado →
assinatura → regras de negócio), e é esse o formato que queremos. A spec atual descreve o conceito
(§7) mas não há desenho de tela. A UI ainda não foi implementada — é o momento certo de desenhar.

**Contexto de produto:** o usuário é contador, conhecimento comum de informática. Ele arrasta uma
pasta com centenas de XMLs e precisa descobrir rapidamente **quais problemas sistêmicos existem**,
não navegar arquivo por arquivo. O valor está no agrupamento por causa: "380 documentos com o mesmo
problema" em vez de 380 linhas.

**Objetivo:** propor o desenho da tela de resultados.

**O que considerar:**
- Como mostrar as camadas de forma que fique claro **o que foi verificado e o que não foi**. Uma
  camada não executada (ex.: conferência de valores sem a Calculadora instalada) precisa ser
  visualmente distinta de uma camada aprovada — confundir as duas é o pior erro possível aqui.
- Como apresentar causa-raiz com contador de documentos afetados, e como o usuário navega dela para
  os arquivos concretos.
- Onde exibir a proveniência (versão dos schemas, versão da base de tabelas, versão da NT) sem
  poluir.
- Como sinalizar base de dados possivelmente desatualizada.
- Uma questão em aberto do dono do projeto: ele imaginou um **grid de arquivos importados** com
  ícone por linha e botão para remover inválidos; a spec atual tem drop → progresso → tabela de
  causas, sem grid. Avalie os dois caminhos e recomende, considerando que remover inválidos não
  muda o resultado da análise.
- Stack: Java Swing com FlatLaf. Não precisa desenhar em Swing — mockup textual, ASCII ou descrição
  estruturada serve. O que importa é a decisão de layout e hierarquia de informação.

**Entregar em:** `docs/pesquisa/ux-1-tela-resultados.md`

Com: proposta de layout, justificativa das escolhas, e os pontos onde você recomenda decisão do
dono do projeto.

**Não faça:** não implemente Swing. Só o desenho.

---

## UX-2 — Textos ao usuário

**Por quê:** o produto traduz mensagens técnicas do parser XML para português acionável. A tabela
de tradução está em `src/main/resources/messages/xsd-translations.properties` (formato
`<código>.<campo>=mensagem|ação`). Ela é semente e precisa crescer — mas **cada tradução errada é
pior que nenhuma**, porque o contador age sobre ela.

**Objetivo:** revisar os textos existentes e propor os da camada de rejeição.

**O que fazer:**
- Ler a tabela atual e avaliar: a mensagem descreve corretamente o erro? A ação é acionável, ou é
  vaga ("verifique o campo")? O tom fala com contador ou vaza jargão de XML?
- Propor os textos das rejeições que entram no primeiro corte: 1115, 1021, 1025, 1033, 1074, 1079.
  A mensagem oficial de cada uma está no catálogo em `.superpowers/sdd/nt-regras-catalogo.md`.
  **Não reescreva a mensagem oficial** — ela é exibida junto; escreva a explicação amigável e a
  ação sugerida.
- Propor o texto do aviso de base desatualizada e o da camada não executada.

**Entregar em:** `docs/pesquisa/ux-2-textos-usuario.md`

Com: os textos propostos em formato de tabela (código | mensagem amigável | ação sugerida), e as
correções que você recomenda nos textos existentes, com a justificativa de cada uma.

**Não faça:** não edite o `.properties`. Proponha no seu arquivo; a integração é feita depois.

---

## Como reportar

Cada tarefa entrega **um arquivo markdown novo** no caminho indicado, commitado na sua branch com
mensagem semântica (`docs(inv-1): ...` ou `docs(ux-1): ...`).

Se durante a investigação você concluir que algo é **inviável de reproduzir**, registre isso
explicitamente com o que testou e por que não deu — é informação valiosa e evita que alguém repita
o caminho. Não invente cobertura que não existe.
