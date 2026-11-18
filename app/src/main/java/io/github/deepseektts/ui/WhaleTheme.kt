package io.github.deepseektts.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val WhaleLightColors = lightColorScheme(
    primary = Color(0xFF376C94),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E9F7),
    onPrimaryContainer = Color(0xFF102F47),
    secondary = Color(0xFF536A7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E6EF),
    onSecondaryContainer = Color(0xFF1B3343),
    tertiary = Color(0xFF537873),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5EAE4),
    onTertiaryContainer = Color(0xFF163632),
    background = Color(0xFFF0F4F7),
    onBackground = Color(0xFF19242C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF19242C),
    surfaceVariant = Color(0xFFE6EDF2),
    onSurfaceVariant = Color(0xFF43535E),
    outline = Color(0xFF71828D),
    outlineVariant = Color(0xFFD1DBE2),
    error = Color(0xFFB3261E),
)

private val WhaleDarkColors = darkColorScheme(
    primary = Color(0xFFA6CBE7),
    onPrimary = Color(0xFF16344B),
    primaryContainer = Color(0xFF315A77),
    onPrimaryContainer = Color(0xFFD6EBF9),
    secondary = Color(0xFFB8CBD8),
    onSecondary = Color(0xFF263946),
    secondaryContainer = Color(0xFF3B505E),
    onSecondaryContainer = Color(0xFFD6E5EF),
    tertiary = Color(0xFFB1D3C9),
    onTertiary = Color(0xFF203B36),
    tertiaryContainer = Color(0xFF3A5851),
    onTertiaryContainer = Color(0xFFCDE9DF),
    background = Color(0xFF111A20),
    onBackground = Color(0xFFE2EAF0),
    surface = Color(0xFF1A252D),
    onSurface = Color(0xFFE2EAF0),
    surfaceVariant = Color(0xFF293740),
    onSurfaceVariant = Color(0xFFCAD5DD),
    outline = Color(0xFF91A2AE),
    outlineVariant = Color(0xFF40515D),
    error = Color(0xFFFFB4AB),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhaleTheme(dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) WhaleDarkColors else WhaleLightColors
    MaterialExpressiveTheme(colorScheme = colors, content = content)
}