# Contexto

## O que é

**Validador de Lote RTC** — ferramenta desktop, local e independente (sem vínculo com
RFB/SEFAZ) que valida em lote XMLs de NF-e (modelo 55) e NFC-e (modelo 65) contra os
schemas XSD oficiais da Reforma Tributária do Consumo e agrupa os achados por causa-raiz.
O exportador CSV permanece no núcleo, mas sua ação está temporariamente suspensa na interface
(D-045). Público: contadores e responsáveis fiscais, não-técnicos, Windows-first.

A partir de 03/08/2026 a SEFAZ rejeita documentos de emitentes CRT=3 com grupos IBS/CBS
fora da NT 2025.002. As ferramentas oficiais validam 1 documento por vez; esta valida
centenas, coletando TODOS os erros de cada arquivo (o endpoint oficial para no primeiro).

## Princípios

1. **Nenhum dado fiscal sai da máquina** — sem telemetria nem envio de XML, chave ou CNPJ. Após o
   boot, o agendamento automático do canal B6 pode consultar metadados e artefatos normativos nas
   fontes oficiais no máximo uma vez a cada 24 horas; a ação manual **Verificar agora** não se
   submete a essa janela. A validação do lote continua local. A consulta só prepara candidatas: a
   ativação exige confirmação global do usuário, ocorre fora de uma validação e entra nos engines
   somente após reinício. A consulta tem prazo também para o corpo HTTP; falhas e rejeições de
   agendamento permanecem visíveis e recuperáveis, sem deixar uma base parcial ativa.
2. **A ferramenta nunca decide tributo** — julgamento vem de artefato oficial (schemas; na v1, motor `regime-geral`).
3. **Zero pré-requisitos** — instalador nativo com runtime embarcado.
4. **Vida útil curta declarada** — simplicidade > extensibilidade.

## Fases

- **v0.x (atual)**: validação estrutural local + agrupamento + UI + instalador Windows; CSV
  preservado no núcleo, sem ação visível até revisão própria (D-045).
- **v1**: conferência de valores via motor oficial (`regime-geral` como processo filho),
  relatório narrativo por IA opcional (BYOK). Ver spec §3.2 e decisões D-012/D-014.

## Índice

1. [`workflow.md`](./workflow.md) — como se executa trabalho aqui: bloco, ledger, brief e adendo, handoff
2. [`architecture.md`](./architecture.md) — camadas, pacotes, regra de dependência, fluxo
3. [`conventions.md`](./conventions.md) — regras de código, commits, fronteiras
4. [`testing.md`](./testing.md) — estratégia de testes, fixtures, tags
5. [`decisions.md`](./decisions.md) — log ADR-lite (D-001..)
6. [`calculadora/`](./calculadora/) — contrato real da Calculadora RFB (descoberta 26/07/2026)
7. [`superpowers/specs/`](./superpowers/specs/) — spec de design aprovada
8. [`superpowers/plans/`](./superpowers/plans/) — plano de implementação vigente
9. [`operacao-atualizacao-bases.md`](./operacao-atualizacao-bases.md) — operação, falhas e aceite do canal de atualização

O projeto é desenvolvido por mais de uma ferramenta de agente. Tudo que vale para todas fica aqui em
`docs/`; `CLAUDE.md` e `AGENTS.md` são adaptadores finos com o que é específico de cada uma.
