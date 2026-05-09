package com.example.bamachat.util

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

class PlayBillingManager(
    context: Context,
    private val onSubscriptionTierChanged: (MonetizationConfig.PlanTier) -> Unit,
    private val onCreditsGranted: (Int) -> Unit,
    private val onBillingReadyChanged: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        const val PLAN_PRO = MonetizationConfig.Subscriptions.PRO_MONTHLY
        const val PLAN_EXPERT = MonetizationConfig.Subscriptions.EXPERT_MONTHLY
        const val CREDIT_100 = MonetizationConfig.Credits.PACK_100
        const val CREDIT_300 = MonetizationConfig.Credits.PACK_300
        const val CREDIT_1000 = MonetizationConfig.Credits.PACK_1000
        val SUBSCRIPTION_IDS = MonetizationConfig.Subscriptions.ids
        val CREDIT_PRODUCT_IDS = MonetizationConfig.Credits.ids
    }

    private val appContext = context.applicationContext
    private var billingClient: BillingClient? = null
    private var productDetailsCache: List<ProductDetails> = emptyList()

    fun connect() {
        if (billingClient?.isReady == true) {
            onBillingReadyChanged(true)
            queryActivePremium()
            return
        }

        billingClient = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enableAutoServiceReconnection()
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val ready = result.responseCode == BillingClient.BillingResponseCode.OK
                onBillingReadyChanged(ready)
                if (!ready) return
                queryProductDetails()
                queryActivePremium()
                queryPendingCreditPurchases()
            }

            override fun onBillingServiceDisconnected() {
                onBillingReadyChanged(false)
            }
        })
    }

    fun queryProductDetails() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val subscriptionProducts = SUBSCRIPTION_IDS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val creditProducts = CREDIT_PRODUCT_IDS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(subscriptionProducts + creditProducts)
            .build()

        client.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsCache = detailsResult.productDetailsList ?: emptyList()
            }
        }
    }

    fun launchSubscriptionPurchase(activity: Activity, planId: String): Boolean {
        val client = billingClient ?: return false
        if (!client.isReady) return false

        val details = productDetailsCache.firstOrNull { it.productId == planId } ?: return false
        val offerToken = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken
            ?: return false

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = client.launchBillingFlow(activity, params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun launchCreditPurchase(activity: Activity, productId: String): Boolean {
        val client = billingClient ?: return false
        if (!client.isReady) return false

        val details = productDetailsCache.firstOrNull {
            it.productId == productId && it.productType == BillingClient.ProductType.INAPP
        } ?: return false

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = client.launchBillingFlow(activity, params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun queryActivePremium() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            onSubscriptionTierChanged(resolveTier(purchases))
            purchases.forEach { acknowledgeIfNeeded(it) }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases.isNullOrEmpty()) return

        purchases.forEach { purchase ->
            if (purchase.products.any { it in SUBSCRIPTION_IDS }) {
                acknowledgeIfNeeded(purchase)
            }
            if (purchase.products.any { it in CREDIT_PRODUCT_IDS }) {
                consumeCreditPurchase(purchase)
            }
        }
        queryActivePremium()
    }

    private fun queryPendingCreditPurchases() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            purchases.forEach { purchase ->
                if (purchase.products.any { it in CREDIT_PRODUCT_IDS }) {
                    consumeCreditPurchase(purchase)
                }
            }
        }
    }

    private fun consumeCreditPurchase(purchase: Purchase) {
        val client = billingClient ?: return
        if (!client.isReady) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val creditProducts = purchase.products.filter { it in CREDIT_PRODUCT_IDS }
        if (creditProducts.isEmpty()) return
        val creditsToGrant = creditProducts.sumOf { MonetizationConfig.creditsForProduct(it) }
        if (creditsToGrant <= 0) return

        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        client.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                onCreditsGranted(creditsToGrant)
            }
        }
    }

    private fun resolveTier(purchases: List<Purchase>): MonetizationConfig.PlanTier {
        val purchasedProducts = purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .flatMap { it.products }
            .toSet()

        return when {
            MonetizationConfig.Subscriptions.EXPERT_MONTHLY in purchasedProducts ->
                MonetizationConfig.PlanTier.EXPERT
            MonetizationConfig.Subscriptions.PRO_MONTHLY in purchasedProducts ->
                MonetizationConfig.PlanTier.PRO
            else -> MonetizationConfig.PlanTier.FREE
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        val client = billingClient ?: return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (purchase.isAcknowledged) return

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        client.acknowledgePurchase(params) { _ -> }
    }

    fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
        onBillingReadyChanged(false)
    }
}
