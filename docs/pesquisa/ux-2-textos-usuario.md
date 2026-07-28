# UX-2 — Textos ao Usuário: Mensagens Amigáveis e Traduções da Camada de Rejeição

Este documento contempla a proposta de comunicação do **Validador Lote RTC** voltada ao usuário final (contadores, analistas fiscais e auditores), priorizando clareza, acionabilidade e eliminação de jargão estritamente técnico de XML/XSD.

---

## 1. Rejeições do Primeiro Corte (NT 2025.002 - Reforma Tributária)

A tabela a seguir apresenta os textos amigáveis e ações sugeridas para as 6 rejeições prioritárias do primeiro corte (decisão D-026). 

> **Regra de Exibição:** A mensagem oficial emitida pela SEFAZ / NT **nunca é substituída**, sendo exibida em conjunto no relatório de validação. As colunas a seguir complementam a mensagem oficial para instruir o contador sobre a causa e a correção no ERP/sistema emissor.

| Código | Mensagem amigável | Ação sugerida |
|---|---|---|
| **1115** | O item da nota não possui o grupo de tributação do IBS/CBS (`IBSCBS`), obrigatório para emitentes do Regime Normal (CRT 3) a partir de 03/08/2026. | Configure seu sistema emissor (ERP) para gerar a estrutura de IBS/CBS nos itens da nota ou revise as regras tributárias dos produtos emitidos. |
| **1021** | O grupo de detalhamento do IBS/CBS (`gIBSCBS`) foi informado para um CST que proíbe o preenchimento de imposto detalhado (ex.: operações isentas ou imunes). | Remova a estrutura de tributação detalhada do item no seu emissor ou ajuste o CST para um código tributado compatível com destaque de IBS/CBS. |
| **1025** | O código de classificação tributária (`cClassTrib`) informado é incompatível com o modelo deste documento fiscal (ex.: uso de código exclusivo de NF-e modelo 55 em NFC-e modelo 65). | Consulte a Tabela Oficial de `cClassTrib` e selecione uma classificação tributária permitida para o modelo de documento emitido (NF-e 55 ou NFC-e 65). |
| **1033** | O CST selecionado ou o enquadramento em Compras Governamentais exige o detalhamento da redução de alíquota estadual, mas o grupo `<gIBSUF/gRed>` não foi preenchido. | Preencha o percentual e o valor da redução estadual no grupo de IBS do estado (`gIBSUF/gRed`) no seu emissor ou corrija o CST do item. |
| **1074** | O CST selecionado ou o enquadramento em Compras Governamentais exige o detalhamento da redução de alíquota municipal, mas o grupo `<gIBSMun/gRed>` não foi preenchido. | Preencha o percentual e o valor da redução municipal no grupo de IBS do município (`gIBSMun/gRed`) no seu emissor ou corrija o CST do item. |
| **1079** | O CST selecionado ou a indicação de Compras Governamentais exige a informação da redução de alíquota da CBS, mas o grupo `<gCBS/gRed>` não foi preenchido. | Preencha as informações de redução da CBS no grupo `<gCBS/gRed>` do item no seu sistema emissor ou altere a classificação de CST da operação. |

### Detalhamento e Justificativas Fiscais

1. **Rejeição 1115 (Regra UB12-10):**
   - *Mensagem oficial:* `Rejeição: IBS/CBS não informado [nItem: 999]`
   - *Justificativa:* Trata-se da rejeição com maior potencial de ocorrência no início da vigência em 03/08/2026. Emissores que não atualizarem seus layouts continuarão omitindo o grupo `IBSCBS`. O texto amigável destaca a obrigatoriedade ligada ao CRT 3 (Regime Normal) e aponta diretamente para a configuração do ERP.

2. **Rejeição 1021 (Regra UB13-20):**
   - *Mensagem oficial:* `Rejeição: Grupo IBS/CBS informado indevidamente [nItem: 999]`
   - *Justificativa:* Ocorre por conflito entre a tabela de CST (`ind_gIBSCBS = 0`) e a presença do subgrupo `gIBSCBS`. A orientação foca na escolha entre remover as alíquotas ou alterar o CST.

3. **Rejeição 1025 (Regra UB14-25):**
   - *Mensagem oficial:* `Rejeição: cClassTrib do IBS/CBS não permitido neste modelo de DFe [nItem: 999]`
   - *Justificativa:* A tabela oficial de `cClassTrib` possui colunas separadas para indNFe e indNFCe. Erros ocorrem ao reutilizar classificações da matriz (NF-e) em operações de varejo (NFC-e).

4. **Rejeições 1033, 1074 e 1079 (Regras UB26-20, UB45-20 e UB64-20):**
   - *Mensagens oficiais:* `Rejeição: Não informado o grupo de redução de alíquota Estadual/Municipal/CBS [nItem: 999]`
   - *Justificativa:* São regras espelhadas para cada esfera fiscal (Estadual, Municipal e Federal/CBS). O grupo de redução torna-se obrigatório se `ind_gRed = 1` no CST ou se houver preenchimento do grupo `gCompraGov`. A mensagem amigável especifica o grupo exato a ser corrigido no XML (`gIBSUF/gRed`, `gIBSMun/gRed`, `gCBS/gRed`).

---

## 2. Proposta de Textos para Estados da Interface (UI)

O Validador Lote RTC opera em um modelo de análise em camadas (parser → schema XSD → assinatura → regras de negócio). A comunicação sobre o estado das verificações e a tempestividade das regras embarcadas é crítica para evitar interpretações equivocadas.

### 2.1. Aviso de Base Desatualizada (Validade das Tabelas Fiscais)

O validador depende de tabelas dinâmicas publicadas pela SEFAZ/Portal NF-e (`cClassTrib`, alíquotas, CST). Quando a base embarcada supera determinado limite de idade (ex.: > 30 dias), o usuário deve ser alertado.

- **Título:** Base de Regras Tributárias Antiga
- **Mensagem Amigável:**  
  > A base de tabelas da Reforma Tributária (NT 2025.002) utilizada nesta validação foi atualizada em **{data_base}** (há **{dias_idade}** dias). Como a SEFAZ e o Portal da NF-e realizam atualizações frequentes em alíquotas e classificações, validações feitas com bases antigas podem indicar falsos positivos ou omitir novas rejeições.
- **Ação Sugerida:**  
  > Atualize o aplicativo ou faça o download da versão mais recente das tabelas oficiais no menu *Configurações > Atualizar Tabelas Fiscais*.

---

### 2.2. Camada Não Executada (Ex.: Calculadora RTC Desconectada)

Quando o usuário executa a análise de lote sem a Calculadora de Alíquotas RTC instalada ou ativa (porta 8080), as validações dependentes do motor de cálculo são puladas. É imperativo que o sistema informe claramente o escopo da validação realizada.

- **Título:** Análise Parcial — Camada de Cálculo Não Executada
- **Mensagem Amigável:**  
  > As camadas de **Estrutura XML (Schema XSD)** e **Assinatura Digital** foram validadas com sucesso. No entanto, a camada de **Previsão de Rejeições de Cálculo e Alíquotas** não foi executada porque o serviço local da Calculadora de Alíquotas RTC não está em execução.
- **Ação Sugerida:**  
  > Para validar alíquotas, valores de IBS/CBS e regras de cálculo avançadas, inicie o serviço da Calculadora RTC (porta 8080) e clique em **"Revalidar Lote"**.

---

## 3. Revisão e Melhorias dos Textos XSD existentes (`xsd-translations.properties`)

### 3.1. Diagnóstico do Arquivo Atual

A inspeção do arquivo `src/main/resources/messages/xsd-translations.properties` revelou três principais inconsistências:
1. **Erros sem Ação Sugerida:** As chaves `cvc-datatype-valid.1.2.1`, `cvc-minLength-valid` e `cvc-maxLength-valid` possuem apenas a descrição textual do erro, omitindo o caractere delimitador `|` e a ação recomendada.
2. **Jargão de XML em excesso:** Uso de termos como *"schema oficial exige"*, *"cvc-complex-type"*, *"sequência específica de elementos"*, *"atributo em elemento"*. O usuário final é um contador/analista fiscal e interage com o ERP, não editando arquivos XML manualmente.
3. **Exemplos e Formatação Incompletos:** Na chave `cvc-pattern-valid.pIBSMun`, faltou o exemplo numérico do formato esperado, presente nas chaves análogas `pCBS` e `pIBSUF`.

---

### 3.2. Tabela Comparativa: Atual vs. Proposta

| Chave XSD | Mensagem Atual | Mensagem Proposta | Ação Sugerida Proposta | Justificativa |
|---|---|---|---|---|
| `cvc-pattern-valid.pCBS` | Alíquota da CBS (pCBS) com formato inválido — o schema exige 2 a 4 casas decimais (ex.: 0.90). \| Corrija o campo pCBS do item no seu emissor para o formato aceito (ex.: 0.90). | Alíquota da CBS (`pCBS`) inválida — informe entre 2 e 4 casas decimais (ex.: 0.90 ou 0.9000). | Ajuste o percentual da CBS no cadastro tributário do item no seu ERP para o formato padrão. | Remove jargão ("o schema exige") e adiciona clareza sobre o limite de casas decimais no ERP. |
| `cvc-pattern-valid.pIBSUF` | Alíquota do IBS estadual (pIBSUF) com formato inválido — o schema exige 2 a 4 casas decimais (ex.: 0.05). \| Corrija o campo pIBSUF do item para o formato aceito. | Alíquota do IBS Estadual (`pIBSUF`) inválida — informe entre 2 e 4 casas decimais (ex.: 0.05 ou 0.0500). | Ajuste o percentual do IBS Estadual no item no seu emissor de notas. | Harmonização do texto e exemplo de acionabilidade no ERP. |
| `cvc-pattern-valid.pIBSMun` | Alíquota do IBS municipal (pIBSMun) com formato inválido — o schema exige 2 a 4 casas decimais. \| Corrija o campo pIBSMun do item para o formato aceito. | Alíquota do IBS Municipal (`pIBSMun`) inválida — informe entre 2 e 4 casas decimais (ex.: 0.02 ou 0.0200). | Ajuste o percentual do IBS Municipal no item no seu emissor de notas. | Adiciona exemplo numérico que faltava na versão anterior. |
| `cvc-pattern-valid.cClassTrib` | Código de classificação tributária (cClassTrib) com formato inválido — são 6 dígitos (ex.: 000001). \| Consulte a tabela oficial de cClassTrib e use um código de 6 dígitos. | Código de Classificação Tributária (`cClassTrib`) inválido — deve conter exatamente 6 dígitos numéricos (ex.: 000001). | Consulte a Tabela Oficial de `cClassTrib` e informe um código numérico de 6 dígitos no cadastro fiscal do produto. | Deixa explícito que são dígitos exclusivamente numéricos e orienta o cadastro no ERP. |
| `cvc-pattern-valid` | Valor com formato inválido para o campo apontado na mensagem oficial (casas decimais, tamanho ou padrão). \| Compare o valor do campo com o formato exigido pela NT 2025.002. | Formato do campo divergente do padrão exigido pela Reforma Tributária (tamanho, casas decimais ou caracteres permitidos). | Verifique o campo indicado na mensagem oficial e ajuste o formato no seu sistema emissor. | Substituição de referências abstratas a "NT 2025.002" por orientação direta ao sistema emissor. |
| `cvc-enumeration-valid` | Valor fora da lista de valores permitidos pelo schema oficial. \| Confira o valor permitido na mensagem oficial e ajuste no emissor. | Código ou valor informado não consta na lista de opções permitidas pela legislação. | Consulte a lista de valores válidos (ex.: lista de CST ou tipo de nota) e selecione uma opção válida no seu ERP. | Troca a menção ao "schema oficial" por "lista de opções permitidas pela legislação". |
| `cvc-complex-type.2.4.a` | Elemento inesperado ou fora de ordem — o schema oficial exige uma sequência específica de elementos. \| Confira a ordem dos elementos do grupo (em gIBSCBS a ordem é vBC, gIBSUF, gIBSMun, vIBS, gCBS). | Grupo de campos em ordem incorreta ou com tags incompatíveis com o layout da NF-e. | Atualize o sistema emissor/ERP ou verifique a montagem das tags do grupo apontado na mensagem oficial. | Remove "cvc-complex-type" da explicação e orienta atualização do layout do ERP. |
| `cvc-complex-type.2.4.b` | Elemento obrigatório ausente no grupo apontado pela mensagem oficial. \| Inclua o(s) elemento(s) listado(s) como esperado(s) na mensagem oficial. | Campo obrigatório ausente no grupo apontado pela validação oficial. | Preencha o campo ou subgrupo faltante indicado na mensagem oficial no seu sistema emissor. | Simplifica a linguagem substituindo "elemento" por "campo ou subgrupo". |
| `cvc-datatype-valid.1.2.1` | Valor incompatível com o tipo do campo (número, data ou texto). *(sem ação)* | Tipo de dado incompatível no campo (ex.: texto em campo numérico ou data inválida). | Corrija o preenchimento do campo no seu sistema emissor inserindo o tipo de dado correto (apenas números, data válida, etc.). | **Correção de incompletude:** Adiciona a ação sugerida separada por `|` que faltava no arquivo original. |
| `cvc-minLength-valid` | Conteúdo mais curto que o mínimo exigido pelo campo. *(sem ação)* | Quantidade de caracteres informada é menor que o tamanho mínimo exigido para o campo. | Complete o preenchimento do campo no seu ERP respeitando o número mínimo de caracteres exigido. | **Correção de incompletude:** Adiciona a ação sugerida e esclarece "tamanho mínimo". |
| `cvc-maxLength-valid` | Conteúdo mais longo que o máximo permitido pelo campo. *(sem ação)* | Quantidade de caracteres informada excede o limite máximo permitido para o campo. | Reduza o tamanho do texto ou código no seu sistema emissor para respeitar o limite do campo. | **Correção de incompletude:** Adiciona a ação sugerida e orienta redução no ERP. |
| `cvc-complex-type.4` | Atributo obrigatório ausente no elemento apontado. \| Inclua o atributo exigido (ex.: nItem em det, Id em infNFe). | Identificador obrigatório ausente no campo do documento (ex.: número do item ou chave). | Verifique se o seu sistema emissor está gerando corretamente os identificadores dos itens (`nItem`) e da nota (`Id`). | Traduz "atributo obrigatório" para "identificador obrigatório" com exemplos práticos (`nItem`, `Id`). |
| `signature.missing` | Documento sem assinatura digital (elemento Signature ausente) — esperado em XML de pré-emissão. \| Se o XML já deveria estar assinado, assine antes de transmitir; se é pré-emissão, mantenha o modo pré-emissão ligado. | Assinatura digital ausente no arquivo XML (`Signature`). | Se este lote é de notas já emitidas/transmitidas, assine o XML com certificado digital; se é análise prévia de emissão, mantenha a opção de pré-emissão ativa. | Refina o texto para deixar claro o contexto de lote de pré-emissão vs lote pós-emissão. |

---

## 4. Recomendações para a Integração Futura

1. **Separação de Preocupações:** O arquivo `xsd-translations.properties` continuará sendo a única fonte de tradução dos códigos de erro XSD.
2. **Formato das Rejeições de Negócio:** Para a camada de rejeição da Reforma Tributária (rejeições 1115, 1021, etc.), recomenda-se criar um novo arquivo `rejection-translations.properties` ou estender o mapa existente no formato:
   `<codigoRejeicao>=mensagemAmigavel|acaoSugerida`
3. **Respeito às Restrições do Harness:** Não foi realizada nenhuma alteração direta em `src/main/resources/messages/xsd-translations.properties` nesta etapa de pesquisa/desenho, garantindo isolamento da linha principal de desenvolvimento.
