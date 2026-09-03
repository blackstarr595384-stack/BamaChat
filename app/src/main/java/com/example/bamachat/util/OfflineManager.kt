package com.example.bamachat.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class OfflineManager(private val context: Context) {

    data class PendingOperation(
        val id: String = UUID.randomUUID().toString(),
        val type: OperationType,
        val payload: String,
        val timestamp: Long = System.currentTimeMillis(),
        val retryCount: Int = 0
    )

    enum class OperationType { SEND_MESSAGE, SYNC_PERSONA, UPLOAD_FILE, WEB_SEARCH }

    enum class NetworkQuality { UNKNOWN, POOR, MODERATE, GOOD, EXCELLENT }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _pendingOperations = MutableStateFlow<List<PendingOperation>>(emptyList())
    val pendingOperations: StateFlow<List<PendingOperation>> = _pendingOperations.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            _isOnline.value = false
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            _isOnline.value = capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        _isOnline.value = connectivityManager.activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
        } ?: false
    }

    fun enqueueOperation(type: OperationType, payload: String) {
        _pendingOperations.update { old -> old + PendingOperation(
            type = type,
            payload = payload
        ) }
    }

    suspend fun processQueue(maxRetries: Int = 3): Int {
        val current = _pendingOperations.value.toList()
        if (current.isEmpty()) return 0

        var processed = 0
        val remaining = mutableListOf<PendingOperation>()

        for (op in current) {
            if (op.retryCount >= maxRetries) {
                remaining.add(op)
                continue
            }
            if (isOnline.value) {
                processed++
            } else {
                remaining.add(op.copy(retryCount = op.retryCount + 1))
            }
        }

        _pendingOperations.value = remaining
        return processed
    }

    fun clearQueue() {
        _pendingOperations.value = emptyList()
    }

    fun getNetworkQuality(): NetworkQuality {
        val network = connectivityManager.activeNetwork ?: return NetworkQuality.UNKNOWN
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkQuality.UNKNOWN

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val speed = capabilities.linkDownstreamBandwidthKbps ?: 0
                when {
                    speed >= 50_000 -> NetworkQuality.EXCELLENT
                    speed >= 20_000 -> NetworkQuality.GOOD
                    speed >= 5_000 -> NetworkQuality.MODERATE
                    else -> NetworkQuality.POOR
                }
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // dataNetworkType benoetigt READ_PHONE_STATE (sensible Berechtigung).
                // Stattdessen: Bandbreite aus NetworkCapabilities lesen (keine extra Permission noetig).
                val downKbps = capabilities.linkDownstreamBandwidthKbps
                when {
                    downKbps >= 20_000 -> NetworkQuality.GOOD
                    downKbps >= 5_000 -> NetworkQuality.MODERATE
                    downKbps > 0 -> NetworkQuality.POOR
                    else -> NetworkQuality.UNKNOWN
                }
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkQuality.EXCELLENT
            else -> NetworkQuality.UNKNOWN
        }
    }
}
