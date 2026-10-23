/* SPI-53 / FEAT-DASH-001: identidade visual real do tenant autenticado. */
document.addEventListener('DOMContentLoaded', async () => {
    const el = id => document.getElementById(id);
    const form = el('identidadeForm'), logo = el('logoUrl'), save = el('btnSalvarIdentidade');
    let branding = {}, loaded = false;
    function alert(message, type) {
        const item = document.createElement('div');
        item.className = 'alert alert-' + type;
        item.setAttribute('role', 'alert'); item.textContent = message;
        el('alertArea').replaceChildren(item);
    }
    function preview() {
        const value = logo.value.trim();
        for (const id of ['logoPreviewThumb', 'previewNavbarLogo']) {
            const image = el(id);
            if (value && logo.checkValidity()) image.src = value;
            else image.removeAttribute('src');
            image.classList.toggle('d-none', !value || !logo.checkValidity());
        }
        el('logoPreviewThumbContainer').classList.toggle('d-none', !value);
        el('uploadIcon').classList.toggle('d-none', !!value);
    }
    logo.addEventListener('input', preview);
    save.disabled = true;
    try {
        const [tenant, current] = await Promise.all([
            API.get('/tenants/me'), API.get('/tenants/me/identidade-visual')
        ]);
        branding = current;
        el('nomeExibicao').value = tenant.name;
        el('tenantContextName').textContent = tenant.name;
        el('previewNavbarBrand').textContent = tenant.name;
        logo.value = branding.logoUrl || '';
        preview(); loaded = true; save.disabled = false;
    } catch {
        alert('Não foi possível carregar a identidade visual. Recarregue a página para tentar novamente.', 'danger');
    }
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (!loaded || save.disabled) return;
        if (!form.checkValidity()) { form.reportValidity(); return; }
        save.disabled = true;
        el('salvarSpinner').classList.remove('d-none');
        try {
            branding = await API.put('/tenants/me/identidade-visual', {
                logoUrl: logo.value.trim() || null,
                primaryColor: branding.primaryColor,
                secondaryColor: branding.secondaryColor
            });
            logo.value = branding.logoUrl || ''; preview();
            alert('Identidade visual salva com sucesso.', 'success');
        } catch {
            alert('Não foi possível salvar a identidade visual. Confira sua permissão e tente novamente.', 'danger');
        } finally {
            save.disabled = false; el('salvarSpinner').classList.add('d-none');
        }
    });
});
