package dev.clipforge.desktop

import javax.swing.SwingUtilities
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent

/**
 * Preview player backed by VLC. Plays one source file at a time; the editor moves to the next
 * clip when [onFinished] fires. Callbacks run on the Swing event thread.
 */
class VlcPreview {

    val component = EmbeddedMediaPlayerComponent()

    /** Index of the clip currently loaded, in the main track. */
    var clipIndex: Int = -1

    var onFinished: () -> Unit = {}

    init {
        component.mediaPlayer().events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun finished(mediaPlayer: MediaPlayer) {
                SwingUtilities.invokeLater { onFinished() }
            }
        })
    }

    /** Starts playback of [path] at [startSeconds] in the source file. */
    fun play(path: String, startSeconds: Double) {
        component.mediaPlayer().media().play(path, ":start-time=${"%.3f".format(startSeconds)}")
    }

    fun pause() {
        component.mediaPlayer().controls().pause()
    }

    fun stop() {
        component.mediaPlayer().controls().stop()
    }

    /** Current position in the source file, in milliseconds. */
    fun sourceTimeMs(): Long = component.mediaPlayer().status().time()

    fun isPlaying(): Boolean = component.mediaPlayer().status().isPlaying

    fun release() {
        component.release()
    }
}
