package com.example.bamachat.ui.screen

import androidx.compose.ui.graphics.Color
import com.example.bamachat.ui.viewmodel.ChatViewModel

data class PersonaMood(
    val gradientTop: Color,
    val gradientBottom: Color,
    val cardSurface: Color,
    val userBubbleStart: Color,
    val userBubbleEnd: Color,
    val accent: Color
)

fun moodForPersona(
    persona: ChatViewModel.Persona,
    baseAccent: Color,
    sentiment: String
): PersonaMood {
    val accent = when (sentiment) {
        "positive" -> Color(0xFF0FB57A)
        "negative" -> Color(0xFFE8505B)
        else -> baseAccent
    }
    return when (persona) {
        ChatViewModel.Persona.DEVELOPER -> PersonaMood(
            gradientTop = Color(0xFF0F1424),
            gradientBottom = Color(0xFF131A2B),
            cardSurface = Color(0xFF1A2236),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF3D7DFF),
            accent = Color(0xFF4CC9FF)
        )
        ChatViewModel.Persona.TEACHER -> PersonaMood(
            gradientTop = Color(0xFF1C1522),
            gradientBottom = Color(0xFF221A2B),
            cardSurface = Color(0xFF2C2038),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF9F6DFF),
            accent = Color(0xFFE6C15A)
        )
        ChatViewModel.Persona.CHEF -> PersonaMood(
            gradientTop = Color(0xFF2A1612),
            gradientBottom = Color(0xFF2F1D15),
            cardSurface = Color(0xFF3A261C),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFFE27A3D),
            accent = Color(0xFFFFB157)
        )
        ChatViewModel.Persona.FITNESS -> PersonaMood(
            gradientTop = Color(0xFF101B16),
            gradientBottom = Color(0xFF13231B),
            cardSurface = Color(0xFF1D3127),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF37C887),
            accent = Color(0xFF6FE5B1)
        )
        ChatViewModel.Persona.TRANSLATOR -> PersonaMood(
            gradientTop = Color(0xFF101D26),
            gradientBottom = Color(0xFF142530),
            cardSurface = Color(0xFF1E3442),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF41A1FF),
            accent = Color(0xFF79C8FF)
        )
        ChatViewModel.Persona.THERAPIST -> PersonaMood(
            gradientTop = Color(0xFF121E1B),
            gradientBottom = Color(0xFF152721),
            cardSurface = Color(0xFF22372F),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF57BFA2),
            accent = Color(0xFF9ADCCB)
        )
        ChatViewModel.Persona.CUSTOM -> PersonaMood(
            gradientTop = Color(0xFF1A1623),
            gradientBottom = Color(0xFF1D1B2B),
            cardSurface = Color(0xFF2B2440),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF7E6DFF),
            accent = Color(0xFFBAA8FF)
        )
        else -> PersonaMood(
            gradientTop = Color(0xFF10151F),
            gradientBottom = Color(0xFF161B26),
            cardSurface = Color(0xFF202838),
            userBubbleStart = accent,
            userBubbleEnd = Color(0xFF4E7BFF),
            accent = Color(0xFF82A6FF)
        )
    }
}
