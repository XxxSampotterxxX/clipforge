package dev.clipforge.desktop

import dev.clipforge.timeline.Clip
import dev.clipforge.timeline.ColorAdjust
import java.io.File
import java.util.Locale

/**
 * Builds the ffmpeg filter graph that trims each clip, applies its color adjustment,
 * and concatenates them into one video and one audio stream.
 * Every clip must have an audio stream.
 */
fun buildFilterGraph(clips: List<Clip>): String {
    require(clips.isNotEmpty()) { "No clips to export" }
    val parts = clips.mapIndexed { i, clip ->
        val start = seconds(clip.sourceStartMs)
        val end = seconds(clip.sourceEndMs)
        val video = listOf("trim=start=$start:end=$end", "setpts=PTS-STARTPTS") + colorFilters(clip.color)
        "[$i:v]${video.joinToString(",")}[v$i];" +
            "[$i:a]atrim=start=$start:end=$end,asetpts=PTS-STARTPTS[a$i]"
    }
    val concatInputs = clips.indices.joinToString("") { "[v$it][a$it]" }
    return parts.joinToString(";") + ";" + concatInputs + "concat=n=${clips.size}:v=1:a=1[outv][outa]"
}

/** ffmpeg eq ranges: brightness -1..1, contrast 0..2, saturation 0..3. The model uses -1..1 for all three. */
private fun colorFilters(color: ColorAdjust): List<String> {
    if (color.isNeutral) return emptyList()
    return listOf(
        "eq=brightness=${fixed(color.brightness)}" +
            ":contrast=${fixed(1f + color.contrast)}" +
            ":saturation=${fixed(1f + color.saturation)}"
    )
}

private fun seconds(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1000.0)

private fun fixed(value: Float): String = String.format(Locale.US, "%.3f", value)

/** Runs ffmpeg and returns the output file, or the tail of ffmpeg's log on failure. */
fun exportTimeline(clips: List<Clip>, output: File): Result<File> = runCatching {
    val command = mutableListOf("ffmpeg", "-y", "-hide_banner", "-loglevel", "error")
    clips.forEach { command += listOf("-i", it.sourceUri) }
    command += listOf(
        "-filter_complex", buildFilterGraph(clips),
        "-map", "[outv]", "-map", "[outa]",
        "-c:v", "libx264", "-preset", "medium", "-crf", "20",
        "-c:a", "aac",
        output.absolutePath,
    )
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val log = process.inputStream.bufferedReader().readText()
    val code = process.waitFor()
    check(code == 0) { "ffmpeg exited with $code: ${log.takeLast(600)}" }
    output
}

/** Reads the duration of a media file in milliseconds using ffprobe, or null if unavailable. */
fun probeDurationMs(path: String): Long? = runCatching {
    val process = ProcessBuilder(
        "ffprobe", "-v", "error",
        "-show_entries", "format=duration",
        "-of", "default=nw=1:nk=1",
        path,
    ).redirectErrorStream(true).start()
    val out = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    out.toDoubleOrNull()?.let { (it * 1000).toLong() }
}.getOrNull()
