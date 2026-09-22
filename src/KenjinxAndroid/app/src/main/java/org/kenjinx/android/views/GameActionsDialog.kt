package org.kenjinx.android.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.kenjinx.android.viewmodels.GameModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameActionsDialog(
    game: GameModel,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onConfigure: () -> Unit,
    onUpdates: () -> Unit,
    onDlc: () -> Unit,
    onMods: () -> Unit,
    onCheats: () -> Unit,
    onSaves: () -> Unit,
    onAmiibo: () -> Unit,
    onCreateShortcut: () -> Unit,
    onClearPptc: () -> Unit,
    onPurgeShader: () -> Unit,
    onDeleteCache: () -> Unit
) {
    var showDeleteCacheConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val bitmap = remember(game.titleId) {
        try {
            val iconFile = File(context.filesDir, "game_icons/${game.titleId}.jpg")
            if (iconFile.exists()) {
                android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath)
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    val actions = listOf(
        DialogAction(Icons.Default.PlayArrow, "Jogar", "Iniciar emulação", true, false, onPlay),
        DialogAction(Icons.Default.Settings, "Configurar", "Perfil deste jogo", false, false, onConfigure),
        DialogAction(Icons.Default.SystemUpdate, "Atualizações", "Patches e versões", false, false, onUpdates),
        DialogAction(Icons.Default.Extension, "DLCs", "Conteúdos adicionais", false, false, onDlc),
        DialogAction(Icons.Default.ViewModule, "Mods", "Gerenciar modificações", false, false, onMods),
        DialogAction(Icons.Default.Code, "Cheats", "Códigos de trapaça", false, false, onCheats),
        DialogAction(Icons.Default.Save, "Saves", "Importar ou exportar", false, false, onSaves),
        DialogAction(Icons.Default.Contactless, "Amiibo", "Configurar slots", false, false, onAmiibo),
        DialogAction(Icons.Default.AddBox, "Criar atalho", "Adicionar à tela inicial", false, false, onCreateShortcut),
        DialogAction(Icons.Default.Memory, "Limpar cache PPTC", "Descarta compilações PPTC", false, false, onClearPptc),
        DialogAction(Icons.Default.DeleteSweep, "Limpar shaders", "Descarta shaders compilados", false, false, onPurgeShader),
        DialogAction(Icons.Default.Delete, "Excluir todo o cache", "Remove PPTC, shaders e arquivos", false, true, { showDeleteCacheConfirm = true })
    )

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier
                .width(620.dp)
                .height(440.dp)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header (Switch identity matching DlcViews)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(54.dp)
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
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = game.titleName ?: "Gerenciar Jogo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val titleIdText = game.titleId?.uppercase(Locale.ROOT) ?: ""
                        Text(
                            text = if (titleIdText.isNotBlank()) "$titleIdText • Ações rápidas" else "Ações rápidas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // Landscape 4:3 action area, arranged in two columns for handhelds.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(actions) { action ->
                        ActionCard(
                            icon = action.icon,
                            title = action.title,
                            subtitle = action.subtitle,
                            isPrimary = action.isPrimary,
                            isError = action.isError,
                            onClick = action.onClick
                        )
                    }
                }
            }
        }
    }

    // Standardized Delete Confirmation Dialog matching DlcViews
    if (showDeleteCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteCacheConfirm = false },
            title = { Text("Excluir cache") },
            text = {
                Text("Deseja excluir todo o cache de \"${game.titleName ?: "deste jogo"}\"? Os dados de PPTC, shaders compilados e arquivos temporários serão apagados.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCache()
                        showDeleteCacheConfirm = false
                    }
                ) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCacheConfirm = false }) {
                    Text("Cancelar", maxLines = 1, softWrap = false)
                }
            }
        )
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isPrimary: Boolean = false,
    isError: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = when {
            isPrimary -> MaterialTheme.colorScheme.primaryContainer
            isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            1.dp,
            when {
                isPrimary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    isPrimary -> MaterialTheme.colorScheme.onPrimaryContainer
                    isError -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        isPrimary -> MaterialTheme.colorScheme.onPrimaryContainer
                        isError -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isPrimary -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class DialogAction(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val isPrimary: Boolean,
    val isError: Boolean = false,
    val onClick: () -> Unit
)
