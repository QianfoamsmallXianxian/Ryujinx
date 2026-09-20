package org.kenjinx.android.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ViewModule
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
    var cacheExpanded by remember { mutableStateOf(false) }
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

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .heightIn(max = 660.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header: Game Image, Title, ID, Size, Close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = game.titleName ?: "Jogo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val details = buildList<String> {
                            game.titleId?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                            game.version?.takeIf { it.isNotBlank() }?.let { add(it) }
                            if (game.fileSize > 0.0) add(String.format(Locale.getDefault(), "%.2f GB", game.fileSize))
                        }.joinToString(" • ")
                        if (details.isNotBlank()) {
                            Text(
                                text = details,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // Scrollable Actions List
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        ActionCard(
                            icon = Icons.Default.PlayArrow,
                            title = "Jogar",
                            subtitle = "Iniciar emulação deste título",
                            isPrimary = true,
                            onClick = onPlay
                        )
                    }
                    item {
                        ActionCard(
                            icon = Icons.Default.Settings,
                            title = "Configurar",
                            subtitle = "Perfil individual ou global de emulação",
                            onClick = onConfigure
                        )
                    }
                    item {
                        ActionCard(
                            icon = Icons.Default.SystemUpdate,
                            title = "Atualizações",
                            subtitle = "Gerenciar patches e versões",
                            onClick = onUpdates
                        )
                    }
                    item {
                        ActionCard(
                            icon = Icons.Default.Extension,
                            title = "DLCs",
                            subtitle = "Gerenciar conteúdos adicionais instalados",
                            onClick = onDlc
                        )
                    }
                    item {
                        ActionCard(
                            icon = Icons.Default.ViewModule,
                            title = "Mods",
                            subtitle = "Importar e ativar modificações",
                            onClick = onMods
                        )
                    }
                    item {
                        ActionCard(
                            icon = Icons.Default.Code,
                            title = "Cheats",
                            subtitle = "Códigos de trapaça para este jogo",
                            onClick = onCheats
                        )
                    }
                    item {
                        ActionCard(
                            icon = Icons.Default.Save,
                            title = "Saves",
                            subtitle = "Exportar ou importar progresso salvo",
                            onClick = onSaves
                        )
                    }
                    item {
                        ActionCard(
                            icon = Icons.Default.Contactless,
                            title = "Amiibo",
                            subtitle = "Configurar slots de Amiibo",
                            onClick = onAmiibo
                        )
                    }
                    item {
                        ActionCard(
                            icon = Icons.Default.AddBox,
                            title = "Criar atalho",
                            subtitle = "Adicionar ícone à tela inicial do Android",
                            onClick = onCreateShortcut
                        )
                    }
                    item {
                        Column {
                            ActionCard(
                                icon = Icons.Default.DeleteSweep,
                                title = "Cache",
                                subtitle = "Limpar PPTC, shaders e dados temporários",
                                trailingIcon = if (cacheExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                onClick = { cacheExpanded = !cacheExpanded }
                            )
                            if (cacheExpanded) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, start = 12.dp, end = 12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        TextButton(
                                            onClick = onClearPptc,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Limpar cache PPTC", modifier = Modifier.weight(1f))
                                        }
                                        TextButton(
                                            onClick = onPurgeShader,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Limpar cache de shaders", modifier = Modifier.weight(1f))
                                        }
                                        TextButton(
                                            onClick = onDeleteCache,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Excluir todo o cache", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isPrimary: Boolean = false,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
