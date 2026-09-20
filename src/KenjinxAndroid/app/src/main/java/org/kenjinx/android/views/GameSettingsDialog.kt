package org.kenjinx.android.views

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .heightIn(min = 500.dp, max = 610.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Perfil", style = MaterialTheme.typography.titleSmall)
                            ToggleRow("Usar configurações personalizadas para este jogo", custom, true) { custom = it }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Emulação e CPU", style = MaterialTheme.typography.titleSmall)
                            ToggleRow("Execução nativa NCE (desativado usa JIT)", useNce, custom) { useNce = it }
                            ToggleRow("PPTC", enablePptc, custom) { enablePptc = it }
                            ToggleRow("Memory Slack (12 GiB)", memorySlack, custom) { memorySlack = it }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Controles e interface", style = MaterialTheme.typography.titleSmall)
                            ToggleRow("Controle na tela", virtualController, custom) { virtualController = it }
                            ToggleRow("Mostrar estatísticas", showStats, custom) { showStats = it }
                            ToggleRow("Movimento (Giroscópio)", motion, custom) { motion = it }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Gráficos", style = MaterialTheme.typography.titleSmall)
                        ToggleRow("Limitar à velocidade do Switch", vsync == VSyncMode.Switch, custom) {
                            vsync = if (it) VSyncMode.Switch else VSyncMode.Unbounded
                        }
                        Text("Escala de resolução", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.5f to "0.5x", 1.0f to "1x", 2.0f to "2x", 3.0f to "3x").forEach { (scale, label) ->
                                ChoiceCard(label, resScale == scale, Modifier.weight(1f), enabled = custom) { resScale = scale }
                            }
                        }
                        Text("Filtro anisotrópico", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(-1.0f to "Auto", 4.0f to "4x", 8.0f to "8x", 16.0f to "16x").forEach { (aniso, label) ->
                                ChoiceCard(label, anisotropy == aniso, Modifier.weight(1f), enabled = custom) { anisotropy = aniso }
                            }
                        }
                        ToggleRow("Cache de shaders", shaderCache, custom) { shaderCache = it }
                        ToggleRow("Shader Loop Safety", loopSafety, custom) { loopSafety = it }
                        ToggleRow("Recompressão de texturas", textureRecompression, custom) { textureRecompression = it }
                        ToggleRow("Macro HLE", macroHle, custom) { macroHle = it }
                        ToggleRow("Esticar para tela inteira", stretch, custom) { stretch = it }
                        Text("Layout do controle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val presets = listOf(
                            QuickSettings.VirtualControllerPreset.Default to "Padrão",
                            QuickSettings.VirtualControllerPreset.Layout2 to "Layout 2",
                            QuickSettings.VirtualControllerPreset.Layout3 to "Layout 3",
                            QuickSettings.VirtualControllerPreset.Layout4 to "Layout 4",
                            QuickSettings.VirtualControllerPreset.Layout5 to "Layout 5",
                            QuickSettings.VirtualControllerPreset.Layout6 to "Layout 6"
                        )
                        GameSettingSelect(
                            selectedLabel = presets.first { it.first == virtualPreset }.second,
                            options = presets,
                            enabled = custom,
                            onSelected = { virtualPreset = it }
                        )
                    }
                }

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
private fun <T> GameSettingSelect(
    selectedLabel: String,
    options: List<Pair<T, String>>,
    enabled: Boolean,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = if (enabled) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedLabel, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Abrir opções")
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.White
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    }
                )
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
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = when {
                !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
        ),
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            selected -> Color(0xFFD6DAE1)
            else -> MaterialTheme.colorScheme.surface
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
