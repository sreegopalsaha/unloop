package unloop.launcher.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

@Composable
fun PermissionRationaleDialog(
    title: String,
    message: String,
    confirmButtonText: String = "Open Settings",
    dismissButtonText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmButtonText,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissButtonText,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    )
}

@Composable
fun NotificationAccessRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    PermissionRationaleDialog(
        title = "Notification Access Required",
        message = "Unloop needs notification access to filter distracting alerts. Your notifications are stored securely on your device and are never uploaded.\n\nWhat to do next\n\nTap Open Settings, find Unloop in the list, and turn on notification access.",
        confirmButtonText = "Open Settings",
        dismissButtonText = "Cancel",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun UsageAccessRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    PermissionRationaleDialog(
        title = "Usage Access Required",
        message = "Unloop needs usage access to identify apps you haven't used in 10 or more days. Your usage data is processed securely on your device and is never uploaded.\n\nWhat to do next\n\nTap Open Settings, find Unloop in the list, and turn on usage access.",
        confirmButtonText = "Open Settings",
        dismissButtonText = "Cancel",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun AccessibilityRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    PermissionRationaleDialog(
        title = "Accessibility Permission Required",
        message = "Unloop needs accessibility access to detect and block distracting short videos. Your activity stays securely on your device and is never recorded or uploaded.\n\nWhat to do next\n\nTap Open Settings, find Unloop in the list, and turn on accessibility.",
        confirmButtonText = "Open Settings",
        dismissButtonText = "Cancel",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun BatteryOptimizationRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    PermissionRationaleDialog(
        title = "Unrestricted Battery Usage",
        message = "Unloop needs unrestricted battery usage to filter notifications reliably without Android putting the app to sleep in the background.\n\nWhat to do next\n\nTap Allow Unrestricted, then choose Unrestricted for Unloop.\n\nNote: Some device manufacturers (such as Samsung, Xiaomi, or OnePlus) impose custom background restrictions that Android does not allow apps to completely bypass.",
        confirmButtonText = "Allow Unrestricted",
        dismissButtonText = "Keep Optimized",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
