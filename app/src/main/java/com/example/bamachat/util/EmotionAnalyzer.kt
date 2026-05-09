package com.example.bamachat.util

data class EmotionSignal(
    val label: String,
    val sentiment: String,
    val empathyHint: String
)

object EmotionAnalyzer {
    private val positiveWords = setOf(
        "super", "danke", "glücklich", "freue", "top", "mega", "cool", "nice", "gelungen"
    )
    private val negativeWords = setOf(
        "traurig", "wütend", "angst", "stress", "kaputt", "schlecht", "überfordert", "genervt", "hilfe"
    )
    private val urgencyWords = setOf(
        "sofort", "dringend", "jetzt", "panic", "notfall", "kritisch"
    )

    fun analyze(text: String): EmotionSignal {
        val lower = text.lowercase()
        val positiveScore = positiveWords.count { lower.contains(it) }
        val negativeScore = negativeWords.count { lower.contains(it) }
        val urgencyScore = urgencyWords.count { lower.contains(it) }

        return when {
            urgencyScore > 0 -> EmotionSignal(
                label = "dringend",
                sentiment = "negative",
                empathyHint = "Der Nutzer wirkt unter Druck. Antworte ruhig, strukturiert und mit klaren Sofort-Schritten."
            )
            negativeScore > positiveScore -> EmotionSignal(
                label = "belastet",
                sentiment = "negative",
                empathyHint = "Der Nutzer wirkt emotional belastet. Antworte empathisch und validiere kurz seine Situation."
            )
            positiveScore > 0 -> EmotionSignal(
                label = "positiv",
                sentiment = "positive",
                empathyHint = "Der Nutzer wirkt positiv. Halte den Ton motivierend und lösungsorientiert."
            )
            else -> EmotionSignal(
                label = "neutral",
                sentiment = "neutral",
                empathyHint = "Neutrale Stimmung. Antworte klar, präzise und hilfreich."
            )
        }
    }
}
