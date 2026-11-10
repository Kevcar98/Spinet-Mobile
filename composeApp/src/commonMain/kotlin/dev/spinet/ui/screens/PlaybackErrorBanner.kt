package dev.spinet.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun PlaybackErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }
}
