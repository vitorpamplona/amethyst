# Sealed Class Catalog

Comprehensive list of sealed types in AmethystMultiplatform with usage patterns.

## Table of Contents
- [State Management](#state-management)
- [Result Types](#result-types)
- [Tag Variants](#tag-variants)
- [Sealed Class vs Sealed Interface](#sealed-class-vs-sealed-interface)
- [Patterns](#patterns)

---

## State Management

### AccountState (Sealed Class)

**File:** `commons/src/jvmAndroid/kotlin/com/vitorpamplona/amethyst/commons/account/AccountManager.kt:36-46`

```kotlin
sealed class AccountState {
    data object LoggedOut : AccountState()

    data class LoggedIn(
        val signer: NostrSigner,
        val pubKeyHex: String,
        val npub: String,
        val nsec: String?,
        val isReadOnly: Boolean
    ) : AccountState()
}
```

**Why sealed class:**
- Two distinct states with different data
- `LoggedIn` holds data, `LoggedOut` doesn't
- No need for generics or multiple inheritance

**Usage:**

```kotlin
fun handleAccountState(state: AccountState) {
    when (state) {
        is AccountState.LoggedOut -> showLogin()
        is AccountState.LoggedIn -> {
            showFeed(
                pubkey = state.pubKeyHex,
                canSign = !state.isReadOnly
            )
        }
    }  // Exhaustive - compiler enforces
}
```

### VerificationState (Sealed Class)

**File:** `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip03Timestamp/VerificationState.kt`

```kotlin
sealed class VerificationState {
    data object NotStarted : VerificationState()
    data object Started : VerificationState()
    data class Failed(val reason: String) : VerificationState()
    data object Verified : VerificationState()
}
```

**Pattern:**
- State machine (NotStarted → Started → Failed/Verified)
- Only `Failed` carries data (reason)
- Rest are singletons (`data object`)

---

## Result Types

### SignerResult (Sealed Interface with Generics)

**File:** `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip46RemoteSigner/signer/SignerResult.kt:25-46`

```kotlin
sealed interface SignerResult<T : IResult> {
    sealed interface RequestAddressed<T : IResult> : SignerResult<T> {
        class Successful<T : IResult>(val result: T) : RequestAddressed<T>
        class Rejected<T : IResult> : RequestAddressed<T>
        class TimedOut<T : IResult> : RequestAddressed<T>
        class ReceivedButCouldNotPerform<T : IResult>(
            val message: String? = null
        ) : RequestAddressed<T>
        class ReceivedButCouldNotParseEventFromResult<T : IResult>(
            val eventJson: String
        ) : RequestAddressed<T>
        class ReceivedButCouldNotVerifyResultingEvent<T : IResult>(
            val invalidEvent: Event
        ) : RequestAddressed<T>
    }
}

interface IResult

data class SignResult(val event: Event) : IResult
data class EncryptionResult(val ciphertext: String) : IResult
data class DecryptionResult(val plaintext: String) : IResult
```

**Why sealed interface:**
- Generic result type `<T : IResult>`
- Nested sealed hierarchy (RequestAddressed)
- Need covariance for flexible result types

**Usage:**

```kotlin
suspend fun signEvent(event: Event): SignerResult<SignResult> {
    return when (val result = remoteSigner.sign(event)) {
        is SignerResult.RequestAddressed.Successful -> result
        is SignerResult.RequestAddressed.Rejected -> {
            logger.warn("Signing rejected")
            result
        }
        is SignerResult.RequestAddressed.TimedOut -> {
            logger.error("Signing timed out")
            result
        }
        is SignerResult.RequestAddressed.ReceivedButCouldNotPerform -> {
            logger.error("Signer error: ${result.message}")
            result
        }
    }
}
```

### CacheResults (Sealed Class with Generics)

**File:** `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/signers/caches/CacheResults.kt`

```kotlin
sealed class CacheResults<T> {
    data class Found<T>(val value: T) : CacheResults<T>()
    class NotFound<T> : CacheResults<T>()
}
```

**Pattern:**
- Simple binary result (found/not found)
- `Found` carries data, `NotFound` doesn't
- Generic for reusability

---

## Tag Variants

### MuteTag (Sealed Class)

**File:** `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip51Lists/muteList/tags/MuteTag.kt`

```kotlin
sealed class MuteTag(
    val nameOrNull: String?,
    val valueOrNull: String?
) {
    class Event(eventId: String) : MuteTag("e", eventId)
    class Profile(pubkey: String) : MuteTag("p", pubkey)
    class Word(word: String) : MuteTag("word", word)
    class Thread(threadId: String) : MuteTag("thread", threadId)

    companion object {
        fun parse(tag: Array<String>): MuteTag? {
            return when (tag.getOrNull(0)) {
                "e" -> tag.getOrNull(1)?.let { Event(it) }
                "p" -> tag.getOrNull(1)?.let { Profile(it) }
                "word" -> tag.getOrNull(1)?.let { Word(it) }
                "thread" -> tag.getOrNull(1)?.let { Thread(it) }
                else -> null
            }
        }
    }

    fun toArray(): Array<String> {
        return arrayOf(nameOrNull ?: "", valueOrNull ?: "")
    }
}
```

**Pattern:**
- Common base class with shared properties
- Each variant represents different tag type
- Factory method `parse()` for parsing
- `toArray()` for serialization

### BookmarkIdTag (Sealed Class)

**File:** `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip51Lists/bookmarkList/tags/BookmarkIdTag.kt`

```kotlin
sealed class BookmarkIdTag {
    abstract val id: String
    abstract val marker: String?

    data class Event(override val id: String, override val marker: String?) : BookmarkIdTag()
    data class Profile(override val id: String, override val marker: String?) : BookmarkIdTag()
    data class Address(override val id: String, override val marker: String?) : BookmarkIdTag()

    companion object {
        fun parse(tag: Array<String>): BookmarkIdTag? {
            val marker = tag.getOrNull(3)
            return when (tag.getOrNull(0)) {
                "e" -> tag.getOrNull(1)?.let { Event(it, marker) }
                "p" -> tag.getOrNull(1)?.let { Profile(it, marker) }
                "a" -> tag.getOrNull(1)?.let { Address(it, marker) }
                else -> null
            }
        }
    }
}
```

**Pattern:**
- Abstract properties in sealed class
- Data classes implement abstract properties
- Parse factory returns sealed variant

---

## Exception Hierarchies

### SignerExceptions (Sealed Class)

**File:** `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/signers/SignerExceptions.kt`

```kotlin
sealed class SignerExceptions(message: String) : Exception(message) {
    class UnableToSign(message: String) : SignerExceptions(message)
    class UnableToDecrypt(message: String) : SignerExceptions(message)
    class UnableToEncrypt(message: String) : SignerExceptions(message)
    class UnableToGetPublicKey(message: String) : SignerExceptions(message)
}
```

**Pattern:**
- Sealed exception hierarchy
- Extends `Exception` base class
- Type-safe error handling

**Usage:**

```kotlin
try {
    signer.sign(event)
} catch (e: SignerExceptions) {
    when (e) {
        is SignerExceptions.UnableToSign -> logger.error("Signing failed: ${e.message}")
        is SignerExceptions.UnableToDecrypt -> logger.error("Decryption failed: ${e.message}")
        is SignerExceptions.UnableToEncrypt -> logger.error("Encryption failed: ${e.message}")
        is SignerExceptions.UnableToGetPublicKey -> logger.error("No public key: ${e.message}")
    }
}
```

---

## Sealed Class vs Sealed Interface

### When to Use Sealed Class

**Examples from codebase:**

1. **AccountState** - State variants with different data
2. **VerificationState** - State machine
3. **MuteTag** - Tag variants with common base properties
4. **SignerExceptions** - Exception hierarchy

**Characteristics:**
- Need common constructor parameters
- Single inheritance only
- State variants
- Exception hierarchies

### When to Use Sealed Interface

**Examples from codebase:**

1. **SignerResult<T>** - Generic result types needing variance
2. **RelayUrlNormalizer.Result** - Binary result with no shared state

**Characteristics:**
- Need generics with variance (`out`, `in`)
- No common state needed
- Multiple inheritance possible
- Contract/capability representation

---

## Patterns

### Pattern: State Machine

```kotlin
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val relay: String) : ConnectionState()
    data class Failed(val error: String) : ConnectionState()
}

// Allowed transitions
fun transition(from: ConnectionState, event: Event): ConnectionState {
    return when (from) {
        is ConnectionState.Disconnected -> {
            when (event) {
                is Event.Connect -> ConnectionState.Connecting
                else -> from
            }
        }
        is ConnectionState.Connecting -> {
            when (event) {
                is Event.Success -> ConnectionState.Connected(event.relay)
                is Event.Error -> ConnectionState.Failed(event.message)
                is Event.Cancel -> ConnectionState.Disconnected
                else -> from
            }
        }
        is ConnectionState.Connected -> {
            when (event) {
                is Event.Disconnect -> ConnectionState.Disconnected
                is Event.Error -> ConnectionState.Failed(event.message)
                else -> from
            }
        }
        is ConnectionState.Failed -> {
            when (event) {
                is Event.Retry -> ConnectionState.Connecting
                is Event.Cancel -> ConnectionState.Disconnected
                else -> from
            }
        }
    }
}
```

### Pattern: Result Type

```kotlin
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val exception: Exception) : Result<Nothing>
    data object Loading : Result<Nothing>
}

// Extension functions
fun <T> Result<T>.getOrNull(): T? = when (this) {
    is Result.Success -> data
    else -> null
}

fun <T> Result<T>.getOrThrow(): T = when (this) {
    is Result.Success -> data
    is Result.Error -> throw exception
    is Result.Loading -> error("Still loading")
}

fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error -> this
    is Result.Loading -> Result.Loading
}
```

### Pattern: Tagged Union (Discriminated Union)

```kotlin
sealed class Command {
    data class SendEvent(val event: Event) : Command()
    data class Subscribe(val filters: List<Filter>) : Command()
    data class Unsubscribe(val subId: String) : Command()
    data object Close : Command()

    fun toJson(): String = when (this) {
        is SendEvent -> """["EVENT",${event.toJson()}]"""
        is Subscribe -> """["REQ","sub",${filters.joinToString { it.toJson() }}]"""
        is Unsubscribe -> """["CLOSE","$subId"]"""
        is Close -> """["CLOSE"]"""
    }
}
```

### Pattern: Nested Sealed Hierarchies

```kotlin
sealed interface UiState {
    sealed interface Loading : UiState {
        data object Initial : Loading
        data class Refreshing(val currentData: List<Item>) : Loading
    }

    sealed interface Content : UiState {
        data class Success(val data: List<Item>) : Content
        data object Empty : Content
    }

    sealed interface Error : UiState {
        data class Network(val message: String) : Error
        data class Server(val code: Int, val message: String) : Error
    }
}

// Usage
fun renderUi(state: UiState) {
    when (state) {
        is UiState.Loading.Initial -> showFullScreenLoader()
        is UiState.Loading.Refreshing -> showRefreshIndicator(state.currentData)
        is UiState.Content.Success -> showList(state.data)
        is UiState.Content.Empty -> showEmptyState()
        is UiState.Error.Network -> showNetworkError(state.message)
        is UiState.Error.Server -> showServerError(state.code, state.message)
    }
}
```

---

## All Sealed Types in Quartz

**Complete list of sealed types found in codebase:**

### Commons
- AccountState (class)

### Quartz
- BaseZapSplitSetup (class)
- MuteTag (class)
- BookmarkIdTag (class)
- SignerResult (interface)
- VerificationState (class)
- CacheResults (class)
- SignerExceptions (class)
- RelayUrlNormalizer.Result (interface)

**Total:** 8 sealed types (7 classes, 1 interface)

---

## Decision Tree

```
Need to represent variants of a concept?
    YES → Use sealed type
    NO → Regular class/interface

Variants have different data?
    YES → sealed class or sealed interface
    NO → enum (if simple constants)

Need generics with variance (out/in)?
    YES → sealed interface
    NO → sealed class (simpler)

Need common constructor/properties?
    YES → sealed class
    NO → sealed interface

Need multiple inheritance?
    YES → sealed interface
    NO → Either works

Representing state machine?
    → sealed class (state transitions)

Representing result/error types?
    → sealed interface (if generic, else class)

Representing tag/command variants?
    → sealed class (common structure)
```

---

## References

- [Sealed Classes | Kotlin Docs](https://kotlinlang.org/docs/sealed-classes.html)
- [Effective Kotlin: Sealed Classes](https://kt.academy/article/ek-sealed-classes)
- [Complete Guide: Sealed Classes & Interfaces 2025](https://proandroiddev.com/complete-technical-guide-sealed-classes-sealed-interfaces-enums-in-kotlin-28ffc39116df)
