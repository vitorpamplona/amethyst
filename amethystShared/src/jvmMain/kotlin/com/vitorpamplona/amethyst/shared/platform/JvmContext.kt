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
package com.vitorpamplona.amethyst.shared.platform

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.LocaleList
import com.vitorpamplona.amethyst.shared.resources.AndroidResourceTable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URLConnection
import java.util.prefs.Preferences

/**
 * The JVM implementation behind the `android.content.Context` stub.
 *
 * Shared code passes a `Context` around for resource lookup, package identity
 * and platform services. On the JVM those needs are met by process-wide
 * singletons, so a single instance suffices; it exists at all because ~235
 * call sites already take a `Context` parameter and rewriting them would be
 * churn without benefit.
 */
object JvmContext : Context() {
    private const val PACKAGE_NAME = "com.vitorpamplona.amethyst"

    /**
     * Android hands every component a Context; on the JVM there is one per
     * process, and Application/Service/Activity all resolve through whatever is
     * installed here. Installing on first touch means a caller that reaches
     * JvmContext at all cannot then hit the "no Context installed" failure.
     */
    init {
        installApplicationContext(this)
    }

    override fun getPackageName(): String = PACKAGE_NAME

    override fun getResources(): Resources = JvmResources

    override fun getString(resId: Int): String = AndroidResourceTable.getString(resId)

    override fun getString(
        resId: Int,
        vararg formatArgs: Any?,
    ): String = AndroidResourceTable.getString(resId, *formatArgs)

    // XDG-ish locations. Android hands the app private per-package dirs; the
    // desktop equivalent is a per-user application directory, and "external"
    // storage has no analogue so it maps to the same place.
    private val appDir: File by lazy {
        val home = System.getProperty("user.home") ?: "."
        File(home, ".amethyst").apply { mkdirs() }
    }

    override fun getCacheDir(): File = File(appDir, "cache").apply { mkdirs() }

    override fun getFilesDir(): File = File(appDir, "files").apply { mkdirs() }

    override fun getExternalCacheDir(): File = cacheDir

    override fun getExternalFilesDir(type: String?): File = filesDir

    override fun getSharedPreferences(
        name: String,
        mode: Int,
    ): SharedPreferences = JvmSharedPreferences.of(name)

    override fun getContentResolver(): ContentResolver = JvmContentResolver
}

/**
 * Backed by java.util.prefs, which is the platform's own per-user key/value
 * store on all three desktop OSes, so preferences survive reinstall the way
 * SharedPreferences do rather than living in a file we would have to manage.
 */
class JvmSharedPreferences private constructor(
    private val node: Preferences,
) : SharedPreferences {
    companion object {
        private val cache = java.util.concurrent.ConcurrentHashMap<String, JvmSharedPreferences>()

        fun of(name: String): JvmSharedPreferences =
            cache.getOrPut(name) {
                JvmSharedPreferences(Preferences.userRoot().node("com/vitorpamplona/amethyst/$name"))
            }
    }

    override fun getAll(): Map<String, Any?> = node.keys().associateWith { node.get(it, null) }

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = node.get(key, defValue)

    override fun getStringSet(
        key: String,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? = node.get(key, null)?.split("\u0000")?.toMutableSet() ?: defValues

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = node.getInt(key, defValue)

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = node.getLong(key, defValue)

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = node.getFloat(key, defValue)

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = node.getBoolean(key, defValue)

    override fun contains(key: String): Boolean = node.get(key, null) != null

    override fun edit(): SharedPreferences.Editor = Editor(node)

    private class Editor(
        private val node: Preferences,
    ) : SharedPreferences.Editor {
        override fun putString(
            key: String,
            value: String?,
        ) = apply { if (value == null) node.remove(key) else node.put(key, value) }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ) = apply { if (values == null) node.remove(key) else node.put(key, values.joinToString("\u0000")) }

        override fun putInt(
            key: String,
            value: Int,
        ) = apply { node.putInt(key, value) }

        override fun putLong(
            key: String,
            value: Long,
        ) = apply { node.putLong(key, value) }

        override fun putFloat(
            key: String,
            value: Float,
        ) = apply { node.putFloat(key, value) }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ) = apply { node.putBoolean(key, value) }

        override fun remove(key: String) = apply { node.remove(key) }

        override fun clear() = apply { node.clear() }

        override fun commit(): Boolean {
            node.flush()
            return true
        }

        override fun apply() {
            node.flush()
        }
    }
}

/** Resolves the `file:` URIs the desktop actually produces; other schemes are absent. */
object JvmContentResolver : ContentResolver() {
    override fun openInputStream(uri: Uri): InputStream? = fileFor(uri)?.inputStream()

    override fun openOutputStream(uri: Uri): OutputStream? = fileFor(uri)?.outputStream()

    override fun getType(uri: Uri): String? = URLConnection.guessContentTypeFromName(uri.toString())

    private fun fileFor(uri: Uri): File? = uri.path?.let(::File)?.takeIf { uri.scheme == null || uri.scheme == "file" }
}

object JvmResources : Resources() {
    override fun getString(resId: Int): String = AndroidResourceTable.getString(resId)

    override fun getString(
        resId: Int,
        vararg formatArgs: Any?,
    ): String = AndroidResourceTable.getString(resId, *formatArgs)

    override fun getQuantityString(
        resId: Int,
        quantity: Int,
    ): String = AndroidResourceTable.getQuantityString(resId, quantity)

    override fun getQuantityString(
        resId: Int,
        quantity: Int,
        vararg formatArgs: Any?,
    ): String = AndroidResourceTable.getQuantityString(resId, quantity, *formatArgs)

    override fun getConfiguration(): Configuration = JvmConfiguration
}

object JvmConfiguration : Configuration() {
    // Rebuilt on read rather than cached: the locale can change at runtime and
    // callers reach for it precisely when they are about to format something.
    override fun getLocales(): LocaleList = LocaleList(AndroidResourceTable.locale)
}
