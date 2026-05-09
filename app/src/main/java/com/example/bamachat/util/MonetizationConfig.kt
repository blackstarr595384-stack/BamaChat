package com.example.bamachat.util

object MonetizationConfig {

    enum class PlanTier(val key: String, val label: String) {
        FREE("free", "Free"),
        PRO("pro", "Pro"),
        EXPERT("expert", "Expert");

        companion object {
            fun fromKey(raw: String?): PlanTier = entries.firstOrNull { it.key == raw } ?: FREE
        }
    }

    data class DailyQuota(
        val textMessages: Int,
        val imageAnalysis: Int,
        val imageGeneration: Int,
        val documentImports: Int,
        val realtimeCollabUsers: Int,
        val webSearchRequests: Int
    )

    data class ActionCreditCost(
        val actionKey: String,
        val credits: Int
    )

    object Subscriptions {
        const val PRO_MONTHLY = "pro_monthly_799"
        const val EXPERT_MONTHLY = "expert_monthly_1999"
        val ids = listOf(PRO_MONTHLY, EXPERT_MONTHLY)
    }

    object Credits {
        const val PACK_100 = "credits_100"
        const val PACK_300 = "credits_300"
        const val PACK_1000 = "credits_1000"
        val ids = listOf(PACK_100, PACK_300, PACK_1000)
    }

    val freeQuota = DailyQuota(
        textMessages = 30,
        imageAnalysis = 2,
        imageGeneration = 2,
        documentImports = 1,
        realtimeCollabUsers = 3,
        webSearchRequests = 5
    )

    val proQuota = DailyQuota(
        textMessages = 500,
        imageAnalysis = 40,
        imageGeneration = 40,
        documentImports = 20,
        realtimeCollabUsers = 10,
        webSearchRequests = 80
    )

    val expertQuota = DailyQuota(
        textMessages = 2000,
        imageAnalysis = 150,
        imageGeneration = 150,
        documentImports = 80,
        realtimeCollabUsers = 25,
        webSearchRequests = 300
    )

    val actionCreditCosts = listOf(
        ActionCreditCost(actionKey = "text_response", credits = 1),
        ActionCreditCost(actionKey = "multi_agent_response", credits = 8),
        ActionCreditCost(actionKey = "image_analysis", credits = 6),
        ActionCreditCost(actionKey = "image_generation", credits = 12),
        ActionCreditCost(actionKey = "web_research", credits = 2),
        ActionCreditCost(actionKey = "document_import", credits = 8),
        ActionCreditCost(actionKey = "audio_video_transcription", credits = 15),
        ActionCreditCost(actionKey = "batch_multimodal", credits = 25)
    )

    fun creditsForProduct(productId: String): Int = when (productId) {
        Credits.PACK_100 -> 100
        Credits.PACK_300 -> 300
        Credits.PACK_1000 -> 1000
        else -> 0
    }

    fun creditsForAction(actionKey: String): Int =
        actionCreditCosts.firstOrNull { it.actionKey == actionKey }?.credits ?: 0

    fun quotaForTier(tier: PlanTier): DailyQuota = when (tier) {
        PlanTier.FREE -> freeQuota
        PlanTier.PRO -> proQuota
        PlanTier.EXPERT -> expertQuota
    }
}
