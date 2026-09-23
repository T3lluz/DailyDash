package com.macrotracker.data.server

import android.util.Base64
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Carries a typed [ServerError] out of the SSH layer so the UI can react to the kind of failure. */
class ServerException(val error: ServerError) : Exception(error.message)

data class SshCommandResult(val stdout: String, val stderr: String, val exitStatus: Int)

/**
 * One live SSH session. Commands run over short-lived `exec` channels on a
 * session that stays open across polls — reconnecting every 5 seconds would
 * mean a full key exchange and re-auth per tick, which is both slow and
 * enough failed-login noise to trip fail2ban on a strict server.
 */
class SshConnection internal constructor(
    private val session: Session,
    val hostKeyFingerprint: String,
) {
    val isConnected: Boolean get() = session.isConnected

    fun exec(command: String, timeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS): SshCommandResult {
        val channel = session.openChannel("exec") as ChannelExec
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        try {
            channel.setCommand(command)
            channel.setInputStream(null)
            channel.setErrStream(stderr)
            val input = channel.getInputStream()
            channel.connect(CHANNEL_CONNECT_TIMEOUT_MS)

            val buffer = ByteArray(16 * 1024)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                while (input.available() > 0) {
                    val read = input.read(buffer, 0, buffer.size)
                    if (read < 0) break
                    stdout.write(buffer, 0, read)
                }
                if (channel.isClosed) {
                    // Drain anything that landed between the read and the close.
                    if (input.available() > 0) continue
                    break
                }
                if (System.currentTimeMillis() > deadline) {
                    throw ServerException(ServerError.CommandFailed("Command timed out after ${timeoutMs}ms"))
                }
                Thread.sleep(POLL_SLEEP_MS)
            }
            return SshCommandResult(
                stdout = stdout.toString(Charsets.UTF_8.name()),
                stderr = stderr.toString(Charsets.UTF_8.name()),
                exitStatus = channel.exitStatus,
            )
        } finally {
            runCatching { channel.disconnect() }
        }
    }

    fun disconnect() {
        runCatching { session.disconnect() }
    }

    private companion object {
        const val CHANNEL_CONNECT_TIMEOUT_MS = 15_000
        const val DEFAULT_COMMAND_TIMEOUT_MS = 20_000L
        const val POLL_SLEEP_MS = 25L
    }
}

/**
 * Opens SSH sessions with trust-on-first-use host-key pinning.
 *
 * The pinned fingerprint is checked by a [HostKeyRepository] during key
 * exchange, which happens *before* authentication — so a server presenting an
 * unexpected key is dropped without ever being handed the password. A changed
 * key is refused outright and surfaced as [ServerError.HostKeyChanged]; only an
 * explicit "trust the new key" from the user clears the pin.
 */
@Singleton
class SshClient @Inject constructor(
    private val store: ServerStore,
) {

    suspend fun connect(profile: ServerProfile): SshConnection = withContext(Dispatchers.IO) {
        val jsch = JSch()
        val hostKeys = TofuHostKeyRepository(profile.id, store)
        jsch.hostKeyRepository = hostKeys

        val secret = store.secret(profile.id)
        if (profile.authMode == ServerAuthMode.PRIVATE_KEY) {
            if (secret.isBlank()) {
                throw ServerException(ServerError.AuthFailed("No private key saved for this server"))
            }
            val passphrase = store.keyPassphrase(profile.id)
            try {
                jsch.addIdentity(
                    profile.id,
                    secret.toByteArray(Charsets.UTF_8),
                    null,
                    passphrase.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8),
                )
            } catch (e: JSchException) {
                throw ServerException(
                    ServerError.AuthFailed("Private key could not be read: ${e.message.orEmpty()}"),
                )
            }
        }

        val session = jsch.getSession(profile.username, profile.host, profile.port)
        if (profile.authMode == ServerAuthMode.PASSWORD) {
            if (secret.isBlank()) {
                throw ServerException(ServerError.AuthFailed("No password saved for this server"))
            }
            session.setPassword(secret.toByteArray(Charsets.UTF_8))
        }
        session.setConfig("StrictHostKeyChecking", "ask")
        session.setConfig(
            "PreferredAuthentications",
            if (profile.authMode == ServerAuthMode.PRIVATE_KEY) {
                "publickey"
            } else {
                "password,keyboard-interactive"
            },
        )
        session.setUserInfo(MonitorUserInfo(secret, store.keyPassphrase(profile.id), hostKeys))
        // Keeps NAT/Tailscale paths from silently dropping an idle session.
        session.serverAliveInterval = SERVER_ALIVE_INTERVAL_MS
        session.setConfig("ServerAliveCountMax", "3")

        try {
            session.connect(CONNECT_TIMEOUT_MS)
        } catch (e: JSchException) {
            runCatching { session.disconnect() }
            throw ServerException(mapConnectError(e, hostKeys))
        } catch (e: Exception) {
            runCatching { session.disconnect() }
            throw ServerException(ServerError.Unknown(e.message ?: e.javaClass.simpleName))
        }

        val fingerprint = hostKeys.acceptedFingerprint
            ?: session.hostKey?.let { fingerprintOf(Base64.decode(it.key, Base64.DEFAULT)) }
            ?: ""
        SshConnection(session, fingerprint)
    }

    /**
     * JSch reports everything as [JSchException], so the cause chain and the
     * message are the only way to tell "wrong password" from "box is asleep".
     */
    private fun mapConnectError(e: JSchException, hostKeys: TofuHostKeyRepository): ServerError {
        hostKeys.changedFingerprint?.let { actual ->
            return ServerError.HostKeyChanged(
                expectedFingerprint = hostKeys.pinnedFingerprint.orEmpty(),
                actualFingerprint = actual,
            )
        }
        val message = e.message.orEmpty()
        return when {
            e.cause is UnknownHostException ->
                ServerError.Unreachable("Host not found — check the address, DNS, or your Tailscale connection")
            e.cause is ConnectException || e.cause is NoRouteToHostException ->
                ServerError.Unreachable("Connection refused — is SSH listening on this port?")
            e.cause is SocketTimeoutException || message.contains("timeout", ignoreCase = true) ->
                ServerError.Unreachable("Timed out reaching the server")
            message.contains("Auth fail", ignoreCase = true) ||
                message.contains("Auth cancel", ignoreCase = true) ||
                message.contains("USERAUTH fail", ignoreCase = true) ->
                ServerError.AuthFailed("Authentication failed — check the username and password/key")
            message.contains("reject HostKey", ignoreCase = true) ||
                message.contains("HostKey has been changed", ignoreCase = true) ->
                ServerError.HostKeyRejected("The server's host key was rejected")
            message.contains("Algorithm negotiation fail", ignoreCase = true) ->
                ServerError.Unknown("No shared SSH algorithms — the server may be very old or very locked down")
            else -> ServerError.Unknown(message.ifBlank { "Could not connect" })
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val SERVER_ALIVE_INTERVAL_MS = 20_000
    }
}

/**
 * Pins one fingerprint per server profile.
 *
 * [check] runs mid-handshake, and JSch then asks [UserInfo.promptYesNo] whether
 * to continue. Rather than parsing JSch's prompt text, the outcome of the last
 * check is recorded here and the prompt answers from that: accept a first-time
 * key, refuse a changed one.
 */
internal class TofuHostKeyRepository(
    private val profileId: String,
    private val store: ServerStore,
) : HostKeyRepository {

    /** Set when the server presented a key that does not match the pin. */
    var changedFingerprint: String? = null
        private set

    /** The fingerprint that was actually accepted for this connection. */
    var acceptedFingerprint: String? = null
        private set

    val pinnedFingerprint: String? get() = store.knownHostKey(profileId)

    private var lastResult: Int = HostKeyRepository.NOT_INCLUDED

    /** True only when the last check saw a key we have never seen before. */
    val shouldAcceptPrompt: Boolean get() = lastResult == HostKeyRepository.NOT_INCLUDED

    override fun check(host: String, key: ByteArray): Int {
        val fingerprint = fingerprintOf(key)
        val pinned = store.knownHostKey(profileId)
        lastResult = when {
            pinned == null -> HostKeyRepository.NOT_INCLUDED
            pinned == fingerprint -> HostKeyRepository.OK
            else -> HostKeyRepository.CHANGED
        }
        when (lastResult) {
            HostKeyRepository.CHANGED -> changedFingerprint = fingerprint
            HostKeyRepository.OK -> acceptedFingerprint = fingerprint
        }
        return lastResult
    }

    override fun add(hostkey: HostKey, ui: UserInfo?) {
        val fingerprint = fingerprintOf(Base64.decode(hostkey.key, Base64.DEFAULT))
        store.saveHostKey(profileId, fingerprint)
        acceptedFingerprint = fingerprint
    }

    override fun remove(host: String?, type: String?) = store.forgetHostKey(profileId)

    override fun remove(host: String?, type: String?, key: ByteArray?) = store.forgetHostKey(profileId)

    override fun getKnownHostsRepositoryID(): String = "dailydash-tofu"

    override fun getHostKey(): Array<HostKey> = emptyArray()

    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}

/** Answers JSch's interactive prompts from stored credentials — nothing blocks on UI. */
private class MonitorUserInfo(
    private val password: String,
    private val passphrase: String,
    private val hostKeys: TofuHostKeyRepository,
) : UserInfo {
    override fun getPassphrase(): String = passphrase
    override fun getPassword(): String = password
    override fun promptPassword(message: String?): Boolean = password.isNotEmpty()
    override fun promptPassphrase(message: String?): Boolean = true

    /** Accept an unknown key once (TOFU); never accept one that changed under us. */
    override fun promptYesNo(message: String?): Boolean = hostKeys.shouldAcceptPrompt

    override fun showMessage(message: String?) = Unit
}

/** OpenSSH-style `SHA256:…` fingerprint of a raw host-key blob. */
internal fun fingerprintOf(keyBlob: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(keyBlob)
    return "SHA256:" + Base64.encodeToString(digest, Base64.NO_PADDING or Base64.NO_WRAP)
}
