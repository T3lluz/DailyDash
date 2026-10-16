package com.macrotracker.data.remote

import java.util.Locale

/**
 * Claude models offered in Settings, the default first.
 *
 * Prices are Anthropic list rates (USD per 1M tokens) and may drift; they are
 * shown in Settings so the cost of a chat turn is never a surprise.
 */
data class AnthropicModelOption(
    val id: String,
    val displayName: String,
    val inputPerMillionUsd: Double,
    val outputPerMillionUsd: Double,
    val blurb: String,
    val recommended: Boolean = false,
) {
    /** "$5 in / $25 out per 1M" */
    val priceLabel: String
        get() = "$${formatUsd(inputPerMillionUsd)} in / $${formatUsd(outputPerMillionUsd)} out per 1M"

    /**
     * Rough cost of one chat turn (~1.5k input including the system prompt and any
     * server context, ~300 output). Chat turns are far heavier than the one-shot
     * nutrition calls.
     */
    val approxTurnCostLabel: String
        get() {
            val usd = approxTurnUsd()
            return when {
                usd < 0.001 -> "~$${String.format(Locale.US, "%.4f", usd)} / message"
                else -> "~$${String.format(Locale.US, "%.3f", usd)} / message"
            }
        }

    fun approxTurnUsd(inputTokens: Int = 1500, outputTokens: Int = 300): Double =
        (inputTokens / 1_000_000.0) * inputPerMillionUsd +
            (outputTokens / 1_000_000.0) * outputPerMillionUsd

    private fun formatUsd(value: Double): String =
        if (value >= 1.0) {
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        } else {
            String.format(Locale.US, "%.2f", value)
        }
}

object AnthropicModels {
    const val DEFAULT_ID = "claude-opus-5-5"

    val options: List<AnthropicModelOption> = listOf(
        AnthropicModelOption(
            id = "claude-opus-5-5",
            displayName = "Claude Opus 5.5",
            inputPerMillionUsd = 4.00,
            outputPerMillionUsd = 20.00,
            blurb = "Best for building widgets and fixing servers",
            recommended = true,
        ),
        AnthropicModelOption(
            id = "claude-sonnet-5-5",
            displayName = "Claude Sonnet 5.5",
            inputPerMillionUsd = 2.00,
            outputPerMillionUsd = 10.00,
            blurb = "Fast and capable",
        ),
        AnthropicModelOption(
            id = "claude-haiku-5-5",
            displayName = "Claude Haiku 5.5",
            inputPerMillionUsd = 0.10,
            outputPerMillionUsd = 0.50,
            blurb = "Fastest and cheapest; fine for food estimates",
        ),
        AnthropicModelOption(
            id = "claude-fable-5-1",
            displayName = "Claude Fable 5.1",
            inputPerMillionUsd = 10.00,
            outputPerMillionUsd = 50.00,
            blurb = "Most capable, and slowest",
        ),
    )

    /** Ids from earlier builds, so a saved pick carries over to its successor. */
    private val RENAMED = mapOf(
        "claude-opus-5" to "claude-opus-5-5",
        "claude-sonnet-5" to "claude-sonnet-5-5",
        "claude-haiku-4-5" to "claude-haiku-5-5",
    )

    fun find(id: String?): AnthropicModelOption {
        val current = RENAMED[id] ?: id
        return options.find { it.id == current } ?: options.first { it.id == DEFAULT_ID }
    }

    fun resolveId(stored: String?): String = find(stored).id

    /**
     * Sampling parameters were removed from the current models: `temperature` is a 400 on
     * Opus 5.5, Fable 5.1, Sonnet 5.5 and Haiku 5.5. Only Haiku 4.5 and older take it.
     */
    fun acceptsSamplingParams(modelId: String): Boolean =
        modelId.startsWith("claude-haiku-4") || modelId.startsWith("claude-3")

    /** `output_config.effort` works on every current model; Haiku 4.5 rejects it. */
    fun acceptsEffort(modelId: String): Boolean = !modelId.startsWith("claude-haiku-4") && !modelId.startsWith("claude-3")

    /** Adaptive thinking (and its summarized display) on every current model; Haiku 4.5 predates it. */
    fun acceptsAdaptiveThinking(modelId: String): Boolean = acceptsEffort(modelId)

    /**
     * The web search tool with dynamic filtering runs on Opus and Sonnet; Haiku and Fable
     * keep the basic one.
     */
    fun webSearchToolType(modelId: String): String =
        if (modelId.startsWith("claude-opus") || modelId.startsWith("claude-sonnet")) "web_search_20260209" else "web_search_20250305"
}
