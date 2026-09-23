package com.macrotracker.data.update

/**
 * Turns GitHub release bodies into concise, readable Markdown for in-app display.
 *
 * Strips CI boilerplate / version-bump noise, converts bare PR/changelog URLs into
 * labeled links, and keeps a short bullet list under a single heading.
 */
object ReleaseNotesFormatter {

    private val boilerplateLine = Regex(
        pattern = """(?i)^(dailydash\s+\d.*(build\s+\d+)?|automated tester build.*|tester build.*|installs as an in-app update.*)$""",
    )
    private val noiseLine = Regex(
        pattern = """(?i)^(chore:\s*bump version.*|.*\[skip ci\].*|merge (pull request|branch).*|bumped? version.*|update version.*)$""",
    )
    private val metaComment = Regex(
        pattern = """(?i)^<!--\s*dailydash-version:\s*([0-9]+(?:\.[0-9]+)*)\s+vc(\d+)\s*-->$""",
    )
    private val fullChangelogLine = Regex(
        pattern = """(?i)^\**full changelog\**:?\s*(https://\S+)\s*$""",
    )
    private val prBullet = Regex(
        pattern = """^\*\s+(.+?)\s+by\s+@\S+\s+in\s+(https://github\.com/\S+/pull/\d+)\s*$""",
    )
    private val numberedPrBullet = Regex(
        pattern = """^\d+\.\s+(.+?)\s+by\s+@\S+\s+in\s+(https://github\.com/\S+/pull/\d+)\s*$""",
    )
    private val linkedBullet = Regex(
        pattern = """^[-*+]\s+\[(.+?)]\((https://github\.com/\S+/pull/\d+)\)\s*$""",
    )
    private val sectionHeading = Regex("""^#{1,6}\s+(.+)$""")
    private val bareUrl = Regex("""https?://[^\s<>\)\]]+""")
    private val viewOnGithubLine = Regex(
        pattern = """(?i)^\[view release on github]\(https://\S+\)$""",
    )

    data class ParsedMeta(
        val versionName: String?,
        val versionCode: Int?,
    )

    /** The first change in [formatted] notes, as plain text for a notification line. */
    fun headline(formatted: String): String? =
        formatted.lines()
            .map { it.trim() }
            .firstOrNull { it.startsWith("- ") || it.startsWith("* ") }
            ?.drop(2)
            ?.replace(Regex("""\[(.+?)]\([^)]*\)"""), "$1")
            ?.replace("**", "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun parseMeta(raw: String): ParsedMeta {
        for (line in raw.replace("\r\n", "\n").lines()) {
            val match = metaComment.matchEntire(line.trim()) ?: continue
            return ParsedMeta(
                versionName = match.groupValues[1],
                versionCode = match.groupValues[2].toIntOrNull(),
            )
        }
        return ParsedMeta(null, null)
    }

    fun format(raw: String, htmlUrl: String = ""): String {
        if (raw.isBlank()) return "Bug fixes and improvements."

        val changes = mutableListOf<String>()
        var changelogUrl: String? = htmlUrl.takeIf { it.isNotBlank() }
        var sawWhatsChanged = false

        for (line in raw.replace("\r\n", "\n").lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (boilerplateLine.matches(trimmed)) continue
            if (noiseLine.matches(trimmed)) continue
            if (metaComment.matches(trimmed)) continue
            if (viewOnGithubLine.matches(trimmed)) {
                val url = trimmed.substringAfter("(").substringBefore(")")
                if (url.startsWith("http")) changelogUrl = url
                continue
            }

            val fullMatch = fullChangelogLine.matchEntire(trimmed)
            if (fullMatch != null) {
                changelogUrl = fullMatch.groupValues[1]
                continue
            }

            val headingMatch = sectionHeading.matchEntire(trimmed)
            if (headingMatch != null) {
                val title = headingMatch.groupValues[1].trim()
                if (title.contains("what's changed", ignoreCase = true) ||
                    title.contains("what changed", ignoreCase = true) ||
                    title.contains("what's new", ignoreCase = true)
                ) {
                    sawWhatsChanged = true
                }
                continue
            }

            val linked = linkedBullet.matchEntire(trimmed)
            if (linked != null) {
                val title = cleanTitle(linked.groupValues[1])
                if (!isNoiseTitle(title)) {
                    changes += "- [$title](${linked.groupValues[2]})"
                }
                continue
            }

            val prMatch = prBullet.matchEntire(trimmed) ?: numberedPrBullet.matchEntire(trimmed)
            if (prMatch != null) {
                val title = cleanTitle(prMatch.groupValues[1])
                if (!isNoiseTitle(title)) {
                    changes += "- [$title](${prMatch.groupValues[2]})"
                }
                continue
            }

            when {
                trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("+ ") -> {
                    val item = cleanTitle(trimmed.drop(2).trim())
                    if (!isNoiseTitle(item)) {
                        changes += "- ${linkifyBareUrls(item)}"
                    }
                }
                trimmed.matches(Regex("""^\d+\.\s+.+""")) -> {
                    val item = cleanTitle(trimmed.replace(Regex("""^\d+\.\s+"""), ""))
                    if (!isNoiseTitle(item)) {
                        changes += "- ${linkifyBareUrls(item)}"
                    }
                }
                sawWhatsChanged -> {
                    // Ignore trailing prose under auto-generated sections.
                }
                else -> {
                    val item = cleanTitle(trimmed)
                    if (!isNoiseTitle(item)) {
                        changes += "- ${linkifyBareUrls(item)}"
                    }
                }
            }
        }

        val unique = changes
            .map { it.trim() }
            .filter { it.length > 3 }
            .distinct()
            .take(12)

        if (unique.isEmpty()) {
            return buildString {
                append("Bug fixes and improvements.")
                if (!changelogUrl.isNullOrBlank()) {
                    append("\n\n[View release on GitHub](")
                    append(changelogUrl)
                    append(")")
                }
            }
        }

        return buildString {
            append("## What's new\n")
            unique.forEach { append(it).append('\n') }
            if (!changelogUrl.isNullOrBlank()) {
                append("\n[View release on GitHub](")
                append(changelogUrl)
                append(")")
            }
        }.trim()
    }

    fun cleanTitle(title: String): String {
        return title
            .removePrefix("QA:")
            .trim()
            .replace(Regex("""^\[.*?]\s*"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "Update" }
    }

    fun isNoiseTitle(title: String): Boolean {
        val t = title.trim()
        if (t.isBlank()) return true
        return noiseLine.matches(t) ||
            t.contains("[skip ci]", ignoreCase = true) ||
            t.startsWith("chore: bump version", ignoreCase = true)
    }

    private fun linkifyBareUrls(text: String): String {
        if (text.contains("](")) return text
        return bareUrl.replace(text) { match ->
            val url = match.value.trimEnd('.', ',', ';', ')', ']')
            val label = when {
                url.contains("/pull/") -> "PR #${url.substringAfterLast('/')}"
                url.contains("/compare/") -> "Changelog"
                url.contains("/releases/") -> "Release"
                else -> url.removePrefix("https://").removePrefix("http://")
            }
            "[$label]($url)"
        }
    }
}
