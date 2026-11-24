package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun ZenncodeTheme(
    palette: IdePalette = IdeThemes.zenncodeDarkPlus,
    content: @Composable () -> Unit
) {
    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.syntaxType,
            onSecondary = palette.onAccent,
            tertiary = palette.syntaxKeyword,
            background = palette.editorBg,
            onBackground = palette.textPrimary,
            surface = palette.sidebarBg,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textMuted,
            outline = palette.border,
            error = palette.errorColor
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.syntaxType,
            onSecondary = palette.onAccent,
            tertiary = palette.syntaxKeyword,
            background = palette.editorBg,
            onBackground = palette.textPrimary,
            surface = palette.sidebarBg,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textMuted,
            outline = palette.border,
            error = palette.errorColor
        )
    }

    CompositionLocalProvider(LocalIdePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
