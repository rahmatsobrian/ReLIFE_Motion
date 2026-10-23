package com.siroha.relifemotion.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.siroha.relifemotion.data.ThemeMode

val Mint = Color(0xFF1DE9B6)

private val AlightDark = darkColorScheme(
    primary = Color(0xFF2EF2C0), onPrimary = Color(0xFF00382B),
    primaryContainer = Mint, onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFFB5C9C1), onSecondary = Color(0xFF20352E),
    secondaryContainer = Color(0xFF38405A), onSecondaryContainer = Color(0xFFDDE1FF),
    tertiary = Color(0xFF9BB8FF), onTertiary = Color(0xFF002A78),
    background = Color(0xFF1B1E2A), onBackground = Color(0xFFE3E4EE),
    surface = Color(0xFF1B1E2A), onSurface = Color(0xFFE3E4EE),
    surfaceVariant = Color(0xFF2A2E40), onSurfaceVariant = Color(0xFFB4B8CC),
    surfaceContainerLowest = Color(0xFF14161F), surfaceContainerLow = Color(0xFF1B1E2A),
    surfaceContainer = Color(0xFF232637), surfaceContainerHigh = Color(0xFF2A2E3F),
    surfaceContainerHighest = Color(0xFF343850),
    outline = Color(0xFF8B90A5), outlineVariant = Color(0xFF454A5E),
    inverseSurface = Color(0xFFE3E4EE), inverseOnSurface = Color(0xFF1F2230)
)

private val AlightLight = lightColorScheme(
    primary = Color(0xFF00876A), onPrimary = Color.White,
    primaryContainer = Mint, onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4B635B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE1FF), onSecondaryContainer = Color(0xFF1B1E2B),
    tertiary = Color(0xFF3F5AA8), onTertiary = Color.White,
    background = Color(0xFFF2F2F6), onBackground = Color(0xFF1B1E2B),
    surface = Color(0xFFF7F7FA), onSurface = Color(0xFF1B1E2B),
    surfaceVariant = Color(0xFFE6E7EE), onSurfaceVariant = Color(0xFF4A4E62),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9F9FC),
    surfaceContainer = Color(0xFFEFF0F5), surfaceContainerHigh = Color(0xFFE8E9F0),
    surfaceContainerHighest = Color(0xFFDFE0E9),
    outline = Color(0xFF767A8E), outlineVariant = Color(0xFFC6C8D6),
    inverseSurface = Color(0xFF1F2233), inverseOnSurface = Color.White
)

private fun amoled(base: ColorScheme): ColorScheme = base.copy(
    background = Color.Black, surface = Color.Black,
    surfaceVariant = Color(0xFF16161A),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF080809),
    surfaceContainer = Color(0xFF101013), surfaceContainerHigh = Color(0xFF17171B),
    surfaceContainerHighest = Color(0xFF1F1F24)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ReLifeTheme(mode: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        else -> true
    }
    val canDynamic = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var scheme: ColorScheme = when {
        canDynamic && dark -> dynamicDarkColorScheme(ctx)
        canDynamic -> dynamicLightColorScheme(ctx)
        dark -> AlightDark
        else -> AlightLight
    }
    if (mode == ThemeMode.AMOLED) scheme = amoled(scheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (view.context as Activity).window
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = AppShapes,
        typography = Typography(),
        content = content
    )
}
