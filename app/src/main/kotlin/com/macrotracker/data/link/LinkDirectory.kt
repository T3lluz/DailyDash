package com.macrotracker.data.link

import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.phone.PhoneHubPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A laptop running tandem, as the dashboard lists it. */
data class LinkHost(
    val id: String,
    val name: String,
    /** Where it listens, best first: its tailnet address, then its name. */
    val addresses: List<String>,
    val port: Int,
    /** Shown to the laptop on connecting; it lets this phone in without asking its owner. */
    val ticket: String,
    val online: Boolean,
    /** A phone is controlling it right now. */
    val busy: Boolean,
)

data class LinkDirectoryState(
    val hosts: List<LinkHost> = emptyList(),
    /** False until the dashboard has answered once, so "none found" is not shown while asking. */
    val loaded: Boolean = false,
    /** Why the list could not be read, in words for the screen; null when it could. */
    val problem: String? = null,
)

/**
 * Finds laptops without typing anything: each one announces itself to the dashboard's bridge
 * (`/_api/link`, laptop_link.py there) and this asks the bridge who is around. The phone hub's
 * token proves this is the paired phone, and the answer carries each laptop's ticket.
 */
@Singleton
class LinkDirectory @Inject constructor(
    okHttpClient: OkHttpClient,
    private val settings: SettingsRepository,
    private val hub: PhoneHubPrefs,
) {
    internal val client = okHttpClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .apply { interceptors().removeAll { it is okhttp3.logging.HttpLoggingInterceptor } }
        .build()

    suspend fun laptops(): LinkDirectoryState = withContext(Dispatchers.IO) {
        val base = settings.dashboardServerUrl.value.trim().trimEnd('/')
        if (base.isBlank()) return@withContext LinkDirectoryState(loaded = true, problem = "No dashboard is set up, so laptops can't announce themselves.")
        try {
            val request = Request.Builder().url("$base/_api/link/hosts").header("X-Phone-Token", hub.token).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 403 -> LinkDirectoryState(loaded = true, problem = "Turn on Phone hub in Connections: the dashboard only lists laptops for its paired phone.")
                    !response.isSuccessful -> LinkDirectoryState(loaded = true, problem = "The dashboard answered ${response.code}.")
                    else -> LinkDirectoryState(hosts = notUs(base, parseLaptops(JSONObject(body))), loaded = true)
                }
            }
        } catch (e: IOException) {
            LinkDirectoryState(loaded = true, problem = "The dashboard didn't answer.")
        } catch (e: org.json.JSONException) {
            LinkDirectoryState(loaded = true, problem = "The dashboard's answer made no sense.")
        }
    }
}

/**
 * A laptop the bridge calls busy may be busy with this very phone: a session it left a moment
 * ago, not yet gone. The dashboard's own list says who each session is from; one from this
 * phone's addresses doesn't count.
 */
private fun LinkDirectory.notUs(base: String, hosts: List<LinkHost>): List<LinkHost> {
    if (hosts.none { it.busy }) return hosts
    val mine = ownAddresses()
    val peers = try {
        val request = Request.Builder().url("$base/_api/link").get().build()
        client.newCall(request).execute().use { r -> if (r.isSuccessful) sessionPeers(JSONObject(r.body?.string().orEmpty())) else return hosts }
    } catch (_: Exception) {
        return hosts
    }
    return hosts.map { h -> if (h.busy && peers[h.id]?.all { it in mine } == true) h.copy(busy = false) else h }
}

/** Each laptop's sessions, by where they come from, out of the dashboard's `/_api/link`. */
internal fun sessionPeers(o: JSONObject): Map<String, List<String>> = buildMap {
    val array = o.optJSONArray("hosts") ?: return@buildMap
    for (i in 0 until array.length()) {
        val h = array.optJSONObject(i) ?: continue
        val sessions = h.optJSONObject("state")?.optJSONArray("sessions") ?: continue
        put(h.optString("id"), (0 until sessions.length()).mapNotNull { sessions.optJSONObject(it)?.optString("peer")?.ifBlank { null } })
    }
}

/** This phone's own addresses, its tailnet one among them. */
internal fun ownAddresses(): Set<String> = try {
    java.net.NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .mapNotNull { it.hostAddress?.substringBefore('%') }
        .toSet()
} catch (_: Exception) {
    emptySet()
}

/** The bridge's answer as laptops. One without an address is left out: there is nowhere to connect to. */
internal fun parseLaptops(o: JSONObject): List<LinkHost> = buildList {
    val array = o.optJSONArray("hosts") ?: return@buildList
    for (i in 0 until array.length()) {
        val h = array.optJSONObject(i) ?: continue
        val addresses = h.optJSONArray("addrs")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
        if (addresses.isEmpty()) continue
        add(
            LinkHost(
                id = h.optString("id"), name = h.optString("name").ifBlank { addresses.last() },
                addresses = addresses, port = h.optInt("port", LinkProtocol.PORT),
                ticket = h.optString("ticket"), online = h.optBoolean("online"), busy = h.optBoolean("busy"),
            ),
        )
    }
}
