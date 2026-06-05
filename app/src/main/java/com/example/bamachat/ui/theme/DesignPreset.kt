package com.example.bamachat.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppDesignPreset(val label: String) {
    PROFESSIONAL("Professional"),
    BOLD("Bold"),
    MINIMAL("Minimal"),
    NOIR("Noir"),
    SOLAR("Solar");

    companion object {
        val labels: List<String> = entries.map { it.label }

        fun fromStored(raw: String?): AppDesignPreset {
            val value = raw.orEmpty().trim().lowercase()
            return when (value) {
                "professional", "aktuell", "neo dashboard", "dashboard" -> PROFESSIONAL
                "bold", "glassmorphism pro", "glass" -> BOLD
                "minimal", "editorial bold", "editorial" -> MINIMAL
                "noir", "midnight noir" -> NOIR
                "solar", "sunset solar" -> SOLAR
                else -> PROFESSIONAL
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
        AppDesignPreset.PROFESSIONAL -> AppDesignPalette(
            screenBgTop = Color(0xFF7A2F20),
            screenBgMid = Color(0xFF57273F),
            screenBgBottom = Color(0xFF171E31),
            heroBg = Color(0xFF102038),
            heroBorder = Color(0xFF385775),
            heroTitle = Color(0xFFF4F7FF),
            heroSubtitle = Color(0xFFC5D4EB),
            heroOverline = Color(0xFF9BB2D5),
            surface = Color(0xFF1D2A41),
            surfaceBorder = Color(0xFF2F4563),
            textPrimary = Color(0xFFF0F4FD),
            textSecondary = Color(0xFFBDC9DE),
            accent = Color(0xFF8FAEFA),
            accentStrong = Color(0xFF5E89F1),
            navContainer = Color(0xFFEAF0FC),
            navIndicator = Color(0xFFBCD0F3),
            navUnselected = Color(0xFF5F6D85),
            chatBgTop = Color(0xFF311926),
            chatBgMid = Color(0xFF3C2641),
            chatBgBottom = Color(0xFF1A243A),
            chatHeaderStart = Color(0xFF7A2F20),
            chatHeaderMid = Color(0xFF5B273F),
            chatHeaderEnd = Color(0xFF294268),
            chatComposerBg = Color(0xFF151E31),
            chatComposerFieldBg = Color(0xFF1D2A42),
            chatNeutralControlBg = Color(0xFF243754),
            chatUserBubbleStart = Color(0xFF5E89F1),
            chatUserBubbleEnd = Color(0xFF7A9BEB),
            chatAssistantSurface = Color(0xFF273A58),
            chatUserAvatarBg = Color(0xFF2D4464)
        )
        AppDesignPreset.BOLD -> AppDesignPalette(
            screenBgTop = Color(0xFF8A3322),
            screenBgMid = Color(0xFF622744),
            screenBgBottom = Color(0xFF181F37),
            heroBg = Color(0xFF0E1D35),
            heroBorder = Color(0xFF3A5A83),
            heroTitle = Color(0xFFF5F8FF),
            heroSubtitle = Color(0xFFC6D8F0),
            heroOverline = Color(0xFFA0BAE1),
            surface = Color(0xFF1A2C48),
            surfaceBorder = Color(0xFF355278),
            textPrimary = Color(0xFFEEF4FF),
            textSecondary = Color(0xFFB8CAE6),
            accent = Color(0xFFA6C2FF),
            accentStrong = Color(0xFF7AA4FF),
            navContainer = Color(0xFFE9F0FF),
            navIndicator = Color(0xFFBDD4FF),
            navUnselected = Color(0xFF60708E),
            chatBgTop = Color(0xFF361A2A),
            chatBgMid = Color(0xFF42264A),
            chatBgBottom = Color(0xFF1A2540),
            chatHeaderStart = Color(0xFF8A3322),
            chatHeaderMid = Color(0xFF622744),
            chatHeaderEnd = Color(0xFF2B4975),
            chatComposerBg = Color(0xFF14223A),
            chatComposerFieldBg = Color(0xFF1B3151),
            chatNeutralControlBg = Color(0xFF244066),
            chatUserBubbleStart = Color(0xFF7BA7FF),
            chatUserBubbleEnd = Color(0xFFA5C0FF),
            chatAssistantSurface = Color(0xFF274160),
            chatUserAvatarBg = Color(0xFF2E4D72)
        )
        AppDesignPreset.MINIMAL -> AppDesignPalette(
            screenBgTop = Color(0xFF6C2D24),
            screenBgMid = Color(0xFF4B273F),
            screenBgBottom = Color(0xFF182030),
            heroBg = Color(0xFF152339),
            heroBorder = Color(0xFF334E71),
            heroTitle = Color(0xFFF3F7FD),
            heroSubtitle = Color(0xFFC3D0E2),
            heroOverline = Color(0xFF9CB1CC),
            surface = Color(0xFF202C41),
            surfaceBorder = Color(0xFF31455F),
            textPrimary = Color(0xFFEFF3FA),
            textSecondary = Color(0xFFBBC7D7),
            accent = Color(0xFF8EA6DB),
            accentStrong = Color(0xFF6D8DCC),
            navContainer = Color(0xFFEAF0FA),
            navIndicator = Color(0xFFC5D4EC),
            navUnselected = Color(0xFF5E6C82),
            chatBgTop = Color(0xFF2C1826),
            chatBgMid = Color(0xFF392542),
            chatBgBottom = Color(0xFF1A2536),
            chatHeaderStart = Color(0xFF6C2D24),
            chatHeaderMid = Color(0xFF4B273F),
            chatHeaderEnd = Color(0xFF27415F),
            chatComposerBg = Color(0xFF172334),
            chatComposerFieldBg = Color(0xFF213047),
            chatNeutralControlBg = Color(0xFF2A3D58),
            chatUserBubbleStart = Color(0xFF6D8DCC),
            chatUserBubbleEnd = Color(0xFF8EA6DB),
            chatAssistantSurface = Color(0xFF2E425E),
            chatUserAvatarBg = Color(0xFF324863)
        )
        AppDesignPreset.NOIR -> AppDesignPalette(
            screenBgTop = Color(0xFF1A111D),
            screenBgMid = Color(0xFF141B2A),
            screenBgBottom = Color(0xFF0C111E),
            heroBg = Color(0xFF121B2A),
            heroBorder = Color(0xFF2A3C55),
            heroTitle = Color(0xFFF2F5FB),
            heroSubtitle = Color(0xFFB8C6DC),
            heroOverline = Color(0xFF90A5C4),
            surface = Color(0xFF161F2F),
            surfaceBorder = Color(0xFF2A3A53),
            textPrimary = Color(0xFFEAF0FA),
            textSecondary = Color(0xFFAEBED7),
            accent = Color(0xFF7BA3FF),
            accentStrong = Color(0xFF4E7DE8),
            navContainer = Color(0xFFEAF0FC),
            navIndicator = Color(0xFFC4D4F7),
            navUnselected = Color(0xFF5A687D),
            chatBgTop = Color(0xFF181322),
            chatBgMid = Color(0xFF172235),
            chatBgBottom = Color(0xFF101A2A),
            chatHeaderStart = Color(0xFF2A1631),
            chatHeaderMid = Color(0xFF1B2740),
            chatHeaderEnd = Color(0xFF16345A),
            chatComposerBg = Color(0xFF101827),
            chatComposerFieldBg = Color(0xFF18263E),
            chatNeutralControlBg = Color(0xFF1E3554),
            chatUserBubbleStart = Color(0xFF4E7DE8),
            chatUserBubbleEnd = Color(0xFF7BA3FF),
            chatAssistantSurface = Color(0xFF1C2E49),
            chatUserAvatarBg = Color(0xFF223A5A)
        )
        AppDesignPreset.SOLAR -> AppDesignPalette(
            screenBgTop = Color(0xFF8B3A1E),
            screenBgMid = Color(0xFF69313F),
            screenBgBottom = Color(0xFF1E2D46),
            heroBg = Color(0xFF1B2A40),
            heroBorder = Color(0xFF3F5C82),
            heroTitle = Color(0xFFF8F5EF),
            heroSubtitle = Color(0xFFDCCCB9),
            heroOverline = Color(0xFFC1B59D),
            surface = Color(0xFF22324A),
            surfaceBorder = Color(0xFF456286),
            textPrimary = Color(0xFFF7F3EA),
            textSecondary = Color(0xFFD3C6B4),
            accent = Color(0xFFFFB66A),
            accentStrong = Color(0xFFF08A3D),
            navContainer = Color(0xFFFFF2E0),
            navIndicator = Color(0xFFFFD9B0),
            navUnselected = Color(0xFF77624A),
            chatBgTop = Color(0xFF40211E),
            chatBgMid = Color(0xFF4A2C3F),
            chatBgBottom = Color(0xFF213754),
            chatHeaderStart = Color(0xFF8B3A1E),
            chatHeaderMid = Color(0xFF69313F),
            chatHeaderEnd = Color(0xFF2D5480),
            chatComposerBg = Color(0xFF1E2E44),
            chatComposerFieldBg = Color(0xFF2A3E5E),
            chatNeutralControlBg = Color(0xFF355174),
            chatUserBubbleStart = Color(0xFFF08A3D),
            chatUserBubbleEnd = Color(0xFFFFB66A),
            chatAssistantSurface = Color(0xFF324B6D),
            chatUserAvatarBg = Color(0xFF3A5981)
        )
    }
}
