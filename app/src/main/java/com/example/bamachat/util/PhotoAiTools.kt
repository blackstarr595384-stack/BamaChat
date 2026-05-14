package com.example.bamachat.util

import android.content.Context
import com.google.gson.Gson

enum class PhotoAiRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

enum class PhotoAiToolId(val key: String) {
    ADJUST_TONE("adjust_tone"),
    ROTATE_FLIP("rotate_flip"),
    CROP_RESIZE("crop_resize"),
    AUTO_ENHANCE("auto_enhance"),
    BACKGROUND_REMOVE("background_remove"),
    OBJECT_ERASE("object_erase"),
    GENERATIVE_FILL("generative_fill"),
    FACE_RETOUCH("face_retouch"),
    DEBLUR("deblur"),
    UPSCALE_HD("upscale_hd"),
    STYLE_TRANSFER("style_transfer"),
    EXPORT_HD("export_hd");

    companion object {
        fun fromKey(raw: String?): PhotoAiToolId? {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == normalized }
        }
    }
}

data class PhotoAiToolManifest(
    val id: PhotoAiToolId,
    val label: String,
    val description: String,
    val risk: PhotoAiRiskLevel,
    val isCloudTool: Boolean = false
)

object PhotoAiToolCatalog {
    val curated: List<PhotoAiToolManifest> = listOf(
        PhotoAiToolManifest(
            id = PhotoAiToolId.ADJUST_TONE,
            label = "Tone Adjust",
            description = "Helligkeit, Kontrast, Sattigung und Warme.",
            risk = PhotoAiRiskLevel.LOW
        ),
        PhotoAiToolManifest(
            id = PhotoAiToolId.ROTATE_FLIP,
            label = "Rotate/Flip",
            description = "Drehen und Spiegeln.",
            risk = PhotoAiRiskLevel.LOW
        ),
        PhotoAiToolManifest(
            id = PhotoAiToolId.CROP_RESIZE,
            label = "Crop/Resize",
            description = "Ausschnitt und Seitenverhaltnis.",
            risk = PhotoAiRiskLevel.LOW
        ),
        PhotoAiToolManifest(
            id = PhotoAiToolId.AUTO_ENHANCE,
            label = "Auto Enhance",
            description = "Automatische KI-Verbesserung.",
            risk = PhotoAiRiskLevel.MEDIUM
        ),
        PhotoAiToolManifest(
            id = PhotoAiToolId.BACKGROUND_REMOVE,
            label = "Background Remove",
            description = "Hintergrund freistellen.",
            risk = PhotoAiRiskLevel.MEDIUM,
            isCloudTool = true
        ),
        PhotoAiToolManifest(
            id = PhotoAiToolId.OBJECT_ERASE,
            label = "Object Erase",
            description = "Objekte entfernen und rekonstruieren.",
            risk = PhotoAiRiskLevel.HIGH,
            isCloudTool = true
        ),
        PhotoAiToolManifest(
            id = PhotoAiToolId.GENERATIVE_FILL,
            label = "Generative Fill",
            description = "Bereiche mit KI erganzen.",
            risk = PhotoAiRiskLevel.HIGH,
            isCloudTool = true
        ),
        PhotoAiToolManifest(
            id = PhotoAiToolId.FACE_RETOUCH,
            label = "Face Retouch",
            description = "Portrat-Retusche mit Sicherheitsregeln.",
            risk = PhotoAiRiskLevel.HIGH,
            isCloudTool = true
        ),
        PhotoAiToolManifest(
            id = PhotoAiToolId.DEBLUR,
            label = "Deblur",
            description = "Unschafe Bilder verbessern.",
            risk = PhotoAiRiskLevel.MEDIUM,
            isCloudTool = true
        ),
        PhotoAiToolManifest(
            id = PhotoAiToolId.UPSCALE_HD,
            label = "Upscale HD",
            description = "Auflosung erhohen.",
            risk = PhotoAiRiskLevel.MEDIUM,
            isCloudTool = true
        ),
        PhotoAiToolManifest(
            id = PhotoAiToolId.STYLE_TRANSFER,
            label = "Style Transfer",
            description = "Kreative Stilubertragung.",
            risk = PhotoAiRiskLevel.MEDIUM,
            isCloudTool = true
        ),
        PhotoAiToolManifest(
            id = PhotoAiToolId.EXPORT_HD,
            label = "Export HD",
            description = "Finalen Export in hoher Qualitat.",
            risk = PhotoAiRiskLevel.LOW
        )
    )

    fun findById(toolId: PhotoAiToolId): PhotoAiToolManifest? {
        return curated.firstOrNull { it.id == toolId }
    }
}

data class PhotoAiPermissionSet(
    val allowCloudTools: Boolean = false,
    val requireConfirmationForHighRisk: Boolean = true,
    val enabledToolKeys: Set<String> = PhotoAiToolCatalog.curated.map { it.id.key }.toSet()
) {
    fun isToolEnabled(toolId: PhotoAiToolId): Boolean {
        return enabledToolKeys.contains(toolId.key)
    }

    fun withToolEnabled(toolId: PhotoAiToolId, enabled: Boolean): PhotoAiPermissionSet {
        val next = enabledToolKeys.toMutableSet()
        if (enabled) next += toolId.key else next -= toolId.key
        return copy(enabledToolKeys = next)
    }

    fun canRun(tool: PhotoAiToolManifest): Boolean {
        if (!isToolEnabled(tool.id)) return false
        if (tool.isCloudTool && !allowCloudTools) return false
        return true
    }

    fun requiresConfirmation(tool: PhotoAiToolManifest): Boolean {
        return requireConfirmationForHighRisk && tool.risk == PhotoAiRiskLevel.HIGH
    }
}

object PhotoAiPolicyStore {
    private const val PREFS_NAME = "settings"
    private const val KEY_PERMISSION_SET_JSON = "photo_ai_permission_set_json"

    private val gson = Gson()

    private data class PersistedPermissionSet(
        val allowCloudTools: Boolean = false,
        val requireConfirmationForHighRisk: Boolean = true,
        val enabledToolKeys: List<String> = emptyList()
    )

    fun load(context: Context): PhotoAiPermissionSet {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PERMISSION_SET_JSON, "").orEmpty().trim()
        if (raw.isBlank()) return PhotoAiPermissionSet()
        val persisted = runCatching {
            gson.fromJson(raw, PersistedPermissionSet::class.java)
        }.getOrNull()
        if (persisted == null) return PhotoAiPermissionSet()

        val validKeys = persisted.enabledToolKeys
            .mapNotNull { PhotoAiToolId.fromKey(it)?.key }
            .toSet()
            .ifEmpty { PhotoAiToolCatalog.curated.map { it.id.key }.toSet() }

        return PhotoAiPermissionSet(
            allowCloudTools = persisted.allowCloudTools,
            requireConfirmationForHighRisk = persisted.requireConfirmationForHighRisk,
            enabledToolKeys = validKeys
        )
    }

    fun save(context: Context, permissions: PhotoAiPermissionSet) {
        val payload = PersistedPermissionSet(
            allowCloudTools = permissions.allowCloudTools,
            requireConfirmationForHighRisk = permissions.requireConfirmationForHighRisk,
            enabledToolKeys = permissions.enabledToolKeys.sorted()
        )
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PERMISSION_SET_JSON, gson.toJson(payload))
            .apply()
    }
}
