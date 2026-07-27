# Investigação: Vínculo NCM x cClassTrib (INV-2)

## 1. O que dispara e o que não dispara a trava

Nos testes realizados na API da Calculadora (endpoint `POST /api/calculadora/regime-geral`), constatamos que a trava de fato existe, mas não se aplica a todas as classificações.

### Não dispara:
Para a classificação `200009` (Fornecimento dos medicamentos registrados na Anvisa), o envio de um NCM incompatível como `61091000` (Camisetas) **não dispara** nenhum erro. A calculadora processou a alíquota zero (pRedAliq: 100.00) normalmente.
*Payload exato testado:*
```json
{
  "id": "test",
  "versao": "0.0.1",
  "dhFatoGerador": "2026-08-03T00:00:00-03:00",
  "municipio": 4314902,
  "uf": "RS",
  "itens": [
    {
      "numero": 1,
      "ncm": "61091000",
      "cst": "200",
      "cClassTrib": "200009",
      "baseCalculo": 100.0,
      "quantidade": 1
    }
  ]
}
```

### Dispara (REG-011 / `ncm-nao-vinculada`):
Para a classificação `200004` (Fornecimento de dispositivos médicos - Anexo XII), que possui uma lista fechada legal de NCMs, o envio do mesmo NCM `61091000` **dispara** o erro.
*Payload exato testado:*
```json
{
  "id": "test",
  "versao": "0.0.1",
  "dhFatoGerador": "2026-08-03T00:00:00-03:00",
  "municipio": 4314902,
  "uf": "RS",
  "itens": [
    {
      "numero": 1,
      "ncm": "61091000",
      "cst": "200",
      "cClassTrib": "200004",
      "baseCalculo": 100.0,
      "quantidade": 1
    }
  ]
}
```
*Resposta da Calculadora (422 Unprocessable Entity):*
```json
{
  "type": "http://localhost/errors/ncm-nao-vinculada",
  "title": "NCM não vinculada",
  "status": 422,
  "detail": "NCM de código 61091000 não vinculada à Classificação Tributária de código 200004 (CBS e IBS)",
  "instance": "/api/calculadora/regime-geral",
  "timestamp": "..."
}
```

## 2. A Regra na Nota Técnica

Buscando pelas regras da NT (via `grep` no catálogo de regras `nt-regras-catalogo.md`), **não existe nenhuma regra de rejeição para IBS/CBS** que valide o vínculo NCM x cClassTrib. 
A única regra que cruza classificação e NCM é a **UB01-30 (Rejeição 1013)**, que é específica para **Imposto Seletivo**: *"É exigido o uso do Imposto Seletivo para esta classificação da operação para este NCM"*. O Imposto Seletivo está explicitamente fora do escopo deste projeto.

## 3. Swagger e Endpoints de Vínculo

O Swagger da calculadora (`/api/v3/api-docs` verificado) expõe as classificações e NCMs, e possui o endpoint `/calculadora/dados-abertos/nbs-aplicaveis`, porém **não possui nenhum endpoint** como `/ncm-aplicaveis` para retornar a lista de NCMs válidos por `cClassTrib`. As propriedades da classificação (ex: `/api/calculadora/dados-abertos/classificacoes-tributarias/cbs-ibs/200004`) não listam os NCMs vinculados.

## 4. Conclusão e Recomendação

Dado que:
1. A Calculadora não expõe a tabela de vínculo NCM x Classificação Tributária em seus dados abertos.
2. A Nota Técnica (1.50) não prevê rejeição explícita (validação formal de schema/regras) para NCM incompatível no contexto de IBS/CBS, sendo uma restrição interna da Calculadora.

**Recomendação:** É **inviável** validar a trava NCM x cClassTrib de forma estática (offline) no v0, pela absoluta falta de insumos de dados. A recomendação é registrar isso como **pendência conhecida** (ou limitação do escopo) e deixar que essa inconsistência seja capturada apenas se/quando o XML passar por cálculo na API da Calculadora, não na nossa camada de previsão offline.
