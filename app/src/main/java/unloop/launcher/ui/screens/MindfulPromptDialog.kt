package unloop.launcher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import unloop.launcher.data.AppItem

@Composable
fun MindfulPromptDialog(
    app: AppItem,
    onDismiss: () -> Unit,
    onConfirmLaunch: (durationMinutes: Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Mindful Pause",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "You are opening ${app.label}. Take a breath—how long do you intend to use it?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                DurationOption(label = "5 minutes") { onConfirmLaunch(5) }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                DurationOption(label = "10 minutes") { onConfirmLaunch(10) }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                DurationOption(label = "15 minutes") { onConfirmLaunch(15) }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                DurationOption(label = "Open without timer") { onConfirmLaunch(0) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Step Back", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun DurationOption(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
