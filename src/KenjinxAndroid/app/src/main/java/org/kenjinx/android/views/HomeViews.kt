package org.kenjinx.android.views

import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.anggrayudi.storage.extension.launchOnUiThread
import java.util.Base64
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kenjinx.android.R
import org.kenjinx.android.viewmodels.FileType
import org.kenjinx.android.viewmodels.GameModel
import org.kenjinx.android.viewmodels.HomeViewModel
import org.kenjinx.android.viewmodels.QuickSettings
import org.kenjinx.android.widgets.SimpleAlertDialog
import org.kenjinx.android.ShortcutUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableFloatStateOf
import androidx.documentfile.provider.DocumentFile

// Cheats
import org.kenjinx.android.cheats.CheatPrefs
import org.kenjinx.android.cheats.CheatItem
import org.kenjinx.android.cheats.loadCheatsFromDisk
import org.kenjinx.android.cheats.applyCheatSelectionOnDisk
import org.kenjinx.android.cheats.importCheatTxt

// Mods
import org.kenjinx.android.cheats.listMods
import org.kenjinx.android.cheats.deleteMod
import org.kenjinx.android.cheats.importModsZip

// Saves
import org.kenjinx.android.saves.*

class HomeViews {
    companion object {
        const val ListImageSize = 150
        const val GridImageSize = 300

        // --- small version badge bottom left
        @Composable
        private fun VersionBadge(modifier: Modifier = Modifier) {
            Text(
                text = "v" + org.kenjinx.android.BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = modifier.padding(8.dp)
            )
        }
        // -- Helper for Shortcut Flow
        private fun resolveGameUri(gm: GameModel): Uri? = gm.file.uri

        private fun decodeGameIcon(gm: GameModel): Bitmap? {
            return try {
                val b64 = gm.icon ?: return null
                val bytes = Base64.getDecoder().decode(b64)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Throwable) {
                null
            }
        }
        @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
        @Composable
        fun Home(
            viewModel: HomeViewModel = HomeViewModel(),
            navController: NavHostController? = null,
            isPreview: Boolean = false
        ) {
            viewModel.ensureReloadIfNecessary()
            val showAppActions = remember { mutableStateOf(false) }
            val showLoading = remember { mutableStateOf(false) }
            val openTitleUpdateDialog = remember { mutableStateOf(false) }
            val canClose = remember { mutableStateOf(true) }
            val openDlcDialog = remember { mutableStateOf(false) }
            val openGameSettingsDialog = remember { mutableStateOf(false) }
            val showError = remember { mutableStateOf("") }
            val selectedModel = remember { mutableStateOf(viewModel.mainViewModel?.selected) }
            val query = remember { mutableStateOf("") }
            var refreshUser by remember { mutableStateOf(true) }
            var isFabVisible by remember { mutableStateOf(true) }
            val isNavigating = remember { mutableStateOf(false) }

            // Amiibo slot picker state
            val showAmiiboSlotDialog = remember { mutableStateOf(false) }
            val pendingSlot = remember { mutableStateOf(1) }

            // Cheats UI state
            val openCheatsDialog = remember { mutableStateOf(false) }
            val cheatsForSelected = remember { mutableStateOf(listOf<CheatItem>()) }
            val enabledCheatKeys = remember { mutableStateOf(mutableSetOf<String>()) }

            // Mods UI state
            val openModsDialog = remember { mutableStateOf(false) }
            val modsForSelected = remember { mutableStateOf(listOf<String>()) }
            val modsImportProgress = remember { mutableFloatStateOf(0f) }
            val modsImportBusy = remember { mutableStateOf(false) }
            val modsImportStatusText = remember { mutableStateOf("") }

            // Save Manager State
            val openSavesDialog = remember { mutableStateOf(false) }
            val saveImportBusy = remember { mutableStateOf(false) }
            val saveExportBusy = remember { mutableStateOf(false) }
            val saveImportProgress = remember { mutableFloatStateOf(0f) }
            val saveExportProgress = remember { mutableFloatStateOf(0f) }
            val saveImportStatus = remember { mutableStateOf("") }
            val saveExportStatus = remember { mutableStateOf("") }

            val activity = LocalContext.current as? Activity

            // Import: OpenDocument (ZIP)
            val importZipLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                val act = activity
                // Guard on selected game – optional
                val tIdNow = viewModel.mainViewModel?.selected?.titleId.orEmpty()
                if (uri != null && act != null && tIdNow.isNotEmpty()) {
                    saveImportBusy.value = true
                    saveImportProgress.floatValue = 0f
                    saveImportStatus.value = "Starting…"

                    thread {
                        val res = importSaveFromZip(act, uri) { prog ->
                            val frac = if (prog.total > 0) prog.bytes.toFloat() / prog.total else 0f
                            saveImportProgress.floatValue = frac.coerceIn(0f, 1f)
                            saveImportStatus.value = "Importing: ${prog.currentEntry}"
                        }
                        saveImportBusy.value = false
                        launchOnUiThread {
                            Toast.makeText(act, res.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            // Export: CreateDocument (ZIP)
            val exportZipLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/zip")
            ) { uri: Uri? ->
                val act = activity
                val tIdNow = viewModel.mainViewModel?.selected?.titleId.orEmpty()
                if (uri != null && act != null && tIdNow.isNotEmpty()) {
                    saveExportBusy.value = true
                    saveExportProgress.floatValue = 0f
                    saveExportStatus.value = "Starting…"

                    thread {
                        val res = exportSaveToZip(act, tIdNow, uri) { prog ->
                            val frac = if (prog.total > 0) prog.bytes.toFloat() / prog.total else 0f
                            saveExportProgress.floatValue = frac.coerceIn(0f, 1f)
                            saveExportStatus.value = "Exporting: ${prog.currentPath}"
                        }
                        saveExportBusy.value = false
                        launchOnUiThread {
                            Toast.makeText(
                                act,
                                if (res.ok) "save exported" else (res.error ?: "export failed"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            // Shortcut Dialog State
            val showShortcutDialog = remember { mutableStateOf(false) }
            val shortcutName = remember { mutableStateOf("") }

            val context = LocalContext.current

            // Amiibo Launcher (OpenDocument)
            val pickAmiiboLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null && activity != null) {
                    try {
                        activity.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                    val name = DocumentFile.fromSingleUri(activity, uri)?.name ?: "amiibo.bin"
                    val qs = QuickSettings(activity)
                    when (pendingSlot.value) {
                        1 -> { qs.amiibo1Uri = uri.toString(); qs.amiibo1Name = name }
                        2 -> { qs.amiibo2Uri = uri.toString(); qs.amiibo2Name = name }
                        3 -> { qs.amiibo3Uri = uri.toString(); qs.amiibo3Name = name }
                        4 -> { qs.amiibo4Uri = uri.toString(); qs.amiibo4Name = name }
                        5 -> { qs.amiibo5Uri = uri.toString(); qs.amiibo5Name = name }
                    }
                    qs.save()
                    Toast.makeText(activity, "Amiibo saved to slot ${pendingSlot.value}", Toast.LENGTH_SHORT).show()
                }
            }

            // Cheats Import (.txt)
            val importCheatLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                val gm = viewModel.mainViewModel?.selected
                val act = viewModel.activity
                val titleId = gm?.titleId ?: ""
                if (uri != null && act != null && titleId.isNotEmpty()) {
                    // Only accept .txt files
                    val okExt = runCatching {
                        DocumentFile.fromSingleUri(act, uri)?.name?.lowercase()?.endsWith(".txt") == true
                    }.getOrElse { false }
                    if (!okExt) {
                        Toast.makeText(act, "Please select a .txt file", Toast.LENGTH_SHORT).show()
                        return@rememberLauncherForActivityResult
                    }

                    val res = importCheatTxt(act, titleId, uri)
                    if (res.isSuccess) {
                        Toast.makeText(act, "Imported: ${res.getOrNull()?.name}", Toast.LENGTH_SHORT).show()
                        // Then update the list
                        cheatsForSelected.value = loadCheatsFromDisk(act, titleId)
                    } else {
                        Toast.makeText(act, "Import failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            // Launcher for Mods
            val pickModZipLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                val gm = viewModel.mainViewModel?.selected
                val act = viewModel.activity
                val titleId = gm?.titleId ?: ""
                if (uri != null && act != null && titleId.isNotEmpty()) {
                    // Persist permission (read)
                    try {
                        act.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) {}

                    modsImportBusy.value = true
                    modsImportProgress.floatValue = 0f
                    modsImportStatusText.value = "Starting…"

                    thread {
                        val res = importModsZip(
                            act,
                            titleId,
                            uri
                        ) { prog ->
                            modsImportProgress.floatValue = prog.fraction
                            modsImportStatusText.value = if (prog.currentEntry.isNotEmpty())
                                "Copying: ${prog.currentEntry}"
                            else
                                "Copying… ${(prog.fraction * 100).toInt()}%"
                        }

                        // Update list
                        modsForSelected.value = listMods(act, titleId)
                        modsImportBusy.value = false

                        launchOnUiThread {
                            val msg = if (res.ok)
                                "Imported: ${res.imported.joinToString(", ")}"
                            else
                                "Import failed"
                            Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()
                        }
                    }

                }
            }


            // Launcher for "Custom icon" (OpenDocument)
            val pickImageLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                val gm = viewModel.mainViewModel?.selected
                if (uri != null && gm != null && activity != null) {
                    val bmp = runCatching {
                        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()

                    val label = shortcutName.value.ifBlank { gm.titleName ?: "Start Game" }
                    val gameUri = resolveGameUri(gm)
                    if (gameUri != null) {
                        ShortcutUtils.persistReadWrite(activity, gameUri)

                        ShortcutUtils.pinShortcutForGame(
                            activity = activity,
                            gameUri = gameUri,
                            label = label,
                            iconBitmap = bmp
                        ) {

                        }
                    } else {
                        showError.value = "Shortcut failed (no game URI found)."
                    }
                }
            }

            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y < -1) isFabVisible = false
                        if (available.y > 1) isFabVisible = true
                        return Offset.Zero
                    }
                }
            }

            // NEO library: quiet header, search, and a cartridge-only grid.
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("NEO", style = MaterialTheme.typography.headlineMedium)
                                    Text(
                                        "${viewModel.gameList.size} jogos",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(onClick = {
                                        viewModel.requestReload()
                                        viewModel.ensureReloadIfNecessary()
                                    }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar")
                                    }
                                    IconButton(onClick = {
                                        val currentRoute = navController?.currentDestination?.route
                                        if (currentRoute != "settings") {
                                            navController?.navigate("settings") {
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Filled.Settings, contentDescription = "Configurações")
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = query.value,
                                onValueChange = { query.value = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                placeholder = {
                                    Text("Buscar jogo", modifier = Modifier.padding(bottom = 4.dp))
                                },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    errorContainerColor = Color.Transparent,
                                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                )
                            )
                        }
                    }
                ) { contentPadding ->
                    Column(modifier = Modifier.padding(contentPadding)) {
                        Box {
                            val list = remember { viewModel.gameList }
                            val isLoading = remember { viewModel.isLoading }

                            viewModel.filter(query.value)

                            if (!isPreview) {
                                if (isLoading.value) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .width(64.dp)
                                                .align(Alignment.Center),
                                            color = MaterialTheme.colorScheme.secondary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                } else {
                                    val size = GridImageSize / Resources.getSystem().displayMetrics.density
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = (size + 12).dp),
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(list) {
                                            it.titleName?.apply {
                                                if (isNotEmpty() && (query.value.trim().isEmpty() || lowercase(Locale.getDefault()).contains(query.value.lowercase(Locale.getDefault())))) {
                                                    GridGameItem(it, viewModel, showAppActions, showLoading, selectedModel, showError)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SimpleAlertDialog.Loading(showDialog = showLoading)
                    SimpleAlertDialog.Custom(
                        showDialog = openTitleUpdateDialog,
                        onDismissRequest = { openTitleUpdateDialog.value = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        val titleId = viewModel.mainViewModel?.selected?.titleId ?: ""
                        val name = viewModel.mainViewModel?.selected?.titleName ?: ""
                        TitleUpdateViews.Main(titleId, name, openTitleUpdateDialog, canClose)
                    }
                    SimpleAlertDialog.Custom(
                        showDialog = openDlcDialog,
                        onDismissRequest = { openDlcDialog.value = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        val titleId = viewModel.mainViewModel?.selected?.titleId ?: ""
                        val name = viewModel.mainViewModel?.selected?.titleName ?: ""
                        DlcViews.Main(titleId, name, openDlcDialog, canClose)
                    }

                    // Amiibo slot chooser dialog (outside of game)
                    if (showAmiiboSlotDialog.value) {
                        AlertDialog(
                            onDismissRequest = { showAmiiboSlotDialog.value = false },
                            title = { Text("Import Amiibo") },
                            text = {
                                Column {
                                    Text("Choose a slot to save this Amiibo:", modifier = Modifier.padding(bottom = 8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            pendingSlot.value = 1
                                            pickAmiiboLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                            showAmiiboSlotDialog.value = false
                                        }) { Text("Slot 1") }
                                        TextButton(onClick = {
                                            pendingSlot.value = 2
                                            pickAmiiboLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                            showAmiiboSlotDialog.value = false
                                        }) { Text("Slot 2") }
                                        TextButton(onClick = {
                                            pendingSlot.value = 3
                                            pickAmiiboLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                            showAmiiboSlotDialog.value = false
                                        }) { Text("Slot 3") }
                                    }
                                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            pendingSlot.value = 4
                                            pickAmiiboLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                            showAmiiboSlotDialog.value = false
                                        }) { Text("Slot 4") }
                                        TextButton(onClick = {
                                            pendingSlot.value = 5
                                            pickAmiiboLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                            showAmiiboSlotDialog.value = false
                                        }) { Text("Slot 5") }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showAmiiboSlotDialog.value = false }) { Text("Close") }
                            }
                        )
                    }
                }

                if (viewModel.mainViewModel?.loadGameModel?.value != null)
                    LaunchedEffect(viewModel.mainViewModel.loadGameModel.value) {
                        if (viewModel.mainViewModel.bootPath.value ==
                            "gameItem_${viewModel.mainViewModel.loadGameModel.value!!.titleName}"
                        ) {
                            viewModel.mainViewModel.bootPath.value = null

                            thread {
                                showLoading.value = true

                                // Push cheats before starting (Auto-start path)
                                val gm = viewModel.mainViewModel.loadGameModel.value!!
                                val tId = gm.titleId ?: ""
                                val act = viewModel.activity

                                val success = viewModel.mainViewModel.loadGame(gm)
                                if (success == 1) {
                                    launchOnUiThread {
                                        viewModel.mainViewModel.navigateToGame()
                                    }
                                } else {
                                    if (success == -2)
                                        showError.value = "Error loading update. Please re-add update file"
                                    viewModel.mainViewModel.loadGameModel.value!!.close()
                                }
                                showLoading.value = false
                            }
                        }
                    }

                if (showAppActions.value) {
                    val gm = viewModel.mainViewModel?.selected
                    if (gm != null) {
                        GameActionsDialog(
                            game = gm,
                            onDismiss = {
                                showAppActions.value = false
                                viewModel.mainViewModel?.apply { selected = null }
                                selectedModel.value = null
                            },
                            onPlay = {
                                showAppActions.value = false
                                val act = viewModel.activity
                                thread {
                                    showLoading.value = true
                                    val success = viewModel.mainViewModel?.loadGame(gm) ?: 0
                                    if (success == 1) {
                                        launchOnUiThread {
                                            viewModel.mainViewModel?.navigateToGame()
                                        }
                                    } else {
                                        if (success == -2)
                                            showError.value = "Error loading update. Please re-add update file"
                                        gm.close()
                                    }
                                    showLoading.value = false
                                }
                            },
                            onConfigure = {
                                showAppActions.value = false
                                openGameSettingsDialog.value = true
                            },
                            onUpdates = {
                                showAppActions.value = false
                                openTitleUpdateDialog.value = true
                            },
                            onDlc = {
                                showAppActions.value = false
                                openDlcDialog.value = true
                            },
                            onMods = {
                                showAppActions.value = false
                                val act = viewModel.activity
                                if (!gm.titleId.isNullOrEmpty() && act != null) {
                                    val titleId = gm.titleId!!
                                    modsForSelected.value = listMods(act, titleId)
                                    openModsDialog.value = true
                                } else {
                                    showError.value = "No title selected."
                                }
                            },
                            onCheats = {
                                showAppActions.value = false
                                val act = viewModel.activity
                                if (!gm.titleId.isNullOrEmpty() && act != null) {
                                    val titleId = gm.titleId!!
                                    cheatsForSelected.value = loadCheatsFromDisk(act, titleId)
                                    enabledCheatKeys.value = CheatPrefs(act).getEnabled(titleId)
                                    openCheatsDialog.value = true
                                } else {
                                    showError.value = "No title selected."
                                }
                            },
                            onSaves = {
                                showAppActions.value = false
                                openSavesDialog.value = true
                            },
                            onAmiibo = {
                                showAppActions.value = false
                                showAmiiboSlotDialog.value = true
                            },
                            onCreateShortcut = {
                                showAppActions.value = false
                                shortcutName.value = gm.titleName ?: ""
                                showShortcutDialog.value = true
                            },
                            onClearPptc = {
                                viewModel.mainViewModel?.clearPptcCache(gm.titleId.orEmpty())
                                Toast.makeText(viewModel.activity, "Cache PPTC limpo", Toast.LENGTH_SHORT).show()
                            },
                            onPurgeShader = {
                                viewModel.mainViewModel?.purgeShaderCache(gm.titleId.orEmpty())
                                Toast.makeText(viewModel.activity, "Cache de shaders limpo", Toast.LENGTH_SHORT).show()
                            },
                            onDeleteCache = {
                                viewModel.mainViewModel?.deleteCache(gm.titleId.orEmpty())
                                Toast.makeText(viewModel.activity, "Todos os caches excluídos", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        showAppActions.value = false
                    }
                }

                if (openGameSettingsDialog.value) {
                    val selectedGame = viewModel.mainViewModel?.selected
                    val selectedActivity = viewModel.activity
                    if (selectedGame != null && selectedActivity != null) {
                        GameSettingsDialog(
                            activity = selectedActivity,
                            titleId = selectedGame.titleId.orEmpty(),
                            titleName = selectedGame.titleName.orEmpty(),
                            onDismiss = { openGameSettingsDialog.value = false }
                        )
                    } else {
                        openGameSettingsDialog.value = false
                    }
                }

                // Management windows
                if (openCheatsDialog.value) {
                    BasicAlertDialog(
                        onDismissRequest = { openCheatsDialog.value = false }
                    ) {
                        val gm = viewModel.mainViewModel?.selected
                        val act = viewModel.activity
                        val titleId = gm?.titleId ?: ""

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth(0.92f)
                                .heightIn(max = 620.dp)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = {
                                        importCheatLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                                    }) { Text("Importar .txt") }

                                    Row {
                                        TextButton(onClick = { openCheatsDialog.value = false }) { Text("Cancelar") }
                                        TextButton(onClick = {
                                            val act2 = act
                                            if (act2 != null && titleId.isNotEmpty()) {
                                                CheatPrefs(act2).setEnabled(titleId, enabledCheatKeys.value)
                                                applyCheatSelectionOnDisk(act2, titleId, enabledCheatKeys.value)
                                                cheatsForSelected.value = loadCheatsFromDisk(act2, titleId)
                                            }
                                            openCheatsDialog.value = false
                                        }) { Text("Salvar") }
                                    }
                                }

                                Text("Gerenciar Cheats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = gm?.titleName ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (cheatsForSelected.value.isEmpty()) {
                                    Text("Nenhum cheat encontrado para este jogo.", modifier = Modifier.padding(vertical = 16.dp))
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f, fill = false)
                                            .padding(bottom = 12.dp)
                                    ) {
                                        items(cheatsForSelected.value) { cheat ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(
                                                    Modifier
                                                        .weight(1f)
                                                        .padding(end = 12.dp)
                                                ) {
                                                    Text(cheat.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                    Text(
                                                        cheat.buildId,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                val checked = enabledCheatKeys.value.contains(cheat.key)
                                                Switch(
                                                    checked = checked,
                                                    onCheckedChange = { isOn ->
                                                        enabledCheatKeys.value =
                                                            enabledCheatKeys.value.toMutableSet().apply {
                                                                if (isOn) add(cheat.key) else remove(cheat.key)
                                                            }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Mods Dialog ---
                if (openModsDialog.value) {
                    BasicAlertDialog(
                        onDismissRequest = { openModsDialog.value = false }
                    ) {
                        val gm = viewModel.mainViewModel?.selected
                        val act = viewModel.activity
                        val titleId = gm?.titleId ?: ""

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth(0.92f)
                                .heightIn(max = 620.dp)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        enabled = !modsImportBusy.value,
                                        onClick = { pickModZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                                    ) { Text("Importar .zip") }
                                    TextButton(onClick = { openModsDialog.value = false }) { Text("Fechar") }
                                }

                                Text("Gerenciar Mods", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = gm?.titleName ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (modsImportBusy.value) {
                                    LinearProgressIndicator(
                                        progress = { modsImportProgress.value },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                    )
                                    Text(
                                        modsImportStatusText.value,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                if (modsForSelected.value.isEmpty()) {
                                    Text("Nenhum mod encontrado para este jogo.", modifier = Modifier.padding(vertical = 16.dp))
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f, fill = false)
                                            .padding(bottom = 12.dp)
                                    ) {
                                        items(modsForSelected.value) { modName ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(modName, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                TextButton(
                                                    onClick = {
                                                        val a = act
                                                        if (a != null && titleId.isNotEmpty()) {
                                                            thread {
                                                                val ok = deleteMod(a, titleId, modName)
                                                                if (ok) {
                                                                    modsForSelected.value = listMods(a, titleId)
                                                                }
                                                            }
                                                        }
                                                    }
                                                ) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Saves Dialog ---
                if (openSavesDialog.value) {
                    BasicAlertDialog(
                        onDismissRequest = { openSavesDialog.value = false }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth(0.92f)
                                .heightIn(max = 620.dp)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text("Gerenciador de Saves", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = viewModel.mainViewModel?.selected?.titleName ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 14.dp)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        enabled = !saveImportBusy.value && !saveExportBusy.value &&
                                            (viewModel.mainViewModel?.selected?.titleId?.isNotEmpty() == true),
                                        onClick = {
                                            saveImportProgress.value = 0f
                                            saveImportStatus.value = ""
                                            importZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                        }
                                    ) { Text("Importar ZIP") }

                                    Button(
                                        enabled = !saveImportBusy.value && !saveExportBusy.value &&
                                            (viewModel.mainViewModel?.selected?.titleId?.isNotEmpty() == true),
                                        onClick = {
                                            val actLocal = activity
                                            val tIdNow = viewModel.mainViewModel?.selected?.titleId.orEmpty()
                                            if (actLocal != null && tIdNow.isNotEmpty()) {
                                                val fname = suggestedCreateDocNameForExport(actLocal, tIdNow)
                                                saveExportProgress.value = 0f
                                                saveExportStatus.value = ""
                                                exportZipLauncher.launch(fname)
                                            }
                                        }
                                    ) { Text("Exportar ZIP") }
                                }

                                if (saveImportBusy.value) {
                                    Column(Modifier.padding(top = 12.dp)) {
                                        LinearProgressIndicator(progress = { saveImportProgress.value }, modifier = Modifier.fillMaxWidth())
                                        Text(saveImportStatus.value, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (saveExportBusy.value) {
                                    Column(Modifier.padding(top = 12.dp)) {
                                        LinearProgressIndicator(progress = { saveExportProgress.value }, modifier = Modifier.fillMaxWidth())
                                        Text(saveExportStatus.value, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { openSavesDialog.value = false }) { Text("Fechar") }
                                }
                            }
                        }
                    }
                }
                // --- Shortcut Dialog
                if (showShortcutDialog.value) {
                    val gm = viewModel.mainViewModel?.selected
                    AlertDialog(
                        onDismissRequest = { showShortcutDialog.value = false },
                        title = { Text("Create shortcut") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = shortcutName.value,
                                    onValueChange = { shortcutName.value = it },
                                    label = { Text("Name") },
                                    singleLine = true
                                )
                                Text(
                                    text = "Choose icon:",
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    TextButton(onClick = {
                                        // App icon (Grid image)
                                        if (gm != null && activity != null) {
                                            val gameUri = resolveGameUri(gm)
                                            if (gameUri != null) {
                                                // Persist rights for the game file
                                                ShortcutUtils.persistReadWrite(activity, gameUri)

                                                val bmp = decodeGameIcon(gm)
                                                val label = shortcutName.value.ifBlank { gm.titleName ?: "Start Game" }

                                                ShortcutUtils.pinShortcutForGame(
                                                    activity = activity,
                                                    gameUri = gameUri,
                                                    label = label,
                                                    iconBitmap = bmp
                                                ) { }
                                                showShortcutDialog.value = false
                                            } else {
                                                showShortcutDialog.value = false
                                            }
                                        } else {
                                            showShortcutDialog.value = false
                                        }
                                    }) { Text("App icon") }

                                    TextButton(onClick = {
                                        // Custom icon: open picker
                                        pickImageLauncher.launch(arrayOf("image/*"))
                                        showShortcutDialog.value = false
                                    }) { Text("Custom icon") }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showShortcutDialog.value = false }) {
                                Text("Close")
                            }
                        }
                    )
                }

            } // End of box
        }

        @OptIn(ExperimentalFoundationApi::class)
        @Composable
        fun GridGameItem(
            gameModel: GameModel,
            viewModel: HomeViewModel,
            showAppActions: MutableState<Boolean>,
            showLoading: MutableState<Boolean>,
            selectedModel: MutableState<GameModel?>,
            showError: MutableState<String>
        ) {
            val decoder = Base64.getDecoder()
            val isSelected = selectedModel.value == gameModel

            Surface(
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .padding(3.dp)
                    .combinedClickable(
                        onClick = {
                            if (viewModel.mainViewModel?.selected != null) {
                                showAppActions.value = false
                                viewModel.mainViewModel.apply { selected = null }
                                selectedModel.value = null
                            } else if (gameModel.titleId.isNullOrEmpty()
                                || gameModel.titleId != "0000000000000000"
                                || gameModel.type == FileType.Nro
                            ) {
                                thread {
                                    showLoading.value = true
                                    val success = viewModel.mainViewModel?.loadGame(gameModel) ?: 0
                                    if (success == 1) {
                                        launchOnUiThread { viewModel.mainViewModel?.navigateToGame() }
                                    } else {
                                        if (success == -2)
                                            showError.value = "Error loading update. Please re-add update file"
                                        gameModel.close()
                                    }
                                    showLoading.value = false
                                }
                            }
                        },
                        onLongClick = {
                            viewModel.mainViewModel?.selected = gameModel
                            showAppActions.value = true
                            selectedModel.value = gameModel
                        }
                    )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "NEO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.background)
                        )
                    }
                    if (!gameModel.titleId.isNullOrEmpty()
                        && (gameModel.titleId != "0000000000000000" || gameModel.type == FileType.Nro)
                    ) {
                        if (gameModel.icon?.isNotEmpty() == true) {
                            val pic = decoder.decode(gameModel.icon)
                            Image(
                                bitmap = BitmapFactory.decodeByteArray(pic, 0, pic.size).asImageBitmap(),
                                contentDescription = gameModel.titleName ?: "Cartucho",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else if (gameModel.type == FileType.Nro) NROIcon()
                        else NotAvailableIcon()
                    } else NotAvailableIcon()
                }
            }
        }

        @Composable
        fun NotAvailableIcon() {
            val size = ListImageSize / Resources.getSystem().displayMetrics.density
            Icon(
                Icons.Filled.Add,
                contentDescription = "N/A",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .width(size.roundToInt().dp)
                    .height(size.roundToInt().dp)
            )
        }

        @Composable
        fun NROIcon() {
            val size = ListImageSize / Resources.getSystem().displayMetrics.density
            Image(
                painter = painterResource(id = R.drawable.icon_nro),
                contentDescription = "NRO",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .width(size.roundToInt().dp)
                    .height(size.roundToInt().dp)
            )
        }
    }

    @Preview
    @Composable
    fun HomePreview() {
        Home(isPreview = true)
    }
}
