package dev.clipforge.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the container duration in milliseconds, or 0 if it cannot be read. */
suspend fun readDurationMs(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (e: RuntimeException) {
        0L
    } finally {
        retriever.release()
    }
}
