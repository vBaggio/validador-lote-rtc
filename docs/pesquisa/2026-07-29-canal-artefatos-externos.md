# Pesquisa provisória — canal de artefatos externos

Data da investigação: 29/07/2026. Este registro orienta o B6 e não substitui a decisão de
produto nem a conferência da fonte oficial no momento de cada atualização.

## Síntese executiva

O produto pode manter localmente schemas e tabelas atualizados sem enviar XMLs, CNPJ, chaves ou
telemetria. A implementação precisa tratar atualização como **instalação transacional de artefato
normativo**, não como simples download: descobrir, baixar em staging, validar, registrar hash,
ativar atomicamente e preservar a última versão íntegra.

O Portal Nacional da NF-e é a autoridade para schemas. O SVN do ACBr está atualizado e é um bom
espelho técnico, mas não deve ser promovido a autoridade normativa. As tabelas da SVRS são uma
fonte dinâmica já usada pelo projeto; a URL atualmente configurada no Gradle mudou e falha, o que
reforça a necessidade de um adaptador com detecção explícita de mudança de formato/endereço.

## Estado atual do repositório

| Artefato | Estado embarcado | Situação encontrada |
|---|---|---|
| Schemas NF-e/NFC-e | 14 XSDs; `engineVersion=1.2.4`, `baseVersion=V0039 (2026-07-08)`, extraídos em 26/07/2026 do JAR da Calculadora | A closure NF-e em `tmp/Schemas/NFe` tem 201 XSDs; quatro dos cinco XSDs utilizados pelo documento diferem da base embarcada. |
| Acervo fornecido | `tmp/Schemas` contém 1.872 XSDs, em 15 famílias de DF-e | É material de referência, não prova de proveniência por si só. O app atual só suporta `NFe`, `nfeProc` e `enviNFe`. |
| Tabelas CST/cClassTrib | 18 CSTs e 164 classificações; extração em 27/07/2026 | Vêm da SVRS e alimentam o `RuleEngine`; alteração pode mudar previsões de rejeição. |
| Calculadora | Não é executada pelo app; somente forneceu os schemas embarcados | Endpoint de descoberta ainda responde e devolve URL pré-assinada do ZIP, mas o motor de cálculo é escopo da v1. |

## Schemas NF-e/NFC-e

### Fonte canônica

O [Portal Nacional — Esquemas XML](https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?AspxAutoDetectCookieSupport=1&tipoConteudo=BMPFMBoln3w%3D)
publica as versões oficiais em uso. Na consulta de 29/07/2026, a página lista, entre outras,
`010e_v1.01` (NT 2025.002 v1.40, NT 2026.002 e NT 2026.003) e `010d_v1.02` (CNPJ alfanumérico).
São perfis paralelos; não é seguro escolher apenas pelo maior nome, letra ou data. O selecionador
deve reconhecer a seção "VERSÕES OFICIAIS (em uso)", identificar o perfil compatível e confirmar a
closure dos roots suportados.

A [Receita Federal](https://www.gov.br/receitafederal/pt-br/acesso-a-informacao/perguntas-frequentes/sped/dere/dere/7-vigencia-transicao-e-documentacao-de-suporte/7-2-onde-obter-os)
informa que esquemas e documentação técnica estão disponíveis exclusivamente nos portais oficiais
do SPED e do Portal Nacional da Reforma Tributária.

### Evidência local e espelho ACBr

Os XSDs `DFeTiposBasicos_v1.00.xsd`, `leiauteNFe_v4.00.xsd`,
`tiposBasico_v4.00.xsd`, `nfe_v4.00.xsd` e `xmldsig-core-schema_v1.01.xsd` da pasta
`tmp/Schemas/NFe` foram comparados com o SVN do ACBr. Os cinco SHA-256 coincidem byte a byte. A
consulta SVN aponta a revisão 47477, com última alteração na pasta em 28/07/2026; o log registra a
atualização da NT 2025.002 v1.40 na revisão 46981.

Endereço verificado do espelho:
[`https://svn.code.sf.net/p/acbr/code/trunk2/Exemplos/ACBrDFe/Schemas/NFe/`](https://svn.code.sf.net/p/acbr/code/trunk2/Exemplos/ACBrDFe/Schemas/NFe/).

O ACBr é útil para disponibilidade, inventário e alerta rápido. Não há, contudo, assinatura ou
declaração oficial de vigência publicada por ele. Política recomendada:

1. Portal Nacional descobre e confirma a versão oficial.
2. Portal e ACBr com mesma árvore/hash: ambos podem servir como transporte.
3. ACBr divergente/adiantado: registrar candidata não confirmada e não ativá-la automaticamente.
4. Portal indisponível: manter a última base oficial válida; nunca baixar a confiança para trocar
   automaticamente uma base normativa.

## Tabelas fiscais

O projeto hoje usa a tabela da SVRS para CST/cClassTrib. A [página de documentos da
SVRS](https://dfe-portal.svrs.rs.gov.br/Nfe/Documentos) aponta a tabela de classificação tributária
e o portal explica que mudanças nessas tabelas refletem imediatamente no motor de validação.

O endpoint gravado em `build.gradle`,
`https://dfe-portal.svrs.rs.gov.br/DFE/ClassificacaoTributaria`, retornou HTTP 404 em 29/07/2026.
O endpoint atual verificado,
`https://dfe-portal.svrs.rs.gov.br/DFE/TabelaClassificacaoTributaria`, responde HTTP 200 e contém
o payload `dadosOriginais` usado pelo extrator existente, mas a página cresceu para cerca de 4,1 MB
e inclui campos/anexos adicionais. Isto deve ser tratado como mudança real de contrato da fonte:
não basta trocar a URL sem teste de regressão e limites de tamanho.

O adaptador de tabelas do B6 precisa preservar as guardas já existentes: campos obrigatórios,
tipos, códigos únicos, datas válidas e recusa de redução brusca de cobertura. Como uma base
incompleta pode levar a falso positivo ou a falso negativo, falha de atualização deve manter a
tabela ativa anterior.

## Calculadora

O endpoint estável de descoberta
`https://piloto-cbs.tributos.gov.br/servico/calculadora-consumo/api/calculadora/download/url?platform=default`
respondeu HTTP 200 e retornou uma URL pré-assinada para o ZIP. A URL final expira; por isso não
serve como identificador duradouro de versão nem como valor fixo em manifesto.

O aplicativo **não calcula tributos hoje**. O pacote tem cerca de 250 MB e seu motor será integrado
na v1 como processo filho para validar valores. No B6 ele deve constar no catálogo com fonte,
última verificação e status "não instalado / não aplicável ao v0", mas não deve ser baixado ou
executado em segundo plano.

## Arquitetura recomendada

### Catálogo e manifesto

Criar um catálogo local com os IDs `NFE_SCHEMAS`, `FISCAL_TABLES` e `CALCULATOR`. Cada versão
instalada deve carregar: fonte efetiva, versão declarada, data de publicação quando disponível,
SHA-256 do download e/ou árvore, data da última verificação, data de ativação, resultado e erro
mais recente. O diretório fica fora da instalação, por exemplo
`~/.validador-lote-rtc/artifacts/<id>/<versão>/`.

O aplicativo conserva os artefatos embarcados como bootstrap e fallback. A referência à versão
ativa deve ser pequena e atualizada por troca atômica; payloads já ativos jamais são sobrescritos.

### Segurança e consistência

- HTTPS e allowlist estrita de hosts; redirecionamento só para host permitido.
- Timeouts, limite de download, limite de arquivos e bytes descompactados.
- Normalização de caminho, rejeição de zip-slip, links simbólicos e duplicatas.
- Schemas: compilar os três roots suportados com o resolver fechado e exercitar fixtures antes de
  ativar.
- Tabelas: validação estrutural e guardas de cobertura antes de construir a instância consumida pelo
  `RuleEngine`.
- Falha, corrupção, concorrência ou falta de rede nunca impedem a validação local com a última base
  boa.

### Experiência do usuário

O primeiro boot deve abrir funcional com os artefatos embarcados e disparar a consulta em segundo
plano. O instalador não baixa nada, preservando instalação offline. A rotina periódica proposta é
uma checagem diária, com ação manual "Verificar agora".

Uma nova base deve, por padrão, ser aplicada no boot seguinte para que um lote não seja validado
com versões misturadas. A UI expõe em uma tela de fontes externas a versão ativa, fonte, hash
abreviado, data da última atualização, última verificação e erro; o rodapé mostra apenas o resumo.

## Recomendação de escopo B6

1. Catálogo, manifesto, staging, rollback e carregamento da base ativa.
2. Atualização segura dos schemas NF-e/NFC-e e upgrade da base embarcada.
3. Correção e atualização transacional das tabelas fiscais.
4. Rotina de boot, consulta periódica, tela de status e manutenção Gradle padronizada.
5. ACBr como espelho condicionado à confirmação oficial.

Ficam para blocos posteriores: suporte semântico aos outros 1.671 XSDs fora da família NFe,
integração do motor de cálculo da Calculadora, novas regras fiscais e publicação da primeira
release/MSI.

## Decisões de produto ainda abertas

- Intervalo padrão da checagem: a recomendação é 24 horas, mas pode ser ajustado.
- Aplicação imediata: recomendação é somente no próximo boot; hot reload exige sincronização de
  `SchemaValidatorEngine` e `RuleEngine` e deve ser permitido apenas sem lote em execução.
- Política de contingência do ACBr: recomendação é não ativar automaticamente uma árvore que o
  Portal ainda não confirmou.
- Retenção: recomendação é manter a base ativa e a anterior, removendo versões antigas apenas após
  limite explícito e nunca durante validação.
