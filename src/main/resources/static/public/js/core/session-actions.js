// SPI-92: ação de sessão compartilhada pelos dashboards.
document.getElementById("dashboardLogout").addEventListener("click", async () => {
    try { await window.Auth.logout(); }
    catch (error) { window.alert("Não foi possível encerrar a sessão. Tente novamente."); }
});
