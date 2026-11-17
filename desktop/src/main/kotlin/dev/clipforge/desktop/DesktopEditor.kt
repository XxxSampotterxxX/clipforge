package dev.clipforge.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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

/** Desktop editor state. Same editing rules as the Android app, using the shared timeline model. */
class DesktopEditor {

    private val history = EditHistory(Timeline(listOf(Track("main"))))

    var timeline by mutableStateOf(history.current)
        private set

    var playheadMs by mutableLongStateOf(0L)

    var selectedClipId by mutableStateOf<String?>(null)

    var status by mutableStateOf("เพิ่มคลิปเพื่อเริ่มตัดต่อ")

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    val mainTrack: Track get() = timeline.tracks.first()

    val selectedClip: Clip?
        get() {
            val id = selectedClipId ?: return null
            val (trackIndex, clipIndex) = timeline.findClip(id) ?: return null
            return timeline.tracks[trackIndex].clips[clipIndex]
        }

    fun append(clip: Clip) = commit(timeline.appendToMainTrack(clip))

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
}
