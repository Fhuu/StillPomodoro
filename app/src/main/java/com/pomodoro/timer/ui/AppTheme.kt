package com.pomodoro.timer.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    outline = AppColors.CreamDeep,
)

@Composable
fun PomodoroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = Typography(
            displayMedium = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Light,
                fontSize = 56.sp,
                color = AppColors.Ink,
                letterSpacing = (-1).sp
            ),
            titleLarge = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                color = AppColors.Ink
            ),
            titleMedium = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                letterSpacing = 1.2.sp,
                color = AppColors.InkSoft
            ),
            bodySmall = TextStyle(fontSize = 13.sp, color = AppColors.InkSoft),
            labelLarge = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                color = AppColors.InkSoft
            ),
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
        ),
        content = content
    )
}

val SoftCardShape = RoundedCornerShape(14.dp)
