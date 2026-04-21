// Generate MDK/OpenMLS interop test vectors for the Amethyst Marmot module.
//
// Emits a JSON document containing, for cipher suite
// MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 (0x0001):
//   - joiner.init_priv        (32 bytes hex) — HPKE KEM private for Welcome unwrap
//   - joiner.signature_priv   (32 bytes hex seed || 32 bytes pub) — Ed25519 private
//   - joiner.key_package      (hex, MlsMessage-wrapped) — the KeyPackage on the wire
//   - committer.signer_pub    (32 bytes hex) — for GroupInfoTBS verification
//   - welcome                 (hex, MlsMessage-wrapped) — what the sender sent
//   - exporter.{label,context,length,secret} — MLS-Exporter KAT derived post-join
//
// The joiner pretends to be offline: we build their KeyPackage, hand the
// public half to the committer, then keep the three private keys so the
// vector is fully-self-decryptable.

use openmls::prelude::*;
use openmls_basic_credential::SignatureKeyPair;
use openmls_rust_crypto::OpenMlsRustCrypto;
use openmls_traits::OpenMlsProvider;
use tls_codec::{Deserialize, Serialize};

const CS: Ciphersuite = Ciphersuite::MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519;

fn new_signer(identity: &[u8]) -> (SignatureKeyPair, CredentialWithKey) {
    let cred = BasicCredential::new(identity.to_vec());
    let sig = SignatureKeyPair::new(CS.signature_algorithm()).unwrap();
    let cwk = CredentialWithKey {
        credential: cred.into(),
        signature_key: sig.public().into(),
    };
    (sig, cwk)
}

fn main() {
    let provider_a = OpenMlsRustCrypto::default();
    let provider_b = OpenMlsRustCrypto::default();

    let (alice_sig, alice_cwk) = new_signer(b"alice");
    alice_sig.store(provider_a.storage()).unwrap();

    let (bob_sig, bob_cwk) = new_signer(b"bob");
    bob_sig.store(provider_b.storage()).unwrap();

    // Bob builds a KeyPackage with openmls defaults + LastResort (marks it as
    // a long-lived KP per MDK behaviour).
    let bob_kp_bundle = KeyPackage::builder()
        .mark_as_last_resort()
        .build(CS, &provider_b, &bob_sig, bob_cwk.clone())
        .unwrap();
    let bob_kp = bob_kp_bundle.key_package().clone();
    let bob_kp_bytes_raw = bob_kp.tls_serialize_detached().unwrap();
    // Wrap in MlsMessage (the shape Amethyst decodes first)
    let bob_kp_msg: MlsMessageOut = MlsMessageOut::from(bob_kp.clone());
    let bob_kp_msg_bytes = bob_kp_msg.tls_serialize_detached().unwrap();

    // Pull Bob's HPKE init private key out of storage via the KeyPackageBundle.
    let bob_init_priv: Vec<u8> = (**bob_kp_bundle.init_private_key()).to_vec();
    let bob_enc_priv: Vec<u8> = (**bob_kp_bundle.encryption_private_key()).to_vec();

    // Alice creates the group with Bob as an initial member.
    let group_cfg = MlsGroupCreateConfig::builder()
        .ciphersuite(CS)
        .wire_format_policy(openmls::group::MIXED_CIPHERTEXT_WIRE_FORMAT_POLICY)
        .use_ratchet_tree_extension(true)
        .build();

    let mut alice_group = MlsGroup::new(
        &provider_a,
        &alice_sig,
        &group_cfg,
        alice_cwk.clone(),
    )
    .unwrap();

    let (_commit_out, welcome_out, _group_info) = alice_group
        .add_members(&provider_a, &alice_sig, &[bob_kp.clone()])
        .unwrap();
    alice_group.merge_pending_commit(&provider_a).unwrap();

    // Commit is not used in this vector — we only need the Welcome.
    let welcome_bytes = welcome_out.tls_serialize_detached().unwrap();

    // Drive Bob through processing so we can emit an MLS-Exporter KAT the
    // Amethyst test can derive independently.
    let welcome_in = MlsMessageIn::tls_deserialize(&mut welcome_bytes.as_slice()).unwrap();
    let welcome = match welcome_in.extract() {
        MlsMessageBodyIn::Welcome(w) => w,
        other => panic!("expected Welcome, got {other:?}"),
    };
    let cfg = MlsGroupJoinConfig::builder().build();
    let staged = StagedWelcome::new_from_welcome(&provider_b, &cfg, welcome, None).unwrap();
    let bob_group = staged.into_group(&provider_b).unwrap();

    let exporter_label = "marmot";
    let exporter_context = b"group-event";
    let exporter_length: usize = 32;
    let exporter_secret = bob_group
        .export_secret(provider_b.crypto(), exporter_label, exporter_context, exporter_length)
        .unwrap();

    // Alice sends three application messages to the group. Bob will replay
    // them through Amethyst's `MlsGroup.decrypt` and verify the plaintexts.
    let plaintexts: Vec<&[u8]> = vec![
        b"Hello Bob!".as_ref(),
        b"Second message in the same epoch.".as_ref(),
        b"Unicode works too: \xe2\x98\x95 \xe2\x9d\xa4".as_ref(),
    ];
    let mut app_messages = Vec::new();
    for pt in &plaintexts {
        let out = alice_group
            .create_message(&provider_a, &alice_sig, pt)
            .unwrap();
        let bytes = out.tls_serialize_detached().unwrap();
        app_messages.push(serde_json::json!({
            "plaintext": hex::encode(pt),
            "private_message": hex::encode(&bytes),
        }));
    }

    let vector = serde_json::json!({
        "cipher_suite": 1,
        "description": "Alice creates a group and welcomes Bob via openmls 0.8 (MDK's MLS backend).",
        "joiner": {
            "init_priv": hex::encode(&bob_init_priv),
            "encryption_priv": hex::encode(&bob_enc_priv),
            "signature_priv": hex::encode(bob_sig.private()),
            "signature_pub": hex::encode(bob_sig.public()),
            "key_package": hex::encode(&bob_kp_msg_bytes),
            "key_package_raw": hex::encode(&bob_kp_bytes_raw),
        },
        "committer": {
            "signer_pub": hex::encode(alice_sig.public()),
        },
        "welcome": hex::encode(&welcome_bytes),
        "exporter": {
            "label": exporter_label,
            "context": hex::encode(exporter_context),
            "length": exporter_length,
            "secret": hex::encode(&exporter_secret),
        },
        "app_messages_alice_to_bob": app_messages,
    });
    println!("{}", serde_json::to_string_pretty(&vector).unwrap());
}
