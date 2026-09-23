package com.macrotracker.data.phone

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * "Send to desk" in the share sheet: a link, some text, a photo or a file goes to the
 * dashboard's phone inbox. No screen of its own; it says how it went in a toast and closes.
 */
class PhoneShareActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entry = EntryPointAccessors.fromApplication(applicationContext, PhoneHubEntryPoint::class.java)
        val body = runCatching { read(intent) }.getOrElse {
            toast(it.message ?: "Could not read that")
            finish()
            return
        }
        if (body == null) {
            toast("Nothing to send")
            finish()
            return
        }
        toast("Sending to the desk…")
        val app = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val msg = runCatching { entry.phoneHubClient().share(body) }.fold(
                onSuccess = { "Sent to the desk" },
                onFailure = { e ->
                    if (e is PhoneHubException && e.code == 403) "The dashboard has not accepted this phone yet"
                    else "Could not reach the dashboard: ${e.message}"
                },
            )
            withContext(Dispatchers.Main) { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
        }
        finish()
    }

    private fun toast(text: String) = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()

    private fun read(intent: Intent): JSONObject? {
        if (intent.action != Intent.ACTION_SEND) return null
        val body = JSONObject().put("from", Build.MODEL)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val stream: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (stream != null) {
            val mime = contentResolver.getType(stream) ?: intent.type ?: "application/octet-stream"
            val name = contentResolver.query(stream, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: stream.lastPathSegment ?: "file"
            val bytes = contentResolver.openInputStream(stream)?.use { it.readBytes() } ?: throw IllegalStateException("Could not read that file")
            if (bytes.size > MAX_BYTES) throw IllegalStateException("That file is over 8 MB")
            body.put("file", JSONObject().put("name", name).put("mime", mime).put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)))
            body.put("title", subject.ifBlank { name })
            if (text.isNotBlank()) body.put("text", text)
            return body
        }
        if (text.isBlank()) return null
        // A shared page usually arrives as "Title https://…"; keep the link and the words apart.
        val url = Regex("""https?://\S+""").find(text)?.value
        if (url != null) {
            body.put("url", url)
            body.put("title", subject.ifBlank { text.replace(url, "").trim() }.ifBlank { url })
        } else {
            body.put("kind", "text").put("text", text).put("title", subject)
        }
        return body
    }

    private companion object {
        const val MAX_BYTES = 8_000_000
    }
}
