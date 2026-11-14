package com.macrotracker.ui.components

/**
 * Colour for a code block, by what each stretch is: a comment, a string, a number, a
 * keyword, a key. It is a tokenizer per family of languages, not a parser: enough that a
 * shell command, a YAML file or a JSON answer reads as one, as T3 Code's chat colours them.
 * A language it does not know is left plain.
 */
internal object CodeHighlight {

    enum class Tone { COMMENT, STRING, NUMBER, KEYWORD, KEY }

    data class Span(val start: Int, val end: Int, val tone: Tone)

    private enum class Family { HASH, SLASH, JSON, SQL }

    private val FAMILIES: Map<String, Family> = buildMap {
        listOf(
            "sh", "bash", "zsh", "shell", "console", "fish", "yaml", "yml", "toml", "ini", "conf", "cfg", "env", "properties",
            "python", "py", "ruby", "rb", "dockerfile", "docker", "makefile", "make", "nginx", "caddyfile", "caddy", "systemd", "service", "r", "perl",
        ).forEach { put(it, Family.HASH) }
        listOf(
            "js", "javascript", "ts", "typescript", "tsx", "jsx", "kotlin", "kt", "kts", "java", "c", "cpp", "h", "go", "rust", "rs",
            "swift", "css", "scss", "php", "gradle", "groovy", "dart", "csharp", "cs", "jsonc", "json5",
        ).forEach { put(it, Family.SLASH) }
        put("json", Family.JSON)
        listOf("sql", "sqlite", "postgres", "psql", "mysql").forEach { put(it, Family.SQL) }
    }

    private val KEYWORDS: Map<Family, Set<String>> = mapOf(
        Family.HASH to setOf(
            "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until", "case", "esac", "function", "return", "exit",
            "export", "local", "sudo", "def", "class", "import", "from", "as", "with", "try", "except", "finally", "raise", "lambda",
            "pass", "yield", "async", "await", "not", "and", "or", "is", "None", "True", "False", "true", "false", "null", "yes", "no",
            "FROM", "RUN", "COPY", "CMD", "ENV", "EXPOSE", "WORKDIR", "ENTRYPOINT", "ARG", "USER", "VOLUME",
        ),
        Family.SLASH to setOf(
            "fun", "val", "var", "const", "let", "function", "return", "if", "else", "for", "while", "when", "switch", "case", "break",
            "continue", "class", "interface", "object", "import", "package", "export", "from", "new", "null", "true", "false", "this",
            "try", "catch", "finally", "throw", "async", "await", "private", "public", "protected", "internal", "override", "suspend",
            "data", "sealed", "enum", "type", "struct", "impl", "fn", "pub", "use", "mut", "match", "static", "void", "extends",
            "implements", "default", "in", "is", "as", "do", "defer", "go", "func", "nil", "undefined",
        ),
        Family.JSON to setOf("true", "false", "null"),
        Family.SQL to setOf(
            "select", "from", "where", "and", "or", "not", "insert", "into", "values", "update", "set", "delete", "create", "table",
            "drop", "alter", "join", "left", "right", "inner", "outer", "on", "group", "by", "order", "limit", "as", "null", "is",
            "in", "like", "having", "distinct", "union", "index", "primary", "key", "count", "desc", "asc",
        ),
    )

    private const val STRING = """"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'"""
    private const val NUMBER = """(?<![\w.])\d+(?:\.\d+)?(?![\w.])"""
    private const val WORD = """[A-Za-z_][A-Za-z0-9_]*"""

    /** Comment, string, number, word: tried in that order at each place, so a `#` inside a string stays in it. */
    private val TOKENS: Map<Family, Regex> = mapOf(
        // a "#" starts a comment only at the start of a line or after a space: "$#" and "url#part" are not comments
        Family.HASH to Regex("""((?:^|(?<=\s))#[^\n]*)|($STRING)|($NUMBER)|($WORD)""", RegexOption.MULTILINE),
        // "//" after a colon is a URL
        Family.SLASH to Regex("""((?<![:\w])//[^\n]*|/\*[\s\S]*?\*/)|($STRING|`[^`]*`)|($NUMBER)|($WORD)"""),
        Family.JSON to Regex("""(￿)|($STRING)|($NUMBER)|($WORD)"""),
        Family.SQL to Regex("""(--[^\n]*)|($STRING)|($NUMBER)|($WORD)"""),
    )

    /** `name:` or `name =` opening a line of YAML, TOML or an ini file. */
    private val LINE_KEY = Regex("""^[ \t]*(?:- )?([A-Za-z_][\w.\-]*)(?=[ \t]*[:=](?:\s|$))""", RegexOption.MULTILINE)
    private val KEYED = setOf("yaml", "yml", "toml", "ini", "conf", "cfg", "env", "properties", "systemd", "service")

    fun spans(code: String, info: String): List<Span> {
        val lang = info.trim().substringBefore(' ').substringBefore(':').lowercase()
        val family = FAMILIES[lang] ?: return emptyList()
        if (code.length > MAX_CHARS) return emptyList()
        val out = ArrayList<Span>()
        val keywords = KEYWORDS.getValue(family)
        for (m in TOKENS.getValue(family).findAll(code)) {
            val start = m.range.first
            val end = m.range.last + 1
            when {
                m.groups[1] != null -> out += Span(start, end, Tone.COMMENT)
                m.groups[2] != null -> {
                    // a JSON string followed by a colon is a key
                    val key = family == Family.JSON && code.substring(end).trimStart().startsWith(":")
                    out += Span(start, end, if (key) Tone.KEY else Tone.STRING)
                }
                m.groups[3] != null -> out += Span(start, end, Tone.NUMBER)
                m.groups[4] != null -> {
                    val word = m.value
                    if (word in keywords || (family == Family.SQL && word.lowercase() in keywords)) out += Span(start, end, Tone.KEYWORD)
                }
            }
        }
        if (lang in KEYED) {
            val taken = out.filter { it.tone == Tone.COMMENT || it.tone == Tone.STRING }
            for (m in LINE_KEY.findAll(code)) {
                val g = m.groups[1] ?: continue
                if (taken.none { g.range.first >= it.start && g.range.first < it.end }) {
                    out.removeAll { it.start >= g.range.first && it.end <= g.range.last + 1 }
                    out += Span(g.range.first, g.range.last + 1, Tone.KEY)
                }
            }
        }
        return out.sortedBy { it.start }
    }

    private const val MAX_CHARS = 20_000
}

/** A path on the server written in an answer (`/home/fredde/docker/caddy/Caddyfile:12`, `~/notes/Home.md`), and the line it names. */
internal object ServerPath {
    private val PATH = Regex("""^((?:~|/(?:home|etc|srv|var|opt|usr|tmp|mnt|root|data|media|boot|run))/[^\s:*?"<>|]+)(?::(\d+)(?:[-:]\d+)?)?$""")

    /** The path and its line (0 when it names none), or null when [text] is not a path. */
    fun parse(text: String): Pair<String, Int>? {
        val m = PATH.matchEntire(text.trim()) ?: return null
        return m.groupValues[1] to (m.groupValues[2].toIntOrNull() ?: 0)
    }
}
