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
package com.vitorpamplona.quartz.nip55AndroidSigner

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.vitorpamplona.quartz.nip55AndroidSigner.api.CommandType
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.Permission
import com.vitorpamplona.quartz.nip55AndroidSigner.client.getExternalSignersInstalled
import com.vitorpamplona.quartz.nip55AndroidSigner.client.isExternalSignerInstalled
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * NIP-55 signs by handing an Intent to a separate app, which is Android-shaped
 * by specification. It still lives in quartz's shared JVM/Android source set,
 * because a desktop build has to be able to *answer the question* — and the
 * answer has to be no.
 *
 * That matters concretely: the login screen offers the external-signer button
 * only when one is installed. A JVM that claimed one was would show a button
 * that launches nothing.
 */
class NoExternalSignerOnJvmTest {
    private val context = JvmTestContext()

    @Test
    fun `no external signer is installed on a desktop`() {
        assertFalse(isExternalSignerInstalled(context))
        assertTrue(getExternalSignersInstalled(context).isEmpty())
    }

    @Test
    fun `the protocol's own data still works, because it is not Android-shaped`() {
        // Permissions and command types are wire values, not IPC. An account
        // saved on a phone carries them into the desktop's settings file, so
        // they have to round-trip here even though no signer can be reached.
        val json = Permission(CommandType.SIGN_EVENT, 1).toJson()
        val back = Permission.fromJson(json)
        assertEquals(CommandType.SIGN_EVENT, back.type)
        assertEquals(1, back.kind)
    }

    @Test
    fun `command types parse from their wire codes`() {
        assertEquals(CommandType.NIP44_DECRYPT, CommandType.parse("nip44_decrypt"))
        assertEquals(CommandType.GET_PUBLIC_KEY, CommandType.parse("get_public_key"))
        assertNotNull(CommandType.parse("sign_event"))
        // An unknown code is null rather than a guess: a signer speaking a
        // newer dialect must not be silently mapped onto the wrong command.
        assertEquals(null, CommandType.parse("teleport_event"))
    }

    /** Minimal Context; only the package manager is exercised. */
    private class JvmTestContext : Context() {
        override fun getPackageName() = "com.vitorpamplona.amethyst"

        override fun getResources(): Resources? = null

        override fun getString(resId: Int) = ""

        override fun getString(
            resId: Int,
            vararg formatArgs: Any?,
        ) = ""

        override fun getCacheDir(): File? = null

        override fun getFilesDir(): File? = null

        override fun getExternalCacheDir(): File? = null

        override fun getExternalFilesDir(type: String?): File? = null

        override fun getSharedPreferences(
            name: String?,
            mode: Int,
        ): SharedPreferences? = null

        override fun getContentResolver(): ContentResolver? = null
    }
}
