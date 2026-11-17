package dev.clipforge.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import dev.clipforge.timeline.Clip
import dev.clipforge.timeline.ColorAdjust
import dev.clipforge.timeline.locate
import java.io.File
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditorApp() {
    val scope = rememberCoroutineScope()
    val editor = remember { DesktopEditor() }
    val preview = remember { VlcPreview() }
    val focusRequester = remember { FocusRequester() }
    var playing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var zoomDp by remember { mutableFloatStateOf(56f) }

    val track = editor.mainTrack

    DisposableEffect(Unit) {
        onDispose { preview.release() }
    }

    fun playFrom(index: Int, offsetMs: Long) {
        val clip = editor.mainTrack.clips.getOrNull(index)
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
    LaunchedEffect(playing) {
        while (playing) {
            delay(100)
            val current = editor.mainTrack
            val index = preview.clipIndex
            val clip = current.clips.getOrNull(index) ?: continue
            val offset = (preview.sourceTimeMs() - clip.sourceStartMs).coerceAtLeast(0L)
            editor.seek(current.startOf(index) + offset)
        }
    }

    // Focus the root so keyboard shortcuts work as soon as the window opens.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.Spacebar -> { togglePlay(); true }
                    event.key == Key.S -> { editor.splitAtPlayhead(); true }
                    event.key == Key.Delete || event.key == Key.Backspace -> { editor.deleteSelected(); true }
                    event.isCtrlPressed && event.key == Key.Z -> { editor.undo(); true }
                    event.isCtrlPressed && event.key == Key.Y -> { editor.redo(); true }
                    else -> false
                }
            },
    ) {
        TopBar(
            status = editor.status,
            exporting = exporting,
            hasClips = track.clips.isNotEmpty(),
            onAdd = { addClips() },
            onExport = { exportVideo() },
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreviewCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                preview = preview,
                playing = playing,
                timeText = "${formatTime(editor.playheadMs)}  /  ${formatTime(editor.timeline.durationMs)}",
                onTogglePlay = { togglePlay() },
            )
            InspectorCard(
                modifier = Modifier.width(280.dp).fillMaxHeight(),
                track = track,
                selectedClip = editor.selectedClip,
                onChange = { editor.setColorOfSelected(it) },
            )
        }

        TimelineCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(16.dp),
            editor = editor,
            zoomDp = zoomDp,
            onZoom = { zoomDp = it },
        )
    }
}

@Composable
private fun TopBar(
    status: String,
    exporting: Boolean,
    hasClips: Boolean,
    onAdd: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Palette.Panel)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("ClipForge", style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
        Spacer(Modifier.width(16.dp))
        Text(
            text = status,
            color = Palette.TextMuted,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onAdd) { Text("เพิ่มคลิป") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onExport, enabled = !exporting && hasClips) { Text("ส่งออก MP4") }
    }
}

@Composable
private fun PreviewCard(
    modifier: Modifier,
    preview: VlcPreview,
    playing: Boolean,
    timeText: String,
    onTogglePlay: () -> Unit,
) {
    Column(modifier = modifier.card()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black),
        ) {
            SwingPanel(
                factory = { preview.component },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onTogglePlay) { Text(if (playing) "หยุด" else "เล่น") }
            Spacer(Modifier.weight(1f))
            Text(timeText, color = Palette.TextMuted)
        }
    }
}

@Composable
private fun InspectorCard(
    modifier: Modifier,
    track: dev.clipforge.timeline.Track,
    selectedClip: Clip?,
    onChange: (ColorAdjust) -> Unit,
) {
    Column(modifier = modifier.card()) {
        Text("คลิปที่เลือก", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
        Spacer(Modifier.height(12.dp))
        if (selectedClip == null) {
            Text("เลือกคลิปบนไทม์ไลน์เพื่อปรับแต่ง", color = Palette.TextMuted)
            return@Column
        }
        val index = track.clips.indexOfFirst { it.id == selectedClip.id }
        Text("คลิป ${index + 1}", color = Palette.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "ช่วง ${formatTime(selectedClip.sourceStartMs)} – ${formatTime(selectedClip.sourceEndMs)}",
            color = Palette.TextMuted,
        )
        Text("ความยาว ${formatTime(selectedClip.durationMs)}", color = Palette.TextMuted)
        Spacer(Modifier.height(20.dp))
        Text("การปรับสี", style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
        Spacer(Modifier.height(8.dp))
        val color = selectedClip.color
        ColorSlider("ความสว่าง", color.brightness) { onChange(color.copy(brightness = it)) }
        ColorSlider("คอนทราสต์", color.contrast) { onChange(color.copy(contrast = it)) }
        ColorSlider("ความอิ่มสี", color.saturation) { onChange(color.copy(saturation = it)) }
    }
}

@Composable
private fun ColorSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Palette.TextMuted)
        Text("%+.2f".format(value), color = Palette.TextMuted)
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = -1f..1f)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun TimelineCard(
    modifier: Modifier,
    editor: DesktopEditor,
    zoomDp: Float,
    onZoom: (Float) -> Unit,
) {
    Column(modifier = modifier.card()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { editor.splitAtPlayhead() }) { Text("แยก  S") }
            OutlinedButton(onClick = { editor.deleteSelected() }) { Text("ลบ  Del") }
            OutlinedButton(onClick = { editor.undo() }, enabled = editor.canUndo) { Text("ย้อนกลับ") }
            OutlinedButton(onClick = { editor.redo() }, enabled = editor.canRedo) { Text("ทำซ้ำ") }
            Spacer(Modifier.weight(1f))
            Text("ซูม", color = Palette.TextMuted)
            Slider(
                value = zoomDp,
                onValueChange = onZoom,
                valueRange = 24f..160f,
                modifier = Modifier.width(180.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        TimelinePanel(
            track = editor.mainTrack,
            playheadMs = editor.playheadMs,
            selectedClipId = editor.selectedClipId,
            zoomDp = zoomDp,
            onSeek = { editor.seek(it) },
            onSelect = { editor.selectedClipId = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

/** Shared card look: raised panel, rounded corners, hairline border, padding. */
private fun Modifier.card(): Modifier =
    this
        .background(Palette.Panel, RoundedCornerShape(14.dp))
        .border(1.dp, Palette.Border, RoundedCornerShape(14.dp))
        .padding(16.dp)
