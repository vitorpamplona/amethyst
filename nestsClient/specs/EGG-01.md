# EGG-01: Room event (`kind:30312`)

`status: draft`
`requires: NIP-01`
`category: required`

## Summary

A nests audio room is a NIP-53 addressable event of `kind:30312`. The author
is the host. Subscribers read this event to learn who runs the room, where the
audio plane lives, who else is invited, and whether the room is currently live.

Every other EGG layers on top of this event.

## Wire format

```json
{
  "kind": 30312,
  "pubkey": "<host pubkey hex>",
  "tags": [
    ["d", "<room id>"],
    ["room", "<room name>"],
    ["summary", "<one-line description>"],
    ["image", "<optional cover image URL>"],
    ["status", "open" | "private" | "closed" | "planned"],
    ["service", "<https URL of moq-auth sidecar>"],
    ["endpoint", "<https URL of moq-relay WebTransport>"],
    ["relays", "<wss relay 1>", "<wss relay 2>", ...],
    ["p", "<pubkey>", "<relay hint>", "host" | "admin" | "speaker"],
    ...
  ],
  "content": "",
  ...
}
```

The `d` tag is the room id; `(pubkey, kind, d)` is the addressable identity.

## Behavior

1. Hosts MUST emit exactly one `kind:30312` event per room. Updating the room
   (rename, status change, role grants) MUST re-publish the same `d` tag with
   a higher `created_at`.
2. The `room`, `status`, `service`, and `endpoint` tags MUST be present. A
   client receiving an event without all four MUST treat the room as
   un-joinable.
3. The `service` value MUST be the base URL of an EGG-02 auth sidecar (do
   not include the `/auth` suffix).
4. The `endpoint` value MUST be the base URL of a WebTransport-capable
   moq-relay implementing EGG-03.
5. The `p` tag MUST list the host as the first participant. Additional
   `p` tags grant roles (EGG-07).
6. Status semantics:
   - `open` — room is live, anyone can join.
   - `private` — room is live, an out-of-band invitation gate applies (relay
     enforces; client behavior identical to `open`).
   - `closed` — room is over, audio plane SHOULD be torn down server-side.
   - `planned` — room hasn't started; see EGG-08.
7. A host MAY treat a room as auto-closed after 8 h of `created_at` staleness
   even if the published status is still `open`/`private`. Receivers SHOULD do
   the same to avoid stale rooms hanging at the top of the live UI.
8. An empty `content` field is REQUIRED. Future EGGs MAY define structured
   content; until then, peers MUST ignore non-empty content rather than
   rejecting the event.

## Example

```json
{
  "kind": 30312,
  "pubkey": "abc...host",
  "created_at": 1714003200,
  "tags": [
    ["d", "office-hours-2026-04"],
    ["room", "Office Hours"],
    ["summary", "Weekly Q&A"],
    ["status", "open"],
    ["service", "https://moq.nostrnests.com"],
    ["endpoint", "https://moq.nostrnests.com"],
    ["p", "abc...host", "wss://relay.example", "host"],
    ["p", "def...co",   "wss://relay.example", "speaker"]
  ],
  "content": "",
  "id": "...",
  "sig": "..."
}
```

## Compatibility

Peers without EGG-01 cannot interpret any other EGG. EGG-04, EGG-05, EGG-06,
EGG-07, and EGG-12 reference rooms by the `(kind, pubkey, d)` tuple defined
here.
