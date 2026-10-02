# Changelog

## 0.2.1 - estabilidade na rotação da tela

### Corrigido
- A ilha agora é ancorada no centro superior da tela em todas as orientações.
- O recorte lateral da câmera em modo paisagem não desloca mais a ilha para a esquerda ou direita.
- O serviço reage a mudanças de rotação/configuração sem precisar reiniciar.
- Adicionada escuta de alterações do display para reposicionar a janela assim que o HyperOS concluir a rotação.
- A atualização de geometria aguarda brevemente o `WindowMetrics` estabilizar, evitando usar dimensões antigas da orientação anterior.
- O ajuste vertical configurado pelo usuário continua sendo preservado após a rotação.


## 0.2.0 - atualização pelo GitHub Releases

### Adicionado
- Verificação automática da release pública mais recente em `solucionx/SX-Dynamic`.
- Botão **Verificar atualizações** dentro do aplicativo.
- Painel com versão instalada, versão disponível, tamanho e notas da release.
- Download do APK pelo `DownloadManager` do Android.
- Notificação quando o APK termina de baixar.
- Instalação assistida pelo instalador padrão do Android.
- Solicitação segura de permissão para instalar APKs desconhecidos apenas quando necessária.
- Cache local da release encontrada para reduzir chamadas ao GitHub.
- Verificação automática limitada a uma janela de 12 horas.
- Workflow **Draft Release**: compila, executa lint, gera APK + SHA-256 e cria/atualiza uma release em draft.
- Releases em draft não são oferecidas ao app; a atualização só aparece depois que o proprietário publica a release.

### Segurança
- O app passa a declarar `INTERNET` exclusivamente para o atualizador.
- Nenhum conteúdo de notificações ou mídia é enviado pela rotina de atualização.
- Builds debug usam assinatura de teste estável para permitir atualização por cima durante o desenvolvimento.
- A assinatura de produção deverá ser mantida fora do repositório, em GitHub Actions Secrets.

## 0.1.2 - robustez do listener de notificações

### Corrigido
- O app detecta quando o acesso às notificações foi concedido, mas o listener ainda não conectou ao Android.
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
