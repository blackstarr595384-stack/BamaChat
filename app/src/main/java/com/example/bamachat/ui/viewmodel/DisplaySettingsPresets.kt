package com.example.bamachat.ui.viewmodel

data class DisplayTuning(
    val compactChatHeader: Boolean,
    val connectChatBottomBars: Boolean,
    val glassEffectsEnabled: Boolean,
    val cornerRoundnessScale: Float,
    val shadowIntensityScale: Float,
    val surfaceOpacity: Float,
    val fontSizeSp: Float
)

object DisplaySettingsPresets {
    const val COMPACT = "Kompakt"
    const val STANDARD = "Standard"
    const val COMFORT = "Komfort"
    const val DEFAULT_PRIMARY_COLOR = 0xFF6A11CB.toInt()
    const val DEFAULT_FONT_SIZE_SP = 15f

    val options: List<String> = listOf(COMPACT, STANDARD, COMFORT)

    fun normalize(raw: String?): String {
        val candidate = raw?.trim().orEmpty()
        return if (options.contains(candidate)) candidate else STANDARD
    }

    fun tuningFor(preset: String): DisplayTuning = when (normalize(preset)) {
        COMPACT -> DisplayTuning(
            compactChatHeader = true,
            connectChatBottomBars = true,
            glassEffectsEnabled = true,
            cornerRoundnessScale = 0.88f,
            shadowIntensityScale = 0.85f,
            surfaceOpacity = 0.78f,
            fontSizeSp = 14f
        )
        COMFORT -> DisplayTuning(
            compactChatHeader = false,
            connectChatBottomBars = false,
            glassEffectsEnabled = true,
            cornerRoundnessScale = 1.18f,
            shadowIntensityScale = 1.15f,
            surfaceOpacity = 0.92f,
            fontSizeSp = 16f
        )
        else -> DisplayTuning(
            compactChatHeader = true,
            connectChatBottomBars = true,
            glassEffectsEnabled = true,
            cornerRoundnessScale = 1.0f,
            shadowIntensityScale = 1.0f,
            surfaceOpacity = 0.85f,
            fontSizeSp = DEFAULT_FONT_SIZE_SP
        )
    }
}
