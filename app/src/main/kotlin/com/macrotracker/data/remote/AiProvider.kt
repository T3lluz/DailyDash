package com.macrotracker.data.remote

/**
 * AI backend used for nutrition estimates, label scanning, weather summaries,
 * widget tips, Tilesmith, and the two chat bots on the AI tab.
 *
 * Listed the way Settings shows them: your plans first (Claude, Cursor), then the
 * dashboard's own model, then the providers that take an API key.
 *
 * [CURSOR] is your Cursor plan through the official Cursor CLI on the dashboard server
 * (Cursor has no API an app can sign in to); [HERMES] is the model the dashboard runs.
 * Neither keeps a key on the phone; both need the dashboard address and the tailnet.
 */
enum class AiProvider(val storageValue: String, val displayName: String) {
    ANTHROPIC("anthropic", "Claude"),
    CURSOR("cursor", "Cursor"),
    HERMES("hermes", "Hermes"),
    GEMINI("gemini", "Gemini"),
    OPENAI("openai", "OpenAI");

    /** No API key on the phone; answers come from `<dashboard>/_api`. */
    val usesDashboard: Boolean get() = this == HERMES || this == CURSOR

    /** Takes a pasted API key (Claude also signs in with an account instead). */
    val takesKey: Boolean get() = this == GEMINI || this == OPENAI || this == ANTHROPIC

    /** Can read a photo (meal photos, label scans, a widget to copy in Tilesmith). */
    val seesPictures: Boolean get() = !usesDashboard

    companion object {
        /** A stored provider this build no longer has (OpenRouter) falls back to Gemini. */
        fun fromStorage(value: String?): AiProvider =
            entries.find { it.storageValue.equals(value, ignoreCase = true) } ?: GEMINI
    }
}

/**
 * How the phone is let in to a provider, for the badge every AI picker shows next to it:
 * signed in to an account (a plan's usage), a pasted API key (billed per token), or the
 * dashboard (no key on the phone).
 */
enum class AiAccess(val label: String) {
    ACCOUNT("Account"),
    API_KEY("API key"),
    DASHBOARD("Dashboard"),
    NONE("Not set up"),
}
