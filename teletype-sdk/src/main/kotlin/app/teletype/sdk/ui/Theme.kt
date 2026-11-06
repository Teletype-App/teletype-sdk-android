package app.teletype.sdk.ui

import androidx.compose.ui.graphics.Color

data class TeletypeTheme(
    val primaryColor: Color = Color(0xFF0084FF),
    val onPrimaryColor: Color = Color.White,
    val backgroundColor: Color = Color(0xFFF4F6F8),
    val headerTextColor: Color = Color.White,
    val headerSubTextColor: Color = Color.White.copy(alpha = 0.9f),
    val ownBubbleColor: Color = Color(0xFF0084FF),
    val ownTextColor: Color = Color.White,
    val operatorBubbleColor: Color = Color(0xFFECEEF1),
    val operatorTextColor: Color = Color(0xFF1A1A1A),
    val mutedColor: Color = Color(0xFF8A949E),
    val borderColor: Color = Color(0xFFE3E8EE),
    val inputBackground: Color = Color.White,
    val inputTextColor: Color = Color(0xFF1A1A1A),
    val badgeColor: Color = Color(0xFFFF3B30),
    val badgeTextColor: Color = Color.White,
    val bubbleRadius: Int = 20,
    val buttonSize: Int = 60,
)

val DefaultTheme = TeletypeTheme()

fun parseColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.trim().removePrefix("#")
    val value = when (cleaned.length) {
        6 -> cleaned.toLongOrNull(16)?.or(0xFF000000L)
        8 -> cleaned.toLongOrNull(16)
        else -> null
    } ?: return null
    return Color(value.toInt())
}

fun mergeTheme(overrides: TeletypeTheme? = null, primaryFromConfig: String? = null): TeletypeTheme {
    var base = DefaultTheme
    parseColor(primaryFromConfig)?.let { color ->
        base = base.copy(primaryColor = color, ownBubbleColor = color)
    }
    return overrides ?: base
}
