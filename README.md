# SX Dynamic

SX Dynamic é um aplicativo Android nativo da **Solucionx** que transforma o recorte da câmera frontal em uma área dinâmica para notificações, mídia e eventos do dispositivo.

O alvo inicial é o **Poco X8 Pro / HyperOS**, mas a arquitetura evita dependência rígida de um modelo específico e usa APIs públicas do Android sempre que possível.

## Estado do projeto

**v0.1.0 — fundação técnica**

A primeira base inclui:

- overlay nativo com `TYPE_APPLICATION_OVERLAY`;
- posicionamento orientado por `DisplayCutout`/`WindowInsets`;
- `NotificationListenerService` com filtragem de ruído e modo privacidade;
- monitor de sessões de mídia com play/pause/anterior/próxima;
- monitor de bateria e carregamento;
- serviço persistente compatível com tipos de foreground service modernos;
- restauração após reinício quando o usuário deixou o recurso ativado;
- ajustes persistidos com DataStore;
- painel de permissões e diagnóstico local;
- tratamento específico e seguro para abrir a tela de auto-início do HyperOS, com fallback;
- UI própria em Jetpack Compose;
- CI para testes, lint, validação do Gradle Wrapper e APK de debug.

## Arquitetura

```text
app/
├── core/                 infraestrutura, permissões, diagnóstico
├── data/                 preferências persistentes
├── domain/               estado e coordenação da ilha
├── service/
│   ├── notification/     captura e filtragem de notificações
│   ├── overlay/          janela dinâmica, mídia e bateria
│   └── boot/             restauração controlada após reinício
└── ui/
    ├── app/              painel do aplicativo
    ├── overlay/          interface da ilha
    └── theme/            identidade visual
```

A intenção é manter o núcleo previsível e testável antes de aumentar o número de integrações. Recursos novos entram como produtores de eventos para o coordenador, sem transformar o serviço de overlay em uma classe monolítica.

## Privacidade

SX Dynamic não precisa de internet para seu funcionamento principal. O projeto não declara permissão de rede e não envia conteúdo de notificações para servidores. As preferências ficam localmente no aparelho e o backup do Android está desativado nesta fase.

## Compilação

Requisitos recomendados:

- JDK 17;
- Android SDK 37;
- Android Gradle Plugin 9.4;
- Gradle 9.6.0;
- Kotlin 2.4.10;
- Compose BOM 2026.09.00.

O repositório inclui Gradle Wrapper fixado em 9.6.0 e o CI valida o wrapper antes do build.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

O APK de CI é apenas para testes internos. Releases de produção terão assinatura própria da Solucionx antes de serem distribuídas como atualização.

## Segurança e permissões

O app solicita somente permissões necessárias ao produto:

- `SYSTEM_ALERT_WINDOW`: janela da ilha sobre outros apps;
- acesso de Notification Listener concedido manualmente pelo usuário;
- `POST_NOTIFICATIONS`: notificação do foreground service em Android recente;
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`: serviço persistente explicitamente ativado;
- `RECEIVE_BOOT_COMPLETED`: restauração opcional quando o usuário deixou o SX Dynamic ativado.

Nenhuma permissão de Acessibilidade é usada na base.

## Licença

Copyright © Solucionx. Todos os direitos reservados, salvo definição posterior no repositório.

## Objetivo de qualidade

A meta do projeto não é apenas reproduzir uma “ilha dinâmica”. A base prioriza previsibilidade, privacidade local, recuperação de falhas, baixo acoplamento ao HyperOS e evolução por produtores de eventos independentes. Consulte `docs/QUALITY.md` para os gates obrigatórios.
