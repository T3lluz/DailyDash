package com.macrotracker.data.google

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.macrotracker.data.youtube.AuthorizeOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** What DailyDash can read from your Google account for widgets, each asked for on its own. */
enum class GoogleScope(val id: String, val label: String, val detail: String, val url: String) {
    GMAIL("gmail", "Gmail", "Your inbox and unread mail, read-only", "https://www.googleapis.com/auth/gmail.readonly"),
    TASKS("tasks", "Google Tasks", "Your task lists, read-only", "https://www.googleapis.com/auth/tasks.readonly"),
    ;

    companion object {
        fun byId(id: String?): GoogleScope? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Google access for Tilesmith's Gmail and Tasks sources, through Play Services'
 * AuthorizationClient: the same Android OAuth client the YouTube import uses (package
 * `com.macrotracker` + the signing SHA-1), so there is no client id or secret in the app.
 *
 * Asking ([request]) shows Google's own consent sheet once per scope; after that [token] gets
 * a fresh access token silently, from a widget refresh with the app closed. When Google wants
 * the person again (a revoked grant, a 7-day Testing-mode expiry) [token] returns null and the
 * scope reads as not granted, so the studio and Settings → Permissions offer it again.
 *
 * Cloud Console, once: enable the Gmail API and the Google Tasks API in the project that has
 * the Android client, and add `gmail.readonly` and `tasks.readonly` under the consent screen's
 * data access. Publish the consent screen (Production) so grants don't lapse after a week.
 */
@Singleton
class GoogleAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) {
    private val prefs by lazy { context.getSharedPreferences("google_access", Context.MODE_PRIVATE) }

    /** A token per scope, reused for most of its hour so a refresh doesn't ask Play Services each time. */
    private val tokens = HashMap<GoogleScope, Pair<String, Long>>()

    fun granted(scope: GoogleScope): Boolean = prefs.getBoolean("ok_${scope.id}", false)

    fun grantedScopes(): Set<GoogleScope> = GoogleScope.entries.filter(::granted).toSet()

    /** The account the grants are for, once known. */
    fun email(): String? = prefs.getString("email", null)?.takeIf { it.isNotBlank() }

    private fun mark(scopes: Collection<GoogleScope>, ok: Boolean) {
        prefs.edit { scopes.forEach { putBoolean("ok_${it.id}", ok) } }
        if (!ok) synchronized(tokens) { scopes.forEach { tokens.remove(it) } }
    }

    private fun requestFor(scopes: Collection<GoogleScope>): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(scopes.map { Scope(it.url) } + Scope(EMAIL_SCOPE))
            .build()

    /**
     * Asks for [scopes]. [AuthorizeOutcome.NeedsConsent] carries Google's sheet: launch it and
     * hand the result to [complete].
     */
    suspend fun request(activity: Activity, scopes: Set<GoogleScope>): AuthorizeOutcome = withContext(Dispatchers.Main) {
        try {
            val result = Identity.getAuthorizationClient(activity).authorize(requestFor(scopes)).await()
            if (result.hasResolution()) {
                result.pendingIntent?.let { AuthorizeOutcome.NeedsConsent(it) } ?: AuthorizeOutcome.Failed("Google's sign-in sheet isn't available")
            } else {
                ready(result, scopes)
            }
        } catch (e: ApiException) {
            Log.w(TAG, "request: ${e.statusCode}", e)
            AuthorizeOutcome.Failed(humanize(e))
        } catch (e: Exception) {
            AuthorizeOutcome.Failed(e.message ?: "Google sign-in failed")
        }
    }

    suspend fun complete(activity: Activity, data: Intent?, scopes: Set<GoogleScope>): AuthorizeOutcome = withContext(Dispatchers.Main) {
        if (data == null) return@withContext AuthorizeOutcome.Failed("Sign-in was cancelled")
        try {
            ready(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data), scopes)
        } catch (e: ApiException) {
            Log.w(TAG, "complete: ${e.statusCode}", e)
            AuthorizeOutcome.Failed(humanize(e))
        } catch (e: Exception) {
            AuthorizeOutcome.Failed(e.message ?: "Couldn't finish Google sign-in")
        }
    }

    private suspend fun ready(result: AuthorizationResult, scopes: Set<GoogleScope>): AuthorizeOutcome {
        val token = result.accessToken ?: return AuthorizeOutcome.Failed("Google sent no access token")
        val got = result.grantedScopes.toSet()
        val ok = scopes.filter { s -> got.isEmpty() || s.url in got }
        mark(ok, true)
        mark(scopes - ok.toSet(), false)
        val email = fetchEmail(token)
        if (email != null) prefs.edit { putString("email", email) }
        synchronized(tokens) { ok.forEach { tokens[it] = token to System.currentTimeMillis() } }
        return if (ok.isEmpty()) AuthorizeOutcome.Failed("Google didn't grant ${scopes.joinToString { it.label }}") else AuthorizeOutcome.Ready(token, email)
    }

    /** An access token for [scope] with no UI, or null when the person must allow it again. */
    suspend fun token(scope: GoogleScope): String? {
        if (!granted(scope)) return null
        synchronized(tokens) {
            tokens[scope]?.let { (t, at) -> if (System.currentTimeMillis() - at < TOKEN_REUSE_MS) return t }
        }
        val result = withTimeoutOrNull(15_000L) {
            withContext(Dispatchers.Main) {
                runCatching { Identity.getAuthorizationClient(context).authorize(requestFor(listOf(scope))).await() }
                    .onFailure { Log.w(TAG, "token ${scope.id}: ${it.message}") }
                    .getOrNull()
            }
        } ?: return null
        if (result.hasResolution() || result.accessToken == null) {
            mark(listOf(scope), false)
            return null
        }
        val token = result.accessToken!!
        synchronized(tokens) { tokens[scope] = token to System.currentTimeMillis() }
        return token
    }

    /** Google said the token is no good (401): forget it, so the next read gets a fresh one. */
    fun dropToken(scope: GoogleScope) {
        synchronized(tokens) { tokens.remove(scope) }
    }

    suspend fun revoke(activity: Activity, scopes: Set<GoogleScope>) {
        withContext(Dispatchers.Main) {
            runCatching {
                Identity.getAuthorizationClient(activity)
                    .revokeAccess(RevokeAccessRequest.builder().setScopes(scopes.map { Scope(it.url) }).build())
                    .await()
            }
        }
        mark(scopes, false)
    }

    private suspend fun fetchEmail(token: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(USERINFO_URL).header("Authorization", "Bearer $token").build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                JSONObject(r.body?.string().orEmpty()).optString("email").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun humanize(e: ApiException): String = when (e.statusCode) {
        10 -> "Google sign-in isn't set up for this build: the Android OAuth client needs package com.macrotracker and this build's SHA-1, and the Gmail / Tasks APIs enabled."
        12501, 16 -> "Sign-in was cancelled"
        7 -> "No network for Google sign-in"
        17 -> "Update Google Play services and try again"
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed (code ${e.statusCode})"
    }

    companion object {
        private const val TAG = "GoogleAccess"
        private const val EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email"
        private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
        private const val TOKEN_REUSE_MS = 40 * 60_000L
    }
}
