package dev.clipforge.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.awt.SwingPanel
import dev.clipforge.timeline.ClipLocation
import dev.clipforge.timeline.Clip
import dev.clipforge.timeline.ColorAdjust
import dev.clipforge.timeline.Track
import dev.clipforge.timeline.locate
import java.io.File
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PX_PER_SECOND: Dp = 48.dp
private val MIN_TIMELINE_WIDTH: Dp = 320.dp

@Composable
fun EditorApp() {
    val scope = rememberCoroutineScope()
    val editor = remember { DesktopEditor() }
    val preview = remember { VlcPreview() }
    var playing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    val track = editor.mainTrack

    DisposableEffect(Unit) {
        onDispose { preview.release() }
    }

    fun playFrom(index: Int, offsetMs: Long) {
        val current = editor.mainTrack
        val clip = current.clips.getOrNull(index)
        if (clip == null) {
            playing = false
            preview.stop()
            return
        }
        preview.clipIndex = index
        preview.play(clip.sourceUri, (clip.sourceStartMs + offsetMs) / 1000.0)
    }

    // When a clip ends during playback, continue with the next one.
    DisposableEffect(Unit) {
        preview.onFinished = {
            if (playing) {
                val next = preview.clipIndex + 1
                if (next < editor.mainTrack.clips.size) {
                    playFrom(next, 0L)
                } else {
                    playing = false
                    editor.seek(editor.mainTrack.durationMs)
                }
            }
        }
        onDispose { preview.onFinished = {} }
    }

    // Keep the playhead in step with the preview while playing.
    androidx.compose.runtime.LaunchedEffect(playing) {
        while (playing) {
            kotlinx.coroutines.delay(100)
            val current = editor.mainTrack
            val index = preview.clipIndex
            val clip = current.clips.getOrNull(index) ?: continue
            val offset = (preview.sourceTimeMs() - clip.sourceStartMs).coerceAtLeast(0L)
            editor.seek(current.startOf(index) + offset)
        }
    }

    fun togglePlay() {
        if (playing) {
            playing = false
            preview.pause()
            return
        }
        val location = track.locate(editor.playheadMs) ?: return
        playing = true
        playFrom(location.clipIndex, location.offsetInClipMs)
    }

    fun seekTo(ms: Long) {
        editor.seek(ms)
    }

    fun addClips() {
        val chooser = JFileChooser().apply {
            isMultiSelectionEnabled = true
            fileFilter = FileNameExtensionFilter("วิดีโอ", "mp4", "mov", "mkv", "avi", "webm")
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        val files = chooser.selectedFiles
        scope.launch {
            for (file in files) {
                editor.status = "กำลังอ่านความยาว ${file.name}..."
                val duration = withContext(Dispatchers.IO) { probeDurationMs(file.absolutePath) }
                if (duration == null || duration <= 0L) {
                    editor.status = "อ่านความยาวของ ${file.name} ไม่ได้ (ตรวจว่าติดตั้ง FFmpeg แล้ว)"
                    continue
                }
                editor.append(
                    Clip(
                        id = UUID.randomUUID().toString(),
                        sourceUri = file.absolutePath,
                        sourceStartMs = 0L,
                        sourceEndMs = duration,
                    )
                )
            }
            if (editor.status.startsWith("กำลังอ่าน")) editor.status = "พร้อมใช้งาน"
        }
    }

    fun exportVideo() {
        val chooser = JFileChooser().apply {
            selectedFile = File("ClipForge-export.mp4")
            fileFilter = FileNameExtensionFilter("MP4", "mp4")
        }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
        var target = chooser.selectedFile
        if (!target.name.endsWith(".mp4", ignoreCase = true)) target = File(target.parentFile, target.name + ".mp4")
        val clips = editor.mainTrack.clips
        exporting = true
        editor.status = "กำลังส่งออก..."
        scope.launch {
            val result = withContext(Dispatchers.IO) { exportTimeline(clips, target) }
            exporting = false
            editor.status = result.fold(
                onSuccess = { "ส่งออกแล้ว: ${it.absolutePath}" },
                onFailure = { "ส่งออกไม่สำเร็จ: ${it.message}" },
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SwingPanel(
            factory = { preview.component },
            modifier = Modifier.fillMaxWidth().height(360.dp),
        )

        Text("หัวเล่น: ${editor.playheadMs} ms / ${editor.timeline.durationMs} ms")

        TimelineStrip(
            track = track,
            playheadMs = editor.playheadMs,
            selectedClipId = editor.selectedClipId,
            onSeek = { seekTo(it) },
            onSelect = { editor.selectedClipId = it },
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )

        editor.selectedClip?.let { selected ->
            ColorControls(
                color = selected.color,
                onChange = { editor.setColorOfSelected(it) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { addClips() }) { Text("เพิ่มคลิป") }
            Button(onClick = { togglePlay() }) { Text(if (playing) "หยุด" else "เล่น") }
            Button(onClick = { editor.splitAtPlayhead() }) { Text("แยกที่หัวเล่น") }
            Button(onClick = { editor.deleteSelected() }) { Text("ลบคลิป") }
            OutlinedButton(onClick = { editor.undo() }, enabled = editor.canUndo) { Text("ย้อนกลับ") }
            OutlinedButton(onClick = { editor.redo() }, enabled = editor.canRedo) { Text("ทำซ้ำ") }
            Button(
                enabled = !exporting && track.clips.isNotEmpty(),
                onClick = { exportVideo() },
            ) { Text("ส่งออก MP4") }
        }

        Text(editor.status)
    }
}

@Composable
private fun ColorControls(color: ColorAdjust, onChange: (ColorAdjust) -> Unit) {
    Column {
        LabeledSlider("ความสว่าง", color.brightness) { onChange(color.copy(brightness = it)) }
        LabeledSlider("คอนทราสต์", color.contrast) { onChange(color.copy(contrast = it)) }
        LabeledSlider("ความอิ่มสี", color.saturation) { onChange(color.copy(saturation = it)) }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Text("$label: ${"%.2f".format(value)}")
    Slider(value = value, onValueChange = onValueChange, valueRange = -1f..1f)
}

@Composable
private fun TimelineStrip(
    track: Track,
    playheadMs: Long,
    selectedClipId: String?,
    onSeek: (Long) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pxPerSecond = with(density) { PX_PER_SECOND.toPx() }
    val totalWidth = (PX_PER_SECOND * (track.durationMs / 1000f)).coerceAtLeast(MIN_TIMELINE_WIDTH)

    Box(modifier.horizontalScroll(rememberScrollState())) {
        Box(
            Modifier
                .width(totalWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(track.durationMs) {
                    detectTapGestures { offset ->
                        onSeek((offset.x / pxPerSecond * 1000f).toLong())
                    }
                },
        ) {
            track.clips.forEachIndexed { index, clip ->
                val selected = clip.id == selectedClipId
                Box(
                    Modifier
                        .offset(x = PX_PER_SECOND * (track.startOf(index) / 1000f))
                        .width(PX_PER_SECOND * (clip.durationMs / 1000f))
                        .fillMaxHeight()
                        .padding(2.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .clickable {
                            onSelect(clip.id)
                            onSeek(track.startOf(index))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}")
                }
            }
            Box(
                Modifier
                    .offset(x = PX_PER_SECOND * (playheadMs / 1000f))
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.Red)
            )
        }
    }
}
