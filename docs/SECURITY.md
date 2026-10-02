# Segurança e privacidade

## Modelo atual

SX Dynamic processa notificações e informações de mídia **localmente**. Ele não possui backend próprio nem transmite esse conteúdo para a Solucionx. A rede é usada apenas para consultar e baixar releases oficiais do GitHub.

## Decisões deliberadas

- `android:allowBackup="false"` para evitar cópia automática de preferências nesta fase;
- `android:usesCleartextTraffic="false"`, mantendo o atualizador em HTTPS;
- nenhum uso de Accessibility Service;
- serviços não públicos usam `android:exported="false"`;
- Notification Listener só é exportado com a permissão de binding exigida pelo próprio Android;
- conteúdo marcado como secreto pelo emissor é redigido;
- modo privacidade permite ocultar títulos e textos independentemente do app de origem;
- logs diagnósticos são limitados em memória e não persistem conteúdo após o processo encerrar.

## Publicação na Play Store

O tipo de foreground service `specialUse` deve ser justificado no Play Console. A declaração no manifesto descreve a finalidade do overlay persistente. Antes de publicação pública, revisar novamente as políticas vigentes do Google Play e as regras de versões do Android alvo.

- o atualizador consulta somente o repositório oficial `solucionx/SX-Dynamic` e delega a confirmação final ao instalador do Android.
