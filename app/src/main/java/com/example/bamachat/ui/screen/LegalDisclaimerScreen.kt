package com.example.bamachat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bamachat.util.LegalPolicy

import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDisclaimerScreen(
    onAccept: () -> Unit,
    onBack: () -> Unit
) {
    var agreedToPrivacy by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Recht & Datenschutz", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2D),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF161B26))
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Bevor du BamaFlow nutzt, bestätigst du unsere Datenschutz- und Nutzungsbedingungen.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 18.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { uriHandler.openUri(LegalPolicy.PRIVACY_POLICY_URL) }
                    ) {
                        Text("Datenschutz", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { uriHandler.openUri(LegalPolicy.TERMS_URL) }
                    ) {
                        Text("AGB", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { uriHandler.openUri(LegalPolicy.ACCOUNT_DELETION_URL) }
                    ) {
                        Text("Löschung", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { uriHandler.openUri(LegalPolicy.SUPPORT_URL) }
                    ) {
                        Text("Support", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(
                    "Die ausführlichen Texte öffnest du online. Telemetrie wird erst nach deiner Freigabe aktiviert.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 17.sp
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "Datenschutzerklärung",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        privacyPolicyText,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 18.sp
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Nutzungsbedingungen",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        termsOfServiceText,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 18.sp
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = agreedToPrivacy,
                        onCheckedChange = { agreedToPrivacy = it }
                    )
                    Text(
                        "Ich akzeptiere die Datenschutzerklärung",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = agreedToTerms,
                        onCheckedChange = { agreedToTerms = it }
                    )
                    Text(
                        "Ich akzeptiere die Nutzungsbedingungen",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = agreedToPrivacy && agreedToTerms
                ) {
                    Text("Akzeptieren & Fortfahren")
                }

                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Ablehnen")
                }
            }
        }
    }
}

private const val privacyPolicyText = """
Datenschutzerklärung für BamaFlow

1. Datenerfassung
   - BamaFlow speichert deine Chat-Nachrichten lokal auf deinem Gerät
   - API-Keys werden verschlüsselt in SharedPreferences gespeichert
   - Firebase wird für Crashlytics und Analytics verwendet, aber erst nach Zustimmung
   - Wir sammeln KEINE persönlichen Informationen ohne deine Freigabe

2. API-Integration
   - Du verknüpfst deine eigenen API-Keys von OpenRouter, Groq, etc.
   - Deine API-Keys sind deine Verantwortung
   - Nachrichten werden an externe KI-Provider gesendet
   - Lies die Datenschutzerklärung deines KI-Providers

3. Cloud Storage
   - Optional: Backups können in Firebase Cloud Storage gespeichert werden
   - Dies ist opt-in und benötigt Google-Account

4. Benutzerrechte
   - Du kannst deine Daten jederzeit exportieren
   - Du kannst alle lokalen Daten löschen
   - Du kannst die App jederzeit deinstallieren

5. Kontakt
   - Fragen zum Datenschutz: ${LegalPolicy.SUPPORT_EMAIL}
   - Wir antworten innerhalb von 7 Tagen
"""

private const val termsOfServiceText = """
Nutzungsbedingungen für BamaFlow

1. Lizenzgewährung
   - BamaFlow ist für persönliche, nicht-kommerzielle Nutzung gedacht
   - Du darfst BamaFlow nicht reverse-engineern oder modifizieren
   - Du darfst die App nicht weiterverkaufen oder vermieten

2. API-Nutzung
   - Du bist verantwortlich für deine API-Key-Verwaltung
   - Alle API-Kosten gehen zu Lasten deines Accounts
   - BamaFlow ist nicht verantwortlich für API-Fehler oder -Ausfälle

3. Inhalte
   - Du bist verantwortlich für alle Inhalte, die du erstellst
   - Nutze BamaFlow NICHT für illegale oder schädliche Inhalte
   - Missbrauch führt zur Account-Sperrung

4. Haftungsausschluss
   - BamaFlow wird "wie besehen" angeboten
   - Keine Garantien für Verfügbarkeit oder Genauigkeit
   - Wir haften nicht für Datenverluste

5. Änderungen
   - Wir können diese Bedingungen jederzeit ändern
   - Änderungen werden 7 Tage vor Inkrafttreten angekündigt

6. Geltungsbereich
   - Diese Bedingungen unterliegen deutschem Recht
   - Gerichtsstand: Berlin, Deutschland
"""
