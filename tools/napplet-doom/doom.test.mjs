// Run: tools/napplet-doom/build.sh && node --test tools/napplet-doom/doom.test.mjs
// Plays the built Freedoom napplet in headless Chromium inside Amethyst's production shell.html,
// shim.js and NIP-5D CSP, injected exactly as NappletWebContract.injectPrelude does on the device.
// The native bridge is a test double that answers storage requests from an in-memory map.
// PLAYWRIGHT_MODULE and CHROMIUM override the Playwright module and browser binary.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { chromium } = process.env.PLAYWRIGHT_MODULE ? await import(process.env.PLAYWRIGHT_MODULE) : require('playwright');
const root = new URL('../../', import.meta.url);
const shim = await readFile(new URL('commonsUI/src/commonMain/composeResources/files/napplet/shim.js', root), 'utf8');
const shell = await readFile(new URL('commonsUI/src/commonMain/composeResources/files/napplet/shell.html', root), 'utf8');
const contract = await readFile(new URL('commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/napplet/NappletWebContract.kt', root), 'utf8');
const appCsp = contract.match(/const val APP_CSP: String =([\s\S]*?)\n\n/)[1].match(/"[^"]*"/g).map(s => JSON.parse(s)).join('');
const shellCsp = contract.match(/fun shellCsp[^=]*=([\s\S]*?)\n\n/)[1].match(/"[^"]*"/g).map(s => JSON.parse(s)).join('').replace('$frameSource', "'self'");
const artifact = await readFile(process.env.DOOM_HTML || new URL('build/index.html', import.meta.url));
const screens = new URL('build/screens/', import.meta.url);
await mkdir(screens, { recursive: true });

// NappletWebContract.injectPrelude for the locked NIP-5D posture.
function injectPrelude(html, domains) {
  const prelude = `<meta http-equiv="Content-Security-Policy" content="${appCsp}">` +
    '<style>html,body{overscroll-behavior:none !important}</style>' +
    '<script>window.__nappletNip07=false;</script>' +
    `<script>window.__nappletDomains=${JSON.stringify(domains)};${shim}</script>`;
  return Buffer.concat([Buffer.from(`<!doctype html><html><head>${prelude}`), html]);
}

async function play(run, { domains = ['storage'], storage = {}, deny = false } = {}) {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM, args: ['--autoplay-policy=no-user-gesture-required'] });
  try {
    const page = await browser.newPage({ viewport: { width: 960, height: 640 } });
    page.setDefaultTimeout(30000);
    const consoleErrors = [];
    page.on('console', msg => { if (msg.type() === 'error' || /Content Security Policy/.test(msg.text())) consoleErrors.push(msg.text()); });
    page.on('pageerror', err => consoleErrors.push(String(err)));
    const bridge = `<script>
      window.store = new Map(${JSON.stringify(Object.entries(storage))});
      window.requests = [];
      window.__nappletBridge = { postMessage(raw) {
        const m = JSON.parse(raw);
        window.requests.push({ type: m.type, key: m.key });
        let reply;
        if (${deny}) reply = { type: m.type + '.result', id: m.id, ok: false, error: 'denied', reason: 'Denied by the user' };
        else if (m.type === 'storage.keys') reply = { type: 'storage.keys.result', id: m.id, keys: [...window.store.keys()] };
        else if (m.type === 'storage.get') reply = { type: 'storage.get.result', id: m.id, value: window.store.get(m.key) ?? null };
        else if (m.type === 'storage.set') { window.store.set(m.key, m.value); reply = { type: 'storage.set.result', id: m.id }; }
        else if (m.type === 'storage.remove') { window.store.delete(m.key); reply = { type: 'storage.remove.result', id: m.id }; }
        else reply = { type: m.type + '.result', id: m.id, ok: false, error: 'unsupported' };
        setTimeout(() => this.onmessage({ data: JSON.stringify(reply) }), 5);
      } };
    <\/script>`;
    // The Android host's bootstrap: base64 in the shell, decoded into the opaque srcdoc child.
    const encoded = injectPrelude(artifact, domains).toString('base64');
    const served = shell.replace('<head>', '<head>' + bridge).replace('__APP_SANDBOX__', 'allow-scripts').replace('__APP_BOOTSTRAP__',
      `var b=atob('${encoded}'),u=new Uint8Array(b.length);for(var j=0;j<b.length;j++)u[j]=b.charCodeAt(j);iframe.srcdoc=new TextDecoder('utf-8').decode(u);`);
    await page.route('https://napplet.local/**', route => route.fulfill({ contentType: 'text/html', headers: { 'Content-Security-Policy': shellCsp }, body: served }));
    await page.goto('https://napplet.local/__shell__');
    await page.waitForFunction(() => document.querySelector('iframe')?.contentWindow != null);
    const frame = page.frames().find(f => f !== page.mainFrame());
    await frame.waitForFunction(() => window.doomStatus && window.doomStatus.phase !== 'loading');
    assert.equal(await frame.evaluate(() => window.doomStatus.phase), 'ready');
    const state = () => frame.evaluate(() => ({ ...window.doomStatus, files: window.doomStatus.files() }));
    const key = async (name, wait = 250) => { await page.keyboard.press(name); await page.waitForTimeout(wait); };
    const pixels = () => frame.evaluate(() => {
      const c = document.getElementById('canvas');
      return Array.from(c.getContext('2d').getImageData(0, 0, c.width, c.height).data.filter((_, i) => i % 4 !== 3 && i % 64 === 0));
    });
    const start = async () => {
      await frame.click('#play');
      await frame.waitForFunction(() => window.doomStatus.frames > 20);
    };
    const newGame = async () => {
      // Title loop -> main menu -> New Game -> episode 1 -> skill 3 -> the melt into E1M1.
      await key('Escape', 400);
      await key('Enter', 400);
      await key('Enter', 400);
      await key('Enter', 2500);
    };
    await run({ page, frame, state, key, pixels, start, newGame, consoleErrors });
  } finally { await browser.close(); }
}

const changed = (a, b) => a.filter((v, i) => Math.abs(v - b[i]) > 8).length;

test('boots under the napplet CSP, renders the title loop, plays sound effects and music', async () => {
  await play(async ({ page, frame, state, pixels, start, consoleErrors }) => {
    await start();
    await frame.waitForFunction(() => window.doomStatus.songs > 0 && window.doomStatus.notes > 20);
    const s = await state();
    assert.equal(s.phase, 'running');
    assert.ok(s.tics > 0);
    assert.ok(new Set(await pixels()).size > 16, 'the title screen is not a blank frame');
    await page.screenshot({ path: new URL('title.png', screens).pathname });
    assert.deepEqual(consoleErrors, []);
  });
});

test('the keyboard starts a game, and the view moves when the player turns', async () => {
  await play(async ({ page, frame, state, key, pixels, start, newGame, consoleErrors }) => {
    await start();
    await newGame();
    await page.screenshot({ path: new URL('e1m1.png', screens).pathname });
    const before = await pixels();
    await page.keyboard.down('ArrowLeft');
    await page.waitForTimeout(600);
    await page.keyboard.up('ArrowLeft');
    await page.waitForTimeout(200);
    assert.ok(changed(before, await pixels()) > 200, 'turning redraws the 3D view');
    await page.keyboard.down('Control');
    await page.waitForTimeout(300);
    await page.keyboard.up('Control');
    await frame.waitForFunction(() => window.doomStatus.sounds > 0);
    await page.screenshot({ path: new URL('turned-and-fired.png', screens).pathname });
    assert.equal((await state()).phase, 'running');
    assert.deepEqual(consoleErrors, []);
  });
});

test('a save game lands in napplet storage and loads back on the next launch', async () => {
  let saved;
  await play(async ({ page, frame, key, start, newGame }) => {
    await start();
    await newGame();
    await key('F2', 500);        // save menu
    await key('Enter', 300);     // slot 1 -> edit its description
    await key('a', 150);
    await key('Enter', 1500);    // save; the page persists it after the engine closes the file
    await page.waitForFunction(() => [...window.store.keys()].some(k => k.endsWith('.dsg')));
    saved = Object.fromEntries(await page.evaluate(() => [...window.store.entries()]));
  });
  const keys = Object.keys(saved);
  assert.ok(keys.some(k => /^fs:\/.*\/doomsav0\.dsg$/.test(k)), `saved keys: ${keys}`);
  const bytes = Object.values(saved).reduce((n, v) => n + v.length, 0);
  assert.ok(bytes < 512 * 1024, `fits the 512 KiB napplet storage quota (${bytes} bytes)`);

  await play(async ({ page, frame, state, key, start }) => {
    await start();
    assert.ok((await state()).files.some(f => f.endsWith('doomsav0.dsg')), 'the save is restored into the file system');
    await key('F3', 500);        // load menu lists the restored slot
    await key('Enter', 2500);    // load it
    await page.screenshot({ path: new URL('loaded.png', screens).pathname });
    assert.equal((await state()).phase, 'running');
    const types = await page.evaluate(() => window.requests.map(r => r.type));
    assert.ok(types.includes('storage.keys') && types.includes('storage.get'));
  }, { storage: saved });
});

test('denied storage leaves the game playable', async () => {
  await play(async ({ frame, state, start, newGame, pixels }) => {
    await start();
    await newGame();
    const s = await state();
    assert.equal(s.phase, 'running');
    assert.ok(new Set(await pixels()).size > 16);
    assert.match(await frame.evaluate(() => document.getElementById('toast').textContent), /Saves last until you close the game/);
  }, { deny: true });
});

test('without the storage domain the game still runs (no window.napplet.storage)', async () => {
  await play(async ({ frame, start }) => {
    assert.equal(await frame.evaluate(() => typeof window.napplet?.storage), 'undefined');
    await start();
    assert.equal(await frame.evaluate(() => window.doomStatus.phase), 'running');
  }, { domains: [] });
});
