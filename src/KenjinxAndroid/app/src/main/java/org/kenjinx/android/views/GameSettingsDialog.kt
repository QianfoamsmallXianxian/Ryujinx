package org.kenjinx.android.views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kenjinx.android.MainActivity
import org.kenjinx.android.SystemLanguage
import org.kenjinx.android.displayName
import org.kenjinx.android.viewmodels.GameSettingsStore
import org.kenjinx.android.viewmodels.QuickSettings
import org.kenjinx.android.viewmodels.VSyncMode
import org.kenjinx.android.viewmodels.VulkanDriverViewModel
import org.kenjinx.android.tr

/**
 * NEOV2-UI1: configuracoes personalizadas do jogo em uma pagina propria (antes era um segundo popup por cima
 * do menu de acoes). Mesmos dados e mesma forma de salvar de antes; so a organizacao da tela mudou:
 * - liga/desliga (sim ou nao) usa switch;
 * - escolhas curtas (ate 4 opcoes, incluindo "Usar global / Ativado / Desativado") usam botoes lado a lado;
 * - listas longas (driver, layout do controle) usam lista suspensa.
 */
@Composable
fun GameSettingsPage(
    activity: MainActivity,
    titleId: String,
    titleName: String,
    iconBase64: String?,
    onBack: () -> Unit
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
    var docked by remember(titleId) { mutableStateOf(initial.enableDocked) }
    var virtualController by remember(titleId) { mutableStateOf(initial.useVirtualController) }
    var virtualPreset by remember(titleId) { mutableStateOf(initial.virtualControllerPreset) }
    var statsLevel by remember(titleId) { mutableIntStateOf(initial.performanceOverlayLevel) }
    var motion by remember(titleId) { mutableStateOf(initial.enableMotion) }
    var vsync by remember(titleId) { mutableStateOf(initial.vSyncMode) }
    var resScale by remember(titleId) { mutableStateOf(initial.resScale) }
    var anisotropy by remember(titleId) { mutableStateOf(initial.maxAnisotropy) }
    var vtgViaComputeMode by remember(titleId) { mutableStateOf(store.vtgViaComputeMode) }
    var nceTrackingProtectionMode by remember(titleId) { mutableStateOf(store.nceTrackingProtectionMode) }
    var drawFlushMode by remember(titleId) { mutableStateOf(store.drawFlushMode) }
    var bufferReadbackMode by remember(titleId) { mutableStateOf(store.bufferReadbackMode) }
    var vp9FFmpegMode by remember(titleId) { mutableStateOf(store.vp9FFmpegMode) }
    var frameGenMode by remember(titleId) { mutableStateOf(store.frameGenMode) }
    var relaxedDmaCopyMode by remember(titleId) { mutableStateOf(store.relaxedDmaCopyMode) }
    var edenGpuMemoryMode by remember(titleId) { mutableStateOf(store.edenGpuMemoryMode) }
    var rejectUnmappedDescriptorsMode by remember(titleId) { mutableStateOf(store.rejectUnmappedDescriptorsMode) }
    var asyncLarge3DUploadMode by remember(titleId) { mutableStateOf(store.asyncLarge3DUploadMode) }
    var hugareOptimizationsMode by remember(titleId) { mutableStateOf(store.hugareOptimizationsMode) }
    var gpuOptimizationsMode by remember(titleId) { mutableStateOf(store.gpuOptimizationsMode) }
    var experimentalGameFixesEnabled by remember(titleId) { mutableStateOf(store.experimentalGameFixesEnabled) }
    var nifmOfflineEnabled by remember(titleId) { mutableStateOf(store.nifmOfflineEnabled) }
    var webOfflineTermsEnabled by remember(titleId) { mutableStateOf(store.webOfflineTermsEnabled) }
    var strictSyncMode by remember(titleId) { mutableStateOf(store.strictSyncMode) }
    val driverViewModel = remember(titleId) { VulkanDriverViewModel(activity) }
    val availableDrivers = remember(titleId) { driverViewModel.getAvailableDrivers().toList() }
    var driverOverride by remember(titleId) { mutableStateOf(store.driverOverride) }
    var languageOverride by remember(titleId) { mutableStateOf(store.languageOverride) }

    val bitmap = remember(titleId) {
        runCatching {
            iconBase64?.takeIf { it.isNotEmpty() }?.let {
                val bytes = java.util.Base64.getDecoder().decode(it)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    val save = {
        if (custom) {
            store.isEnabled = true
            store.vtgViaComputeMode = vtgViaComputeMode
            store.nceTrackingProtectionMode = nceTrackingProtectionMode
            store.drawFlushMode = drawFlushMode
            store.bufferReadbackMode = bufferReadbackMode
            store.vp9FFmpegMode = vp9FFmpegMode
            store.frameGenMode = frameGenMode
            store.relaxedDmaCopyMode = relaxedDmaCopyMode
            store.edenGpuMemoryMode = edenGpuMemoryMode
            store.rejectUnmappedDescriptorsMode = rejectUnmappedDescriptorsMode
            store.asyncLarge3DUploadMode = asyncLarge3DUploadMode
            store.gpuOptimizationsMode = gpuOptimizationsMode
            store.hugareOptimizationsMode = hugareOptimizationsMode
            store.experimentalGameFixesEnabled = experimentalGameFixesEnabled
            store.nifmOfflineEnabled = nifmOfflineEnabled
            store.webOfflineTermsEnabled = webOfflineTermsEnabled
            store.strictSyncMode = strictSyncMode
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
                    this.enableDocked = docked
                    this.useVirtualController = virtualController
                    this.virtualControllerPreset = virtualPreset
                    this.performanceOverlayLevel = statsLevel
                    this.showPerformanceOverlay = statsLevel != 0
                    this.enableMotion = motion
                    this.vSyncMode = vsync
                    this.resScale = resScale
                    this.maxAnisotropy = anisotropy
                }
            )
            store.driverOverride = driverOverride
            store.languageOverride = languageOverride
        } else {
            store.isEnabled = false
        }
    }

    val restoreGlobal = {
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
        docked = def.enableDocked
        virtualController = def.useVirtualController
        virtualPreset = def.virtualControllerPreset
        statsLevel = def.performanceOverlayLevel
        motion = def.enableMotion
        vsync = def.vSyncMode
        resScale = def.resScale
        anisotropy = def.maxAnisotropy
        vtgViaComputeMode = 0
        nceTrackingProtectionMode = 0
        drawFlushMode = 0
        bufferReadbackMode = 0
        vp9FFmpegMode = 0
        frameGenMode = 0
        relaxedDmaCopyMode = 0
        edenGpuMemoryMode = 0
        rejectUnmappedDescriptorsMode = 0
        asyncLarge3DUploadMode = 0
        gpuOptimizationsMode = 0
        hugareOptimizationsMode = 0
        experimentalGameFixesEnabled = false
        nifmOfflineEnabled = false
        webOfflineTermsEnabled = false
        strictSyncMode = 0
        driverOverride = ""
        languageOverride = ""
    }

    // Voltar (gesto ou botao) sai sem salvar, igual ao "Cancelar" de antes.
    BackHandler { onBack() }

    val globalOnOff = listOf(0 to tr("Usar global"), 1 to tr("Ativado"), 2 to tr("Desativado"))

    // Cada secao e uma funcao para poder ser colocada em 1 coluna (retrato) ou 2 colunas (paisagem).
    val systemSection: @Composable () -> Unit = {
        SettingsSection(tr("Sistema e CPU")) {
            val languageOptions = listOf("" to tr("Usar idioma global")) +
                SystemLanguage.entries.map { it.name to it.displayName }
            SelectSetting(
                tr("Idioma do jogo"),
                tr("Idioma informado ao jogo. Reinicie o jogo para aplicar."),
                languageOptions.firstOrNull { it.first == languageOverride }?.second ?: tr("Usar idioma global"),
                languageOptions,
                custom
            ) { languageOverride = it }
            SwitchSetting(tr("Execução nativa (NCE)"), tr("Desligado usa JIT (mais lento, mais compatível)."), useNce, custom) { useNce = it }
            SwitchSetting("PPTC", tr("Guarda o código já traduzido para abrir o jogo mais rápido."), enablePptc, custom) { enablePptc = it }
            SwitchSetting(tr("Memory Slack (12 GiB)"), tr("Mais memória para jogos que travam por falta dela."), memorySlack, custom) { memorySlack = it }
            ChoiceSetting(
                tr("Modo do console"),
                null,
                listOf(true to tr("Dock"), false to tr("Portátil")),
                docked,
                custom
            ) { docked = it }
        }
    }

    val graphicsSection: @Composable () -> Unit = {
        SettingsSection(tr("Gráficos")) {
            val driverOptions = listOf(
                "" to tr("Usar driver global"),
                GameSettingsStore.SYSTEM_DRIVER to tr("Driver do sistema")
            ) + availableDrivers.map { it.driverPath to "${it.name} · ${it.driverVersion}" }
            SelectSetting(
                tr("Driver Vulkan"),
                null,
                driverOptions.firstOrNull { it.first == driverOverride }?.second ?: tr("Usar driver global"),
                driverOptions,
                custom
            ) { driverOverride = it }
            ChoiceSetting(
                tr("Escala de resolução"),
                null,
                listOf(0.5f to "0.5x", 0.75f to "0.75x", 1.0f to "1x", 2.0f to "2x", 3.0f to "3x"),
                resScale,
                custom
            ) { resScale = it }
            ChoiceSetting(
                tr("Filtro anisotrópico"),
                null,
                listOf(-1.0f to tr("Auto"), 4.0f to "4x", 8.0f to "8x", 16.0f to "16x"),
                anisotropy,
                custom
            ) { anisotropy = it }
            SwitchSetting(tr("Esticar para tela inteira"), null, stretch, custom) { stretch = it }
            SwitchSetting(tr("Cache de shaders"), tr("Guarda os shaders compilados para evitar engasgos."), shaderCache, custom) { shaderCache = it }
            SwitchSetting(tr("Recompressão de texturas"), tr("Economiza memória de vídeo em texturas grandes."), textureRecompression, custom) { textureRecompression = it }
            ChoiceSetting(tr("Geração de quadros (LSFG)"), tr("Gera quadros intermediários até o teto do jogo. Exige a Lossless.dll selecionada em Configurações > Gráficos. Requer reiniciar o jogo."), globalOnOff, frameGenMode, custom) { frameGenMode = it }
        }
    }

    val controlsSection: @Composable () -> Unit = {
        SettingsSection(tr("Controles e interface")) {
            SwitchSetting(tr("Controle na tela"), null, virtualController, custom) { virtualController = it }
            val presets = listOf(
                QuickSettings.VirtualControllerPreset.Default to tr("Padrão"),
                QuickSettings.VirtualControllerPreset.Layout2 to tr("Layout 2"),
                QuickSettings.VirtualControllerPreset.Layout3 to tr("Layout 3"),
                QuickSettings.VirtualControllerPreset.Layout4 to tr("Layout 4"),
                QuickSettings.VirtualControllerPreset.Layout5 to tr("Layout 5"),
                QuickSettings.VirtualControllerPreset.Layout6 to tr("Layout 6")
            )
            SelectSetting(
                tr("Layout do controle"),
                null,
                presets.firstOrNull { it.first == virtualPreset }?.second ?: tr("Padrão"),
                presets,
                custom && virtualController
            ) { virtualPreset = it }
            val statOptions = listOf(0 to tr("Desligado"), 1 to tr("Só FPS"), 2 to tr("FPS + quadro"), 3 to tr("Completo"))
            SelectSetting(
                tr("Estatísticas de desempenho"), null,
                statOptions.first { it.first == statsLevel }.second,
                statOptions, custom
            ) { statsLevel = it }
            SwitchSetting(tr("Movimento (giroscópio)"), null, motion, custom) { motion = it }
        }
    }

    val compatibilitySection: @Composable () -> Unit = {
        SettingsSection(tr("Compatibilidade e correções")) {
            SwitchSetting(tr("Compatibilidade gráfica 2D"), tr("Corrige tela preta e escala em jogos 2D."), experimentalGameFixesEnabled, custom) { experimentalGameFixesEnabled = it }
            ChoiceSetting(tr("Otimizações de GPU"), null, globalOnOff, gpuOptimizationsMode, custom) { gpuOptimizationsMode = it }
            ChoiceSetting(tr("VTG via Compute"), null, globalOnOff, vtgViaComputeMode, custom) { vtgViaComputeMode = it }
            ChoiceSetting(tr("VP9 pelo FFmpeg"), tr("Desligue se o vídeo do jogo ficar com erros (ex.: GRID Autosport)."), globalOnOff, vp9FFmpegMode, custom) { vp9FFmpegMode = it }
            ChoiceSetting(tr("Cópia DMA relaxada"), tr("Mais desempenho em alguns jogos (ex.: Diablo II); pode causar erros gráficos."), globalOnOff, relaxedDmaCopyMode, custom) { relaxedDmaCopyMode = it }
            ChoiceSetting(tr("Memória de GPU estilo Eden"), tr("Libera buffers e texturas sem uso e fecha o jogo se a RAM acabar (ex.: DOOM Eternal). Desligue se aparecer erro gráfico."), globalOnOff, edenGpuMemoryMode, custom) { edenGpuMemoryMode = it }
            ChoiceSetting(tr("Rejeitar descritores não mapeados"), null, globalOnOff, rejectUnmappedDescriptorsMode, custom) { rejectUnmappedDescriptorsMode = it }
            ChoiceSetting(tr("Upload assíncrono de texturas 3D (Eden)"), null, globalOnOff, asyncLarge3DUploadMode, custom) { asyncLarge3DUploadMode = it }
        }
    }

    val advancedSection: @Composable () -> Unit = {
        SettingsSection(tr("Sincronização da GPU (avançado)")) {
            ChoiceSetting(
                tr("Sincronização rápida de Draw"),
                null,
                listOf(0 to tr("Usar global"), 1 to tr("Padrão"), 2 to tr("Reduzido"), 3 to tr("Desativado")),
                drawFlushMode,
                custom
            ) { drawFlushMode = it }
            ChoiceSetting(
                tr("Leitura de buffer da GPU"),
                null,
                listOf(0 to tr("Usar global"), 1 to tr("Padrão"), 2 to tr("Relaxada")),
                bufferReadbackMode,
                custom
            ) { bufferReadbackMode = it }
            ChoiceSetting(
                tr("Sincronização estrita da GPU"),
                null,
                listOf(0 to tr("Usar global"), 1 to tr("Padrão"), 2 to tr("Imediata")),
                strictSyncMode,
                custom
            ) { strictSyncMode = it }
            SwitchSetting(tr("Shader Loop Safety"), tr("Evita travamento em shaders com laços infinitos."), loopSafety, custom) { loopSafety = it }
            SwitchSetting(tr("Macro HLE"), null, macroHle, custom) { macroHle = it }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Cabecalho: voltar, icone e nome do jogo, restaurar e salvar.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Voltar"))
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        titleName.ifBlank { tr("Configurações do jogo") },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (titleId.isNotBlank()) tr("{0} • Configurações do jogo", titleId.uppercase()) else tr("Configurações do jogo"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = restoreGlobal) {
                    Text(tr("Restaurar global"), maxLines = 1, softWrap = false)
                }
                Spacer(Modifier.width(4.dp))
                Button(onClick = {
                    save()
                    onBack()
                }) {
                    Text(tr("Salvar"), maxLines = 1, softWrap = false)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val twoColumns = maxWidth >= 720.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Chave principal: sem ela, o jogo usa as configuracoes globais e o resto fica apagado.
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (custom) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (custom) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { custom = !custom }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tr("Usar configurações personalizadas"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    if (custom) tr("Este jogo usa as opções abaixo.") else tr("Desligado: este jogo usa as configurações globais."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = custom, onCheckedChange = { custom = it })
                        }
                    }

                    if (twoColumns) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                systemSection()
                                controlsSection()
                                advancedSection()
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                graphicsSection()
                                compatibilitySection()
                            }
                        }
                    } else {
                        systemSection()
                        graphicsSection()
                        controlsSection()
                        compatibilitySection()
                        advancedSection()
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SettingLabel(title: String, description: String?, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
        if (!description.isNullOrBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
            )
        }
    }
}

/** Liga/desliga (sim ou nao). */
@Composable
private fun SwitchSetting(
    title: String,
    description: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingLabel(title, description, enabled, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Escolha curta (ate ~5 opcoes): botoes lado a lado, todos visiveis. */
@Composable
private fun <T> ChoiceSetting(
    title: String,
    description: String?,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingLabel(title, description, enabled)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            options.forEach { (value, label) ->
                ChoiceCard(label, value == selected, Modifier.weight(1f), enabled = enabled) { onSelected(value) }
            }
        }
    }
}

/** Lista longa: lista suspensa. */
@Composable
private fun <T> SelectSetting(
    title: String,
    description: String?,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    enabled: Boolean,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingLabel(title, description, enabled)
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selectedLabel,
                        modifier = Modifier.weight(1f),
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = tr("Abrir opções"))
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surface
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
            selected -> MaterialTheme.colorScheme.primaryContainer
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
