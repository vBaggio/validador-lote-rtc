# Canal próprio de schemas curados — curadoria, publicação e aceite

Este documento operacionaliza D-051. O canal runtime de schemas NF-e/NFC-e é um repositório
público separado, com `stable.json` assinado e ZIPs imutáveis. Ele é distinto tanto do repositório
do aplicativo quanto do canal SVRS da tabela fiscal. ACBr e SVRS são fontes de pesquisa e
proveniência; não são transporte nem fallback runtime de schemas.

## Estado de publicação

**Canal publicado:** o repositório público é
[`vBaggio/validador-lote-rtc-bases`](https://github.com/vBaggio/validador-lote-rtc-bases), com
manifesto em `https://vbaggio.github.io/validador-lote-rtc-bases/channels/nfe-schemas/stable.json`, `keyId`
`schemas-2026-01` e chave pública Ed25519 embarcada. Indisponibilidade, assinatura inválida ou
conteúdo incompatível não substitui a última `current` íntegra nem o fallback embarcado.

O bootstrap já foi publicado como a release `010e_v1.02-r2`, sequência `2`, com a árvore XSD
completa em ZIP imutável,
manifesto e assinatura verificáveis pela chave pública embarcada. O teste offline do aplicativo
preserva o `stable.json` publicado como fixture para detectar alteração acidental de `keyId` ou da
chave confiada. Novas releases seguem o checklist abaixo e não alteram esse bootstrap.

## Contrato da release

O manifesto tem `format = 1`, `keyId` conhecido, conteúdo `signed` e assinatura Ed25519 Base64
sobre a serialização canônica UTF-8 de `signed` (campos em ordem alfabética; mapas ordenados por
chave; arrays conservam ordem). Ele identifica `NFE_SCHEMAS`, versão humana, `releaseSequence`,
`publishedAt`, `minimumAppVersion`, URL HTTPS do ZIP, SHA-256 hexadecimal minúsculo e a lista de
proveniência.

`releaseSequence` é estritamente crescente dentro do canal e é a única ordem anti-rollback.
`publishedAt` serve a auditoria/exibição e não resolve precedência. Uma release publicada é
imutável: corrigir artefato, hash ou metadado requer novo ZIP, novo manifesto, nova assinatura e
sequência maior.

## Checklist do curador antes de publicar

1. Consulte a origem e verifique os caminhos alterados no diretório de schemas NF-e com:

   ```bash
   svn log --xml -v -l 1 https://svn.code.sf.net/p/acbr/code/trunk2/Exemplos/ACBrDFe/Schemas/NFe/
   ```

   Uma revisão em qualquer outro diretório do ACBr não é evidência de mudança em
   `Exemplos/ACBrDFe/Schemas/NFe/`.
2. Registre a revisão e URLs de proveniência. Confira também a vigência em fonte oficial; ACBr é
   somente evidência de disponibilidade/diff, nunca autoridade ou automação de publicação.
3. Extraia a closure pretendida, inspecione todos os caminhos alterados e revise o diff dos XSDs.
   Verifique entrypoint, imports/includes, roots e que não houve arquivo estranho, symlink ou ZIP
   com caminho escapando da árvore.
4. Compile a closure com este projeto antes de publicar. A compilação real de `nota.xsd` é o gate
   de estrutura; execute a suíte pertinente e mantenha fixtures/metadata coerentes.
5. Escolha `releaseSequence` estritamente maior que a release ativa do mesmo canal. Não compare
   nomes de pacote, data, ordem lexicográfica ou timestamp como substitutos da sequência.
6. Produza ZIP imutável, calcule SHA-256 dos bytes exatos, gere `stable.json` com a proveniência e
   assine somente a serialização canônica de `signed` com a chave privada Ed25519 guardada fora deste
   projeto. Nunca versione ou publique a chave privada.
7. Abra revisão no repositório de bases com diff dos XSDs, ZIP/hash, manifesto, assinatura,
   `releaseSequence`, compatibilidade mínima e revisão de proveniência. A revisão aprova a
   completude da closure, não um rótulo de “pacote mais recente”.
8. Só após revisão publique o ZIP e o manifesto estável em URL HTTPS allowlisted. Preserve a
   revisão de proveniência no manifesto e no histórico da revisão; não edite uma release já
   publicada.

## Aceite do cliente runtime

Execute em instalação/imagem limpa; observe simultaneamente rodapé e diálogo **Bases e
atualizações**. Em todos os casos, nenhuma falha pode substituir `current` ou misturar engines já
carregados no processo.

| Cenário | Resultado obrigatório |
|---|---|
| Offline, DNS, conexão lenta ou timeout | A base embarcada ou a última `current` íntegra valida como antes; há falha recuperável visível; não há reinício necessário. |
| Release válida com sequência maior | A candidata aparece; após confirmação ela é ativada; o sucesso fica visível acima do diálogo de bases; é solicitado reinício; no boot seguinte a nova closure é carregada. |
| Hash do ZIP, assinatura, redirect ou traversal de ZIP inválido | Não há candidata; staging e `current` não mudam; a fonte inválida fica visível como falha recuperável. |
| Release assinada com closure quebrada/incompatível ou `minimumAppVersion` maior | O feedback diz **estrutura não suportada**; a base anterior permanece ativa; atualização de tabela ainda pode prosseguir; schemas não armam reinício necessário. |
| Mesma `releaseSequence` e mesma identidade assinada/hash do ZIP | A fonte fica em dia sem baixar nem extrair o ZIP novamente. |
| `releaseSequence` menor ou igual com identidade/hash divergente | Há rejeição explícita de rollback/conflito; a base anterior permanece ativa. |
| Cliques concorrentes e timer de boot | Só uma operação é executada; o estado segue observável; uma consulta posterior é aceita depois do estado terminal. |
| Remover `~/.validador-lote-rtc/artifacts/NFE_SCHEMAS` e o arquivo de estado | A próxima consulta manual baixa e prepara a release assinada `current`, sem comparar versão com a base embarcada. |

O último cenário usa o endpoint publicado; qualquer falha de confiança permanece explícita e nunca
faz tentativa de SVRS/ACBr como fallback.

## Limites deliberados

Não há atualização automática do aplicativo, descoberta SVN/ACBr em runtime, fallback de schemas
para SVRS/ACBr nem publicação automática do repositório de bases. Qualquer um desses itens exige
decisão e plano de segurança próprios.

## Pendências pós-entrega

O fluxo entregue está validado em runtime e é seguro para a operação atual. Os itens abaixo são
evoluções deliberadamente fora deste bloco; não impedem a publicação da release `010e_v1.02-r2`.

1. **Ferramenta de curadoria/publicação.** Hoje a montagem do ZIP, cálculo do hash, assinatura e
   publicação são um procedimento revisado e manual. Falta uma ferramenta reprodutível que valide
   a closure, gere o manifesto canônico e use uma chave protegida fora dos repositórios.
2. **Rotação e revogação de chave.** O cliente já seleciona chave por `keyId`, mas falta definir o
   procedimento operacional de inclusão, transição e remoção de chaves confiadas, incluindo backup
   e recuperação da chave de assinatura.
3. **Monitoramento de fontes para o curador.** A consulta de mudanças no diretório de schemas do
   ACBr e a conferência da fonte oficial continuam manuais. Não há robô que publique conteúdo de
   terceiros, nem deve haver sem uma revisão humana da closure e da vigência fiscal.
4. **Novos tipos de artefato.** A estrutura externa já nasce por canal (`channels/<artefato>/`) e
   pode hospedar, por exemplo, uma tabela curada de importação. Cada artefato novo ainda precisa de
   seu próprio `ArtifactId`, contrato, validação, armazenamento, UI e critérios fiscais; schemas
   não devem ser reutilizados como validação genérica.
5. **Gate visual do instalador Windows.** O smoke desta entrega foi feito no runtime do
   desenvolvedor. A validação da distribuição instalada no Windows (100%, 125% e 150% de DPI,
   diálogo, reinício e recuperação de rede) permanece como aceite de release do instalador.
