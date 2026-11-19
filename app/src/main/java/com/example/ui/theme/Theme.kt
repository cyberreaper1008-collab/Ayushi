package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.model.HudTheme

private val ArcCyanColorScheme = darkColorScheme(
    primary = ArcCyan,
    onPrimary = OnArcCyan,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricBlue,
    onSecondary = TextPrimary,
    secondaryContainer = GlassCardHighlight,
    onSecondaryContainer = TextPrimary,
    tertiary = StatusEmerald,
    onTertiary = OnArcCyan,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = DeepNavy,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceNavy,
    onSurfaceVariant = TextSecondary,
    outline = BorderGlowCyan,
    error = StatusRed,
    onError = Color.White
)

private val StarkGoldColorScheme = darkColorScheme(
    primary = StarkGold,
    onPrimary = Color(0xFF1F1200),
    primaryContainer = DeepGoldContainer,
    onPrimaryContainer = TextPrimary,
    secondary = StarkCrimson,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF33141C),
    onSecondaryContainer = TextPrimary,
    tertiary = ArcCyan,
    onTertiary = OnArcCyan,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = Color(0xFF120B0E),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF1E1418),
    onSurfaceVariant = Color(0xFFD6C0A5),
    outline = Color(0x55FFB300),
    error = StatusRed,
    onError = Color.White
)

private val StealthNavyColorScheme = darkColorScheme(
    primary = StealthTeal,
    onPrimary = Color(0xFF00201A),
    primaryContainer = Color(0xFF08352E),
    onPrimaryContainer = TextPrimary,
    secondary = StealthIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF141B3B),
    onSecondaryContainer = TextPrimary,
    tertiary = ArcCyan,
    onTertiary = OnArcCyan,
    background = Color(0xFF02050B),
    onBackground = TextPrimary,
    surface = Color(0xFF08101E),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF101C32),
    onSurfaceVariant = TextSecondary,
    outline = Color(0x5564FFDA),
    error = StatusRed,
    onError = Color.White
)

val JarvisShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JarvisTheme(
    hudTheme: HudTheme = HudTheme.ARC_CYAN,
    content: @Composable () -> Unit
) {
    val colorScheme = when (hudTheme) {
        HudTheme.ARC_CYAN -> ArcCyanColorScheme
        HudTheme.STARK_GOLD -> StarkGoldColorScheme
        HudTheme.STEALTH_NAVY -> StealthNavyColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
