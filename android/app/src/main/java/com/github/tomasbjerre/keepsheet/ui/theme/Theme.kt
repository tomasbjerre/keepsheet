package com.github.tomasbjerre.keepsheet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Deliberately not using dynamic color (Material You): KeepSheet has no settings screen
// to opt back out of it. Primary matches the launcher icon.
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF1B5E4F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA6F2DA),
        onPrimaryContainer = Color(0xFF002116),
        secondary = Color(0xFF4C8C7C),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCDE9DE),
        onSecondaryContainer = Color(0xFF082019),
        tertiary = Color(0xFFD84315),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDBCF),
        onTertiaryContainer = Color(0xFF3A0900),
        background = Color(0xFFF7FBF9),
        onBackground = Color(0xFF191C1B),
        surface = Color(0xFFF7FBF9),
        onSurface = Color(0xFF191C1B),
        surfaceVariant = Color(0xFFDCE5E0),
        onSurfaceVariant = Color(0xFF404944),
        outline = Color(0xFF707974),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF8ED9C0),
        onPrimary = Color(0xFF00382A),
        primaryContainer = Color(0xFF00513D),
        onPrimaryContainer = Color(0xFFA6F2DA),
        secondary = Color(0xFFB3CCC2),
        onSecondary = Color(0xFF1F352D),
        secondaryContainer = Color(0xFF354B42),
        onSecondaryContainer = Color(0xFFCDE9DE),
        tertiary = Color(0xFFFFB59D),
        onTertiary = Color(0xFF5F1600),
        tertiaryContainer = Color(0xFF7D2C0D),
        onTertiaryContainer = Color(0xFFFFDBCF),
        background = Color(0xFF0F1512),
        onBackground = Color(0xFFDDE4E0),
        surface = Color(0xFF0F1512),
        onSurface = Color(0xFFDDE4E0),
        surfaceVariant = Color(0xFF404944),
        onSurfaceVariant = Color(0xFFC0C9C3),
        outline = Color(0xFF8A938D),
    )

@Composable
fun KeepSheetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
