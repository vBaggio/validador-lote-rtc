# Validador de XML em Lote - Reforma Tributária

Aplicativo desktop para revisar, de uma vez, XMLs de NF-e (modelo 55) e NFC-e (modelo 65)
quanto à estrutura exigida para os grupos IBS/CBS da Reforma Tributária do Consumo.

> **Ferramenta independente.** Este projeto não tem vínculo com a Receita Federal, SEFAZ ou
> qualquer órgão público. Ele usa artefatos oficiais embarcados para fazer verificações técnicas;
> a decisão fiscal continua sendo responsabilidade da empresa e de sua assessoria contábil.

## Privacidade

A análise acontece localmente, no computador em que o aplicativo está aberto. Não há cadastro,
telemetria ou envio de XMLs. Após o boot, e no máximo uma vez a cada 4 horas, o aplicativo pode
consultar a tabela fiscal no canal SVRS e o manifesto e ZIP assinados do canal próprio de schemas
curados; **Fontes externas** também permite pedir essa verificação manualmente. A rotina nunca envia
documentos, chaves, CNPJ ou conteúdo do lote. Os documentos permanecem na sua máquina.

## O que o aplicativo faz

- Importa uma pasta ou XMLs individuais para formar um lote de trabalho.
- Lê os dados exibidos na grade e valida os documentos quando você solicita.
- Mostra o andamento da validação documento a documento, sem bloquear a janela.
- Reúne os problemas do documento selecionado em uma grade de detalhe.
- Permite manter apenas os documentos que ainda precisam de atenção com **Remover válidos**.

Os XMLs são verificados contra os schemas XSD oficiais embarcados para NF-e/NFC-e. O rodapé abre
**Fontes externas**, onde é possível conferir origem, versão/snapshot, hash abreviado, últimas
atualização e verificação, além de solicitar uma nova consulta sem interromper o lote. Uma base
obtida nessa consulta só é usada no próximo boot, para que o lote atual não misture versões.

## O que o aplicativo não faz

- Não emite, assina, transmite nem corrige XMLs.
- Não substitui orientação fiscal ou a validação definitiva dos ambientes autorizadores.
- Não transforma uma validação sem problemas em garantia de autorização pela SEFAZ.
- Não mostra arquivos que não puderam ser lidos com segurança; ao fim da importação, informa quais
  deles ficaram de fora.
- Não oferece exportação CSV na interface nesta versão. O recurso existe no núcleo do projeto, mas
  está deliberadamente suspenso até uma revisão específica de seu fluxo e formato.

## Como usar

1. Abra o aplicativo e arraste uma pasta ou XMLs individuais para a área central. Também é possível
   usar o botão de adicionar arquivos.
2. Revise a grade **Documentos Fiscais**. Você pode adicionar mais XMLs, excluir uma linha ou
   limpar o lote antes de iniciar.
3. Clique em **Validar pendentes**. A coluna de status e a barra de progresso acompanham o processo
   em tempo real. Se necessário, use **Interromper**; o que já foi validado é preservado e o que
   não começou continua pendente.
4. Selecione um documento para consultar a grade **Problemas** abaixo dele.
5. Depois de tratar o resultado, use **Remover válidos** para deixar visíveis apenas os documentos
   que requerem atenção.

O status da grade distingue documentos pendentes (cinza), em validação (azul), sem problemas
identificados (verde), com erro ou rejeição (vermelho), com atenção (amarelo) e não avaliados
(branco). Essas cores ajudam a navegar pelo lote; elas não substituem a leitura da mensagem e do
detalhe apresentado.

## Instalação

A primeira release pública ainda não foi publicada. Quando houver um instalador para Windows, ele
será disponibilizado na página de releases deste repositório e incluirá o runtime necessário: não
será preciso instalar Java separadamente.

Enquanto não há release, o aplicativo pode ser executado a partir do código-fonte.

### Para desenvolvimento

Pré-requisito: JDK 21.

```bash
./gradlew clean test --console=plain
./gradlew run
```

Para gerar um aplicativo com runtime embarcado, use:

```bash
./gradlew jpackageImage
```

Para gerar o instalador nativo do sistema operacional atual, use:

```bash
./gradlew jpackageInstaller
```

No Linux, a geração do instalador requer as ferramentas de empacotamento disponíveis no sistema;
no Windows, o resultado é um instalador MSI. Os artefatos são criados em `build/jpackage/`.

## Base de validação

A base atualmente embarcada é o perfil `010e_v1.02`; seu payload foi transportado do espelho ACBr
SVN r47146 e identificado por hashes registrados no aplicativo. O runtime de schemas foi projetado
para aceitar somente releases completas, curadas e assinadas pelo canal próprio do projeto; SVRS e
ACBr são pesquisa/proveniência, nunca fallback runtime. O canal publicado usa GitHub Pages,
manifesto Ed25519 e releases imutáveis; a ativação continua exigindo validação local e só terá
efeito no boot seguinte. A tabela fiscal segue no canal SVRS independente.

As tasks Gradle históricas `updateSchemas` e `updateFiscalTables` estão intencionalmente bloqueadas:
elas não são a atualização do usuário final e não podem sobrescrever `src/main/resources`. Uma
alteração de base embarcada é manutenção de release: obter candidata do acervo curado em staging,
validar, revisar o diff e atualizar manifesto/proveniência no mesmo change.

Os detalhes de arquitetura, decisões e proveniência dos artefatos estão em
[`docs/`](./docs/). Para a estrutura do projeto, consulte também
[`docs/architecture.md`](./docs/architecture.md).

## Licença

[GPL-3.0](./LICENSE) — livre para usar, estudar, modificar e redistribuir; trabalhos derivados
devem permanecer abertos.
