package dev.clipforge.export

import android.content.Context
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dev.clipforge.timeline.ColorAdjust
import dev.clipforge.timeline.Timeline

/**
 * Exports the main track of a [Timeline] as one MP4.
 * Each clip reads its original source, applies its in/out points and its color adjustment.
 * Overlay tracks are not exported yet.
 */
class TimelineExporter(private val context: Context) {

    /** Starts the export and returns the running [Transformer], or null if there is nothing to export. */
    fun export(
        timeline: Timeline,
        outputPath: String,
        onResult: (Result<String>) -> Unit,
    ): Transformer? {
        val track = timeline.tracks.firstOrNull()?.takeIf { it.clips.isNotEmpty() }
        if (track == null) {
            onResult(Result.failure(IllegalStateException("ไทม์ไลน์ว่างเปล่า")))
            return null
        }

        val items = track.clips.map { clip ->
            val mediaItem = MediaItem.Builder()
                .setUri(clip.sourceUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.sourceStartMs)
                        .setEndPositionMs(clip.sourceEndMs)
                        .build()
                )
                .build()
            EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(emptyList(), colorEffects(clip.color)))
                .build()
        }

        val composition = Composition.Builder(listOf(EditedMediaItemSequence(items))).build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onResult(Result.success(outputPath))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    onResult(Result.failure(exportException))
                }
            })
            .build()

        transformer.start(composition, outputPath)
        return transformer
    }

    /**
     * Maps [ColorAdjust] to Media3 video effects. Values are in -1..1 in the model.
     * NOTE: the Media3 effect constructors and value ranges here are written from the API
     * documentation and have not been compiled against the library in this environment.
     * Verify them when the Android build first runs.
     */
    private fun colorEffects(color: ColorAdjust): List<Effect> {
        if (color.isNeutral) return emptyList()
        val effects = mutableListOf<Effect>()
        if (color.brightness != 0f) effects += Brightness(color.brightness)
        if (color.contrast != 0f) effects += Contrast(color.contrast)
        if (color.saturation != 0f) {
            effects += HslAdjustment.Builder().adjustSaturation(color.saturation * 100f).build()
        }
        return effects
    }
}
