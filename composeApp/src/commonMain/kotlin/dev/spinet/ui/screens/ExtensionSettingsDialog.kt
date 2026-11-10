package dev.spinet.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.spinet.extension.SettingField
import dev.spinet.extension.SourceItem
import dev.spinet.extensions.ExtensionSettingsUi

@Composable
internal fun ExtensionSettingsDialog(
    title: String,
    settings: ExtensionSettingsUi,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    val values = remember(settings) { mutableStateMapOf<String, String>().apply { putAll(settings.values) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                settings.fields.forEach { field ->
                    val choices = settings.choices[field.key].orEmpty()
                    if (choices.isEmpty()) {
                        SettingTextField(field, values[field.key].orEmpty()) { values[field.key] = it }
                    } else {
                        SettingChoiceList(field, choices, values[field.key].orEmpty()) { values[field.key] = it }
                    }
                }
                Text(
                    "Kept on this device and given only to this source.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(values.toMap()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettingTextField(field: SettingField, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(field.label) },
        placeholder = { if (field.hint.isNotBlank()) Text(field.hint) },
        visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SettingChoiceList(
    field: SettingField,
    choices: List<SourceItem>,
    value: String,
    onChange: (String) -> Unit
) {
    val picked = value.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    fun isPicked(item: SourceItem) = picked.any { it.equals(item.id, ignoreCase = true) }

    Text(field.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    if (field.hint.isNotBlank()) {
        Text(field.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
        items(choices, key = { it.id }) { item ->
            val on = isPicked(item)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val next = if (on) picked.filterNot { it.equals(item.id, ignoreCase = true) } else picked + item.id
                        onChange(next.joinToString(", "))
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.artworkUrl.isNotBlank()) {
                    ItemArt(item.artworkUrl, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (on) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "On",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
