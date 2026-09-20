package org.kenjinx.android.views

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.kenjinx.android.viewmodels.GameSettingsStore
import org.kenjinx.android.viewmodels.QuickSettings
import org.kenjinx.android.viewmodels.VSyncMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSettingsDialog(
    activity: Activity,
    titleId: String,
    titleName: String,
    onDismiss: () -> Unit
) {
    val store = remember(titleId) { GameSettingsStore(activity, titleId) }
    val initial = remember(titleId) { store.applyTo(QuickSettings(activity)) }

    var custom by remember(titleId) { mutableStateOf(store.isEnabled) }
    var useNce by remember(titleId) { mutableStateOf(initial.useNce) }
    var enablePptc by remember(titleId) { mutableStateOf(initial.enablePptc) }
    var memorySlack by remember(titleId) { mutableStateOf(initial.memorySlackEnabled) }
    var shaderCache by remember(titleId) { mutableStateOf(initial.enableShaderCache) }
    var loopSafety by remember(titleId) { mutableStateOf(initial.enableShaderLoopSafety) }
    var textureRecompression by remember(titleId) { mutableStateOf(initial.enableTextureRecompression) }
    var macroHle by remember(titleId) { mutableStateOf(initial.enableMacroHLE) }
    var stretch by remember(titleId) { mutableStateOf(initial.stretchToFullscreen) }
    var virtualController by remember(titleId) { mutableStateOf(initial.useVirtualController) }
    var virtualPreset by remember(titleId) { mutableStateOf(initial.virtualControllerPreset) }
    var showStats by remember(titleId) { mutableStateOf(initial.showPerformanceOverlay) }
    var motion by remember(titleId) { mutableStateOf(initial.enableMotion) }
    var vsync by remember(titleId) { mutableStateOf(initial.vSyncMode) }
    var resScale by remember(titleId) { mutableStateOf(initial.resScale) }
    var anisotropy by remember(titleId) { mutableStateOf(initial.maxAnisotropy) }

    val save = {
        if (custom) {
            store.isEnabled = true
            store.save(
                initial.apply {
                    this.useNce = useNce
                    this.enablePptc = enablePptc
                    this.memorySlackEnabled = memorySlack
                    this.enableShaderCache = shaderCache
                    this.enableShaderLoopSafety = loopSafety
                    this.enableTextureRecompression = textureRecompression
                    this.enableMacroHLE = macroHle
                    this.stretchToFullscreen = stretch
                    this.useVirtualController = virtualController
                    this.virtualControllerPreset = virtualPreset
                    this.showPerformanceOverlay = showStats
                    this.enableMotion = motion
                    this.vSyncMode = vsync
                    this.resScale = resScale
                    this.maxAnisotropy = anisotropy
                }
            )
        } else {
            store.isEnabled = false
        }
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        titleName.ifBlank { "Configurações do jogo" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (titleId.isNotBlank()) {
                        Text(
                            titleId.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Profile Mode (Global vs Custom)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Perfil", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceCard("Global", !custom, Modifier.weight(1f)) { custom = false }
                        ChoiceCard("Personalizado", custom, Modifier.weight(1f)) { custom = true }
                    }
                }

                // Emulation & CPU
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Emulação e CPU", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceCard("NCE", useNce, Modifier.weight(1f), enabled = custom) { useNce = true }
                        ChoiceCard("JIT", !useNce, Modifier.weight(1f), enabled = custom) { useNce = false }
                    }
                    ToggleRow("PPTC", enablePptc, custom) { enablePptc = it }
                    ToggleRow("Memory Slack (12 GiB)", memorySlack, custom) { memorySlack = it }
                }

                // Graphics
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gráficos", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceCard("Switch (60Hz)", vsync == VSyncMode.Switch, Modifier.weight(1f), enabled = custom) {
                            vsync = VSyncMode.Switch
                        }
                        ChoiceCard("Ilimitado", vsync == VSyncMode.Unbounded, Modifier.weight(1f), enabled = custom) {
                            vsync = VSyncMode.Unbounded
                        }
                    }
                    Text("Escala de resolução", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.5f to "0.5x", 1.0f to "1x", 2.0f to "2x", 3.0f to "3x").forEach { (scale, label) ->
                            ChoiceCard(label, resScale == scale, Modifier.weight(1f), enabled = custom) {
                                resScale = scale
                            }
                        }
                    }
                    Text("Filtro anisotrópico", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(-1.0f to "Auto", 4.0f to "4x", 8.0f to "8x", 16.0f to "16x").forEach { (aniso, label) ->
                            ChoiceCard(label, anisotropy == aniso, Modifier.weight(1f), enabled = custom) {
                                anisotropy = aniso
                            }
                        }
                    }
                    ToggleRow("Cache de shaders", shaderCache, custom) { shaderCache = it }
                    ToggleRow("Shader Loop Safety", loopSafety, custom) { loopSafety = it }
                    ToggleRow("Recompressão de texturas", textureRecompression, custom) { textureRecompression = it }
                    ToggleRow("Macro HLE", macroHle, custom) { macroHle = it }
                    ToggleRow("Esticar para tela inteira", stretch, custom) { stretch = it }
                }

                // Controls and Interface
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Controles e interface", style = MaterialTheme.typography.titleSmall)
                    ToggleRow("Controle na tela", virtualController, custom) { virtualController = it }
                    Text("Layout do controle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            QuickSettings.VirtualControllerPreset.Default to "Padrão",
                            QuickSettings.VirtualControllerPreset.Layout2 to "Layout 2",
                            QuickSettings.VirtualControllerPreset.Layout3 to "Layout 3",
                            QuickSettings.VirtualControllerPreset.Layout4 to "Layout 4",
                            QuickSettings.VirtualControllerPreset.Layout5 to "Layout 5",
                            QuickSettings.VirtualControllerPreset.Layout6 to "Layout 6"
                        ).forEach { (preset, label) ->
                            ChoiceCard(label, virtualPreset == preset, Modifier.weight(1f), enabled = custom) {
                                virtualPreset = preset
                            }
                        }
                    }
                    ToggleRow("Mostrar estatísticas", showStats, custom) { showStats = it }
                    ToggleRow("Movimento (Giroscópio)", motion, custom) { motion = it }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        store.reset()
                        custom = false
                        val def = QuickSettings(activity)
                        useNce = def.useNce
                        enablePptc = def.enablePptc
                        memorySlack = def.memorySlackEnabled
                        shaderCache = def.enableShaderCache
                        loopSafety = def.enableShaderLoopSafety
                        textureRecompression = def.enableTextureRecompression
                        macroHle = def.enableMacroHLE
                        stretch = def.stretchToFullscreen
                        virtualController = def.useVirtualController
                        virtualPreset = def.virtualControllerPreset
                        showStats = def.showPerformanceOverlay
                        motion = def.enableMotion
                        vsync = def.vSyncMode
                        resScale = def.resScale
                        anisotropy = def.maxAnisotropy
                    }) {
                        Text("Restaurar global")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    TextButton(onClick = {
                        save()
                        onDismiss()
                    }) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

@Composable
fun ChoiceCard(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}
