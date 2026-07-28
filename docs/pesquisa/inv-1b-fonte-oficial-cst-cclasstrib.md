# INV-1b — Fonte oficial da tabela CST × cClassTrib (complementa a INV-1)

**Data:** 27/07/2026
**Origem:** questionamento do dono do projeto — *"tenho certeza que a tabela está disponível para
download, talvez até via endpoint GET de fontes oficiais versionadas"*. Estava certo.

## Conclusão

Uma **única fonte pública, sem autenticação**, entrega tudo que faltava:

```
https://dfe-portal.svrs.rs.gov.br/DFE/ClassificacaoTributaria
```

A página redireciona de `/DFE/TabelaClassificacaoTributaria` e embute o conjunto completo numa
variável JavaScript `dadosOriginais`, em JSON. São ~4,4 MB de HTML, dos quais o JSON é a maior
parte.

Isso **corrige duas conclusões anteriores** — a da INV-1 (que recomendava extrair de PDF ou montar
tabela auxiliar à mão) e a da INV-2 (que declarou o vínculo NCM × cClassTrib inviável). Ambas
investigaram a API da Calculadora e o portal nacional; nenhuma olhou o JSON embutido na página da
SVRS.

## O que o conjunto contém

| Nível | Quantidade | Conteúdo |
|---|---|---|
| CST | 18 | 8 indicadores + vigência |
| cClassTrib (aninhadas por CST) | 164 | 27 indicadores + percentuais + vigência |
| Anexos de NCM/NBS | 4.628 entradas | 1.792 NCMs e 190 NBS distintos, ligados a 34 classificações |

### Indicadores por CST — o que faltava

| Campo no JSON | Nome na NT | Governa |
|---|---|---|
| `IndExigeTrib` | `ind_gIBSCBS` | rejeições **1021**, **1022** |
| `IndReducaoAliq` | `ind_gRed` | rejeições **1033**, **1074**, **1079** |
| `IndDiferimento` | `ind_gDif` | grupos de diferimento |
| `IndMonofasica` | `ind_gIBSCBSMono` | regime monofásico |
| `IndReducaoBc`, `IndTransferenciaCred`, `IndCredPresIbsZfm`, `IndAjusteCompet` | idem | demais grupos |

Distribuição medida: `IndExigeTrib` verdadeiro em 11 dos 18 CSTs; `IndReducaoAliq` verdadeiro em
apenas **3** (011, 200, 515).

### Por que a granularidade importa

O `IndReducaoAliq` é verdadeiro para **3 CSTs**. O campo `possuiPercentualReducao` da Calculadora,
que esta spec chegou a supor equivalente, é verdadeiro para **60 de 161 classificações**. Usar o
segundo no lugar do primeiro produziria falso positivo em escala — acusaria grupo de redução
ausente em dezenas de classificações que não o exigem. A confirmação empírica desses dois números
é a evidência mais forte de que os campos **não** são intercambiáveis.

### Vínculo NCM × cClassTrib

Cada classificação com anexo traz `Anexos[]` com `CodNcmNbs`, `TipoCodigo` (`NCM` ou `NBS`) e
vigência própria. É exatamente o dado que a Calculadora não expõe (`/dados-abertos/ncm-aplicaveis`
devolve 404) e que fazia a trava ser classificada como inviável.

Ressalva: só 34 das 164 classificações têm anexo. Para as demais, a ausência de anexo significa
"sem restrição por nomenclatura" ou "restrição não publicada nesta tabela" — a distinção precisa
ser confirmada antes de transformar ausência de anexo em veredito.

## Amostra destilada

`dados/cst-cclasstrib-svrs.json` (420 KB) — os campos relevantes ao escopo IBS/CBS nos modelos 55 e
65, com os anexos de NCM/NBS preservados. O bruto de 4,4 MB não foi versionado; é reproduzível a
partir da URL acima.

## Recomendação para a D-025

Substituir a recomendação anterior (tabela auxiliar mantida à mão a partir de PDF) por:

**Ingestão automatizada da tabela da SVRS**, numa task Gradle análoga à `updateSchemas`, extraindo o
JSON de `dadosOriginais` da página e gravando a versão destilada em resources. Fora do build normal
— rede só nessa task, como já é a política do projeto.

Pontos a verificar antes de implementar:

- **Estabilidade do formato**: o JSON está embutido em HTML, não é contrato de API. Uma mudança de
  layout da página quebra a extração. Mitigar com validação de esquema na task e falha ruidosa.
- **Versionamento**: o conjunto traz `DthPublicacao`, `DthIniVig` e `DthFimVig` por registro, o que
  permite filtrar por data do fato gerador — melhor que versionar o arquivo inteiro.
- **Fonte alternativa**: verificar se `consumo.tributos.gov.br` expõe o mesmo dado como API REST
  formal, o que seria mais estável que raspar HTML.
