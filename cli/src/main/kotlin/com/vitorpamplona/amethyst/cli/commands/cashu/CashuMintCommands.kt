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
package com.vitorpamplona.amethyst.cli.commands.cashu

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.cli.commands.route
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpClient
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpException
import okhttp3.OkHttpClient

/**
 * `amy cashu mint <ping|info> URL` — stateless NIP-60 mint /v1/info probes.
 * No account or relays; talks straight to the mint over HTTP.
 */
object CashuMintCommands {
    val USAGE: String =
        """
        |Cashu mint probes (stateless — no account, no relays):
        |  cashu mint ping URL    /v1/info probe: name, pubkey, version, description
        |  cashu mint info URL    full /v1/info DTO (force-refreshed)
        """.trimMargin()

    suspend fun dispatch(tail: Array<String>): Int =
        route(
            name = "cashu mint",
            tail = tail,
            usage = "cashu mint <ping|info> URL",
            help = USAGE,
            routes =
                mapOf(
                    "ping" to { rest -> ping(rest) },
                    "info" to { rest -> info(rest) },
                ),
        )

    private val okhttp = OkHttpClient.Builder().build()

    private suspend fun ping(rest: Array<String>): Int {
        val args = Args(rest)
        val url = args.positional(0, "mint-url")
        args.rejectUnknown()
        return try {
            // userConfigured: the operator typed this URL on the command line.
            val dto = MintHttpClient(url, userConfigured = true) { okhttp }.info()
            Output.emit(
                mapOf(
                    "mint_url" to url,
                    "name" to dto.name,
                    "pubkey" to dto.pubkey,
                    "version" to dto.version,
                    "description" to dto.description,
                ),
            )
            0
        } catch (e: MintHttpException) {
            Output.error("mint_http_${e.code}", e.message)
        } catch (e: Exception) {
            Output.error("mint_unreachable", e.message)
        }
    }

    private suspend fun info(rest: Array<String>): Int {
        val args = Args(rest)
        val url = args.positional(0, "mint-url")
        args.rejectUnknown()
        return try {
            val dto = MintHttpClient(url, userConfigured = true) { okhttp }.info(force = true)
            Output.emit(mapOf("mint_url" to url, "mint_info" to dto))
            0
        } catch (e: MintHttpException) {
            Output.error("mint_http_${e.code}", e.message)
        } catch (e: Exception) {
            Output.error("mint_unreachable", e.message)
        }
    }
}
