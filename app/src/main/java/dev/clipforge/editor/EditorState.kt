package dev.clipforge.editor

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.clipforge.timeline.ColorAdjust
import dev.clipforge.timeline.Clip
import dev.clipforge.timeline.EditHistory
import dev.clipforge.timeline.Timeline
import dev.clipforge.timeline.Track
import dev.clipforge.timeline.TimelineOps
import dev.clipforge.timeline.appendToMainTrack
import dev.clipforge.timeline.splitTimelineAt

/**
 * Observable editor state. Every change goes through [commit] so undo/redo stays consistent.
 * Plain Kotlin logic lives in the timeline package; this class only wires it to Compose state.
 */
class EditorState(initial: Timeline = Timeline(listOf(Track(MAIN_ID)))) {

    private val history = EditHistory(initial)

    var timeline by mutableStateOf(initial)
        private set

    var playheadMs by mutableLongStateOf(0L)

    var selectedClipId by mutableStateOf<String?>(null)

    var status by mutableStateOf("เพิ่มคลิปเพื่อเริ่มตัดต่อ")

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    val mainTrack: Track get() = timeline.tracks.first()

    /** The overlay track, if one has been created. */
    val overlayTrack: Track? get() = timeline.tracks.firstOrNull { !it.magnetic }

    val selectedClip: Clip?
        get() {
            val id = selectedClipId ?: return null
            val (trackIndex, clipIndex) = timeline.findClip(id) ?: return null
            return timeline.tracks[trackIndex].clips[clipIndex]
        }

    fun append(clip: Clip) = commit(timeline.appendToMainTrack(clip))

    /** Adds [clip] to the overlay track at the playhead, creating the overlay track if needed. */
    fun addOverlayClip(clip: Clip) {
        var base = timeline
        if (base.trackIndexOf(OVERLAY_ID) == null) {
            base = TimelineOps.addTrack(base, Track(OVERLAY_ID, magnetic = false))
        }
        commit(TimelineOps.addClip(base, OVERLAY_ID, clip.copy(timelineStartMs = playheadMs)))
    }

    fun splitAtPlayhead() {
        val next = splitTimelineAt(timeline, playheadMs)
        if (next == null) {
            status = "วางหัวเล่นให้อยู่กลางคลิปก่อนแยก"
        } else {
            commit(next)
        }
    }

    fun deleteSelected() {
        val id = selectedClipId
        if (id == null) {
            status = "เลือกคลิปก่อนลบ"
            return
        }
        commit(TimelineOps.deleteClip(timeline, id))
        selectedClipId = null
    }

    fun setColorOfSelected(color: ColorAdjust) {
        val id = selectedClipId ?: return
        commit(TimelineOps.setColor(timeline, id, color))
    }

    fun seek(ms: Long) {
        playheadMs = ms.coerceIn(0L, timeline.durationMs)
    }

    fun undo() {
        history.undo()?.let { publish(it) }
    }

    fun redo() {
        history.redo()?.let { publish(it) }
    }

    private fun commit(next: Timeline) {
        history.push(next)
        publish(next)
    }

    private fun publish(next: Timeline) {
        timeline = next
        playheadMs = playheadMs.coerceIn(0L, next.durationMs)
        val selected = selectedClipId
        if (selected != null && next.findClip(selected) == null) selectedClipId = null
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    companion object {
        const val MAIN_ID = "main"
        const val OVERLAY_ID = "overlay"
    }
}
