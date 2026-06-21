package com.example.bamachat.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppDesignPreset(val label: String) {
    FUTURISTIC("Futuristic AI"),
    DEEP_SPACE("Deep Space"),
    NEON_NOIR("Neon Noir");

    companion object {
        val labels: List<String> = entries.map { it.label }

        fun fromStored(raw: String?): AppDesignPreset {
            val value = raw.orEmpty().trim().lowercase()
            return when (value) {
                "futuristic ai", "aktuell", "futuristic" -> FUTURISTIC
                "deep space", "deepspace" -> DEEP_SPACE
                "neon noir", "neonnoir" -> NEON_NOIR
                else -> FUTURISTIC
            }
        }
    }
}

data class AppDesignPalette(
    val screenBgTop: Color,
    val screenBgMid: Color,
    val screenBgBottom: Color,
    val heroBg: Color,
    val heroBorder: Color,
    val heroTitle: Color,
    val heroSubtitle: Color,
    val heroOverline: Color,
    val surface: Color,
    val surfaceBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentStrong: Color,
    val navContainer: Color,
    val navIndicator: Color,
    val navUnselected: Color,
    val chatBgTop: Color,
    val chatBgMid: Color,
    val chatBgBottom: Color,
    val chatHeaderStart: Color,
    val chatHeaderMid: Color,
    val chatHeaderEnd: Color,
    val chatComposerBg: Color,
    val chatComposerFieldBg: Color,
    val chatNeutralControlBg: Color,
    val chatUserBubbleStart: Color,
    val chatUserBubbleEnd: Color,
    val chatAssistantSurface: Color,
    val chatUserAvatarBg: Color
)

object AppDesignSystem {
    fun normalizePresetLabel(raw: String?): String = AppDesignPreset.fromStored(raw).label

    fun paletteForStored(raw: String?): AppDesignPalette = palette(AppDesignPreset.fromStored(raw))

    fun palette(preset: AppDesignPreset): AppDesignPalette = when (preset) {
        AppDesignPreset.FUTURISTIC -> AppDesignPalette(
            screenBgTop = Color(0xFF0D0D1A),
            screenBgMid = Color(0xFF14142A),
            screenBgBottom = Color(0xFF1A1A2E),
            heroBg = Color(0xFF16162E),
            heroBorder = Color(0xFF2D2D5E),
            heroTitle = Color(0xFFF0F0FF),
            heroSubtitle = Color(0xFFB0B0CC),
            heroOverline = Color(0xFF8888AA),
            surface = Color(0xFF1E1E3A),
            surfaceBorder = Color(0xFF333360),
            textPrimary = Color(0xFFF0F0FF),
            textSecondary = Color(0xFFB0B0CC),
            accent = Color(0xFFBB86FC),
            accentStrong = Color(0xFF9C5CFF),
            navContainer = Color(0xFF1A1A35),
            navIndicator = Color(0xFF2D2D5E),
            navUnselected = Color(0xFF707090),
            chatBgTop = Color(0xFF0D0D1A),
            chatBgMid = Color(0xFF14142A),
            chatBgBottom = Color(0xFF1A1A2E),
            chatHeaderStart = Color(0xFF1A1A35),
            chatHeaderMid = Color(0xFF14142A),
            chatHeaderEnd = Color(0xFF0D0D1A),
            chatComposerBg = Color(0xFF1A1A35),
            chatComposerFieldBg = Color(0xFF222240),
            chatNeutralControlBg = Color(0xFF2D2D5E),
            chatUserBubbleStart = Color(0xFFBB86FC),
            chatUserBubbleEnd = Color(0xFF7C4DFF),
            chatAssistantSurface = Color(0xFF222240),
            chatUserAvatarBg = Color(0xFF333360)
        )
        AppDesignPreset.DEEP_SPACE -> AppDesignPalette(
            screenBgTop = Color(0xFF050510),
            screenBgMid = Color(0xFF0A0A1A),
            screenBgBottom = Color(0xFF101025),
            heroBg = Color(0xFF12122A),
            heroBorder = Color(0xFF1E1E4A),
            heroTitle = Color(0xFFE8E8FF),
            heroSubtitle = Color(0xFFA0A0C0),
            heroOverline = Color(0xFF7878A0),
            surface = Color(0xFF181838),
            surfaceBorder = Color(0xFF282858),
            textPrimary = Color(0xFFE8E8FF),
            textSecondary = Color(0xFFA0A0C0),
            accent = Color(0xFF00B0FF),
            accentStrong = Color(0xFF0088CC),
            navContainer = Color(0xFF151538),
            navIndicator = Color(0xFF202050),
            navUnselected = Color(0xFF606080),
            chatBgTop = Color(0xFF050510),
            chatBgMid = Color(0xFF0A0A1A),
            chatBgBottom = Color(0xFF101025),
            chatHeaderStart = Color(0xFF151538),
            chatHeaderMid = Color(0xFF0A0A1A),
            chatHeaderEnd = Color(0xFF050510),
            chatComposerBg = Color(0xFF151538),
            chatComposerFieldBg = Color(0xFF1C1C44),
            chatNeutralControlBg = Color(0xFF202050),
            chatUserBubbleStart = Color(0xFF00B0FF),
            chatUserBubbleEnd = Color(0xFF0066CC),
            chatAssistantSurface = Color(0xFF1C1C44),
            chatUserAvatarBg = Color(0xFF282858)
        )
        AppDesignPreset.NEON_NOIR -> AppDesignPalette(
            screenBgTop = Color(0xFF0A0A0A),
            screenBgMid = Color(0xFF151515),
            screenBgBottom = Color(0xFF1A1A1A),
            heroBg = Color(0xFF1E1E2E),
            heroBorder = Color(0xFF3A2A4A),
            heroTitle = Color(0xFFFFF0F0),
            heroSubtitle = Color(0xFFCCB0B0),
            heroOverline = Color(0xFFAA8888),
            surface = Color(0xFF222230),
            surfaceBorder = Color(0xFF3A3040),
            textPrimary = Color(0xFFFFF0F0),
            textSecondary = Color(0xFFCCB0B0),
            accent = Color(0xFFFF4081),
            accentStrong = Color(0xFFD4005A),
            navContainer = Color(0xFF1A1A28),
            navIndicator = Color(0xFF2A2038),
            navUnselected = Color(0xFF707080),
            chatBgTop = Color(0xFF0A0A0A),
            chatBgMid = Color(0xFF151515),
            chatBgBottom = Color(0xFF1A1A1A),
            chatHeaderStart = Color(0xFF1A1A28),
            chatHeaderMid = Color(0xFF151515),
            chatHeaderEnd = Color(0xFF0A0A0A),
            chatComposerBg = Color(0xFF1A1A28),
            chatComposerFieldBg = Color(0xFF222238),
            chatNeutralControlBg = Color(0xFF2A2038),
            chatUserBubbleStart = Color(0xFFFF4081),
            chatUserBubbleEnd = Color(0xFFD4005A),
            chatAssistantSurface = Color(0xFF222238),
            chatUserAvatarBg = Color(0xFF3A3040)
        )
    }
}
