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
package com.vitorpamplona.amethyst.cli.secrets

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Where the private key physically lives. Persisted inside `identity.json`
 * as the `secret` field. Read-only identities persist `secret: null`.
 *
 * Variants:
 *  - [Keychain] — private key held in the OS keychain; file stores only a
 *    reference (service + account). On macOS the Keychain ACL binds the item
 *    to the binary that stored it so other apps need user consent; on Linux
 *    Secret Service any app on the user's D-Bus session can retrieve it once
 *    the keyring is unlocked — the file-level gain there is at-rest
 *    encryption while the keyring is locked.
 *  - [Ncryptsec] — NIP-49 scrypt+XChaCha20 blob. The passphrase is supplied
 *    at runtime (env / file / TTY) and no other same-user app can decrypt
 *    the blob without that passphrase. Protects against same-user malware
 *    on every platform at the cost of requiring a passphrase per session.
 *  - [Plaintext] — opt-in escape hatch for dev scripts that want to diff
 *    identity.json. Equivalent to the old pre-hardening behaviour plus the
 *    0600 file mode from `SecureFileIO`.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = IdentitySecret.Keychain::class, name = "keychain"),
    JsonSubTypes.Type(value = IdentitySecret.Ncryptsec::class, name = "ncryptsec"),
    JsonSubTypes.Type(value = IdentitySecret.Plaintext::class, name = "plaintext"),
)
sealed interface IdentitySecret {
    data class Keychain(
        val backend: String,
        val service: String,
        val account: String,
    ) : IdentitySecret

    data class Ncryptsec(
        val ncryptsec: String,
    ) : IdentitySecret

    data class Plaintext(
        val privKeyHex: String,
    ) : IdentitySecret
}

/**
 * On-disk shape of `identity.json`. The public fields are always present;
 * the private key is stored indirectly via [secret]. The two `legacy*` fields
 * are honoured when reading a pre-secret-store file so existing users are
 * auto-migrated on the next save-capable command.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IdentityFile(
    val pubKeyHex: String,
    val npub: String,
    val secret: IdentitySecret? = null,
    // Tolerated on read for forward-compat with pre-secret-store data-dirs;
    // never written by current code.
    val privKeyHex: String? = null,
    val nsec: String? = null,
)
