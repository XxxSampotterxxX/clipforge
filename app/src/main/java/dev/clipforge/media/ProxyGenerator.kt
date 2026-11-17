package dev.clipforge.media

import android.content.Context
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Composition
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Builds low-resolution preview copies so the timeline stays smooth on large sources.
 * Export always reads the original file; proxies are for preview only.
 * Call from the main thread: Media3 Transformer must be used on the application looper.
 */
class ProxyGenerator(private val context: Context) {

    private val proxies = mutableMapOf<String, String>()

    /** Path of an existing proxy for [sourceUri], or null if none has been made yet. */
    fun proxyFor(sourceUri: String): String? = proxies[sourceUri]

    suspend fun generate(sourceUri: String, heightPx: Int = PREVIEW_HEIGHT_PX): Result<String> {
        proxies[sourceUri]?.let { return Result.success(it) }

        val out = File(context.cacheDir, "proxy_${sourceUri.hashCode()}_${heightPx}p.mp4")
        if (out.exists() && out.length() > 0L) {
            proxies[sourceUri] = out.absolutePath
            return Result.success(out.absolutePath)
        }

        return suspendCancellableCoroutine { cont ->
            val item = EditedMediaItem.Builder(MediaItem.fromUri(sourceUri))
                .setEffects(Effects(emptyList(), listOf<Effect>(Presentation.createForHeight(heightPx))))
                .build()

            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        proxies[sourceUri] = out.absolutePath
                        if (cont.isActive) cont.resume(Result.success(out.absolutePath))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (cont.isActive) cont.resume(Result.failure(exportException))
                    }
                })
                .build()

            cont.invokeOnCancellation { transformer.cancel() }
            transformer.start(item, out.absolutePath)
        }
    }

    private companion object {
        const val PREVIEW_HEIGHT_PX = 360
    }
}
