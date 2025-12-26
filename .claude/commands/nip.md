---
description: Get NIP specification and implementation guidance
---

Fetch and explain NIP-$ARGUMENTS from the Nostr protocol:

1. **Get the specification** from https://github.com/nostr-protocol/nips/blob/master/$ARGUMENTS.md

2. **Show key details**:
   - Event kind(s) used
   - Required and optional fields
   - Tag structure
   - Message flow between client and relay

3. **Check implementation status** in Quartz:
   ```bash
   grep -r "NIP-$ARGUMENTS\|nip$ARGUMENTS\|kind.*=" quartz/src/
   ```

4. **Provide implementation guidance**:
   - Which Quartz classes to use or create
   - Event construction example
   - Relay subscription filters
   - Verification/validation logic

## Example Usage

```
/nip 01    # Basic protocol
/nip 44    # Versioned encryption
/nip 57    # Zaps
```
