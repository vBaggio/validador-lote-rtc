# B6 — Canal confiável de atualização de schemas

## Objetivo

Atualizar a validação estrutural de NF-e/NFC-e para a árvore oficial vigente e introduzir um
canal local e auditável para artefatos externos: schemas, tabelas fiscais e, quando a v1 passar a
usá-lo, o motor da Calculadora. O inventário e o protocolo de atualização registram versão, fonte,
hash, última consulta e resultado. A validação continua integralmente local; rede é usada somente
pelo subsistema de atualização de artefatos normativos.

## Escopo deliberado

- Suportar os roots já aceitos pelo produto: `NFe`, `nfeProc` e `enviNFe`.
- Usar a página de Documentos da SVRS como canal operacional oficial para descobrir e baixar
pacotes publicados. O produto só ativa a closure exigida pelos documentos fiscais que suporta e
  nunca troca uma base por perfil anterior ou incompatível (D-049). Nesta implementação,
  compatível significa `010e`; família sucessora requer task explícita de suporte.
- Atualizar as tabelas fiscais que já alimentam o `RuleEngine` por sua fonte oficial, usando o
  mesmo mecanismo transacional e as guardas estruturais já exigidas na ingestão manual.
- Registrar o motor da Calculadora no catálogo como artefato futuro, com fonte e política de
  atualização definidos. Ele não é baixado, executado ou usado para decidir tributos no v0.
- Na primeira abertura após a instalação e periodicamente depois, consultar/atualizar de modo não
  destrutivo: a base embarcada torna o instalador independente de rede e a base anterior é
  preservada até a candidata passar todas as verificações. O instalador em si não baixa nada.
- Registrar localmente, por artefato, fonte, versão publicada, data, hash SHA-256, última consulta,
  última atualização e o resultado/erro mais recente.
- Avaliar o SVN do ACBr apenas como espelho de contingência. Ele nunca pode ativar sozinho uma
  base cujo hash não tenha sido confirmado pela fonte canônica.

Fora deste bloco: validar eventos, serviços, retornos ou todos os roots do pacote oficial; eles
requerem catálogo de entrypoints, metadados e uma experiência de produto próprios. A atualização
de tabelas altera somente a base oficial consumida pelo `RuleEngine`; não cria regras, tabelas ou
interpretações fiscais novas.

## Fonte e cadeia de confiança

1. O cliente consulta somente URLs HTTPS do host oficial permitido da SVRS.
2. Descobre exclusivamente pacotes de schema publicados pela página e seleciona o perfil
   NF-e/NFC-e compatível e mais novo que a base ativa.
3. O download é feito em staging com limites de tamanho, quantidade de entradas, caminho
   normalizado e proibição de zip-slip. Redirecionamentos só podem permanecer na allowlist.
4. A árvore extraída precisa conter os entrypoints e includes esperados; os XSDs são compilados
   usando as mesmas restrições seguras de XML do produto e exercitados contra fixtures locais.
5. Só então o diretório recebe um manifesto SHA-256 e vira a base ativa por troca atômica. Falha
   mantém a última base válida e é comunicada sem bloquear o uso offline.

Não há API oficial ou manifesto assinado de versões encontrado na SVRS. HTTPS, allowlist,
validação estrutural, hash auditável e rollback reduzem o risco de transporte, mas não transformam
uma publicação nova em julgamento fiscal automático. Por isso, a automação de repositório também
continuará detectando e revisando as mudanças antes de elas virarem a base embarcada de uma release.

## Desenho operacional proposto

### Catálogo único de artefatos

Cada artefato tem uma identidade estável (`NFE_SCHEMAS`, `FISCAL_TABLES`, `CALCULATOR`), um
provedor de origem, uma política de validação e um manifesto local. O catálogo não conhece regra
fiscal; ele apenas resolve qual artefato íntegro está ativo e mantém histórico auditável.

| Artefato | Fonte canônica | Uso no v0 | Política de ativação |
|---|---|---|---|
| Schemas NF-e/NFC-e | Portal de Documentos da SVRS | `SchemaValidatorEngine` | Compilação segura dos três roots + fixtures + hash |
| Tabelas CST/cClassTrib | Fonte oficial SVRS já usada pelo projeto | `RuleEngine` | Validação estrutural, unicidade e guarda contra regressão de cobertura |
| Calculadora | endpoint oficial da RFB já documentado | nenhum, até v1 | apenas inventariada; não baixa 250 MB nem decide tributo no v0 |

O diretório de dados é separado da instalação, por exemplo
`~/.validador-lote-rtc/artifacts/<id>/<versão>/`. Cada versão contém o payload imutável e o
manifesto; uma referência pequena e atômica escolhe a ativa. Nunca se sobrescreve a versão em uso.

### Rotina de atualização (proposta original, superada no runtime)

Os itens 1–5 abaixo preservam a proposta de 29/07/2026. Eles foram superados pelo adendo de
30/07/2026 e pela D-050: a janela de 24 horas limita apenas o agendamento automático (a ação
manual não a usa); `check` e `prepare` não alteram `current`; uma única confirmação global ativa
todas as candidatas válidas; e os engines só adotam as bases ativadas no próximo processo, sem
hot reload. O texto histórico permanece para registrar o desenho que foi refinado.

1. No primeiro boot após a instalação, a aplicação começa funcional com os artefatos embarcados e
   dispara a checagem em segundo plano. Assim, instalador e primeiro uso permanecem possíveis sem
   internet.
2. Depois, uma checagem com intervalo configurado (proposta inicial: 24 horas) consulta somente os
   metadados necessários. O usuário também tem ação explícita de “verificar agora”.
3. Um payload novo é sempre baixado para staging, verificado e instalado como nova versão. Falha
   não degrada nem interrompe a validação.
4. Para evitar que uma mesma área de trabalho seja analisada por bases diferentes, a nova base é
   aplicada no próximo boot por padrão. Uma aplicação imediata poderá ser oferecida apenas quando
   não houver validação em curso e após reconstruir os engines de modo atômico.
5. A tela de fontes externas expõe, por artefato, versão ativa, fonte, hash abreviado, última
   atualização, última verificação e erro recuperável. O rodapé mantém apenas o resumo compacto.

### SVRS e ACBr

O Portal de Documentos da SVRS é o canal operacional oficial. A consulta reconhece somente
pacotes de schema que ela publicou, nunca nomes adivinhados; perfis paralelos ou anteriores não
entram por ordenação lexicográfica de `PL`.

O SVN público do ACBr foi conferido em 29/07/2026: a pasta
`Exemplos/ACBrDFe/Schemas/NFe` na revisão 47477 contém os cinco arquivos necessários à closure da
nota com SHA-256 idêntico ao acervo local `tmp/Schemas/NFe`. Ele é um espelho útil para detectar
atualizações e reduzir indisponibilidade, mas não publica uma assinatura ou declaração oficial de
vigência. A proposta é usá-lo assim:

- quando SVRS e ACBr concordarem nos hashes, o segundo serve de evidência técnica adicional;
- se o ACBr adiantar/divergir, registrar “candidata não confirmada” e não ativar automaticamente;
- se a SVRS estiver indisponível, manter a última base oficial e informar a indisponibilidade,
  em vez de trocar confiança por disponibilidade sem aviso.

Esse desenho não promete uma impossibilidade: sem API/manifesto assinado da SVRS, não existe
forma de provar automaticamente que um artefato novo é fiscalmente vigente. Ele maximiza
atualidade sem aceitar uma fonte não oficial como autoridade silenciosa.

## Tasks

### Task 30 — Contrato do canal, inventário local e carregamento da base ativa

Criar a infraestrutura genérica de catálogo de artefatos externos, manifesto imutável,
descoberta/instalação transacional e carregamento preferencial da última base válida, com fallback
para os schemas embarcados. Cobrir offline, corrupção, rollback, concorrência e rejeição de
arquivos hostis.

### Task 31 — Atualização da árvore NF-e/NFC-e e regressão estrutural

Confirmar a proveniência da árvore em `tmp/Schemas` contra o pacote oficial vigente. Atualizar
apenas a closure dos documents roots e o wrapper próprio `nota.xsd`; incluir fixtures válidas com
campos introduzidos pela base nova e controles negativos. Registrar versão, URL e hashes.

### Task 32 — Canal transacional das tabelas fiscais

Levar a fonte oficial já consumida pela task Gradle para o mesmo contrato de artefatos externos.
Validar estruturalmente a candidata, comparar contra a base vigente para detectar regressão de
formato/cobertura e ativá-la com rollback. O `RuleEngine` só recebe uma tabela que passou pelas
guardas; falha mantém a tabela embarcada/anterior e não acusa documento por causa de atualização
duvidosa.

### Task 33 — Orquestração de atualização no aplicativo e experiência de falha segura

Executar checagem/atualização em segundo plano na primeira abertura e no intervalo configurado,
sem congelar a interface nem interromper uma validação. Mostrar no rodapé a base ativa e oferecer
uma tela/diálogo discreto de fontes externas com versão, data da última atualização e da última
verificação, origem e erro recuperável. Nenhum XML é lido, enviado ou retido pelo canal.

### Task 34 — Espelho ACBr, manutenção e documentação

Investigar o endpoint SVN do ACBr por revisão e atraso em relação à SVRS. Implementar
somente a política aprovada: telemetria local de discrepância e contingência condicionada a hash
oficial conhecido; nunca aceitação cega. Substituir as tasks Gradle isoladas por uma ferramenta de
manutenção que baixa, verifica, faz diff e exige revisão antes de alterar resources. Documentar a
Calculadora como artefato futuro do catálogo, sem introduzi-la no runtime fiscal antes da v1.

### Task 35 — Fechamento do bloco

Revisão independente, testes completos, verificação de mutação dos caminhos de troca/rollback,
documentação canônica, atualização do harness e preparação de PR. A tag pública e o smoke real do
MSI continuam o gate humano herdado da Task 29.

### Task 36 — Correção do canal de schemas após validação em campo

O Portal Nacional falha a validação TLS em instalações Java usuais e o erro genérico impede o
usuário de entender a continuidade segura. Substituir a aquisição runtime por adaptador do catálogo
SVRS: extrair apenas entradas de schema publicadas, construir o download estático a partir do nome
extraído, restringir host/tamanho/ZIP como no canal existente e recusar perfil anterior ou
incompatível. Enquanto a SVRS não publicar `010e` ou sucessor suportado, registrar consulta
concluída sem candidata, mantendo a base atual. ACBr permanece inspeção/contingência, sem ativação
automática. Cobrir a listagem, URL encoding, ZIP vazio/antigo, candidata nova e a mensagem
recuperável na tela de Fontes externas.

### Tasks 37–41 — Fluxo observável, confirmação e aplicação de bases

A validação em campo revelou que o canal tecnicamente seguro ainda não explica bem consulta,
candidata, falha parcial e ativação. O detalhamento aprovado para separar consulta/staging de
ativação, compartilhar o estado entre rodapé e diálogo, adiar a aplicação durante validação e
corrigir a experiência do modal está em
[`2026-07-30-fluxo-observavel-atualizacao-bases.md`](./2026-07-30-fluxo-observavel-atualizacao-bases.md).
Esse adendo governa o refinamento final do B6.

### Fechamento do refinamento (30/07/2026)

Tasks 37–40 foram concluídas e revisadas independentemente: `6c007e0` separa preparação de
ativação; `e3997d1` introduz falhas tipadas, retentativa limitada e coordenação resiliente;
`774c117` consolida snapshots, gate de validação e latch de reinício; `0d7ed20` entrega rodapé e
diálogo adaptável. A Task 42 adicional (`0ada78b`) endurece a orquestração após a revisão: admissão
atômica entre validação e ativação, snapshots monotônicos, evento terminal mesmo sob listener com
falha e reinício latched se a ativação física vencer a persistência. A Task 41 registra a decisão,
as verificações e o handoff. O smoke visual manual no Windows permanece gate do dono antes de
publicação/PR; não há merge automático.

## Critérios de aceite

- Sem rede ou com fonte indisponível, a aplicação continua validando com a última base íntegra.
- Um ZIP malformado, excessivo ou com caminho indevido nunca modifica a base ativa.
- Uma candidata que não compila ou não valida os roots suportados nunca é ativada.
- A versão ativa e sua proveniência podem ser vistas no aplicativo e auditadas no disco.
- O app não envia XMLs, CNPJ, chave de acesso ou telemetria no processo de atualização.
- A base embarcada e a base atualizada validam NF-e e NFC-e pelos mesmos entrypoints suportados.
- O usuário consegue consultar, sem depender de logs técnicos, qual versão está ativa e quando cada
  artefato externo foi verificado e atualizado pela última vez.
