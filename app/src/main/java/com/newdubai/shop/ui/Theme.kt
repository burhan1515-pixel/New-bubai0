package com.newdubai.shop.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** "Lapis & Saffron": Afghan lapis-lazuli night with saffron-thread gold accents. */
val Lapis = Color(0xFF3D5AFE)
val LapisDeep = Color(0xFF14234B)
val Saffron = Color(0xFFF2B544)
val Emerald = Color(0xFF34D1A0)
val Ruby = Color(0xFFFF6B6B)

private val Dark = darkColorScheme(
    primary = Saffron, onPrimary = Color(0xFF1A1200),
    secondary = Color(0xFF6C86FF), onSecondary = Color.White,
    tertiary = Emerald, onTertiary = Color(0xFF00281C),
    background = Color(0xFF0A0F1F), onBackground = Color(0xFFEDEBE3),
    surface = Color(0xFF121A33), onSurface = Color(0xFFEDEBE3),
    surfaceVariant = Color(0xFF1B2547), onSurfaceVariant = Color(0xFF9AA3C7),
    outline = Color(0xFF3A4672), error = Ruby, primaryContainer = Color(0xFF3B2D0C), onPrimaryContainer = Saffron
)

private val Light = lightColorScheme(
    primary = Color(0xFF1E3A8A), onPrimary = Color.White,
    secondary = Color(0xFFB87A0C), onSecondary = Color.White,
    tertiary = Color(0xFF0E8F6A), onTertiary = Color.White,
    background = Color(0xFFFBF6EA), onBackground = Color(0xFF14182B),
    surface = Color.White, onSurface = Color(0xFF14182B),
    surfaceVariant = Color(0xFFF0E8D2), onSurfaceVariant = Color(0xFF5B6280),
    outline = Color(0xFFC9BFA3), error = Color(0xFFC62828), primaryContainer = Color(0xFFDDE4FF), onPrimaryContainer = Color(0xFF1E3A8A)
)

@Composable
fun NewDubaiTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp)),
        content = content
    )
}
