# Changelog

## 0.1.2 - robustez do listener de notificações

### Corrigido
- O app agora detecta quando o acesso às notificações foi concedido, mas o listener ainda não conectou ao Android.
- Solicita reconexão do `NotificationListenerService` ao voltar das configurações.
- Exibe separadamente o estado de "Acesso às notificações" e "Listener do sistema".
- Registra, sem conteúdo sensível, quando uma notificação chega ao listener.
- Adiciona botão de teste para separar falha de overlay de falha do listener.
- Build de testes passa a usar uma assinatura debug estável para facilitar atualizações futuras.

## 0.1.1 - validação no Poco X8 Pro

### Alterado
- A ilha ociosa não exibe mais o texto "SX Dynamic" ao toque.
- Notificações exibem ícone real do aplicativo, remetente/título e mensagem.
- Sessões de mídia exibem capa/ícone, faixa, artista e controles.
- Estado compacto de mídia mostra capa à esquerda e equalizador animado à direita.
- Posicionamento usa a área real da tela/cutout e calibração vertical ampliada.

## 0.1.0 - fundação técnica

### Adicionado
- Estrutura Android nativa.
- Overlay dinâmico.
- Captura de notificações, mídia e eventos de energia.
- Preferências locais.
- Painel de configuração e diagnóstico.
- Restauração opcional após boot.
- CI com lint, validação do wrapper e build de APK de debug.
