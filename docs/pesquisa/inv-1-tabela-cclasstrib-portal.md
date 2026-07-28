# Investigação — Tabela Oficial cClassTrib do Portal NF-e (INV-1)

## 1. Resumo Executivo

Esta investigação mapeou e analisou as publicações da Tabela de Classificação Tributária (`cClassTrib`), Tabela de Situação Tributária (`CST`) e Tabela de Crédito Presumido (`cCredPres`) nos portais oficiais da SEFAZ/RFB (Portal Nacional da NF-e e SVRS).

### Principais Descobertas:
1. **Localização dos Indicadores por CST (`ind_gIBSCBS` e `ind_gRed`)**:
   Os indicadores `ind_gIBSCBS` (obrigatoriedade do grupo IBS/CBS na regra `UB13-30` / rejeição 1022) e `ind_gRed` (obrigatoriedade de grupo de redução de alíquota / rejeições 1033, 1074, 1079) **não pertencem à Tabela cClassTrib**, mas sim à **Tabela de Situação Tributária (Tabela CST)** publicada no mesmo Informe Técnico (IT 2025.002).
2. **Formato das Publicações Oficiais**:
   - No **Portal Nacional da NF-e** (`nfe.fazenda.gov.br` > Documentos > Informes Técnicos / Diversos), a publicação oficial consolidadora é feita em formato **PDF** (**Informe Técnico IT 2025.002**, ex: v1.60 de 23/06/2026, ~4.5 MB). Não há arquivo XLSX ou CSV estático publicado diretamente nessa seção do Portal Nacional.
   - Nos **Portais Interativos da SVRS / RTC** (`dfe-portal.svrs.rs.gov.br` e `consumo.tributos.gov.br`), as tabelas são exibidas via interface Web com dados JSON embutidos nas páginas HTML (`dadosOriginais`).
   - Os serviços REST/JSON automatizados da SVRS (`/CFF/Servicos`) exigem autenticação via **Certificado Digital ICP-Brasil** (e-CNPJ/e-CPF).
3. **Estabilidade de URLs e Versionamento**:
   As URLs de download dos PDFs no Portal da NF-e contêm hash opaco (ex: `exibirArquivo.aspx?conteudo=jxTMMQeEVM8=`), alterando a cada nova publicação. A cada 4 a 6 semanas é publicado um novo Informe Técnico com atualizações de versão (v1.00 a v1.60).
4. **Recomendação de Automação vs. Atualização Manual**:
   - **Inviável automatizar o download direto do Portal NF-e no build sem rede / CI**: Devido ao formato PDF no Portal Nacional, instabilidade de hashes de URL e exigência de certificado digital nos serviços JSON da SVRS.
   - **Recomendação Técnica Operacional (Decisão D-025)**:
     - **Manter a ingestão via Calculadora RTC (D-005 / task `updateTables`)**: A Calculadora Oficial da RFB já expõe os endpoints abertos JSON (`/dados-abertos/classificacoes-tributarias/cbs-ibs` e `/situacoes-tributarias/cbs-ibs`) sem necessidade de certificado.
     - **Para os indicadores de CST (`ind_gIBSCBS`, `ind_gRed`) que faltam na Calculadora**: Materializar uma tabela estática auxiliar JSON (`cst-indicators.json`) extraída do IT 2025.002 e mantida em `src/main/resources/tables/`. O download/atualização dessa tabela pode ser feito via script utilitário de scraping do JSON público da página da SVRS durante manutenção programada.

---

## 2. Detalhamento das Fontes e URLs Encontradas

| Fonte / Portal | URL / Endereço | Formato | Autenticação | Estabilidade da URL |
|---|---|---|---|---|
| **Portal Nacional NF-e** (Documentos → Informes Técnicos) | `https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=hXzemuyNHW4=` | HTML (Lista) | Nenhuma | URL do índice é fixa, mas links internos mudam por hash |
| **Download IT 2025.002 v1.60** (Portal NF-e) | `https://www.nfe.fazenda.gov.br/portal/exibirArquivo.aspx?conteudo=jxTMMQeEVM8=` | PDF (~4.5 MB) | Nenhuma | Instável (hash `jxTMMQeEVM8=` muda a cada release) |
| **SVRS — Tabela cClassTrib & CST** | `https://dfe-portal.svrs.rs.gov.br/DFE/TabelaClassificacaoTributaria` | Web / HTML + JSON embutido (`dadosOriginais`) | Nenhuma | URL fixa |
| **SVRS — Serviços JSON (CFF)** | `https://dfe-portal.svrs.rs.gov.br/CFF/Servicos` | REST API (JSON) | **Certificado Digital** (ICP-Brasil) | URL fixa |
| **Plataforma RTC / Calculadora** | `https://consumo.tributos.gov.br/servico/calcular-tributos-consumo/calculadora/classificacoestributarias` | Web / REST API | Nenhuma | URL fixa |

---

## 3. Estrutura de Colunas e Comparativo

### 3.1 Localização exata das colunas no IT 2025.002 v1.60

No Informe Técnico **IT 2025.002**, as regras de obrigatoriedade são divididas em tabelas distintas:

#### **Tabela 03 — Tabela CST (Situação Tributária)**
Traz os indicadores de presença de grupos no XML:
- `CST-IBS/CBS`: Código de 3 dígitos (ex: 000, 010, 011, 200, 222, etc.).
- `Descrição CST-IBS/CBS`: Texto explicativo.
- `ind_gIBSCBS`: (Booleano) Indica se deve ser preenchido o grupo padrão de IBS e CBS no XML (**Governa a regra UB13-30 / rejeição 1022**).
- `ind_gIBSCBSMono`: (Booleano) Indica se deve ser preenchido grupo de regime monofásico.
- `ind_gRed`: (Booleano) Indica necessidade dos grupos de redução de alíquota (**Governa as rejeições 1033, 1074, 1079**).
- `ind_gDif`: (Booleano) Indica se devem ser informados grupos de diferimento.
- `ind_gTransfCred`: (Booleano) Indica se deve ser informado grupo de transferência de crédito.
- `ind_gCredPresIBSZFM`: (Booleano) Crédito presumido de IBS na ZFM (art. 450, § 1º, LC 214/25).
- `ind_gAjusteCompet`: (Booleano) Ajustes por competência.
- `ind_RedutorBC`: (Booleano) Redução de base de cálculo.

#### **Tabela 02 — Tabela cClassTrib (Classificação Tributária)**
Traz a fundamentação legal por item (6 dígitos):
- `CST-IBS/CBS`: 3 primeiros dígitos.
- `cClassTrib`: Código de 6 dígitos.
- `Nome cClassTrib` e `Descrição cClassTrib`.
- `LC 214/25` / `Regulamento CBS` / `Regulamento IBS`: Dispositivos legais atrelados.
- `Tipo de Alíquota`: Fixa, Padrão, Sem Alíquota, Uniforme Nacional, Uniforme Setorial.
- `pRedIBS` e `pRedCBS`: Percentuais de redução oficiais.
- Indicadores de exigência por tipo de operação: `ind_gTribRegular`, `ind_gCredPresOper`, `ind_gMonoPadrao`, `ind_gMonoReten`, `ind_gMonoRet`, `ind_gMonoDif`, `ind_gpBioDiferenca`, `ind_gEstornoCred`.
- `tpRBSN`: Formas de receita bruta do Simples Nacional.
- `dIniVig` / `dFimVig` / `DataAtualização`.
- Indicadores por modelo de documento: `indNFe`, `indNFCe`, `indCTe`, `indCTeOS`, `indBPe`, `indNF3e`, `indNFCom`, `indNFSe`, `indNFGas`, `indDERE`, `indDIR`, `indDUIMP`, `indNFeABI`.
- `ANEXO`: Número do anexo correspondente na LC 214/2025 (formato 9XXXY).
- `Link`: Hiperlink para a legislação.

### 3.2 Comparação entre a Tabela do Portal e a Calculadora (§5.1 da Spec)

| Campo / Indicador | Presente na Calculadora (`/dados-abertos`) | Presente no Portal (IT 2025.002) | Observações |
|---|---|---|---|
| Metadados cClassTrib (`possuiPercentualReducao`, `pRed`, etc.) | **Sim** (18 campos na visão em massa) | **Sim** (Tabela 02 cClassTrib) | Equivalentes. |
| Modelo DF-e (`tiposDfeClassificacao` / `validoParaSiglaDfeInformado`) | **Sim** | **Sim** (`indNFe`, `indNFCe`, etc.) | Governa a rejeição **1025**. |
| `ind_gIBSCBS` (Obrigatoriedade grupo IBS/CBS por CST) | **Não** | **Sim** (Tabela 03 CST) | Governa a rejeição **1022** (regra `UB13-30`). |
| `ind_gRed` (Exigência do grupo de redução por CST) | **Não** (apenas `possuiPercentualReducao` no cClassTrib) | **Sim** (Tabela 03 CST) | Governa as rejeições **1033**, **1074**, **1079**. |
| Fundamentação Legal (`LC 214/25`, Decreto, Resolução) | **Não** | **Sim** | Útil para enriquecer os relatos amigáveis ao usuário (UX-2). |
| Anexo de NCM/NBS vinculados (`ANEXO`) | **Parcial** (`nomenclatura`) | **Sim** (`ANEXO` 9XXXY) | Detalha os anexos da LC 214/2025. |

---

## 4. Avaliação de Viabilidade e Recomendação Operacional

### 4.1 Inviabilidade de Automação no Build Sem Conexão/CI
1. **Formato PDF**: O Portal Nacional publica o Informe Técnico consolidado exclusivamente em PDF. Fazer o parsing de tabelas de PDF no Gradle/CI introduziria alto acoplamento e fragilidade.
2. **URLs com Hashes Dinâmicos**: A URL do PDF no Portal da NF-e muda a cada versão publicada (ex: `conteudo=jxTMMQeEVM8=`). Não há URL estática direta como `cclasstrib.xlsx`.
3. **Exigência de Certificado Digital**: As APIs REST JSON oficiais da SVRS exigem certificado digital ICP-Brasil, o que inviabiliza builds automatizados em ambientes de CI/CD padrão.

### 4.2 Recomendação Objetiva para a Decisão D-025

1. **Abordagem Primária**: Continuar utilizando as tabelas embarcadas geradas pela Calculadora RTC via task `updateTables` (D-005).
2. **Tratamento da Regra 1022 (`UB13-30`) e Regras de Redução (1033, 1074, 1079)**:
   - Embarcar uma tabela JSON estática auxiliar em `src/main/resources/tables/cst-indicators.json` contendo o de-para dos 12 CSTs e seus indicadores (`ind_gIBSCBS`, `ind_gRed`, etc.).
   - A atualização dessa tabela auxiliar pode ser realizada de forma semiautomática lendo o JSON exposto publicamente na página HTML da SVRS (`dadosOriginais`), por meio de um script Python utilitário durante tarefas de manutenção da base.
