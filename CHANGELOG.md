# Changelog

## 0.1.1 - validação no Poco X8 Pro

### Alterado
- A ilha ociosa não exibe mais o texto "SX Dynamic" ao toque.
- Notificações agora exibem o ícone real do aplicativo, remetente/título e mensagem.
- Sessões de mídia exibem capa/ícone, faixa, artista e controles anterior/reproduzir-pausar/próxima.
- Estado compacto de mídia mostra capa à esquerda e equalizador animado à direita.
- Toque na área de mídia abre o aplicativo da sessão quando o Android fornece a ação.
- Posicionamento do overlay passa a usar a área real da tela/cutout com `FLAG_LAYOUT_IN_SCREEN`.
- Faixa de calibração vertical ampliada para -80 dp a +80 dp.
- Versão elevada para 0.1.1.

## 0.1.0 - fundação técnica

### Adicionado
- Estrutura Android nativa.
- Overlay dinâmico com estado recolhido/expandido.
- Captura de notificações, mídia e eventos de energia.
- Preferências locais.
- Painel de configuração, recursos e diagnóstico.
- Reinicialização opcional do overlay após boot.
- CI com lint, validação do wrapper e build de APK de debug.
