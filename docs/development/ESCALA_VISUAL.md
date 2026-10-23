# Escala visual global

A interface usa escala de 75% em todas as páginas que compartilham o cabeçalho EJS,
conforme solicitação do usuário. Configuração em
`src/main/resources/static/public/css/base/display-scale.css`, variável
`--app-display-scale`. O estilo é carregado por `views/partials/cabecalho.ejs`.

O mecanismo é CSS `zoom`, não alteração das preferências do navegador. Para a
escala pretendida, o navegador deve estar em 100%. Zoom do navegador em 75%
com escala CSS de 75% resulta em 56,25% do tamanho original. O usuário continua
podendo ampliar a página. CSS zoom não altera os breakpoints das media queries
como o zoom do navegador, portanto não é uma reprodução exata dessa preferência.

As dimensões de tela cheia de login, onboarding e dashboards usam
`--app-viewport-height`/`--app-viewport-width` para compensar a escala e manter
o preenchimento da janela. Impressão mantém escala CSS normal.
Após atualizar estilos já armazenados em cache, recarregar com Ctrl+F5.
