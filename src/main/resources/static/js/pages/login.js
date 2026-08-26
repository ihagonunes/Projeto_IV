// js/pages/login.js

// 1. Captura dos elementos do DOM
const form = document.getElementById('loginForm');
const inputEmail = document.getElementById('email');
const inputSenha = document.getElementById('senha');
const btnEsqueciSenha = document.getElementById('btnEsqueciSenha');

// 2. Evento de clique para Redefinir Senha
if (btnEsqueciSenha) {
    btnEsqueciSenha.addEventListener('click', function (event) {
        event.preventDefault();
        // Como 'login.html' e 'redefinir-senha.html' estão na mesma pasta ('auth')
        window.location.href = 'redefinir-senha.html';
    });
}

// 3. Validação do Formulário de Login
if (form) {
    form.addEventListener('submit', function (event) {
        event.preventDefault(); 

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
            console.log("Tudo preenchido! Enviando dados para a API...");
        }
    });
}

// 4. Limpeza da mensagem de erro ao digitar
if (inputEmail) {
    inputEmail.addEventListener('input', function () {
        inputEmail.classList.remove('is-invalid');
    });
}

if (inputSenha) {
    inputSenha.addEventListener('input', function () {
        inputSenha.classList.remove('is-invalid');
    });
}