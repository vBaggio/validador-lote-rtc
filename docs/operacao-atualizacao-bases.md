# Atualização de bases — operação, falhas e aceite

Este guia descreve o comportamento operacional do canal de atualização de schemas NF-e/NFC-e e da
tabela fiscal SVRS. Ele não envia XMLs, chaves, CNPJ ou telemetria: só consulta metadados e
artefatos normativos nas origens oficiais permitidas.

## Objetivo operacional

Uma base nova só passa a ser usada depois de quatro etapas separadas:

```text
consultar → preparar em staging → confirmar globalmente → ativar → reiniciar
```

Consulta e preparo **nunca** alteram a referência ativa (`current`). A confirmação do usuário
ativa todas as candidatas válidas de uma vez; os engines desta sessão continuam usando o conjunto
carregado no boot. O reinício é obrigatório para carregar as versões ativadas.

## Fluxo normal

1. Depois de a janela ficar visível, a consulta automática verifica schemas e tabela. O intervalo
   de 4 horas vale apenas para a consulta automática; **Verificar agora** é uma ação manual.
2. Cada fonte é baixada somente por HTTPS allowlisted, com redirects controlados, limite de tamanho
   e prazo que cobre conexão, resposta e leitura do corpo.
3. O artefato é validado em `versions/<versão>`: estrutura confinada, sem symlinks, hash e formato;
   schemas são compilados e tabelas são recarregadas/validadas. `current` permanece intacto.
4. Se houver candidata, o app pede uma confirmação única. Se houver validação de lote, espera seu
   término ou cancelamento; se uma ativação já estiver reservada/em andamento, uma nova validação é
   recusada de forma visível para não misturar referências.
5. Ao confirmar, a ativação revalida a candidata e troca `current` atomicamente. O diálogo fica
   application-modal durante a operação; não fecha por X, Esc ou Alt+F4.
6. Ao finalizar, o app mantém **Reinício necessário** até encerrar o processo, inclusive se a
   persistência do evento terminal falhar depois de a troca física já ter ocorrido.

## Estado visível e ordem de eventos

O rodapé e o diálogo recebem o mesmo snapshot imutável. As revisões são monotônicas, enfileiradas e
observadas na EDT; revisões obsoletas são descartadas. A abertura do diálogo modal é postada para
um ciclo posterior da EDT, para que nunca bloqueie a entrega de `APPLIED` ou `FAILED`.

| Estado | Significado para o usuário | Ação disponível |
|---|---|---|
| Consultando | fontes em consulta/preparo | aguardar ou abrir detalhes |
| Bases verificadas e atualizadas | consulta sem candidata e sem falha | verificar novamente |
| Atualizações disponíveis | há ao menos uma candidata | confirmar atualização |
| Aguardando validação | há candidata, mas lote ativo | aguardar fim/cancelamento do lote |
| Falha de consulta | uma ou todas as fontes falharam, sem candidata predominante | tentar novamente |
| Aplicando | ativação em andamento | nenhuma; fechamento bloqueado |
| Reinício necessário | ao menos uma base foi ativada nesta sessão | encerrar e reabrir depois |

Em sucesso parcial, uma candidata válida continua visível mesmo se a outra fonte falhar. Em
`UP_TO_DATE + FAILED`, a falha também permanece visível e oferece retry; o sistema nunca volta a
"não verificado" para esconder uma falha parcial.

## Guardas e mitigação por falha

| Falha/cenário | Comportamento seguro | Recuperação |
|---|---|---|
| DNS, conexão, timeout ou HTTP 502/503/504 | até duas tentativas, com atraso curto | falha terminal com retry manual/novo boot |
| TLS, HTTP 4xx, origem não permitida, tamanho/formato inválido | não retenta | base ativa preservada; erro visível |
| corpo HTTP lento após headers | prazo total cancela requisição e assinatura de leitura | falha `CONNECTION`, sem executor preso |
| ZIP/tabela inválido, hash divergente, symlink ou compilação falha | candidata isolada; `current` não muda | fonte falha e pode ser consultada novamente |
| duas consultas da mesma candidata | staging reutiliza identidade estável | continua candidata, sem duplicar nem ativar |
| falha parcial | fonte saudável conserva candidata/estado; fonte falha não apaga a outra | confirmação global ativa apenas válidas |
| listener/UI lança exceção em CHECKING ou APPLYING | operação produz estado terminal, libera gate e isola o listener defeituoso | retry/consulta fresca; sem limbo |
| falha ao persistir após ativação física | fato físico é preservado e reinício fica latched | não reaplica cegamente; nova consulta reconcilia estado |
| executor rejeita consulta/aplicação | reserva atômica é desfeita e erro chega pela EDT | usuário pode tentar novamente, sem loop de prompt |
| candidata falha ao aplicar | candidata é bloqueada até nova consulta | não repetir ativação automaticamente |
| modal em aplicação | fechamento e ações ficam bloqueados, mas snapshots terminais continuam chegando | terminal restaura política de fechamento |

## Roteiro de teste em runtime

Faça o roteiro em uma instalação/imagem limpa, sem XMLs carregados.

1. Inicie o app com rede disponível e observe o rodapé: deve aparecer **Consultando** e depois um
   estado terminal.
2. Abra **Bases e atualizações**; confirme que há apenas os cards de Schemas NF-e/NFC-e e Tabela
   fiscal, com origem, versão e detalhes legíveis.
3. Clique **Verificar agora** repetidamente durante a consulta: deve existir uma única operação,
   sem downloads duplicados, sem travar a janela e sem desaparecer o feedback.
4. Simule indisponibilidade de uma fonte (rede/firewall) e mantenha a outra acessível. Confira que
   a fonte falha mostra retry, a saudável preserva seu estado/candidata e a base ativa não muda.
5. Produza ou use uma candidata válida. Inicie uma validação de lote antes da confirmação: o prompt
   deve aguardar; ao fim/cancelamento, deve ser oferecido uma única vez.
6. Confirme a atualização. Durante **Aplicando**, teste X, Esc e Alt+F4: nenhum fecha o diálogo;
   todos os botões ficam indisponíveis, e o terminal não fica preso em spinner.
7. Ao concluir, confira **Reinício necessário**. Feche e reabra o aplicativo; confirme no diálogo
   que a versão ativada passou a ser a referência em uso.
8. Rode novamente sem atualização: a tabela idêntica deve ficar **verificada/atualizada**; schema
   de família anterior (por exemplo 010b diante de 010e) não pode causar downgrade.

## Critérios de aceite

- [ ] nenhuma consulta/preparo modifica `current`;
- [ ] falha preserva a base anterior e permanece visível, inclusive parcial;
- [ ] nenhuma combinação de listener, timeout, modal ou executor deixa `CHECKING`/`APPLYING` preso;
- [ ] timeout do corpo não bloqueia a única operação de atualização;
- [ ] confirmação e ativação não concorrem com validação de lote;
- [ ] a ativação é atômica, revalidada e exige reinício para efeito nos engines;
- [ ] rodapé e diálogo mostram a mesma evolução de estado;
- [ ] retry não provoca reaplicação cega ou prompts repetidos;
- [ ] escala Windows 100%, 125% e 150% não corta conteúdo; há rolagem, ícones e ações acessíveis;
- [ ] nenhum XML do lote participa das requisições;
- [ ] `./gradlew clean test`, `./gradlew jpackageImage` e `git diff --check` passam.
