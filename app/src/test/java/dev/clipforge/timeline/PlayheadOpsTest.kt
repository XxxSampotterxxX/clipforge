package dev.clipforge.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlayheadOpsTest {

    private fun clip(id: String, length: Long) =
        Clip(id = id, sourceUri = "content://test/$id", sourceStartMs = 0, sourceEndMs = length)

    private val track = Track("main", listOf(clip("a", 2_000), clip("b", 3_000)))
    private val timeline = Timeline(listOf(track))

    @Test
    fun locateFindsClipAndOffset() {
        assertEquals(ClipLocation(0, 500), track.locate(500))
        assertEquals(ClipLocation(1, 1_000), track.locate(3_000))
    }

    @Test
    fun locateOnBoundaryBelongsToNextClip() {
        assertEquals(ClipLocation(1, 0), track.locate(2_000))
    }

    @Test
    fun locatePastEndClampsToLastClipEnd() {
        assertEquals(ClipLocation(1, 3_000), track.locate(99_000))
    }

    @Test
    fun locateOnEmptyTrackIsNull() {
        assertNull(Track("empty").locate(0))
    }

    @Test
    fun splitAtPlayheadInsideClipAddsOneClip() {
        val result = splitTimelineAt(timeline, 3_000, newId = { "id${System.nanoTime()}" })
        assertNotNull(result)
        assertEquals(3, result!!.tracks[0].clips.size)
        assertEquals(timeline.durationMs, result.durationMs)
    }

    @Test
    fun splitOnBoundaryIsNoOp() {
        assertNull(splitTimelineAt(timeline, 2_000))
        assertNull(splitTimelineAt(timeline, 0))
    }

    @Test
    fun appendAddsClipAtEndOfMainTrack() {
        val result = timeline.appendToMainTrack(clip("c", 1_000))
        assertEquals(listOf("a", "b", "c"), result.tracks[0].clips.map { it.id })
        assertEquals(6_000L, result.durationMs)
    }
}
