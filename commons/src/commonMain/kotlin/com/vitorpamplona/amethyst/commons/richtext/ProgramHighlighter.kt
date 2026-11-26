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
package com.vitorpamplona.amethyst.commons.richtext

/**
 * What [ProgramHighlighter] needs to know about a language: its keywords, how it writes comments
 * and strings. Close enough for the snippets in articles and notes; never a full grammar.
 */
class ProgramGrammar(
    val keywords: Set<String>,
    val lineComments: List<String> = listOf("//"),
    val blockComment: Pair<String, String>? = "/*" to "*/",
    val quotes: String = "\"'",
    val tripleQuotes: Boolean = false,
    /** Capitalized words are types (`String`, `Note`): true for most typed languages. */
    val capitalizedTypes: Boolean = true,
    /** `$name` and `${name}` are variables (shells, PHP). */
    val dollarVariables: Boolean = false,
    val caseInsensitiveKeywords: Boolean = false,
) {
    companion object {
        private val C_LIKE = setOf("if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "true", "false", "null", "new", "this", "class", "struct", "enum", "static", "const", "void", "public", "private", "protected", "try", "catch", "finally", "throw")

        private val KOTLIN =
            ProgramGrammar(
                C_LIKE +
                    setOf(
                        "fun",
                        "val",
                        "var",
                        "object",
                        "interface",
                        "data",
                        "sealed",
                        "open",
                        "override",
                        "abstract",
                        "internal",
                        "companion",
                        "when",
                        "in",
                        "is",
                        "as",
                        "by",
                        "init",
                        "constructor",
                        "import",
                        "package",
                        "suspend",
                        "inline",
                        "reified",
                        "typealias",
                        "lateinit",
                        "operator",
                        "infix",
                        "out",
                        "vararg",
                        "it",
                        "super",
                        "where",
                        "annotation",
                        "value",
                        "expect",
                        "actual",
                        "throws",
                    ),
                tripleQuotes = true,
            )
        private val JAVA =
            ProgramGrammar(
                C_LIKE + setOf("import", "package", "extends", "implements", "interface", "final", "abstract", "synchronized", "volatile", "instanceof", "super", "throws", "int", "long", "boolean", "double", "float", "char", "byte", "short", "var", "record"),
            )
        private val SWIFT =
            ProgramGrammar(
                C_LIKE + setOf("func", "let", "var", "import", "protocol", "extension", "guard", "in", "as", "is", "nil", "self", "Self", "init", "deinit", "inout", "async", "await", "throws", "rethrows", "some", "any", "where", "fileprivate", "open", "mutating", "override", "final", "lazy", "weak", "repeat"),
                tripleQuotes = true,
                quotes = "\"",
            )
        private val RUST =
            ProgramGrammar(
                setOf(
                    "fn",
                    "let",
                    "mut",
                    "pub",
                    "use",
                    "mod",
                    "crate",
                    "self",
                    "Self",
                    "super",
                    "struct",
                    "enum",
                    "trait",
                    "impl",
                    "for",
                    "in",
                    "if",
                    "else",
                    "match",
                    "loop",
                    "while",
                    "break",
                    "continue",
                    "return",
                    "as",
                    "ref",
                    "move",
                    "async",
                    "await",
                    "dyn",
                    "where",
                    "type",
                    "const",
                    "static",
                    "unsafe",
                    "extern",
                    "true",
                    "false",
                    "Some",
                    "None",
                    "Ok",
                    "Err",
                    "i8",
                    "i16",
                    "i32",
                    "i64",
                    "i128",
                    "u8",
                    "u16",
                    "u32",
                    "u64",
                    "u128",
                    "usize",
                    "isize",
                    "f32",
                    "f64",
                    "bool",
                    "str",
                    "char",
                ),
                quotes = "\"",
            )
        private val GO =
            ProgramGrammar(
                setOf(
                    "func",
                    "package",
                    "import",
                    "var",
                    "const",
                    "type",
                    "struct",
                    "interface",
                    "map",
                    "chan",
                    "go",
                    "defer",
                    "select",
                    "range",
                    "if",
                    "else",
                    "for",
                    "switch",
                    "case",
                    "default",
                    "break",
                    "continue",
                    "return",
                    "fallthrough",
                    "goto",
                    "nil",
                    "true",
                    "false",
                    "string",
                    "int",
                    "int64",
                    "uint64",
                    "byte",
                    "rune",
                    "bool",
                    "error",
                    "float64",
                ),
                quotes = "\"'`",
                capitalizedTypes = false,
            )
        private val C =
            ProgramGrammar(
                C_LIKE + setOf("int", "long", "short", "char", "float", "double", "unsigned", "signed", "sizeof", "typedef", "union", "extern", "inline", "auto", "register", "goto", "include", "define", "ifdef", "ifndef", "endif", "pragma", "bool", "nullptr", "namespace", "using", "template", "typename", "virtual", "override", "delete", "operator", "friend", "constexpr", "noexcept", "uint8_t", "uint32_t", "uint64_t", "size_t"),
            )
        private val CSHARP =
            ProgramGrammar(
                C_LIKE + setOf("using", "namespace", "var", "int", "long", "bool", "string", "double", "float", "decimal", "object", "interface", "abstract", "sealed", "override", "virtual", "readonly", "async", "await", "get", "set", "in", "out", "ref", "is", "as", "foreach", "base", "record", "init"),
            )
        private val JS =
            ProgramGrammar(
                C_LIKE + setOf("function", "let", "var", "const", "async", "await", "import", "export", "from", "of", "in", "typeof", "instanceof", "undefined", "yield", "extends", "super", "interface", "type", "implements", "readonly", "as", "string", "number", "boolean", "any", "unknown", "never"),
                quotes = "\"'`",
            )
        private val PYTHON =
            ProgramGrammar(
                setOf(
                    "def",
                    "class",
                    "return",
                    "if",
                    "elif",
                    "else",
                    "for",
                    "while",
                    "in",
                    "not",
                    "and",
                    "or",
                    "is",
                    "None",
                    "True",
                    "False",
                    "import",
                    "from",
                    "as",
                    "with",
                    "try",
                    "except",
                    "finally",
                    "raise",
                    "pass",
                    "break",
                    "continue",
                    "lambda",
                    "yield",
                    "global",
                    "nonlocal",
                    "async",
                    "await",
                    "self",
                    "assert",
                    "del",
                    "match",
                    "case",
                ),
                lineComments = listOf("#"),
                blockComment = null,
                tripleQuotes = true,
            )
        private val RUBY =
            ProgramGrammar(
                setOf("def", "end", "class", "module", "if", "elsif", "else", "unless", "while", "until", "for", "in", "do", "return", "yield", "begin", "rescue", "ensure", "raise", "nil", "true", "false", "self", "require", "attr_accessor", "puts", "then", "case", "when", "and", "or", "not", "lambda", "proc"),
                lineComments = listOf("#"),
                blockComment = null,
            )
        private val SHELL =
            ProgramGrammar(
                setOf("if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case", "esac", "in", "function", "return", "export", "local", "readonly", "set", "unset", "echo", "cd", "exit", "source", "sudo"),
                lineComments = listOf("#"),
                blockComment = null,
                capitalizedTypes = false,
                dollarVariables = true,
            )
        private val SQL =
            ProgramGrammar(
                setOf(
                    "select",
                    "from",
                    "where",
                    "and",
                    "or",
                    "not",
                    "insert",
                    "into",
                    "values",
                    "update",
                    "set",
                    "delete",
                    "create",
                    "table",
                    "index",
                    "on",
                    "join",
                    "left",
                    "right",
                    "inner",
                    "outer",
                    "group",
                    "by",
                    "order",
                    "having",
                    "limit",
                    "offset",
                    "as",
                    "distinct",
                    "null",
                    "is",
                    "in",
                    "like",
                    "primary",
                    "key",
                    "foreign",
                    "references",
                    "drop",
                    "alter",
                    "add",
                    "union",
                    "all",
                    "exists",
                    "case",
                    "when",
                    "then",
                    "else",
                    "end",
                    "integer",
                    "text",
                    "blob",
                    "varchar",
                    "with",
                    "asc",
                    "desc",
                    "count",
                ),
                lineComments = listOf("--"),
                capitalizedTypes = false,
                caseInsensitiveKeywords = true,
            )
        private val LUA =
            ProgramGrammar(
                setOf("function", "local", "end", "if", "then", "else", "elseif", "for", "while", "do", "repeat", "until", "return", "break", "in", "and", "or", "not", "nil", "true", "false", "goto"),
                lineComments = listOf("--"),
                blockComment = "--[[" to "]]",
                capitalizedTypes = false,
            )
        private val TOML =
            ProgramGrammar(
                setOf("true", "false"),
                lineComments = listOf("#"),
                blockComment = null,
                tripleQuotes = true,
                capitalizedTypes = false,
            )
        private val PHP = ProgramGrammar(C_LIKE + setOf("function", "echo", "namespace", "use", "foreach", "as", "array", "fn", "match", "extends", "implements", "interface", "trait"), lineComments = listOf("//", "#"), dollarVariables = true)
        private val DART = ProgramGrammar(C_LIKE + setOf("import", "final", "var", "late", "required", "async", "await", "extends", "implements", "with", "mixin", "dynamic", "void", "int", "double", "bool", "String", "Future", "Stream", "is", "as", "in"), tripleQuotes = true)
        private val SCALA = ProgramGrammar(C_LIKE + setOf("def", "val", "var", "object", "trait", "extends", "with", "import", "package", "match", "case", "implicit", "lazy", "sealed", "override", "type", "yield", "given", "using", "then"), tripleQuotes = true)

        private val BY_NAME: Map<String, ProgramGrammar> =
            buildMap {
                fun add(
                    grammar: ProgramGrammar,
                    vararg names: String,
                ) = names.forEach { put(it, grammar) }
                add(KOTLIN, "kotlin", "kt", "kts")
                add(JAVA, "java")
                add(SWIFT, "swift")
                add(RUST, "rust", "rs")
                add(GO, "go", "golang")
                add(C, "c", "h", "cpp", "c++", "cc", "cxx", "hpp", "objc", "objective-c", "zig")
                add(CSHARP, "csharp", "cs", "c#")
                add(JS, "jsx", "tsx", "mjs", "cjs")
                add(PYTHON, "python", "py", "python3")
                add(RUBY, "ruby", "rb")
                add(SHELL, "bash", "sh", "shell", "zsh", "console", "shellsession", "fish", "dockerfile", "docker", "makefile", "make")
                add(SQL, "sql", "sqlite", "postgres", "postgresql", "mysql")
                add(LUA, "lua")
                add(TOML, "toml", "ini", "conf", "properties")
                add(PHP, "php")
                add(DART, "dart")
                add(SCALA, "scala")
            }

        fun of(language: String?): ProgramGrammar? = language?.let { BY_NAME[it.lowercase()] }
    }
}

/**
 * Colors the code of a programming language by [ProgramGrammar]: comments, strings, numbers,
 * keywords, types and function calls. One pass, no backtracking: hostile input costs linear time.
 */
object ProgramHighlighter {
    fun tokenize(
        code: String,
        grammar: ProgramGrammar,
    ): List<CodeToken> {
        val tokens = ArrayList<CodeToken>()
        val n = code.length
        var i = 0
        while (i < n) {
            val c = code[i]
            val block = grammar.blockComment
            when {
                c.isWhitespace() -> i++

                block != null && code.startsWith(block.first, i) -> {
                    val close = code.indexOf(block.second, i + block.first.length)
                    val end = if (close < 0) n else close + block.second.length
                    tokens.add(CodeToken(i, end, CodeTokenType.COMMENT))
                    i = end
                }

                grammar.lineComments.any { code.startsWith(it, i) } && opensLineComment(code, i, grammar) -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                    tokens.add(CodeToken(i, end, CodeTokenType.COMMENT))
                    i = end
                }

                grammar.tripleQuotes && (code.startsWith("\"\"\"", i) || code.startsWith("'''", i)) -> {
                    val fence = code.substring(i, i + 3)
                    val close = code.indexOf(fence, i + 3)
                    val end = if (close < 0) n else close + 3
                    tokens.add(CodeToken(i, end, CodeTokenType.STRING))
                    i = end
                }

                c in grammar.quotes && !isApostrophe(code, i, c) -> {
                    val end = stringEnd(code, i, c)
                    tokens.add(CodeToken(i, end, CodeTokenType.STRING))
                    i = end
                }

                c == '$' && grammar.dollarVariables && i + 1 < n && (code[i + 1] == '{' || isWordStart(code[i + 1])) -> {
                    val end =
                        if (code[i + 1] == '{') {
                            code.indexOf('}', i).let { if (it < 0) n else it + 1 }
                        } else {
                            wordEnd(code, i + 1)
                        }
                    tokens.add(CodeToken(i, end, CodeTokenType.KEY))
                    i = end
                }

                c.isDigit() && (i == 0 || !isWordPart(code[i - 1])) -> {
                    val end = numberEnd(code, i)
                    tokens.add(CodeToken(i, end, CodeTokenType.NUMBER))
                    i = end
                }

                isWordStart(c) -> {
                    val end = wordEnd(code, i)
                    val word = code.substring(i, end)
                    val keyword = if (grammar.caseInsensitiveKeywords) word.lowercase() in grammar.keywords else word in grammar.keywords
                    val type =
                        when {
                            keyword -> CodeTokenType.KEYWORD
                            nextNonSpace(code, end) == '(' -> CodeTokenType.FUNCTION
                            grammar.capitalizedTypes && c.isUpperCase() && word.any { it.isLowerCase() } -> CodeTokenType.TYPE
                            else -> null
                        }
                    if (type != null) tokens.add(CodeToken(i, end, type))
                    i = end
                }

                else -> i++
            }
        }
        return tokens
    }

    /** `#` opens a comment at a line's start or after a space, not inside `a#b` or a shell's `$#`. */
    private fun opensLineComment(
        code: String,
        at: Int,
        grammar: ProgramGrammar,
    ): Boolean {
        if (code[at] != '#') return true
        val previous = code.getOrNull(at - 1)
        return previous == null || previous.isWhitespace() || (previous == ';' && grammar.dollarVariables)
    }

    /** Rust lifetimes (`'a`) and words like `don't` in a shell echo are not strings. */
    private fun isApostrophe(
        code: String,
        at: Int,
        quote: Char,
    ): Boolean {
        if (quote != '\'') return false
        val previous = code.getOrNull(at - 1)
        if (previous != null && previous.isLetterOrDigit()) return true
        // 'a without a closing quote on its line: a lifetime or a label.
        val close = code.indexOf('\'', at + 1)
        val newline = code.indexOf('\n', at + 1).let { if (it < 0) code.length else it }
        return close < 0 || close > newline
    }

    private fun stringEnd(
        code: String,
        from: Int,
        quote: Char,
    ): Int {
        var j = from + 1
        // A backtick string (Go, JS templates) may span lines; others end at the line's end.
        val multiline = quote == '`'
        while (j < code.length) {
            val ch = code[j]
            if (ch == '\\') {
                j += 2
                continue
            }
            if (ch == quote) return j + 1
            if (ch == '\n' && !multiline) return j
            j++
        }
        return code.length
    }

    private fun numberEnd(
        code: String,
        from: Int,
    ): Int {
        var j = from + 1
        while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_' || (code[j] == '.' && code.getOrNull(j + 1)?.isDigit() == true))) j++
        return j
    }

    private fun wordEnd(
        code: String,
        from: Int,
    ): Int {
        var j = from + 1
        while (j < code.length && isWordPart(code[j])) j++
        return j
    }

    private fun nextNonSpace(
        code: String,
        from: Int,
    ): Char? {
        var j = from
        while (j < code.length && (code[j] == ' ' || code[j] == '\t')) j++
        return code.getOrNull(j)
    }

    private fun isWordStart(c: Char) = c.isLetter() || c == '_'

    private fun isWordPart(c: Char) = c.isLetterOrDigit() || c == '_'
}
