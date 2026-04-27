# EGG-07: Roles & moderation (`kind:4312`)

`status: draft`
`requires: EGG-01, EGG-04`
`category: optional`

## Summary

Hosts and admins manage the room by re-publishing the `kind:30312` event
with updated `p`-tag role markers, and (for kicks) by emitting a separate
`kind:4312` admin command. Authorization is signature-based: receivers
gate inbound commands on the signer's role at the time of receipt.

## Wire format

### Role markers (in `kind:30312`)

The 4th element of a `p`-tag, when present, is the role:

```
["p", "<pubkey>", "<relay hint>", "host" | "admin" | "speaker"]
```

Effective roles:

| value      | privileges                                                |
|------------|-----------------------------------------------------------|
| `host`     | full control; one host per room (the event author)        |
| `admin`    | promote / demote / kick; cannot edit room metadata        |
| `speaker`  | may publish audio (EGG-03 `put` claim required)           |
| (omitted)  | listener — no publish rights                              |

### Admin command (`kind:4312`)

```json
{
  "kind": 4312,
  "pubkey": "<host or admin pubkey hex>",
  "tags": [
    ["a", "30312:<host pubkey hex>:<room d>", "<relay hint>"],
    ["p", "<target pubkey hex>"],
    ["action", "kick"]
  ],
  "content": "",
  ...
}
```

`kind:4312` is an *ephemeral* event — receivers MUST NOT persist it past
the 60-second validity window defined below.

## Behavior

### Promote / demote

1. To promote a listener to speaker, the host or an admin MUST re-publish
   the `kind:30312` event with the target added (or updated) as
   `["p", <target>, "<relay>", "speaker"]`. The new event's `created_at`
   MUST be greater than the previous one.
2. To demote, the host or admin MUST re-publish the `kind:30312` event
   without the role marker (drop the 4th element of the `p`-tag) or
   remove the `p`-tag entirely.
3. Hosts MUST NOT demote themselves. Demoting the host (changing the
   event author's `p`-tag from `host`) is undefined and MAY be ignored
   by receivers.
4. The `host` role is determined by event authorship, not by `p`-tag
   marker. The `["p", <author>, _, "host"]` tag is descriptive only.
5. Speakers and admins SHOULD NOT delete or edit each other's role
   markers in their own re-publishes (only the host's re-publish is
   authoritative, but admins MAY drive promote/demote).

### Kick

6. To kick a participant, a host or admin signs and broadcasts a
   `kind:4312` event with `action="kick"` and a `p`-tag pointing at the
   target.
7. Receivers MUST gate inbound `kind:4312` events:
   - The signer MUST currently hold `host` or `admin` role on the active
     `kind:30312` (most recent `created_at` per `(kind, pubkey, d)`).
   - The event's `created_at` MUST be within the last 60 s.
   - The `a`-tag MUST match the room the receiver is in.
   - The `["action", "kick"]` element MUST be present.
   Events failing any gate MUST be silently discarded.
8. The TARGET of a kick MUST disconnect from the audio plane (close the
   moq-lite session) and tear down the in-room UI.
9. The kick command does NOT also demote the target. The host MAY follow
   up with a `kind:30312` re-publish dropping the target's role tag; the
   client-side filter then prevents future presence events from the
   target re-rendering them in the participant grid.
10. The relay MUST NOT enforce kicks. Authorization is purely
    client-side and signature-based.

## Example

Host promotes a listener to speaker:

```json
{
  "kind": 30312,
  "pubkey": "abc...host",
  "created_at": 1714003250,
  "tags": [
    ["d", "office-hours-2026-04"],
    ["room", "Office Hours"],
    ["status", "open"],
    ["service", "https://moq.nostrnests.com"],
    ["endpoint", "https://moq.nostrnests.com"],
    ["p", "abc...host", "wss://relay.example", "host"],
    ["p", "def...co",   "wss://relay.example", "speaker"],
    ["p", "ghi...newSpeaker", "wss://relay.example", "speaker"]
  ],
  "content": "",
  "id": "...",
  "sig": "..."
}
```

Admin kicks a disruptive participant:

```json
{
  "kind": 4312,
  "pubkey": "jkl...admin",
  "created_at": 1714003280,
  "tags": [
    ["a", "30312:abchost:office-hours-2026-04"],
    ["p", "mno...troll"],
    ["action", "kick"]
  ],
  "content": "",
  "id": "...",
  "sig": "..."
}
```

## Compatibility

`kind:4312` is intentionally a Nostr event, not a moq-lite control message,
so non-broadcasting clients (web, mobile, headless bots) can apply
moderation without speaking to the relay.

Future EGGs MAY introduce additional `action` values (e.g. `"mute"`,
`"warn"`). Implementers MUST treat unknown actions as no-ops.
