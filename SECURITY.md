# Política de segurança — SX Dynamic

## Princípios

1. **Menor privilégio:** nenhuma permissão é adicionada sem função concreta.
2. **Local-first:** a base não possui `INTERNET`.
3. **Sem persistência de conteúdo sensível:** título e texto de notificações são mantidos apenas em memória durante a exibição.
4. **Sem analytics na fundação:** nenhum SDK de rastreamento foi incluído.
5. **Falha segura:** se a permissão de overlay for removida, o serviço encerra o overlay em vez de tentar contornar o sistema.
6. **Sem Accessibility Service:** o app não usa acessibilidade para capturar interface ou cliques de outros apps.

## Permissões sensíveis

- `SYSTEM_ALERT_WINDOW`: necessária para desenhar a ilha sobre outros apps.
- `BIND_NOTIFICATION_LISTENER_SERVICE`: concedida pelo usuário ao serviço de notificações.
- `POST_NOTIFICATIONS`: usada para a notificação do serviço em primeiro plano em Android 13+.
- `FOREGROUND_SERVICE_SPECIAL_USE`: usada para o serviço persistente de overlay em Android moderno.
- `RECEIVE_BOOT_COMPLETED`: permite restaurar a função somente se ela já estiver habilitada pelo usuário.

## Publicação

A classificação `specialUse` de serviço em primeiro plano deve ser declarada e justificada também no processo de publicação da Play Store. A justificativa deve corresponder ao comportamento real do app.

## Relato de vulnerabilidade

Não publique segredos, tokens, chaves de assinatura ou dados pessoais em issues públicas.
