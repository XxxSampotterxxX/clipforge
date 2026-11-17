package dev.clipforge.desktop

import java.util.Locale

/** Formats milliseconds as mm:ss.t for the playhead and clip labels. */
fun formatTime(ms: Long): String {
    val tenths = (ms / 100) % 10
    val seconds = (ms / 1000) % 60
    val minutes = ms / 60_000
    return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
}
