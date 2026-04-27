# EGG-09: User server list (`kind:10112`)

`status: draft`
`requires: NIP-01`
`category: optional`

## Summary

A user MAY publish a list of nests servers they prefer to host on. Clients
read this list to default-fill the "service" and "endpoint" fields when the
user opens a new room, and to suggest peer rooms in discovery surfaces.

This is the audio-rooms equivalent of the user-server-list patterns in
Blossom (BUD-03) and Mostr.

## Wire format

`kind:10112` is a NIP-01 *replaceable* event with one entry per server:

```json
{
  "kind": 10112,
  "pubkey": "<user pubkey hex>",
  "tags": [
    ["server", "<https URL — moq-auth/moq-relay base>"],
    ["server", "<https URL>"],
    ...
  ],
  "content": "",
  ...
}
```

Each `["server", url]` is one entry. The relative ordering MUST be
preserved by receivers — earlier entries are higher priority.

## Behavior

1. Each `server` value MUST be a fully-qualified URL beginning with
   `https://`. Receivers MUST reject (drop) entries that are not
   well-formed HTTPS URLs.
2. Receivers MUST de-duplicate entries by exact-string match after
   trimming a single trailing `/`. Order of remaining entries MUST be
   preserved (the FIRST occurrence wins).
3. When the user opens the create-room sheet, the client SHOULD pre-fill
   the EGG-01 `service` and `endpoint` fields from the FIRST entry in
   the list (a single nests deployment serves both via the same base
   URL today).
4. Users MAY enumerate up to 64 servers. Receivers MUST tolerate longer
   lists by truncating to the first 64.
5. Hosts who list a server in their `kind:10112` are not declaring an
   alliance — they are merely advertising "I will probably create rooms
   here". Receivers MUST NOT use the list as a moderation signal.
6. The list is purely a defaults / discovery hint. A `kind:30312` event's
   own `service` / `endpoint` tags are authoritative for that specific
   room and override the user list at join time.

## Example

```json
{
  "kind": 10112,
  "pubkey": "abc...host",
  "created_at": 1714003000,
  "tags": [
    ["server", "https://moq.nostrnests.com"],
    ["server", "https://moq.example.org"]
  ],
  "content": "",
  "id": "...",
  "sig": "..."
}
```

## Compatibility

A peer that does not implement EGG-09 simply does not pre-fill server
fields and does not surface "rooms hosted by this user are usually on…"
hints in profile screens. All other interop is unaffected.
