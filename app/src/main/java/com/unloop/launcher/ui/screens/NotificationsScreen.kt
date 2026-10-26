package com.unloop.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unloop.launcher.service.FilteredNotificationItem
import com.unloop.launcher.ui.components.PermissionRationaleDialog

@Composable
fun NotificationsScreen(
    notificationsEnabled: Boolean,
    notifications: List<FilteredNotificationItem>,
    isBatteryOptimizationIgnored: Boolean = true,
    showBatteryOptimizationDialog: Boolean = false,
    onToggleNotifications: (Boolean, () -> Unit) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestBatteryOptimization: () -> Unit = {},
    onDismissBatteryOptimizationDialog: () -> Unit = {},
    onConfirmBatteryOptimizationDialog: () -> Unit = {},
    onCancelPendingPermission: () -> Unit,
    onClearAll: () -> Unit,
    onDismissNotification: (FilteredNotificationItem) -> Unit = {},
    onNotificationClick: (FilteredNotificationItem) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var showPermissionDialog by remember { mutableStateOf(false) }
    var batteryBannerDismissed by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp, bottom = 24.dp)
        ) {
            // Header: Title and Back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                TextButton(onClick = onBack) {
                    Text(
                        text = "Back",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notification management master toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notification Filter",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Quiet distracting alerts and view them inside Unloop",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { targetState ->
                        onToggleNotifications(targetState) {
                            showPermissionDialog = true
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Contextual Battery Optimization Guidance Card (Dismissible)
            if (notificationsEnabled && !isBatteryOptimizationIgnored && !batteryBannerDismissed) {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reliable Background Filtering",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { batteryBannerDismissed = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss tip",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Battery optimization is active. Set battery usage to Unrestricted to prevent Android or device killers from pausing background filtering.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onRequestBatteryOptimization) {
                                Text(
                                    text = "Configure Battery Settings →",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // Filtered alerts section header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FILTERED ALERTS (${notifications.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                if (notifications.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text(
                            text = "Clear All",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (notificationsEnabled) "No filtered notifications yet" else "Notification Filter is off",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (notificationsEnabled) {
                                "Distracting alerts will be filtered from your status bar and stored here quietly."
                            } else {
                                "Turn on the filter above to quiet notifications and view them inside Unloop."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = notifications,
                        key = { it.key.ifBlank { "${it.packageName}_${it.timestamp}" } }
                    ) { item ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNotificationClick(item) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.appName,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = item.timestampFormatted,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        IconButton(
                                            onClick = { onDismissNotification(item) },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss notification",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                if (item.title.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (item.text.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Permission Rationale Dialog for Notification Listener Access
    if (showPermissionDialog) {
        PermissionRationaleDialog(
            title = "Notification Access Required",
            message = "Unloop requires Notification Access to filter distracting alerts from your status bar and store them privately for mindful review.\n\nYour notifications are stored locally on your device and never leave Unloop.",
            confirmButtonText = "Open Settings",
            dismissButtonText = "Cancel",
            onConfirm = {
                showPermissionDialog = false
                onOpenNotificationSettings()
            },
            onDismiss = {
                showPermissionDialog = false
                onCancelPendingPermission()
            }
        )
    }

    // Battery Optimization Guidance Dialog
    if (showBatteryOptimizationDialog) {
        PermissionRationaleDialog(
            title = "Unrestricted Battery Usage",
            message = "To filter notifications reliably without Android putting Unloop to sleep in the background, set battery usage to Unrestricted.\n\nNote: Even with unrestricted battery settings, some device manufacturers (such as Samsung, Xiaomi, or OnePlus) impose custom background restrictions that Android does not allow apps to completely bypass.",
            confirmButtonText = "Allow Unrestricted",
            dismissButtonText = "Keep Optimized",
            onConfirm = onConfirmBatteryOptimizationDialog,
            onDismiss = onDismissBatteryOptimizationDialog
        )
    }
}
