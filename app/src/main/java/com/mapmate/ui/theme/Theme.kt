package com.mapmate.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9EC3F7),
    onPrimary = Color(0xFF0B2E59),
    primaryContainer = Color(0xFF213F64),
    onPrimaryContainer = Color(0xFFD5E5FF),
    secondary = Color(0xFFB8C5CD),
    onSecondary = Color(0xFF26343C),
    secondaryContainer = Color(0xFF303C44),
    onSecondaryContainer = Color(0xFFD9E4EC),
    tertiary = Color(0xFF7BD1BE),
    onTertiary = Color(0xFF00382D),
    tertiaryContainer = Color(0xFF124D40),
    onTertiaryContainer = Color(0xFFA6F2DE),
    background = Color(0xFF121416),
    surface = Color(0xFF191D20),
    surfaceVariant = Color(0xFF303638),
    onBackground = Color(0xFFE5E8EA),
    onSurface = Color(0xFFE5E8EA),
    onSurfaceVariant = Color(0xFFBCC3C7),
    outline = Color(0xFF8B959A),
    outlineVariant = Color(0xFF3E474B),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1767D2),
    secondary = Color(0xFF506270),
    tertiary = Color(0xFF07845B),
    onTertiary = Color.White,
    background = Color(0xFFF2F4F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDF0F2),
    primaryContainer = Color(0xFFE8F1FF),
    secondaryContainer = Color(0xFFE9EEF0),
    tertiaryContainer = Color(0xFFD8F2E8),
    onTertiaryContainer = Color(0xFF004E3E),
    onPrimary = Color.White,
    onBackground = Color(0xFF172128),
    onSurface = Color(0xFF172128),
    onSurfaceVariant = Color(0xFF59676E),
    outline = Color(0xFF839097),
    outlineVariant = Color(0xFFD6DFE3),
)

private val MapMateShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun MapMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = MapMateShapes,
        content = content
    )
}
