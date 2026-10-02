# Arquitetura do SX Dynamic

## Princípios

1. **Serviço pequeno, produtores separados.** Notificações, mídia e bateria não devem conhecer detalhes de renderização.
2. **Estado único da ilha.** `OverlayCoordinator` resolve qual conteúdo está ativo e retorna ao estado-base após eventos transitórios.
3. **Sem dependência de Acessibilidade.** Interações usam APIs oficiais de notificação, mídia e overlay.
4. **Configuração persistente.** Preferências usam DataStore e não `SharedPreferences` ad-hoc.
5. **Falha controlada.** Permissões ausentes não devem derrubar o processo; o app registra o evento e apresenta orientação.
6. **HyperOS tratado como adaptação, não como arquitetura.** Integrações MIUI/HyperOS ficam encapsuladas e têm fallback para telas padrão do Android.

## Fluxo de eventos

```text
NotificationListener ─┐
MediaMonitor ─────────┼──> OverlayCoordinator ──> StateFlow<OverlayUiState> ──> DynamicIsland
BatteryMonitor ───────┘
```

Eventos transitórios, como notificação e mudança de carregamento, têm prioridade temporária. A sessão de mídia atua como estado-base quando existe reprodução ativa.

## Evolução planejada

- fonte de eventos de chamadas com comportamento específico;
- temporizador/cronômetro;
- progresso de downloads quando a origem expuser informação confiável;
- filtros por aplicativo sem depender de `QUERY_ALL_PACKAGES`;
- perfis de tamanho e posição por dispositivo;
- telemetria **local e opt-in** para diagnóstico de estabilidade;
- suíte de testes instrumentados em dispositivo físico.
