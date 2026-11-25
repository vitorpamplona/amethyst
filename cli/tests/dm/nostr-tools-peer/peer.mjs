// A second NIP-17 client for amy's DM harness, built on nostr-tools' nip17/nip59 (Unlicense).
//
//   node peer.mjs pubkey   --sk HEX
//   node peer.mjs inbox    --sk HEX --relay URL                 publish kind:10050 (+ NIP-65) naming URL
//   node peer.mjs send     --sk HEX --relay URL --to PK[,PK…] --text T [--subject S] [--reply-to ID]
//   node peer.mjs room     --sk HEX --relay URL --to PK[,PK…] --text T     one rumor for the whole room
//   node peer.mjs react    --sk HEX --relay URL --to PK[,PK…] --target ID --target-author PK --emoji E
//   node peer.mjs file     --sk HEX --relay URL --to PK[,PK…] --url U --key HEX --nonce HEX --type MIME --x HASH
//   node peer.mjs read     --sk HEX --relay URL [--wait SECS]    every rumor our wraps hold, one JSON per line
//
// send uses nostr-tools' nip17.wrapManyEvents as is (the recipients and a copy to ourselves). Note that
// it writes a separate rumor per recipient, p-tagging only that recipient (and the self copy only us),
// so it never makes a NIP-17 room of more than two; room builds the one rumor NIP-17 asks for (every
// recipient in p) and wraps it to each with nip59.wrapEvent. nostr-tools
// has no NIP-17 helper for kind:7 or kind:15, so react and file build the rumor by NIP-25 / NIP-17
// "File Message" and wrap it with nip59.wrapEvent, once per recipient and once to ourselves.
// Every wrap goes to --relay (the harness's one loopback relay). Output: one JSON object per line.
import { WebSocket } from 'ws'
import { finalizeEvent, getEventHash, getPublicKey } from 'nostr-tools/pure'
import { Relay, useWebSocketImplementation } from 'nostr-tools/relay'
import * as nip17 from 'nostr-tools/nip17'
import * as nip59 from 'nostr-tools/nip59'
const hexToBytes = (hex) => Uint8Array.from(Buffer.from(hex, 'hex'))

useWebSocketImplementation(WebSocket)

const [cmd, ...rest] = process.argv.slice(2)
const opt = {}
for (let i = 0; i < rest.length; i += 2) opt[rest[i].replace(/^--/, '')] = rest[i + 1]
const sk = hexToBytes(opt.sk)
const me = getPublicKey(sk)
const to = (opt.to || '').split(',').filter(Boolean)

async function connect() {
  const relay = await Relay.connect(opt.relay)
  relay.onauth = async (tmpl) => finalizeEvent(tmpl, sk)
  return relay
}

async function publishAll(events) {
  const relay = await connect()
  const results = []
  for (const e of events) {
    try {
      results.push({ id: e.id, ok: true, message: await relay.publish(e) })
    } catch (err) {
      results.push({ id: e.id, ok: false, message: String(err && err.message ? err.message : err) })
    }
  }
  relay.close()
  return results
}

function wrapToAll(rumorTemplate) {
  return [...to, me].map((pk) => nip59.wrapEvent(rumorTemplate, sk, pk))
}

function rumorOf(wraps) {
  // Our own copy (the last wrap) tells the rumor id the recipients see.
  return nip59.unwrapEvent(wraps[wraps.length - 1], sk)
}

const out = (o) => console.log(JSON.stringify(o))

switch (cmd) {
  case 'pubkey':
    out({ pubkey: me })
    break
  case 'inbox': {
    const now = Math.floor(Date.now() / 1000)
    const events = [
      finalizeEvent({ kind: 10050, created_at: now, tags: [['relay', opt.relay]], content: '' }, sk),
      finalizeEvent({ kind: 10002, created_at: now, tags: [['r', opt.relay]], content: '' }, sk),
    ]
    out({ pubkey: me, published: await publishAll(events) })
    break
  }
  case 'send': {
    const replyTo = opt['reply-to'] ? { eventId: opt['reply-to'] } : undefined
    const createdAt = Math.floor(Date.now() / 1000)
    const wraps = nip17.wrapManyEvents(sk, to.map((publicKey) => ({ publicKey })), opt.text, opt.subject, replyTo, createdAt)
    // wrapManyEvents writes one rumor per copy; which one each recipient gets is the same rumor with
    // only its own p tag (our copy, wraps[0], p-tags us). Recompute each id, and check ours unwraps to it.
    const rumorFor = (pk) => {
      const tags = [['p', pk]]
      if (replyTo) tags.push(['e', replyTo.eventId, '', 'reply'])
      if (opt.subject) tags.push(['subject', opt.subject])
      const r = { kind: 14, created_at: createdAt, tags, content: opt.text, pubkey: me }
      return { ...r, id: getEventHash(r) }
    }
    const self = nip59.unwrapEvent(wraps[0], sk)
    if (self.id !== rumorFor(me).id) throw new Error('nostr-tools changed its NIP-17 rumor; update peer.mjs')
    const rumors = Object.fromEntries(to.map((pk) => [pk, rumorFor(pk)]))
    out({ rumor: rumors[to[0]], rumors, published: await publishAll(wraps) })
    break
  }
  case 'room': {
    const wraps = wrapToAll({ kind: 14, content: opt.text, tags: to.map((pk) => ['p', pk]) })
    out({ rumor: rumorOf(wraps), published: await publishAll(wraps) })
    break
  }
  case 'react': {
    const wraps = wrapToAll({
      kind: 7,
      content: opt.emoji,
      tags: [['e', opt.target], ['p', opt['target-author']], ['k', '14']],
    })
    out({ rumor: rumorOf(wraps), published: await publishAll(wraps) })
    break
  }
  case 'file': {
    const wraps = wrapToAll({
      kind: 15,
      content: opt.url,
      tags: [
        ...to.map((pk) => ['p', pk]),
        ['file-type', opt.type],
        ['encryption-algorithm', 'aes-gcm'],
        ['decryption-key', opt.key],
        ['decryption-nonce', opt.nonce],
        ['x', opt.x],
      ],
    })
    out({ rumor: rumorOf(wraps), published: await publishAll(wraps) })
    break
  }
  case 'read': {
    const relay = await connect()
    const wraps = []
    await new Promise((resolve) => {
      const sub = relay.subscribe([{ kinds: [1059], '#p': [me] }], {
        onevent: (e) => wraps.push(e),
        oneose: () => setTimeout(() => { sub.close(); resolve() }, 200),
      })
      setTimeout(resolve, Number(opt.wait || 8) * 1000)
    })
    relay.close()
    const seen = new Set()
    for (const w of wraps) {
      let rumor
      try {
        rumor = nip59.unwrapEvent(w, sk)
      } catch (err) {
        out({ wrap: w.id, error: String(err.message || err) })
        continue
      }
      if (seen.has(rumor.id)) continue
      seen.add(rumor.id)
      out({ wrap: w.id, rumor })
    }
    break
  }
  default:
    console.error('unknown command: ' + cmd)
    process.exit(2)
}
