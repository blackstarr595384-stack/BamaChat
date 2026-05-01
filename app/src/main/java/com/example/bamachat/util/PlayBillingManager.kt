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
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

class PlayBillingManager(
    context: Context,
    private val onPremiumChanged: (Boolean) -> Unit,
    private val onBillingReadyChanged: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        const val PLAN_BASIC = "bamachat_basic_monthly"
        const val PLAN_PRO = "bamachat_pro_monthly"
        const val PLAN_EXPERT = "bamachat_expert_monthly"
        val SUBSCRIPTION_IDS = listOf(PLAN_BASIC, PLAN_PRO, PLAN_EXPERT)
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
            }

            override fun onBillingServiceDisconnected() {
                onBillingReadyChanged(false)
            }
        })
    }

    fun queryProductDetails() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val products = SUBSCRIPTION_IDS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
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

    fun queryActivePremium() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val hasPremium = purchases.any { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.products.any { it in SUBSCRIPTION_IDS }
            }
            onPremiumChanged(hasPremium)
            purchases.forEach { acknowledgeIfNeeded(it) }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases.isNullOrEmpty()) return

        val hasPremium = purchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.any { it in SUBSCRIPTION_IDS }
        }
        if (hasPremium) onPremiumChanged(true)
        purchases.forEach { acknowledgeIfNeeded(it) }
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
