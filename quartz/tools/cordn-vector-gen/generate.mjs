// Emits cordn coordinator-contract + group-ref vectors, validated by cordn's
// OWN schemas and codecs (`@cordn/core`, MIT).
//
// The point is direction: every sample below is what OUR Kotlin client puts on
// the wire, handed to THEIR zod schema. A shape we invented that their
// coordinator would reject fails here, at generation time, instead of silently
// in production. The negative samples do the converse — they prove the schema
// is actually strict where we assume it is, so a passing positive means
// something.
//
// Usage: npm install && node generate.mjs > ../../src/commonTest/resources/cordn/coordinator-contracts.json

import {
  COORDINATOR_METHODS,
  publishKeyPackageInputSchema,
  publishKeyPackageOutputSchema,
  listAvailableKeyPackagesInputSchema,
  listAvailableKeyPackagesOutputSchema,
  consumeKeyPackageInputSchema,
  consumeKeyPackageOutputSchema,
  removeKeyPackagesInputSchema,
  removeKeyPackagesOutputSchema,
  fetchPendingWelcomesInputSchema,
  fetchPendingWelcomesOutputSchema,
  storeWelcomeInputSchema,
  storeWelcomeOutputSchema,
  storeJoinRequestInputSchema,
  storeJoinRequestOutputSchema,
  fetchManyPendingJoinRequestsInputSchema,
  fetchManyPendingJoinRequestsOutputSchema,
  postGroupMessageInputSchema,
  postGroupMessageOutputSchema,
  fetchManyGroupMessagesInputSchema,
  fetchManyGroupMessagesOutputSchema,
  subscribeManyGroupMessagesInputSchema,
  subscribeManyGroupMessagesOutputSchema,
  groupMessageSchema,
  encodeGroupRef,
  decodeGroupRef,
  isGroupRef,
} from "@cordn/core";
import { readFileSync } from "node:fs";

const coreVersion = JSON.parse(
  readFileSync(new URL("./node_modules/@cordn/core/package.json", import.meta.url), "utf8"),
).version;

// Deterministic actors. 64 lowercase hex, because that is what a cordn
// credential carries as 64 ASCII bytes (spec/00.md §13).
const ALICE = "a".repeat(64);
const BOB = "b".repeat(64);
const COORDINATOR = "c".repeat(64);
const GID = "6d1f0f6a-2a3e-4f2c-9a1d-7c6b5e4d3a21";
const KP_REF = "1f".repeat(16);
const KP_REF_2 = "2e".repeat(16);

const failures = [];

/** Validates `value` against `schema`, recording (not throwing) on mismatch. */
function accepts(label, schema, value) {
  const parsed = schema.safeParse(value);
  if (!parsed.success) {
    failures.push(`${label}: cordn's schema REJECTED a payload we send/expect — ${JSON.stringify(parsed.error.issues)}`);
  }
  return value;
}

/** The converse: proves the schema really is strict about `label`. */
function rejects(label, schema, value) {
  const parsed = schema.safeParse(value);
  if (parsed.success) {
    failures.push(`${label}: cordn's schema ACCEPTED a payload we assume it rejects`);
  }
  return value;
}

const contracts = {
  [COORDINATOR_METHODS.publishKeyPackage]: {
    input: accepts("kp_publish.input", publishKeyPackageInputSchema, {
      kp_ref: KP_REF,
      kp_64: "AAECAwQFBgc=",
    }),
    output: accepts("kp_publish.output", publishKeyPackageOutputSchema, {
      kp_ref: KP_REF,
      last_resort: false,
      at: 1757000000,
    }),
    rejects: [
      rejects("kp_publish.input/no-ref", publishKeyPackageInputSchema, { kp_64: "AAECAwQFBgc=" }),
      rejects("kp_publish.input/legacy-field", publishKeyPackageInputSchema, {
        kp_ref: KP_REF,
        keyPackageBase64: "AAECAwQFBgc=",
      }),
    ],
  },
  [COORDINATOR_METHODS.listAvailableKeyPackages]: {
    input: accepts("kp_list.input", listAvailableKeyPackagesInputSchema, {}),
    output: accepts("kp_list.output", listAvailableKeyPackagesOutputSchema, {
      keyPackages: [
        { pk: ALICE, kp_ref: KP_REF, last_resort: false, at: 1757000000 },
        { pk: BOB, kp_ref: KP_REF_2, last_resort: true, at: 1757000100 },
      ],
    }),
    rejects: [
      rejects("kp_list.output/missing-last_resort", listAvailableKeyPackagesOutputSchema, {
        keyPackages: [{ pk: ALICE, kp_ref: KP_REF, at: 1757000000 }],
      }),
    ],
  },
  [COORDINATOR_METHODS.consumeKeyPackage]: {
    input: accepts("kp_take.input", consumeKeyPackageInputSchema, { id: KP_REF }),
    output: accepts("kp_take.output", consumeKeyPackageOutputSchema, {
      keyPackage: {
        pk: BOB,
        kp_ref: KP_REF_2,
        last_resort: true,
        at: 1757000100,
        event: {
          id: "0".repeat(64),
          pubkey: BOB,
          created_at: 1757000100,
          kind: 25910,
          tags: [["p", COORDINATOR]],
          content: JSON.stringify({
            jsonrpc: "2.0",
            id: 1,
            method: "tools/call",
            params: { name: "kp_publish", arguments: { kp_ref: KP_REF_2, kp_64: "AAECAwQFBgc=" } },
          }),
          sig: "0".repeat(128),
        },
      },
    }),
    // `keyPackage: null` is how the coordinator says "nothing matching" — our
    // client returns null rather than raising, so pin that it is legal.
    emptyOutput: accepts("kp_take.output/null", consumeKeyPackageOutputSchema, { keyPackage: null }),
    rejects: [
      rejects("kp_take.output/no-event", consumeKeyPackageOutputSchema, {
        keyPackage: { pk: BOB, kp_ref: KP_REF_2, last_resort: true, at: 1757000100 },
      }),
    ],
  },
  [COORDINATOR_METHODS.removeKeyPackages]: {
    input: accepts("kp_remove.input", removeKeyPackagesInputSchema, { kp_refs: [KP_REF, KP_REF_2] }),
    output: accepts("kp_remove.output", removeKeyPackagesOutputSchema, { kp_refs: [KP_REF] }),
    rejects: [rejects("kp_remove.input/scalar", removeKeyPackagesInputSchema, { kp_refs: KP_REF })],
  },
  [COORDINATOR_METHODS.fetchPendingWelcomes]: {
    // Our client omits `consumed` entirely when it has nothing to retire,
    // rather than sending an empty array.
    input: accepts("welcome_take.input/empty", fetchPendingWelcomesInputSchema, {}),
    inputWithConsumed: accepts("welcome_take.input/consumed", fetchPendingWelcomesInputSchema, {
      consumed: [{ kp_ref: KP_REF, at: 1757000000 }],
    }),
    output: accepts("welcome_take.output", fetchPendingWelcomesOutputSchema, {
      welcomes: [
        { kp_ref: KP_REF, welcome_64: "V2VsY29tZQ==", at: 1757000200, after: 12 },
        { kp_ref: KP_REF_2, welcome_64: "V2VsY29tZTI=", at: 1757000300 },
      ],
    }),
    rejects: [
      rejects("welcome_take.input/consumed-without-at", fetchPendingWelcomesInputSchema, {
        consumed: [{ kp_ref: KP_REF }],
      }),
    ],
  },
  [COORDINATOR_METHODS.storeWelcome]: {
    input: accepts("welcome_store.input", storeWelcomeInputSchema, {
      target_pk: BOB,
      kp_ref: KP_REF_2,
      welcome_64: "V2VsY29tZQ==",
      after: 12,
    }),
    inputWithoutAfter: accepts("welcome_store.input/no-after", storeWelcomeInputSchema, {
      target_pk: BOB,
      kp_ref: KP_REF_2,
      welcome_64: "V2VsY29tZQ==",
    }),
    output: accepts("welcome_store.output", storeWelcomeOutputSchema, { at: 1757000200 }),
    rejects: [
      rejects("welcome_store.input/no-target", storeWelcomeInputSchema, {
        kp_ref: KP_REF_2,
        welcome_64: "V2VsY29tZQ==",
      }),
    ],
  },
  [COORDINATOR_METHODS.storeJoinRequest]: {
    input: accepts("join_request_store.input", storeJoinRequestInputSchema, { gid: GID, kp_ref: KP_REF }),
    output: accepts("join_request_store.output", storeJoinRequestOutputSchema, { at: 1757000400 }),
    rejects: [rejects("join_request_store.input/no-gid", storeJoinRequestInputSchema, { kp_ref: KP_REF })],
  },
  [COORDINATOR_METHODS.fetchManyPendingJoinRequests]: {
    input: accepts("join_request_take_many.input", fetchManyPendingJoinRequestsInputSchema, {
      groups: [{ gid: GID }],
    }),
    inputWithConsumed: accepts("join_request_take_many.input/consumed", fetchManyPendingJoinRequestsInputSchema, {
      groups: [{ gid: GID }],
      consumed: [{ gid: GID, pk: BOB, at: 1757000400 }],
    }),
    output: accepts("join_request_take_many.output", fetchManyPendingJoinRequestsOutputSchema, {
      requests: [{ pk: BOB, kp_ref: KP_REF_2, at: 1757000400, gid: GID }],
    }),
    rejects: [
      // The many-variant carries `gid` on every request; the single-group
      // shape does not, and mixing them is the easy mistake.
      rejects("join_request_take_many.output/no-gid", fetchManyPendingJoinRequestsOutputSchema, {
        requests: [{ pk: BOB, kp_ref: KP_REF_2, at: 1757000400 }],
      }),
      rejects("join_request_take_many.input/bare-gids", fetchManyPendingJoinRequestsInputSchema, { groups: [GID] }),
    ],
  },
  [COORDINATOR_METHODS.postGroupMessage]: {
    input: accepts("msg_post.input", postGroupMessageInputSchema, { gid: GID, msg_64: "c2VhbGVk" }),
    output: accepts("msg_post.output", postGroupMessageOutputSchema, { gid: GID, cursor: 42, at: 1757000500 }),
    rejects: [rejects("msg_post.input/no-msg", postGroupMessageInputSchema, { gid: GID })],
  },
  [COORDINATOR_METHODS.fetchManyGroupMessages]: {
    // A first-ever fetch omits `after` rather than sending 0 — the schema
    // types it as a number, and 0 would be a real cursor value.
    input: accepts("msg_fetch_many.input/first", fetchManyGroupMessagesInputSchema, { groups: [{ gid: GID }] }),
    inputWithCursor: accepts("msg_fetch_many.input/resume", fetchManyGroupMessagesInputSchema, {
      groups: [{ gid: GID, after: 42 }],
    }),
    output: accepts("msg_fetch_many.output", fetchManyGroupMessagesOutputSchema, {
      messages: [
        { gid: GID, cursor: 43, msg_64: "c2VhbGVkLTE=", at: 1757000600 },
        { gid: GID, cursor: 44, msg_64: "c2VhbGVkLTI=", at: 1757000700 },
      ],
    }),
    rejects: [
      rejects("msg_fetch_many.input/string-cursor", fetchManyGroupMessagesInputSchema, {
        groups: [{ gid: GID, after: "42" }],
      }),
    ],
  },
  [COORDINATOR_METHODS.subscribeManyGroupMessages]: {
    input: accepts("msg_sub_many.input", subscribeManyGroupMessagesInputSchema, {
      groups: [{ gid: GID, after: 42 }],
    }),
    output: accepts("msg_sub_many.output", subscribeManyGroupMessagesOutputSchema, {
      subscribed: true,
      groups: [GID],
    }),
    // Each CEP-41 stream fragment is one of these, NOT a {messages:[…]} page.
    streamFragment: accepts("msg_sub_many.fragment", groupMessageSchema, {
      gid: GID,
      cursor: 45,
      msg_64: "c2VhbGVkLTM=",
      at: 1757000800,
    }),
    rejects: [
      rejects("msg_sub_many.output/subscribed-false", subscribeManyGroupMessagesOutputSchema, {
        subscribed: false,
        groups: [GID],
      }),
    ],
  },
};

// Group refs, round-tripped through THEIR bech32 codec.
const groupRefCases = [
  { gid: GID },
  { gid: GID, coordinatorPubkey: COORDINATOR },
  { gid: GID, coordinatorPubkey: COORDINATOR, relays: ["wss://relay.example.com/"] },
  {
    gid: GID,
    coordinatorPubkey: COORDINATOR,
    relays: ["wss://relay.example.com/", "wss://relay2.example.com/"],
  },
  { gid: "a" },
  { gid: "grupo-café-éàü" },
];

const groupRefs = groupRefCases.map((ref) => {
  const encoded = encodeGroupRef(ref);
  const decoded = decodeGroupRef(encoded);
  if (JSON.stringify(decoded) !== JSON.stringify(ref)) {
    failures.push(`groupRef: their own round-trip changed ${JSON.stringify(ref)} into ${JSON.stringify(decoded)}`);
  }
  if (!isGroupRef(encoded)) failures.push(`groupRef: isGroupRef rejected ${encoded}`);
  return { ...ref, encoded };
});

// Bech32 forbids mixed case but allows an all-uppercase form. Their
// `isGroupRef` screen rejects uppercase while `decodeGroupRef` accepts it, so
// a ref pasted in caps decodes on both sides — pin that, it is easy to get
// wrong in either direction.
const uppercaseRef = encodeGroupRef({ gid: GID, coordinatorPubkey: COORDINATOR }).toUpperCase();
if (JSON.stringify(decodeGroupRef(uppercaseRef)) !== JSON.stringify({ gid: GID, coordinatorPubkey: COORDINATOR })) {
  failures.push("groupRef: their decoder did not round-trip the uppercase form");
}
const groupRefUppercase = { gid: GID, coordinatorPubkey: COORDINATOR, encoded: uppercaseRef };

// Strings their decoder must refuse. Ours has to refuse them too, or we accept
// group coordinates nobody else would.
const groupRefRejects = [
  "cordn1qqqqq",
  "nostr1qqqqq",
  "CORDN1QQQQQ",
  "cordn",
  "",
];
for (const bad of groupRefRejects) {
  let threw = false;
  try {
    decodeGroupRef(bad);
  } catch {
    threw = true;
  }
  if (!threw) failures.push(`groupRef: their decoder ACCEPTED ${JSON.stringify(bad)}, which we treat as invalid`);
}

if (failures.length > 0) {
  console.error("cordn-vector-gen: refusing to emit vectors\n  " + failures.join("\n  "));
  process.exit(1);
}

process.stdout.write(
  JSON.stringify(
    {
      _comment:
        "Generated by quartz/tools/cordn-vector-gen from @cordn/core (MIT). " +
        "Every payload here was validated by cordn's own zod schemas / bech32 codec. Do not hand-edit.",
      generator: { cordnCore: coreVersion },
      methods: COORDINATOR_METHODS,
      contracts,
      groupRefs,
      groupRefUppercase,
      groupRefRejects,
    },
    null,
    2,
  ) + "\n",
);
