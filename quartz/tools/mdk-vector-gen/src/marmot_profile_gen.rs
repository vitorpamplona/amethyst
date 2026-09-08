// Generate a *current-profile Marmot* interop vector for the Amethyst Marmot module.
//
// `main.rs` proves our MLS core against stock OpenMLS: Welcome unwrap, key
// schedule, exporter. This binary proves the layer above it — the Marmot
// profile the adopted spec actually defines — by building a group the same way
// MDK's `cgka-engine` does:
//
//   * handshake wire format is PublicMessage (`foundation/mls-protocol.md`,
//     "Handshake wire format"); OpenMLS's WireFormatPolicy governs handshakes
//     only, application messages stay PrivateMessage per RFC 9420;
//   * `RequiredCapabilities` = extension `0x0006` app_data_dictionary +
//     proposal `0x0008` app_data_update;
//   * GroupContext carries an `app_data_dictionary` with the required-component
//     list (`0x0001`) plus profile / admin-policy / nostr-routing / lifecycle;
//   * every member LeafNode carries its own `app_data_dictionary` with the
//     supported-component list, an empty `safe_aad` list, and the 104-byte
//     `marmot.member.account-identity-proof.v2` component (`0x8009`);
//   * the KeyPackage-level dictionary carries the empty-data
//     `last_resort_key_package` component (`0x0004`) — last resort is NOT an
//     MLS extension type in this profile.
//
// Everything emitted here is the byte shape our Kotlin side has to produce and
// parse. The component payload encoders below are deliberately hand-rolled from
// the spec text rather than pulled from MDK: if the hand-rolled bytes and
// OpenMLS's framing agree with MDK, the spec was read correctly.

use ::tls_codec::{Deserialize, Serialize};
use openmls::extensions::{AppDataDictionary, AppDataDictionaryExtension};
use openmls::prelude::*;
use openmls_basic_credential::SignatureKeyPair;
use openmls_rust_crypto::OpenMlsRustCrypto;
use openmls_traits::OpenMlsProvider;
use secp256k1::{Keypair, Secp256k1, SecretKey, XOnlyPublicKey};
use sha2::{Digest, Sha256};

const CS: Ciphersuite = Ciphersuite::MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519;

// foundation/registries.md — upstream MLS extensions draft ids.
const COMPONENT_APP_COMPONENTS: u16 = 0x0001;
const COMPONENT_SAFE_AAD: u16 = 0x0002;
const COMPONENT_LAST_RESORT: u16 = 0x0004;
// foundation/registries.md — Marmot private-range component ids.
const COMPONENT_GROUP_PROFILE: u16 = 0x8001;
const COMPONENT_ADMIN_POLICY: u16 = 0x8003;
const COMPONENT_NOSTR_ROUTING: u16 = 0x8004;
const COMPONENT_ACCOUNT_IDENTITY_PROOF: u16 = 0x8009;
const COMPONENT_GROUP_LIFECYCLE: u16 = 0x800c;

const PROOF_EVENT_KIND: u16 = 450;
const PROOF_DOMAIN: &str = "marmot.account-identity-proof.v2";
const PROOF_CONTENT: &str = "Authorize this MLS leaf key for my Marmot account";

// ---------------------------------------------------------------- encoders

/// QUIC variable-length integer, `foundation/canonical-encoding.md`.
fn put_varint(value: u64, out: &mut Vec<u8>) {
    if value < 64 {
        out.push(value as u8);
    } else if value < 16_384 {
        out.extend_from_slice(&(0x4000_u16 | value as u16).to_be_bytes());
    } else if value < 1_073_741_824 {
        out.extend_from_slice(&(0x8000_0000_u32 | value as u32).to_be_bytes());
    } else {
        out.extend_from_slice(&(0xC000_0000_0000_0000_u64 | value).to_be_bytes());
    }
}

fn put_var_bytes(bytes: &[u8], out: &mut Vec<u8>) {
    put_varint(bytes.len() as u64, out);
    out.extend_from_slice(bytes);
}

/// `ComponentsList { ComponentID component_ids<V>; }` — ids ascending, no dups.
fn encode_components_list(ids: &[u16]) -> Vec<u8> {
    let mut sorted = ids.to_vec();
    sorted.sort_unstable();
    sorted.dedup();
    let mut out = Vec::new();
    put_varint((sorted.len() * 2) as u64, &mut out);
    for id in sorted {
        out.extend_from_slice(&id.to_be_bytes());
    }
    out
}

/// `marmot.group.profile.v1` — two var-byte UTF-8 fields.
fn encode_group_profile(name: &str, description: &str) -> Vec<u8> {
    let mut out = Vec::new();
    put_var_bytes(name.as_bytes(), &mut out);
    put_var_bytes(description.as_bytes(), &mut out);
    out
}

/// `marmot.group.admin-policy.v1` — one var-byte vector of concatenated
/// 32-byte x-only account keys, sorted and de-duplicated.
fn encode_admin_policy(admins: &[[u8; 32]]) -> Vec<u8> {
    let mut sorted = admins.to_vec();
    sorted.sort_unstable();
    sorted.dedup();
    let mut flat = Vec::with_capacity(sorted.len() * 32);
    for admin in &sorted {
        flat.extend_from_slice(admin);
    }
    let mut out = Vec::new();
    put_var_bytes(&flat, &mut out);
    out
}

/// `marmot.transport.nostr.routing.v1` — raw 32-byte routing id followed by a
/// var-byte vector of var-byte relay URLs, sorted lexicographically.
fn encode_nostr_routing(nostr_group_id: &[u8; 32], relays: &[&str]) -> Vec<u8> {
    let mut sorted = relays.to_vec();
    sorted.sort_unstable();
    sorted.dedup();
    let mut entries = Vec::new();
    for relay in &sorted {
        put_var_bytes(relay.as_bytes(), &mut entries);
    }
    let mut out = Vec::with_capacity(32 + entries.len() + 8);
    out.extend_from_slice(nostr_group_id);
    put_var_bytes(&entries, &mut out);
    out
}

/// `marmot.group.lifecycle.v1` — exactly one byte; 0x00 active, 0x01 disbanded.
fn encode_group_lifecycle_active() -> Vec<u8> {
    vec![0x00]
}

// ------------------------------------------------- account identity proof v2

struct ProofMaterial {
    component: Vec<u8>,
    event_json: String,
    event_id: [u8; 32],
    signature: [u8; 64],
    created_at: u64,
}

/// Build the kind-450 signing template from `app-components/account-identity-proof-v2.md`
/// and return its NIP-01 canonical serialization plus id.
fn proof_event(
    account_pubkey: &XOnlyPublicKey,
    mls_signature_key: &[u8],
    created_at: u64,
) -> (String, [u8; 32]) {
    let tags = serde_json::json!([
        ["d", PROOF_DOMAIN],
        [
            "component",
            format!("0x{COMPONENT_ACCOUNT_IDENTITY_PROOF:04x}")
        ],
        ["ciphersuite", format!("0x{:04x}", u16::from(CS))],
        [
            "signature_scheme",
            format!("0x{:04x}", CS.signature_algorithm() as u16)
        ],
        ["mls_signature_key", hex::encode(mls_signature_key)],
    ]);
    // NIP-01 canonical form: [0, pubkey, created_at, kind, tags, content].
    // serde_json applies the exact escaping rules the id is defined over.
    let canonical = serde_json::json!([
        0,
        hex::encode(account_pubkey.serialize()),
        created_at,
        PROOF_EVENT_KIND,
        tags,
        PROOF_CONTENT,
    ]);
    let serialized = serde_json::to_string(&canonical).unwrap();
    let id: [u8; 32] = Sha256::digest(serialized.as_bytes()).into();
    (serialized, id)
}

fn build_proof(account: &Keypair, mls_signature_key: &[u8], created_at: u64) -> ProofMaterial {
    let secp = Secp256k1::new();
    let (xonly, _parity) = account.x_only_public_key();
    let (event_json, event_id) = proof_event(&xonly, mls_signature_key, created_at);

    // The 32-byte event id is itself the BIP-340 message; Marmot does not
    // re-hash it (`account-identity-proof-v2.md`, "Signing event").
    let signature = secp
        .sign_schnorr_no_aux_rand(&event_id, account)
        .to_byte_array();

    let mut component = Vec::with_capacity(104);
    component.extend_from_slice(&xonly.serialize());
    component.extend_from_slice(&created_at.to_be_bytes());
    component.extend_from_slice(&signature);
    assert_eq!(component.len(), 104, "proof component must be 104 bytes");

    ProofMaterial {
        component,
        event_json,
        event_id,
        signature,
        created_at,
    }
}

/// Self-check the hand-rolled proof construction against the fixed vector
/// published in `app-components/account-identity-proof-v2.md`. If this trips,
/// the generator is emitting proofs no MDK client will accept — and every
/// downstream vector in this file is worthless.
fn assert_spec_proof_vector() {
    let secp = Secp256k1::new();
    let mut sk_bytes = [0_u8; 32];
    sk_bytes[31] = 3;
    let account = Keypair::from_secret_key(&secp, &SecretKey::from_byte_array(sk_bytes).unwrap());
    let mls_signature_key: Vec<u8> = (0_u8..32).collect();
    let (xonly, _) = account.x_only_public_key();
    assert_eq!(
        hex::encode(xonly.serialize()),
        "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9",
        "spec fixture pubkey mismatch"
    );

    let (json, id) = proof_event(&xonly, &mls_signature_key, 1_700_000_000);
    assert_eq!(
        json,
        r#"[0,"f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9",1700000000,450,[["d","marmot.account-identity-proof.v2"],["component","0x8009"],["ciphersuite","0x0001"],["signature_scheme","0x0807"],["mls_signature_key","000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"]],"Authorize this MLS leaf key for my Marmot account"]"#,
        "canonical proof-event serialization does not match the spec fixture"
    );
    assert_eq!(
        hex::encode(id),
        "b7e9a15dd85990fb0f49c33db3cc9875f73986207b038404ceb6b7fec4e0af6b",
        "proof event id does not match the spec fixture"
    );

    let expected_component = "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9\
                              000000006553f100\
                              c5315d3c85b9d4907cb03395a2a97b3ba2eab393f8e45b13a5d5233acedac60a\
                              51d2a295e1b1b5ee372d18a49bdb8041a7dba9dedce722c7c6f712f78bbdfb5d"
        .replace([' ', '\n'], "");
    let proof = build_proof(&account, &mls_signature_key, 1_700_000_000);
    assert_eq!(
        hex::encode(&proof.component),
        expected_component,
        "104-byte proof component does not match the spec fixture"
    );
}

// ------------------------------------------------------------------- member

struct Member {
    account: Keypair,
    account_xonly: [u8; 32],
    signer: SignatureKeyPair,
    credential: CredentialWithKey,
}

fn new_member(secret_byte: u8) -> Member {
    let secp = Secp256k1::new();
    let mut sk_bytes = [0_u8; 32];
    sk_bytes[31] = secret_byte;
    let account = Keypair::from_secret_key(&secp, &SecretKey::from_byte_array(sk_bytes).unwrap());
    let (xonly, _) = account.x_only_public_key();
    let account_xonly = xonly.serialize();

    // foundation/identity.md: the MLS BasicCredential identity is the raw
    // 32-byte x-only account key — not hex text, not an npub.
    let signer = SignatureKeyPair::new(CS.signature_algorithm()).unwrap();
    let credential = CredentialWithKey {
        credential: BasicCredential::new(account_xonly.to_vec()).into(),
        signature_key: signer.public().into(),
    };
    Member {
        account,
        account_xonly,
        signer,
        credential,
    }
}

/// Component ids this generator claims to support, advertised in every leaf.
fn supported_components() -> Vec<u16> {
    vec![
        COMPONENT_APP_COMPONENTS,
        COMPONENT_GROUP_PROFILE,
        COMPONENT_ADMIN_POLICY,
        COMPONENT_NOSTR_ROUTING,
        COMPONENT_ACCOUNT_IDENTITY_PROOF,
        COMPONENT_GROUP_LIFECYCLE,
    ]
}

/// Component ids a current-profile group requires.
fn required_components() -> Vec<u16> {
    vec![
        COMPONENT_GROUP_PROFILE,
        COMPONENT_ADMIN_POLICY,
        COMPONENT_NOSTR_ROUTING,
        COMPONENT_ACCOUNT_IDENTITY_PROOF,
        COMPONENT_GROUP_LIFECYCLE,
    ]
}

fn leaf_capabilities() -> Capabilities {
    // RFC 9420 section 7.2 forbids advertising default extension types, so
    // only the draft app_data_dictionary extension and app_data_update
    // proposal appear here.
    Capabilities::new(
        None,
        Some(&[CS]),
        Some(&[ExtensionType::AppDataDictionary]),
        Some(&[ProposalType::AppDataUpdate]),
        None,
    )
}

fn leaf_extensions(proof_component: &[u8]) -> Extensions<LeafNode> {
    let mut dict = AppDataDictionary::new();
    dict.insert(
        COMPONENT_APP_COMPONENTS,
        encode_components_list(&supported_components()),
    );
    // Advertising safe_aad support with an empty component list: understood,
    // nothing contributed. Required of anyone advertising app_data_dictionary.
    dict.insert(COMPONENT_SAFE_AAD, encode_components_list(&[]));
    dict.insert(COMPONENT_ACCOUNT_IDENTITY_PROOF, proof_component.to_vec());
    Extensions::single(Extension::AppDataDictionary(
        AppDataDictionaryExtension::new(dict),
    ))
    .unwrap()
}

fn group_context_extensions(
    admins: &[[u8; 32]],
    nostr_group_id: &[u8; 32],
    relays: &[&str],
    name: &str,
    description: &str,
) -> Extensions<GroupContext> {
    let mut dict = AppDataDictionary::new();
    dict.insert(
        COMPONENT_APP_COMPONENTS,
        encode_components_list(&required_components()),
    );
    dict.insert(
        COMPONENT_GROUP_PROFILE,
        encode_group_profile(name, description),
    );
    dict.insert(COMPONENT_ADMIN_POLICY, encode_admin_policy(admins));
    dict.insert(
        COMPONENT_NOSTR_ROUTING,
        encode_nostr_routing(nostr_group_id, relays),
    );
    dict.insert(COMPONENT_GROUP_LIFECYCLE, encode_group_lifecycle_active());
    // 0x8009 is required but leaf-only: its data must NOT appear here.

    Extensions::from_vec(vec![
        Extension::RequiredCapabilities(RequiredCapabilitiesExtension::new(
            &[ExtensionType::AppDataDictionary],
            &[ProposalType::AppDataUpdate],
            &[],
        )),
        Extension::AppDataDictionary(AppDataDictionaryExtension::new(dict)),
    ])
    .unwrap()
}

fn dictionary_entries_json(dictionary: Option<&AppDataDictionaryExtension>) -> serde_json::Value {
    let mut entries = serde_json::Map::new();
    if let Some(ext) = dictionary {
        for entry in ext.dictionary().entries() {
            entries.insert(
                format!("0x{:04x}", entry.id()),
                serde_json::Value::String(hex::encode(entry.data())),
            );
        }
    }
    serde_json::Value::Object(entries)
}

fn main() {
    assert_spec_proof_vector();

    let provider_a = OpenMlsRustCrypto::default();
    let provider_b = OpenMlsRustCrypto::default();

    let alice = new_member(0x11);
    alice.signer.store(provider_a.storage()).unwrap();
    let bob = new_member(0x22);
    bob.signer.store(provider_b.storage()).unwrap();

    let created_at = 1_700_000_000_u64;
    let alice_proof = build_proof(&alice.account, alice.signer.public(), created_at);
    let bob_proof = build_proof(&bob.account, bob.signer.public(), created_at);

    // Bob publishes a last-resort KeyPackage in the current profile.
    let bob_kp_bundle = KeyPackage::builder()
        .leaf_node_capabilities(leaf_capabilities())
        .leaf_node_extensions(leaf_extensions(&bob_proof.component))
        .mark_as_last_resort()
        .build(CS, &provider_b, &bob.signer, bob.credential.clone())
        .unwrap();
    let bob_kp = bob_kp_bundle.key_package().clone();
    let bob_kp_msg: MlsMessageOut = MlsMessageOut::from(bob_kp.clone());
    let bob_kp_msg_bytes = bob_kp_msg.tls_serialize_detached().unwrap();
    let bob_init_priv: Vec<u8> = (**bob_kp_bundle.init_private_key()).to_vec();
    let bob_enc_priv: Vec<u8> = (**bob_kp_bundle.encryption_private_key()).to_vec();
    let bob_kp_dict = dictionary_entries_json(bob_kp.extensions().app_data_dictionary());
    let bob_leaf_dict =
        dictionary_entries_json(bob_kp.leaf_node().extensions().app_data_dictionary());

    let nostr_group_id = [0x5a_u8; 32];
    let relays = ["wss://nos.lol", "wss://relay.damus.io"];
    let gc_exts = group_context_extensions(
        &[alice.account_xonly],
        &nostr_group_id,
        &relays,
        "Marmot interop",
        "current-profile fixture",
    );

    let group_cfg = MlsGroupCreateConfig::builder()
        .ciphersuite(CS)
        .capabilities(leaf_capabilities())
        .with_leaf_node_extensions(leaf_extensions(&alice_proof.component))
        .unwrap()
        .wire_format_policy(openmls::group::PURE_PLAINTEXT_WIRE_FORMAT_POLICY)
        .with_group_context_extensions(gc_exts)
        .use_ratchet_tree_extension(true)
        .build();

    let mut alice_group = MlsGroup::new(
        &provider_a,
        &alice.signer,
        &group_cfg,
        alice.credential.clone(),
    )
    .unwrap();

    let epoch0_gc_dict = dictionary_entries_json(alice_group.extensions().app_data_dictionary());
    let alice_leaf_dict = dictionary_entries_json(
        alice_group
            .own_leaf_node()
            .unwrap()
            .extensions()
            .app_data_dictionary(),
    );

    let (commit_out, welcome_out, _group_info) = alice_group
        .add_members(&provider_a, &alice.signer, &[bob_kp.clone()])
        .unwrap();
    alice_group.merge_pending_commit(&provider_a).unwrap();

    // The Add commit is a PublicMessage under the Marmot handshake profile —
    // the exact shape our peeler has to authenticate and replay.
    let add_commit_bytes = commit_out.tls_serialize_detached().unwrap();
    let welcome_bytes = welcome_out.tls_serialize_detached().unwrap();

    let welcome_in = MlsMessageIn::tls_deserialize(&mut welcome_bytes.as_slice()).unwrap();
    let welcome = match welcome_in.extract() {
        MlsMessageBodyIn::Welcome(w) => w,
        other => panic!("expected Welcome, got {other:?}"),
    };
    let join_cfg = MlsGroupJoinConfig::builder().build();
    let staged = StagedWelcome::new_from_welcome(&provider_b, &join_cfg, welcome, None).unwrap();
    let bob_group = staged.into_group(&provider_b).unwrap();

    let group_event_secret = bob_group
        .export_secret(provider_b.crypto(), "marmot", b"group-event", 32)
        .unwrap();
    // foundation/conformance.md — the synthetic state-commitment exporter.
    let conformance_secret = bob_group
        .export_secret(
            provider_b.crypto(),
            "marmot",
            b"convergence-conformance-v1",
            32,
        )
        .unwrap();
    let conformance_commitment: [u8; 32] = {
        let mut hasher = Sha256::new();
        hasher.update(b"marmot-convergence-conformance-v1");
        hasher.update([0x00]);
        hasher.update(&conformance_secret);
        hasher.finalize().into()
    };

    let plaintexts: Vec<&[u8]> = vec![
        b"Hello Bob!".as_ref(),
        b"Second message in the same epoch.".as_ref(),
    ];
    let mut app_messages = Vec::new();
    for pt in &plaintexts {
        let out = alice_group
            .create_message(&provider_a, &alice.signer, pt)
            .unwrap();
        app_messages.push(serde_json::json!({
            "plaintext": hex::encode(pt),
            "private_message": hex::encode(out.tls_serialize_detached().unwrap()),
        }));
    }

    let vector = serde_json::json!({
        "cipher_suite": u16::from(CS),
        "signature_scheme": CS.signature_algorithm() as u16,
        "description":
            "Current-profile Marmot group (app_data_dictionary + account-identity-proof v2), \
             built on the OpenMLS extensions-draft fork MDK pins.",
        "profile": "current",
        "handshake_wire_format": "public_message",
        "required_capabilities": {
            "extensions": ["0x0006"],
            "proposals": ["0x0008"],
        },
        "component_ids": {
            "app_components": format!("0x{COMPONENT_APP_COMPONENTS:04x}"),
            "safe_aad": format!("0x{COMPONENT_SAFE_AAD:04x}"),
            "last_resort_key_package": format!("0x{COMPONENT_LAST_RESORT:04x}"),
            "group_profile_v1": format!("0x{COMPONENT_GROUP_PROFILE:04x}"),
            "admin_policy_v1": format!("0x{COMPONENT_ADMIN_POLICY:04x}"),
            "nostr_routing_v1": format!("0x{COMPONENT_NOSTR_ROUTING:04x}"),
            "account_identity_proof_v2": format!("0x{COMPONENT_ACCOUNT_IDENTITY_PROOF:04x}"),
            "group_lifecycle_v1": format!("0x{COMPONENT_GROUP_LIFECYCLE:04x}"),
        },
        "group_state": {
            "nostr_group_id": hex::encode(nostr_group_id),
            "relays": relays,
            "name": "Marmot interop",
            "description": "current-profile fixture",
            "admins": [hex::encode(alice.account_xonly)],
            "epoch0_group_context_dictionary": epoch0_gc_dict,
            "epoch1_group_context_dictionary":
                dictionary_entries_json(alice_group.extensions().app_data_dictionary()),
        },
        "committer": {
            "account_pubkey": hex::encode(alice.account_xonly),
            // Same key name as the joiner's: both are that member's MLS leaf
            // signature public key, and one concept gets one name.
            "signature_pub": hex::encode(alice.signer.public()),
            "leaf_dictionary": alice_leaf_dict,
            "account_identity_proof": {
                "component": hex::encode(&alice_proof.component),
                "created_at": alice_proof.created_at,
                "event_json": alice_proof.event_json,
                "event_id": hex::encode(alice_proof.event_id),
                "signature": hex::encode(alice_proof.signature),
            },
        },
        "joiner": {
            "account_pubkey": hex::encode(bob.account_xonly),
            "init_priv": hex::encode(&bob_init_priv),
            "encryption_priv": hex::encode(&bob_enc_priv),
            "signature_priv": hex::encode(bob.signer.private()),
            "signature_pub": hex::encode(bob.signer.public()),
            "key_package": hex::encode(&bob_kp_msg_bytes),
            "key_package_dictionary": bob_kp_dict,
            "leaf_dictionary": bob_leaf_dict,
            "account_identity_proof": {
                "component": hex::encode(&bob_proof.component),
                "created_at": bob_proof.created_at,
                "event_json": bob_proof.event_json,
                "event_id": hex::encode(bob_proof.event_id),
                "signature": hex::encode(bob_proof.signature),
            },
        },
        "add_commit_public_message": hex::encode(&add_commit_bytes),
        "welcome": hex::encode(&welcome_bytes),
        "exporters": {
            "group_event": {
                "label": "marmot",
                "context": hex::encode(b"group-event"),
                "length": 32,
                "secret": hex::encode(&group_event_secret),
            },
            "convergence_conformance_v1": {
                "label": "marmot",
                "context": hex::encode(b"convergence-conformance-v1"),
                "length": 32,
                "commitment": hex::encode(conformance_commitment),
            },
        },
        "app_messages_alice_to_bob": app_messages,
    });
    println!("{}", serde_json::to_string_pretty(&vector).unwrap());
}
