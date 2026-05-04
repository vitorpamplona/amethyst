// Reverse-interop verifier: Amethyst → ts-mls.
//
// Reads two JSON files:
//   $1 — the joiner-KP handoff produced by emit-joiner-kp.mjs
//        (contains Bob's private keys in ts-mls-compatible form).
//   $2 — the Amethyst-authored fixture (produced by the Kotlin
//        AmethystAuthoredVectorGen test) containing Alice's Welcome
//        and three PrivateMessages she sent to Bob.
//
// Succeeds (exit 0) if Bob can:
//   1. decode Amethyst's KeyPackage wrapper (basic wire-format sanity),
//   2. join Alice's group from Amethyst's Welcome,
//   3. decrypt each of Alice's application messages and match the
//      expected plaintext.

import { readFileSync } from "node:fs";
import {
  decode,
  defaultCryptoProvider,
  getCiphersuiteImpl,
  joinGroup,
  keyPackageDecoder,
  mlsMessageDecoder,
  processPrivateMessage,
  unsafeTestingAuthenticationService,
  wireformats,
} from "ts-mls";

function assert(cond, msg) {
  if (!cond) {
    console.error("FAIL:", msg);
    process.exit(1);
  }
}

const hexToBytes = (s) =>
  Uint8Array.from(s.match(/../g).map((b) => parseInt(b, 16)));

const [handoffPath, fixturePath] = process.argv.slice(2);
if (!handoffPath || !fixturePath) {
  console.error(
    "usage: verify-amethyst-fixture.mjs <joiner-handoff.json> <amethyst-fixture.json>",
  );
  process.exit(2);
}
const handoff = JSON.parse(readFileSync(handoffPath, "utf8"));
const fixture = JSON.parse(readFileSync(fixturePath, "utf8"));

const cs = await getCiphersuiteImpl(
  "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519",
  defaultCryptoProvider,
);
const ctx = {
  cipherSuite: cs,
  authService: unsafeTestingAuthenticationService,
};

// Rebuild Bob's public+private KP from the handoff.
const kpBytes = hexToBytes(handoff.joiner.key_package_raw);
const kpPub = decode(keyPackageDecoder, kpBytes);
assert(kpPub, "failed to decode joiner key_package_raw");

// ts-mls signature_priv is a PKCS#8 Ed25519 DER envelope. Reconstruct
// it from the raw 32-byte seed emitted by the handoff.
const PKCS8_ED25519_PREFIX = hexToBytes("302e020100300506032b657004220420");
const seed = hexToBytes(handoff.joiner.signature_priv);
const sigPrivDer = new Uint8Array(PKCS8_ED25519_PREFIX.length + seed.length);
sigPrivDer.set(PKCS8_ED25519_PREFIX, 0);
sigPrivDer.set(seed, PKCS8_ED25519_PREFIX.length);

const privateKeys = {
  initPrivateKey: hexToBytes(handoff.joiner.init_priv),
  hpkePrivateKey: hexToBytes(handoff.joiner.encryption_priv),
  signaturePrivateKey: sigPrivDer,
};

// Decode Amethyst's Welcome (wrapped in MlsMessage).
const welcomeBytes = hexToBytes(fixture.welcome);
const welcomeMsg = decode(mlsMessageDecoder, welcomeBytes);
assert(welcomeMsg, "failed to decode Amethyst Welcome bytes");
assert(
  welcomeMsg.wireformat === wireformats.mls_welcome,
  "Amethyst Welcome wire-format mismatch",
);

// Join.
let state;
try {
  state = await joinGroup({
    context: ctx,
    welcome: welcomeMsg.welcome,
    keyPackage: kpPub,
    privateKeys,
  });
} catch (e) {
  console.error("FAIL: joinGroup threw:", e?.message ?? e);
  process.exit(1);
}
console.log("PASS: joined Amethyst-authored Welcome at epoch", state.groupContext.epoch);

// Dump the decoded GroupInfo for diagnostic use by other verifiers.
// ts-mls already unwrapped it during joinGroup, so we just print a
// compact summary + the full extensions array.
{
  const gc = state.groupContext;
  const ext = state.publicGroupState?.groupInfoExtensions;
  // (state layout varies; these fields may or may not exist. Best-effort.)
  if (process.env.DUMP_GROUP_INFO) {
    console.log(JSON.stringify({ groupContext: gc, extensions: ext }, (_k, v) =>
      v instanceof Uint8Array ? Array.from(v).map(b => b.toString(16).padStart(2, "0")).join("") : v,
      2));
  }
}

// Decrypt each application message.
for (const [idx, msg] of fixture.app_messages_alice_to_bob.entries()) {
  const msgBytes = hexToBytes(msg.private_message);
  const framedMsg = decode(mlsMessageDecoder, msgBytes);
  assert(framedMsg, `failed to decode application message ${idx}`);
  assert(
    framedMsg.wireformat === wireformats.mls_private_message,
    `application message ${idx} wire-format mismatch`,
  );

  let res;
  try {
    res = await processPrivateMessage({
      context: ctx,
      state,
      privateMessage: framedMsg.privateMessage,
    });
  } catch (e) {
    console.error(`FAIL: processPrivateMessage(${idx}) threw:`, e?.message ?? e);
    process.exit(1);
  }
  state = res.newState;
  if (res.kind !== "applicationMessage") {
    console.error(`FAIL: processPrivateMessage(${idx}) returned kind=${res.kind}, expected applicationMessage`);
    process.exit(1);
  }
  const gotHex = Array.from(res.message ?? new Uint8Array(), (b) =>
    b.toString(16).padStart(2, "0"),
  ).join("");
  if (gotHex !== msg.plaintext) {
    console.error(
      `FAIL: app message ${idx} plaintext mismatch\n  want: ${msg.plaintext}\n  got : ${gotHex}`,
    );
    process.exit(1);
  }
  console.log(`PASS: decrypted Amethyst app message ${idx}`);
}

console.log("ALL PASS — ts-mls read every Amethyst-authored artifact.");
