// aceite-convite.js
// Lê o token e o e-mail da query string, valida o formulário e faz
// POST /api/auth/aceitar-convite via ApiClient (proxy BFF → Spring Boot).
// SPI-30.
(function () {
    'use strict';

    var form           = document.getElementById('aceiteConviteForm');
    var nomeInput      = document.getElementById('nomeCompleto');
    var emailInput     = document.getElementById('emailConvite');
    var senhaInput     = document.getElementById('senha');
    var confirmarInput = document.getElementById('confirmarSenha');
    var btnConcluir    = document.getElementById('btnConcluir');
    var errorMsg       = document.getElementById('aceiteErrorMessage');

    // Lê token e e-mail da query string (injetados pelo link de convite)
    var params = new URLSearchParams(window.location.search);
    var token  = params.get('token') || '';
    var email  = params.get('email') || '';

    // Preenche e-mail readonly se disponível na URL
    if (emailInput && email) {
        emailInput.value = decodeURIComponent(email);
    }

    // Convite sem token — redireciona para login
    if (!token) {
        window.location.href = '/login';
        return;
    }

    // Carrega a arte ASCII (igual ao redefinir-senha.js)
    var asciiContainer = document.getElementById('asciiArtContainer');
    if (asciiContainer) {
        fetch('/public/txt/atlas-ascii.txt')
            .then(function (r) {
                if (!r.ok) throw new Error('ASCII não encontrado');
                return r.text();
            })
            .then(function (text) {
                asciiContainer.textContent = text;
            })
            .catch(function (err) {
                console.error('Erro ao carregar ASCII art:', err);
            });
    }

    // -----------------------------------------------------------------------
    // Helpers de UI
    // -----------------------------------------------------------------------

    function showError(message) {
        if (!errorMsg) return;
        errorMsg.textContent = message;
        errorMsg.classList.remove('d-none');
    }

    function hideError() {
        if (!errorMsg) return;
        errorMsg.textContent = '';
        errorMsg.classList.add('d-none');
    }

    function setLoading(loading) {
        if (!btnConcluir) return;
        btnConcluir.disabled = loading;
        btnConcluir.textContent = loading ? 'aguarde…' : 'concluir cadastro';
    }

    function clearErrors() {
        [nomeInput, senhaInput, confirmarInput].forEach(function (el) {
            if (el) el.classList.remove('is-invalid');
        });
        hideError();
    }

    function markInvalid(input, message) {
        if (!input) return;
        input.classList.add('is-invalid');
        var feedback = input.parentElement
            ? input.parentElement.querySelector('.invalid-feedback')
            : null;
        if (feedback && message) {
            feedback.textContent = message;
        }
    }

    // -----------------------------------------------------------------------
    // Validação local
    // -----------------------------------------------------------------------

    function validate() {
        var valid = true;
        clearErrors();

        if (!nomeInput || !nomeInput.value.trim()) {
            markInvalid(nomeInput, 'Informe seu nome completo.');
            valid = false;
        }

        if (!senhaInput || !senhaInput.value) {
            markInvalid(senhaInput, 'Informe uma senha.');
            valid = false;
        }

        if (senhaInput && confirmarInput && senhaInput.value !== confirmarInput.value) {
            markInvalid(confirmarInput, 'As senhas não coincidem.');
            valid = false;
        }

        return valid;
    }

    // -----------------------------------------------------------------------
    // Submit
    // -----------------------------------------------------------------------

    if (form) {
        form.addEventListener('submit', async function (e) {
            e.preventDefault();

            if (!validate()) return;

            setLoading(true);
            try {
                await ApiClient.post('/v1/convites/aceitar', {
                    token: token,
                    nomeCompleto: nomeInput.value.trim(),
                    password: senhaInput.value
                });

                // Sucesso: redireciona ao login com flag para exibir feedback
                window.location.href = '/login?cadastro=concluido';
            } catch (err) {
                var msg = (err && err.message)
                    ? err.message
                    : 'Não foi possível concluir o cadastro. Tente novamente.';
                showError(msg);
            } finally {
                setLoading(false);
            }
        });
    }

    // Limpa is-invalid ao digitar
    [nomeInput, senhaInput, confirmarInput].forEach(function (el) {
        if (el) {
            el.addEventListener('input', function () {
                el.classList.remove('is-invalid');
                hideError();
            });
        }
    });
})();
