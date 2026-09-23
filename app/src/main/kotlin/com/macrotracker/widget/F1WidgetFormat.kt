package com.macrotracker.widget

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "Formula 1 Grand Prix of Monaco" / "Monaco Grand Prix" → "Monaco GP". */
internal fun cleanRaceName(name: String): String {
    val noPrefix = name.removePrefix("Formula 1 ").trim()
    return if (noPrefix.startsWith("Grand Prix of ")) {
        "${noPrefix.removePrefix("Grand Prix of ").trim()} GP"
    } else {
        noPrefix.replace("Grand Prix", "GP").trim()
    }
}

/** Session label as a short chip: SQ, SPR, QUALI, RACE, FP1… */
internal fun sessionAbbrev(label: String): String = when {
    label.startsWith("Sprint Quali", ignoreCase = true) -> "SQ"
    label.startsWith("Sprint", ignoreCase = true) -> "SPR"
    label.startsWith("Qualifying", ignoreCase = true) -> "QUALI"
    label.startsWith("Race", ignoreCase = true) -> "RACE"
    label.startsWith("FP", ignoreCase = true) -> label.take(3).uppercase()
    else -> label.take(5).uppercase()
}

/** A UTC session date + time from OpenF1, as local "HH:mm"; empty when either part is missing. */
internal fun sessionLocalTime(dateStr: String?, timeStr: String?): String {
    if (dateStr == null || timeStr == null) return ""
    return try {
        LocalDateTime.parse("${dateStr}T${timeStr.trimEnd('Z')}", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneId.of("UTC"))
            .withZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        ""
    }
}

/** "2026-05-24" → "Sun 24 May". */
internal fun sessionLongDate(dateStr: String?): String {
    if (dateStr == null) return ""
    return try {
        LocalDate.parse(dateStr).format(DateTimeFormatter.ofPattern("EEE d MMM"))
    } catch (_: Exception) {
        dateStr
    }
}
