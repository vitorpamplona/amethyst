// Produce a ts-mls-authored joiner KeyPackage for the reverse-interop test
// (Amethyst → ts-mls). Emits on stdout:
//
//   {
//     "cipher_suite": 1,
//     "joiner": {
//       "key_package_raw": hex,                 // inner KeyPackage bytes
//       "init_priv":      hex(32 B),
//       "encryption_priv":hex(32 B),
//       "signature_priv": hex(32 B, raw seed),  // PKCS#8 header stripped
//       "signature_pub":  hex(32 B)
//     }
//   }
//
// Amethyst reads `key_package_raw` to drive its MlsGroup.addMember flow;
// verify-amethyst-fixture.mjs reads the private keys back to drive
// ts-mls joinGroup + processPrivateMessage against Amethyst's Welcome.

import {
  defaultCapabilities,
  defaultCryptoProvider,
  defaultLifetime,
  generateKeyPackage,
  getCiphersuiteImpl,
  keyPackageEncoder,
  encode,
} from "ts-mls";

const hex = (bytes) =>
  Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");

const cs = await getCiphersuiteImpl(
  "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519",
  defaultCryptoProvider,
);

// Mirror MDK: advertise marmot_group_data (0xF2EE) as a supported
// extension and self_remove (0x000A) as a supported proposal so
// Amethyst's required_capabilities is satisfied when our KP is added.
const caps = defaultCapabilities();
if (!caps.extensions.includes(0xf2ee)) caps.extensions.push(0xf2ee);
if (!caps.proposals.includes(0x000a)) caps.proposals.push(0x000a);

const kp = await generateKeyPackage({
  credential: { credentialType: 1, identity: new TextEncoder().encode("bob") },
  capabilities: caps,
  lifetime: defaultLifetime(),
  cipherSuite: cs,
});

const sigPriv = kp.privatePackage.signaturePrivateKey;
const sigSeed =
  sigPriv.length === 32
    ? sigPriv
    : sigPriv.length === 64
      ? sigPriv.slice(0, 32)
      : sigPriv.slice(sigPriv.length - 32);

const out = {
  cipher_suite: 1,
  joiner: {
    key_package_raw: hex(encode(keyPackageEncoder, kp.publicPackage)),
    init_priv: hex(kp.privatePackage.initPrivateKey),
    encryption_priv: hex(kp.privatePackage.hpkePrivateKey),
    signature_priv: hex(sigSeed),
    signature_pub: hex(kp.publicPackage.leafNode.signaturePublicKey),
  },
};

process.stdout.write(JSON.stringify(out, null, 2) + "\n");
