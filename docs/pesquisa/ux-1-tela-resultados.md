# UX-1: Tela de resultados em camadas

## 1. Avaliação de Abordagens de Fluxo

O projeto levantou uma dúvida fundamental sobre duas possíveis abordagens para a interface principal:
- **Abordagem A:** Grid de arquivos importados com ícone por linha e botão para remover os inválidos.
- **Abordagem B:** Área de *Drop* (arrastar e soltar) → Exibição de progresso → Tabela de causas e rejeições (conforme spec inicial).

### Recomendação
Recomendo seguir a **Abordagem B (Drop → Progresso → Tabela de causas)**.

**Justificativa para o dono do projeto:**
O perfil do nosso usuário (contador) precisa lidar com lotes massivos de notas (centenas a milhares de XMLs).
1. **Grid inviabiliza a leitura sistêmica:** Apresentar 500 linhas em um grid exige esforço cognitivo enorme. A dor real do contador não é corrigir nota a nota no XML, mas identificar se um comportamento sistêmico do software emissor está causando problemas. Agrupar por causas ("380 notas com o mesmo erro") resolve a dor do contador em segundos.
2. **Remoção não resolve o problema base:** A ação de "remover da lista" dá uma falsa sensação de resolução. Como este é um validador offline focado em prever falhas no SEFAZ a partir de 03/08/2026, remover uma nota da tela não a torna válida nem corrige o sistema emissor.
3. **Fluxo mais limpo:** Um sumário direto focado nos erros com a possibilidade de aprofundamento (drill-down) em "Quais notas deram o erro 1115?" atende muito melhor ao fluxo analítico.

---

## 2. Proposta de Layout em Camadas

A visualização de resultados deve apresentar as validações como "camadas". É crítico que o usuário perceba instantaneamente o que foi testado, o que foi reprovado e o que **não foi testado**.

### Diretrizes Visuais (Java Swing + FlatLaf)

1. **Camadas Aprovadas [✓]**
   - **Cor/Sinalização:** Título em texto padrão ou negrito leve, com ícone de sucesso (ex. `FlatSVGIcon` de *check* verde, `Actions.Green`).
   - **Informação de Apoio:** Exibir a quantidade de documentos aprovados e, se aplicável, a proveniência (ex: "Base V0039").

2. **Camadas Reprovadas [❌]**
   - **Cor/Sinalização:** Título destacado, ícone de erro vermelho (`Actions.Red`).
   - **Foco de Atenção:** Uma tabela/lista aninhada logo abaixo indicando a **causa-raiz** (código + resumo) e a **quantidade de documentos afetados**, ordenada decrescentemente pelo volume.

3. **Camadas Não Executadas [⊘]**
   - **Cor/Sinalização:** Redução de contraste para indicar ausência de execução (tons de cinza, `textInactiveText`). Ícone de bloqueio/ignorado (círculo cortado ou traço).
   - **Subtítulo:** Deve informar o motivo de forma direta (ex: "Calculadora não instalada ou rodando").

4. **Alerta de Desatualização [⚠️]**
   - Posicionado no topo ou rodapé como um banner permanente, exibindo laranja forte se as tabelas (proveniência) tiverem mais de 60 dias de extração.

---

## 3. Mockup ASCII da Tela de Resultados

```text
================================================================================
|  Validador de Lote - RTC IBS/CBS                                      [?] [-][x]
================================================================================
| 
|  [📂 Nova Análise (Arrastar XMLs)]      Lote: C:\XMLs\Agosto (500 documentos)
|
|  ============================================================================
|  ⚠️ ATENÇÃO: Base de tabelas extraída há mais de 60 dias (2026-05-20). 
|     Considere atualizar a aplicação para evitar divergências.
|  ============================================================================
|
|  ----------------------------------------------------------------------------
|  📊 RESULTADO DO PROCESSAMENTO EM CAMADAS
|  ----------------------------------------------------------------------------
|
|  [✓] Leitura dos Arquivos (Parser)
|      500 documentos lidos com sucesso.
|
|  [✓] Validação de Schema XML 
|      Todos os arquivos são estruturalmente válidos.
|      (Base V0039, extraída em 2026-07-26)
|
|  [⊘] Certificado
|      Ignorado na triagem offline.
|
|  [⊘] Assinatura
|      Ignorado na triagem offline.
|
|  [❌] Previsão de Rejeição de Negócio (NT 2025.002 v1.50)
|      Encontrados problemas sistêmicos em 399 documentos.
|
|      +----------------------------------------------------------------------+
|      | Código | Causa-Raiz / Regra                         | Afetados | Ação|
|      |--------|--------------------------------------------|----------|-----|
|      | 1115   | IBS/CBS não informado                      |      380 | [🔍]|
|      | 1025   | cClassTrib não permitida neste modelo      |       12 | [🔍]|
|      | 1033   | Grupo de redução estadual não informado    |        7 | [🔍]|
|      +----------------------------------------------------------------------+
|
|  [⊘] Conferência de Valores Diferenciais (Calculadora)
|      Não executada. A Calculadora oficial não está instalada ou em execução.
|
================================================================================
```

---

## 4. Navegação para os Arquivos Concretos (Drill-down)

Ao clicar no botão de lupa `[🔍]` na tabela de causa-raiz, o validador apresentará um diálogo modal (`JDialog`) com a lista filtrada dos XMLs que falharam especificamente por aquela regra.

### Mockup ASCII do Modal (Drill-down)

```text
  +-----------------------------------------------------------+
  | Detalhes da Rejeição: 1115                                |
  +-----------------------------------------------------------+
  | Descrição: IBS/CBS não informado                          |
  |                                                           |
  | Arquivos Afetados (380):                                  |
  | 📄 35260812345678901234550010000000011000000018.xml      |
  | 📄 35260812345678901234550010000000021000000023.xml      |
  | 📄 35260812345678901234550010000000031000000055.xml      |
  | ...                                                       |
  |                                                           |
  |            [ Exportar lista p/ CSV ] [ Copiar Nomes ]     |
  +-----------------------------------------------------------+
```
