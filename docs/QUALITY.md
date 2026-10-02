# Quality gates

O SX Dynamic não considera uma alteração pronta apenas porque ela compila localmente.

## Gates obrigatórios

- `testDebugUnitTest`
- `lintDebug`
- `assembleDebug`
- validação do Gradle Wrapper
- revisão de permissões e componentes exportados quando o Manifest mudar
- nenhuma permissão de Internet sem justificativa arquitetural explícita
- nenhuma dependência de Accessibility Service na fundação

## Critérios de estabilidade

O overlay deve falhar de forma controlada quando permissões forem removidas, o processo for recriado ou o HyperOS encerrar o serviço. Preferências persistentes não devem derrubar a UI por falha transitória de leitura. Eventos transitórios nunca devem destruir o estado-base de mídia.

## Dispositivo alvo

O primeiro aparelho de validação física é o Poco X8 Pro / HyperOS. A arquitetura continua baseada em APIs públicas do Android para evitar acoplamento desnecessário ao fabricante.
