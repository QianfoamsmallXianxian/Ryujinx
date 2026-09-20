package org.kenjinx.android.views
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset

import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.animation.core.tween
import androidx.preference.PreferenceManager
import androidx.navigation.NavHostController
import com.anggrayudi.storage.extension.launchOnUiThread
import java.util.Base64
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
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
        private const val HOME_LAYOUT_KEY = "neoHomeLayout"
        private enum class HomeLayout { Neo, Switch }
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
            val homePreferences = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
            var homeLayout by remember {
                mutableStateOf(
                    if (homePreferences.getString(HOME_LAYOUT_KEY, HomeLayout.Neo.name) == HomeLayout.Switch.name) {
                        HomeLayout.Switch
                    } else {
                        HomeLayout.Neo
                    }
                )
            }

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

            val homeBackground by animateColorAsState(
                targetValue = Color(0xFFF2F3F5),
                animationSpec = tween(420),
                label = "homeBackground"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                homeBackground,
                                Color(0xFFE7E9ED)
                            )
                        )
                    )
            ) {
                Crossfade(targetState = homeLayout, animationSpec = tween(360), label = "homeLayout") { layout ->
                    if (layout == HomeLayout.Switch) {
                        SwitchHome(
                            viewModel = viewModel,
                            query = query,
                            showAppActions = showAppActions,
                            showLoading = showLoading,
                            selectedModel = selectedModel,
                            showError = showError,
                            onChangeLayout = {
                                homeLayout = HomeLayout.Neo
                                homePreferences.edit().putString(HOME_LAYOUT_KEY, homeLayout.name).apply()
                            },
                            onOpenSettings = {
                                category ->
                                val route = category?.let { "settings?category=$it" } ?: "settings"
                                navController?.navigate(route) { launchSingleTop = true }
                            },
                            isPreview = isPreview
                        )
                    } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 30.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                    // Caixinha Superior: Header Panel (Logo, Pill Search, Hamburger Menu)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = RoundedCornerShape(0.dp),
                        color = Color.Transparent,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Ryujinx Neo Logo from /imagens
                            Image(
                                painter = painterResource(R.drawable.ryujinx_neo_logo),
                                contentDescription = "Ryujinx Neo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .width(220.dp)
                                    .height(64.dp)
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // Center: Pill Search Bar (White rounded with magnifying glass on the right)
                            Surface(
                                modifier = Modifier
                                    .width(360.dp)
                                    .height(42.dp),
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 3.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    BasicTextField(
                                        value = query.value,
                                        onValueChange = { query.value = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = Color(0xFF222222),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            if (query.value.isEmpty()) {
                                                Text(
                                                    text = "Pesquisar jogos...",
                                                    color = Color(0xFF757575),
                                                    fontSize = 13.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Pesquisar",
                                        tint = Color(0xFF454545),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Box(
                                modifier = Modifier.width(90.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                        val currentRoute = navController?.currentDestination?.route
                                        if (currentRoute != "settings") {
                                            navController?.navigate("settings") {
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    shape = CircleShape,
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFD2D5DB)),
                                    shadowElevation = 2.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Configurações",
                                            tint = Color(0xFF30343C),
                                            modifier = Modifier.size(25.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Caixinha Principal: Library Panel (Grid de 7 Colunas com Mockup do Cartucho)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(0.dp),
                        color = Color.Transparent,
                        shadowElevation = 0.dp
                    ) {
                        val list = remember { viewModel.gameList }
                        val isLoading = remember { viewModel.isLoading }

                        viewModel.filter(query.value)

                        if (!isPreview) {
                            if (isLoading.value) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(54.dp),
                                        color = Color(0xFF30343C),
                                        trackColor = Color(0xFFD7DAE0)
                                    )
                                }
                            } else {
                                val filteredList = list.filter { game ->
                                    val title = game.titleName.orEmpty()
                                    title.isNotEmpty() && (query.value.trim().isEmpty() || title.lowercase(Locale.getDefault()).contains(query.value.lowercase(Locale.getDefault())))
                                }

                                if (filteredList.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                "Nenhum jogo encontrado",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color(0xFF20242B),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Adicione pastas de jogos no menu de configurações.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF686E78)
                                            )
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(7),
                                        contentPadding = PaddingValues(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(filteredList) { game ->
                                            GridGameItem(game, viewModel, showAppActions, showLoading, selectedModel, showError)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 30.dp, bottom = 20.dp)
                            .size(50.dp)
                            .clip(CircleShape)
                            .clickable {
                                homeLayout = HomeLayout.Switch
                                homePreferences.edit().putString(HOME_LAYOUT_KEY, homeLayout.name).apply()
                            },
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFD2D5DB)),
                        shadowElevation = 5.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ViewCarousel,
                                contentDescription = "Usar visual Switch",
                                tint = Color(0xFF30343C),
                                modifier = Modifier.size(23.dp)
                            )
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
                                TextButton(onClick = { showAmiiboSlotDialog.value = false }) { Text("Fechar") }
                            }
                        )
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
                                    }) { Text("Ícone personalizado") }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showShortcutDialog.value = false }) {
                                Text("Fechar")
                            }
                        }
                    )
                }

            } // End of box
        }

        @OptIn(ExperimentalFoundationApi::class)
        @Composable
        private fun SwitchHome(
            viewModel: HomeViewModel,
            query: MutableState<String>,
            showAppActions: MutableState<Boolean>,
            showLoading: MutableState<Boolean>,
            selectedModel: MutableState<GameModel?>,
            showError: MutableState<String>,
            onChangeLayout: () -> Unit,
            onOpenSettings: (String?) -> Unit,
            isPreview: Boolean
        ) {
            val list = remember { viewModel.gameList }
            val isLoading = remember { viewModel.isLoading }
            val currentTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
            viewModel.filter(query.value)
            val filteredList = list.filter { game ->
                val title = game.titleName.orEmpty()
                title.isNotEmpty() && (query.value.isBlank() || title.contains(query.value.trim(), ignoreCase = true))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.ryujinx_neo_logo),
                        contentDescription = "Ryujinx Neo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.width(220.dp).height(64.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .width(360.dp)
                            .height(42.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = query.value,
                                onValueChange = { query.value = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color(0xFF1A1C20), fontSize = 13.sp),
                                modifier = Modifier.weight(1f),
                                decorationBox = { field ->
                                    if (query.value.isEmpty()) Text("Pesquisar jogos...", color = Color(0xFF8B9099), fontSize = 13.sp)
                                    field()
                                }
                            )
                            Icon(Icons.Default.Search, contentDescription = "Pesquisar", tint = Color(0xFF454A54), modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Box(
                        modifier = Modifier.width(90.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            currentTime,
                            color = Color(0xFF30343C),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    when {
                        isLoading.value -> CircularProgressIndicator(color = Color(0xFF30343C))
                        !isPreview && filteredList.isEmpty() -> Text(
                            "Nenhum jogo encontrado",
                            color = Color(0xFF555A64),
                            fontWeight = FontWeight.Medium
                        )
                        else -> LazyRow(
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(filteredList) { game ->
                                SwitchGameItem(game, viewModel, showAppActions, showLoading, selectedModel, showError)
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 5.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(7.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SwitchRailButton(Icons.Default.Memory, "CPU", Color(0xFFE3A33B)) { onOpenSettings("Emulacao") }
                            SwitchRailButton(Icons.Default.Palette, "Gráficos", Color(0xFF5DAA67)) { onOpenSettings("Graficos") }
                            SwitchRailButton(Icons.Default.SportsEsports, "Controles", Color(0xFF4B91D1)) { onOpenSettings("Controles") }
                            SwitchRailButton(Icons.Default.Folder, "Dados", Color(0xFF54A9A4)) { onOpenSettings("Jogos") }
                            SwitchRailButton(Icons.Default.Settings, "Sistema", Color(0xFF787F8A)) { onOpenSettings("Sistema") }
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(50.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onChangeLayout),
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFD2D5DB)),
                        shadowElevation = 5.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ViewModule,
                                contentDescription = "Usar visual NEO",
                                tint = Color(0xFF30343C),
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }
                }
            }
        }

        @Composable
        private fun SwitchRailButton(
            icon: androidx.compose.ui.graphics.vector.ImageVector,
            label: String,
            color: Color,
            onClick: () -> Unit
        ) {
            Surface(
                shape = CircleShape,
                color = color,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClick)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(21.dp))
                }
            }
        }

        @Composable
        private fun SwitchGameItem(
            gameModel: GameModel,
            viewModel: HomeViewModel,
            showAppActions: MutableState<Boolean>,
            showLoading: MutableState<Boolean>,
            selectedModel: MutableState<GameModel?>,
            showError: MutableState<String>
        ) {
            val context = LocalContext.current
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            var isFocused by remember { mutableStateOf(false) }
            val emphasis = isPressed || isFocused
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.965f else if (isFocused) 1.035f else 1f,
                animationSpec = tween(140),
                label = "switchGameScale"
            )
            val elevation by animateFloatAsState(
                targetValue = if (emphasis) 16f else 8f,
                animationSpec = tween(160),
                label = "switchGameElevation"
            )
            val coverBitmap = remember(gameModel.titleId) {
                try {
                    val iconFile = java.io.File(context.filesDir, "game_icons/${gameModel.titleId}.jpg")
                    if (iconFile.exists()) BitmapFactory.decodeFile(iconFile.absolutePath)
                    else if (gameModel.icon?.isNotEmpty() == true) {
                        val bytes = Base64.getDecoder().decode(gameModel.icon)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else null
                } catch (_: Throwable) { null }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(168.dp)) {
                Surface(
                    modifier = Modifier
                        .size(168.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .shadow(elevation.dp, RoundedCornerShape(10.dp))
                        .border(
                            width = if (emphasis) 3.dp else 1.dp,
                            color = if (emphasis) Color(0xFF24272D) else Color(0xFFD7DAE0),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                thread {
                                    showLoading.value = true
                                    val success = viewModel.mainViewModel?.loadGame(gameModel) ?: 0
                                    if (success == 1) launchOnUiThread { viewModel.mainViewModel?.navigateToGame() }
                                    else {
                                        if (success == -2) showError.value = "Erro ao carregar atualização."
                                        gameModel.close()
                                    }
                                    showLoading.value = false
                                }
                            },
                            onLongClick = {
                                viewModel.mainViewModel?.selected = gameModel
                                selectedModel.value = gameModel
                                showAppActions.value = true
                            }
                        ),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE2E4E8)
                ) {
                    if (coverBitmap != null) Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = gameModel.titleName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("NEO", color = Color(0xFF555A64), fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    gameModel.titleName ?: "Jogo",
                    color = Color(0xFF292C32),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        @Composable
        fun GridGameItem(
            gameModel: GameModel,
            viewModel: HomeViewModel,
            showAppActions: MutableState<Boolean>,
            showLoading: MutableState<Boolean>,
            selectedModel: MutableState<GameModel?>,
            showError: MutableState<String>
        ) {
            val context = LocalContext.current
            val isSelected = selectedModel.value == gameModel
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            var isFocused by remember { mutableStateOf(false) }
            val emphasis = isPressed || isFocused || isSelected
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.965f else if (emphasis) 1.035f else 1f,
                animationSpec = tween(140),
                label = "cartridgeScale"
            )
            val elevation by animateFloatAsState(
                targetValue = if (emphasis) 13f else 2f,
                animationSpec = tween(160),
                label = "cartridgeElevation"
            )

            // Load game cover bitmap (from game_icons file or base64 icon)
            val coverBitmap = remember(gameModel.titleId) {
                try {
                    val iconFile = java.io.File(context.filesDir, "game_icons/${gameModel.titleId}.jpg")
                    if (iconFile.exists()) {
                        BitmapFactory.decodeFile(iconFile.absolutePath)
                    } else if (gameModel.icon?.isNotEmpty() == true) {
                        val pic = Base64.getDecoder().decode(gameModel.icon)
                        BitmapFactory.decodeByteArray(pic, 0, pic.size)
                    } else null
                } catch (_: Throwable) {
                    null
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(214f / 308f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(elevation.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = if (emphasis) 2.dp else 0.dp,
                        color = if (emphasis) Color(0xFF22252B) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
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
                                            showError.value = "Erro ao carregar atualização. Adicione o arquivo de atualização novamente."
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
                val w = maxWidth
                val h = maxHeight

                // Layer 1: Game artwork strictly aligned with the cartridge cutout (18, 83) to (197, 240) in 214x308
                Box(
                    modifier = Modifier
                        .offset(
                            x = w * (18f / 214f),
                            y = h * (83f / 308f)
                        )
                        .size(
                            width = w * (179f / 214f),
                            height = h * (157f / 308f)
                        )
                        .background(Color(0xFF1B1B1D)),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap.asImageBitmap(),
                            contentDescription = gameModel.titleName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (gameModel.type == FileType.Nro) {
                        Image(
                            painter = painterResource(id = R.drawable.icon_nro),
                            contentDescription = "NRO",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Text(
                            text = gameModel.titleName ?: "Jogo",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }

                // Layer 2: Nintendo Switch Cartridge Mockup frame from /imagens (transparent cutout allows artwork to show)
                Image(
                    painter = painterResource(R.drawable.mockup_cartucho_switch),
                    contentDescription = gameModel.titleName,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )

                // The selected/focused cartridge keeps a visible inner highlight.
                if (emphasis) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(2.dp, Color.White.copy(alpha = 0.88f), RoundedCornerShape(8.dp))
                    )
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
