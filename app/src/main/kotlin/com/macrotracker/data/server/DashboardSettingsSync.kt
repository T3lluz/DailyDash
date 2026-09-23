package com.macrotracker.data.server

import com.macrotracker.data.hermes.HermesClient
import com.macrotracker.data.hermes.HermesException
import com.macrotracker.data.hermes.HermesLiveFeed
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.remote.TempUnit
import com.macrotracker.data.remote.WindUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The few settings the phone and the web dashboard both have, kept the same through the
 * dashboard's own settings blob (`/_api/sync`, the one every browser tab shares):
 * the mode a new Hermes chat starts in (`ai.perm`), the temperature unit (`units`) and the
 * wind unit (`wind`).
 *
 * The web's protocol is last write wins on the whole blob, so the phone reads it, changes
 * only its own fields and writes it back stamped now. It pulls when the live feed connects
 * and whenever the feed says another device saved.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Singleton
class DashboardSettingsSync @Inject constructor(
    private val client: HermesClient,
    private val feed: HermesLiveFeed,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    /** What the blob held last time it was read or written, so adopting it never echoes back. */
    private var seen: Shared? = null

    private data class Shared(val perm: String?, val temp: TempUnit?, val wind: WindUnit?)

    fun bind() {
        scope.launch {
            feed.connected.filter { it }.collect { pull() }
        }
        scope.launch {
            feed.events.filter { it.optString("ch") == "sync" }.collect { pull() }
        }
        scope.launch {
            combine(settings.hermesPermission, settings.tempUnit, settings.windUnit) { perm, temp, wind ->
                Shared(perm, temp, wind)
            }
                .distinctUntilChanged()
                .debounce(PUSH_DEBOUNCE_MS)
                .collect { local -> if (feed.connected.value) push(local) }
        }
    }

    private suspend fun pull() {
        val data = runCatching { client.syncRead().optJSONObject("data") }.getOrNull() ?: return
        val remote = read(data)
        seen = remote
        remote.perm?.let { if (it != settings.hermesPermission.value) settings.setHermesPermission(it) }
        remote.temp?.let { if (it != settings.tempUnit.value) settings.setTempUnit(it) }
        remote.wind?.let { if (it != settings.windUnit.value) settings.setWindUnit(it) }
    }

    private suspend fun push(local: Shared) {
        val last = seen ?: return // Never write before the web's copy has been read once.
        if (local == last) return
        try {
            val data = client.syncRead().optJSONObject("data") ?: return
            val ai = data.optJSONObject("ai") ?: JSONObject().also { data.put("ai", it) }
            local.perm?.let { ai.put("perm", it) }
            local.temp?.let { data.put("units", it.storageValue) }
            local.wind?.let { data.put("wind", it.storageValue) }
            val at = System.currentTimeMillis()
            data.put("_at", at)
            client.syncWrite(at, data)
            seen = local
        } catch (e: CancellationException) {
            throw e
        } catch (e: HermesException) {
            if (e.code == 409) pull()
        } catch (_: Exception) {
            // Off the tailnet; the next change or connection tries again.
        }
    }

    private fun read(data: JSONObject): Shared = Shared(
        perm = data.optJSONObject("ai")?.optString("perm")?.takeIf { it.isNotBlank() },
        temp = data.optString("units").takeIf { it.isNotBlank() }?.let { v -> TempUnit.entries.firstOrNull { it.storageValue == v } },
        wind = data.optString("wind").takeIf { it.isNotBlank() }?.let { v -> WindUnit.entries.firstOrNull { it.storageValue == v } },
    )

    private companion object {
        const val PUSH_DEBOUNCE_MS = 1_500L
    }
}
