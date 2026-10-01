package com.macrotracker.util

/**
 * A number as a person types it: "2.5" or "2,5" (a Swedish keyboard's decimal comma).
 * [String.toDoubleOrNull] reads only the first, so "2,5" came back as nothing.
 */
fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
