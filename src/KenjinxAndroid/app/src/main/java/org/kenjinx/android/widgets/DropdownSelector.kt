package org.kenjinx.android.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> DropdownSelector(
    label: String,
    selectedValue: T,
    options: List<T>,
    getDisplayText: (T) -> String,
    onOptionSelected: (T) -> Unit,
    description: String? = null
) {
    val expanded = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (description != null) Modifier.padding(horizontal = 8.dp, vertical = 8.dp) else Modifier.height(64.dp).padding(horizontal = 8.dp, vertical = 8.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = label,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box {
            Button(
                onClick = { expanded.value = true },
                modifier = Modifier.height(36.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(getDisplayText(selectedValue))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Expand",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            DropdownMenu(
                expanded = expanded.value,
                onDismissRequest = { expanded.value = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(getDisplayText(option)) },
                        onClick = {
                            onOptionSelected(option)
                            expanded.value = false
                        }
                    )
                }
            }
        }
    }
}
