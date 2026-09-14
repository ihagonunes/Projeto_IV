document.addEventListener('DOMContentLoaded', () => {
    const codeInputs = document.querySelectorAll('.code-input');

    codeInputs.forEach((input, index) => {
        // 1. Apenas números e avanço automático
        input.addEventListener('input', (e) => {
            // Remove qualquer caractere que NÃO seja número (0-9)
            input.value = input.value.replace(/[^0-9]/g, '');

            // Se um número foi inserido, pula para a próxima caixa
            if (input.value && index < codeInputs.length - 1) {
                codeInputs[index + 1].focus();
            }
        });

        // 2. Voltar para a caixa anterior com Backspace
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Backspace' && !input.value && index > 0) {
                codeInputs[index - 1].focus();
            }
        });

        // 3. Permitir colar o código de 6 dígitos de uma vez (Ctrl+V)
        input.addEventListener('paste', (e) => {
            e.preventDefault();
            // Pega o texto colado e deixa apenas os números
            const pastedData = (e.clipboardData || window.clipboardData)
                .getData('text')
                .replace(/[^0-9]/g, '');

            // Distribui cada número nas caixas sequencialmente
            pastedData.split('').forEach((char, i) => {
                if (index + i < codeInputs.length) {
                    codeInputs[index + i].value = char;
                    codeInputs[index + i].focus();
                }
            });
        });
    });

    // 4. Carrega a arte ASCII dinamicamente via Fetch
    const asciiArtContainer = document.getElementById('asciiArtContainer');
    if (asciiArtContainer) {
        fetch('/public/txt/atlas-ascii.txt')
            .then(response => {
                if (!response.ok) throw new Error('Não foi possível carregar a arte ASCII');
                return response.text();
            })
            .then(text => {
                asciiArtContainer.textContent = text;
            })
            .catch(err => {
                console.error('Erro ao carregar ASCII art:', err);
            });
    }
});