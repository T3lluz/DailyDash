package com.macrotracker.ui.components

/**
 * Markdown as blocks, with the web dashboard's rules (`hermes-markdown.js`), so a Hermes
 * reply reads the same on the desk and on the phone: GFM tables with alignment, nested
 * and task lists, quotes and `> [!NOTE]` callouts, rules, headings, fenced code.
 */
internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock

    /** Lines of one paragraph; the renderer decides whether a line break is a space. */
    data class Paragraph(val lines: List<String>) : MdBlock

    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<MdListItem>) : MdBlock

    /** A quote, or a GitHub callout when [callout] is `note`, `tip`, `important`, `warning` or `caution`. */
    data class Quote(val lines: List<String>, val callout: String? = null) : MdBlock

    data class Table(val header: List<String>, val align: List<MdAlign>, val rows: List<List<String>>) : MdBlock

    data object Rule : MdBlock

    /** A fence. [open] while a streamed reply has not closed it yet. */
    data class Code(val info: String, val code: String, val open: Boolean = false) : MdBlock
}

internal data class MdListItem(
    val lines: List<String>,
    /** Null for a plain item; true or false for `- [x]` / `- [ ]`. */
    val checked: Boolean? = null,
    val children: List<MdBlock.ListBlock> = emptyList(),
)

internal enum class MdAlign { START, CENTER, END }

internal object MarkdownParser {
    private val headingRx = Regex("""^(#{1,6})\s+(.*?)\s*#*$""")
    private val ruleRx = Regex("""^(?:-{3,}|\*{3,}|_{3,})$""")
    private val ulRx = Regex("""^[-*+•] """)
    private val olRx = Regex("""^\d+[.)] """)
    private val taskRx = Regex("""^[-*+•] \[([ xX])] (.*)$""")
    private val calloutRx = Regex("""^>\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val sepRowRx = Regex("""^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?$""")
    private val pipeRowRx = Regex("""^\|.*\|$""")
    private val cellSplitRx = Regex("""(?<!\\)\|""")

    fun parse(raw: String): List<MdBlock> {
        if (raw.isBlank()) return emptyList()
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ").split('\n')
        return Reader(lines).blocks()
    }

    private fun isUl(t: String) = ulRx.containsMatchIn(t)
    private fun isOl(t: String) = olRx.containsMatchIn(t)
    private fun indentOf(line: String) = line.length - line.trimStart(' ').length
    private fun isFence(t: String) = t.startsWith("```") || t.startsWith("~~~")

    private fun cells(row: String): List<String> =
        row.trim().removePrefix("|").removeSuffix("|")
            .split(cellSplitRx)
            .map { it.trim().replace("\\|", "|") }

    private class Reader(private val lines: List<String>) {
        var i = 0

        fun blocks(): List<MdBlock> {
            val out = mutableListOf<MdBlock>()
            val para = mutableListOf<String>()
            fun flush() {
                if (para.isNotEmpty()) out += MdBlock.Paragraph(para.toList())
                para.clear()
            }
            while (i < lines.size) {
                val t = lines[i].trim()
                when {
                    t.isEmpty() -> { flush(); i++ }
                    isFence(t) -> { flush(); out += fence() }
                    headingRx.matches(t) -> {
                        flush()
                        val m = headingRx.find(t)!!
                        out += MdBlock.Heading(m.groupValues[1].length.coerceAtMost(4), m.groupValues[2])
                        i++
                    }
                    ruleRx.matches(t) -> { flush(); out += MdBlock.Rule; i++ }
                    t.startsWith(">") -> { flush(); out += quote() }
                    isUl(t) || isOl(t) -> { flush(); out += list() }
                    isTableStart(t) -> { flush(); out += table() }
                    else -> { para += t; i++ }
                }
            }
            flush()
            return out
        }

        private fun fence(): MdBlock.Code {
            val opener = lines[i].trim()
            val marker = opener.take(3)
            val info = opener.drop(3).trim()
            i++
            val body = StringBuilder()
            while (i < lines.size) {
                if (lines[i].trim().startsWith(marker)) {
                    i++
                    return MdBlock.Code(info, body.toString().trimEnd('\n'))
                }
                body.append(lines[i]).append('\n')
                i++
            }
            // The reply was cut off, or is still streaming, inside the block. Show what arrived.
            return MdBlock.Code(info, body.toString().trimEnd('\n'), open = true)
        }

        private fun quote(): MdBlock.Quote {
            val q = mutableListOf<String>()
            var kind: String? = null
            while (i < lines.size) {
                val qt = lines[i].trim()
                if (qt.startsWith(">")) {
                    val alert = calloutRx.find(qt)
                    if (alert != null) {
                        if (kind != null || q.isNotEmpty()) break
                        kind = alert.groupValues[1].lowercase()
                        alert.groupValues[2].takeIf { it.isNotBlank() }?.let { q += it }
                        i++
                        continue
                    }
                    q += qt.removePrefix(">").removePrefix(" ")
                    i++
                    continue
                }
                if (qt.isEmpty() && i + 1 < lines.size && lines[i + 1].trim().startsWith(">")) {
                    i++
                    continue
                }
                break
            }
            return MdBlock.Quote(q, kind)
        }

        private fun list(): MdBlock.ListBlock {
            val base = indentOf(lines[i])
            val ordered = isOl(lines[i].substring(base))
            // "3. …" after a paragraph is item three, not a new item one.
            val first = if (ordered) lines[i].substring(base).takeWhile { it.isDigit() }.toIntOrNull() ?: 1 else 1
            val items = mutableListOf<MdListItem>()
            while (i < lines.size) {
                val raw = lines[i]
                val ind = indentOf(raw)
                val t = raw.substring(ind).trimEnd()
                if (t.isEmpty()) {
                    val next = lines.getOrNull(i + 1)
                    if (next.isNullOrBlank()) break
                    val nind = indentOf(next)
                    val nt = next.substring(nind)
                    if (nind >= base && (isUl(nt) || isOl(nt) || nind > base)) {
                        i++
                        continue
                    }
                    break
                }
                // A fence is a block of its own, even inside an item.
                if (isFence(t)) break
                if (ind < base) break
                if (ind > base) {
                    if (items.isEmpty()) break
                    val last = items.removeAt(items.lastIndex)
                    if (isUl(t) || isOl(t)) {
                        items += last.copy(children = last.children + list())
                    } else {
                        items += last.copy(lines = last.lines + t)
                        i++
                    }
                    continue
                }
                if (if (ordered) !isOl(t) else !isUl(t)) break
                val task = taskRx.find(t)
                items += if (task != null) {
                    MdListItem(listOf(task.groupValues[2]), checked = task.groupValues[1] != " ")
                } else {
                    MdListItem(listOf(t.replaceFirst(ulRx, "").replaceFirst(olRx, "")))
                }
                i++
            }
            return MdBlock.ListBlock(ordered, first, items)
        }

        private fun isTableStart(t: String): Boolean {
            val next = lines.getOrNull(i + 1)?.trim() ?: return false
            if (!sepRowRx.matches(next)) return false
            return (t.contains('|') && next.contains('|')) || pipeRowRx.matches(t)
        }

        private fun table(): MdBlock.Table {
            val head = cells(lines[i])
            val align = cells(lines[i + 1]).map { c ->
                when {
                    c.startsWith(":") && c.endsWith(":") -> MdAlign.CENTER
                    c.endsWith(":") -> MdAlign.END
                    else -> MdAlign.START
                }
            }
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
                val row = cells(lines[i])
                rows += List(head.size) { k -> row.getOrElse(k) { "" } }
                i++
            }
            return MdBlock.Table(head, List(head.size) { k -> align.getOrElse(k) { MdAlign.START } }, rows)
        }
    }
}
