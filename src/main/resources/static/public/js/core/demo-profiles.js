(() => {
    "use strict";
    const select = document.getElementById("demo-profile-select");
    const status = document.getElementById("demo-profile-status");
    const endpoint = "/api/v1/auth/profiles";
    const labels = {
        administrador: "Administrador", executivo: "Executivo", gestor: "Gestor",
        lider: "Líder", colaborador: "Colaborador"
    };
    async function load() {
        const response = await fetch(endpoint, { cache: "no-store" });
        if (!response.ok) throw new Error("Não foi possível consultar as contas salvas.");
        const { profiles } = await response.json();
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
            location.assign(result.destination);
        } catch (error) {
            status.textContent = error.message;
            try { await load(); } catch { select.disabled = true; }
        }
    });
    window.addEventListener("storage", event => {
        if (event.key === "qsoft.profile-changed") location.reload();
    });
    load().catch(error => { status.textContent = error.message; });
})();
