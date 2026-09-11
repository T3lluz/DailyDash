package com.macrotracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class ClaudeOAuthTest {

    @Test
    fun pkceVerifierAndChallengeAreUrlSafeAndMatchS256() {
        val pkce = ClaudeOAuth.generatePkce(SecureRandom.getInstance("SHA1PRNG").apply { setSeed(7) })
        assertTrue(pkce.verifier.length >= 32)
        assertTrue(pkce.state.length >= 16)
        assertFalse(pkce.verifier.contains("+") || pkce.verifier.contains("/") || pkce.verifier.contains("="))
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(pkce.verifier.toByteArray(Charsets.US_ASCII)),
        )
        assertEquals(expected, pkce.challenge)
    }

    @Test
    fun eachPkceGenerationIsUnique() {
        val first = ClaudeOAuth.generatePkce()
        val second = ClaudeOAuth.generatePkce()
        assertNotEquals(first.verifier, second.verifier)
        assertNotEquals(first.state, second.state)
    }

    @Test
    fun authorizeUrlIncludesPkceAndManualCallback() {
        val pkce = ClaudeOAuth.Pkce(verifier = "v", challenge = "c", state = "s")
        val url = ClaudeOAuth.authorizeUrl(pkce)
        assertTrue(url.startsWith("https://claude.ai/oauth/authorize?"))
        assertTrue(url.contains("client_id=${ClaudeOAuth.CLIENT_ID}"))
        assertTrue(url.contains("code_challenge=c"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("state=s"))
        assertTrue(url.contains("code=true"))
        assertTrue(url.contains("redirect_uri="))
        assertTrue(url.contains("oauth%2Fcode%2Fcallback"))
    }

    @Test
    fun parseAuthorizationInputSplitsCodeHashState() {
        val parsed = ClaudeOAuth.parseAuthorizationInput("abc123#xyz789", fallbackState = "fallback")
        assertEquals("abc123", parsed.code)
        assertEquals("xyz789", parsed.state)
    }

    @Test
    fun parseAuthorizationInputUsesFallbackStateForBareCode() {
        val parsed = ClaudeOAuth.parseAuthorizationInput("  abc123  ", fallbackState = "fallback")
        assertEquals("abc123", parsed.code)
        assertEquals("fallback", parsed.state)
    }

    @Test
    fun parseAuthorizationInputReadsRedirectUrl() {
        val parsed = ClaudeOAuth.parseAuthorizationInput(
            "https://console.anthropic.com/oauth/code/callback?code=tokA&state=stB",
            fallbackState = "fallback",
        )
        assertEquals("tokA", parsed.code)
        assertEquals("stB", parsed.state)
    }

    @Test
    fun parseTokenResponseReadsAccount() {
        val tokens = ClaudeOAuth.parseTokenResponse(
            """
            {
              "access_token": "sk-ant-oat01-aaa",
              "refresh_token": "sk-ant-ort01-bbb",
              "expires_in": 28800,
              "account": { "email": "ada@example.com", "subscription_type": "claude_pro_subscription" }
            }
            """.trimIndent(),
        )
        assertEquals("sk-ant-oat01-aaa", tokens.accessToken)
        assertEquals("sk-ant-ort01-bbb", tokens.refreshToken)
        assertEquals(28800, tokens.expiresInSec)
        assertEquals("ada@example.com", tokens.email)
        assertEquals("claude_pro_subscription", tokens.subscriptionType)
    }

    @Test(expected = IllegalStateException::class)
    fun parseTokenResponseSurfacesOauthError() {
        ClaudeOAuth.parseTokenResponse(
            """{"error":"invalid_grant","error_description":"code expired"}""",
        )
    }

    @Test
    fun parseProfileReadsEmailAndPlan() {
        val (email, plan) = ClaudeOAuth.parseProfile(
            """{"email":"ada@example.com","subscription_type":"claude_pro_subscription"}""",
        )
        assertEquals("ada@example.com", email)
        assertEquals("claude_pro_subscription", plan)
    }

    @Test
    fun subscriptionLabelNormalizesClaudeCodeTypes() {
        assertEquals("Pro", ClaudeOAuth.subscriptionLabel("claude_pro_subscription"))
        assertEquals("Max 20x", ClaudeOAuth.subscriptionLabel("claudeMax20xSubscription"))
        assertEquals("Team", ClaudeOAuth.subscriptionLabel("team"))
        assertNull(ClaudeOAuth.subscriptionLabel(null))
        assertNull(ClaudeOAuth.subscriptionLabel("  "))
    }

    @Test
    fun prependRequiredSystemIsIdempotent() {
        val once = ClaudeOAuth.prependRequiredSystem(listOf("You are DailyDash."))
        assertEquals(ClaudeOAuth.REQUIRED_SYSTEM, once.first())
        assertEquals("You are DailyDash.", once[1])
        assertEquals(once, ClaudeOAuth.prependRequiredSystem(once))
    }

    @Test
    fun authorizationHeadersUseBearerAndOauthBeta() {
        val headers = ClaudeOAuth.authorizationHeaders("tok")
        assertEquals("Bearer tok", headers["Authorization"])
        assertEquals(ClaudeOAuth.API_BETA, headers["anthropic-beta"])
        assertEquals("2023-06-01", headers["anthropic-version"])
        assertFalse(headers.containsKey("x-api-key"))
    }

    @Test
    fun apiKeyHeadersStayOnXApiKey() {
        val headers = AiApiClient.anthropicHeaders("sk-ant-api", oauth = false)
        assertEquals("sk-ant-api", headers["x-api-key"])
        assertFalse(headers.containsKey("Authorization"))
        val oauth = AiApiClient.anthropicHeaders("oat", oauth = true)
        assertEquals("Bearer oat", oauth["Authorization"])
        assertFalse(oauth.containsKey("x-api-key"))
    }
}
