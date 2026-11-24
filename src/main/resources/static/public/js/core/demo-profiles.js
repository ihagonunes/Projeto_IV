(() => {
    "use strict";
    const select = document.getElementById("demo-profile-select");
    const status = document.getElementById("demo-profile-status");
    if (!select || !status) return;
    const toggle = document.getElementById("demo-profile-toggle");
    const panel = document.getElementById("demo-profile-panel");
    toggle?.addEventListener("click", () => {
        panel.hidden = !panel.hidden;
        toggle.setAttribute("aria-expanded", String(!panel.hidden));
        toggle.setAttribute("aria-label", panel.hidden ? "Mostrar troca de conta" : "Esconder troca de conta");
        if (!panel.hidden) select.focus();
    });
    const endpoint = "/api/v1/auth/profiles";
    const labels = {
        administrador: "Administrador", executivo: "Executivo", gestor: "Gestor",
        lider: "Líder", colaborador: "Colaborador"
    };
    async function load() {
        const response = await fetch(endpoint, { cache: "no-store" });
        if (!response.ok) throw new Error("Não foi possível consultar as contas salvas.");
        const { profiles } = await response.json();
        const current = document.getElementById("demo-profile-current");
        if (current) current.textContent = labels[profiles.find(p => p.active)?.profile] || "Sessão corporativa";
        select.replaceChildren(new Option(profiles.length ? "Trocar conta" : "Nenhuma conta salva", ""));
        for (const { profile, active } of profiles) {
            const option = new Option(labels[profile] + (active ? " · atual" : ""), profile);
            select.add(option);
            if (active) select.value = profile;
        }
        select.disabled = profiles.length === 0;
    }
    select.addEventListener("change", async () => {
        if (!select.value) return;
        select.disabled = true;
        status.textContent = "Trocando…";
        try {
            const response = await fetch(endpoint, {
                method: "POST", headers: { "Content-Type": "application/json", "X-Qsoft-Request": "1" },
                body: JSON.stringify({ profile: select.value })
            });
            const result = await response.json();
            if (!response.ok) throw new Error(result.message || "Não foi possível trocar de conta.");
            try {
                localStorage.removeItem("qsoft.user");
                localStorage.removeItem("authToken");
                localStorage.setItem("qsoft.session", "1");
                localStorage.setItem("qsoft.profile-changed", String(Date.now()));
            } catch { /* a sessão HTTP é a autoridade */ }
            // Stay in the chat when the new account can use it. Navigate to a
            // clean URL so a flow from the previous account is not carried over.
            const destination = location.pathname.replace(/\/+$/, "") === "/chat"
                && result.destination === "/dashboard" ? "/chat" : result.destination;
            location.assign(destination);
        } catch (error) {
            status.textContent = error.message;
            try { await load(); } catch { select.disabled = true; }
        }
    });
    window.addEventListener("storage", event => {
        if (event.key === "qsoft.profile-changed") {
            if (location.pathname.replace(/\/+$/, "") === "/chat") location.assign("/chat");
            else location.reload();
        }
    });
    load().catch(error => { status.textContent = error.message; });
})();
