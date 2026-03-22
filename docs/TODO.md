# TODO

## DM: Mixed NIP-04 + NIP-17 conversations

**Question:** What happens when a conversation with an npub has both NIP-04 (kind 4) and NIP-17 (kind 14/15 in GiftWrap) messages?

**Current behavior to investigate:**
- NIP-04 messages use `PrivateDmEvent.chatroomKey(pubKey)` → creates a `ChatroomKey` based on recipient
- NIP-17 messages use `ChatMessageEvent.chatroomKey(pubKey)` → also creates a `ChatroomKey` based on group members
- Do these produce the **same** `ChatroomKey` for 1-on-1 chats? If yes, both message types merge into one conversation. If no, they appear as separate conversations.
- Android Amethyst handles this — check how `Account.kt` merges them
- Edge cases: timeline ordering, decryption display, NIP-04 messages showing as "legacy" vs NIP-17

**Action:** Test with a real conversation that has both types. If they split into two rooms, we need to merge them in `ChatroomListState` or unify the `ChatroomKey` derivation.
