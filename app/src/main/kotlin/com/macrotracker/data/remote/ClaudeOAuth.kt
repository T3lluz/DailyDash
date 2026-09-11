package com.macrotracker.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Claude Code's public OAuth (the same login T3 Code uses via `claude auth login`).
 *
 * Anthropic does not offer a third-party OAuth app for Claude Pro/Max. Claude Code
 * publishes this client id, and signing in with it draws from the Claude.ai
 * subscription instead of console API credits. DailyDash runs the PKCE loop itself
 * because an Android app cannot spawn the Claude CLI.
 */
object ClaudeOAuth {
    const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    const val AUTHORIZE_URL = "https://claude.ai/oauth/authorize"
    const val REDIRECT_URI = "https://platform.claude.com/oauth/code/callback"
    /**
     * Same union Claude Code `/login` sends (`ALL_OAUTH_SCOPES`). A shorter list
     * is rejected on the authorize page as "Invalid request format".
     */
    const val SCOPE =
        "org:create_api_key user:profile user:inference user:sessions:claude_code user:mcp_servers user:file_upload"
    const val PROFILE_URL = "https://api.anthropic.com/api/oauth/profile"

    /** Claude Code generates `base64url(32 bytes)` = 43 chars; shorter `state` is rejected. */
    const val STATE_BYTES = 32
    const val PKCE_VERIFIER_BYTES = 32

    val TOKEN_URLS = listOf(
        "https://platform.claude.com/v1/oauth/token",
        "https://console.anthropic.com/v1/oauth/token",
        "https://api.anthropic.com/v1/oauth/token",
    )

    /** Required on Messages calls that use a Claude Code OAuth access token. */
    const val API_BETA = "oauth-2025-04-20"

    /**
     * OAuth tokens are issued for Claude Code. Anthropic rejects `/v1/messages`
     * unless this identity is the first system block.
     */
    const val REQUIRED_SYSTEM = "You are Claude Code, Anthropic's official CLI for Claude."

    data class Pkce(
        val verifier: String,
        val challenge: String,
        val state: String,
    )

    data class AuthorizationCode(
        val code: String,
        val state: String,
    )

    data class TokenBundle(
        val accessToken: String,
        val refreshToken: String?,
        val expiresInSec: Int,
        val email: String?,
        val subscriptionType: String?,
    )

    fun generatePkce(random: SecureRandom = SecureRandom()): Pkce {
        val verifier = b64url(randomBytes(random, PKCE_VERIFIER_BYTES))
        val challenge = b64url(sha256(verifier.toByteArray(Charsets.US_ASCII)))
        val state = b64url(randomBytes(random, STATE_BYTES))
        return Pkce(verifier = verifier, challenge = challenge, state = state)
    }

    fun authorizeUrl(pkce: Pkce): String {
        val params = linkedMapOf(
            "code" to "true",
            "client_id" to CLIENT_ID,
            "response_type" to "code",
            "redirect_uri" to REDIRECT_URI,
            "scope" to SCOPE,
            "code_challenge" to pkce.challenge,
            "code_challenge_method" to "S256",
            "state" to pkce.state,
        )
        return AUTHORIZE_URL + "?" + params.entries.joinToString("&") { (k, v) ->
            "$k=${urlEncode(v)}"
        }
    }

    /**
     * Accepts the callback page's `code#state`, a bare code, or the full redirect URL.
     */
    fun parseAuthorizationInput(raw: String, fallbackState: String): AuthorizationCode {
        val trimmed = raw.trim().trim('"', '\'')
        require(trimmed.isNotBlank()) { "Paste the code from the Claude page" }

        val fromUrl = parseFromRedirectUrl(trimmed)
        if (fromUrl != null) {
            return AuthorizationCode(
                code = fromUrl.code,
                state = fromUrl.state.ifBlank { fallbackState },
            )
        }

        val hash = trimmed.indexOf('#')
        if (hash >= 0) {
            val code = trimmed.substring(0, hash).trim()
            val state = trimmed.substring(hash + 1).trim()
            require(code.isNotBlank()) { "Paste the code from the Claude page" }
            return AuthorizationCode(code = code, state = state.ifBlank { fallbackState })
        }

        return AuthorizationCode(code = trimmed, state = fallbackState)
    }

    fun parseTokenResponse(body: String): TokenBundle {
        val json = parseObject(body)
        val error = json.string("error")
        if (error != null) {
            throw IllegalStateException(json.string("error_description") ?: error)
        }
        val access = json.string("access_token")
            ?: throw IllegalStateException("Claude login did not return an access token")
        val refresh = json.string("refresh_token")
        val expiresIn = json.int("expires_in") ?: 3600
        val account = json.obj("account")
        return TokenBundle(
            accessToken = access,
            refreshToken = refresh,
            expiresInSec = if (expiresIn > 0) expiresIn else 3600,
            email = account?.string("email"),
            subscriptionType = account?.string("subscription_type")
                ?: account?.string("subscriptionType"),
        )
    }

    fun parseProfile(body: String): Pair<String?, String?> {
        val json = parseObject(body)
        val account = json.obj("account")
        val email = json.string("email") ?: account?.string("email")
        val subscription = json.string("subscription_type")
            ?: json.string("subscriptionType")
            ?: account?.string("subscription_type")
        return email to subscription
    }

    fun subscriptionLabel(raw: String?): String? {
        val normalized = raw?.lowercase()?.replace(Regex("[\\s_-]+"), "").orEmpty()
        if (normalized.isBlank()) return null
        return when (normalized) {
            "claudemaxsubscription", "max", "maxplan" -> "Max"
            "claudemax5xsubscription", "max5" -> "Max 5x"
            "claudemax20xsubscription", "max20" -> "Max 20x"
            "claudeenterprisesubscription", "enterprise" -> "Enterprise"
            "claudeteamsubscription", "team" -> "Team"
            "claudeprosubscription", "pro" -> "Pro"
            "claudefreesubscription", "free" -> "Free"
            else -> raw.orEmpty().trim().replaceFirstChar { it.uppercase() }
        }
    }

    fun prependRequiredSystem(blocks: List<String>): List<String> {
        val cleaned = blocks.filter { it.isNotBlank() }
        if (cleaned.firstOrNull() == REQUIRED_SYSTEM) return cleaned
        return listOf(REQUIRED_SYSTEM) + cleaned
    }

    fun authorizationHeaders(accessToken: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $accessToken",
        "anthropic-version" to AiApiClient.ANTHROPIC_VERSION,
        "anthropic-beta" to API_BETA,
        "x-app" to "cli",
    )

    fun tokenRequestBody(
        grantType: String,
        extra: Map<String, String>,
    ): JSONObject {
        val body = JSONObject()
            .put("grant_type", grantType)
            .put("client_id", CLIENT_ID)
        extra.forEach { (k, v) -> body.put(k, v) }
        return body
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun parseObject(body: String): JsonObject =
        json.parseToJsonElement(body).jsonObject

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.obj(key: String): JsonObject? =
        this[key]?.let { runCatching { it.jsonObject }.getOrNull() }

    private fun parseFromRedirectUrl(raw: String): AuthorizationCode? {
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) return null
        return try {
            val uri = URI(raw)
            val query = parseQuery(uri.rawQuery.orEmpty())
            val fragment = parseQuery(uri.rawFragment.orEmpty())
            val code = query["code"] ?: fragment["code"] ?: return null
            val state = query["state"] ?: fragment["state"].orEmpty()
            // Claude's callback often puts `code#state` in the path or fragment.
            if (code.contains('#')) {
                val parts = code.split('#', limit = 2)
                AuthorizationCode(parts[0], parts.getOrElse(1) { state })
            } else {
                AuthorizationCode(code, state)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&').mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val key = decode(pair.substring(0, eq))
            val value = decode(pair.substring(eq + 1))
            key to value
        }.toMap()
    }

    private fun randomBytes(random: SecureRandom, size: Int): ByteArray =
        ByteArray(size).also { random.nextBytes(it) }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun b64url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun decode(value: String): String =
        java.net.URLDecoder.decode(value, Charsets.UTF_8.name())
}
