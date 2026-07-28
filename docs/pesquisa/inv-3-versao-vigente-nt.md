# INV-3 — Como saber a versão vigente da Nota Técnica

| | |
|---|---|
| **Status** | Concluída |
| **Data** | 27/07/2026 |
| **Relacionado** | §6 da spec [`2026-07-27-camada-rejeicao-design.md`](../superpowers/specs/2026-07-27-camada-rejeicao-design.md), Decisão D-005 |
| **Entrega** | Mapeamento de mecanismos de verificação da versão vigente da NT 2025.002 e recomendação para a spec |

---

## 1. Resumo Executivo

1. **Inexistência de APIs públicas ou feeds RSS/Atom**: Os portais oficiais (`nfe.fazenda.gov.br` e `dfe-portal.svrs.rs.gov.br`) **não possuem** endpoints JSON/REST documentados nem feeds RSS/Atom para acompanhamento automatizado de revisões de Notas Técnicas.
2. **Índice HTML Previsível e Perene**: O Portal Nacional da NF-e possui uma página de índice permanente (`https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=04BIflQt1aY=`) que lista todas as Notas Técnicas em ordem cronológica com versão e data de publicação.
3. **Calculadora (`versaoDb`) não indica a versão da NT**: O endpoint `/api/calculadora/dados-abertos/versao` expõe `versaoDb` (ex: `V0039`) e `dataVersaoDb` (ex: `2026-07-08`), que refletem atualizações da **base de dados/tabelas**, mas **não citam a versão da NT 2025.002**.
4. **Recomendação Concreta**: Manter a abordagem híbrida descrita a seguir:
   - **Offline (Padrão)**: Aviso de obsolescência por idade (base/regras com mais de 60 dias).
   - **Online (Opt-in Barato)**: Botão/opção explícita "Verificar Atualização Online" que consulta o índice HTML do Portal da NF-e via HTTP GET de baixo custo (~200 KB) com timeout curto (5s), respeitando a privacidade (Princípio 1).

---

## 2. Diagnóstico das Fontes Oficiais e APIs

### 2.1 Portal Nacional da NF-e (`nfe.fazenda.gov.br`)

O Portal da NF-e organiza a documentação em páginas com identificadores estáticos de conteúdo (`tipoConteudo`).

- **Página de Índice de Notas Técnicas**:
  - **URL**: `https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=04BIflQt1aY=`
  - **Formato**: HTML contendo tabela de hiperlinks com descrição textual completa.
  - **Comportamento**: Mantido ativamente pelo ENCAT/SEFAZ. Toda nova revisão de NT é inserida no topo da lista.
  - **Exemplo de registros extraídos empiricamente**:
    ```text
    Nota Técnica 2025.002 v.1.50 - Publicada em 03/06/2026
    Nota Técnica 2025.002 v.1.40 - Publicada em 20/05/2026
    Nota Técnica 2025.002 v.1.36 - Publicada em 30/04/2026
    Nota Técnica 2025.002 v.1.35 - Publicada em 31/03/2026
    Nota Técnica 2025.002 v.1.34 - Publicada em 04/12/2025
    ...
    Nota Técnica 2025.002.v.1.00 - Publicada em 28/03/2025
    ```

- **Outras páginas relevantes no portal**:
  - `listaConteudo.aspx?tipoConteudo=UTx2da6sXiA=`: Avisos do Portal.
  - `listaConteudo.aspx?tipoConteudo=hXzemuyNHW4=`: Informes Técnicos (ex: IT 2025.002 v1.60).
  - `listaConteudo.aspx?tipoConteudo=BMPFMBoln3w=`: Esquemas XML (XSD).

### 2.2 Portal SVRS (`dfe-portal.svrs.rs.gov.br`)

- **URL de Documentos**: `https://dfe-portal.svrs.rs.gov.br/Nfe/Documentos`
- **Formato**: HTML com chamadas JavaScript `download_arquivo_estatico(...)` para arquivos `.zip` de pacotes de schemas (ex: `PL_010b_NT2025_002_v1.30.zip`).
- **Avaliação**: Útil para download de XSDs, porém a identificação de versão da NT depende de parse do nome de arquivos ZIP de schemas, sendo menos direta e padronizada do que o Portal Nacional.

### 2.3 Teste de Feeds RSS / Atom / APIs Públicas

Foram testadas diversas variações de URLs comuns para feeds RSS e APIs em ambos os portais:
- `https://dfe-portal.svrs.rs.gov.br/Nfe/Rss` -> `HTTP 404`
- `https://dfe-portal.svrs.rs.gov.br/Nfe/Feed` -> `HTTP 404`
- `https://www.nfe.fazenda.gov.br/portal/rss.xml` -> `HTTP 404`

**Conclusão**: Não existem feeds RSS/Atom ou APIs JSON/REST oficiais para consulta de Notas Técnicas.

---

## 3. Análise da Calculadora (`versaoDb` × NT 2025.002)

O endpoint da Calculadora oficial da RFB/SEFAZ `/api/calculadora/dados-abertos/versao` retorna:

```json
{
  "versaoApp": "1.2.4",
  "versaoDb": "V0039",
  "descricaoVersaoDb": "Ajustes na tabela NCM para incluir 20 novos registros e extinguir 5 registros.",
  "dataVersaoDb": "2026-07-08",
  "ambiente": "offline"
}
```

### Histórico de Revisões da NT 2025.002 (13 revisões em 16 meses)

| Versão NT | Data Publicação | Intervalo |
|---|---|---|
| v1.00 | 28/03/2025 | — |
| v1.01 | 15/04/2025 | 18 dias |
| v1.10 | 09/06/2025 | 55 dias |
| v1.20 | 30/07/2025 | 51 dias |
| v1.30 | 03/10/2025 | 65 dias |
| v1.31 | 11/11/2025 | 39 dias |
| v1.32 | 25/11/2025 | 14 dias |
| v1.33 | 02/12/2025 | 7 dias |
| v1.34 | 04/12/2025 | 2 dias |
| v1.35 | 31/03/2026 | 117 dias |
| v1.36 | 30/04/2026 | 30 dias |
| v1.40 | 20/05/2026 | 20 dias |
| v1.50 | 03/06/2026 | 14 dias |

### Correlação Encontrada

- `versaoDb` e `descricaoVersaoDb` indicam mudanças na **base de dados das tabelas tributárias** (NCM, NBS, cClassTrib), não na **versão normativa das regras de validação da NT**.
- Uma atualização de tabela na Calculadora (ex: `V0039` em 08/07/2026) pode ocorrer sem alteração no texto da NT, e vice-versa.
- **Conclusão**: Não é possível determinar a versão da NT apenas lendo a `versaoDb` da Calculadora.

---

## 4. Estratégia de Verificação Online Opt-in (Algoritmo Leve)

Para implementar a checagem online opt-in de forma barata e sem dependências externas:

1. **Requisição HTTP**:
   - **URL**: `https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=04BIflQt1aY=`
   - **Método**: `GET`
   - **Timeout**: 5000 ms (5 segundos).
   - **Headers**: `User-Agent` de navegador comum (para evitar bloqueios por WAF da SEFAZ).
   - **SSL**: Tratar exceções de SSL para suporte à cadeia de certificados ICP-Brasil.

2. **Regex de Extração**:
   ```regex
   Nota\s*Técnica\s*2025\.002[^\w]*v\.?([0-9\.]+)\s*-\s*Publicada\s*em\s*(\d{2}/\d{2}/\d{4})
   ```

3. **Lógica de Comparação**:
   - Capturar todas as correspondências. A primeira ocorrência no HTML é a versão mais recente publicada.
   - Comparar a versão capturada (ex: `1.50`) com a versão transcrita embarcada no Validador (ex: `1.50`).
   - Se `versaoRemota > versaoLocal`, sinalizar atualização disponível com data de publicação.

---

## 5. Recomendação Concreta para a §6 da Spec

Proposta de alteração na redação da **Seção 6 (Versionamento e obsolescência)** da spec (`2026-07-27-camada-rejeicao-design.md`):

```markdown
## 6. Versionamento e obsolescência

A Calculadora expõe `versaoApp` e `versaoDb` (ex: V0039), que indicam a versão dos dados das tabelas,
mas não a versão da Nota Técnica. As regras de documento (presença, CRT, vigência) ficam atadas à
versão da NT transcrita no código.

Estratégia de controle de obsolescência:

1. **Proveniência em todos os relatórios**:
   - Exibir na UI e no cabeçalho/rodape dos relatórios a versão da NT transcrita (ex: NT 2025.002 v1.50),
     a versão da base de tabelas (ex: V0039) e a data de extração dos dados.

2. **Aviso por Idade (Offline / Padrão)**:
   - Se a base de regras/tabelas locais tiver mais de 60 dias desde a data de publicação/extração,
     exibir alerta preventivo na UI: *"Base de regras com mais de 60 dias. Recomenda-se verificar se há nova versão da NT."*
   - Funciona 100% offline, sem violar o Princípio 1.

3. **Verificação Online Opt-in (Manual / Voluntária)**:
   - Disponibilizar na tela de configurações ou via botão "Verificar atualizações da NT":
   - Requisição leve (HTTP GET, timeout 5s) ao Portal da NF-e (`listaConteudo.aspx?tipoConteudo=04BIflQt1aY=`).
   - Extrai a versão vigente da NT 2025.002.
   - Se houver versão mais recente no portal, exibe aviso: *"Nova versão da NT 2025.002 identificada no Portal da NF-e (v1.60 de DD/MM/AAAA). Sua versão local é v1.50."*
   - A requisição NUNCA é executada automaticamente no boot ou durante a validação; apenas por ação explícita do usuário.
```

---

## 6. Próximos Passos e Integração

- Esta investigação encerra a tarefa **INV-3**.
- A recomendação para a §6 da spec está pronta para apreciação e eventual merge pelo mantenedor.
- Nenhuma alteração em código de produção (`src/main/java`) foi realizada, mantendo total isolamento da linha principal de desenvolvimento.
