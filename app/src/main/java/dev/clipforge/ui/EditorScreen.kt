package dev.clipforge.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import dev.clipforge.editor.EditorState
import dev.clipforge.export.TimelineExporter
import dev.clipforge.media.ProxyGenerator
import dev.clipforge.media.readDurationMs
import dev.clipforge.timeline.ColorAdjust
import dev.clipforge.timeline.Clip
import dev.clipforge.timeline.Track
import dev.clipforge.timeline.locate
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val PX_PER_SECOND: Dp = 48.dp
private val MIN_TIMELINE_WIDTH: Dp = 320.dp

@Composable
fun EditorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editor = remember { EditorState() }
    val proxies = remember { ProxyGenerator(context) }
    val exporter = remember { TimelineExporter(context) }
    val proxyUris = remember { mutableStateMapOf<String, String>() }
    val player = remember { ExoPlayer.Builder(context).build() }
    var activeExport: Transformer? by remember { mutableStateOf(null) }
    var pickingOverlay by remember { mutableStateOf(false) }

    val timeline = editor.timeline
    val track = editor.mainTrack

    // Release the player and track seeks made with the player's own controls.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                val current = editor.mainTrack
                val index = player.currentMediaItemIndex
                if (index in current.clips.indices) {
                    editor.playheadMs = current.startOf(index) + player.currentPosition
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Keep the player's playlist in sync with the main track. Uses proxies when they exist.
    LaunchedEffect(timeline, proxyUris.size) {
        val items = track.clips.map { clip ->
            MediaItem.Builder()
                .setUri(proxyUris[clip.sourceUri] ?: clip.sourceUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.sourceStartMs)
                        .setEndPositionMs(clip.sourceEndMs)
                        .build()
                )
                .build()
        }
        val location = track.locate(editor.playheadMs)
        player.setMediaItems(items, location?.clipIndex ?: 0, location?.offsetInClipMs ?: 0L)
        player.prepare()
    }

    // Move the playhead while playing.
    LaunchedEffect(player) {
        while (isActive) {
            if (player.isPlaying) {
                val current = editor.mainTrack
                val index = player.currentMediaItemIndex
                if (index in current.clips.indices) {
                    editor.playheadMs = current.startOf(index) + player.currentPosition
                }
            }
            delay(50)
        }
    }

    fun seekTo(ms: Long) {
        editor.seek(ms)
        val location = editor.mainTrack.locate(editor.playheadMs) ?: return
        player.seekTo(location.clipIndex, location.offsetInClipMs)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val toOverlay = pickingOverlay
        scope.launch {
            editor.status = "กำลังอ่านข้อมูลคลิป..."
            val duration = readDurationMs(context, uri)
            if (duration <= 0L) {
                editor.status = "อ่านความยาววิดีโอไม่ได้"
                return@launch
            }
            val source = uri.toString()
            val clip = Clip(
                id = UUID.randomUUID().toString(),
                sourceUri = source,
                sourceStartMs = 0L,
                sourceEndMs = duration,
            )
            if (toOverlay) {
                editor.addOverlayClip(clip)
                editor.status = "เพิ่มคลิปซ้อนที่หัวเล่นแล้ว (พรีวิวและส่งออกยังไม่รวม)"
                return@launch
            }
            editor.append(clip)
            editor.status = "เพิ่มคลิปแล้ว กำลังสร้างพรีวิว..."
            proxies.generate(source)
                .onSuccess { path ->
                    proxyUris[source] = Uri.fromFile(File(path)).toString()
                    editor.status = "พร้อมใช้งาน"
                }
                .onFailure {
                    editor.status = "สร้างพรีวิวไม่สำเร็จ จะใช้ไฟล์ต้นฉบับแทน"
                }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = true } },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )

        Text("หัวเล่น: ${editor.playheadMs} ms / ${timeline.durationMs} ms")

        TimelineStrip(
            track = track,
            playheadMs = editor.playheadMs,
            selectedClipId = editor.selectedClipId,
            onSeek = { seekTo(it) },
            onSelect = { editor.selectedClipId = it },
            modifier = Modifier.fillMaxWidth().height(72.dp),
        )

        val overlay = editor.overlayTrack
        if (overlay != null) {
            TimelineStrip(
                track = overlay,
                playheadMs = editor.playheadMs,
                selectedClipId = editor.selectedClipId,
                onSeek = { seekTo(it) },
                onSelect = { editor.selectedClipId = it },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
        }

        val selected = editor.selectedClip
        if (selected != null) {
            ColorControls(
                color = selected.color,
                onChange = { editor.setColorOfSelected(it) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = {
                pickingOverlay = false
                picker.launch("video/*")
            }) { Text("เพิ่มคลิป") }
            Button(onClick = {
                pickingOverlay = true
                picker.launch("video/*")
            }) { Text("เพิ่มคลิปซ้อน") }
            Button(onClick = { editor.splitAtPlayhead() }) { Text("แยกที่หัวเล่น") }
            Button(onClick = { editor.deleteSelected() }) { Text("ลบคลิป") }
            OutlinedButton(onClick = { editor.undo() }, enabled = editor.canUndo) { Text("ย้อนกลับ") }
            OutlinedButton(onClick = { editor.redo() }, enabled = editor.canRedo) { Text("ทำซ้ำ") }
            Button(
                enabled = activeExport == null && track.clips.isNotEmpty(),
                onClick = {
                    val out = File(
                        context.getExternalFilesDir(null) ?: context.filesDir,
                        "export_${System.currentTimeMillis()}.mp4",
                    )
                    if (timeline.tracks.drop(1).any { it.clips.isNotEmpty() }) {
                        editor.status = "คลิปซ้อนจะยังไม่ถูกรวมในไฟล์ส่งออกรอบนี้ กำลังส่งออก..."
                    } else {
                        editor.status = "กำลังส่งออก..."
                    }
                    activeExport = exporter.export(timeline, out.absolutePath) { result ->
                        activeExport = null
                        editor.status = result.fold(
                            onSuccess = { "ส่งออกแล้ว: $it" },
                            onFailure = { "ส่งออกไม่สำเร็จ: ${it.message}" },
                        )
                    }
                },
            ) { Text("ส่งออก") }
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

/**
 * Draws one lane. Every clip is positioned at [Track.startOf], so the same code shows
 * magnetic tracks (back to back) and overlay tracks (with gaps).
 */
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
    val scroll = rememberScrollState()

    Box(modifier.horizontalScroll(scroll)) {
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
