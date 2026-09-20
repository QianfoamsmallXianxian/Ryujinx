package org.kenjinx.android.viewmodels

import android.app.Activity
import android.content.Context
import androidx.core.content.edit

/**
 * Stores only per-title overrides. Global QuickSettings remain the source of truth
 * when a title has no custom profile.
 */
class GameSettingsStore(activity: Activity, titleId: String) {
    private val preferences = activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val cleanTitleId = titleId.trim().lowercase()
    private val prefix = "$cleanTitleId."

    val isValid: Boolean = cleanTitleId.isNotEmpty() && 
        cleanTitleId != "0" && 
        cleanTitleId != "0000000000000000"

    var isEnabled: Boolean
        get() = if (!isValid) false else preferences.getBoolean(key("enabled"), false)
        set(value) {
            if (isValid) {
                preferences.edit { putBoolean(key("enabled"), value) }
            }
        }

    fun applyTo(settings: QuickSettings): QuickSettings {
        if (!isValid || !isEnabled) return settings

        settings.useNce = safeBoolean(key("useNce"), settings.useNce)
        settings.enablePptc = safeBoolean(key("enablePptc"), settings.enablePptc)
        settings.memorySlackEnabled = safeBoolean(key("memorySlackEnabled"), settings.memorySlackEnabled)
        settings.vSyncMode = enumValue(
            safeInt(key("vSyncMode"), settings.vSyncMode.ordinal),
            VSyncMode.entries,
            settings.vSyncMode
        )
        settings.enableShaderCache = safeBoolean(key("enableShaderCache"), settings.enableShaderCache)
        settings.enableShaderLoopSafety = safeBoolean(key("enableShaderLoopSafety"), settings.enableShaderLoopSafety)
        settings.enableTextureRecompression = safeBoolean(key("enableTextureRecompression"), settings.enableTextureRecompression)
        settings.enableMacroHLE = safeBoolean(key("enableMacroHLE"), settings.enableMacroHLE)
        settings.stretchToFullscreen = safeBoolean(key("stretchToFullscreen"), settings.stretchToFullscreen)
        settings.resScale = safeFloat(key("resScale"), settings.resScale)
        settings.maxAnisotropy = safeFloat(key("maxAnisotropy"), settings.maxAnisotropy)
        settings.useVirtualController = safeBoolean(key("useVirtualController"), settings.useVirtualController)
        settings.enableMotion = safeBoolean(key("enableMotion"), settings.enableMotion)
        settings.showPerformanceOverlay = safeBoolean(key("showPerformanceOverlay"), settings.showPerformanceOverlay)
        settings.virtualControllerPreset = enumValue(
            safeInt(key("virtualControllerPreset"), settings.virtualControllerPreset.ordinal),
            QuickSettings.VirtualControllerPreset.entries,
            settings.virtualControllerPreset
        )
        return settings
    }

    fun save(settings: QuickSettings) {
        if (!isValid) return
        preferences.edit {
            putBoolean(key("enabled"), true)
            putBoolean(key("useNce"), settings.useNce)
            putBoolean(key("enablePptc"), settings.enablePptc)
            putBoolean(key("memorySlackEnabled"), settings.memorySlackEnabled)
            putInt(key("vSyncMode"), settings.vSyncMode.ordinal)
            putBoolean(key("enableShaderCache"), settings.enableShaderCache)
            putBoolean(key("enableShaderLoopSafety"), settings.enableShaderLoopSafety)
            putBoolean(key("enableTextureRecompression"), settings.enableTextureRecompression)
            putBoolean(key("enableMacroHLE"), settings.enableMacroHLE)
            putBoolean(key("stretchToFullscreen"), settings.stretchToFullscreen)
            putFloat(key("resScale"), settings.resScale)
            putFloat(key("maxAnisotropy"), settings.maxAnisotropy)
            putBoolean(key("useVirtualController"), settings.useVirtualController)
            putBoolean(key("enableMotion"), settings.enableMotion)
            putBoolean(key("showPerformanceOverlay"), settings.showPerformanceOverlay)
            putInt(key("virtualControllerPreset"), settings.virtualControllerPreset.ordinal)
        }
    }

    fun reset() {
        if (!isValid) return
        preferences.edit {
            preferences.all.keys
                .filter { it.startsWith(prefix) }
                .forEach(::remove)
        }
    }

    private fun key(name: String) = prefix + name

    private fun safeBoolean(key: String, fallback: Boolean): Boolean = try {
        preferences.getBoolean(key, fallback)
    } catch (_: Exception) { fallback }

    private fun safeFloat(key: String, fallback: Float): Float = try {
        preferences.getFloat(key, fallback)
    } catch (_: Exception) { fallback }

    private fun safeInt(key: String, fallback: Int): Int = try {
        preferences.getInt(key, fallback)
    } catch (_: Exception) { fallback }

    private fun <T> enumValue(index: Int, values: List<T>, fallback: T): T =
        values.getOrNull(index) ?: fallback

    companion object {
        private const val PREFERENCES_NAME = "neo_per_game_settings"
    }
}
