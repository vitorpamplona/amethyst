/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import java.nio.file.FileAlreadyExistsException
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory

enum class NetworkType {
    MAINNET,
    TESTNET,
    STAGENET,
}

object WalletManager {
    init {
        System.loadLibrary("monerujo")
    }

    fun openWallet(
        name: String,
        password: String,
        netType: NetworkType = getNetworkType(),
    ): Wallet {
        val path = Path(getWalletDir(), name).toString()
        val handle = openWalletJ(path, password, netType.ordinal)
        return Wallet(handle)
    }

    private external fun openWalletJ(
        path: String,
        password: String,
        netType: Int,
    ): Long

    fun createWalletFromSpendKey(
        name: String,
        password: String,
        spendKey: String,
        language: String = localeToSupportedLanguage(Locale.getDefault()),
        netType: NetworkType = getNetworkType(),
        restoreHeight: Long = 0,
    ): Wallet {
        val path = Path(getWalletDir(), name).toString()
        val handle = createWalletFromKeysJ(path, password, language, netType.ordinal, restoreHeight, "", "", spendKey)
        return Wallet(handle)
    }

    fun createWallet(
        name: String,
        password: String,
        language: String = localeToSupportedLanguage(Locale.getDefault()),
        netType: NetworkType = getNetworkType(),
    ): Wallet {
        val path = Path(getWalletDir(), name).toString()
        val handle = createWalletJ(path, password, language, netType.ordinal)
        return Wallet(handle)
    }

    private external fun createWalletJ(
        path: String,
        password: String,
        language: String,
        netType: Int,
    ): Long

    // technically, restoreHeight should be an ULong, but we're not using it (at the moment?) anyways, so let's just leave it at this for now
    private external fun createWalletFromKeysJ(
        path: String,
        password: String,
        language: String,
        netType: Int,
        restoreHeight: Long,
        address: String,
        viewKey: String,
        spendKey: String,
    ): Long

    fun close(wallet: Wallet) {
        closeJ(wallet)
    }

    private external fun closeJ(wallet: Wallet)

    fun getNetworkType(): NetworkType {
        return if (BuildConfig.FLAVOR_net == "mainnet") {
            NetworkType.MAINNET
        } else {
            NetworkType.STAGENET
        }
    }

    fun walletExists(name: String): Boolean {
        val path = Path(getWalletDir(), name).toString()
        return walletExistsJ(path)
    }

    private external fun walletExistsJ(path: String): Boolean

    fun deleteWallet(name: String): Boolean {
        if (!Path(getWalletDir(), name).deleteIfExists()) {
            return false
        }
        if (!Path(getWalletDir(), "$name.keys").deleteIfExists()) {
            return false
        }
        return true
    }

    fun deleteCache(name: String): Boolean {
        return Path(getWalletDir(), name).deleteIfExists()
    }

    const val MONERO_DIR = "monero"

    fun getWalletDir(): String {
        val context = Amethyst.instance.applicationContext
        val path = Path(context.filesDir.absolutePath, "monero")
        try {
            path.createDirectory()
        } catch (e: FileAlreadyExistsException) {
            if (!path.isDirectory()) {
                throw IllegalStateException("Monero directory is a file")
            }
        }
        return path.toString()
    }

    external fun getBlockchainHeight(): Long

    private fun localeToSupportedLanguage(locale: Locale): String {
        return if (locale.language in arrayOf("de", "es", "fr", "it", "nl", "pt", "ru", "ja", "zh")) {
            // always use the english name
            locale.getDisplayLanguage(Locale.ENGLISH)
        } else {
            "English"
        }
    }

    external fun setProxy(proxy: String): Boolean
}
