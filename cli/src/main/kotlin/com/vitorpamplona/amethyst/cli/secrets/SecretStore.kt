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

import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Backend responsible for the round-trip `privKeyHex ⇄ IdentitySecret`.
 *
 * Every backend operates on a single identity's material. Creating or rotating
 * a key goes through [store]; a subsequent load routes the persisted
 * [IdentitySecret] back through [resolve]. Backends are selected in
 * [SecretStore] — callers should not instantiate concrete backends directly.
 */
internal interface SecretBackend {
    val name: String

    /** Quick probe that avoids committing to the backend before we know it works. */
    fun isAvailable(): Boolean

    fun store(
        pubKeyHex: String,
        privKeyHex: String,
    ): IdentitySecret

    fun resolve(secret: IdentitySecret): String

    fun delete(secret: IdentitySecret)
}

/**
 * Facade over [SecretBackend]s. Decides which backend handles a new save
 * (based on the `--secret-backend` flag or platform auto-detection) and
 * routes a stored [IdentitySecret] back to its originating backend on load.
 */
class SecretStore internal constructor(
    private val passphrase: PassphraseProvider,
    private val backendOverride: String?,
) {
    /** Pick a backend for a new private-key save. */
    internal fun selectBackend(): SecretBackend =
        when (backendOverride) {
            null, "auto" -> {
                pickKeychain() ?: NcryptsecBackend(passphrase)
            }

            "keychain" -> {
                pickKeychain() ?: throw IllegalStateException(
                    "no OS keychain backend available on this platform (need /usr/bin/security on macOS " +
                        "or secret-tool + an active Secret Service on Linux)",
                )
            }

            "ncryptsec" -> {
                NcryptsecBackend(passphrase)
            }

            "plaintext" -> {
                PlaintextBackend
            }

            else -> {
                throw IllegalArgumentException("unknown --secret-backend: $backendOverride")
            }
        }

    /** Route a stored secret descriptor back to its originating backend. */
    internal fun backendFor(secret: IdentitySecret): SecretBackend =
        when (secret) {
            is IdentitySecret.Keychain -> {
                when (secret.backend) {
                    MacosKeychainBackend.BACKEND_ID -> MacosKeychainBackend
                    SecretServiceBackend.BACKEND_ID -> SecretServiceBackend
                    else -> throw IllegalStateException("unknown keychain backend: ${secret.backend}")
                }
            }

            is IdentitySecret.Ncryptsec -> {
                NcryptsecBackend(passphrase)
            }

            is IdentitySecret.Plaintext -> {
                PlaintextBackend
            }
        }

    private fun pickKeychain(): SecretBackend? = sequenceOf(MacosKeychainBackend, SecretServiceBackend).firstOrNull { it.isAvailable() }

    fun store(
        pubKeyHex: String,
        privKeyHex: String,
    ): IdentitySecret = selectBackend().store(pubKeyHex, privKeyHex)

    fun resolve(secret: IdentitySecret): String = backendFor(secret).resolve(secret)

    fun delete(secret: IdentitySecret) {
        try {
            backendFor(secret).delete(secret)
        } catch (e: Exception) {
            System.err.println("[cli] secret delete failed: ${e.message}")
        }
    }

    companion object {
        /**
         * Build a [SecretStore] from command-line flags + environment.
         *
         *  - [backendFlag] = `auto` | `keychain` | `ncryptsec` | `plaintext`
         *  - [passphraseFile] reads passphrase from a file (for scripted test
         *    harnesses); otherwise `$AMY_PASSPHRASE` is consulted, then a TTY
         *    prompt. Only used by [NcryptsecBackend].
         */
        fun from(
            backendFlag: String?,
            passphraseFile: String?,
        ): SecretStore = SecretStore(PassphraseProvider(passphraseFile), backendFlag)
    }
}

// ---------------------------------------------------------------------------
// macOS Keychain backend — shells out to /usr/bin/security.
// ---------------------------------------------------------------------------

internal object MacosKeychainBackend : SecretBackend {
    const val BACKEND_ID = "macos"
    private const val SERVICE_PREFIX = "amy-nostr"
    private const val SECURITY_BIN = "/usr/bin/security"

    override val name: String = "keychain:$BACKEND_ID"

    override fun isAvailable(): Boolean {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        if (!os.contains("mac") && !os.contains("darwin")) return false
        return File(SECURITY_BIN).canExecute()
    }

    override fun store(
        pubKeyHex: String,
        privKeyHex: String,
    ): IdentitySecret {
        val service = SERVICE_PREFIX
        // -U updates in place if an item with (-s, -a) already exists. The private
        // key appears in the command line momentarily — same-user /proc reads
        // could observe it, which is the exact threat we are defending against.
        // `security` has no non-interactive stdin path for add-generic-password,
        // so this is the standard trade-off every keychain helper makes.
        val res =
            runProc(
                SECURITY_BIN,
                "add-generic-password",
                "-U",
                "-s",
                service,
                "-a",
                pubKeyHex,
                "-l",
                "amy Nostr identity ${pubKeyHex.take(8)}",
                "-D",
                "amy nostr key",
                "-w",
                privKeyHex,
            )
        if (res.exit != 0) {
            throw RuntimeException("security add-generic-password failed (exit=${res.exit}): ${res.stderr.trim()}")
        }
        return IdentitySecret.Keychain(backend = BACKEND_ID, service = service, account = pubKeyHex)
    }

    override fun resolve(secret: IdentitySecret): String {
        require(secret is IdentitySecret.Keychain)
        val res = runProc(SECURITY_BIN, "find-generic-password", "-s", secret.service, "-a", secret.account, "-w")
        if (res.exit != 0) {
            throw RuntimeException(
                "security find-generic-password failed (exit=${res.exit}): ${res.stderr.trim()} — " +
                    "the user may have denied the access prompt",
            )
        }
        return res.stdout.trim()
    }

    override fun delete(secret: IdentitySecret) {
        require(secret is IdentitySecret.Keychain)
        runProc(SECURITY_BIN, "delete-generic-password", "-s", secret.service, "-a", secret.account)
    }
}

// ---------------------------------------------------------------------------
// Linux Secret Service backend — shells out to `secret-tool`.
// ---------------------------------------------------------------------------

internal object SecretServiceBackend : SecretBackend {
    const val BACKEND_ID = "secret-service"
    private const val SERVICE_ATTR = "amy-nostr"
    private const val SECRET_TOOL_BIN = "secret-tool"

    override val name: String = "keychain:$BACKEND_ID"

    override fun isAvailable(): Boolean {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        if (!os.contains("linux")) return false
        // secret-tool requires a running Secret Service daemon which lives on
        // the session D-Bus. Headless servers typically lack both.
        if (System.getenv("DBUS_SESSION_BUS_ADDRESS").isNullOrEmpty() &&
            System.getenv("XDG_RUNTIME_DIR").isNullOrEmpty()
        ) {
            return false
        }
        return which(SECRET_TOOL_BIN) != null
    }

    override fun store(
        pubKeyHex: String,
        privKeyHex: String,
    ): IdentitySecret {
        val res =
            runProc(
                SECRET_TOOL_BIN,
                "store",
                "--label=amy Nostr identity ${pubKeyHex.take(8)}",
                "service",
                SERVICE_ATTR,
                "account",
                pubKeyHex,
                stdin = privKeyHex.toByteArray(),
            )
        if (res.exit != 0) {
            throw RuntimeException("secret-tool store failed (exit=${res.exit}): ${res.stderr.trim()}")
        }
        return IdentitySecret.Keychain(backend = BACKEND_ID, service = SERVICE_ATTR, account = pubKeyHex)
    }

    override fun resolve(secret: IdentitySecret): String {
        require(secret is IdentitySecret.Keychain)
        val res = runProc(SECRET_TOOL_BIN, "lookup", "service", secret.service, "account", secret.account)
        if (res.exit != 0 || res.stdout.isBlank()) {
            throw RuntimeException("secret-tool lookup failed (exit=${res.exit}): ${res.stderr.trim()}")
        }
        return res.stdout.trim()
    }

    override fun delete(secret: IdentitySecret) {
        require(secret is IdentitySecret.Keychain)
        runProc(SECRET_TOOL_BIN, "clear", "service", secret.service, "account", secret.account)
    }

    private fun which(cmd: String): File? {
        val path = System.getenv("PATH") ?: return null
        for (dir in path.split(File.pathSeparator)) {
            val f = File(dir, cmd)
            if (f.canExecute()) return f
        }
        return null
    }
}

// ---------------------------------------------------------------------------
// NIP-49 passphrase-encrypted backend — uses quartz's Nip49 (scrypt+XChaCha20).
// ---------------------------------------------------------------------------

internal class NcryptsecBackend(
    private val passphrase: PassphraseProvider,
) : SecretBackend {
    override val name: String = "ncryptsec"

    override fun isAvailable(): Boolean = true

    override fun store(
        pubKeyHex: String,
        privKeyHex: String,
    ): IdentitySecret {
        val pw = passphrase.read(prompt = "Passphrase for new identity", confirm = true)
        val blob = Nip49().encrypt(privKeyHex, pw)
        return IdentitySecret.Ncryptsec(ncryptsec = blob)
    }

    override fun resolve(secret: IdentitySecret): String {
        require(secret is IdentitySecret.Ncryptsec)
        val pw = passphrase.read(prompt = "Passphrase to unlock identity", confirm = false)
        return try {
            Nip49().decrypt(secret.ncryptsec, pw)
        } catch (e: Exception) {
            throw RuntimeException("NIP-49 decrypt failed — wrong passphrase?", e)
        }
    }

    override fun delete(secret: IdentitySecret) {
        // Nothing external to forget; the blob is inside identity.json and
        // goes away when the file is removed by the caller.
    }
}

// ---------------------------------------------------------------------------
// Plaintext backend — dev escape hatch, equivalent to the old pre-hardening
// behaviour (but still written via SecureFileIO, so 0600 on disk).
// ---------------------------------------------------------------------------

internal object PlaintextBackend : SecretBackend {
    override val name: String = "plaintext"

    override fun isAvailable(): Boolean = true

    override fun store(
        pubKeyHex: String,
        privKeyHex: String,
    ): IdentitySecret = IdentitySecret.Plaintext(privKeyHex = privKeyHex)

    override fun resolve(secret: IdentitySecret): String {
        require(secret is IdentitySecret.Plaintext)
        return secret.privKeyHex
    }

    override fun delete(secret: IdentitySecret) {
        // Nothing external.
    }
}

// ---------------------------------------------------------------------------
// Process helper — shared by the keychain backends.
// ---------------------------------------------------------------------------

internal data class ProcResult(
    val exit: Int,
    val stdout: String,
    val stderr: String,
)

internal fun runProc(
    vararg argv: String,
    stdin: ByteArray? = null,
    timeoutSecs: Long = 15,
): ProcResult {
    val pb = ProcessBuilder(*argv).redirectErrorStream(false)
    val proc = pb.start()
    if (stdin != null) {
        proc.outputStream.use { it.write(stdin) }
    } else {
        proc.outputStream.close()
    }
    val out = proc.inputStream.bufferedReader().readText()
    val err = proc.errorStream.bufferedReader().readText()
    val finished = proc.waitFor(timeoutSecs, TimeUnit.SECONDS)
    if (!finished) {
        proc.destroyForcibly()
        throw RuntimeException("timed out after ${timeoutSecs}s: ${argv.joinToString(" ")}")
    }
    return ProcResult(proc.exitValue(), out, err)
}
