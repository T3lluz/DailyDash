package com.macrotracker.data.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitDiffTest {
    private val sample = """
        diff --git a/src/a.kt b/src/a.kt
        index 111..222 100644
        --- a/src/a.kt
        +++ b/src/a.kt
        @@ -3,4 +3,5 @@ fun main() {
         keep
        -old
        +new
        +added
         tail
        @@ -20 +21 @@
        -x
        +y
        diff --git a/img.png b/img.png
        new file mode 100644
        Binary files /dev/null and b/img.png differ
    """.trimIndent()

    @Test
    fun `files hunks and line numbers are worked out`() {
        val files = GitDiff.parse(sample)
        assertEquals(2, files.size)
        val a = files[0]
        assertEquals("src/a.kt", a.path)
        assertEquals(2, a.hunks.size)
        assertEquals(3, a.plus)
        assertEquals(2, a.minus)
        val lines = a.hunks[0].lines
        assertEquals(DiffLine.Kind.CONTEXT, lines[0].kind)
        assertEquals(3, lines[0].old)
        assertEquals(3, lines[0].new)
        assertEquals(DiffLine.Kind.DEL, lines[1].kind)
        assertEquals(4, lines[1].old)
        assertEquals(DiffLine.Kind.ADD, lines[2].kind)
        assertEquals(4, lines[2].new)
        assertEquals(5, lines[3].new)
        assertEquals(21, a.hunks[1].lines[1].new)
    }

    @Test
    fun `a binary new file is marked and has no hunks`() {
        val f = GitDiff.parse(sample)[1]
        assertTrue(f.binary)
        assertEquals("A", f.status)
        assertEquals(0, f.hunks.size)
    }

    @Test
    fun `status badges and groups`() {
        val st = GitParse.status(
            org.json.JSONObject(
                """{"repo":"/r","name":"r","branch":"main","upstream":"origin/main","ahead":1,"behind":0,"stashes":0,"files":[
                {"path":"a","x":"M","y":".","staged":true,"unstaged":false,"plus":1,"minus":0},
                {"path":"b","x":".","y":"M","staged":false,"unstaged":true,"plus":2,"minus":1},
                {"path":"c","x":".","y":"?","untracked":true,"staged":false,"unstaged":true,"plus":9,"minus":0}],"last":{"short":"abc","subject":"s","at":5}}""",
            ),
        )
        assertEquals(1, st.staged.size)
        assertEquals(1, st.changed.size)
        assertEquals(1, st.untracked.size)
        assertEquals('?', st.untracked[0].badge)
        assertEquals(5000L, st.lastMs)
    }
}
