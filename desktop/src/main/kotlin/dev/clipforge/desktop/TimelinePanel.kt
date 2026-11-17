package dev.clipforge.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import dev.clipforge.timeline.Track

private val RULER_HEIGHT = 24.dp
private val LABEL_WIDTH = 56.dp

/**
 * Timeline with a time ruler, one lane, clip tiles and a playhead.
 * [zoomDp] is how many dp one second occupies. Both the ruler and the lane scroll together.
 */
@Composable
fun TimelinePanel(
    track: Track,
    playheadMs: Long,
    selectedClipId: String?,
    zoomDp: Float,
    onSeek: (Long) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pxPerSecond = zoomDp.dp
    val pxPerSecondRaw = zoomDp * density.density
    val contentWidth = (pxPerSecond * (track.durationMs / 1000f)).coerceAtLeast(640.dp)
    val tickStepSeconds = when {
        zoomDp >= 90f -> 1
        zoomDp >= 40f -> 2
        else -> 5
    }
    val scroll = rememberScrollState()

    Row(modifier) {
        Box(Modifier.width(LABEL_WIDTH).fillMaxHeight().padding(top = RULER_HEIGHT)) {
            Text("V1", color = Palette.TextMuted)
        }

        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
            Box(Modifier.width(contentWidth).fillMaxHeight()) {
                Column(Modifier.fillMaxSize()) {
                    // Ruler: tick marks and time labels. Tapping seeks.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(RULER_HEIGHT)
                            .pointerInput(pxPerSecondRaw) {
                                detectTapGestures { offset ->
                                    onSeek((offset.x / pxPerSecondRaw * 1000f).toLong())
                                }
                            },
                    ) {
                        val maxSecond = (track.durationMs / 1000).toInt()
                        for (second in 0..maxSecond step tickStepSeconds) {
                            Box(
                                Modifier
                                    .offset(x = pxPerSecond * second.toFloat())
                                    .width(1.dp)
                                    .height(RULER_HEIGHT)
                                    .background(Palette.Border)
                            )
                            Text(
                                text = "%d:%02d".format(second / 60, second % 60),
                                color = Palette.TextMuted,
                                modifier = Modifier.offset(x = pxPerSecond * second.toFloat() + 4.dp),
                            )
                        }
                    }

                    // Lane: clip tiles. Tapping empty space seeks.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Palette.PanelRaised)
                            .pointerInput(pxPerSecondRaw, track.durationMs) {
                                detectTapGestures { offset ->
                                    onSeek((offset.x / pxPerSecondRaw * 1000f).toLong())
                                }
                            },
                    ) {
                        track.clips.forEachIndexed { index, clip ->
                            val selected = clip.id == selectedClipId
                            val tint = Palette.ClipColors[index % Palette.ClipColors.size]
                            Box(
                                Modifier
                                    .offset(x = pxPerSecond * (track.startOf(index) / 1000f))
                                    .width(pxPerSecond * (clip.durationMs / 1000f))
                                    .fillMaxHeight()
                                    .padding(vertical = 8.dp, horizontal = 1.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) tint else tint.copy(alpha = 0.55f))
                                    .then(
                                        if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                                        else Modifier
                                    )
                                    .clickable {
                                        onSelect(clip.id)
                                        onSeek(track.startOf(index))
                                    },
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = "คลิป ${index + 1}",
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                )
                            }
                        }
                    }
                }

                // Playhead line across the ruler and lane.
                Box(
                    Modifier
                        .offset(x = pxPerSecond * (playheadMs / 1000f))
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Palette.Playhead)
                )
            }
        }
    }
}
