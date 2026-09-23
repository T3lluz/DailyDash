package com.macrotracker.data.hermes

/**
 * Hermes, as the t3lluz dashboard's bridge (`bridge.py`, under `/_api/`) exposes it.
 *
 * Hermes is an agent in its own container on the server, with a memory. The bridge
 * gives each chat thread a Hermes session, runs the commands Hermes may run, turns the
 * ones it may not into approval cards, and keeps the transcript on the server — so a
 * thread started on the desk can be carried on from the phone, and the other way round.
 */
data class HermesStatus(
    /** The container is up. */
    val up: Boolean,
    /** Its API server answers, which is what a chat needs. */
    val api: Boolean,
    val model: String,
    val modelLabel: String?,
    val version: String?,
    val models: List<HermesModelOption>,
) {
    val ready: Boolean get() = up && api
}

data class HermesModelOption(
    val id: String,
    val label: String,
    val group: String,
    val note: String?,
    val current: Boolean,
)

/**
 * What Hermes may do in a thread. The ids are the bridge's own; the web calls these
 * Chat, Look around, Fix things and Trusted.
 */
enum class HermesPermission(val id: String, val label: String, val blurb: String) {
    CHAT("none", "Chat", "Words only. It does not touch the server."),
    LOOK("read", "Look around", "Runs read-only commands by itself and reports back. Changes are described, not made."),
    ASK("ask", "Ask first", "Reads by itself. Anything that changes the server comes back as a card for you to approve."),
    FULL("full", "Full access", "Reversible changes run by themselves. Deletes, the proxy and a reboot still ask."),
    ;

    companion object {
        fun fromId(id: String?): HermesPermission = when (id?.lowercase()) {
            "none", "chat" -> CHAT
            "read", "diagnose", "plan" -> LOOK
            "full", "write", "acceptedits", "bypasspermissions", "auto", "agent", "build" -> FULL
            else -> ASK
        }
    }
}

data class HermesThreadSummary(
    val id: String,
    val title: String,
    val kind: String,
    val perm: HermesPermission,
    /** The bridge's own id, which may be a CLI mode like `agent`; sent back unchanged so the web keeps its label. */
    val permId: String,
    val busy: Boolean,
    val pinned: Boolean,
    val preview: String,
    val updatedMs: Long,
    /** Approval cards or questions still waiting on someone. */
    val pending: Int,
)

data class HermesTool(val name: String, val state: String, val preview: String) {
    val failed: Boolean get() = state == "failed"
    val running: Boolean get() = state == "started" || state == "running"
}

data class HermesFileChange(val path: String, val plus: Int, val minus: Int)

data class HermesAskCommand(
    val cmd: String,
    val why: String,
    /** `read`, `act` or `grave`. */
    val risk: String,
    /** `pending`, `ran`, `failed` or `skipped`. */
    val state: String,
    val out: String?,
    val code: Int?,
    val what: String?,
    val undo: String?,
) {
    val pending: Boolean get() = state.isBlank() || state == "pending"
}

/** One entry in a thread's transcript, as the bridge stores it. */
sealed interface HermesItem {
    val key: String

    data class User(override val key: String, val text: String) : HermesItem

    /** Command output or an answer handed back to Hermes; drawn as a note, not a bubble. */
    data class Output(override val key: String, val text: String) : HermesItem

    data class Assistant(
        override val key: String,
        val text: String,
        /** Narration that came before the answer ("I'll check the disk first…"). */
        val say: String?,
        val think: String?,
        val thinkMs: Long?,
        val tools: List<HermesTool>,
        val ms: Long?,
        val model: String?,
        val changes: List<HermesFileChange>,
    ) : HermesItem

    data class Exec(
        override val key: String,
        val cmd: String,
        val out: String,
        val code: Int,
        val risk: String,
        val ms: Long?,
        val what: String?,
    ) : HermesItem

    data class Ask(override val key: String, val id: String, val cmds: List<HermesAskCommand>) : HermesItem {
        val settled: Boolean get() = cmds.none { it.pending }
    }

    data class Clarify(
        override val key: String,
        val id: String,
        val question: String,
        val choices: List<String>,
        val state: String,
        val answer: String?,
    ) : HermesItem {
        val open: Boolean get() = state.isBlank() || state == "pending"
    }

    data class Web(
        override val key: String,
        val kind: String,
        val arg: String,
        val title: String?,
        val hits: Int?,
        val error: String?,
    ) : HermesItem

    data class Error(override val key: String, val text: String) : HermesItem
}

data class HermesThread(
    val summary: HermesThreadSummary,
    val items: List<HermesItem>,
)

/** The turn in progress, as its events arrive. */
data class HermesLive(
    val startedAtMs: Long,
    val got: String = "",
    val think: String = "",
    val tools: List<HermesTool> = emptyList(),
    val phase: String = "",
    val round: Int = 0,
)

/** One server-sent event from `/ai/chat` or `/ai/threads/{id}/watch`. */
sealed interface HermesEvent {
    data class Snapshot(val live: HermesLive) : HermesEvent
    data class Meta(val title: String?, val model: String?) : HermesEvent
    data class Delta(val text: String) : HermesEvent
    data class Think(val text: String, val isDelta: Boolean) : HermesEvent
    data class Tool(val tool: HermesTool) : HermesEvent
    data class Phase(val text: String) : HermesEvent
    data class Round(val n: Int) : HermesEvent
    data class Final(val text: String, val say: String?, val ms: Long?) : HermesEvent
    data class Item(val item: HermesItem) : HermesEvent
    data class Done(val error: String?, val stopped: Boolean) : HermesEvent
}

/** What `/ai/exec` answers after an approved command has run. */
data class HermesExecResult(val out: String, val code: Int)
