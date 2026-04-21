// Generate marmot-ts / ts-mls interop vectors for the Amethyst Marmot module.
//
// ts-mls is the TypeScript MLS implementation that powers marmot-ts (the
// Marmot protocol client that runs in the browser and on Node/Bun/Deno).
// This generator uses ts-mls directly at the MLS layer — the Nostr wrapping
// marmot-ts adds is orthogonal to the cipher, so an MLS-level interop match
// against ts-mls is the same interop property we'd get from running the
// marmot-ts client end-to-end.
//
// Output: a JSON document on stdout with the same shape as mdk-welcome.json,
// so the Amethyst MdkWelcomeInteropTest test harness can consume either.

import {
  ciphersuites,
  createApplicationMessage,
  createCommit,
  createGroup,
  decode,
  defaultCryptoProvider,
  defaultLifetime,
  encode,
  generateKeyPackage,
  getCiphersuiteImpl,
  joinGroup,
  keyPackageEncoder,
  mlsExporter,
  mlsMessageDecoder,
  mlsMessageEncoder,
  protocolVersions,
  unsafeTestingAuthenticationService,
  wireformats,
} from "ts-mls";

const hex = (bytes) =>
  Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");

async function main() {
  const cs = await getCiphersuiteImpl(
    "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519",
    defaultCryptoProvider,
  );

  const ctx = {
    cipherSuite: cs,
    authService: unsafeTestingAuthenticationService,
  };

  // Alice + Bob KeyPackages
  const aliceKp = await generateKeyPackage({
    credential: { credentialType: 1 /* basic */, identity: new TextEncoder().encode("alice") },
    lifetime: defaultLifetime(),
    cipherSuite: cs,
  });
  const bobKp = await generateKeyPackage({
    credential: { credentialType: 1 /* basic */, identity: new TextEncoder().encode("bob") },
    lifetime: defaultLifetime(),
    cipherSuite: cs,
  });

  // Alice creates the group
  const groupId = crypto.getRandomValues(new Uint8Array(32));
  const aliceState0 = await createGroup({
    context: ctx,
    groupId,
    keyPackage: aliceKp.publicPackage,
    privateKeyPackage: aliceKp.privatePackage,
    extensions: [],
  });

  // Alice adds Bob via a commit containing an Add proposal
  const { newState: aliceState1, welcome, commit } = await createCommit({
    context: ctx,
    state: aliceState0,
    extraProposals: [{ proposalType: 1 /* add */, add: { keyPackage: bobKp.publicPackage } }],
    ratchetTreeExtension: true,
  });
  if (!welcome) throw new Error("createCommit did not produce a Welcome");

  const welcomeBytes = encode(mlsMessageEncoder, welcome);

  // Bob joins from the Welcome
  const welcomeRoundTrip = decode(mlsMessageDecoder, welcomeBytes);
  if (!welcomeRoundTrip || welcomeRoundTrip.wireformat !== wireformats.mls_welcome) {
    throw new Error("round-trip welcome not of wire_format mls_welcome");
  }
  const bobState1 = await joinGroup({
    context: ctx,
    welcome: welcomeRoundTrip.welcome,
    keyPackage: bobKp.publicPackage,
    privateKeys: bobKp.privatePackage,
  });

  // MLS-Exporter KAT: the "marmot" / "group-event" exporter used to seal
  // kind:445 outer envelopes.
  const exporterLabel = "marmot";
  const exporterContext = new TextEncoder().encode("group-event");
  const exporterLength = 32;
  const exporterSecret = await mlsExporter(
    bobState1.keySchedule.exporterSecret,
    exporterLabel,
    exporterContext,
    exporterLength,
    cs,
  );

  // Alice sends three application messages that Bob's side should decrypt.
  // We ratchet aliceState forward between sends.
  let aliceCursor = aliceState1;
  const plaintexts = [
    "Hello from ts-mls",
    "Second message in the same epoch.",
    "Unicode works too: ☕ ❤",
  ];
  const appMessages = [];
  for (const pt of plaintexts) {
    const ptBytes = new TextEncoder().encode(pt);
    const { newState, message } = await createApplicationMessage({
      context: ctx,
      state: aliceCursor,
      message: ptBytes,
    });
    aliceCursor = newState;
    const msgBytes = encode(mlsMessageEncoder, message);
    appMessages.push({
      plaintext: hex(ptBytes),
      private_message: hex(msgBytes),
    });
  }

  // Bob's exported signature public key (we stored Ed25519 seed ourselves)
  // ts-mls generateKeyPackage keeps the full signing-key pair in the
  // PrivateKeyPackage.signaturePrivateKey field as seed || pub.
  const bobSigPriv = bobKp.privatePackage.signaturePrivateKey;
  const bobSigPub = bobKp.publicPackage.leafNode.signaturePublicKey;

  // Wrap Bob's KeyPackage in an MlsMessage (matches the shape Amethyst decodes)
  const bobKpWire = {
    version: protocolVersions.mls10,
    wireformat: wireformats.mls_key_package,
    keyPackage: bobKp.publicPackage,
  };
  const bobKpMsgBytes = encode(mlsMessageEncoder, bobKpWire);
  const bobKpRawBytes = encode(keyPackageEncoder, bobKp.publicPackage);

  const aliceSigPub = aliceKp.publicPackage.leafNode.signaturePublicKey;

  // ts-mls returns Ed25519 signature private keys as PKCS#8-DER envelopes
  // (~48 bytes: a fixed 16-byte ASN.1 header then the 32-byte seed at the
  // tail). openmls returns the raw 32-byte seed, and Amethyst expects
  // seed || pub. Normalise to the raw 32-byte seed here; the Kotlin test
  // appends signature_pub back on to rebuild the 64-byte form.
  const sigSeed =
    bobSigPriv.length === 32
      ? bobSigPriv
      : bobSigPriv.length === 64
        ? bobSigPriv.slice(0, 32)
        : bobSigPriv.slice(bobSigPriv.length - 32); // PKCS#8: seed is at the end

  const vector = {
    cipher_suite: 1,
    description:
      "Alice creates a group and welcomes Bob via ts-mls 2.0.0-rc.10 (marmot-ts's MLS backend).",
    joiner: {
      init_priv: hex(bobKp.privatePackage.initPrivateKey),
      encryption_priv: hex(bobKp.privatePackage.hpkePrivateKey),
      signature_priv: hex(sigSeed),
      signature_pub: hex(bobSigPub),
      key_package: hex(bobKpMsgBytes),
      key_package_raw: hex(bobKpRawBytes),
    },
    committer: {
      signer_pub: hex(aliceSigPub),
    },
    welcome: hex(welcomeBytes),
    exporter: {
      label: exporterLabel,
      context: hex(exporterContext),
      length: exporterLength,
      secret: hex(exporterSecret),
    },
    app_messages_alice_to_bob: appMessages,
  };

  process.stdout.write(JSON.stringify(vector, null, 2) + "\n");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
