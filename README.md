# SX Dynamic

SX Dynamic é um aplicativo Android nativo da **Solucionx** que transforma o recorte da câmera frontal em uma área dinâmica para notificações, mídia e eventos do dispositivo.

O alvo inicial é o **Poco X8 Pro / HyperOS**, mas a arquitetura evita dependência rígida de um modelo específico e usa APIs públicas do Android sempre que possível.

## Estado do projeto

**v0.2.0 — fundação com atualização pelo GitHub Releases**

A primeira base inclui:

- overlay nativo com `TYPE_APPLICATION_OVERLAY`;
- posicionamento orientado por `DisplayCutout`/`WindowInsets`;
- `NotificationListenerService` com filtragem de ruído e modo privacidade;
- monitor de sessões de mídia com play/pause/anterior/próxima;
- monitor de bateria e carregamento;
- eventos de progresso, chamadas, alarmes e temporizadores;
- restauração controlada após reinício;
- preferências locais;
- painel profissional de permissões, recursos e ajuste fino;
- diagnóstico técnico local limitado;
- CI com lint, build e APK de debug.

## Arquitetura

```text
app/
├── core/                 permissões, diagnóstico e ciclo de vida
├── data/                 preferências persistentes
├── domain/               estado e prioridade da ilha
├── service/
│   ├── notification/     classificação de notificações
│   ├── overlay/          janela, mídia, bateria e renderização
│   └── boot/             restauração controlada
├── ui/                   painel e identidade visual
└── update/               consulta de releases, download e instalação assistida
```

O runtime principal continua baseado em APIs da plataforma Android, com acesso HTTPS ao GitHub apenas para verificar e baixar atualizações publicadas. Isso reduz tamanho, consumo, superfície de dependências e risco de incompatibilidade em um componente que precisa permanecer estável em segundo plano.

## Privacidade

SX Dynamic não precisa de internet para a ilha, notificações ou mídia. A permissão `INTERNET` é usada somente pelo atualizador para consultar `solucionx/SX-Dynamic` no GitHub Releases e baixar um APK publicado. Conteúdo de notificações não é enviado.

Conteúdo de notificações é processado em memória. O diagnóstico local registra apenas eventos técnicos e possui tamanho limitado.

## Compilação

Requisitos:

- JDK 17;
- Android SDK 36;
- Android Gradle Plugin 9.4;
- Gradle 9.6.0.

```bash
./gradlew lintDebug assembleDebug
```

O Gradle Wrapper oficial fica versionado no repositório e é validado pelo CI.

## Segurança e permissões

O app solicita somente permissões relacionadas às funções centrais:

- `SYSTEM_ALERT_WINDOW`: desenhar a ilha sobre outros apps;
- acesso de Notification Listener: receber eventos de notificações e permitir acesso às sessões de mídia;
- `POST_NOTIFICATIONS`: notificação do serviço em primeiro plano;
- `FOREGROUND_SERVICE_SPECIAL_USE`: manter o overlay explicitamente ativado;
- `RECEIVE_BOOT_COMPLETED`: restaurar a função quando ela já estava habilitada;
- `INTERNET`: consultar e baixar versões publicadas no GitHub Releases;
- `REQUEST_INSTALL_PACKAGES`: abrir o instalador do Android para concluir uma atualização baixada.

Nenhuma permissão de Acessibilidade é usada.

## Qualidade

A fundação prioriza:

- baixo acoplamento ao HyperOS;
- recuperação de falhas;
- filtros para evitar notificações inúteis;
- prioridade determinística entre chamada, temporizador, notificação, progresso, bateria e mídia;
- tamanho de janela animado de verdade, evitando uma grande área transparente capturando toques;
- logs técnicos limitados;
- validação automática no GitHub Actions.

Consulte `docs/QUALITY.md`, `docs/ARCHITECTURE.md` e `SECURITY.md`.

## Licença

Copyright © Solucionx. Todos os direitos reservados, salvo definição posterior no repositório.


## Atualizações

O SX Dynamic verifica automaticamente a release pública mais recente do repositório oficial. A verificação também pode ser iniciada manualmente no próprio app.

O fluxo é:

1. GitHub Actions compila e valida a versão.
2. O workflow `Draft Release` cria ou atualiza uma release em modo **draft**.
3. Enquanto estiver em draft, a versão não é oferecida pelo app.
4. Depois que o proprietário publica a release, o SX Dynamic detecta a nova versão.
5. O APK é baixado pelo Android DownloadManager.
6. O usuário confirma a instalação no instalador do Android.

Android não permite que um app comum se atualize silenciosamente; a confirmação final de instalação permanece obrigatória.

As builds de desenvolvimento usam uma assinatura de teste estável para permitir atualização por cima da versão anterior. Antes de distribuição pública de produção, a assinatura definitiva deve ficar protegida em GitHub Actions Secrets.
