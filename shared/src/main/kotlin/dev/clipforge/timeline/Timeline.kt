package dev.clipforge.timeline

/**
 * Pure-Kotlin timeline model. No Android dependencies, so it can be unit-tested on the JVM.
 *
 * Two kinds of [Track]:
 * - Magnetic (main): clips sit back to back. Deleting or trimming ripples the rest.
 *   [Clip.timelineStartMs] is ignored here.
 * - Overlay (non-magnetic): each clip sits at its own [Clip.timelineStartMs], with gaps allowed.
 */
data class ColorAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
) {
    init {
        require(listOf(brightness, contrast, saturation).all { it in -1f..1f }) {
            "ColorAdjust values must be in -1..1"
        }
    }

    val isNeutral: Boolean get() = brightness == 0f && contrast == 0f && saturation == 0f

    companion object {
        val NONE = ColorAdjust()
    }
}

data class Clip(
    val id: String,
    val sourceUri: String,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val timelineStartMs: Long = 0L,
    val color: ColorAdjust = ColorAdjust.NONE,
) {
    init {
        require(sourceStartMs >= 0) { "sourceStartMs must be >= 0" }
        require(sourceEndMs > sourceStartMs) { "Clip '$id' must have positive length" }
        require(timelineStartMs >= 0) { "timelineStartMs must be >= 0" }
    }

    val durationMs: Long get() = sourceEndMs - sourceStartMs
}

data class Track(
    val id: String,
    val clips: List<Clip> = emptyList(),
    val magnetic: Boolean = true,
) {
    val durationMs: Long
        get() = if (magnetic) {
            clips.sumOf { it.durationMs }
        } else {
            clips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
        }

    /** Timeline position where the clip at [index] starts. */
    fun startOf(index: Int): Long =
        if (magnetic) clips.take(index).sumOf { it.durationMs } else clips[index].timelineStartMs
}

data class Timeline(val tracks: List<Track> = emptyList()) {
    val durationMs: Long get() = tracks.maxOfOrNull { it.durationMs } ?: 0L

    fun findClip(clipId: String): Pair<Int, Int>? {
        tracks.forEachIndexed { trackIndex, track ->
            val clipIndex = track.clips.indexOfFirst { it.id == clipId }
            if (clipIndex >= 0) return trackIndex to clipIndex
        }
        return null
    }

    fun trackIndexOf(trackId: String): Int? = tracks.indexOfFirst { it.id == trackId }.takeIf { it >= 0 }
}

/** Edit operations. Each returns a new [Timeline]; inputs are never mutated. */
object TimelineOps {

    /**
     * Splits [clipId] at [offsetMs] measured from the start of that clip (not the timeline).
     * The two halves get [leftId] and [rightId]. The right half keeps its absolute position
     * on overlay tracks.
     */
    fun splitClip(
        timeline: Timeline,
        clipId: String,
        offsetMs: Long,
        leftId: String,
        rightId: String,
    ): Timeline {
        val (t, c) = timeline.findClip(clipId) ?: error("Clip '$clipId' not found")
        val clip = timeline.tracks[t].clips[c]
        require(offsetMs > 0 && offsetMs < clip.durationMs) {
            "Split point $offsetMs ms must be inside clip of ${clip.durationMs} ms"
        }
        val splitSource = clip.sourceStartMs + offsetMs
        val left = clip.copy(id = leftId, sourceEndMs = splitSource)
        val right = clip.copy(
            id = rightId,
            sourceStartMs = splitSource,
            timelineStartMs = clip.timelineStartMs + offsetMs,
        )
        return replaceClipInTrack(timeline, t) { clips ->
            clips.toMutableList().apply {
                removeAt(c)
                add(c, right)
                add(c, left)
            }
        }
    }

    /** Changes the in/out points of a clip, expressed in source time. */
    fun trimClip(timeline: Timeline, clipId: String, newStartMs: Long, newEndMs: Long): Timeline {
        val (t, c) = timeline.findClip(clipId) ?: error("Clip '$clipId' not found")
        val updated = timeline.tracks[t].clips[c].copy(sourceStartMs = newStartMs, sourceEndMs = newEndMs)
        return replaceClipInTrack(timeline, t) { clips ->
            clips.toMutableList().apply { set(c, updated) }
        }
    }

    /** Removes a clip. A magnetic track closes the gap automatically. */
    fun deleteClip(timeline: Timeline, clipId: String): Timeline {
        val (t, c) = timeline.findClip(clipId) ?: error("Clip '$clipId' not found")
        return replaceClipInTrack(timeline, t) { clips ->
            clips.toMutableList().apply { removeAt(c) }
        }
    }

    /** Moves a clip to [toIndex] within a magnetic track. */
    fun moveClip(timeline: Timeline, clipId: String, toIndex: Int): Timeline {
        val (t, c) = timeline.findClip(clipId) ?: error("Clip '$clipId' not found")
        require(timeline.tracks[t].magnetic) { "moveClip applies to magnetic tracks; use placeClip for overlays" }
        return replaceClipInTrack(timeline, t) { clips ->
            clips.toMutableList().apply {
                val clip = removeAt(c)
                add(toIndex.coerceIn(0, size), clip)
            }
        }
    }

    /** Sets the absolute start of a clip on an overlay track. */
    fun placeClip(timeline: Timeline, clipId: String, newTimelineStartMs: Long): Timeline {
        val (t, c) = timeline.findClip(clipId) ?: error("Clip '$clipId' not found")
        require(!timeline.tracks[t].magnetic) { "placeClip applies to overlay tracks only" }
        val updated = timeline.tracks[t].clips[c].copy(timelineStartMs = newTimelineStartMs.coerceAtLeast(0L))
        return replaceClipInTrack(timeline, t) { clips ->
            clips.toMutableList().apply { set(c, updated) }
        }
    }

    /** Replaces the color adjustment of a clip. */
    fun setColor(timeline: Timeline, clipId: String, color: ColorAdjust): Timeline {
        val (t, c) = timeline.findClip(clipId) ?: error("Clip '$clipId' not found")
        val updated = timeline.tracks[t].clips[c].copy(color = color)
        return replaceClipInTrack(timeline, t) { clips ->
            clips.toMutableList().apply { set(c, updated) }
        }
    }

    /** Appends [clip] to the track with [trackId]. */
    fun addClip(timeline: Timeline, trackId: String, clip: Clip): Timeline {
        val t = timeline.trackIndexOf(trackId) ?: error("Track '$trackId' not found")
        return replaceClipInTrack(timeline, t) { clips -> clips + clip }
    }

    /** Adds an empty track at the end of the stack. */
    fun addTrack(timeline: Timeline, track: Track): Timeline {
        require(timeline.trackIndexOf(track.id) == null) { "Track '${track.id}' already exists" }
        return Timeline(timeline.tracks + track)
    }

    private fun replaceClipInTrack(
        timeline: Timeline,
        trackIndex: Int,
        transform: (List<Clip>) -> List<Clip>,
    ): Timeline {
        val tracks = timeline.tracks.toMutableList()
        val track = tracks[trackIndex]
        tracks[trackIndex] = track.copy(clips = transform(track.clips))
        return Timeline(tracks)
    }
}

/**
 * Linear undo/redo over immutable snapshots. Every edit pushes a new state;
 * pushing after an undo discards the redo branch.
 */
class EditHistory<T>(initial: T, private val limit: Int = 200) {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()

    var current: T = initial
        private set

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun push(next: T) {
        past.addLast(current)
        if (past.size > limit) past.removeFirst()
        future.clear()
        current = next
    }

    fun undo(): T? {
        if (past.isEmpty()) return null
        future.addLast(current)
        current = past.removeLast()
        return current
    }

    fun redo(): T? {
        if (future.isEmpty()) return null
        past.addLast(current)
        current = future.removeLast()
        return current
    }
}
