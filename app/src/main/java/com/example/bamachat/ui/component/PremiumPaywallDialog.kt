package com.example.bamachat.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bamachat.ui.viewmodel.SettingsViewModel
import com.example.bamachat.util.PlayBillingManager

@Composable
fun PremiumPaywallDialog(
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val billingReady by settingsViewModel.billingReady.collectAsStateWithLifecycle()
    val purchaseInProgress by settingsViewModel.purchaseInProgress.collectAsStateWithLifecycle()
    val isPremiumActive by settingsViewModel.isPremiumActive.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BamaChat Premium", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isPremiumActive) "Premium ist bereits aktiv. Du hast unbegrenzte Nutzung."
                    else "Free-Plan-Limit erreicht. Mit Premium werden Tageslimits entfernt und neue Profi-Funktionen freigeschaltet.",
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            val activity = context as? android.app.Activity ?: return@AssistChip
                            settingsViewModel.startSubscriptionCheckout(activity, PlayBillingManager.PLAN_PRO)
                        },
                        label = { Text("Basic") },
                        enabled = !isPremiumActive && billingReady && !purchaseInProgress
                    )
                    AssistChip(
                        onClick = {
                            val activity = context as? android.app.Activity ?: return@AssistChip
                            settingsViewModel.startSubscriptionCheckout(activity, PlayBillingManager.PLAN_PRO)
                        },
                        label = { Text("Pro") },
                        enabled = !isPremiumActive && billingReady && !purchaseInProgress
                    )
                    AssistChip(
                        onClick = {
                            val activity = context as? android.app.Activity ?: return@AssistChip
                            settingsViewModel.startSubscriptionCheckout(activity, PlayBillingManager.PLAN_EXPERT)
                        },
                        label = { Text("Expert") },
                        enabled = !isPremiumActive && billingReady && !purchaseInProgress
                    )
                }
                if (!billingReady) {
                    Text(
                        "Play Billing ist aktuell nicht bereit. Prüfe Play Store/Tester-Konto oder nutze vorübergehend den lokalen Premium-Test in Einstellungen.",
                        fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settingsViewModel.refreshBillingState()
                onDismiss()
            }) { Text("Schließen") }
        }
    )
}
