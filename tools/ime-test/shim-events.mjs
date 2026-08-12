// Regression test for the embedded-WebView IME relay's page→host protocol.
//
// Loads the REAL shim (commons/src/commonMain/composeResources/files/napplet/shim.js) into real Chromium
// with the embedded-surface flags set, drives genuine focus/tap/blur gestures, and asserts the `ime.*`
// envelopes it emits. This is the only honest automated coverage for this code: the host-side parser runs
// on Android's `org.json`, which the JVM unit tests stub out (`unitTests.isReturnDefaultValues = true`), so
// a Kotlin test of it would pass without parsing anything.
//
//   cd tools/ime-test && npm i playwright-core && node shim-events.mjs
//
// Exits 0 if every expectation holds, 1 otherwise. Override the browser with CHROMIUM_PATH, and the shim
// under test with argv[2] (useful for diffing a candidate against the committed one).

import { chromium } from 'playwright-core'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const HERE = dirname(fileURLToPath(import.meta.url))
const SHIM = process.argv[2] ?? resolve(HERE, '../../commons/src/commonMain/composeResources/files/napplet/shim.js')
const CHROMIUM = process.env.CHROMIUM_PATH ?? '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'

const HTML = `<!doctype html><meta charset=utf-8><title>ime</title>
<body style="margin:0;font:16px sans-serif">
<p id="para">plain page text, not editable</p>
<input id="inp" value="hello world" style="width:90%;height:40px">
<input id="ro" value="read only field" readonly style="width:90%;height:40px">
<div id="ce" contenteditable="true" style="border:1px solid #000;padding:8px">
  <span id="cespan">editable span text</span>
</div>
</body>`

const browser = await chromium.launch({ executablePath: CHROMIUM, args: ['--no-sandbox'] })
const context = await browser.newContext()

// Stand in for the native bridge the `:napplet` process installs: collect what the page sends, and keep the
// reply channel so host→page ops (`ime.resync`) can be delivered exactly as the host delivers them.
await context.addInitScript(() => {
  window.__sent = []
  window.__nappletDirectBridge = true
  window.__nappletImeProxy = true
  window.__nappletBridge = {
    postMessage(s) { window.__sent.push(s) },
    set onmessage(fn) { window.__imeIn = fn },
    get onmessage() { return window.__imeIn },
  }
})
await context.addInitScript({ content: readFileSync(SHIM, 'utf8') })
await context.route('https://ime.test/**', (route) => route.fulfill({ contentType: 'text/html', body: HTML }))
const page = await context.newPage()
await page.goto('https://ime.test/')

const drain = async () => {
  const raw = await page.evaluate(() => { const s = window.__sent.slice(); window.__sent.length = 0; return s })
  return raw.map((s) => JSON.parse(s)).filter((m) => (m.type || '').startsWith('ime.'))
}

const failures = []
const results = []

// `expect` is a predicate over the messages one gesture produced, described in words for the report.
const step = async (name, gesture, expectation) => {
  await gesture()
  await page.waitForTimeout(150)
  const msgs = await drain()
  const problem = expectation(msgs)
  const types = msgs.map((m) => m.type).join(', ') || '(nothing)'
  results.push([name, types, problem])
  if (problem) failures.push(`${name}: ${problem}\n      got: ${types}`)
}

const has = (msgs, type) => msgs.some((m) => m.type === type)
const find = (msgs, type) => msgs.find((m) => m.type === type)

await step(
  'tap an unfocused field announces it and asks for the keyboard',
  () => page.click('#inp'),
  (m) => {
    const focus = find(m, 'ime.focus')
    if (!focus) return 'no ime.focus'
    if (focus.text !== 'hello world') return `ime.focus carried text "${focus.text}"`
    if (focus.readOnly !== false) return 'ime.focus said readOnly on an editable field'
    return has(m, 'ime.wantkb') ? null : 'no ime.wantkb doorbell'
  },
)

// The regression this suite exists for: a tap on a field that never blurred fires no focus event, so before
// `ime.wantkb` existed the host had no signal at all and the keyboard could not be brought back.
await step(
  'tap the SAME already-focused field still rings the doorbell',
  () => page.click('#inp'),
  (m) => {
    if (!has(m, 'ime.wantkb')) return 'no ime.wantkb — the keyboard could never come back'
    if (has(m, 'ime.focus')) return 'unexpected ime.focus (page focus never moved)'
    return null
  },
)

// The doorbell must stay payload-free: it fires on every tap in a field, so attaching the editing state
// would put the whole field text on the wire per tap.
await step(
  'the doorbell carries no payload',
  () => page.click('#inp'),
  (m) => {
    const kb = find(m, 'ime.wantkb')
    if (!kb) return 'no ime.wantkb'
    const extra = Object.keys(kb).filter((k) => k !== 'type' && k !== 'id')
    return extra.length ? `ime.wantkb carried ${extra.join(', ')}` : null
  },
)

await step(
  'a host resync is answered with the focused field state',
  () => page.evaluate(() => window.__imeIn({ data: JSON.stringify({ type: 'ime.resync' }) })),
  (m) => {
    const re = find(m, 'ime.refocus')
    if (!re) return 'no ime.refocus'
    if (re.text !== 'hello world') return `ime.refocus carried text "${re.text}"`
    if (re.geom !== undefined) return 'ime.refocus carried geometry (forces a synchronous layout per send)'
    return null
  },
)

await step(
  'tapping off the field blurs it',
  () => page.click('#para'),
  (m) => (has(m, 'ime.blur') ? null : 'no ime.blur'),
)

await step(
  'a resync with nothing focused answers nothing',
  () => page.evaluate(() => window.__imeIn({ data: JSON.stringify({ type: 'ime.resync' }) })),
  (m) => (m.length ? 'answered a resync with no focused field' : null),
)

await step(
  'a readonly field announces itself as readonly',
  () => page.click('#ro'),
  (m) => {
    const focus = find(m, 'ime.focus')
    if (!focus) return 'no ime.focus'
    return focus.readOnly === true ? null : 'ime.focus did not report readOnly'
  },
)

await step(
  'readonly survives the resync round trip',
  () => page.evaluate(() => window.__imeIn({ data: JSON.stringify({ type: 'ime.resync' }) })),
  (m) => {
    const re = find(m, 'ime.refocus')
    if (!re) return 'no ime.refocus'
    return re.readOnly === true ? null : 'ime.refocus dropped readOnly — a restore would raise a keyboard'
  },
)

// contenteditable taps land on a child node, so the doorbell has to test containment, not equality.
await step(
  'a tap inside an already-focused contenteditable rings the doorbell',
  async () => {
    await page.click('#cespan')
    await page.waitForTimeout(150)
    await drain()
    await page.click('#cespan')
  },
  (m) => (has(m, 'ime.wantkb') ? null : 'no ime.wantkb from inside a contenteditable'),
)

console.log(`\nshim: ${SHIM}\n`)
for (const [name, types, problem] of results) {
  console.log(`  ${problem ? 'FAIL' : 'ok  '}  ${name}\n          ${types}`)
}
if (failures.length) {
  console.log(`\n${failures.length} failure(s):\n`)
  failures.forEach((f) => console.log(`  - ${f}`))
}
await browser.close()
process.exit(failures.length ? 1 : 0)
