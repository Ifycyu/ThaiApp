package com.thai2chinese.ui.theme

import androidx.compose.ui.graphics.Color

val DarkBg = Color(0xFF1a1a2e)
val DarkSurface = Color(0xFF16213e)
val DarkCard = Color(0xFF1f2940)
val AccentBlue = Color(0xFF4a9eff)
val AccentPurple = Color(0xFF667eea)
val TextPrimary = Color(0xFFe0e0e0)
val TextSecondary = Color(0xFF9ca3af)
val TextMuted = Color(0xFF6b7280)
val ToneMid = Color(0xFF6B7280)
val ToneLow = Color(0xFFEF4444)
val ToneFalling = Color(0xFFF59E0B)
val ToneHigh = Color(0xFF3B82F6)
val ToneRising = Color(0xFF10B981)
val ActiveGreen = Color(0xFF2E7D6F)

fun toneColor(toneNumber: Int): Color = when (toneNumber) {
    1 -> ToneMid; 2 -> ToneLow; 3 -> ToneFalling; 4 -> ToneHigh; 5 -> ToneRising
    else -> TextMuted
}
