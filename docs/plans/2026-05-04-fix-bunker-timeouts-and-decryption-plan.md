# Fix: Bunker Timeouts & Broken Decryption

**Branch**: `fix/bunker-timeout-and-decrypt`
**Date**: 2026-05-04
**Deepened**: 2026-05-04

## Enhancement Summary

**Research agents used:** 8 (timeout mechanics, decrypt UI trace, retry edge cases, SignerResult structure, NIP-46 protocol, test coverage, auth-signers skill, coroutine testing patterns)

### Key Improvements from Research
1. **Retry is unsafe as originally planned** — fresh UUIDs per attempt mean old responses are silently dropped. Changed to: republish same request (same ID) + extend timeout.
2. **Desktop shows raw ciphertext on failure** (confirmed at `ChatPane.kt:447`) — need error placeholder like Android's `R.string.could_not_decrypt_the_message`.
3. **DecryptCache marks `CouldNotPerformException` as `DontTryAgain`** — the parser bug causes PERMANENT failure caching. Fixing the parser also fixes the cache poisoning.
4. **Zero logging in NIP-46 module** — add structured logging for debugging.

### New Considerations Discovered
- `client.publish()` is fire-and-forget with no error feedback
- Late responses after timeout are silently discarded (continuation already removed)
- `DecryptCache` won't retry `CouldNotPerformException` — existing cached failures need invalidation after fix

---

## Problem Summary

| Issue | Symptom | Root Cause |
|-------|---------|-----------|
| Timeout | Amber shows "1m event timeout" | Amethyst 30s timeout < Amber 60s timeout |
| Decryption | Weird strings shown instead of messages | `BunkerResponseDeserializer` never creates `BunkerResponseDecrypt`; parsers reject generic `BunkerResponse` |

---

## Fix 1: Response Parsers (Decryption)

### Root Cause

`BunkerResponseDeserializer` (jvmAndroid) is context-free — it can't distinguish decrypt results (plaintext) from encrypt results (ciphertext). When result is a plain string that's not a pubkey/JSON/ack/pong, it falls through to `BunkerResponse(id, result, error)` base type.

The 4 response parsers only match specific subtypes, so all hit `else -> ReceivedButCouldNotPerform()`.

### Research Insight: Cache Poisoning

`DecryptCache` (at `quartz/.../signers/caches/DecryptCache.kt`) handles exceptions:
- `CouldNotPerformException` → **`DontTryAgain`** (permanent failure, never retried)
- `TimedOutException` → `CanTryAgain` (retries after 10s)

This means the parser bug causes **permanent cache poisoning**: once a message fails to decrypt due to the wrong response type, it's cached as permanently undecryptable until app restart.

### Fix (Option A) — Make parsers handle generic `BunkerResponse`

**Files to modify:**
- `quartz/src/commonMain/.../nip46RemoteSigner/signer/Nip04DecryptResponse.kt`
- `quartz/src/commonMain/.../nip46RemoteSigner/signer/Nip44DecryptResponse.kt`
- `quartz/src/commonMain/.../nip46RemoteSigner/signer/Nip04EncryptResponse.kt`
- `quartz/src/commonMain/.../nip46RemoteSigner/signer/Nip44EncryptResponse.kt`

**Pattern (decrypt parsers):**
```kotlin
class Nip04DecryptResponse {
    companion object {
        fun parse(response: BunkerResponse): SignerResult.RequestAddressed<DecryptionResult> =
            when (response) {
                is BunkerResponseDecrypt -> {
                    SignerResult.RequestAddressed.Successful(DecryptionResult(response.plaintext))
                }
                is BunkerResponseError -> {
                    SignerResult.RequestAddressed.Rejected()
                }
                else -> {
                    // Deserializer can't distinguish decrypt results from other strings.
                    // If we got a generic BunkerResponse with a non-null result, treat as plaintext.
                    response.result?.let {
                        SignerResult.RequestAddressed.Successful(DecryptionResult(it))
                    } ?: SignerResult.RequestAddressed.ReceivedButCouldNotPerform("No result in response")
                }
            }
    }
}
```

**Pattern (encrypt parsers):**
```kotlin
else -> {
    response.result?.let {
        SignerResult.RequestAddressed.Successful(EncryptionResult(it))
    } ?: SignerResult.RequestAddressed.ReceivedButCouldNotPerform("No result in response")
}
```

### Research Insight: `ReceivedButCouldNotPerform` already has `message: String?`

Confirmed at `SignerResult.kt:35-37`:
```kotlin
class ReceivedButCouldNotPerform<T : IResult>(
    val message: String? = null,
) : RequestAddressed<T>
```

And `convertExceptions()` at `NostrSignerRemote.kt:291`:
```kotlin
is SignerResult.RequestAddressed.ReceivedButCouldNotPerform<*> ->
    SignerExceptions.CouldNotPerformException("$title: ${result.message}")
```

No changes needed to SignerResult — just pass meaningful messages.

---

## Fix 2: Timeout + Retry

### Root Cause

`RemoteSignerManager.timeout = 30_000L` is too short. Amber allows 60s for user approval. After relay latency, Amethyst gives up before Amber responds.

### Research Insight: Retry with Fresh UUIDs is UNSAFE

Each `bunkerRequestBuilder()` generates a fresh UUID:
```kotlin
class BunkerRequestSign(
    id: String = Uuid.random().toString(),  // NEW UUID every call
    ...
)
```

**Problem with naive retry:**
1. Attempt 1: publishes request with UUID-A, times out
2. `invokeOnCancellation` removes UUID-A from `awaitingRequests`
3. Bunker responds to UUID-A → `awaitingRequests.get("UUID-A")` → null → silently dropped
4. Attempt 2: publishes with UUID-B, but bunker already processed UUID-A (may not respond to UUID-B)
5. Result: permanent failure

### Revised Fix: Republish Same Request + Extended Timeout

**Strategy:** Build the request ONCE (single UUID), then retry = republish the same event.

**File:** `quartz/src/commonMain/.../nip46RemoteSigner/signer/RemoteSignerManager.kt`

```kotlin
class RemoteSignerManager(
    val timeout: Long = 65_000,  // Match Amber's 60s + 5s relay buffer
    val client: INostrClient,
    val signer: NostrSignerInternal,
    val remoteKey: String,
    val relayList: Set<NormalizedRelayUrl>,
) {
    private val awaitingRequests = LargeCache<String, Continuation<BunkerResponse>>()

    suspend fun newResponse(responseEvent: NostrConnectEvent) {
        val decryptedJson = signer.decrypt(responseEvent.content, remoteKey)
        val bunkerResponse = OptimizedJsonMapper.fromJsonTo<BunkerResponse>(decryptedJson)
        awaitingRequests.get(bunkerResponse.id)?.resume(bunkerResponse)
    }

    suspend fun <T : IResult> launchWaitAndParse(
        bunkerRequestBuilder: () -> BunkerRequest,
        parser: (response: BunkerResponse) -> SignerResult.RequestAddressed<T>,
        maxRetries: Int = 1,
    ): SignerResult.RequestAddressed<T> {
        // Build request ONCE — same UUID for all attempts
        val request = bunkerRequestBuilder()
        val event = NostrConnectEvent.create(
            message = request,
            remoteKey = remoteKey,
            signer = signer,
        )

        var attempt = 0
        while (true) {
            val result = tryAndWait(timeout) { continuation ->
                continuation.invokeOnCancellation {
                    awaitingRequests.remove(request.id)
                }
                awaitingRequests.put(request.id, continuation)
                client.publish(event, relayList = relayList)
            }

            when {
                result != null -> return parser(result)
                attempt >= maxRetries -> return SignerResult.RequestAddressed.TimedOut()
                else -> {
                    attempt++
                    delay(2_000L)  // Brief pause before republish
                }
            }
        }
    }
}
```

**Key differences from original plan:**
1. `bunkerRequestBuilder()` called ONCE (same UUID across retries)
2. `NostrConnectEvent.create()` called ONCE (same encrypted event republished)
3. `maxRetries = 1` (conservative: 1 retry = 2 total attempts = ~130s max)
4. `delay(2_000L)` flat (no exponential — relay reconnection is the issue, not load)

### Research Insight: `client.publish()` is fire-and-forget

`NostrClient.publish()` queues to outbox and returns immediately — no success/failure feedback. Republishing the same event to relays is idempotent (relays deduplicate by event ID).

### Research Insight: No `since` filter on subscription

`StaticSubscription` uses:
```kotlin
Filter(kinds = listOf(24133), tags = mapOf("p" to listOf(signer.pubKey)))
```

No `since` → relay reconnection replays old events. This is GOOD for our retry: if relay reconnects and replays the bunker's response, we'll catch it on the second attempt.

---

## Fix 3: Desktop UI Error Display

### Research Insight: Desktop shows raw ciphertext

**File:** `desktopApp/src/jvmMain/.../desktop/ui/chats/ChatPane.kt` (lines 447-466)

Current code:
```kotlin
decryptedContent = when (event) {
    is PrivateDmEvent -> {
        try {
            event.decryptContent(account.signer)
        } catch (_: Exception) {
            event.content  // BUG: Shows raw ciphertext (base64 gibberish)
        }
    }
    else -> event?.content
}
```

Android equivalent uses `LoadDecryptedContentOrNull` with:
```kotlin
if (eventContent != null) {
    TranslatableRichTextViewer(content = eventContent, ...)
} else {
    TranslatableRichTextViewer(content = stringRes(R.string.could_not_decrypt_the_message), ...)
}
```

### Fix: Show error placeholder on Desktop

```kotlin
decryptedContent = when (event) {
    is PrivateDmEvent -> {
        try {
            event.decryptContent(account.signer)
        } catch (_: Exception) {
            null  // Changed: signal failure instead of showing raw ciphertext
        }
    }
    else -> event?.content
}

// In display:
Text(
    text = decryptedContent ?: "Could not decrypt the message",
    style = if (decryptedContent == null)
        MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
    else
        MaterialTheme.typography.bodyMedium,
    color = if (decryptedContent == null)
        MaterialTheme.colorScheme.onSurfaceVariant
    else
        MaterialTheme.colorScheme.onSurface,
)
```

### Research Insight: Cache invalidation after fix

After deploying Fix 1, previously-failed messages are stuck in `DontTryAgain` cache state. Options:
- **App restart clears cache** (DecryptCache is in-memory only) — simplest
- Document that users should restart after update
- No code change needed for this

---

## Phase Plan

### Phase 1: Fix decrypt/encrypt response parsing (P0)
1. Update `Nip04DecryptResponse.parse()` — handle generic `BunkerResponse` with `response.result`
2. Update `Nip44DecryptResponse.parse()` — same pattern
3. Update `Nip04EncryptResponse.parse()` — same pattern for encrypt
4. Update `Nip44EncryptResponse.parse()` — same pattern
5. Write tests: generic `BunkerResponse(id, "Hello world", null)` → `Successful(DecryptionResult("Hello world"))`
6. Write tests: generic `BunkerResponse(id, null, null)` → `ReceivedButCouldNotPerform`

### Phase 2: Fix timeout + safe retry (P0)
1. Increase `RemoteSignerManager.timeout` to `65_000`
2. Refactor `launchWaitAndParse`: build request once, retry = republish same event
3. Add `import kotlinx.coroutines.delay`
4. Write test: first `tryAndWait` times out, second succeeds (same request ID)
5. Write test: all attempts timeout → `TimedOut()` result
6. Write test: verify request built only once (same UUID across retries)

### Phase 3: Desktop UI error display (P1)
1. In `ChatPane.kt`, change catch fallback from `event.content` to `null`
2. Display italic error placeholder when `decryptedContent == null`
3. Match Android's UX pattern (clear error state, not raw ciphertext)

### Phase 4: Logging (P2)
1. Add `Log.d` in `newResponse()` when response arrives but no continuation found (late response)
2. Add `Log.w` in `launchWaitAndParse()` when timeout occurs (with request method)
3. Add `Log.d` on retry attempt

### Phase 5: Test coverage
New tests:
- `ResponseParserDecryptFallbackTest` — generic BunkerResponse → Successful(DecryptionResult)
- `ResponseParserEncryptFallbackTest` — generic BunkerResponse → Successful(EncryptionResult)
- `ResponseParserNullResultTest` — BunkerResponse with null result → ReceivedButCouldNotPerform
- `RemoteSignerManagerRetryTest` — timeout then success (uses `runTest` + virtual time)
- `RemoteSignerManagerSameIdTest` — verify UUID stability across retries
- `RemoteSignerManagerMaxRetriesTest` — all timeout → TimedOut

**Test patterns to use** (from kotlin-coroutines skill):
```kotlin
@Test
fun `retry succeeds on second attempt`() = runTest {
    val fakeClient = EmptyNostrClient()
    val manager = RemoteSignerManager(
        timeout = 1000,  // Short for tests
        client = fakeClient,
        signer = testSigner,
        remoteKey = testRemoteKey,
        relayList = setOf(testRelay),
    )
    // ... simulate late response arrival
}
```

---

## Manual Testing Checklist

- [ ] Login with bunker:// URI on Desktop
- [ ] Send a DM (requires sign + encrypt)
- [ ] Receive and read a DM (requires decrypt)
- [ ] Post a note (requires sign)
- [ ] Verify no "weird strings" in message views
- [ ] Verify error placeholder shown when bunker offline
- [ ] Verify timeout doesn't fire before 60s
- [ ] Restart app after fix to clear poisoned DecryptCache entries

---

## File Change Summary

| File | Change | Phase |
|------|--------|-------|
| `quartz/.../signer/Nip04DecryptResponse.kt` | Fallback to `response.result` | 1 |
| `quartz/.../signer/Nip44DecryptResponse.kt` | Fallback to `response.result` | 1 |
| `quartz/.../signer/Nip04EncryptResponse.kt` | Fallback to `response.result` | 1 |
| `quartz/.../signer/Nip44EncryptResponse.kt` | Fallback to `response.result` | 1 |
| `quartz/.../signer/RemoteSignerManager.kt` | Timeout 65s, retry with same request | 2 |
| `desktopApp/.../ui/chats/ChatPane.kt` | Error placeholder instead of raw ciphertext | 3 |
| `quartz/src/commonTest/.../ResponseParserFallbackTest.kt` | New test file | 5 |
| `quartz/src/commonTest/.../RemoteSignerManagerRetryTest.kt` | New test file | 5 |

---

## Questions (Resolved)

| Q | A |
|---|---|
| Android or Desktop? | Desktop (Jackson deserializer path) |
| Amber or Amethyst timeout msg? | Amber UI |
| Global or per-operation timeout? | Global, sync with Amber |
| Retry or just longer timeout? | Both: longer timeout + safe republish retry |
| Option A or B for decrypt? | A (parser handles generic BunkerResponse) |
| UUID per retry? | NO — build request once, reuse across attempts |
| Cache invalidation? | Not needed — app restart clears in-memory DecryptCache |
