"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const ejs = require("ejs");
const base = path.resolve(__dirname, "../../main/resources/static");
const render = (file, data) => ejs.renderFile(path.join(base, file), { contentFor: () => "", ...data });

test("sessão: engrenagem abre e recolhe a troca de conta", async () => {
    const html = await render("views/partials/demo-profiles.ejs", {});
    assert.match(html, /aria-expanded="false" aria-controls="demo-profile-panel"/);
    assert.match(html, /id="demo-profile-panel"[^>]*hidden/);
    assert.match(html, /bi-gear/);
    const elements = {};
    for (const id of ["select", "status", "toggle", "panel", "current"]) {
        elements[`demo-profile-${id}`] = {
            hidden: id === "panel", attrs: {}, events: {},
            addEventListener(name, callback) { this.events[name] = callback; },
            setAttribute(name, value) { this.attrs[name] = value; },
            replaceChildren() {}, add() {}, focus() { this.focused = true; }
        };
    }
    require("node:vm").runInNewContext(require("node:fs").readFileSync(path.join(base, "public/js/core/demo-profiles.js"), "utf8"), {
        document: { getElementById: id => elements[id] }, window: { addEventListener() {} },
        Option: class {}, fetch: async () => ({ ok: true, json: async () => ({ profiles: [{ profile: "executivo", active: true }] }) })
    });
    const toggle = elements["demo-profile-toggle"], panel = elements["demo-profile-panel"];
    toggle.events.click();
    assert.equal(panel.hidden, false); assert.equal(toggle.attrs["aria-expanded"], "true");
    assert.equal(elements["demo-profile-select"].focused, true);
    toggle.events.click();
    assert.equal(panel.hidden, true); assert.equal(toggle.attrs["aria-expanded"], "false");
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(elements["demo-profile-current"].textContent, "Executivo");
});

test("sessão: seletor fica no workspace da sidebar do chat conectado", async () => {
    for (const enabled of [false, true]) {
        for (const preview of [false, true]) {
            const html = await render("modules/chat/views/index.ejs", { preview, demoProfilesEnabled: enabled });
            if (enabled && !preview) {
                assert.match(html, /class="chat-workspace">[\s\S]*class="demo-session-switcher"/);
                assert.ok(html.indexOf('id="demo-profile-select"') < html.indexOf('</aside>'));
                assert.equal((html.match(/id="demo-profile-select"/g) || []).length, 1);
                assert.match(html, /id="chat-help"/);
            } else assert.doesNotMatch(html, /id="demo-profile-select"/);
            assert.doesNotMatch(html, /demo-session-bar/);
        }
    }
});

test("sessão: dashboards compartilham seletor no rodapé da sidebar apenas no modo permitido", async () => {
    const layouts = require("../../main/resources/static/modules/dashboard/dashboards.layouts");
    for (const profile of Object.keys(layouts)) {
        for (const enabled of [false, true]) {
            const html = await render("modules/dashboard/views/index.ejs", {
                profile, panel: layouts[profile], indicatorReason: () => "", demoReturn: false,
                canManageBudget: false, demoProfilesEnabled: enabled
            });
            if (enabled) {
                assert.ok(html.indexOf('id="demo-profile-select"') > html.indexOf('id="dashboardLogout"'));
                assert.ok(html.indexOf('id="demo-profile-select"') < html.indexOf('</aside>'));
            } else assert.doesNotMatch(html, /id="demo-profile-select"/);
        }
    }
});

test("sessão: layout global não injeta troca de conta em login, onboarding ou cadastros", async () => {
    for (const page of ["login", "onboarding", "cadastros"]) {
        const html = await render("views/layout.ejs", {
            body: `<main>${page}</main>`, defineContent: () => "", demoProfilesEnabled: true
        });
        assert.doesNotMatch(html, /demo-profile-select|demo-session-bar|core\/demo-profiles.js/);
    }
});
