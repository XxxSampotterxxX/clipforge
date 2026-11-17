package dev.clipforge.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTest {

    private fun clip(id: String, start: Long, end: Long, at: Long = 0L) =
        Clip(id = id, sourceUri = "content://test/$id", sourceStartMs = start, sourceEndMs = end, timelineStartMs = at)

    private val main = Track("main", listOf(clip("a", 0, 4_000), clip("b", 0, 2_000)))
    private val overlay = Track("ov", listOf(clip("o1", 0, 1_000, at = 3_000)), magnetic = false)
    private val timeline = Timeline(listOf(main, overlay))

    @Test
    fun overlayDurationIsLatestEndNotSum() {
        assertEquals(4_000L, overlay.durationMs)
        assertEquals(6_000L, main.durationMs)
    }

    @Test
    fun overlayClipStartsAtItsOwnPosition() {
        assertEquals(3_000L, overlay.startOf(0))
    }

    @Test
    fun locateOnOverlayFindsClipInsideItsRange() {
        assertEquals(ClipLocation(0, 500), overlay.locate(3_500))
    }

    @Test
    fun locateOnOverlayGapReturnsNull() {
        assertNull(overlay.locate(1_000))
        assertNull(overlay.locate(4_000))
    }

    @Test
    fun placeClipMovesOverlayAndClampsNegativeToZero() {
        val moved = TimelineOps.placeClip(timeline, "o1", 5_000)
        assertEquals(5_000L, moved.tracks[1].clips[0].timelineStartMs)
        val clamped = TimelineOps.placeClip(timeline, "o1", -10)
        assertEquals(0L, clamped.tracks[1].clips[0].timelineStartMs)
    }

    @Test
    fun placeClipRejectsMagneticTrack() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineOps.placeClip(timeline, "a", 100)
        }
    }

    @Test
    fun moveClipRejectsOverlayTrack() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineOps.moveClip(timeline, "o1", 0)
        }
    }

    @Test
    fun splitOverlayKeepsAbsolutePositionOfRightHalf() {
        val result = TimelineOps.splitClip(timeline, "o1", offsetMs = 400, leftId = "l", rightId = "r")
        val clips = result.tracks[1].clips
        assertEquals(3_000L, clips[0].timelineStartMs)
        assertEquals(3_400L, clips[1].timelineStartMs)
        assertEquals(timeline.tracks[1].durationMs, result.tracks[1].durationMs)
    }

    @Test
    fun deleteOnMagneticTrackStillRipples() {
        val result = TimelineOps.deleteClip(timeline, "a")
        assertEquals(2_000L, result.tracks[0].durationMs)
        assertEquals(0L, result.tracks[0].startOf(0))
    }

    @Test
    fun addClipAppendsToNamedTrack() {
        val result = TimelineOps.addClip(timeline, "ov", clip("o2", 0, 500, at = 9_000))
        assertEquals(listOf("o1", "o2"), result.tracks[1].clips.map { it.id })
        assertEquals(9_500L, result.tracks[1].durationMs)
    }

    @Test
    fun addClipToUnknownTrackFails() {
        assertThrows(IllegalStateException::class.java) {
            TimelineOps.addClip(timeline, "nope", clip("x", 0, 100))
        }
    }

    @Test
    fun addTrackRejectsDuplicateId() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineOps.addTrack(timeline, Track("main"))
        }
        val added = TimelineOps.addTrack(timeline, Track("ov2", magnetic = false))
        assertEquals(3, added.tracks.size)
    }

    @Test
    fun timelineDurationIsLongestTrack() {
        assertEquals(6_000L, timeline.durationMs)
        val longOverlay = TimelineOps.placeClip(timeline, "o1", 10_000)
        assertEquals(11_000L, longOverlay.durationMs)
    }

    @Test
    fun setColorUpdatesOnlyTargetClip() {
        val color = ColorAdjust(brightness = 0.5f, contrast = -0.25f, saturation = 1f)
        val result = TimelineOps.setColor(timeline, "a", color)
        assertEquals(color, result.tracks[0].clips[0].color)
        assertTrue(result.tracks[0].clips[1].color.isNeutral)
        assertFalse(result.tracks[0].clips[0].color.isNeutral)
    }

    @Test
    fun colorAdjustRejectsOutOfRange() {
        assertThrows(IllegalArgumentException::class.java) { ColorAdjust(brightness = 1.5f) }
        assertThrows(IllegalArgumentException::class.java) { ColorAdjust(saturation = -2f) }
    }
}
