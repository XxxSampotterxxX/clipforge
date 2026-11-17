package dev.clipforge.timeline

import java.util.UUID

/** Where a timeline position falls: which clip, and how far into that clip. */
data class ClipLocation(val clipIndex: Int, val offsetInClipMs: Long)

/**
 * Maps a position on this track to the clip under it.
 *
 * Magnetic tracks: a position exactly on a boundary belongs to the clip that starts there,
 * and positions past the end map to the end of the last clip.
 * Overlay tracks: returns null when the position falls in a gap or outside every clip.
 */
fun Track.locate(timelineMs: Long): ClipLocation? {
    if (clips.isEmpty()) return null

    if (!magnetic) {
        clips.forEachIndexed { index, clip ->
            val start = startOf(index)
            if (timelineMs >= start && timelineMs < start + clip.durationMs) {
                return ClipLocation(index, timelineMs - start)
            }
        }
        return null
    }

    var start = 0L
    clips.forEachIndexed { index, clip ->
        val end = start + clip.durationMs
        if (timelineMs < end) {
            return ClipLocation(index, (timelineMs - start).coerceAtLeast(0L))
        }
        start = end
    }
    return ClipLocation(clips.lastIndex, clips.last().durationMs)
}

/**
 * Splits the main (first, magnetic) track at [timelineMs]. Returns null when the position is not
 * strictly inside a clip (for example on a boundary or past the end).
 */
fun splitTimelineAt(
    timeline: Timeline,
    timelineMs: Long,
    newId: () -> String = { UUID.randomUUID().toString() },
): Timeline? {
    val track = timeline.tracks.firstOrNull() ?: return null
    val location = track.locate(timelineMs) ?: return null
    val clip = track.clips[location.clipIndex]
    if (location.offsetInClipMs <= 0L || location.offsetInClipMs >= clip.durationMs) return null
    return TimelineOps.splitClip(
        timeline = timeline,
        clipId = clip.id,
        offsetMs = location.offsetInClipMs,
        leftId = newId(),
        rightId = newId(),
    )
}

/** Appends [clip] to the end of the main (first) track. */
fun Timeline.appendToMainTrack(clip: Clip): Timeline {
    val main = tracks.first()
    return Timeline(listOf(main.copy(clips = main.clips + clip)) + tracks.drop(1))
}
