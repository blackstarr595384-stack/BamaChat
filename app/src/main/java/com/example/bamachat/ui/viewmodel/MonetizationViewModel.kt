package com.example.bamachat.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.example.bamachat.util.MonetizationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * MonetizationViewModel: Verwaltet Monetisierung
 * - Plan-Tier (Free, Pro, Expert)
 * - Tägliche Quotas für Text/Bilder/Dokumente
 * - Credit-System
 * - Paywall-State
 */
class MonetizationViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ===== State =====
    private val _usageStatus = MutableStateFlow(UsageStatus())
    val usageStatus: StateFlow<UsageStatus> = _usageStatus

    private val _showPaywall = MutableStateFlow(false)
    val showPaywall: StateFlow<Boolean> = _showPaywall

    // ===== Public API =====

    fun refreshMonetizationState() {
        ensureUsageDayIsCurrent()
        val tier = currentPlanTier()
        val quota = MonetizationConfig.quotaForTier(tier)
        val isPremium = tier != MonetizationConfig.PlanTier.FREE

        _usageStatus.value = UsageStatus(
            isPremium = isPremium,
            tierLabel = tier.label,
            creditsBalance = prefs.getInt(KEY_CREDITS_BALANCE, 0),
            textUsed = prefs.getInt(KEY_USAGE_TEXT, 0),
            textLimit = quota.textMessages,
            webSearchUsed = prefs.getInt(KEY_USAGE_WEB_SEARCH, 0),
            webSearchLimit = quota.webSearchRequests,
            imageAnalysisUsed = prefs.getInt(KEY_USAGE_IMAGE_ANALYSIS, 0),
            imageAnalysisLimit = quota.imageAnalysis,
            imageGenerationUsed = prefs.getInt(KEY_USAGE_IMAGE_GENERATION, 0),
            imageGenerationLimit = quota.imageGeneration
        )
    }

    fun dismissPaywall() {
        _showPaywall.value = false
    }

    fun openPaywall() {
        _showPaywall.value = true
    }

    fun consumeQuota(type: QuotaType): Boolean {
        val tier = currentPlanTier()
        if (tier != MonetizationConfig.PlanTier.FREE) return true

        val quota = MonetizationConfig.quotaForTier(tier)
        ensureUsageDayIsCurrent()

        val (prefKey, limit) = when (type) {
            QuotaType.TEXT_MESSAGE -> KEY_USAGE_TEXT to quota.textMessages
            QuotaType.WEB_RESEARCH -> KEY_USAGE_WEB_SEARCH to quota.webSearchRequests
            QuotaType.IMAGE_ANALYSIS -> KEY_USAGE_IMAGE_ANALYSIS to quota.imageAnalysis
            QuotaType.IMAGE_GENERATION -> KEY_USAGE_IMAGE_GENERATION to quota.imageGeneration
        }

        val current = prefs.getInt(prefKey, 0)
        if (current >= limit) {
            val actionKey = when (type) {
                QuotaType.TEXT_MESSAGE -> "text_response"
                QuotaType.WEB_RESEARCH -> "web_research"
                QuotaType.IMAGE_ANALYSIS -> "image_analysis"
                QuotaType.IMAGE_GENERATION -> "image_generation"
            }
            if (consumeCreditsForAction(actionKey)) {
                refreshMonetizationState()
                return true
            }
            _showPaywall.value = true
            refreshMonetizationState()
            return false
        }

        prefs.edit().putInt(prefKey, current + 1).apply()
        refreshMonetizationState()
        return true
    }

    // ===== Privat =====

    private fun ensureUsageDayIsCurrent() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val storedDay = prefs.getString(KEY_USAGE_DAY, null)
        if (storedDay == today) return

        prefs.edit()
            .putString(KEY_USAGE_DAY, today)
            .putInt(KEY_USAGE_TEXT, 0)
            .putInt(KEY_USAGE_WEB_SEARCH, 0)
            .putInt(KEY_USAGE_IMAGE_ANALYSIS, 0)
            .putInt(KEY_USAGE_IMAGE_GENERATION, 0)
            .apply()
    }

    private fun consumeCreditsForAction(actionKey: String): Boolean {
        val cost = MonetizationConfig.creditsForAction(actionKey)
        if (cost <= 0) return false

        val current = prefs.getInt(KEY_CREDITS_BALANCE, 0)
        if (current < cost) return false

        prefs.edit().putInt(KEY_CREDITS_BALANCE, current - cost).apply()
        return true
    }

    private fun currentPlanTier(): MonetizationConfig.PlanTier {
        val storedTier = prefs.getString(KEY_SUBSCRIPTION_TIER, null)
        val normalized = MonetizationConfig.PlanTier.fromKey(storedTier)
        if (normalized != MonetizationConfig.PlanTier.FREE) return normalized

        return if (prefs.getBoolean(KEY_PREMIUM_ACTIVE, false)) {
            MonetizationConfig.PlanTier.PRO
        } else {
            MonetizationConfig.PlanTier.FREE
        }
    }

    data class UsageStatus(
        val isPremium: Boolean = false,
        val tierLabel: String = MonetizationConfig.PlanTier.FREE.label,
        val creditsBalance: Int = 0,
        val textUsed: Int = 0,
        val textLimit: Int = 0,
        val webSearchUsed: Int = 0,
        val webSearchLimit: Int = 0,
        val imageAnalysisUsed: Int = 0,
        val imageAnalysisLimit: Int = 0,
        val imageGenerationUsed: Int = 0,
        val imageGenerationLimit: Int = 0
    ) {
        val textRemaining: Int get() = (textLimit - textUsed).coerceAtLeast(0)
        val webSearchRemaining: Int get() = (webSearchLimit - webSearchUsed).coerceAtLeast(0)
        val imageAnalysisRemaining: Int get() = (imageAnalysisLimit - imageAnalysisUsed).coerceAtLeast(0)
        val imageGenerationRemaining: Int get() = (imageGenerationLimit - imageGenerationUsed).coerceAtLeast(0)
    }

    enum class QuotaType {
        TEXT_MESSAGE,
        WEB_RESEARCH,
        IMAGE_ANALYSIS,
        IMAGE_GENERATION
    }

    companion object {
        private const val KEY_PREMIUM_ACTIVE = "premium_active"
        private const val KEY_SUBSCRIPTION_TIER = "subscription_tier"
        private const val KEY_CREDITS_BALANCE = "credits_balance"
        private const val KEY_USAGE_DAY = "usage_day"
        private const val KEY_USAGE_TEXT = "usage_text_count"
        private const val KEY_USAGE_WEB_SEARCH = "usage_web_search_count"
        private const val KEY_USAGE_IMAGE_ANALYSIS = "usage_image_analysis_count"
        private const val KEY_USAGE_IMAGE_GENERATION = "usage_image_generation_count"
    }
}
