/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.model.accountLifecycle

// Verbatim normative vectors from tasks/namecoin-account-status/spec/conformance-vectors.json.
// Full traversal vectors are retained here as explicit Phase 5 coverage, not evaluated by the MVP.
internal val ACCOUNT_LIFECYCLE_CONFORMANCE_VECTORS = """{
  "format_version": 1,
  "draft": "nip-namecoin-account-lifecycle-draft.md",
  "description": "Normative Namecoin account lifecycle vectors. All keys and events are disposable deterministic test fixtures. No event or Namecoin value has been published.",
  "constants": {
    "OLD": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
    "NEW": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
    "OTHER": "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9",
    "AUX": "e493dbf1c10d80f3581e4904930b1404cc6c13900ee0758474fa94abe8c4cd13",
    "MAX_VALUE_BYTES": 520,
    "MAX_SINCE": 9007199254740991,
    "SINCE_EXAMPLE": 1794739200,
    "SINCE_EXAMPLE_UTC": "2026-11-15T10:40:00Z"
  },
  "fixed_example_values": {
    "compromised_309": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}",
    "retired_286": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"retired\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}",
    "retired_with_since_305": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"retired\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
  },
  "events": {
    "old_root": {
      "signature_valid": true,
      "event": {
        "id": "7ddc4fb8b6be635c1a51a13cf2e63f8b8511dade91f51b94312a0ec008ec2e8b",
        "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
        "created_at": 1794739000,
        "kind": 0,
        "tags": [],
        "content": "{\"nip05\":\"example.bit\"}",
        "sig": "0f3015be67b854851412892c17089f923d30323ad53404cb8afcb13dde9c1d1ab473c5b38f6e08d0c875505bdd1805ecf16a801d3d3816a74343c74cf7ae6e4b"
      }
    },
    "old_root_withdrawn": {
      "signature_valid": true,
      "event": {
        "id": "32060fcadd0f9be4e3c9d64a9697e1d8a6a47be097b95255aa1d7a4700bc1747",
        "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
        "created_at": 1794739100,
        "kind": 0,
        "tags": [],
        "content": "{\"name\":\"old\"}",
        "sig": "607fa25a5631abc1ba47dd60aa7ef54819bc4ef4a267ee246ea34249cc13b3a591011feace0e0c8ad3e66ba317b3d917ef7345c7c9a473991c21febc74c58b82"
      }
    },
    "old_alice": {
      "signature_valid": true,
      "event": {
        "id": "8ffbd5079395310a5fa6f2f39805e0389fb0f3fe822dff01bcfee3476c38433c",
        "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
        "created_at": 1794739001,
        "kind": 0,
        "tags": [],
        "content": "{\"nip05\":\"alice@example.bit\"}",
        "sig": "07dfceae58cc655a23594e4e7840132fba0d9de3c1b13ffaa4ba79bf469cb9216b1b372fe4878d6e88216d6689cff49a3a1e882f30f42bd1359b7d01ecda739e"
      }
    },
    "old_bob": {
      "signature_valid": true,
      "event": {
        "id": "4c08a3c81940776a00dcff24a8efc8ce49abaab659247c9a5c30e94a7aa9ece8",
        "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
        "created_at": 1794739002,
        "kind": 0,
        "tags": [],
        "content": "{\"nip05\":\"bob@example.bit\"}",
        "sig": "59fc05e0fceb2dd8b6b9433fd2be04bf1629365ce115477f3cefaae27c3deb8d61fc4984df384547be7c1a3c2bbd6ffe2b4b6cf78ed531347954f5d3dc8d6b5a"
      }
    },
    "old_id": {
      "signature_valid": true,
      "event": {
        "id": "6d608dab5833f475c71f1432dcd27e2a57f83a768f7a98f8ad197882cabac046",
        "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
        "created_at": 1794739003,
        "kind": 0,
        "tags": [],
        "content": "{\"nip05\":\"id/old\"}",
        "sig": "99f717f584b32619e2c5f7356523345aacf1ba5f0dda452305ad800f6dc4b138aa837eca74efb63027eab9e4995bf91e955aeeeb92b0d0d9350b839807618466"
      }
    },
    "old_sub_root": {
      "signature_valid": true,
      "event": {
        "id": "899cf86055a7c3a90f38425ea5972fa3017ae9f9c6cb698625defd99f1210e04",
        "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
        "created_at": 1794739007,
        "kind": 0,
        "tags": [],
        "content": "{\"nip05\":\"sub.example.bit\"}",
        "sig": "95b17b9c667cd23c2ec6507cbc5b0b4f0e459176f0c983750b6d0d753030904353d793978c09f6aa70d00ae659cc8dde8af26e8e1fff0cfe6f2418227550947b"
      }
    },
    "old_current_alias": {
      "signature_valid": true,
      "event": {
        "id": "0d1470a1304eaad40703567066311ae9c91b4d167320e8f946fcc97fc799e751",
        "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
        "created_at": 1794739004,
        "kind": 0,
        "tags": [],
        "content": "{\"nip05\":\"current@example.bit\"}",
        "sig": "318d7704b95d7d2f772fc3891475bd4b621e5319a2615233491716207413ab6aea20a61a82c769e2e78e27f0a5037c377cb91ad256daf6cf0f2e57631c7e0935"
      }
    },
    "other_bob": {
      "signature_valid": true,
      "event": {
        "id": "97d954347d847c9ad22ee27393313b6883a4842070461e4ac9fbc6959cd14cf2",
        "pubkey": "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9",
        "created_at": 1794739005,
        "kind": 0,
        "tags": [],
        "content": "{\"nip05\":\"bob@example.bit\"}",
        "sig": "4b81995edb30ad0964684758bd1b6af0f739dc10eb398fdebf496185a26afefc7583822285c1141e9ecfe367a57a8a84b36cc1cf89bc675fb9d9e1dfa4605898"
      }
    },
    "other_root": {
      "signature_valid": true,
      "event": {
        "id": "44f3ac888134269e668b7323a7ef7cdcae6b042eaf3a6e2a2dcb196f659d4fdd",
        "pubkey": "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9",
        "created_at": 1794739006,
        "kind": 0,
        "tags": [],
        "content": "{\"nip05\":\"example.bit\"}",
        "sig": "c5667981325e0b481c9322a5f7d60869387c9539119d66254743ca127698ac8d574d39c567cabb66ba4e230033da5675132959c5d0a9efb689ad9ddc85a1d0d4"
      }
    },
    "new_current": {
      "signature_valid": true,
      "event": {
        "id": "ca5f4aacdfcd7f87a6dc90fdac2e4b529cf48b3723914c5d210a0dd17725ced7",
        "pubkey": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "created_at": 1794739201,
        "kind": 0,
        "tags": [],
        "content": "{\"nip05\":\"current@example.bit\"}",
        "sig": "65d56ce5bb45c8d7683828389e1743e7c8dd5c676b8342206193c132468397b6fb8496902de4587a2a13d9f4a774a4898ece580f65bbc9be20d6f6ba50ffec20"
      }
    },
    "new_next": {
      "signature_valid": true,
      "event": {
        "id": "9845a864e1a903017066160428dc041a7d0d038caf5a270d330b0dc9b7289015",
        "pubkey": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "created_at": 1794739202,
        "kind": 0,
        "tags": [],
        "content": "{\"nip05\":\"next@example.bit\"}",
        "sig": "28751a093b13ea18b43d15f39ad770c160bce7bc9056eff920fb62eeee7a5b51ddf50de623effce726d168460fc09c3176ebedb4f9eec7da2cfc7badd8317444"
      }
    },
    "new_root": {
      "signature_valid": true,
      "event": {
        "id": "d3d0c26f9c64055fb80623351fabfecad6ac94a8dc9db2584f431cfb02a6d4e5",
        "pubkey": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "created_at": 1794739203,
        "kind": 0,
        "tags": [],
        "content": "{\"nip05\":\"example.bit\"}",
        "sig": "5a108f93c400af2bd2472ea87ecda15b085e09e91516471b74c654638dffcdaf98968d6499aad9cc0b027fd0d8f4942f3292e3a045cb0c664c72460b10be97d3"
      }
    },
    "new_withdrawn": {
      "signature_valid": true,
      "event": {
        "id": "cdbe8ee15c08147b74e93848022dafdd8e05715dfbf630b541671e67898f6df4",
        "pubkey": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "created_at": 1794739301,
        "kind": 0,
        "tags": [],
        "content": "{\"name\":\"new\"}",
        "sig": "ae057717aa3d0d9a659ddfb15409e08dba8da53dbc77ae42ff076df7b2952affd3f36233fec375ada56576be906db9e9d00d389b457b528ea875d7ba36d65cab"
      }
    },
    "old_bad_signature": {
      "signature_valid": false,
      "event": {
        "id": "7ddc4fb8b6be635c1a51a13cf2e63f8b8511dade91f51b94312a0ec008ec2e8b",
        "pubkey": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
        "created_at": 1794739000,
        "kind": 0,
        "tags": [],
        "content": "{\"nip05\":\"example.bit\"}",
        "sig": "1f3015be67b854851412892c17089f923d30323ad53404cb8afcb13dde9c1d1ab473c5b38f6e08d0c875505bdd1805ecf16a801d3d3816a74343c74cf7ae6e4b"
      }
    }
  },
  "vectors": [
    {
      "id": "bytes_compromised_309",
      "description": "Compromised root with since and successor, using two full 64-character keys.",
      "mode": "encoding",
      "value": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}",
      "utf8_bytes": 309,
      "canonical_json": true,
      "expected": {
        "publisher_accepts": true
      }
    },
    {
      "id": "bytes_retired_286",
      "description": "Retired root with successor and without since.",
      "mode": "encoding",
      "value": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"retired\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}",
      "utf8_bytes": 286,
      "canonical_json": true,
      "expected": {
        "publisher_accepts": true
      }
    },
    {
      "id": "bytes_retired_305",
      "description": "Retired root with the same since and successor.",
      "mode": "encoding",
      "value": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"retired\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}",
      "utf8_bytes": 305,
      "canonical_json": true,
      "expected": {
        "publisher_accepts": true
      }
    },
    {
      "id": "boundary_520_ascii",
      "description": "A complete compact value of exactly 520 UTF-8 bytes is accepted.",
      "mode": "encoding",
      "value": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}},\"extension\":\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"}}",
      "utf8_bytes": 520,
      "canonical_json": true,
      "expected": {
        "publisher_accepts": true
      }
    },
    {
      "id": "boundary_521_ascii",
      "description": "A complete compact value of 521 UTF-8 bytes is rejected, never truncated.",
      "mode": "encoding",
      "value": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}},\"extension\":\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"}}",
      "utf8_bytes": 521,
      "canonical_json": true,
      "expected": {
        "publisher_accepts": false
      }
    },
    {
      "id": "boundary_520_utf8",
      "description": "Exactly 520 bytes with two-byte UTF-8 filler proves that characters are not bytes.",
      "mode": "encoding",
      "value": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}},\"extension\":\"éééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééé\"}}",
      "utf8_bytes": 520,
      "canonical_json": true,
      "expected": {
        "publisher_accepts": true
      }
    },
    {
      "id": "boundary_521_utf8",
      "description": "Exactly 521 bytes with two-byte UTF-8 filler is rejected.",
      "mode": "encoding",
      "value": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}},\"extension\":\"ééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééééx\"}}",
      "utf8_bytes": 521,
      "canonical_json": true,
      "expected": {
        "publisher_accepts": false
      }
    },
    {
      "id": "root_compromised_current_backlink",
      "description": "Exact root binding and current signed OLD backlink produce an account-wide warning while exact NIP-05 remains verified.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "root_compromised_without_since_or_successor",
      "description": "Compromised remains a valid warning with neither optional field.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 134
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": null,
        "successor": null,
        "successor_confirmation": "absent",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "root_retired_without_successor",
      "description": "Voluntary retirement without a successor is valid and visually distinct from compromise.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"},\"lifecycle\":{\"_\":{\"state\":\"retired\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 130
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "retired",
        "since": null,
        "successor": null,
        "successor_confirmation": "absent",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "named_exact_binding",
      "description": "A named localpart uses only names.alice and the matching OLD backlink.",
      "mode": "inline_root",
      "identifier": "alice@example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"alice\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"},\"lifecycle\":{\"alice\":{\"state\":\"retired\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 138
      },
      "record_errors": {},
      "old_events": [
        "old_alice"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "retired",
        "since": null,
        "successor": null,
        "successor_confirmation": "absent",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "legacy_pubkey_root_binding",
      "description": "Legacy nostr.pubkey binds only root when names._ is absent.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"pubkey\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"lifecycle\":{\"_\":{\"state\":\"retired\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 125
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "retired",
        "since": null,
        "successor": null,
        "successor_confirmation": "absent",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "id_root_names_binding",
      "description": "An id/ identity record uses the exact root binding and an id/ OLD backlink.",
      "mode": "inline_root",
      "identifier": "id/old",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "id/old": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"},\"lifecycle\":{\"_\":{\"state\":\"retired\"}}}}"
      },
      "record_utf8_bytes": {
        "id/old": 130
      },
      "record_errors": {},
      "old_events": [
        "old_id"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "retired",
        "since": null,
        "successor": null,
        "successor_confirmation": "absent",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "id/old",
          "dependencies": [
            "id/old"
          ]
        }
      }
    },
    {
      "id": "id_legacy_pubkey_binding",
      "description": "An id/ identity record also accepts exact legacy nostr.pubkey for root.",
      "mode": "inline_root",
      "identifier": "id/old",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "id/old": "{\"nostr\":{\"pubkey\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"lifecycle\":{\"_\":{\"state\":\"retired\"}}}}"
      },
      "record_utf8_bytes": {
        "id/old": 125
      },
      "record_errors": {},
      "old_events": [
        "old_id"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "retired",
        "since": null,
        "successor": null,
        "successor_confirmation": "absent",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "id/old",
          "dependencies": [
            "id/old"
          ]
        }
      }
    },
    {
      "id": "direct_string_has_no_lifecycle_container",
      "description": "A direct legacy nostr string can be a NIP-05 binding but cannot carry lifecycle data.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"}"
      },
      "record_utf8_bytes": {
        "d/example": 76
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "no_declaration",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "no_lifecycle"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "arbitrary_subject_without_old_backlink",
      "description": "A Namecoin owner naming OLD without an OLD signature is confined to direct record inspection.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "direct_record",
        "state": "compromised",
        "since": 1794739200,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unverified",
        "diagnostics": [
          "direct_record_claim_only",
          "old_backlink_missing"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "wrong_key_backlink_rejected",
      "description": "OTHER signing the same identifier does not establish OLD association.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [
        "other_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "direct_record",
        "state": "compromised",
        "since": 1794739200,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unverified",
        "diagnostics": [
          "direct_record_claim_only",
          "old_backlink_missing"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "invalid_backlink_signature_rejected",
      "description": "A kind 0 with an invalid Schnorr signature is not OLD backlink evidence.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [
        "old_bad_signature"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "direct_record",
        "state": "compromised",
        "since": 1794739200,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unverified",
        "diagnostics": [
          "direct_record_claim_only",
          "old_backlink_missing"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "historical_old_backlink_survives_profile_rewrite",
      "description": "The latest OLD profile removes nip05, so current NIP-05 fails, but retained signed history preserves the lifecycle warning.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [
        "old_root",
        "old_root_withdrawn"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "unverified",
        "diagnostics": [
          "historical_old_backlink"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "nonroot_fallback_rejected",
      "description": "A named lookup never falls back to names._ even though the root names OLD.",
      "mode": "inline_root",
      "identifier": "alice@example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"},\"lifecycle\":{\"alice\":{\"state\":\"compromised\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 217
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "invalid",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unverified",
        "diagnostics": [
          "exact_binding_missing"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "root_first_entry_fallback_rejected",
      "description": "Root lifecycle binding does not use NIP-N1's optional first available names entry fallback.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"current\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 219
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "invalid",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unverified",
        "diagnostics": [
          "exact_binding_missing"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "root_dual_binding_conflict",
      "description": "Conflicting names._ and legacy pubkey root bindings fail closed for lifecycle.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"},\"pubkey\":\"f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9\",\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 289
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "invalid",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unverified",
        "diagnostics": [
          "conflicting_root_bindings"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "uppercase_subject_binding_rejected",
      "description": "Lifecycle binding grammar remains lowercase-only even if a base resolver is lenient.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 213
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "invalid",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unverified",
        "diagnostics": [
          "invalid_subject_binding"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "reciprocal_alias_confirmed",
      "description": "Exact current alias plus latest valid NEW kind 0 gives reciprocal alias confirmation, not migration consent.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [
        "new_current"
      ],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "reciprocal_alias_confirmed",
        "confirmation_aliases": [
          "current"
        ],
        "base_nip05": "verified",
        "diagnostics": [
          "reciprocity_is_not_consent"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "nonreserved_successor_alias_confirmed",
      "description": "The literal alias current is not reserved; an exact next alias can confirm instead.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"next\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 306
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [
        "new_next"
      ],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "reciprocal_alias_confirmed",
        "confirmation_aliases": [
          "next"
        ],
        "base_nip05": "verified",
        "diagnostics": [
          "reciprocity_is_not_consent"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "successor_alias_missing_unconfirmed",
      "description": "A one-way successor pointer remains unconfirmed when no exact names alias equals NEW.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 232
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [
        "new_current"
      ],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "successor_alias_missing"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "new_root_does_not_confirm_current_alias",
      "description": "NEW signing the root identifier cannot confirm current while root still names OLD.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [
        "new_root"
      ],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "new_wrong_localpart"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "old_signature_cannot_confirm_new",
      "description": "OLD signing current@example.bit is not a NEW backlink.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [
        "old_current_alias"
      ],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "new_withdraws_reciprocal_alias",
      "description": "Latest NEW metadata removes the alias, downgrading a previous confirmation without erasing OLD's warning.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [
        "new_current",
        "new_withdrawn"
      ],
      "previous_confirmation": true,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "withdrawn",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "reciprocal_withdrawn"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "new_metadata_unavailable_unconfirmed",
      "description": "No observed NEW metadata means no green confirmation, while the OLD warning remains.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "id_successor_alias_not_nip05_confirmable",
      "description": "Version 1 reciprocal alias syntax is defined for d/ names, not named id/ aliases.",
      "mode": "inline_root",
      "identifier": "id/old",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "id/old": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "id/old": 309
      },
      "record_errors": {},
      "old_events": [
        "old_id"
      ],
      "new_events": [
        "new_current"
      ],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "successor_alias_requires_d_name"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "id/old",
          "dependencies": [
            "id/old"
          ]
        }
      }
    },
    {
      "id": "invalid_since_string",
      "description": "Malformed since is omitted while the independent compromise warning remains.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":\"1794739200\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 311
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": null,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "invalid_since"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "invalid_since_boolean",
      "description": "Malformed since is omitted while the independent compromise warning remains.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":true,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 303
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": null,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "invalid_since"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "invalid_since_fraction",
      "description": "Malformed since is omitted while the independent compromise warning remains.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1.5,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 302
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": null,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "invalid_since"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "invalid_since_negative",
      "description": "Malformed since is omitted while the independent compromise warning remains.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":-1,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 301
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": null,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "invalid_since"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "invalid_since_above_safe_integer",
      "description": "Malformed since is omitted while the independent compromise warning remains.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":9007199254740992,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 315
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": null,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "invalid_since"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "invalid_since_null",
      "description": "Malformed since is omitted while the independent compromise warning remains.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":null,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 303
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": null,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "invalid_since"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "future_since_does_not_postpone_warning",
      "description": "The maximum safe integer is valid but informational; a future date never delays the current warning.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":9007199254740991,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 315
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 9007199254740991,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "unknown_state_rejected",
      "description": "Unknown states are unsupported, never active or safe, and do not fall back to another entry.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"active\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 304
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "unsupported_state",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "unknown_state"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "missing_state_rejected",
      "description": "A successor-only lifecycle object has no recognized state and no migration target.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 268
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "invalid",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "missing_state"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "lifecycle_wrong_type",
      "description": "A non-object lifecycle is invalid for lifecycle only; exact base NIP-05 still verifies.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"},\"lifecycle\":[]}}"
      },
      "record_utf8_bytes": {
        "d/example": 107
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "invalid",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "invalid_lifecycle"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "entry_wrong_type",
      "description": "A non-object localpart entry is invalid and cannot borrow root or another entry.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"},\"lifecycle\":{\"_\":\"compromised\"}}}"
      },
      "record_utf8_bytes": {
        "d/example": 124
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "invalid",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "invalid_lifecycle_entry"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "self_succession_rejected",
      "description": "A successor equal to OLD is omitted with an invalid successor diagnostic.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [
        "old_current_alias"
      ],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": null,
        "successor_confirmation": "invalid",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "invalid_successor"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "uppercase_successor_rejected",
      "description": "Uppercase lifecycle pubkeys are not case-folded or repaired.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"C6047F9441ED7D6D3045406E95C07CD85C778E4B8CEF3CA7ABAC09B95C709EE5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": null,
        "successor_confirmation": "invalid",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "invalid_successor"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "npub_successor_rejected",
      "description": "An npub or any non-hex successor encoding is malformed.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"npub1xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": null,
        "successor_confirmation": "invalid",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "invalid_successor"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "null_successor_rejected",
      "description": "Explicit null is a malformed supplied successor, not the same as omitting the field.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":null}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 247
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": null,
        "successor_confirmation": "invalid",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "invalid_successor"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "unknown_extension_keys_ignored",
      "description": "Unknown lifecycle and nostr extension keys do not change a recognized state.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"extension\":{\"future\":true},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\",\"future\":\"ignored\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 356
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "unknown_fields_ignored"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "pubkey_scan_consistent_multiple_localparts",
      "description": "A subject-only scan combines exact backlinked localparts only when state and successor agree.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": true,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"alice\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"bob\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"alice\":{\"state\":\"retired\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"bob\":{\"state\":\"retired\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 472
      },
      "record_errors": {},
      "old_events": [
        "old_alice",
        "old_bob"
      ],
      "new_events": [
        "new_current"
      ],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "retired",
        "since": null,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "reciprocal_alias_confirmed",
        "confirmation_aliases": [
          "current"
        ],
        "base_nip05": "not_applicable",
        "diagnostics": [
          "multiple_exact_localparts",
          "reciprocity_is_not_consent"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "pubkey_scan_conflicting_states",
      "description": "Conflicting backlinked states produce a protective compromise warning but no migration target or date.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": true,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"alice\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"bob\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"alice\":{\"state\":\"retired\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"bob\":{\"state\":\"compromised\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 476
      },
      "record_errors": {},
      "old_events": [
        "old_alice",
        "old_bob"
      ],
      "new_events": [
        "new_current"
      ],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "conflicting",
        "state": "compromised",
        "since": null,
        "successor": null,
        "successor_confirmation": "conflicting",
        "confirmation_aliases": [],
        "base_nip05": "not_applicable",
        "diagnostics": [
          "conflicting_states",
          "multiple_exact_localparts"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "pubkey_scan_conflicting_successors",
      "description": "Different declared successors across exact OLD bindings suppress every migration target.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": true,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"alice\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"bob\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"alice\":{\"state\":\"retired\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"bob\":{\"state\":\"retired\",\"successor\":\"f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 472
      },
      "record_errors": {},
      "old_events": [
        "old_alice",
        "old_bob"
      ],
      "new_events": [
        "new_current"
      ],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "conflicting",
        "state": "retired",
        "since": null,
        "successor": null,
        "successor_confirmation": "conflicting",
        "confirmation_aliases": [],
        "base_nip05": "not_applicable",
        "diagnostics": [
          "conflicting_successors",
          "multiple_exact_localparts"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "known_localpart_does_not_inherit_other_state",
      "description": "With a known localpart, a lifecycle entry at another localpart is not evaluated.",
      "mode": "inline_root",
      "identifier": "alice@example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"alice\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"bob\":\"f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9\"},\"lifecycle\":{\"bob\":{\"state\":\"compromised\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 213
      },
      "record_errors": {},
      "old_events": [
        "old_alice"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "no_declaration",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "no_lifecycle"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "multiple_predecessors_not_exclusive",
      "description": "Two predecessors may name the same NEW; alias confirmation is non-exclusive and still not consent.",
      "mode": "inline_root",
      "identifier": "alice@example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"alice\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"bob\":\"f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"alice\":{\"state\":\"retired\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"bob\":{\"state\":\"retired\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 472
      },
      "record_errors": {},
      "old_events": [
        "old_alice"
      ],
      "new_events": [
        "new_current"
      ],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "retired",
        "since": null,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "reciprocal_alias_confirmed",
        "confirmation_aliases": [
          "current"
        ],
        "base_nip05": "verified",
        "diagnostics": [
          "non_exclusive_predecessor",
          "reciprocity_is_not_consent"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "full_import_complete_nostr",
      "description": "Full traversal accepts an imported complete nostr object and records atomic dependencies.",
      "mode": "full",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"import\":\"d/base\"}",
        "d/base": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"retired\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 19,
        "d/base": 286
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "retired",
        "since": null,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/base",
            "d/example"
          ]
        }
      }
    },
    {
      "id": "full_import_importer_wins_no_deep_merge",
      "description": "A local nostr object replaces imported nostr wholesale; names and lifecycle in different records do not combine.",
      "mode": "full",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"import\":\"d/base\",\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"}}}",
        "d/base": "{\"nostr\":{\"lifecycle\":{\"_\":{\"state\":\"compromised\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 110,
        "d/base": 53
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "no_declaration",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "no_lifecycle"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/base",
            "d/example"
          ]
        }
      }
    },
    {
      "id": "full_import_failure_fail_closed",
      "description": "A failed selected import is unavailable even when local fields might otherwise look usable.",
      "mode": "full",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"import\":\"d/missing\",\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"}}}"
      },
      "record_utf8_bytes": {
        "d/example": 113
      },
      "record_errors": {
        "d/missing": "not_found"
      },
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "unavailable",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unavailable",
        "diagnostics": [
          "import_failed"
        ],
        "automatic_actions": [],
        "provenance": null
      }
    },
    {
      "id": "mvp_import_unsupported",
      "description": "The inline-root MVP reports UnsupportedTraversal rather than no lifecycle when import resolution is required.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"import\":\"d/base\"}",
        "d/base": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"retired\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 19,
        "d/base": 286
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "unsupported_traversal",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unsupported",
        "diagnostics": [
          "import_requires_traversal"
        ],
        "automatic_actions": [],
        "provenance": null
      }
    },
    {
      "id": "full_delegate_exclusive_success",
      "description": "A successful delegate replaces the local object; local nostr is not merged or used as fallback.",
      "mode": "full",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"delegate\":\"d/target\",\"nostr\":{\"names\":{\"_\":\"f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\"}}}}",
        "d/target": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"retired\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 156,
        "d/target": 286
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "retired",
        "since": null,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/target",
          "dependencies": [
            "d/example",
            "d/target"
          ]
        }
      }
    },
    {
      "id": "full_delegate_failure_fail_closed",
      "description": "A failed delegate is unavailable and never falls back to a valid-looking local nostr object.",
      "mode": "full",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"delegate\":\"d/missing\",\"nostr\":{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 342
      },
      "record_errors": {
        "d/missing": "not_found"
      },
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "unavailable",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unavailable",
        "diagnostics": [
          "delegate_failed"
        ],
        "automatic_actions": [],
        "provenance": null
      }
    },
    {
      "id": "mvp_delegate_unsupported",
      "description": "The inline-root MVP returns UnsupportedTraversal for delegate.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"delegate\":\"d/target\"}",
        "d/target": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"retired\",\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 23,
        "d/target": 286
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "unsupported_traversal",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unsupported",
        "diagnostics": [
          "delegate_requires_traversal"
        ],
        "automatic_actions": [],
        "provenance": null
      }
    },
    {
      "id": "full_map_selected_child_only",
      "description": "The selected map child supplies the complete nostr object and does not inherit ancestor lifecycle.",
      "mode": "full",
      "identifier": "sub.example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}},\"map\":{\"sub\":{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"},\"lifecycle\":{\"_\":{\"state\":\"retired\"}}}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 464
      },
      "record_errors": {},
      "old_events": [
        "old_sub_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "current",
        "scope": "account",
        "state": "retired",
        "since": null,
        "successor": null,
        "successor_confirmation": "absent",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "full_map_no_ancestor_lifecycle",
      "description": "A selected child with names but no lifecycle has no declaration even if the parent is compromised.",
      "mode": "full",
      "identifier": "sub.example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}},\"map\":{\"sub\":{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"}}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 426
      },
      "record_errors": {},
      "old_events": [
        "old_sub_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "no_declaration",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "no_lifecycle"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "mvp_map_unsupported",
      "description": "The inline-root MVP never parses the apex as though it were a requested map descendant.",
      "mode": "inline_root",
      "identifier": "sub.example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}},\"map\":{\"sub\":{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\"}}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 426
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "current",
      "expected": {
        "status": "unsupported_traversal",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unsupported",
        "diagnostics": [
          "map_requires_traversal"
        ],
        "automatic_actions": [],
        "provenance": null
      }
    },
    {
      "id": "stale_compromise_after_timeout",
      "description": "A transient or expiry failure retains the last compromise warning as stale, never as healthy or revoked.",
      "mode": "availability",
      "observation": "timeout",
      "cached": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "reciprocal_alias_confirmed",
        "confirmation_aliases": [
          "current"
        ],
        "base_nip05": "verified",
        "diagnostics": [
          "reciprocity_is_not_consent"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      },
      "expected": {
        "status": "stale",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "stale",
        "confirmation_aliases": [],
        "base_nip05": "unavailable",
        "diagnostics": [
          "reciprocity_is_not_consent",
          "stale_after_timeout"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "stale_compromise_after_expired",
      "description": "A transient or expiry failure retains the last compromise warning as stale, never as healthy or revoked.",
      "mode": "availability",
      "observation": "expired",
      "cached": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "reciprocal_alias_confirmed",
        "confirmation_aliases": [
          "current"
        ],
        "base_nip05": "verified",
        "diagnostics": [
          "reciprocity_is_not_consent"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      },
      "expected": {
        "status": "stale",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "stale",
        "confirmation_aliases": [],
        "base_nip05": "unavailable",
        "diagnostics": [
          "reciprocity_is_not_consent",
          "stale_after_expired"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "stale_compromise_after_server_disagreement",
      "description": "A transient or expiry failure retains the last compromise warning as stale, never as healthy or revoked.",
      "mode": "availability",
      "observation": "server_disagreement",
      "cached": {
        "status": "current",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "reciprocal_alias_confirmed",
        "confirmation_aliases": [
          "current"
        ],
        "base_nip05": "verified",
        "diagnostics": [
          "reciprocity_is_not_consent"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      },
      "expected": {
        "status": "stale",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "stale",
        "confirmation_aliases": [],
        "base_nip05": "unavailable",
        "diagnostics": [
          "reciprocity_is_not_consent",
          "stale_after_server_disagreement"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "unavailable_without_cache",
      "description": "A fresh viewer with no cached record gets unavailable, not no declaration and not active.",
      "mode": "availability",
      "observation": "timeout",
      "cached": null,
      "expected": {
        "status": "unavailable",
        "scope": "none",
        "state": null,
        "since": null,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unavailable",
        "diagnostics": [
          "lookup_unavailable"
        ],
        "automatic_actions": [],
        "provenance": null
      }
    },
    {
      "id": "provisional_shallow_observation",
      "description": "A shallow chain observation is explicitly provisional while retaining warning semantics.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [
        "old_root"
      ],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "provisional",
      "expected": {
        "status": "provisional",
        "scope": "account",
        "state": "compromised",
        "since": 1794739200,
        "successor": "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5",
        "successor_confirmation": "unconfirmed",
        "confirmation_aliases": [],
        "base_nip05": "verified",
        "diagnostics": [
          "provisional_chain_observation"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "reregistration_requires_fresh_backlink",
      "description": "A newly registered record with the same OLD binding but no OLD evidence does not inherit cached authority.",
      "mode": "inline_root",
      "identifier": "example.bit",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "subject_only": false,
      "records": {
        "d/example": "{\"nostr\":{\"names\":{\"_\":\"79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798\",\"current\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"since\":1794739200,\"successor\":\"c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5\"}}}}"
      },
      "record_utf8_bytes": {
        "d/example": 309
      },
      "record_errors": {},
      "old_events": [],
      "new_events": [],
      "previous_confirmation": false,
      "observation": "reregistered",
      "expected": {
        "status": "current",
        "scope": "direct_record",
        "state": "compromised",
        "since": 1794739200,
        "successor": null,
        "successor_confirmation": "not_applicable",
        "confirmation_aliases": [],
        "base_nip05": "unverified",
        "diagnostics": [
          "direct_record_claim_only",
          "fresh_registration",
          "old_backlink_missing"
        ],
        "automatic_actions": [],
        "provenance": {
          "selected_name": "d/example",
          "dependencies": [
            "d/example"
          ]
        }
      }
    },
    {
      "id": "discovery_fresh_viewer_no_reverse_index",
      "description": "Given only OLD and attacker-rewritten metadata, Namecoin alone cannot discover a candidate name.",
      "mode": "discovery",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "known_name": null,
      "backlink_events": [
        "old_root_withdrawn"
      ],
      "expected": {
        "candidate_name": null,
        "signed_association": false,
        "warning": "undiscoverable"
      }
    },
    {
      "id": "discovery_persisted_name_and_backlink",
      "description": "A previously persisted name plus signed OLD evidence permits rechecking; the latest OLD profile need not retain nip05.",
      "mode": "discovery",
      "subject": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
      "known_name": "example.bit",
      "backlink_events": [
        "old_root",
        "old_root_withdrawn"
      ],
      "expected": {
        "candidate_name": "d/example",
        "signed_association": true,
        "warning": "recheck_required"
      }
    },
    {
      "id": "policy_no_automatic_migration_or_event_reclassification",
      "description": "Lifecycle context never transfers trust, revokes signatures, or labels events by created_at versus since.",
      "mode": "policy",
      "event_created_at_values": [
        1794739199,
        1794739200,
        1794739201
      ],
      "since": 1794739200,
      "expected": {
        "automatic_actions": [],
        "explicit_confirmation_required": [
          "copy_metadata",
          "follow_successor",
          "migrate_admin_or_wot",
          "select_dm_recipient",
          "select_payment_recipient",
          "unfollow_old"
        ],
        "event_classifications": [
          "unclassified",
          "unclassified",
          "unclassified"
        ],
        "old_signatures_revoked": false,
        "old_events_hidden_or_reattributed": false
      }
    }
  ]
}
"""
