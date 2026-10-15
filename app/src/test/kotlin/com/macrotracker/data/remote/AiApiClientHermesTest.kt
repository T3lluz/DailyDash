package com.macrotracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class AiApiClientHermesTest {

    @Test
    fun dashboardPathJoinsUnderApi() {
        assertEquals(
            "https://t3lluz.com/_api/v1/chat/completions",
            AiApiClient.dashboardPath("https://t3lluz.com/", "/v1/chat/completions"),
        )
        assertEquals(
            "https://t3lluz.com/_api/ai/status",
            AiApiClient.dashboardPath("https://t3lluz.com", "ai/status"),
        )
    }

    @Test
    fun modelFromStatusPrefersTheCurrentFlag() {
        val body = """
            {"hermes":{"model":"claude:sonnet"},
             "models":[
               {"id":"claude:sonnet","current":false},
               {"id":"cursor:gpt-5.3-codex-high","current":true}
             ]}
        """.trimIndent()
        assertEquals("cursor:gpt-5.3-codex-high", AiApiClient.hermesModelFromStatus(body))
    }

    @Test
    fun modelFromStatusFallsBackToHermesField() {
        val body = """{"hermes":{"model":"claude:opus"}}"""
        assertEquals("claude:opus", AiApiClient.hermesModelFromStatus(body))
    }

    @Test
    fun modelLabelForHermesUsesTheFamilyName() {
        assertEquals("Hermes", AiApiClient.modelLabel(AiProvider.HERMES))
        assertEquals(
            "gpt 5.3 codex high",
            AiApiClient.modelLabel(AiProvider.HERMES, hermesModelId = "cursor:gpt-5.3-codex-high"),
        )
    }
}
