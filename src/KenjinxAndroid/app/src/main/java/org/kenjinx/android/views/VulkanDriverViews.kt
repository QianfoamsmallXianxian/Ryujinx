package org.kenjinx.android.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kenjinx.android.MainActivity
import org.kenjinx.android.viewmodels.VulkanDriverViewModel

class VulkanDriverViews {
    companion object {
        @Composable
        fun Main(activity: MainActivity, openDialog: MutableState<Boolean>) {
            val driverViewModel = remember(activity) { VulkanDriverViewModel(activity) }
            val refresh = remember { mutableStateOf(false) }
            var drivers by remember { mutableStateOf(driverViewModel.getAvailableDrivers().toList()) }
            var selectedPath by remember { mutableStateOf(driverViewModel.selected) }
            var showDownloaderDialog by remember { mutableStateOf(false) }

            fun selectDriver(path: String) {
                selectedPath = path
                driverViewModel.selected = path
                // A escolha feita no card deve sobreviver ao fechamento e
                // reabertura da tela, sem depender do botão inferior Salvar.
                driverViewModel.saveSelected()
            }

            LaunchedEffect(refresh.value) {
                if (refresh.value) {
                    drivers = driverViewModel.getAvailableDrivers().toList()
                    refresh.value = false
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Drivers Vulkan",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Escolha o driver usado globalmente pelo emulador.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().height(248.dp),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        DriverCard(
                            title = "Driver do sistema",
                            version = "Android / fabricante do dispositivo",
                            description = "Usa o driver Vulkan instalado no sistema.",
                            sourceTag = "Sistema",
                            selected = selectedPath.isEmpty(),
                            onClick = { selectDriver("") }
                        )
                    }
                    items(drivers, key = { it.driverPath }) { driver ->
                        DriverCard(
                            title = driver.name.ifBlank { driver.libraryName },
                            version = listOf(driver.vendor, driver.driverVersion)
                                .filter { it.isNotBlank() }
                                .joinToString(" • ")
                                .ifBlank { driver.packageVersion },
                            description = driver.description.ifBlank { driver.libraryName },
                            sourceTag = driver.sourceTag,
                            selected = selectedPath == driver.driverPath,
                            onClick = { selectDriver(driver.driverPath) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { driverViewModel.add(refresh) }) {
                        Text("Adicionar driver")
                    }
                    Button(
                        onClick = { showDownloaderDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Baixar drivers")
                    }
                    OutlinedButton(
                        enabled = selectedPath.isNotEmpty(),
                        onClick = {
                            driverViewModel.selected = selectedPath
                            driverViewModel.removeSelected()
                            selectedPath = ""
                            drivers = driverViewModel.getAvailableDrivers().toList()
                        }
                    ) {
                        Text("Excluir selecionado")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    OutlinedButton(onClick = { openDialog.value = false }) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            driverViewModel.selected = selectedPath
                            driverViewModel.saveSelected()
                            openDialog.value = false
                        }
                    ) {
                        Text("Salvar")
                    }
                }
            }

            if (showDownloaderDialog) {
                DriverDownloaderDialog(
                    activity = activity,
                    onDismiss = { showDownloaderDialog = false },
                    onDriverInstalled = {
                        refresh.value = true
                    }
                )
            }
        }

        @Composable
        private fun DriverCard(
            title: String,
            version: String,
            description: String,
            sourceTag: String = "",
            selected: Boolean,
            onClick: () -> Unit
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(16.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                tonalElevation = if (selected) 3.dp else 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (sourceTag.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = sourceTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (selected) {
                            Text(
                                text = "ATIVO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = version,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (selected) {
                            Text(
                                text = "Driver selecionado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        OutlinedButton(
                            onClick = onClick,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(if (selected) "Selecionado" else "Usar este")
                        }
                    }
                }
            }
        }
    }
}
