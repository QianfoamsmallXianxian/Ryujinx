package org.kenjinx.android.views
import org.kenjinx.android.viewmodels.QuickSettings.VirtualControllerPreset
import org.kenjinx.android.viewmodels.QuickSettings.OrientationPreference
import kotlin.concurrent.thread
import androidx.compose.ui.text.style.TextAlign
import org.kenjinx.android.views.VulkanDriverViews
import com.anggrayudi.storage.file.extension
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.window.DialogProperties
import org.kenjinx.android.widgets.SimpleAlertDialog
import org.kenjinx.android.RegionCode
import org.kenjinx.android.SystemLanguage
import org.kenjinx.android.MainActivity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import org.kenjinx.android.R
import org.kenjinx.android.viewmodels.DataImportState
import org.kenjinx.android.viewmodels.DataResetState
import org.kenjinx.android.viewmodels.FirmwareInstallState
import org.kenjinx.android.viewmodels.KeyInstallState
import org.kenjinx.android.viewmodels.MainViewModel
import org.kenjinx.android.viewmodels.MemoryConfiguration
import org.kenjinx.android.viewmodels.MemoryManagerMode
import org.kenjinx.android.viewmodels.QuickSettings
import org.kenjinx.android.viewmodels.SettingsViewModel
import org.kenjinx.android.viewmodels.VSyncMode

enum class SettingsCategory(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconRes: Int? = null
) {
    Geral(
        title = "Geral",
        description = "Orientação da tela, exibição de estatísticas e modo de alto desempenho",
        icon = Icons.Default.Tune
    ),
    Emulacao(
        title = "Emulação e CPU",
        description = "Modo NCE vs JIT, PPTC de baixo consumo e expansão de memória Slack",
        icon = Icons.Default.Memory
    ),
    Graficos(
        title = "Gráficos",
        description = "Driver Vulkan, sincronização VSync, resolução e cache de shaders",
        icon = Icons.Default.Palette
    ),
    Controles(
        title = "Controles",
        description = "Controle virtual na tela, layouts predefinidos, giroscópio e sensibilidade",
        icon = Icons.Default.SportsEsports,
        iconRes = R.drawable.ryujinx_neo_icone
    ),
    Sistema(
        title = "Sistema",
        description = "Idioma do console, região, firmware e chaves de segurança (prod.keys)",
        icon = Icons.Default.Devices
    ),
    Jogos(
        title = "Jogos e Dados",
        description = "Diretórios de jogos, atualizações, DLCs e gerenciamento de arquivos",
        icon = Icons.Default.Folder
    ),
    Avancado(
        title = "Avançado",
        description = "Registros de log, integridade do sistema de arquivos e serviços ausentes",
        icon = Icons.Default.Build
    ),
    Sobre(
        title = "Sobre",
        description = "Informações da versão, branch neo-v2-nce2 e créditos do projeto",
        icon = Icons.Default.Info,
        iconRes = R.drawable.ryujinx_neo_icone
    )
}

class SettingViews {
    companion object {
        const val EXPANSTION_TRANSITION_DURATION = 450
        const val IMPORT_CODE = 12341
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

            val selectedCategory = remember { mutableStateOf<SettingsCategory?>(null) }

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

            val save = {
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
            }

            // Hardware Back handling
            BackHandler(enabled = selectedCategory.value != null) {
                save()
                selectedCategory.value = null
            }
            BackHandler(enabled = selectedCategory.value == null) {
                save()
                mainViewModel.navController?.popBackStack()
            }

            // Main Dual Panel Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF141416))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val activeCat = selectedCategory.value

                    // Caixinha Superior (Header Panel)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF454545),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (activeCat == null) {
                                // Root Header: Back to Games, Logo, Title
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            save()
                                            mainViewModel.navController?.popBackStack()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Voltar",
                                            tint = Color.White
                                        )
                                    }
                                    Image(
                                        painter = painterResource(R.drawable.ryujinx_neo_logo),
                                        contentDescription = "Ryujinx Neo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.height(30.dp)
                                    )
                                    Text(
                                        "Configurações",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        save()
                                        mainViewModel.navController?.popBackStack()
                                    }
                                ) {
                                    Text("Fechar", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                // Dedicated Section Header: Back Arrow, Title, Subtitle, Save button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IconButton(
                                        onClick = {
                                            save()
                                            selectedCategory.value = null
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Voltar às Configurações",
                                            tint = Color.White
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = activeCat.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = activeCat.description,
                                            fontSize = 12.sp,
                                            color = Color(0xFFD0D0D0),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        save()
                                        selectedCategory.value = null
                                    }
                                ) {
                                    Text("Salvar", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Caixinha Principal (Content Panel)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF454545),
                        shadowElevation = 6.dp
                    ) {
                        if (activeCat == null) {
                            // Grade de Cards de Navegação (Menu Principal de Configurações)
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 280.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(SettingsCategory.entries) { cat ->
                                    CategoryNavCard(cat) {
                                        selectedCategory.value = cat
                                    }
                                }
                            }
                        } else {
                            // Janela Nativa Dedicada da Seção Selecionada
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                when (activeCat) {
                                    SettingsCategory.Geral -> {
                                        SectionTitle("Orientação da Tela")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf<Pair<OrientationPreference, String>>(
                                                OrientationPreference.SensorLandscape to "Horizontal",
                                                OrientationPreference.SensorPortrait to "Vertical",
                                                OrientationPreference.Sensor to "Sensor"
                                            ).forEach { (pref, label) ->
                                                ChoiceCard(
                                                    label = label,
                                                    selected = orientationPref.value == pref,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    orientationPref.value = pref
                                                    QuickSettings(mainViewModel.activity).orientationPreference = pref
                                                }
                                            }
                                        }

                                        HighContrastToggle(
                                            title = "Mostrar estatísticas de desempenho",
                                            description = "Exibe FPS, tempos de quadro e uso de memória sobre a tela do jogo",
                                            checked = showPerformanceOverlay.value,
                                            onCheckedChange = { showPerformanceOverlay.value = it }
                                        )

                                        HighContrastToggle(
                                            title = "Modo de alto desempenho",
                                            description = "Aumenta a prioridade do processo de emulação e frequências operacionais",
                                            checked = enablePerformanceMode.value,
                                            onCheckedChange = { enablePerformanceMode.value = it }
                                        )
                                    }

                                    SettingsCategory.Emulacao -> {
                                        SectionTitle("Modo de Execução da CPU")
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            ChoiceCard(
                                                label = "NCE (Execução Nativa)",
                                                selected = useNce.value,
                                                modifier = Modifier.weight(1f)
                                            ) { useNce.value = true }
                                            ChoiceCard(
                                                label = "JIT (Interpretador Dinâmico)",
                                                selected = !useNce.value,
                                                modifier = Modifier.weight(1f)
                                            ) { useNce.value = false }
                                        }

                                        HighContrastToggle(
                                            title = "Cache PPTC (Compilação em Perfil)",
                                            description = "Salva o código traduzido em disco para carregamento instantâneo",
                                            checked = enablePptc.value,
                                            onCheckedChange = { enablePptc.value = it }
                                        )

                                        HighContrastToggle(
                                            title = "PPTC de baixo consumo",
                                            description = "Reduz o consumo de bateria limitando threads de compilação em segundo plano",
                                            checked = enableLowPowerPptc.value,
                                            onCheckedChange = { enableLowPowerPptc.value = it }
                                        )

                                        HighContrastToggle(
                                            title = "Limpeza de cache do JIT",
                                            description = "Descarta periodicamente blocos de tradução órfãos na memória",
                                            checked = enableJitCacheEviction.value,
                                            onCheckedChange = { enableJitCacheEviction.value = it }
                                        )

                                        SectionTitle("Expansão de Memória Slack")
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            ChoiceCard(
                                                label = "Ligado (12 GiB)",
                                                selected = memorySlackEnabled.value,
                                                modifier = Modifier.weight(1f)
                                            ) { memorySlackEnabled.value = true }
                                            ChoiceCard(
                                                label = "Desligado (0 GiB)",
                                                selected = !memorySlackEnabled.value,
                                                modifier = Modifier.weight(1f)
                                            ) { memorySlackEnabled.value = false }
                                        }

                                        SectionTitle("Gerenciador de Memória")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf<Pair<MemoryManagerMode, String>>(
                                                MemoryManagerMode.HostMappedUnsafe to "Host Mapeado (Rápido)",
                                                MemoryManagerMode.SoftwarePageTable to "Software (Seguro)"
                                            ).forEach { (mode, label) ->
                                                ChoiceCard(
                                                    label = label,
                                                    selected = memoryManagerMode.value == mode,
                                                    modifier = Modifier.weight(1f)
                                                ) { memoryManagerMode.value = mode }
                                            }
                                        }

                                        SectionTitle("Tamanho da DRAM")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf<Pair<MemoryConfiguration, String>>(
                                                MemoryConfiguration.MemoryConfiguration4GiB to "4 GiB (Padrão)",
                                                MemoryConfiguration.MemoryConfiguration6GiB to "6 GiB",
                                                MemoryConfiguration.MemoryConfiguration8GiB to "8 GiB",
                                                MemoryConfiguration.MemoryConfiguration12GiB to "12 GiB"
                                            ).forEach { (cfg, label) ->
                                                ChoiceCard(
                                                    label = label,
                                                    selected = memoryConfiguration.value == cfg,
                                                    modifier = Modifier.weight(1f)
                                                ) { memoryConfiguration.value = cfg }
                                            }
                                        }
                                    }

                                    SettingsCategory.Graficos -> {
                                        SectionTitle("Driver Vulkan")
                                        ActionButtonRow(
                                            title = "Selecionar Driver Gráfico",
                                            description = "Gerenciar drivers Vulkan personalizados e do sistema",
                                            buttonText = "Configurar Driver"
                                        ) { isDriverSelectorOpen.value = true }

                                        SectionTitle("Sincronização Vertical (VSync)")
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            ChoiceCard(
                                                label = "Switch (60 Hz)",
                                                selected = vSyncMode.value == VSyncMode.Switch,
                                                modifier = Modifier.weight(1f)
                                            ) { vSyncMode.value = VSyncMode.Switch }
                                            ChoiceCard(
                                                label = "Ilimitado",
                                                selected = vSyncMode.value == VSyncMode.Unbounded,
                                                modifier = Modifier.weight(1f)
                                            ) { vSyncMode.value = VSyncMode.Unbounded }
                                        }

                                        SectionTitle("Escala de Resolução")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf<Pair<Float, String>>(0.5f to "0.5x", 0.75f to "0.75x", 1.0f to "1x", 2.0f to "2x", 3.0f to "3x").forEach { (scale, label) ->
                                                ChoiceCard(
                                                    label = label,
                                                    selected = resScale.floatValue == scale,
                                                    modifier = Modifier.weight(1f)
                                                ) { resScale.floatValue = scale }
                                            }
                                        }

                                        SectionTitle("Filtro Anisotrópico")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf<Pair<Float, String>>(-1.0f to "Automático", 4.0f to "4x", 8.0f to "8x", 16.0f to "16x").forEach { (aniso, label) ->
                                                ChoiceCard(
                                                    label = label,
                                                    selected = maxAnisotropy.floatValue == aniso,
                                                    modifier = Modifier.weight(1f)
                                                ) { maxAnisotropy.floatValue = aniso }
                                            }
                                        }

                                        HighContrastToggle(
                                            title = "Modo Docked (TV)",
                                            description = "Aumenta resolução interna para perfil docked quando suportado",
                                            checked = enableDocked.value,
                                            onCheckedChange = { enableDocked.value = it }
                                        )

                                        HighContrastToggle(
                                            title = "Cache de Shaders",
                                            description = "Armazena shaders compilados para eliminar travamentos e stuttering",
                                            checked = enableShaderCache.value,
                                            onCheckedChange = { enableShaderCache.value = it }
                                        )

                                        HighContrastToggle(
                                            title = "Proteção de Loops em Shaders (Shader Loop Safety)",
                                            description = "Previne congelamentos causados por loops infinitos de GPU",
                                            checked = enableShaderLoopSafety.value,
                                            onCheckedChange = { enableShaderLoopSafety.value = it }
                                        )

                                        HighContrastToggle(
                                            title = "Recompressão de Texturas",
                                            description = "Reduz o uso de VRAM recomprimindo formatos pesados de textura",
                                            checked = enableTextureRecompression.value,
                                            onCheckedChange = { enableTextureRecompression.value = it }
                                        )

                                        HighContrastToggle(
                                            title = "Macro HLE",
                                            description = "Acelera operações gráficas através de emulação de alto nível",
                                            checked = enableMacroHLE.value,
                                            onCheckedChange = { enableMacroHLE.value = it }
                                        )

                                        HighContrastToggle(
                                            title = "Esticar para Tela Inteira",
                                            description = "Preenche as bordas da tela ignorando a proporção 16:9",
                                            checked = stretchToFullscreen.value,
                                            onCheckedChange = { stretchToFullscreen.value = it }
                                        )

                                        HighContrastToggle(
                                            title = "Renderização em Múltiplas Threads",
                                            description = "Executa comandos de renderização em threads dedicadas para mais FPS",
                                            checked = !disableThreadedRendering.value,
                                            onCheckedChange = {
                                                disableThreadedRendering.value = !it
                                                QuickSettings(mainViewModel.activity).disableThreadedRendering = !it
                                            }
                                        )
                                    }

                                    SettingsCategory.Controles -> {
                                        HighContrastToggle(
                                            title = "Controle Virtual na Tela",
                                            description = "Exibe botões touch overlay para jogar diretamente na tela",
                                            checked = useVirtualController.value,
                                            onCheckedChange = { useVirtualController.value = it }
                                        )

                                        SectionTitle("Layout Predefinido do Controle")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf<Pair<QuickSettings.VirtualControllerPreset, String>>(
                                                QuickSettings.VirtualControllerPreset.Default to "Padrão",
                                                QuickSettings.VirtualControllerPreset.Layout2 to "Layout 2",
                                                QuickSettings.VirtualControllerPreset.Layout3 to "Layout 3",
                                                QuickSettings.VirtualControllerPreset.Layout4 to "Layout 4",
                                                QuickSettings.VirtualControllerPreset.Layout5 to "Layout 5",
                                                QuickSettings.VirtualControllerPreset.Layout6 to "Layout 6"
                                            ).forEach { (preset, label) ->
                                                ChoiceCard(
                                                    label = label,
                                                    selected = vcPreset.value == preset,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    vcPreset.value = preset
                                                    QuickSettings(mainViewModel.activity).virtualControllerPreset = preset
                                                }
                                            }
                                        }

                                        HighContrastToggle(
                                            title = "Inverter A/B e X/Y (Layout Switch)",
                                            description = "Mantém a posição nativa do botão Nintendo Switch",
                                            checked = useSwitchLayout.value,
                                            onCheckedChange = { useSwitchLayout.value = it }
                                        )

                                        HighContrastToggle(
                                            title = "Giroscópio / Sensor de Movimento",
                                            description = "Utiliza os sensores do aparelho como sensor de movimento dos Joy-Cons",
                                            checked = enableMotion.value,
                                            onCheckedChange = { enableMotion.value = it }
                                        )

                                        SectionTitle("Sensibilidade dos Analógicos: %.2fx".format(controllerStickSensitivity.floatValue))
                                        Slider(
                                            value = controllerStickSensitivity.floatValue,
                                            onValueChange = { controllerStickSensitivity.floatValue = it },
                                            valueRange = 0.5f..2.0f,
                                            steps = 15,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        SectionTitle("Escala dos Botões Touch: %.2fx".format(controllerScale.floatValue))
                                        Slider(
                                            value = controllerScale.floatValue,
                                            onValueChange = {
                                                controllerScale.floatValue = it
                                                QuickSettings(mainViewModel.activity).controllerScale = it
                                            },
                                            valueRange = 0.5f..2.0f,
                                            steps = 15,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    SettingsCategory.Sistema -> {
                                        SectionTitle("Idioma do Sistema")
                                        val langList = listOf<Pair<SystemLanguage, String>>(
                                            SystemLanguage.BrazilianPortuguese to "Português (Brasil)",
                                            SystemLanguage.AmericanEnglish to "English (US)",
                                            SystemLanguage.LatinAmericanSpanish to "Español (LatAm)",
                                            SystemLanguage.Portuguese to "Português (Portugal)",
                                            SystemLanguage.BritishEnglish to "English (UK)",
                                            SystemLanguage.Spanish to "Español (Espanha)",
                                            SystemLanguage.Japanese to "Japonês",
                                            SystemLanguage.French to "Français",
                                            SystemLanguage.German to "Deutsch",
                                            SystemLanguage.Italian to "Italiano",
                                            SystemLanguage.Russian to "Russo",
                                            SystemLanguage.SimplifiedChinese to "Chinês (Simplificado)"
                                        )
                                        LazyVerticalGrid(
                                            columns = GridCells.Adaptive(minSize = 140.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.height(180.dp)
                                        ) {
                                            items(langList) { (lang, label) ->
                                                ChoiceCard(
                                                    label = label,
                                                    selected = systemLanguage.value == lang
                                                ) { systemLanguage.value = lang }
                                            }
                                        }

                                        SectionTitle("Região do Console")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf<Pair<RegionCode, String>>(
                                                RegionCode.USA to "EUA",
                                                RegionCode.Europe to "Europa",
                                                RegionCode.Japan to "Japão",
                                                RegionCode.Australia to "Austrália"
                                            ).forEach { (reg, label) ->
                                                ChoiceCard(
                                                    label = label,
                                                    selected = regionCode.value == reg,
                                                    modifier = Modifier.weight(1f)
                                                ) { regionCode.value = reg }
                                            }
                                        }

                                        SectionTitle("Instalação de Chaves e Firmware")
                                        ActionButtonRow(
                                            title = "Instalar Chaves (prod.keys)",
                                            description = "Chaves decodificadoras necessárias para carregar os jogos",
                                            buttonText = "Instalar Keys"
                                        ) { showKeyDialog.value = true }

                                        ActionButtonRow(
                                            title = "Instalar Firmware",
                                            description = "Versão atual: ${firmwareVersion.value.ifBlank { "Nenhum instalado" }}",
                                            buttonText = "Instalar Firmware"
                                        ) { showFirwmareDialog.value = true }
                                    }

                                    SettingsCategory.Jogos -> {
                                        SectionTitle("Diretórios de Jogos")
                                        ActionButtonRow(
                                            title = "Adicionar Pasta de Jogos",
                                            description = "Selecione o diretório onde suas ROMs estão armazenadas",
                                            buttonText = "Adicionar Pasta"
                                        ) { settingsViewModel.openGameFolder() }

                                        ActionButtonRow(
                                            title = "Pasta de Atualizações e DLCs",
                                            description = "Diretório de patches e expansões instaladas",
                                            buttonText = "Selecionar Pasta"
                                        ) { settingsViewModel.openUpdatesFolder() }

                                        SectionTitle("Gerenciamento de Dados")
                                        ActionButtonRow(
                                            title = "Abrir Pasta de Dados",
                                            description = "Acessar arquivos internos do emulador no gerenciador de arquivos",
                                            buttonText = "Abrir"
                                        ) {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(
                                                    DocumentsContract.buildRootUri(
                                                        "${mainViewModel.activity.packageName}.providers",
                                                        "root"
                                                    ),
                                                    "vnd.android.document/root"
                                                )
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            try {
                                                mainViewModel.activity.startActivity(intent)
                                            } catch (_: ActivityNotFoundException) {}
                                        }

                                        ActionButtonRow(
                                            title = "Importar Dados (ZIP)",
                                            description = "Restaurar saves, configurações e firmware de um backup",
                                            buttonText = "Importar"
                                        ) { showDataImportDialog.value = true }

                                        ActionButtonRow(
                                            title = "Redefinir Dados da Aplicação",
                                            description = "Limpar todos os dados e restaurar o emulador para os padrões de fábrica",
                                            buttonText = "Redefinir"
                                        ) { showDataResetDialog.value = true }
                                    }

                                    SettingsCategory.Avancado -> {
                                        HighContrastToggle(
                                            title = "Verificações de Integridade do FS",
                                            description = "Valida assinaturas de integridade do sistema de arquivos ao carregar",
                                            checked = enableFsIntegrityChecks.value,
                                            onCheckedChange = { enableFsIntegrityChecks.value = it }
                                        )

                                        HighContrastToggle(
                                            title = "Ignorar Serviços Ausentes",
                                            description = "Evita travamentos caso o jogo requisite serviços ainda não emulados",
                                            checked = ignoreMissingServices.value,
                                            onCheckedChange = { ignoreMissingServices.value = it }
                                        )

                                        SectionTitle("Registros de Log e Diagnóstico")
                                        HighContrastToggle(
                                            title = "Logs de Stub",
                                            description = "Registra chamadas a funções não implementadas",
                                            checked = enableStubLogs.value,
                                            onCheckedChange = { enableStubLogs.value = it }
                                        )
                                        HighContrastToggle(
                                            title = "Logs de Informações (Info)",
                                            description = "Registros gerais do processo de emulação",
                                            checked = enableInfoLogs.value,
                                            onCheckedChange = { enableInfoLogs.value = it }
                                        )
                                        HighContrastToggle(
                                            title = "Logs de Avisos (Warning)",
                                            description = "Alertas de operações inesperadas",
                                            checked = enableWarningLogs.value,
                                            onCheckedChange = { enableWarningLogs.value = it }
                                        )
                                        HighContrastToggle(
                                            title = "Logs de Erros (Error)",
                                            description = "Falhas críticas durante a execução",
                                            checked = enableErrorLogs.value,
                                            onCheckedChange = { enableErrorLogs.value = it }
                                        )
                                        HighContrastToggle(
                                            title = "Logs de Gráficos (Graphics)",
                                            description = "Depuração avançada do pipeline Vulkan",
                                            checked = enableGraphicsLogs.value,
                                            onCheckedChange = { enableGraphicsLogs.value = it }
                                        )
                                    }

                                    SettingsCategory.Sobre -> {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color(0xFF2E2E32),
                                            border = BorderStroke(1.dp, Color(0xFF5A5A60)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(20.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Image(
                                                    painter = painterResource(R.drawable.ryujinx_neo_logo),
                                                    contentDescription = "Ryujinx Neo",
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.height(44.dp)
                                                )
                                                Text(
                                                    "Ryujinx Neo - Emulador de Nintendo Switch",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = Color.White
                                                )
                                                Text(
                                                    "Branch: neo-v2-nce2\nMotor NCE Armv8.2-A nativo com proteção de loop de shader e memória Slack.",
                                                    fontSize = 13.sp,
                                                    color = Color(0xFFD0D0D0)
                                                )
                                                Text(
                                                    "Interface reformulada com caixinhas modulares de alto contraste e mockups de cartucho.",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFFA0A0A0)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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

        @Composable
        private fun CategoryNavCard(
            category: SettingsCategory,
            onClick: () -> Unit
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF2E2E32),
                border = BorderStroke(1.dp, Color(0xFF5A5A60)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onClick)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (category.iconRes != null) {
                                    Image(
                                        painter = painterResource(category.iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = category.description,
                                fontSize = 12.sp,
                                color = Color(0xFFC0C0C0),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Abrir",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        @Composable
        private fun SectionTitle(title: String) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }

        @Composable
        private fun HighContrastToggle(
            title: String,
            description: String,
            checked: Boolean,
            onCheckedChange: (Boolean) -> Unit
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF2E2E32),
                border = BorderStroke(1.dp, Color(0xFF505056)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onCheckedChange(!checked) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = description,
                            fontSize = 12.sp,
                            color = Color(0xFFB0B0B8)
                        )
                    }
                    Switch(
                        checked = checked,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF6A6A75),
                            uncheckedThumbColor = Color(0xFF909090),
                            uncheckedTrackColor = Color(0xFF222226)
                        )
                    )
                }
            }
        }

        @Composable
        private fun ActionButtonRow(
            title: String,
            description: String,
            buttonText: String,
            onClick: () -> Unit
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF2E2E32),
                border = BorderStroke(1.dp, Color(0xFF505056)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = description,
                            fontSize = 12.sp,
                            color = Color(0xFFB0B0B8)
                        )
                    }
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(buttonText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
