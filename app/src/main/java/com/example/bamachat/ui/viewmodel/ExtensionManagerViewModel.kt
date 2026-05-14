package com.example.bamachat.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.example.bamachat.util.ExtensionCapability
import com.example.bamachat.util.ExtensionCatalog
import com.example.bamachat.util.ExtensionManifest
import com.example.bamachat.util.ExtensionStateStore
import com.example.bamachat.util.InstalledExtensionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ManagedExtension(
    val manifest: ExtensionManifest,
    val state: InstalledExtensionState? = null,
    val missingRequiredCapabilities: Set<ExtensionCapability> = emptySet()
) {
    val isInstalled: Boolean
        get() = state != null

    val isEnabled: Boolean
        get() = state?.enabled == true

    val grantedCapabilities: Set<ExtensionCapability>
        get() = state?.grantedCapabilities.orEmpty()
}

class ExtensionManagerViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val KEY_EXTENSION_STATES_JSON = "workspace_extension_states_json"
    }

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _catalog = MutableStateFlow(ExtensionCatalog.curated)
    val catalog: StateFlow<List<ExtensionManifest>> = _catalog.asStateFlow()

    private val _installedStates = MutableStateFlow(loadInstalledStates())
    val installedStates: StateFlow<List<InstalledExtensionState>> = _installedStates.asStateFlow()

    private val _managedExtensions = MutableStateFlow(emptyList<ManagedExtension>())
    val managedExtensions: StateFlow<List<ManagedExtension>> = _managedExtensions.asStateFlow()

    private val _orphanedInstalledStates = MutableStateFlow(emptyList<InstalledExtensionState>())
    val orphanedInstalledStates: StateFlow<List<InstalledExtensionState>> = _orphanedInstalledStates.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        refreshManagedState()
    }

    fun clearMessages() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    fun installExtension(extensionId: String) {
        clearMessages()
        val manifest = ExtensionCatalog.findById(extensionId)
        if (manifest == null) {
            _errorMessage.value = "Extension nicht gefunden."
            return
        }
        if (_installedStates.value.any { it.extensionId == extensionId }) {
            _statusMessage.value = "${manifest.name} ist bereits installiert."
            return
        }
        val updated = (_installedStates.value + InstalledExtensionState(extensionId = extensionId))
            .sortedByDescending { it.installedAt }
        applyInstalledStates(updated)
        _statusMessage.value = "${manifest.name} installiert."
    }

    fun uninstallExtension(extensionId: String) {
        clearMessages()
        val existing = _installedStates.value.firstOrNull { it.extensionId == extensionId } ?: return
        val updated = _installedStates.value.filterNot { it.extensionId == extensionId }
        applyInstalledStates(updated)
        val manifestName = ExtensionCatalog.findById(existing.extensionId)?.name ?: existing.extensionId
        _statusMessage.value = "$manifestName deinstalliert."
    }

    fun setExtensionEnabled(extensionId: String, enabled: Boolean) {
        clearMessages()
        val manifest = ExtensionCatalog.findById(extensionId)
        if (manifest == null) {
            _errorMessage.value = "Unbekannte Extension kann nicht aktiviert werden."
            return
        }
        val current = _installedStates.value.firstOrNull { it.extensionId == extensionId }
        if (current == null) {
            _errorMessage.value = "${manifest.name} ist nicht installiert."
            return
        }
        if (enabled) {
            val missing = current.missingRequiredCapabilities(manifest)
            if (missing.isNotEmpty()) {
                _errorMessage.value = buildString {
                    append("Aktivierung blockiert. Fehlende Rechte: ")
                    append(missing.joinToString { it.label })
                }
                return
            }
        }
        updateState(extensionId) { state -> state.copy(enabled = enabled) }
        _statusMessage.value = if (enabled) {
            "${manifest.name} aktiviert."
        } else {
            "${manifest.name} deaktiviert."
        }
    }

    fun grantCapability(extensionId: String, capability: ExtensionCapability) {
        clearMessages()
        val manifest = ExtensionCatalog.findById(extensionId)
        if (manifest == null) {
            _errorMessage.value = "Unbekannte Extension."
            return
        }
        if (!manifest.allCapabilities.contains(capability)) {
            _errorMessage.value = "${manifest.name} fordert ${capability.label} nicht an."
            return
        }
        updateState(extensionId) { state ->
            state.copy(grantedCapabilities = state.grantedCapabilities + capability)
        }
        _statusMessage.value = "${capability.label} für ${manifest.name} freigegeben."
    }

    fun revokeCapability(extensionId: String, capability: ExtensionCapability) {
        clearMessages()
        val manifest = ExtensionCatalog.findById(extensionId)
        if (manifest == null) {
            _errorMessage.value = "Unbekannte Extension."
            return
        }
        updateState(extensionId) { state ->
            val removingRequired = capability in manifest.requiredCapabilities
            state.copy(
                enabled = if (removingRequired && state.enabled) false else state.enabled,
                grantedCapabilities = state.grantedCapabilities - capability
            )
        }
        _statusMessage.value = if (capability in manifest.requiredCapabilities) {
            "${capability.label} entfernt. ${manifest.name} wurde vorsorglich deaktiviert."
        } else {
            "${capability.label} für ${manifest.name} entfernt."
        }
    }

    fun grantAllRequiredCapabilities(extensionId: String) {
        clearMessages()
        val manifest = ExtensionCatalog.findById(extensionId)
        if (manifest == null) {
            _errorMessage.value = "Unbekannte Extension."
            return
        }
        updateState(extensionId) { state ->
            state.copy(grantedCapabilities = state.grantedCapabilities + manifest.requiredCapabilities)
        }
        _statusMessage.value = "Pflichtrechte für ${manifest.name} freigegeben."
    }

    private fun updateState(
        extensionId: String,
        transform: (InstalledExtensionState) -> InstalledExtensionState
    ) {
        val current = _installedStates.value
        var found = false
        val updated = current.map { state ->
            if (state.extensionId == extensionId) {
                found = true
                transform(state)
            } else {
                state
            }
        }
        if (!found) {
            _errorMessage.value = "Extension ist nicht installiert."
            return
        }
        applyInstalledStates(updated)
    }

    private fun applyInstalledStates(states: List<InstalledExtensionState>) {
        val normalized = states
            .distinctBy { it.extensionId }
            .sortedByDescending { it.installedAt }
        _installedStates.value = normalized
        prefs.edit()
            .putString(KEY_EXTENSION_STATES_JSON, ExtensionStateStore.encode(normalized))
            .apply()
        refreshManagedState()
    }

    private fun refreshManagedState() {
        val installedMap = _installedStates.value.associateBy { it.extensionId }
        _managedExtensions.value = _catalog.value.map { manifest ->
            val state = installedMap[manifest.id]
            ManagedExtension(
                manifest = manifest,
                state = state,
                missingRequiredCapabilities = state?.missingRequiredCapabilities(manifest).orEmpty()
            )
        }
        val knownIds = _catalog.value.map { it.id }.toSet()
        _orphanedInstalledStates.value = _installedStates.value.filterNot { knownIds.contains(it.extensionId) }
    }

    private fun loadInstalledStates(): List<InstalledExtensionState> {
        val stored = prefs.getString(KEY_EXTENSION_STATES_JSON, "")
        return ExtensionStateStore.decode(stored)
            .distinctBy { it.extensionId }
            .sortedByDescending { it.installedAt }
    }
}
