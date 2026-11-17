package dev.clipforge.desktop

import dev.clipforge.timeline.Clip
import dev.clipforge.timeline.ColorAdjust
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegTest {

    private fun clip(id: String, start: Long, end: Long, color: ColorAdjust = ColorAdjust.NONE) =
        Clip(id = id, sourceUri = "C:/videos/$id.mp4", sourceStartMs = start, sourceEndMs = end, color = color)

    @Test
    fun neutralClipsTrimAndConcatWithoutColorFilter() {
        val graph = buildFilterGraph(listOf(clip("a", 0, 2_000), clip("b", 1_000, 3_500)))

        assertTrue(graph.contains("[0:v]trim=start=0.000:end=2.000"))
        assertTrue(graph.contains("[1:a]atrim=start=1.000:end=3.500"))
        assertTrue(graph.contains("[v0][a0][v1][a1]concat=n=2:v=1:a=1[outv][outa]"))
        assertFalse(graph.contains("eq="))
    }

    @Test
    fun colorAdjustMapsToEqFilterRanges() {
        val graph = buildFilterGraph(
            listOf(clip("a", 0, 1_000, ColorAdjust(brightness = 0.5f, contrast = -0.25f, saturation = 0f)))
        )

        assertTrue(graph.contains("eq=brightness=0.500:contrast=0.750:saturation=1.000"))
    }

    @Test
    fun emptyClipListIsRejected() {
        val failure = runCatching { buildFilterGraph(emptyList()) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
