// js/pages/login.js

// 1. Captura dos elementos do DOM
const form = document.getElementById('loginForm');
const inputEmail = document.getElementById('email');
const inputSenha = document.getElementById('senha');
const btnEsqueciSenha = document.getElementById('btnEsqueciSenha');
const loginErrorMessage = document.getElementById('loginErrorMessage');

function hideErrorMessage() {
    if (loginErrorMessage) {
        loginErrorMessage.textContent = '';
        loginErrorMessage.classList.add('d-none');
    }
}

function showErrorMessage(msg) {
    if (loginErrorMessage) {
        loginErrorMessage.textContent = msg;
        loginErrorMessage.classList.remove('d-none');
    }
}

// Exibe mensagem de sucesso quando o usuário conclui o cadastro via aceite de convite
(function () {
    var params = new URLSearchParams(window.location.search);
    if (params.get('cadastro') === 'concluido' && loginErrorMessage) {
        loginErrorMessage.textContent = 'Cadastro concluído! Faça login para continuar.';
        loginErrorMessage.style.color = 'rgba(72,199,116,0.95)';
        loginErrorMessage.classList.remove('d-none');
    }
})();



// Exibe um aviso de erro no formulário, sem alterar o design do template.
function showLoginError(message) {
    const field = document.getElementById('loginForm');
    if (!field) return;
    const existing = field.querySelector('.login-error-banner');
    if (existing) existing.remove();
    const banner = document.createElement('div');
    banner.className = 'alert alert-danger text-center mt-3 login-error-banner';
    banner.setAttribute('role', 'alert');
    banner.textContent = message;
    field.appendChild(banner);
}


// 2. Evento de clique para Redefinir Senha
if (btnEsqueciSenha) {
    btnEsqueciSenha.addEventListener('click', function (event) {
        event.preventDefault();
        window.location.href = '/redefinir-senha';
    });
}


// 3. Validação do Formulário de Login
if (form) {
    form.addEventListener('submit', async function (event) {
        event.preventDefault(); 
        hideErrorMessage();


        let formularioValido = true;


        // Validação do E-mail
        if (inputEmail.value.trim() === '') {
            inputEmail.classList.add('is-invalid');
            formularioValido = false;
        } else {
            inputEmail.classList.remove('is-invalid');
        }


        // Validação da Senha
        if (inputSenha.value.trim() === '') {
            inputSenha.classList.add('is-invalid');
            formularioValido = false;
        } else {
            inputSenha.classList.remove('is-invalid');
        }


        if (formularioValido) {
            try {
                await Auth.login({ email: inputEmail.value, password: inputSenha.value });
                window.location.href = '/onboarding';
            } catch(e) {
                console.error("Erro ao realizar login:", e);
                const msg = e && e.message ? e.message : "Falha ao conectar ao servidor. Verifique se o backend está rodando.";
                showErrorMessage(msg);
            }
        }
    });
}


// 4. Limpeza da mensagem de erro ao digitar
if (inputEmail) {
    inputEmail.addEventListener('input', function () {
        inputEmail.classList.remove('is-invalid');
        hideErrorMessage();
    });
}


if (inputSenha) {
    inputSenha.addEventListener('input', function () {
        inputSenha.classList.remove('is-invalid');
        hideErrorMessage();
    });
}