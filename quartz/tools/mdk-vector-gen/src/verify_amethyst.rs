// Reverse-interop verifier: Amethyst → MDK/OpenMLS.
//
// Takes three paths on argv:
//   arg1 — path to the binary storage snapshot written by emit_joiner_kp.rs
//          (restores Bob's openmls provider exactly as it was when the
//          KeyPackage + bundle were generated);
//   arg2 — the joiner-handoff JSON (we only read the signature_pub here,
//          to look up Bob's own leaf after joining);
//   arg3 — the Amethyst-authored fixture (Welcome + PrivateMessages).
//
// Prints PASS/... lines on success and exits non-zero on any failure.

use std::env;
use std::fs::{self, File};
use std::process;

use base64::Engine;
use openmls::prelude::*;
use openmls_rust_crypto::OpenMlsRustCrypto;
use openmls_traits::OpenMlsProvider;
use serde::Deserialize;
use std::collections::HashMap;
use tls_codec::Deserialize as TlsDeserialize;

#[derive(Deserialize)]
struct Handoff {
    cipher_suite: u16,
    joiner: HandoffJoiner,
}

#[derive(Deserialize)]
struct HandoffJoiner {
    signature_pub: String,
}

#[derive(Deserialize)]
struct Fixture {
    cipher_suite: u16,
    welcome: String,
    app_messages_alice_to_bob: Vec<AppMessage>,
}

#[derive(Deserialize)]
struct AppMessage {
    plaintext: String,
    private_message: String,
}

fn hex_bytes(s: &str) -> Vec<u8> {
    hex::decode(s).expect("bad hex in fixture")
}

fn fail(msg: &str) -> ! {
    eprintln!("FAIL: {msg}");
    process::exit(1);
}

fn main() {
    env_logger::init();
    let args: Vec<String> = env::args().collect();
    if args.len() != 4 {
        eprintln!(
            "usage: verify-amethyst <storage-snapshot.bin> \
                       <joiner-handoff.json> <amethyst-fixture.json>"
        );
        process::exit(2);
    }

    let handoff: Handoff = serde_json::from_str(&fs::read_to_string(&args[2]).unwrap()).unwrap();
    let fixture: Fixture = serde_json::from_str(&fs::read_to_string(&args[3]).unwrap()).unwrap();
    assert_eq!(handoff.cipher_suite, 1);
    assert_eq!(fixture.cipher_suite, 1);

    // Restore Bob's MemoryStorage key/value map from the snapshot. We
    // replicate the on-disk format here (it's a simple base64-encoded
    // HashMap) instead of going through MemoryStorage::load_from_file —
    // that API wants `&mut self` on a field we can't move into the
    // OpenMlsRustCrypto provider (its fields are private and there's no
    // constructor that accepts a pre-populated storage).
    #[derive(Deserialize)]
    struct SerializableKeyStore {
        values: HashMap<String, String>,
    }
    let snap: SerializableKeyStore =
        serde_json::from_reader(File::open(&args[1]).expect("open snapshot for read"))
            .expect("parse snapshot JSON");
    let provider = OpenMlsRustCrypto::default();
    {
        let mut map = provider.storage().values.write().unwrap();
        for (k, v) in snap.values {
            map.insert(
                base64::prelude::BASE64_STANDARD.decode(k).unwrap(),
                base64::prelude::BASE64_STANDARD.decode(v).unwrap(),
            );
        }
    }

    // Parse Amethyst's Welcome.
    let welcome_bytes = hex_bytes(&fixture.welcome);
    let msg_in = MlsMessageIn::tls_deserialize(&mut welcome_bytes.as_slice())
        .unwrap_or_else(|e| fail(&format!("welcome decode: {e:?}")));
    let welcome = match msg_in.extract() {
        MlsMessageBodyIn::Welcome(w) => w,
        other => fail(&format!("expected Welcome, got {other:?}")),
    };

    let cfg = MlsGroupJoinConfig::builder().build();
    let staged = StagedWelcome::new_from_welcome(&provider, &cfg, welcome, None)
        .unwrap_or_else(|e| fail(&format!("StagedWelcome::new_from_welcome: {e:?}")));
    let mut group = staged
        .into_group(&provider)
        .unwrap_or_else(|e| fail(&format!("StagedWelcome::into_group: {e:?}")));
    println!(
        "PASS: joined Amethyst-authored Welcome at epoch {}",
        group.epoch().as_u64()
    );

    // Sanity-check: the signature_pub we advertised must appear in the
    // ratchet tree as OUR leaf.
    let expected_sig_pub = hex_bytes(&handoff.joiner.signature_pub);
    let own_leaf = group.own_leaf_node().unwrap();
    if own_leaf.signature_key().as_slice() != expected_sig_pub.as_slice() {
        fail("own leaf signature key does not match handoff signature_pub");
    }

    for (idx, app) in fixture.app_messages_alice_to_bob.iter().enumerate() {
        let bytes = hex_bytes(&app.private_message);
        let msg = MlsMessageIn::tls_deserialize(&mut bytes.as_slice())
            .unwrap_or_else(|e| fail(&format!("app msg {idx} decode: {e:?}")));
        let protocol = msg
            .try_into_protocol_message()
            .unwrap_or_else(|e| fail(&format!("app msg {idx} not a protocol message: {e:?}")));
        let processed = group
            .process_message(&provider, protocol)
            .unwrap_or_else(|e| fail(&format!("app msg {idx} process_message: {e:?}")));
        match processed.into_content() {
            ProcessedMessageContent::ApplicationMessage(app_msg) => {
                let got = app_msg.into_bytes();
                let want = hex_bytes(&app.plaintext);
                if got != want {
                    fail(&format!(
                        "app msg {idx} plaintext mismatch\n  want: {}\n  got : {}",
                        hex::encode(&want),
                        hex::encode(&got),
                    ));
                }
                println!("PASS: decrypted Amethyst app message {idx}");
            }
            other => fail(&format!("app msg {idx} unexpected content: {other:?}")),
        }
    }
    println!("ALL PASS — openmls read every Amethyst-authored artifact.");
}
