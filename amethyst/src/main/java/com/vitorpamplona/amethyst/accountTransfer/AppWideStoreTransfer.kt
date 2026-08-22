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
package com.vitorpamplona.amethyst.accountTransfer

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.vitorpamplona.amethyst.commons.model.account.transfer.AccountTransferKeys
import com.vitorpamplona.amethyst.commons.model.account.transfer.AccountTransferStores
import com.vitorpamplona.amethyst.commons.model.account.transfer.AccountTransferValues
import com.vitorpamplona.amethyst.commons.model.account.transfer.TransferValue
import com.vitorpamplona.quartz.utils.Log
import java.io.File

/**
 * Collects and restores the stores that are app-wide rather than per-account:
 * UI settings, Tor, favorites, browser history, napplet grants and storage,
 * per-relay AUTH decisions, connected-app signer permissions, calendar
 * reminders, scheduled posts.
 *
 * [AccountTransferStores] is the list of what that means; this file is only the
 * mechanics.
 */
object AppWideStoreTransfer {
    /** Everything in [AccountTransferStores.FILES] and the DataStores, Base64'd. */
    fun exportFiles(context: Context): Map<String, String> {
        val filesDir = context.filesDir
        val paths =
            (AccountTransferStores.DATA_STORES + AccountTransferStores.PERMISSION_STORES)
                .map(AccountTransferStores::dataStorePath) +
                signerPermissionStorePaths(filesDir) +
                AccountTransferStores.FILES

        return paths.mapNotNull { path -> readFile(filesDir, path)?.let { path to it } }.toMap()
    }

    /**
     * The per-app-coordinate signer permission stores, discovered by prefix
     * because their names embed a hash computed at runtime.
     */
    private fun signerPermissionStorePaths(filesDir: File): List<String> {
        val dir = File(filesDir, AccountTransferStores.DATA_STORE_DIR)
        val names = dir.list() ?: return emptyList()

        return names
            .filter {
                it.startsWith(AccountTransferStores.SIGNER_PERMISSION_PREFIX) &&
                    it.endsWith(AccountTransferStores.DATA_STORE_SUFFIX)
            }.map { "${AccountTransferStores.DATA_STORE_DIR}/$it" }
    }

    private fun readFile(
        filesDir: File,
        path: String,
    ): String? {
        val file = File(filesDir, path)
        if (!file.isFile) return null

        return try {
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        } catch (e: Exception) {
            // A store that can't be read shouldn't sink the whole export — the
            // rest of the transfer is still worth having.
            Log.w("AccountTransfer") { "Skipping unreadable store $path: ${e.message}" }
            null
        }
    }

    /**
     * Writes the carried files back.
     *
     * Written via a temp file and renamed, matching how DataStore itself commits,
     * so a crash mid-import cannot leave a half-written store that fails to
     * parse on next launch. A store the running app already has open keeps
     * serving its in-memory copy until the app restarts — which is why the
     * import screen asks for one.
     */
    fun importFiles(
        context: Context,
        files: Map<String, String>,
        includePermissions: Boolean,
    ) {
        val filesDir = context.filesDir

        files.forEach { (path, encoded) ->
            // Allow-list, not a traversal check: a bundle is untrusted input, and
            // anything under the files dir it is not supposed to touch — an
            // account's .secrets DataStore, say — must be unreachable by name.
            if (!AccountTransferStores.isImportableFile(path)) {
                Log.w("AccountTransfer") { "Refusing to import an unrecognized store: $path" }
                return@forEach
            }

            // Restoring a consent record is not the same act as restoring a
            // setting: these decide which apps may sign with the user's key and
            // which relays they authenticate to, and the bundle deciding that on
            // their behalf is exactly what a hostile file would want. Off unless
            // the user said so for this import.
            if (!includePermissions && AccountTransferStores.isPermissionFile(path)) {
                Log.i("AccountTransfer") { "Skipping a permission store the user did not opt to restore: $path" }
                return@forEach
            }

            try {
                val target = File(filesDir, path)
                target.parentFile?.mkdirs()

                val temp = File(target.parentFile, "${target.name}.importing")
                temp.writeBytes(Base64.decode(encoded, Base64.NO_WRAP))
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            } catch (e: Exception) {
                Log.w("AccountTransfer") { "Could not restore store $path: ${e.message}" }
            }
        }
    }

    /** The app-wide SharedPreferences files named in [AccountTransferStores]. */
    fun exportPreferenceFiles(context: Context): Map<String, Map<String, TransferValue>> =
        AccountTransferStores.PREFERENCE_FILES
            .filter { it !in AccountTransferKeys.EXCLUDED_PREFERENCE_FILES }
            .mapNotNull { name ->
                val values = AccountTransferValues.fromPreferenceMap(context.prefs(name).all)
                if (values.isEmpty()) null else name to values
            }.toMap()

    fun importPreferenceFiles(
        context: Context,
        preferenceFiles: Map<String, Map<String, TransferValue>>,
    ) {
        preferenceFiles.forEach { (name, values) ->
            // Allow-list rather than a deny-list: naming `secret_keeper` here
            // would write plaintext into the EncryptedSharedPreferences file.
            if (!AccountTransferStores.isImportablePreferenceFile(name)) {
                Log.w("AccountTransfer") { "Refusing to import an unrecognized preference file: $name" }
                return@forEach
            }

            context.prefs(name).edit {
                values.forEach { (key, value) ->
                    when (value) {
                        is TransferValue.Str -> putString(key, value.v)
                        is TransferValue.Bool -> putBoolean(key, value.v)
                        is TransferValue.Int32 -> putInt(key, value.v)
                        is TransferValue.Int64 -> putLong(key, value.v)
                        is TransferValue.Flt -> putFloat(key, value.v)
                        is TransferValue.StrSet -> putStringSet(key, value.v.toSet())
                    }
                }
            }
        }
    }

    private fun Context.prefs(name: String) = getSharedPreferences(name, Context.MODE_PRIVATE)
}
