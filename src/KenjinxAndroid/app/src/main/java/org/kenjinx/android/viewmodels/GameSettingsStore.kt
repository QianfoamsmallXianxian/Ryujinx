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

    /**
     * Empty means follow the globally selected Vulkan driver.
     * [SYSTEM_DRIVER] forces the system Vulkan driver for this game, even when a custom driver is selected globally.
     */
    var driverOverride: String
        get() = if (!isValid) "" else preferences.getString(key("driverOverride"), "").orEmpty()
        set(value) {
            if (isValid) preferences.edit { putString(key("driverOverride"), value) }
        }

    /** NEOV2 NCE-PROT1: 0: Use Global, 1: Enabled, 2: Disabled */
    var nceTrackingProtectionMode: Int
        get() = if (!isValid) 0 else preferences.getInt(key("nceTrackingProtectionMode"), 0)
        set(value) {
            if (isValid) preferences.edit { putInt(key("nceTrackingProtectionMode"), value) }
        }

    /** NEOV2 DMA-RELAX1: 0: Use Global, 1: Enabled, 2: Disabled */
    var relaxedDmaCopyMode: Int
        get() = if (!isValid) 0 else preferences.getInt(key("relaxedDmaCopyMode"), 0)
        set(value) {
            if (isValid) preferences.edit { putInt(key("relaxedDmaCopyMode"), value) }
        }

    /** 0: Use Global, 1: Enabled, 2: Disabled */
    var vtgViaComputeMode: Int
        get() = if (!isValid) 0 else preferences.getInt(key("vtgViaComputeMode"), 0)
        set(value) {
            if (isValid) preferences.edit { putInt(key("vtgViaComputeMode"), value) }
        }

    /** 0: Use Global, 1: Padrao, 2: Reduzido, 3: Desativado */
    var drawFlushMode: Int
        get() = if (!isValid) 0 else preferences.getInt(key("drawFlushMode"), 0)
        set(value) {
            if (isValid) preferences.edit { putInt(key("drawFlushMode"), value) }
        }

    /** 0: Use Global, 1: Padrao, 2: Relaxada */
    var bufferReadbackMode: Int
        get() = if (!isValid) 0 else preferences.getInt(key("bufferReadbackMode"), 0)
        set(value) {
            if (isValid) preferences.edit { putInt(key("bufferReadbackMode"), value) }
        }

    /** 0: Use Global, 1: Padrao, 2: Imediata */
    var strictSyncMode: Int
        get() = if (!isValid) 0 else preferences.getInt(key("strictSyncMode"), 0)
        set(value) {
            if (isValid) preferences.edit { putInt(key("strictSyncMode"), value) }
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
        when (safeInt(key("vtgViaComputeMode"), 0)) {
            1 -> settings.enableVtgViaCompute = true
            2 -> settings.enableVtgViaCompute = false
        }
        when (safeInt(key("nceTrackingProtectionMode"), 0)) {
            1 -> settings.enableNceTrackingProtection = true
            2 -> settings.enableNceTrackingProtection = false
        }
        when (safeInt(key("relaxedDmaCopyMode"), 0)) {
            1 -> settings.enableRelaxedDmaCopy = true
            2 -> settings.enableRelaxedDmaCopy = false
        }
        when (safeInt(key("drawFlushMode"), 0)) {
            1 -> settings.drawFlushMode = 0
            2 -> settings.drawFlushMode = 1
            3 -> settings.drawFlushMode = 2
        }
        when (safeInt(key("bufferReadbackMode"), 0)) {
            1 -> settings.bufferReadbackMode = 0
            2 -> settings.bufferReadbackMode = 1
        }
        when (safeInt(key("strictSyncMode"), 0)) {
            1 -> settings.strictSyncMode = 0
            2 -> settings.strictSyncMode = 1
        }
        settings.enableMacroHLE = safeBoolean(key("enableMacroHLE"), settings.enableMacroHLE)
        settings.stretchToFullscreen = safeBoolean(key("stretchToFullscreen"), settings.stretchToFullscreen)
        settings.enableDocked = safeBoolean(key("enableDocked"), settings.enableDocked)
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
            putInt(key("vtgViaComputeMode"), vtgViaComputeMode)
            putInt(key("nceTrackingProtectionMode"), nceTrackingProtectionMode)
            putInt(key("relaxedDmaCopyMode"), relaxedDmaCopyMode)
            putInt(key("drawFlushMode"), drawFlushMode)
            putInt(key("bufferReadbackMode"), bufferReadbackMode)
            putInt(key("strictSyncMode"), strictSyncMode)
            putBoolean(key("enableMacroHLE"), settings.enableMacroHLE)
            putBoolean(key("stretchToFullscreen"), settings.stretchToFullscreen)
            putBoolean(key("enableDocked"), settings.enableDocked)
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

        /** Per game driver override value that selects the system Vulkan driver. */
        const val SYSTEM_DRIVER = "system"

        fun cleanupOrphanedDriver(context: Context, deletedDriverPath: String) {
            if (deletedDriverPath.isBlank()) return
            try {
                val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                val keysToClean = prefs.all.filter { (key, value) ->
                    key.endsWith(".driverOverride") && value == deletedDriverPath
                }.keys

                if (keysToClean.isNotEmpty()) {
                    prefs.edit {
                        keysToClean.forEach(::remove)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
