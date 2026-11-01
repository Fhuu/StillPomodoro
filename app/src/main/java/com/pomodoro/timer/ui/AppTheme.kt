package com.pomodoro.timer.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppColors {
    val Cream = Color(0xFFF4EFE6)
    val CreamDeep = Color(0xFFE8E0D4)
    val Ink = Color(0xFF2A2826)
    val InkSoft = Color(0xFF6B6560)
    val Terracotta = Color(0xFFC56B4A)
    val TerracottaDark = Color(0xFF9E5238)
    val Sage = Color(0xFF8A9A7B)
    val Island = Color(0xF022211F)
    val IslandStroke = Color(0xFF4A4541)
    val IslandText = Color(0xFFF7F3EC)
}

private val LightScheme = lightColorScheme(
    primary = AppColors.Terracotta,
    onPrimary = Color.White,
    secondary = AppColors.Sage,
    onSecondary = Color.White,
    background = AppColors.Cream,
    onBackground = AppColors.Ink,
    surface = Color.White,
    onSurface = AppColors.Ink,
    onSurfaceVariant = AppColors.InkSoft,
    surfaceVariant = AppColors.CreamDeep,
    tertiary = AppColors.Ink,
    onTertiary = Color.White,
    outline = AppColors.CreamDeep,
    outlineVariant = AppColors.CreamDeep,
)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFF448AFF),
    onPrimary = Color(0xFF00112E),
    primaryContainer = Color(0xFF123CA1),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF6D9FFF),
    onSecondary = Color(0xFF001B47),
    secondaryContainer = Color(0xFF0D2451),
    onSecondaryContainer = Color(0xFFC8DDFF),
    tertiary = Color(0xFFFFA877),
    onTertiary = Color(0xFF351400),
    tertiaryContainer = Color(0xFF50301D),
    onTertiaryContainer = Color(0xFFFFDCC6),
    background = Color(0xFF030611),
    onBackground = Color(0xFFEDF6FF),
    surface = Color(0xFF090E1A),
    onSurface = Color(0xFFEDF6FF),
    surfaceVariant = Color(0xFF121E33),
    onSurfaceVariant = Color(0xFFAFC6DB),
    outline = Color(0xFF5B7893),
    outlineVariant = Color(0xFF293F55),
    error = Color(0xFFFF9F8A),
    onError = Color(0xFF3B0900),
    errorContainer = Color(0xFF591D13),
    onErrorContainer = Color(0xFFFFDAD2),
)

@Composable
fun PomodoroTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            displayMedium = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Light,
                fontSize = 56.sp,
                letterSpacing = (-1).sp
            ),
            titleLarge = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
            ),
            titleMedium = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                letterSpacing = 1.2.sp,
            ),
            bodySmall = TextStyle(fontSize = 13.sp),
            labelLarge = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
            ),
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
        ),
        content = {
            CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
        }
    )
}

val SoftCardShape = RoundedCornerShape(14.dp)
