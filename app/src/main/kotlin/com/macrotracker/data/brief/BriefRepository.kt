package com.macrotracker.data.brief

import android.content.Context
import androidx.core.content.edit
import com.macrotracker.data.hermes.HermesClient
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and re-runs the dashboard's morning briefing through the bridge.
 *
 * The server is tailnet-only, so the last good answer is kept on disk and shown when it
 * cannot be reached, the way Coming up keeps its timeline.
 */
@Singleton
class BriefRepository @Inject constructor(
    private val client: HermesClient,
    private val settings: SettingsRepository,
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("brief_cache", Context.MODE_PRIVATE)

    fun cached(): DailyBrief? {
        if (prefs.getString(KEY_BASE, null) != base()) return null
        val raw = prefs.getString(KEY_JSON, null) ?: return null
        return runCatching { DailyBrief.parse(JSONObject(raw)) }.getOrNull()
    }

    suspend fun load(): DailyBrief = keep(client.brief())

    /** Writes today's again (or for the first time). Answers at once, `writing`. */
    suspend fun run(): DailyBrief = keep(client.briefRun())

    private fun keep(o: JSONObject): DailyBrief {
        val brief = DailyBrief.parse(o)
        prefs.edit {
            putString(KEY_BASE, base())
            putString(KEY_JSON, o.toString())
        }
        return brief
    }

    private fun base(): String = settings.dashboardServerUrl.value.trim().trimEnd('/')

    private companion object {
        const val KEY_BASE = "base"
        const val KEY_JSON = "json"
    }
}
