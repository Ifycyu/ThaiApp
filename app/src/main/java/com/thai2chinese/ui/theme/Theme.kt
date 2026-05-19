package com.thai2chinese.ui.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color

// 背景
val DarkBg = Color(0xFFF8FAEF)
val DarkSurface = Color(0xFFD8DBCF)
val DarkCard = Color(0xFFE8EBDF)

// 强调色
val AccentBlue = Color(0xFF3B7DD8)
val AccentPurple = Color(0xFF7C5CBF)

// 文字
val TextPrimary = Color(0xFF1A1D15)
val TextSecondary = Color(0xFF4F5249)
val TextMuted = Color(0xFF8A8D84)

// 声调颜色
val ToneMid = Color(0xFF6B7280)
val ToneLow = Color(0xFFEF4444)
val ToneFalling = Color(0xFFF59E0B)
val ToneHigh = Color(0xFF3B82F6)
val ToneRising = Color(0xFF10B981)

// 高亮
val ActiveGreen = Color(0xFF2E7D6F)

fun toneColor(toneNumber: Int): Color = when (toneNumber) {
    1 -> ToneMid; 2 -> ToneLow; 3 -> ToneFalling; 4 -> ToneHigh; 5 -> ToneRising
    else -> TextMuted
}

val AppTextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    focusedBorderColor = AccentBlue, unfocusedBorderColor = DarkCard,
    cursorColor = AccentBlue, focusedContainerColor = DarkCard, unfocusedContainerColor = DarkCard
)
