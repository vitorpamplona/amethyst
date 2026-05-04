// Produce an MDK/OpenMLS-authored joiner KeyPackage for the reverse-interop
// test (Amethyst → MDK). Emits TWO artifacts:
//   - a binary storage snapshot at $1 (openmls_memory_storage save_to_file
//     format) so verify_amethyst.rs can restore Bob's provider exactly as
//     it was when the KP was generated;
//   - JSON on stdout with the public KP bytes plus the private keys
//     (hex-encoded) so Amethyst can drive its addMember and the reader
//     can sanity-check shapes:
//
//   {
//     "cipher_suite": 1,
//     "joiner": {
//       "key_package_raw": hex,
//       "init_priv":       hex(32 B),
//       "encryption_priv": hex(32 B),
//       "signature_priv":  hex(32 B, raw Ed25519 seed),
//       "signature_pub":   hex(32 B)
//     }
//   }

use std::env;
use std::fs::File;
use std::process;

use openmls::prelude::*;
use openmls_basic_credential::SignatureKeyPair;
use openmls_rust_crypto::OpenMlsRustCrypto;
use openmls_traits::OpenMlsProvider;
use tls_codec::Serialize;

const CS: Ciphersuite = Ciphersuite::MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519;

fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() != 2 {
        eprintln!("usage: emit-joiner-kp <storage-snapshot-path>");
        process::exit(2);
    }
    let snapshot_path = &args[1];

    let provider = OpenMlsRustCrypto::default();
    let cred = BasicCredential::new(b"bob".to_vec());
    let sig = SignatureKeyPair::new(CS.signature_algorithm()).unwrap();
    sig.store(provider.storage()).unwrap();
    let cwk = CredentialWithKey {
        credential: cred.into(),
        signature_key: sig.public().into(),
    };

    // Advertise the Marmot extensions a real MDK KeyPackage would carry,
    // so Amethyst's required_capabilities (which lists marmot_group_data
    // 0xF2EE and the self_remove proposal 0x000A) is satisfied when our
    // KP is added to an Amethyst group.
    let capabilities = Capabilities::new(
        None,
        Some(&[CS]),
        Some(&[
            ExtensionType::from(0xF2EE), // marmot_group_data
            ExtensionType::ApplicationId,
            ExtensionType::LastResort,
        ]),
        Some(&[openmls::prelude::ProposalType::SelfRemove]),
        None,
    );
    let bundle = KeyPackage::builder()
        .leaf_node_capabilities(capabilities)
        .mark_as_last_resort()
        .build(CS, &provider, &sig, cwk)
        .unwrap();
    let kp = bundle.key_package().clone();
    let kp_bytes = kp.tls_serialize_detached().unwrap();

    let init_priv: Vec<u8> = (**bundle.init_private_key()).to_vec();
    let enc_priv: Vec<u8> = (**bundle.encryption_private_key()).to_vec();

    // Persist the entire provider storage — validate_amethyst restores it
    // byte-for-byte so the KeyPackage + bundle + signature key can be used
    // to process a Welcome.
    let file = File::create(snapshot_path).expect("open snapshot file");
    provider
        .storage()
        .save_to_file(&file)
        .expect("save storage snapshot");

    let out = serde_json::json!({
        "cipher_suite": 1,
        "joiner": {
            "key_package_raw": hex::encode(&kp_bytes),
            "init_priv": hex::encode(&init_priv),
            "encryption_priv": hex::encode(&enc_priv),
            "signature_priv": hex::encode(sig.private()),
            "signature_pub":  hex::encode(sig.public()),
        }
    });
    println!("{}", serde_json::to_string_pretty(&out).unwrap());
}
