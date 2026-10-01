package com.macrotracker.util

import java.io.InputStream

/**
 * Reads the stream only up to [limit] bytes. Returns null when there is more, without ever
 * holding more than [limit] + 1 bytes: reading a whole picked file first and checking its
 * size after could run out of memory on a large video.
 */
fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        total += n
        if (total > limit) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
