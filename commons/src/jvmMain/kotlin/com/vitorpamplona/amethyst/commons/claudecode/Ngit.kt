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
package com.vitorpamplona.amethyst.commons.claudecode

import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.File

/**
 * Publishing a branch through the user's own `ngit` (https://gitworkshop.dev/ngit), signed by the
 * account they logged in with there, instead of the app's signer. Only the user's click runs it:
 * a Claude Code session is never allowed to (ClaudeChat.NO_PUBLISHING).
 */
object Ngit {
    /** An account `ngit account whoami` lists; [active] is the one it signs with. */
    data class Account(
        val name: String,
        val npub: String,
        val active: Boolean,
    )

    sealed interface SendResult {
        /** Published: [nevent] as ngit printed it, [eventId] its hex id when it decodes. */
        data class Published(
            val nevent: String,
            val eventId: String?,
            val updated: Boolean,
        ) : SendResult

        data class Failed(
            val reason: String,
        ) : SendResult
    }

    /** The `ngit` program: on PATH, or where its installers put it (cargo, Homebrew). */
    fun find(environment: Map<String, String> = System.getenv()): File? {
        val home = System.getProperty("user.home")
        val names = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) listOf("ngit.exe", "ngit") else listOf("ngit")
        val dirs =
            environment["PATH"].orEmpty().split(File.pathSeparator).filter { it.isNotBlank() } +
                listOf("$home/.cargo/bin", "$home/.local/bin", "/opt/homebrew/bin", "/usr/local/bin")
        return dirs.firstNotNullOfOrNull { dir -> names.map { File(dir, it) }.firstOrNull { it.isFile && it.canExecute() } }
    }

    /** The accounts ngit can sign with, read from its local cache (no network). */
    suspend fun accounts(
        dir: File,
        exe: File? = find(),
    ): List<Account> {
        exe ?: return emptyList()
        val result = withContext(Dispatchers.IO) { GitWorkspace.run(dir, listOf(exe.path, "account", "whoami", "--offline", "--json"), timeoutSeconds = 20) }
        if (result.code != 0) return emptyList()
        return parseAccounts(result.output)
    }

    /**
     * Sends the commits after [mergeBase] as a proposal to [repo] (an naddr), or as a new version
     * of [proposalId]. Non-interactive: it fails instead of asking anything.
     */
    suspend fun send(
        dir: File,
        repo: String,
        mergeBase: String,
        title: String,
        description: String,
        proposalId: String?,
        exe: File? = find(),
    ): SendResult {
        exe ?: return SendResult.Failed("ngit is not installed")
        val command =
            buildList {
                add(exe.path)
                add("--repo")
                add(repo)
                add("--json")
                add("--defaults")
                add("send")
                add("$mergeBase..HEAD")
                if (proposalId != null) {
                    add("--in-reply-to")
                    add(proposalId)
                } else {
                    add("--subject")
                    add(title)
                    add("--description")
                    add(description)
                }
            }
        // Publishing waits on relays and git servers: minutes, not seconds.
        val result = withContext(Dispatchers.IO) { GitWorkspace.run(dir, command, timeoutSeconds = 300) }
        return parseSend(result.code, result.output)
    }

    internal fun parseAccounts(output: String): List<Account> {
        val json = lastJson(output) ?: return emptyList()
        return (json["accounts"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            Account(
                name = obj.text("name").orEmpty(),
                npub = obj.text("npub") ?: return@mapNotNull null,
                active = (obj["active"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
        }
    }

    internal fun parseSend(
        code: Int,
        output: String,
    ): SendResult {
        val json = lastJson(output)
        if (json?.text("command_status") == "ok") {
            val nevent = json.text("id") ?: return SendResult.Failed(output.takeLast(2000))
            val id = (Nip19Parser.uriToRoute(nevent)?.entity as? NEvent)?.hex
            return SendResult.Published(nevent, id, updated = json.text("action") == "updated")
        }
        val reason = json?.text("error") ?: output.trim().ifEmpty { "ngit exited with code $code" }
        return SendResult.Failed(reason.takeLast(2000))
    }

    /** ngit prints progress first and its JSON document last (pretty-printed). */
    private fun lastJson(output: String): JsonObject? {
        val end = output.lastIndexOf('}')
        if (end < 0) return null
        var start = output.lastIndexOf("\n{", end)
        while (start >= -1) {
            val from = if (start < 0) 0 else start + 1
            try {
                return Json.parseToJsonElement(output.substring(from, end + 1)) as? JsonObject
            } catch (e: IllegalArgumentException) {
                if (start < 0) return null
                start = output.lastIndexOf("\n{", start - 1)
            }
        }
        return null
    }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
