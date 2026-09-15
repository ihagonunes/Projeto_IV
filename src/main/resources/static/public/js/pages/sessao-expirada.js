// sessao-expirada.js
// Countdown regressivo e redirecionamento automático ao login.
// Chamado pela view sessao-expirada.ejs (SPI-66).
(function () {
    'use strict';

    var REDIRECT_URL = '/login';
    var COUNTDOWN_SECONDS = 5;

    var countEl = document.getElementById('countdown');
    var btn = document.getElementById('btnEntrarNovamente');

    function goToLogin() {
        // Limpa token e dados de usuário antes de redirecionar
        if (window.API && typeof window.API.clearToken === 'function') {
            window.API.clearToken();
        }
        try { localStorage.removeItem('qsoft.user'); } catch (e) { /* ignore */ }
        window.location.href = REDIRECT_URL;
    }

    if (btn) {
        btn.addEventListener('click', goToLogin);
    }

    // Inicia o contador regressivo
    var remaining = COUNTDOWN_SECONDS;
    var timer = setInterval(function () {
        remaining -= 1;
        if (countEl) {
            countEl.textContent = remaining;
        }
        if (remaining <= 0) {
            clearInterval(timer);
            goToLogin();
        }
    }, 1000);
})();
