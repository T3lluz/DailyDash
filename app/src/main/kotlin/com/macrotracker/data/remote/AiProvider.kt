package com.macrotracker.data.remote

/**
 * AI backend used for nutrition estimates, label scanning, weather summaries,
 * widget tips, Tilesmith, and the two chat bots on the AI tab.
 *
 * [HERMES] is the model on the t3lluz dashboard (the same brain Tech support
 * chats with). It needs the dashboard URL, not a key on the phone.
 */
enum class AiProvider(val storageValue: String, val displayName: String) {
    GEMINI("gemini", "Gemini"),
    OPENAI("openai", "OpenAI"),
    OPENROUTER("openrouter", "OpenRouter"),
    ANTHROPIC("anthropic", "Claude"),
    HERMES("hermes", "Hermes");

    /** No API key on the phone; answers come from `<dashboard>/_api`. */
    val usesDashboard: Boolean get() = this == HERMES

    companion object {
        fun fromStorage(value: String?): AiProvider =
            entries.find { it.storageValue.equals(value, ignoreCase = true) } ?: GEMINI
    }
}
