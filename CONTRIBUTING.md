# Contribuindo

## Regras de engenharia

- Mudanças funcionais devem ser pequenas, revisáveis e acompanhadas de teste quando houver lógica determinística.
- Não adicionar permissões Android sem documentar a finalidade em `SECURITY.md`.
- Não adicionar SDK de analytics, publicidade ou rede sem decisão explícita de produto.
- Não armazenar conteúdo de notificações em disco.
- Tratar fabricantes/OEMs como compatibilidade opcional: toda integração específica deve possuir fallback Android padrão.
- O app não deve depender de root, ADB permanente ou Accessibility Service para funcionar.

## Validação mínima

Antes de mergear:

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Em CI, os mesmos gates são executados automaticamente.
