package com.mapmate.ui.theme

import android.app.Activity
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
    primary = Navy80,
    secondary = BlueGrey80,
    tertiary = Sky80,
)

private val LightColorScheme = lightColorScheme(
    primary = Navy40,
    secondary = BlueGrey40,
    tertiary = Sky40,
    background = SurfaceBlue,
    surface = Color(0xFFFFFFFF),
    surfaceVariant = SurfaceBlueVariant,
    primaryContainer = Color(0xFFD7E3FF),
    secondaryContainer = Color(0xFFDCE7F4),
    tertiaryContainer = Color(0xFFD2EBFF),
    onPrimary = Color.White,
    onBackground = TextNavy,
    onSurface = TextNavy,
    onSurfaceVariant = TextBlueGrey,
    outline = Color(0xFF91A0B5),
    outlineVariant = Color(0xFFD7E0EC),
)

private val MapMateShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(14.dp),
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
