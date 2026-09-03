// notifications.js
// Utilitário global para exibir toasts/alertas no BFF Node.
// Usado por qualquer página que precise de feedback visual sem depender do Bootstrap direto.
(function () {
    "use strict";

    /**
     * Exibe uma notificação toast na área de alerta da página.
     * @param {string} message  - Mensagem HTML a exibir.
     * @param {string} type     - Tipo Bootstrap: 'success' | 'danger' | 'warning' | 'info'.
     * @param {string} containerId - ID do elemento container (padrão: 'alertArea').
     */
    function show(message, type, containerId) {
        type = type || "info";
        containerId = containerId || "alertArea";
        var container = document.getElementById(containerId);
        if (!container) return;

        var alertDiv = document.createElement("div");
        alertDiv.className = "alert alert-" + type + " alert-dismissible fade show alert-toast shadow-sm";
        alertDiv.role = "alert";
        alertDiv.innerHTML =
            '<div class="d-flex align-items-center">' +
                '<span>' + message + '</span>' +
            '</div>' +
            '<button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Fechar"></button>';

        container.appendChild(alertDiv);

        setTimeout(function () {
            if (alertDiv && alertDiv.parentNode) {
                alertDiv.classList.remove("show");
                setTimeout(function () { alertDiv.remove(); }, 200);
            }
        }, 5000);
    }

    window.Notifications = {
        show: show,
        success: function (msg, id) { show(msg, "success", id); },
        danger:  function (msg, id) { show(msg, "danger",  id); },
        warning: function (msg, id) { show(msg, "warning", id); },
        info:    function (msg, id) { show(msg, "info",    id); }
    };
})();