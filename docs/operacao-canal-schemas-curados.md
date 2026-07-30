# Canal próprio de schemas curados — curadoria, publicação e aceite

Este documento operacionaliza D-051. O canal runtime de schemas NF-e/NFC-e é um repositório
público separado, com `stable.json` assinado e ZIPs imutáveis. Ele é distinto tanto do repositório
do aplicativo quanto do canal SVRS da tabela fiscal. ACBr e SVRS são fontes de pesquisa e
proveniência; não são transporte nem fallback runtime de schemas.

## Estado de publicação

**Gate humano ainda aberto:** endpoint estável, repositório público, `keyId`, chave pública Ed25519
embarcada e bootstrap revisado ainda não foram publicados. Portanto, o aplicativo atual deixa a
consulta de schemas explicitamente desabilitada e continua validando com a base embarcada (ou a
última `current` íntegra). Não há canal produtivo de schemas disponível nesta condição.

O bootstrap precisa publicar uma release real revisada no repositório de bases, por exemplo
`vBaggio/validador-lote-rtc-bases`, contendo `stable.json`, ZIP de fixture/release, manifesto e
assinatura verificáveis pela chave pública que será embarcada. Não inventar endpoint, host, `keyId`
ou chave no código para contornar este gate.

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
| Replay ou `releaseSequence` menor | Há rejeição explícita de rollback; a base anterior permanece ativa. |
| Cliques concorrentes e timer de boot | Só uma operação é executada; o estado segue observável; uma consulta posterior é aceita depois do estado terminal. |
| Remover `~/.validador-lote-rtc/artifacts/NFE_SCHEMAS` e o arquivo de estado | A próxima consulta manual baixa e prepara a release assinada `current`, sem comparar versão com a base embarcada. |

Para o último cenário, o endpoint/chave reais precisam já ter passado o gate humano. Antes disso, o
resultado correto é o estado desabilitado explícito, e não tentativa de SVRS/ACBr.

## Limites deliberados

Não há atualização automática do aplicativo, descoberta SVN/ACBr em runtime, fallback de schemas
para SVRS/ACBr nem publicação automática do repositório de bases. Qualquer um desses itens exige
decisão e plano de segurança próprios.
