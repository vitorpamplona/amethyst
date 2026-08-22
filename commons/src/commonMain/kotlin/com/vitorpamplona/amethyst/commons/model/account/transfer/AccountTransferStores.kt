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
package com.vitorpamplona.amethyst.commons.model.account.transfer

/**
 * The app-wide stores a transfer file carries, beyond the per-account
 * preference files.
 *
 * Listed in one place, and by name, because these are the stores a reader has
 * to check when asking "does X follow me to a new phone?". Anything not named
 * here does not travel — so adding a store to the app means deciding, once,
 * whether it belongs on this list.
 */
object AccountTransferStores {
    /**
     * Preference DataStores, by the name passed to `preferencesDataStore(name=)`.
     * Each maps to `datastore/<name>.preferences_pb` under the files dir and is
     * carried as raw bytes — see [AccountTransferBundle.files] for why.
     */
    val DATA_STORES =
        listOf(
            "favorite_apps",
            "browser_history",
        )

    /**
     * Stores whose restore needs the user to say so explicitly, because they
     * decide who is trusted or how the device reaches the network: which apps
     * may sign with the key, which relays it authenticates to, what a napplet
     * may do, and whether traffic goes over Tor.
     *
     * Kept separate from [DATA_STORES] because restoring them is not the same
     * kind of act as restoring settings. A transfer file is untrusted input — a
     * user can be talked into importing one someone else made — and these stores
     * are keyed by app coordinate, relay URL or account pubkey rather than being
     * scoped to the bundle's own accounts. Restoring them from a hostile bundle
     * would silently pre-authorize an attacker's app to sign with the victim's
     * key, or pre-approve NIP-42 AUTH to a relay that then deanonymizes them.
     *
     * They still travel; the import just asks first. See
     * `AppWideStoreTransfer.importFiles`.
     */
    val GUARDED_STORES =
        listOf(
            // Per-relay NIP-42 AUTH decisions.
            "relay_auth",
            // Connected apps paired with Amethyst as a NIP-46 signer. The bunker
            // identity itself deliberately stays behind
            // (AccountTransferKeys.NIP46_DEVICE_IDENTITY), so these only become
            // live again once the user re-pairs from the new phone.
            "nip46_clients",
            // Per-applet capability grants, keyed by account pubkey — and npubs
            // are public, so a bundle can name any victim.
            "napplet_permissions",
            // Not a permission, but the same kind of decision: this file holds the
            // Tor block that TorSharedPreferences reads (torType, the external
            // SOCKS port, and the per-purpose "via Tor" switches). Restoring it
            // from a bundle could turn Tor off, or point it at a port of the
            // bundle author's choosing, and deanonymize the user on next launch.
            // It also carries theme and language, which therefore need the opt-in
            // too — a cost worth paying for a safe default.
            "shared_settings",
            // Per-origin "use Tor for this host" decisions.
            "napplet_network",
            "weburl_network",
            // A napplet's own sandboxed storage: an import can plant values inside
            // an installed applet's namespace, and what an applet trusts from its
            // own storage is the applet's business, not the bundle's.
            "napplet_storage",
        )

    /**
     * Per-app-coordinate signer permission stores, named
     * `nsp_<hash>.preferences_pb`. Discovered by prefix because the hash is
     * derived from the app coordinate at runtime and cannot be listed here.
     *
     * A permission store in the sense of [GUARDED_STORES] — these hold the
     * policy deciding whether an app may sign with the user's key.
     */
    const val SIGNER_PERMISSION_PREFIX = "nsp_"

    /** App-wide SharedPreferences files, by file name (no `.xml`). */
    val PREFERENCE_FILES =
        listOf(
            // Global master switch for the always-on notification service.
            "amethyst_global_settings",
            "amethyst_calendar_reminders",
            "amethyst_calendar_reminder_prefs",
            "chess_dismissed_games",
        )

    /** The directory, under the files dir, holding every DataStore file. */
    const val DATA_STORE_DIR = "datastore"

    /** The on-disk suffix DataStore appends to a store name. */
    const val DATA_STORE_SUFFIX = ".preferences_pb"

    /** Path, relative to the files dir, of the DataStore backing [name]. */
    fun dataStorePath(name: String) = "$DATA_STORE_DIR/$name$DATA_STORE_SUFFIX"

    /**
     * Queued posts. Guarded, not plain data: each row carries a pre-signed event
     * and a relay list, and the worker publishes them on the account's behalf, so
     * a bundle that plants rows makes the device talk to hosts of its choosing.
     */
    const val SCHEDULED_POSTS = "scheduled_posts.json"

    /**
     * Plain files, by path relative to the app's files dir.
     *
     * Scheduled posts reference uploaded media by local path, so a row can
     * arrive on the new phone pointing at a file that is not there. It travels
     * anyway: losing a queued post silently is worse than a post that reports a
     * missing attachment, and text-only posts — the common case — are unaffected.
     */
    val FILES = listOf(SCHEDULED_POSTS)

    /**
     * True when a bundle is allowed to write to [path] (relative to the files
     * dir).
     *
     * An allow-list, not a traversal check. A bundle is untrusted input — it can
     * be edited, or simply come from someone else — and a path check that only
     * blocks `..` still lets a crafted file overwrite anything else under the
     * files dir, including an account's `.secrets` DataStore. Only the stores
     * this feature actually exports can be written back.
     */
    fun isImportableFile(path: String): Boolean = storeNameOf(path) != null

    /** True when [path] is one of the guarded stores described on [GUARDED_STORES]. */
    fun isGuardedFile(path: String): Boolean {
        val store = storeNameOf(path) ?: return false
        return store == SCHEDULED_POSTS || store in GUARDED_STORES || store.startsWith(SIGNER_PERMISSION_PREFIX)
    }

    /**
     * The store [path] names, or null when a bundle is not allowed to write it.
     * Returns the plain file name for entries in [FILES].
     */
    private fun storeNameOf(path: String): String? {
        if (path in FILES) return path

        val name = path.removePrefix("$DATA_STORE_DIR/")
        if (name == path || name.contains('/')) return null
        if (!name.endsWith(DATA_STORE_SUFFIX)) return null

        val store = name.removeSuffix(DATA_STORE_SUFFIX)
        val known = store in DATA_STORES || store in GUARDED_STORES || store.startsWith(SIGNER_PERMISSION_PREFIX)
        return if (known) store else null
    }

    /**
     * True when a bundle is allowed to write the SharedPreferences file [name].
     *
     * Same reasoning as [isImportableFile]. In particular this keeps a bundle
     * from naming `secret_keeper`, which would write plaintext into the
     * EncryptedSharedPreferences file that holds the account registry.
     */
    fun isImportablePreferenceFile(name: String): Boolean = name in PREFERENCE_FILES
}
