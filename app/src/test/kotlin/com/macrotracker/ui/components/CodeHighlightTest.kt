package com.macrotracker.ui.components

import com.macrotracker.ui.components.CodeHighlight.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlightTest {

    private fun toned(code: String, lang: String) = CodeHighlight.spans(code, lang).map { code.substring(it.start, it.end) to it.tone }

    @Test
    fun `a shell command keeps its comment, strings and keywords apart`() {
        val code = "if [ \"\$#\" -gt 2 ]; then echo 'a # b'; fi # done"
        assertEquals(
            listOf("if" to Tone.KEYWORD, "\"\$#\"" to Tone.STRING, "2" to Tone.NUMBER, "then" to Tone.KEYWORD, "'a # b'" to Tone.STRING, "fi" to Tone.KEYWORD, "# done" to Tone.COMMENT),
            toned(code, "bash"),
        )
    }

    @Test
    fun `yaml and json name their keys`() {
        assertEquals(
            listOf("services" to Tone.KEY, "image" to Tone.KEY, "ports" to Tone.KEY, "\"80:80\"" to Tone.STRING, "# web" to Tone.COMMENT),
            toned("services:\n  image: caddy\n  ports:\n    - \"80:80\" # web", "yaml"),
        )
        assertEquals(
            listOf("\"name\"" to Tone.KEY, "\"caddy\"" to Tone.STRING, "\"up\"" to Tone.KEY, "true" to Tone.KEYWORD, "\"n\"" to Tone.KEY, "3" to Tone.NUMBER),
            toned("{\"name\": \"caddy\", \"up\": true, \"n\": 3}", "json"),
        )
    }

    @Test
    fun `a url is not a comment and an unknown language is left plain`() {
        assertEquals(listOf("val" to Tone.KEYWORD, "\"https://x.y//z\"" to Tone.STRING, "// why" to Tone.COMMENT), toned("val u = \"https://x.y//z\" // why", "kotlin"))
        assertTrue(CodeHighlight.spans("anything # at all", "").isEmpty())
        assertTrue(CodeHighlight.spans("anything # at all", "text").isEmpty())
    }

    @Test
    fun `a server path is told from other code`() {
        assertEquals("/home/fredde/docker/caddy/Caddyfile" to 12, ServerPath.parse("/home/fredde/docker/caddy/Caddyfile:12"))
        assertEquals("~/notes/Home.md" to 0, ServerPath.parse("~/notes/Home.md"))
        assertEquals("/etc/caddy/Caddyfile" to 3, ServerPath.parse("/etc/caddy/Caddyfile:3-9"))
        assertNull(ServerPath.parse("docker ps"))
        assertNull(ServerPath.parse("/wp-admin/"))
        assertNull(ServerPath.parse("~/notes/*.md"))
        assertNull(ServerPath.parse("ls /etc/caddy"))
    }
}
