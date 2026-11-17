package dev.clipforge.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Design tokens for the desktop editor. Dark, low-glare palette with one violet accent. */
object Palette {
    val Background = Color(0xFF0E1014)
    val Panel = Color(0xFF161A22)
    val PanelRaised = Color(0xFF1E2430)
    val Border = Color(0xFF2A3140)
    val TextPrimary = Color(0xFFE7EAF0)
    val TextMuted = Color(0xFF8B93A7)
    val Accent = Color(0xFF7C5CFF)
    val Playhead = Color(0xFFFF5C7A)

    /** Clip tints, cycled by clip index so neighbouring clips read as separate blocks. */
    val ClipColors = listOf(
        Color(0xFF5B6CFF),
        Color(0xFF22B8A6),
        Color(0xFFF59E42),
        Color(0xFFE65C9C),
        Color(0xFF6BCB77),
        Color(0xFF4DA8DA),
    )
}

@Composable
fun ClipForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color.White,
            background = Palette.Background,
            surface = Palette.Panel,
            onSurface = Palette.TextPrimary,
            surfaceVariant = Palette.PanelRaised,
            secondaryContainer = Palette.PanelRaised,
            onSecondaryContainer = Palette.TextPrimary,
            outline = Palette.Border,
        ),
        content = content,
    )
}
