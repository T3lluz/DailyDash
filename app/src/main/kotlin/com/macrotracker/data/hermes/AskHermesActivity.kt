package com.macrotracker.data.hermes

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import com.macrotracker.MainActivity
import com.macrotracker.util.readAtMost
import java.io.File

/**
 * "Ask Hermes" in the share sheet: a link, some text, a photo or files become a new chat's first
 * message. No screen of its own. The files are copied into the cache here, while this activity
 * still holds the sender's permission to read them, and the app opens on the AI tab to take them.
 */
class AskHermesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = runCatching { read(intent) }.getOrNull()
        if (shared == null || (shared.text.isBlank() && shared.files.isEmpty() && intent.action != Intent.ACTION_VIEW)) {
            Toast.makeText(applicationContext, "Nothing to ask Hermes about", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        HermesShareInbox.put(shared)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN, "ai"),
        )
        finish()
    }

    private fun read(intent: Intent): HermesShareInbox.Shared? {
        // dailydash://ask?prompt=... : a link that starts a chat with the words in the box (never sent for you)
        if (intent.action == Intent.ACTION_VIEW) {
            val prompt = intent.data?.getQueryParameter("prompt")?.trim().orEmpty()
            return HermesShareInbox.Shared(prompt, emptyList())
        }
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return null
        val text = listOfNotNull(
            intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotBlank() },
            intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotBlank() },
        ).distinct().joinToString("\n")
        val uris: List<Uri> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (intent.action == Intent.ACTION_SEND) {
                listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
            } else {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            }
        } else {
            @Suppress("DEPRECATION")
            if (intent.action == Intent.ACTION_SEND) {
                listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            } else {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            }
        }
        val dir = File(cacheDir, "hermes_shared").apply {
            deleteRecursively()
            mkdirs()
        }
        val files = uris.take(MAX_FILES).mapIndexedNotNull { i, uri ->
            runCatching {
                val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                val bytes = contentResolver.openInputStream(uri)?.use { it.readAtMost(MAX_BYTES) } ?: return@runCatching null
                File(dir, "$i-${name.replace('/', '_')}").also { it.writeBytes(bytes) }
            }.getOrNull()
        }
        return HermesShareInbox.Shared(text, files)
    }

    private companion object {
        const val MAX_FILES = 6
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
