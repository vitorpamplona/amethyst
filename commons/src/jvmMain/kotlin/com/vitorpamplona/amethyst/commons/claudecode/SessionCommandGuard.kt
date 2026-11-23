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

import com.vitorpamplona.agents.CommandGuard

/**
 * The shell commands a guarded session (a nostr checkout, a workstream) may not run, checked
 * before each one runs ([CommandGuard]): merging, closing or approving a pull request or
 * proposal, writes through `gh api` (anything but a plain GET), changing or deleting the
 * repository, releases, publishing a nostr repository's announcement or state, and turning
 * the push guard ([PushGuard]) off.
 *
 * It reads the command line as a shell would split it: chains, pipes, subshells, `sh -c`
 * scripts, wrappers (`env`, `command`, `timeout`), leading variable assignments and full paths.
 * It is a check on what the agent types, not a sandbox: a script file it wrote, an alias or a
 * program of its own still runs whatever it likes. Prevents accidents, not a determined agent.
 */
object SessionCommandGuard : CommandGuard {
    override fun refusal(command: String): String? = refusal(command, depth = 0)

    private fun refusal(
        command: String,
        depth: Int,
    ): String? {
        if (depth > MAX_NESTING) return "Amethyst can't check a command nested this deep; run it in simpler steps."
        for (words in segments(command)) {
            segmentRefusal(words, depth)?.let { return it }
        }
        return null
    }

    private const val MAX_NESTING = 4

    private fun segmentRefusal(
        all: List<String>,
        depth: Int,
    ): String? {
        all.firstOrNull { it.contains("GIT_CONFIG") && !it.contains("\$GIT_CONFIG") && !it.contains("\${GIT_CONFIG") }?.let {
            return "The push guard runs from the session's GIT_CONFIG_* variables; this session may not change them ($it)."
        }
        // A command substitution inside a quoted word is a command of its own.
        all.firstOrNull { it.contains("\$(") || it.contains('`') }?.let { word ->
            refusal(word.replace("\$(", ";").replace('`', ';'), depth + 1)?.let { return it }
        }
        var words = all
        while (true) {
            val first = words.firstOrNull() ?: return null
            val name = first.substringAfterLast('/')
            words =
                when {
                    ASSIGNMENT.matches(first) -> words.drop(1)
                    name == "env" -> {
                        var i = 1
                        while (i < words.size && (words[i].startsWith("-") || ASSIGNMENT.matches(words[i]))) {
                            val option = words[i]
                            if (option == "-" || option == "-i" || option == "--ignore-environment") {
                                return "env -i drops the push guard's configuration; this session may not run it."
                            }
                            if (option == "-S" || option == "--split-string") {
                                return refusal(words.drop(i + 1).joinToString(" "), depth + 1)
                            }
                            i += if (option == "-u" || option == "--unset" || option == "-C" || option == "--chdir") 2 else 1
                        }
                        words.drop(i)
                    }
                    name in WRAPPERS -> words.drop(1).dropWhile { it.startsWith("-") || it.toDoubleOrNull() != null }
                    name in SHELLS -> {
                        val script = words.indexOfFirst { it.startsWith("-") && !it.startsWith("--") && 'c' in it }
                        return if (script >= 0 && script + 1 < words.size) refusal(words[script + 1], depth + 1) else null
                    }
                    name == "eval" -> return refusal(words.drop(1).joinToString(" "), depth + 1)
                    name == "git" -> return gitRefusal(words.drop(1))
                    name == "gh" -> return ghRefusal(words.drop(1))
                    name == "ngit" -> return ngitRefusal(words.drop(1))
                    else -> return null
                }
        }
    }

    private val ASSIGNMENT = Regex("[A-Za-z_][A-Za-z0-9_]*=.*", RegexOption.DOT_MATCHES_ALL)

    /** Commands that run the command after them (and their own options). */
    private val WRAPPERS = setOf("command", "builtin", "exec", "nohup", "time", "nice", "timeout", "xargs", "sudo", "stdbuf", "caffeinate", "then", "do", "else", "if", "while", "until", "!", "{")

    private val SHELLS = setOf("sh", "bash", "zsh", "dash", "ksh", "fish")

    private fun gitRefusal(args: List<String>): String? {
        var i = 0
        while (i < args.size && args[i].startsWith("-")) {
            val option = args[i]
            val config =
                when {
                    option == "-c" || option == "--config-env" -> args.getOrNull(++i)
                    option.startsWith("--config-env=") -> option.removePrefix("--config-env=")
                    option.startsWith("-c") && !option.startsWith("--") -> option.substring(2)
                    else -> null
                }
            if (config != null) {
                val key = config.substringBefore('=').lowercase()
                if (key.startsWith("hook.") || key == "core.hookspath") {
                    return "git -c $key changes which hooks run, and so turns off the push guard; this session may not."
                }
                if (config.contains("--no-v")) return "An alias that skips the push guard (--no-verify) may not run in this session."
            }
            if (option in GIT_OPTIONS_WITH_VALUE) i++
            i++
        }
        if (args.getOrNull(i) == "push") {
            args.drop(i + 1).firstOrNull { it.length >= NO_VERIFY_SHORTEST && "--no-verify".startsWith(it) }?.let {
                return "git push $it skips the push guard; push without it, and leave what it refuses to the user."
            }
        }
        return null
    }

    private val GIT_OPTIONS_WITH_VALUE = setOf("-C", "--git-dir", "--work-tree", "--namespace", "--super-prefix")

    /** `--no-v`: shorter, git can't tell it from `--no-verbose`'s kin and refuses it itself. */
    private const val NO_VERIFY_SHORTEST = 6

    /** The first two words that aren't options (the command and subcommand), skipping the values of [valued] options. */
    private fun commandWords(
        args: List<String>,
        valued: Set<String>,
    ): List<String> {
        val out = ArrayList<String>(2)
        var i = 0
        while (i < args.size && out.size < 2) {
            val word = args[i++]
            if (word.startsWith("-")) {
                if (word in valued) i++
            } else {
                out.add(word)
            }
        }
        return out
    }

    private fun ghRefusal(args: List<String>): String? {
        val words = commandWords(args, setOf("-R", "--repo"))
        val command = words.getOrNull(0)
        val sub = words.getOrNull(1)
        return when {
            command == "api" -> ghApiRefusal(args.drop(args.indexOf("api") + 1))
            command == "release" -> "Releases are the user's to publish."
            command == "pr" && sub in setOf("merge", "review", "close") -> "gh pr $sub is the user's to do from the app."
            command == "pr" && sub == "edit" && args.any { it == "-B" || it == "--base" || it.startsWith("--base=") || (it.startsWith("-B") && !it.startsWith("--")) } ->
                "Changing a pull request's base branch is the user's to do."
            command == "repo" && sub in setOf("edit", "delete", "rename", "archive", "unarchive", "sync") -> "gh repo $sub changes the repository; that is the user's to do."
            else -> null
        }
    }

    /** Only a plain read: no fields or body, and no method but GET. */
    private fun ghApiRefusal(args: List<String>): String? {
        val write = "gh api may only read here (GET, no -f/-F/--input); writes are the user's to do."
        var i = 0
        while (i < args.size) {
            val arg = args[i++]
            when {
                arg in setOf("-f", "-F", "--field", "--raw-field", "--input") -> return write
                arg.startsWith("--field=") || arg.startsWith("--raw-field=") || arg.startsWith("--input=") -> return write
                (arg.startsWith("-f") || arg.startsWith("-F")) && !arg.startsWith("--") -> return write
                arg == "-X" || arg == "--method" -> if (!args.getOrNull(i++).equals("GET", ignoreCase = true)) return write
                arg.startsWith("--method=") -> if (!arg.removePrefix("--method=").equals("GET", ignoreCase = true)) return write
                arg.startsWith("-X") -> if (!arg.substring(2).equals("GET", ignoreCase = true)) return write
                arg in GH_API_OPTIONS_WITH_VALUE -> i++
            }
        }
        return null
    }

    private val GH_API_OPTIONS_WITH_VALUE = setOf("-H", "--header", "-q", "--jq", "-t", "--template", "--hostname", "--cache", "-p", "--preview")

    private fun ngitRefusal(args: List<String>): String? {
        val words = commandWords(args, setOf("-n", "--nsec", "--nsec-file", "--nbunksec", "--nbunksec-file", "--signer", "--repo"))
        val command = words.getOrNull(0)
        val sub = words.getOrNull(1)
        return when {
            command in setOf("merge", "init", "sync", "release", "nsite", "container", "oci") -> "ngit $command publishes for the repository; that is the user's to do."
            command == "repo" && sub != null && sub != "help" -> "ngit repo $sub publishes the repository's announcement; that is the user's to do."
            command == "pr" && sub in setOf("merge", "close", "applied") -> "ngit pr $sub is the user's to do from the app."
            command == "account" && sub == "export" -> "Exporting the user's keys is never the agent's to do."
            else -> null
        }
    }

    /**
     * [command] split as a shell would into simple commands (at `;`, `&`, `|`, newlines,
     * parentheses, backquotes and `$(`), each a list of words with quotes and escapes removed.
     */
    internal fun segments(command: String): List<List<String>> {
        val segments = ArrayList<MutableList<String>>()
        segments.add(ArrayList())
        val word = StringBuilder()
        var inWord = false

        fun endWord() {
            if (inWord) segments.last().add(word.toString())
            word.setLength(0)
            inWord = false
        }

        fun endSegment() {
            endWord()
            if (segments.last().isNotEmpty()) segments.add(ArrayList())
        }
        val n = command.length
        var i = 0
        while (i < n) {
            val c = command[i]
            when {
                c == '\\' && i + 1 < n -> {
                    if (command[i + 1] != '\n') word.append(command[i + 1])
                    inWord = true
                    i++
                }
                c == '\'' -> {
                    val end = command.indexOf('\'', i + 1).let { if (it < 0) n else it }
                    word.append(command, i + 1, end)
                    inWord = true
                    i = end
                }
                c == '"' -> {
                    var j = i + 1
                    while (j < n && command[j] != '"') {
                        if (command[j] == '\\' && j + 1 < n && command[j + 1] in "\"\\$`") j++
                        word.append(command[j])
                        j++
                    }
                    inWord = true
                    i = j
                }
                c == '$' && i + 1 < n && command[i + 1] == '(' && !inWord -> {
                    endSegment()
                    i++
                }
                c == '\n' || c in SEPARATORS -> endSegment()
                c.isWhitespace() -> endWord()
                else -> {
                    word.append(c)
                    inWord = true
                }
            }
            i++
        }
        endWord()
        return segments.filter { it.isNotEmpty() }
    }

    private const val SEPARATORS = ";&|()`"
}
