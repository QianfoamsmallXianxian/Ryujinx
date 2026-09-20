package org.kenjinx.android.views

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Label
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.extension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import org.kenjinx.android.MainActivity
import org.kenjinx.android.providers.DocumentProvider
import org.kenjinx.android.viewmodels.DataImportState
import org.kenjinx.android.viewmodels.DataResetState
import org.kenjinx.android.viewmodels.FirmwareInstallState
import org.kenjinx.android.viewmodels.KeyInstallState
import org.kenjinx.android.viewmodels.MainViewModel
import org.kenjinx.android.viewmodels.SettingsViewModel
import org.kenjinx.android.viewmodels.MemoryConfiguration
import org.kenjinx.android.viewmodels.MemoryManagerMode
import org.kenjinx.android.viewmodels.VSyncMode
import org.kenjinx.android.widgets.ActionButton
import org.kenjinx.android.widgets.DropdownSelector
import org.kenjinx.android.widgets.ExpandableView
import org.kenjinx.android.widgets.SimpleAlertDialog
import org.kenjinx.android.widgets.SwitchSelector
import org.kenjinx.android.viewmodels.QuickSettings
import org.kenjinx.android.viewmodels.QuickSettings.OrientationPreference
import org.kenjinx.android.viewmodels.QuickSettings.VirtualControllerPreset
import org.kenjinx.android.KenjinxNative
import org.kenjinx.android.SystemLanguage
import org.kenjinx.android.RegionCode

class SettingViews {
    companion object {
        const val EXPANSTION_TRANSITION_DURATION = 450
        const val IMPORT_CODE = 12341

        @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
        @Composable
        fun Main(settingsViewModel: SettingsViewModel, mainViewModel: MainViewModel) {
            val loaded = remember { mutableStateOf(false) }
            val memoryManagerMode = remember { mutableStateOf(MemoryManagerMode.HostMappedUnsafe) }
            val useNce = remember { mutableStateOf(false) }
            val memoryConfiguration = remember { mutableStateOf(MemoryConfiguration.MemoryConfiguration4GiB) }
            val memorySlackEnabled = remember { mutableStateOf(true) }
            val vSyncMode = remember { mutableStateOf(VSyncMode.Switch) }
            val enableDocked = remember { mutableStateOf(false) }
            val enablePptc = remember { mutableStateOf(false) }
            val enableLowPowerPptc = remember { mutableStateOf(false) }
            val enableJitCacheEviction = remember { mutableStateOf(false) }
            val enableFsIntegrityChecks = remember { mutableStateOf(false) }
            val fsGlobalAccessLogMode = remember { mutableIntStateOf(0) }
            val ignoreMissingServices = remember { mutableStateOf(false) }
            val enableShaderCache = remember { mutableStateOf(false) }
            val enableShaderLoopSafety = remember { mutableStateOf(false) }
            val enableTextureRecompression = remember { mutableStateOf(false) }
            val enableMacroHLE = remember { mutableStateOf(false) }
            val stretchToFullscreen = remember { mutableStateOf(false) }
            val resScale = remember { mutableFloatStateOf(1f) }
            val maxAnisotropy = remember { mutableFloatStateOf(0f) }
            val useVirtualController = remember { mutableStateOf(true) }
            val showKeyDialog = remember { mutableStateOf(false) }
            val keyInstallState = remember { mutableStateOf(KeyInstallState.File) }
            val showFirwmareDialog = remember { mutableStateOf(false) }
            val firmwareInstallState = remember { mutableStateOf(FirmwareInstallState.File) }
            val firmwareVersion = remember { mutableStateOf(mainViewModel.firmwareVersion) }
            val showDataResetDialog = remember { mutableStateOf(false) }
            val showDataImportDialog = remember { mutableStateOf(false) }
            val dataResetState = remember { mutableStateOf(DataResetState.Query) }
            val dataImportState = remember { mutableStateOf(DataImportState.File) }
            val dataFile = remember { mutableStateOf<DocumentFile?>(null) }
            val isGrid = remember { mutableStateOf(true) }
            val useSwitchLayout = remember { mutableStateOf(true) }
            val enableMotion = remember { mutableStateOf(true) }
            val vcPreset = remember {
                mutableStateOf(QuickSettings(mainViewModel.activity).virtualControllerPreset)
            }
            val enablePerformanceMode = remember { mutableStateOf(true) }
            val showPerformanceOverlay = remember {
                mutableStateOf(QuickSettings(mainViewModel.activity).showPerformanceOverlay)
            }
            val controllerStickSensitivity = remember { mutableFloatStateOf(1.0f) }

            val controllerScale = remember {
                mutableFloatStateOf(QuickSettings(mainViewModel.activity).controllerScale)
            }

            val enableStubLogs = remember { mutableStateOf(true) }
            val enableInfoLogs = remember { mutableStateOf(true) }
            val enableWarningLogs = remember { mutableStateOf(true) }
            val enableErrorLogs = remember { mutableStateOf(true) }
            val enableGuestLogs = remember { mutableStateOf(true) }
            val enableFsAccessLogs = remember { mutableStateOf(true) }
            val enableTraceLogs = remember { mutableStateOf(true) }
            val enableDebugLogs = remember { mutableStateOf(true) }
            val enableGraphicsLogs = remember { mutableStateOf(true) }
            val isNavigating = remember { mutableStateOf(false) }

            val orientationPref = remember {
                mutableStateOf(QuickSettings(mainViewModel.activity).orientationPreference)
            }

            val systemLanguage = remember { mutableStateOf(SystemLanguage.AmericanEnglish) }
            val regionCode = remember { mutableStateOf(RegionCode.USA) }

            val disableThreadedRendering = remember {
                mutableStateOf(QuickSettings(mainViewModel.activity).disableThreadedRendering)
            }
            val threadToggleInitialized = remember { mutableStateOf(false) }
            val isDriverSelectorOpen = remember { mutableStateOf(false) }

            if (!loaded.value) {
                settingsViewModel.initializeState(
                    memoryManagerMode,
                    useNce,
                    memoryConfiguration,
                    memorySlackEnabled,
                    vSyncMode,
                    enableDocked,
                    enablePptc,
                    enableLowPowerPptc,
                    enableJitCacheEviction,
                    enableFsIntegrityChecks,
                    fsGlobalAccessLogMode,
                    ignoreMissingServices,
                    enableShaderCache,
                    enableShaderLoopSafety,
                    enableTextureRecompression,
                    enableMacroHLE,
                    stretchToFullscreen,
                    resScale,
                    maxAnisotropy,
                    useVirtualController,
                    isGrid,
                    useSwitchLayout,
                    enableMotion,
                    enablePerformanceMode,
                    controllerStickSensitivity,
                    enableStubLogs,
                    enableInfoLogs,
                    enableWarningLogs,
                    enableErrorLogs,
                    enableGuestLogs,
                    enableFsAccessLogs,
                    enableTraceLogs,
                    enableDebugLogs,
                    enableGraphicsLogs,
                    showPerformanceOverlay,
                    vcPreset,
                    systemLanguage,
                    regionCode
                )
                loaded.value = true
            }

            fun saveAllSettings() {
                settingsViewModel.save(
                    memoryManagerMode,
                    useNce,
                    memoryConfiguration,
                    memorySlackEnabled,
                    vSyncMode,
                    enableDocked,
                    enablePptc,
                    enableLowPowerPptc,
                    enableJitCacheEviction,
                    enableFsIntegrityChecks,
                    fsGlobalAccessLogMode,
                    ignoreMissingServices,
                    enableShaderCache,
                    enableShaderLoopSafety,
                    enableTextureRecompression,
                    enableMacroHLE,
                    stretchToFullscreen,
                    resScale,
                    maxAnisotropy,
                    useVirtualController,
                    isGrid,
                    useSwitchLayout,
                    enableMotion,
                    enablePerformanceMode,
                    controllerStickSensitivity,
                    enableStubLogs,
                    enableInfoLogs,
                    enableWarningLogs,
                    enableErrorLogs,
                    enableGuestLogs,
                    enableFsAccessLogs,
                    enableTraceLogs,
                    enableDebugLogs,
                    enableGraphicsLogs,
                    showPerformanceOverlay,
                    vcPreset,
                    systemLanguage,
                    regionCode
                )
                val qs = QuickSettings(mainViewModel.activity)
                qs.controllerScale = controllerScale.floatValue
                qs.disableThreadedRendering = disableThreadedRendering.value
                qs.showPerformanceOverlay = showPerformanceOverlay.value
                qs.virtualControllerPreset = vcPreset.value
                qs.save()
            }

            BackHandler {
                saveAllSettings()
                mainViewModel.navController?.popBackStack()
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = { Text(text = "Configurações", fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = {
                                saveAllSettings()
                                if (!isNavigating.value) {
                                    isNavigating.value = true
                                    mainViewModel.navController?.popBackStack()
                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(500)
                                        isNavigating.value = false
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar"
                                )
                            }
                        }
                    )
                }
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ==========================================
                    // 1. GERAL
                    // ==========================================
                    ExpandableView(onCardArrowClick = { }, title = "Geral", icon = Icons.Outlined.Tune, isFirst = true) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OrientationDropdown(
                                selectedOrientation = orientationPref.value,
                                onOrientationSelected = { sel ->
                                    orientationPref.value = sel
                                    val qs = QuickSettings(mainViewModel.activity)
                                    qs.orientationPreference = sel
                                    qs.save()
                                    val act = mainViewModel.activity
                                    act.requestedOrientation = sel.value
                                    val rot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        act.display?.rotation
                                    } else {
                                        null
                                    }
                                    mainViewModel.gameHost?.onOrientationOrSizeChanged(rot)
                                }
                            )
                            showPerformanceOverlay.SwitchSelector(label = "Mostrar estatísticas na tela")
                            enablePerformanceMode.SwitchSelector(label = "Modo de alto desempenho")
                        }
                    }

                    // ==========================================
                    // 2. EMULAÇÃO
                    // ==========================================
                    ExpandableView(onCardArrowClick = { }, title = "Emulação", icon = Icons.Outlined.Memory) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                "Execução de CPU",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ChoiceCard("NCE (Nativo)", useNce.value, Modifier.weight(1f)) { useNce.value = true }
                                ChoiceCard("JIT (Dinâmico)", !useNce.value, Modifier.weight(1f)) { useNce.value = false }
                            }

                            enablePptc.SwitchSelector(label = "PPTC (Profiled Persistent Translation Cache)")
                            enableLowPowerPptc.SwitchSelector(label = "PPTC de baixo consumo")
                            enableJitCacheEviction.SwitchSelector(label = "Limpeza de cache JIT")

                            Text(
                                "Memory Slack",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ChoiceCard("Ligado (12 GiB)", memorySlackEnabled.value, Modifier.weight(1f)) { memorySlackEnabled.value = true }
                                ChoiceCard("Desligado (0 GiB)", !memorySlackEnabled.value, Modifier.weight(1f)) { memorySlackEnabled.value = false }
                            }

                            MemoryModeDropdown(
                                selectedMemoryManagerMode = memoryManagerMode.value,
                                onModeSelected = { mode -> memoryManagerMode.value = mode }
                            )

                            MemoryDropdown(
                                selectedMemoryConfiguration = memoryConfiguration.value,
                                onConfigurationSelected = { conf -> memoryConfiguration.value = conf }
                            )
                        }
                    }

                    // ==========================================
                    // 3. GRÁFICOS
                    // ==========================================
                    ExpandableView(onCardArrowClick = { }, title = "Gráficos", icon = Icons.Outlined.Panorama) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = { isDriverSelectorOpen.value = !isDriverSelectorOpen.value },
                                    text = "Instalar driver Vulkan",
                                    icon = Icons.Default.Build,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }

                            SimpleAlertDialog.Custom(
                                showDialog = isDriverSelectorOpen,
                                onDismissRequest = { isDriverSelectorOpen.value = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false),
                            ) {
                                VulkanDriverViews.Main(settingsViewModel.activity, isDriverSelectorOpen)
                            }

                            Text(
                                "Sincronização vertical (VSync)",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ChoiceCard("Switch (60 Hz)", vSyncMode.value == VSyncMode.Switch, Modifier.weight(1f)) { vSyncMode.value = VSyncMode.Switch }
                                ChoiceCard("Ilimitado", vSyncMode.value == VSyncMode.Unbounded, Modifier.weight(1f)) { vSyncMode.value = VSyncMode.Unbounded }
                            }

                            Text(
                                "Escala de resolução",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(0.5f to "0.5x", 0.75f to "0.75x", 1f to "1.0x", 2f to "2.0x", 3f to "3.0x").forEach { (v, lbl) ->
                                    ChoiceCard(lbl, resScale.floatValue == v, Modifier.weight(1f)) { resScale.floatValue = v }
                                }
                            }

                            Text(
                                "Filtro anisotrópico",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(0f to "Auto", 2f to "4x", 3f to "8x", 4f to "16x").forEach { (v, lbl) ->
                                    ChoiceCard(lbl, maxAnisotropy.floatValue == v, Modifier.weight(1f)) { maxAnisotropy.floatValue = v }
                                }
                            }

                            enableDocked.SwitchSelector(label = "Modo Docked")
                            enableShaderCache.SwitchSelector(label = "Cache de shaders")
                            enableShaderLoopSafety.SwitchSelector(label = "Proteção de loop de shader")
                            enableTextureRecompression.SwitchSelector(label = "Recompressão de texturas")
                            enableMacroHLE.SwitchSelector(label = "Macro HLE")
                            stretchToFullscreen.SwitchSelector(label = "Esticar para tela inteira")

                            LaunchedEffect(disableThreadedRendering.value) {
                                if (threadToggleInitialized.value) {
                                    val backendVal = if (disableThreadedRendering.value) 1 else 2
                                    KenjinxNative.graphicsSetBackendThreading(backendVal)
                                } else {
                                    threadToggleInitialized.value = true
                                }
                            }
                            disableThreadedRendering.SwitchSelector(label = "Desativar renderização threaded")
                        }
                    }

                    // ==========================================
                    // 4. CONTROLES
                    // ==========================================
                    ExpandableView(onCardArrowClick = { }, title = "Controles", icon = Icons.Outlined.VideogameAsset) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            useVirtualController.SwitchSelector(label = "Controle na tela")

                            if (useVirtualController.value) {
                                Text(
                                    "Layout do controle",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        VirtualControllerPreset.Default to "Padrão",
                                        VirtualControllerPreset.Layout2 to "Layout 2",
                                        VirtualControllerPreset.Layout3 to "Layout 3"
                                    ).forEach { (preset, label) ->
                                        ChoiceCard(label, vcPreset.value == preset, Modifier.weight(1f)) { vcPreset.value = preset }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        VirtualControllerPreset.Layout4 to "Layout 4",
                                        VirtualControllerPreset.Layout5 to "Layout 5",
                                        VirtualControllerPreset.Layout6 to "Layout 6"
                                    ).forEach { (preset, label) ->
                                        ChoiceCard(label, vcPreset.value == preset, Modifier.weight(1f)) { vcPreset.value = preset }
                                    }
                                }
                            }

                            useSwitchLayout.SwitchSelector(label = "Inverter A/B e X/Y (Layout Switch)")
                            enableMotion.SwitchSelector(label = "Movimento (Giroscópio)")

                            val stickInteractionSource = remember { MutableInteractionSource() }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Sensibilidade dos analógicos",
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Slider(
                                    modifier = Modifier.width(200.dp),
                                    value = controllerStickSensitivity.floatValue,
                                    onValueChange = { controllerStickSensitivity.floatValue = it },
                                    valueRange = 0.5f..2.0f,
                                    steps = 15,
                                    interactionSource = stickInteractionSource
                                )
                            }

                            val scaleInteractionSource = remember { MutableInteractionSource() }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Escala do controle na tela",
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Slider(
                                    modifier = Modifier.width(200.dp),
                                    value = controllerScale.floatValue,
                                    onValueChange = { controllerScale.floatValue = it.coerceIn(0.5f, 1.5f) },
                                    valueRange = 0.5f..1.5f,
                                    steps = 20,
                                    interactionSource = scaleInteractionSource
                                )
                            }
                        }
                    }

                    // ==========================================
                    // 5. SISTEMA
                    // ==========================================
                    ExpandableView(onCardArrowClick = { }, title = "Sistema", icon = Icons.Outlined.Settings) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LanguageDropdown(
                                selectedLanguage = systemLanguage.value,
                                onLanguageSelected = { lang -> systemLanguage.value = lang }
                            )

                            RegionDropdown(
                                selectedRegion = regionCode.value,
                                onRegionSelected = { reg -> regionCode.value = reg }
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Firmware do console")
                                Text(
                                    firmwareVersion.value.ifBlank { "Não instalado" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = { showKeyDialog.value = true },
                                    text = "Instalar Keys (prod.keys)",
                                    icon = Icons.Default.Build,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = { showFirwmareDialog.value = true },
                                    text = "Instalar Firmware",
                                    icon = Icons.Default.Build,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }
                        }
                    }

                    // ==========================================
                    // 6. JOGOS E DADOS
                    // ==========================================
                    ExpandableView(onCardArrowClick = { }, title = "Jogos e dados", icon = Icons.Outlined.Folder) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = { settingsViewModel.openGameFolder() },
                                    text = "Adicionar pasta de jogos",
                                    icon = Icons.Default.Add,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = { settingsViewModel.openUpdatesFolder() },
                                    text = "Selecionar pasta de Updates/DLC",
                                    icon = Icons.Outlined.Folder,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = {
                                        fun createIntent(action: String): Intent {
                                            val intent = Intent(action)
                                            intent.addCategory(Intent.CATEGORY_DEFAULT)
                                            intent.data = DocumentsContract.buildRootUri(
                                                DocumentProvider.AUTHORITY,
                                                DocumentProvider.ROOT_ID
                                            )
                                            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                            return intent
                                        }
                                        try {
                                            mainViewModel.activity.startActivity(createIntent(Intent.ACTION_VIEW))
                                            return@ActionButton
                                        } catch (_: ActivityNotFoundException) {}
                                        try {
                                            mainViewModel.activity.startActivity(createIntent("android.provider.action.BROWSE"))
                                            return@ActionButton
                                        } catch (_: ActivityNotFoundException) {}
                                        try {
                                            mainViewModel.activity.startActivity(createIntent("com.google.android.documentsui"))
                                            return@ActionButton
                                        } catch (_: ActivityNotFoundException) {}
                                        try {
                                            mainViewModel.activity.startActivity(createIntent("com.android.documentsui"))
                                            return@ActionButton
                                        } catch (_: ActivityNotFoundException) {}
                                    },
                                    text = "Abrir pasta de dados",
                                    icon = Icons.Default.Home,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = { showDataImportDialog.value = true },
                                    text = "Importar dados da aplicação (ZIP)",
                                    icon = Icons.Default.FileDownload,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = { showDataResetDialog.value = true },
                                    text = "Redefinir dados da aplicação",
                                    icon = Icons.Default.Create,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }
                        }
                    }

                    // ==========================================
                    // 7. AVANÇADO
                    // ==========================================
                    ExpandableView(onCardArrowClick = { }, title = "Avançado", icon = Icons.Outlined.Code) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            enableFsIntegrityChecks.SwitchSelector(label = "Verificações de integridade do FS")
                            ignoreMissingServices.SwitchSelector(label = "Ignorar serviços ausentes")

                            enableStubLogs.SwitchSelector(label = "Stub Logs")
                            enableInfoLogs.SwitchSelector(label = "Info Logs")
                            enableWarningLogs.SwitchSelector(label = "Warning Logs")
                            enableErrorLogs.SwitchSelector(label = "Error Logs")
                            enableGuestLogs.SwitchSelector(label = "Guest Logs")
                            enableTraceLogs.SwitchSelector(label = "Trace Logs")
                            enableFsAccessLogs.SwitchSelector(label = "FS Access Logs")
                            enableDebugLogs.SwitchSelector(label = "Debug Logs")
                            enableGraphicsLogs.SwitchSelector(label = "Graphics Logs")

                            FsGlobalAccessLogModeDropdown(
                                selectedFsGlobalAccess = fsGlobalAccessLogMode.intValue,
                                onFsGlobalAccessSelected = { fsGlobalAccess ->
                                    fsGlobalAccessLogMode.intValue = fsGlobalAccess
                                }
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ActionButton(
                                    onClick = { mainViewModel.logging.requestExport() },
                                    text = "Enviar logs",
                                    icon = Icons.Default.MailOutline,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false,
                                )
                            }
                        }
                    }

                    // ==========================================
                    // 8. SOBRE
                    // ==========================================
                    ExpandableView(onCardArrowClick = { }, title = "Sobre", icon = Icons.Outlined.Info) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("NEO", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Kenji-NX • Branch neo-v2-nce2", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "Emulador de Nintendo Switch para Android focado em desempenho com motor NCE armv8.2-a e interface minimalista.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text("Base estável: 7cb21885e", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Dialogs
                SimpleAlertDialog.Custom(
                        showDialog = showKeyDialog,
                        onDismissRequest = {
                            if (keyInstallState.value != KeyInstallState.Install) {
                                showKeyDialog.value = false
                                settingsViewModel.clearKeySelection(keyInstallState)
                                keyInstallState.value = KeyInstallState.File
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Key Installation",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                textAlign = TextAlign.Center
                            )

                            when (keyInstallState.value) {
                                KeyInstallState.File -> {
                                    Text(
                                        text = "Select a key file to install key from.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                settingsViewModel.selectKey(
                                                    keyInstallState
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Select File")
                                        }
                                        Button(
                                            onClick = {
                                                showKeyDialog.value = false
                                                settingsViewModel.clearKeySelection(keyInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Cancel")
                                        }
                                    }
                                }
                                KeyInstallState.Query -> {
                                    Text(
                                        text = "Key file will be installed. Do you want to continue?",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                settingsViewModel.installKey(
                                                    keyInstallState
                                                )

                                                if (keyInstallState.value == KeyInstallState.File) {
                                                    showKeyDialog.value = false
                                                    settingsViewModel.clearKeySelection(keyInstallState)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Yes")
                                        }
                                        Button(
                                            onClick = {
                                                showKeyDialog.value = false
                                                settingsViewModel.clearKeySelection(keyInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "No")
                                        }
                                    }
                                }
                                KeyInstallState.Install -> {
                                    Text(
                                        text = "Installing key file...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }
                                KeyInstallState.Done -> {
                                    Text(
                                        text = "Key file installed.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showKeyDialog.value = false
                                                settingsViewModel.clearKeySelection(keyInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Close")
                                        }
                                    }
                                }
                                KeyInstallState.Cancelled -> {
                                    val file = settingsViewModel.selectedKeyFile
                                    if (file != null) {
                                        if (file.extension == "key") {
                                            Text(
                                                text = "Unknown Error has occurred. Please check logs.",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp, bottom = 8.dp),
                                                textAlign = TextAlign.Start
                                            )
                                        } else {
                                            Text(
                                                text = "File type is not supported.",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp, bottom = 8.dp),
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "File type is not supported.",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp, bottom = 8.dp),
                                            textAlign = TextAlign.Start
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Button(
                                            onClick = {
                                                showKeyDialog.value = false
                                                settingsViewModel.clearKeySelection(keyInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Close")
                                        }
                                    }
                                }
                            }
                        }
                    }

                SimpleAlertDialog.Custom(
                        showDialog = showFirwmareDialog,
                        onDismissRequest = {
                            if (firmwareInstallState.value != FirmwareInstallState.Install) {
                                showFirwmareDialog.value = false
                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                firmwareInstallState.value = FirmwareInstallState.File
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Firmware Installation",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                textAlign = TextAlign.Center
                            )

                            when (firmwareInstallState.value) {
                                FirmwareInstallState.File -> {
                                    Text(
                                        text = "Select a zip or xci file to install firmware from.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                settingsViewModel.selectFirmware(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Select File")
                                        }
                                        Button(
                                            onClick = {
                                                showFirwmareDialog.value = false
                                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Cancel")
                                        }
                                    }
                                }
                                FirmwareInstallState.Query -> {
                                    Text(
                                        text = "Firmware ${settingsViewModel.selectedFirmwareVersion} will be installed. Do you want to continue?",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                settingsViewModel.installFirmware(firmwareInstallState)

                                                if (firmwareInstallState.value == FirmwareInstallState.File) {
                                                    showFirwmareDialog.value = false
                                                    settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Yes")
                                        }
                                        Button(
                                            onClick = {
                                                showFirwmareDialog.value = false
                                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "No")
                                        }
                                    }
                                }
                                FirmwareInstallState.Verifying -> {
                                    Text(
                                        text = "Verifying selected file...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }
                                FirmwareInstallState.Install -> {
                                    Text(
                                        text = "Installing firmware ${settingsViewModel.selectedFirmwareVersion}...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }
                                FirmwareInstallState.Done -> {
                                    Text(
                                        text = "Firmware ${settingsViewModel.selectedFirmwareVersion}.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    firmwareVersion.value = mainViewModel.firmwareVersion

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showFirwmareDialog.value = false
                                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Close")
                                        }
                                    }
                                }
                                FirmwareInstallState.Cancelled -> {
                                    val file = settingsViewModel.selectedFirmwareFile
                                    if (file != null) {
                                        if (file.extension == "xci" || file.extension == "zip") {
                                            if (settingsViewModel.selectedFirmwareVersion.isEmpty()) {
                                                Text(
                                                    text = "Unable to find version in selected file.",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 8.dp, bottom = 8.dp),
                                                    textAlign = TextAlign.Start
                                                )
                                            } else {
                                                Text(
                                                    text = "Unknown Error has occurred. Please check logs.",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 8.dp, bottom = 8.dp),
                                                    textAlign = TextAlign.Start
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "File type is not supported.",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp, bottom = 8.dp),
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "File type is not supported.",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp, bottom = 8.dp),
                                            textAlign = TextAlign.Start
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Button(
                                            onClick = {
                                                showFirwmareDialog.value = false
                                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Close")
                                        }
                                    }
                                }
                            }
                        }
                    }

                SimpleAlertDialog.Custom(
                        showDialog = showDataResetDialog,
                        onDismissRequest = {
                            if (dataResetState.value != DataResetState.Reset) {
                                showDataResetDialog.value = false
                                dataResetState.value = DataResetState.Query
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "App Data Reinit",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                textAlign = TextAlign.Center
                            )

                            when (dataResetState.value) {
                                DataResetState.Query -> {
                                    Text(
                                        text = "Current bis, games, profiles and system folders will be reset. Do you want to continue?",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                thread {
                                                    settingsViewModel.resetAppData(dataResetState)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Yes")
                                        }
                                        Button(
                                            onClick = {
                                                showDataResetDialog.value = false
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "No")
                                        }
                                    }
                                }
                                DataResetState.Reset -> {
                                    Text(
                                        text = "Resetting app data...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }
                                DataResetState.Done -> {
                                    Text(
                                        text = "Data reset completed successfully.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showDataResetDialog.value = false
                                                firmwareVersion.value = mainViewModel.firmwareVersion
                                                dataResetState.value = DataResetState.Query
                                                mainViewModel.userViewModel.refreshUsers()
                                                mainViewModel.homeViewModel.requestReload()
                                                mainViewModel.activity.shutdownAndRestart()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Close")
                                        }
                                    }
                                }
                            }
                        }
                    }

                SimpleAlertDialog.Custom(
                        showDialog = showDataImportDialog,
                        onDismissRequest = {
                            if (dataImportState.value != DataImportState.Import) {
                                showDataImportDialog.value = false
                                dataFile.value = null
                                dataImportState.value = DataImportState.File
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "App Data Import",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                textAlign = TextAlign.Center
                            )

                            when (dataImportState.value) {
                                DataImportState.File -> {
                                    Text(
                                        text = "Select a zip file to import bis, games, profiles and system folders from another Android installation.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val storage = MainActivity.StorageHelper
                                                storage?.apply {
                                                    val callBack = this.onFileSelected
                                                    onFileSelected = { requestCode, files ->
                                                        run {
                                                            onFileSelected = callBack
                                                            if (requestCode == IMPORT_CODE) {
                                                                val file = files.firstOrNull()
                                                                file?.apply {
                                                                    if (this.extension == "zip") {
                                                                        dataFile.value = this
                                                                        dataImportState.value = DataImportState.Query
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    openFilePicker(
                                                        IMPORT_CODE,
                                                        filterMimeTypes = arrayOf("application/zip")
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Select File")
                                        }
                                        Button(
                                            onClick = {
                                                showDataImportDialog.value = false
                                                dataFile.value = null
                                                dataImportState.value = DataImportState.File
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Cancel")
                                        }
                                    }
                                }
                                DataImportState.Query -> {
                                    Text(
                                        text = "Current app data will be wiped and replaced. Do you want to continue?",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val file = dataFile.value
                                                dataFile.value = null
                                                file?.apply {
                                                    thread {
                                                        settingsViewModel.importAppData(this, dataImportState)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Yes")
                                        }
                                        Button(
                                            onClick = {
                                                showDataImportDialog.value = false
                                                dataFile.value = null
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "No")
                                        }
                                    }
                                }
                                DataImportState.Import -> {
                                    Text(
                                        text = "Importing app data...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }

                                DataImportState.Done -> {
                                    Text(
                                        text = "Data import completed successfully.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showDataImportDialog.value = false
                                                dataFile.value = null
                                                firmwareVersion.value = mainViewModel.firmwareVersion
                                                dataImportState.value = DataImportState.File
                                                mainViewModel.userViewModel.refreshUsers()
                                                mainViewModel.homeViewModel.requestReload()
                                                mainViewModel.activity.shutdownAndRestart()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(text = "Close")
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }

@Composable
        fun LanguageDropdown(
            selectedLanguage: SystemLanguage,
            onLanguageSelected: (SystemLanguage) -> Unit
        ) {
            val options = SystemLanguage.entries.toTypedArray()
            DropdownSelector(
                label = "System Language",
                selectedValue = selectedLanguage,
                options = options.toList(),
                getDisplayText = { lang ->
                    when (lang) {
                        SystemLanguage.Japanese -> "Japanese"
                        SystemLanguage.AmericanEnglish -> "English (US)"
                        SystemLanguage.French -> "French"
                        SystemLanguage.German -> "German"
                        SystemLanguage.Italian -> "Italian"
                        SystemLanguage.Spanish -> "Spanish (EU)"
                        SystemLanguage.Chinese -> "Chinese"
                        SystemLanguage.Korean -> "Korean"
                        SystemLanguage.Dutch -> "Dutch"
                        SystemLanguage.Portuguese -> "Portuguese (EU)"
                        SystemLanguage.Russian -> "Russian"
                        SystemLanguage.Taiwanese -> "Chinese (Taiwan)"
                        SystemLanguage.BritishEnglish -> "English (UK)"
                        SystemLanguage.CanadianFrench -> "French (Canada)"
                        SystemLanguage.LatinAmericanSpanish -> "Spanish (LatAm)"
                        SystemLanguage.SimplifiedChinese -> "Chinese (Simplified)"
                        SystemLanguage.TraditionalChinese -> "Chinese (Traditional)"
                        SystemLanguage.BrazilianPortuguese -> "Portuguese (Brazil)"
                    }
                },
                onOptionSelected = onLanguageSelected
            )
        }

        @Composable
        fun RegionDropdown(
            selectedRegion: RegionCode,
            onRegionSelected: (RegionCode) -> Unit
        ) {
            val options = RegionCode.entries.toTypedArray()
            DropdownSelector(
                label = "Region",
                selectedValue = selectedRegion,
                options = options.toList(),
                getDisplayText = { region ->
                    when (region) {
                        RegionCode.Japan -> "Japan"
                        RegionCode.USA -> "USA"
                        RegionCode.Europe -> "Europe"
                        RegionCode.Australia -> "Australia"
                        RegionCode.China -> "China"
                        RegionCode.Korea -> "Korea"
                        RegionCode.Taiwan -> "Taiwan"
                    }
                },
                onOptionSelected = onRegionSelected
            )
        }

        // ---- Dropdown for orientation ----
        @Composable
        fun OrientationDropdown(
            selectedOrientation: OrientationPreference,
            onOrientationSelected: (OrientationPreference) -> Unit
        ) {
            val options = listOf(
                OrientationPreference.Sensor,
                OrientationPreference.SensorLandscape,
                OrientationPreference.SensorPortrait
            )

            DropdownSelector(
                label = "Screen Orientation",
                selectedValue = selectedOrientation,
                options = options,
                getDisplayText = { opt ->
                    when (opt) {
                        OrientationPreference.Sensor -> "Sensor"
                        OrientationPreference.SensorLandscape -> "Sensor Landscape"
                        OrientationPreference.SensorPortrait -> "Sensor Portrait"
                    }
                },
                onOptionSelected = onOrientationSelected
            )
        }

        @Composable
        fun VirtualControllerPresetDropdown(
            selectedPreset: VirtualControllerPreset,
            onPresetSelected: (VirtualControllerPreset) -> Unit
        ) {
            val options = listOf(
                VirtualControllerPreset.Default,
                VirtualControllerPreset.Layout2,
                VirtualControllerPreset.Layout3,
                VirtualControllerPreset.Layout4,
                VirtualControllerPreset.Layout5,
                VirtualControllerPreset.Layout6
            )

            DropdownSelector(
                label = "Controller Layout",
                selectedValue = selectedPreset,
                options = options,
                getDisplayText = { opt ->
                    when (opt) {
                        VirtualControllerPreset.Default -> "Default"
                        VirtualControllerPreset.Layout2 -> "Layout 2"
                        VirtualControllerPreset.Layout3 -> "Layout 3"
                        VirtualControllerPreset.Layout4 -> "Layout 4"
                        VirtualControllerPreset.Layout5 -> "Layout 5"
                        VirtualControllerPreset.Layout6 -> "Layout 6"
                    }
                },
                onOptionSelected = onPresetSelected
            )
        }


        @Composable
        fun MemoryModeDropdown(
            selectedMemoryManagerMode: MemoryManagerMode,
            onModeSelected: (MemoryManagerMode) -> Unit
        ) {
            val modes = MemoryManagerMode.entries.toTypedArray()

            DropdownSelector(
                label = "Memory Manager Mode",
                selectedValue = selectedMemoryManagerMode,
                options = modes.toList(),
                getDisplayText = { mode ->
                    when(mode) {
                        MemoryManagerMode.SoftwarePageTable -> "Software"
                        MemoryManagerMode.HostMapped -> "Host (fast)"
                        MemoryManagerMode.HostMappedUnsafe -> "Host Unchecked (fastest, unsafe)"
                    }
                },
                onOptionSelected = onModeSelected
            )
        }

        @Composable
        fun VSyncDropdown(
            selectedVSyncMode: VSyncMode,
            onModeSelected: (VSyncMode) -> Unit
        ) {
            val modes = VSyncMode.entries.toTypedArray()

            DropdownSelector(
                label = "VSync",
                selectedValue = selectedVSyncMode,
                options = modes.toList(),
                getDisplayText = { mode ->
                    when(mode) {
                        VSyncMode.Switch -> "Switch"
                        VSyncMode.Unbounded -> "Unbounded"
                    }
                },
                onOptionSelected = onModeSelected
            )
        }

        @Composable
        fun MemoryDropdown(
            selectedMemoryConfiguration: MemoryConfiguration,
            onConfigurationSelected: (MemoryConfiguration) -> Unit
        ) {
            val modes = MemoryConfiguration.entries.toTypedArray()

            DropdownSelector(
                label = "DRAM Size",
                selectedValue = selectedMemoryConfiguration,
                options = modes.toList(),
                getDisplayText = { configuration ->
                    when(configuration) {
                        MemoryConfiguration.MemoryConfiguration4GiB -> "4GiB"
                        MemoryConfiguration.MemoryConfiguration6GiB -> "6GiB"
                        MemoryConfiguration.MemoryConfiguration8GiB -> "8GiB"
                        MemoryConfiguration.MemoryConfiguration10GiB -> "10GiB"
                        MemoryConfiguration.MemoryConfiguration12GiB -> "12GiB"
                    }
                },
                onOptionSelected = onConfigurationSelected
            )
        }

        @Composable
        fun ResolutionScaleDropdown(
            selectedScale: Float,
            onScaleSelected: (Float) -> Unit
        ) {
            val scaleOptions = listOf(
                0.5f to "0.5x (360p/540p)",
                0.75f to "0.75x (540p/810p)",
                1f to "1.0x (720p/1080p)",
                2f to "2.0x (1440p/2160p)",
                3f to "3.0x (2160p/3240p)",
                4f to "4.0x (2800p/4320p)"
            )

            DropdownSelector(
                label = "Resolution Scale",
                selectedValue = selectedScale,
                options = scaleOptions.map { it.first },
                getDisplayText = { scale ->
                    scaleOptions.find { it.first == scale }?.second ?: "${scale}x"
                },
                onOptionSelected = onScaleSelected
            )
        }

        @Composable
        fun AnisotropicFilteringDropdown(
            selectedAnisotropy: Float,
            onAnisotropySelected: (Float) -> Unit
        ) {
            val anisotropyOptions = listOf(
                0.0f to "0x",
                1.0f to "2x",
                2.0f to "4x",
                3.0f to "8x",
                4.0f to "16x"
            )

            DropdownSelector(
                label = "Anisotropic Filtering",
                selectedValue = selectedAnisotropy,
                options = anisotropyOptions.map { it.first },
                getDisplayText = { anisotropy ->
                    anisotropyOptions.find { it.first == anisotropy }?.second ?: "${anisotropy}x"
                },
                onOptionSelected = onAnisotropySelected
            )
        }

        @Composable
        fun FsGlobalAccessLogModeDropdown(
            selectedFsGlobalAccess: Int,
            onFsGlobalAccessSelected: (Int) -> Unit
        ) {
            val fsGlobalAccessOptions = listOf(
                0 to "0",
                1 to "1",
                2 to "2",
                3 to "3"
            )

            DropdownSelector(
                label = "Fs Global Access Log Mode",
                selectedValue = selectedFsGlobalAccess,
                options = fsGlobalAccessOptions.map { it.first },
                getDisplayText = { fsGlobalAccess ->
                    fsGlobalAccessOptions.find { it.first == fsGlobalAccess }?.second ?: "${fsGlobalAccess}x"
                },
                onOptionSelected = onFsGlobalAccessSelected
            )
        }
    }
}
