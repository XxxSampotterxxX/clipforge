package dev.clipforge.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {

    private fun clip(id: String, start: Long, end: Long) =
        Clip(id = id, sourceUri = "content://test/$id", sourceStartMs = start, sourceEndMs = end)

    private fun sample() = Timeline(
        listOf(Track("v1", listOf(clip("a", 0, 4_000), clip("b", 1_000, 3_000))))
    )

    @Test
    fun trackDurationIsSumOfClips() {
        assertEquals(6_000L, sample().durationMs)
    }

    @Test
    fun splitKeepsTotalDurationAndSourceRanges() {
        val result = TimelineOps.splitClip(sample(), "a", offsetMs = 1_500, leftId = "a1", rightId = "a2")
        val clips = result.tracks[0].clips

        assertEquals(listOf("a1", "a2", "b"), clips.map { it.id })
        assertEquals(0L, clips[0].sourceStartMs)
        assertEquals(1_500L, clips[0].sourceEndMs)
        assertEquals(1_500L, clips[1].sourceStartMs)
        assertEquals(4_000L, clips[1].sourceEndMs)
        assertEquals(sample().durationMs, result.durationMs)
    }

    @Test
    fun splitRejectsOffsetsOutsideTheClip() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineOps.splitClip(sample(), "a", offsetMs = 0, leftId = "x", rightId = "y")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimelineOps.splitClip(sample(), "a", offsetMs = 4_000, leftId = "x", rightId = "y")
        }
    }

    @Test
    fun deleteRipplesLaterClipsForward() {
        val result = TimelineOps.deleteClip(sample(), "a")

        assertEquals(listOf("b"), result.tracks[0].clips.map { it.id })
        assertEquals(0L, result.tracks[0].startOf(0))
        assertEquals(2_000L, result.durationMs)
    }

    @Test
    fun trimChangesOnlyTheTargetClip() {
        val result = TimelineOps.trimClip(sample(), "a", newStartMs = 500, newEndMs = 2_500)

        assertEquals(2_000L, result.tracks[0].clips[0].durationMs)
        assertEquals(sample().tracks[0].clips[1], result.tracks[0].clips[1])
    }

    @Test
    fun moveReordersWithinTrack() {
        val result = TimelineOps.moveClip(sample(), "b", toIndex = 0)
        assertEquals(listOf("b", "a"), result.tracks[0].clips.map { it.id })
    }

    @Test
    fun editsDoNotMutateTheOriginal() {
        val original = sample()
        TimelineOps.deleteClip(original, "a")
        assertEquals(2, original.tracks[0].clips.size)
    }

    @Test
    fun findClipReturnsNullForUnknownId() {
        assertNull(sample().findClip("missing"))
        assertTrue(sample().findClip("b") != null)
    }

    @Test
    fun historyUndoRedoRoundTrip() {
        val history = EditHistory(sample())
        val edited = TimelineOps.deleteClip(sample(), "a")
        history.push(edited)

        assertTrue(history.canUndo)
        assertEquals(sample(), history.undo())
        assertFalse(history.canUndo)
        assertEquals(edited, history.redo())
    }

    @Test
    fun newEditAfterUndoClearsRedoBranch() {
        val history = EditHistory(sample())
        history.push(TimelineOps.deleteClip(sample(), "a"))
        history.undo()
        history.push(TimelineOps.deleteClip(sample(), "b"))

        assertFalse(history.canRedo)
    }
}
