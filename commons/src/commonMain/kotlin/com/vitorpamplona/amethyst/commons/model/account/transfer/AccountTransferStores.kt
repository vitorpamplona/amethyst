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
            // UI settings, Tor, OTS, Namecoin, Buzz workspaces/stars/attestations,
            // relay-group deletions — everything on `Context.sharedPreferencesDataStore`.
            "shared_settings",
            "favorite_apps",
            "browser_history",
            "napplet_permissions",
            "napplet_storage",
            "napplet_network",
            "weburl_network",
            // Per-relay NIP-42 AUTH decisions.
            "relay_auth",
            // Connected apps that pair with Amethyst as a NIP-46 signer. The
            // bunker identity itself deliberately stays behind
            // (AccountTransferKeys.NIP46_DEVICE_IDENTITY), so these entries only
            // become live again once the user re-pairs from the new phone.
            "nip46_clients",
        )

    /**
     * Per-app-coordinate signer permission stores, named
     * `nsp_<hash>.preferences_pb`. Discovered by prefix because the hash is
     * derived from the app coordinate at runtime and cannot be listed here.
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
     * Plain files, by path relative to the app's files dir.
     *
     * Scheduled posts reference uploaded media by local path, so a row can
     * arrive on the new phone pointing at a file that is not there. It travels
     * anyway: losing a queued post silently is worse than a post that reports a
     * missing attachment, and text-only posts — the common case — are unaffected.
     */
    val FILES = listOf("scheduled_posts.json")
}
