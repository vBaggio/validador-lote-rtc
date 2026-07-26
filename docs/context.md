# Contexto

## O que é

**Validador de Lote RTC** — ferramenta desktop, offline e independente (sem vínculo com
RFB/SEFAZ) que valida em lote XMLs de NF-e (modelo 55) e NFC-e (modelo 65) contra os
schemas XSD oficiais da Reforma Tributária do Consumo, agrupa os achados por causa-raiz
e exporta CSV. Público: contadores e responsáveis fiscais, não-técnicos, Windows-first.

A partir de 03/08/2026 a SEFAZ rejeita documentos de emitentes CRT=3 com grupos IBS/CBS
fora da NT 2025.002. As ferramentas oficiais validam 1 documento por vez; esta valida
centenas, coletando TODOS os erros de cada arquivo (o endpoint oficial para no primeiro).

## Princípios

1. **Nenhum dado sai da máquina por padrão** — sem telemetria, sem rede em runtime no v0.
2. **A ferramenta nunca decide tributo** — julgamento vem de artefato oficial (schemas; na v1, motor `regime-geral`).
3. **Zero pré-requisitos** — instalador nativo com runtime embarcado.
4. **Vida útil curta declarada** — simplicidade > extensibilidade.

## Fases

- **v0.x (atual)**: validação estrutural local + agrupamento + CSV + UI + instalador Windows.
- **v1**: conferência de valores via motor oficial (`regime-geral` como processo filho),
  relatório narrativo por IA opcional (BYOK). Ver spec §3.2 e decisões D-012/D-014.

## Índice

1. [`architecture.md`](./architecture.md) — camadas, pacotes, regra de dependência, fluxo
2. [`conventions.md`](./conventions.md) — regras de código, commits, fronteiras
3. [`testing.md`](./testing.md) — estratégia de testes, fixtures, tags
4. [`decisions.md`](./decisions.md) — log ADR-lite (D-001..)
5. [`calculadora/`](./calculadora/) — contrato real da Calculadora RFB (descoberta 26/07/2026)
6. [`superpowers/specs/`](./superpowers/specs/) — spec de design aprovada
7. [`superpowers/plans/`](./superpowers/plans/) — plano de implementação vigente
