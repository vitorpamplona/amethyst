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
 * proposal, writes through `gh api` (only reads pass), changing or deleting the repository, its
 * workflows, secrets and variables, releases, publishing a nostr repository's announcement or
 * state, and turning the push guard ([PushGuard]) off.
 *
 * It parses the command line as a shell would: chains, pipes, subshells, `$(…)` and backquotes
 * (in plain or double-quoted text, not single-quoted or `$'…'`), here-strings, here-documents (their text is data; a
 * substitution in an unquoted one runs), `sh -c` scripts, wrappers (`env`, `command`,
 * `timeout`), leading variable assignments and full paths. It is a check on what the agent
 * types, not a sandbox: a script file it wrote, an alias or a program of its own still runs
 * whatever it likes. Prevents accidents, not a determined agent.
 */
object SessionCommandGuard : CommandGuard {
    override fun refusal(command: String): String? = refusal(command, depth = 0)

    private fun refusal(
        command: String,
        depth: Int,
    ): String? {
        val commands = if (depth > MAX_NESTING) null else commands(command)
        if (commands == null) return "Amethyst can't check a command nested this deep; run it in simpler steps."
        for (words in commands) {
            segmentRefusal(words, depth)?.let { return it }
        }
        return null
    }

    private const val MAX_NESTING = 4

    private fun segmentRefusal(
        all: List<String>,
        depth: Int,
    ): String? {
        var words = all
        while (true) {
            val first = words.firstOrNull() ?: return null
            val name = first.substringAfterLast('/')
            words =
                when {
                    ASSIGNMENT.matches(first) -> {
                        if (first.startsWith(GIT_CONFIG)) return changesGuardConfig(first)
                        words.drop(1)
                    }
                    name == "env" -> {
                        var i = 1
                        while (i < words.size && (words[i].startsWith("-") || ASSIGNMENT.matches(words[i]))) {
                            val option = words[i]
                            when {
                                option == "-" || option == "-i" || option == "--ignore-environment" ->
                                    return "env -i drops the push guard's configuration; this session may not run it."
                                option == "-S" || option == "--split-string" -> return refusal(words.drop(i + 1).joinToString(" "), depth + 1)
                                option == "-u" || option == "--unset" -> {
                                    if (words.getOrNull(i + 1).orEmpty().startsWith(GIT_CONFIG)) return changesGuardConfig(words[i + 1])
                                    i++
                                }
                                option.startsWith("--unset=") -> if (option.removePrefix("--unset=").startsWith(GIT_CONFIG)) return changesGuardConfig(option)
                                option.startsWith("-u") && !option.startsWith("--") -> if (option.substring(2).startsWith(GIT_CONFIG)) return changesGuardConfig(option)
                                option == "-C" || option == "--chdir" -> i++
                                option.startsWith(GIT_CONFIG) -> return changesGuardConfig(option)
                            }
                            i++
                        }
                        words.drop(i)
                    }
                    name == "unset" -> return words.drop(1).firstOrNull { it.startsWith(GIT_CONFIG) }?.let(::changesGuardConfig)
                    name in DECLARERS -> {
                        val args = words.drop(1)
                        // `export -n` / `declare +x` stop passing a variable on, as unsetting it would.
                        val unexports = args.any { (it.startsWith("-") && 'n' in it) || it.startsWith("+") }
                        return args.firstOrNull { it.startsWith(GIT_CONFIG) && ('=' in it || unexports) }?.let(::changesGuardConfig)
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

    private const val GIT_CONFIG = "GIT_CONFIG"

    private fun changesGuardConfig(word: String) = "The push guard runs from the session's GIT_CONFIG_* variables; this session may not change them ($word)."

    private val ASSIGNMENT = Regex("[A-Za-z_][A-Za-z0-9_]*=.*", RegexOption.DOT_MATCHES_ALL)

    /** Builtins that set or export variables. */
    private val DECLARERS = setOf("export", "declare", "typeset", "local", "readonly")

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
            command == "secret" || command == "variable" -> "The repository's ${command}s are the user's to manage."
            command == "pr" && sub in setOf("merge", "review", "close") -> "gh pr $sub is the user's to do from the app."
            command == "pr" && sub == "edit" && args.any { it == "-B" || it == "--base" || it.startsWith("--base=") || (it.startsWith("-B") && !it.startsWith("--")) } ->
                "Changing a pull request's base branch is the user's to do."
            command == "repo" && sub in setOf("edit", "delete", "rename", "archive", "unarchive", "sync", "create", "fork") -> "gh repo $sub is the user's to do."
            command == "workflow" && sub in setOf("run", "enable", "disable") -> "gh workflow $sub is the user's to do."
            command == "run" && sub in setOf("cancel", "rerun", "delete") -> "gh run $sub is the user's to do."
            else -> null
        }
    }

    /**
     * Only reads: no body (`--input`), fields only with an explicit GET (they are then query
     * parameters), and a GraphQL query that is not a mutation and is written out, not read from a file.
     */
    private fun ghApiRefusal(args: List<String>): String? {
        val write = "gh api may only read here (GET, or a GraphQL query that isn't a mutation); writes are the user's to do."
        var method: String? = null
        var endpoint: String? = null
        val fields = ArrayList<String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i++]
            when {
                arg == "--input" || arg.startsWith("--input=") -> return write
                arg == "-X" || arg == "--method" -> method = args.getOrNull(i++).orEmpty()
                arg.startsWith("--method=") -> method = arg.removePrefix("--method=")
                arg.startsWith("-X") -> method = arg.substring(2)
                arg in setOf("-f", "-F", "--field", "--raw-field") -> fields.add(args.getOrNull(i++).orEmpty())
                arg.startsWith("--field=") || arg.startsWith("--raw-field=") -> fields.add(arg.substringAfter('='))
                (arg.startsWith("-f") || arg.startsWith("-F")) && !arg.startsWith("--") -> fields.add(arg.substring(2))
                arg in GH_API_OPTIONS_WITH_VALUE -> i++
                !arg.startsWith("-") && endpoint == null -> endpoint = arg
            }
        }
        val verb = method?.uppercase()
        if (endpoint == "graphql") {
            if (verb != null && verb != "POST" && verb != "GET") return write
            val query = fields.firstOrNull { it.startsWith("query=") }?.removePrefix("query=") ?: return write
            if (query.startsWith("@")) return "gh api graphql with a query from a file can't be checked here; write the query out."
            return if (isMutation(query)) write else null
        }
        if (fields.isNotEmpty()) return if (verb == "GET") null else write
        return if (verb == null || verb == "GET") null else write
    }

    /** Whether a GraphQL document has a mutation in it, outside its comments and strings. */
    private fun isMutation(query: String): Boolean {
        val code = query.replace(GRAPHQL_STRING, "\"\"").lines().joinToString("\n") { it.substringBefore('#') }
        return GRAPHQL_MUTATION.containsMatchIn(code)
    }

    private val GRAPHQL_STRING = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")
    private val GRAPHQL_MUTATION = Regex("\\bmutation\\b")

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
     * Every simple command [command] runs, at any depth (inside `(…)`, `$(…)`, backquotes, and
     * substitutions in unquoted here-documents), each as its words with quotes and escapes
     * removed; null when it nests too deep to check.
     */
    internal fun commands(command: String): List<List<String>>? {
        val parser = ShellParser(command)
        parser.parseAll()
        return if (parser.tooDeep) null else parser.out
    }

    private class Heredoc(
        val delimiter: String,
        val quoted: Boolean,
        val stripTabs: Boolean,
    )

    /** A shell's reading of a command line, as far as finding the commands in it needs. */
    private class ShellParser(
        private val s: String,
        val out: MutableList<List<String>> = ArrayList(),
        private var nesting: Int = 0,
    ) {
        private var i = 0
        private val pending = ArrayList<Heredoc>()
        var tooDeep = false

        fun parseAll() = parseList(null)

        /** Text where only substitutions run: an unquoted here-document's body. */
        fun parseExpansions() {
            while (i < s.length && !tooDeep) {
                when {
                    s[i] == '\\' -> i += 2
                    s.startsWith("$(", i) -> {
                        i += 2
                        parseList(')')
                    }
                    s[i] == '`' -> {
                        i++
                        parseList('`')
                    }
                    else -> i++
                }
            }
        }

        /** Commands up to [stop] (the end of a substitution or subshell), or the end of the text. */
        private fun parseList(stop: Char?) {
            if (++nesting > MAX_PARSE_NESTING) {
                tooDeep = true
                i = s.length
                return
            }
            var words = ArrayList<String>()
            val word = StringBuilder()
            var inWord = false

            fun endWord() {
                if (inWord) words.add(word.toString())
                word.setLength(0)
                inWord = false
            }

            fun endCommand() {
                endWord()
                if (words.isNotEmpty()) out.add(words)
                words = ArrayList()
            }
            while (i < s.length && !tooDeep) {
                val c = s[i]
                when {
                    stop != null && c == stop -> {
                        endCommand()
                        i++
                        nesting--
                        return
                    }
                    c == '\\' -> {
                        if (i + 1 < s.length && s[i + 1] != '\n') {
                            word.append(s[i + 1])
                            inWord = true
                        }
                        i += 2
                    }
                    c == '\'' -> {
                        val end = s.indexOf('\'', i + 1).let { if (it < 0) s.length else it }
                        word.append(s, i + 1, end)
                        inWord = true
                        i = end + 1
                    }
                    c == '"' -> {
                        i++
                        readDoubleQuoted(word)
                        inWord = true
                    }
                    s.startsWith("$(", i) -> {
                        i += 2
                        parseList(')')
                        word.append("$()")
                        inWord = true
                    }
                    c == '`' -> {
                        i++
                        parseList('`')
                        word.append("``")
                        inWord = true
                    }
                    c == '#' && !inWord -> while (i < s.length && s[i] != '\n') i++
                    s.startsWith("$'", i) -> {
                        i += 2
                        readAnsiCQuoted(word)
                        inWord = true
                    }
                    // A here-string: the next word, read as any other.
                    s.startsWith("<<<", i) -> {
                        endWord()
                        i += 3
                    }
                    s.startsWith("<<", i) -> {
                        endWord()
                        i += 2
                        readHeredocStart()
                    }
                    c == '(' -> {
                        endCommand()
                        i++
                        parseList(')')
                    }
                    c == '\n' -> {
                        endCommand()
                        i++
                        readHeredocBodies()
                    }
                    c == ')' || c in ";&|" -> {
                        endCommand()
                        i++
                    }
                    c.isWhitespace() -> {
                        endWord()
                        i++
                    }
                    else -> {
                        word.append(c)
                        inWord = true
                        i++
                    }
                }
            }
            endCommand()
            nesting--
        }

        private fun readDoubleQuoted(word: StringBuilder) {
            while (i < s.length && !tooDeep) {
                val c = s[i]
                when {
                    c == '"' -> {
                        i++
                        return
                    }
                    c == '\\' && i + 1 < s.length && s[i + 1] in "\"\\$`\n" -> {
                        if (s[i + 1] != '\n') word.append(s[i + 1])
                        i += 2
                    }
                    s.startsWith("$(", i) -> {
                        i += 2
                        parseList(')')
                        word.append("$()")
                    }
                    c == '`' -> {
                        i++
                        parseList('`')
                        word.append("``")
                    }
                    else -> {
                        word.append(c)
                        i++
                    }
                }
            }
        }

        /** `$'…'`: backslash escapes, `\'` among them, and no substitution inside. */
        private fun readAnsiCQuoted(word: StringBuilder) {
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '\'' -> {
                        i++
                        return
                    }
                    c == '\\' && i + 1 < s.length -> {
                        word.append(s[i + 1])
                        i += 2
                    }
                    else -> {
                        word.append(c)
                        i++
                    }
                }
            }
        }

        /** After `<<`: `-` to strip tabs, then the delimiter; any quoting in it makes the body plain text. */
        private fun readHeredocStart() {
            val stripTabs = i < s.length && s[i] == '-'
            if (stripTabs) i++
            while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++
            val delimiter = StringBuilder()
            var quoted = false
            while (i < s.length && !s[i].isWhitespace() && s[i] !in ";&|()<>") {
                val c = s[i]
                when (c) {
                    '\'', '"' -> {
                        quoted = true
                        val end = s.indexOf(c, i + 1).let { if (it < 0) s.length else it }
                        delimiter.append(s, i + 1, end)
                        i = end + 1
                    }
                    '\\' -> {
                        quoted = true
                        if (i + 1 < s.length) delimiter.append(s[i + 1])
                        i += 2
                    }
                    else -> {
                        delimiter.append(c)
                        i++
                    }
                }
            }
            pending.add(Heredoc(delimiter.toString(), quoted, stripTabs))
        }

        /** The bodies of the here-documents started on the line just ended, in order. */
        private fun readHeredocBodies() {
            val docs = pending.toList()
            pending.clear()
            for (doc in docs) {
                val body = StringBuilder()
                while (i < s.length) {
                    val end = s.indexOf('\n', i).let { if (it < 0) s.length else it }
                    val line = s.substring(i, end)
                    i = minOf(end + 1, s.length)
                    if ((if (doc.stripTabs) line.trimStart('\t') else line) == doc.delimiter) break
                    body.append(line).append('\n')
                }
                if (!doc.quoted) {
                    val inner = ShellParser(body.toString(), out, nesting)
                    inner.parseExpansions()
                    if (inner.tooDeep) tooDeep = true
                }
            }
        }
    }

    private const val MAX_PARSE_NESTING = 32
}
