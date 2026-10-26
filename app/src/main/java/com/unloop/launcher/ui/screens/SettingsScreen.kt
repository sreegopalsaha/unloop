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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unloop.launcher.ui.components.PermissionRationaleDialog
import com.unloop.launcher.util.AppVersionUtils

@Composable
fun SettingsScreen(
    isDefaultLauncher: Boolean,
    shortVideoBlockerEnabled: Boolean,
    focusModeEnabled: Boolean,
    notificationManagerEnabled: Boolean,
    themeMode: String,
    onSetDefaultLauncher: () -> Unit,
    onToggleShortVideoBlocker: (Boolean, () -> Unit) -> Unit,
    onToggleFocusMode: (Boolean) -> Unit,
    onToggleNotificationManager: (Boolean, () -> Unit) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onCancelPendingPermission: () -> Unit,
    onOpenFocusSettings: () -> Unit,
    onOpenHiddenApps: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenScreenTime: () -> Unit,
    onChangeTheme: (String) -> Unit,
    onOpenAbout: () -> Unit = {},
    onBackToHome: () -> Unit
) {
    BackHandler(onBack = onBackToHome)

    val context = LocalContext.current
    val appVersion = remember(context) { AppVersionUtils.getAppVersion(context) }

    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 40.dp, bottom = 48.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                TextButton(onClick = onBackToHome) {
                    Text(
                        text = "Done",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // --- SECTION: LAUNCHER ---
            SectionHeader(title = "LAUNCHER")

            SettingItem(
                title = "Default launcher",
                subtitle = "Set Unloop as your default launcher",
                trailing = {
                    Text(
                        text = if (isDefaultLauncher) "Default" else "Configure",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isDefaultLauncher) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                },
                onClick = onSetDefaultLauncher
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            SettingItem(
                title = "Appearance",
                subtitle = "Choose light, dark, or system theme",
                onClick = {
                    val nextMode = when (themeMode) {
                        "DARK" -> "LIGHT"
                        "LIGHT" -> "SYSTEM"
                        else -> "DARK"
                    }
                    onChangeTheme(nextMode)
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            SettingItem(
                title = "Hidden apps",
                subtitle = "Manage and open hidden apps",
                onClick = onOpenHiddenApps
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --- SECTION: DIGITAL WELLBEING ---
            SectionHeader(title = "DIGITAL WELLBEING")

            // 1. Focus Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFocusSettings)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Focus mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Choose apps to pause during focus mode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Switch(
                        checked = focusModeEnabled,
                        onCheckedChange = onToggleFocusMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.background,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // 2. Short-Video Blocker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Short-video blocker",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Block Reels, Shorts, and Spotlight",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Switch(
                    checked = shortVideoBlockerEnabled,
                    onCheckedChange = { targetState ->
                        onToggleShortVideoBlocker(targetState) {
                            showAccessibilityDialog = true
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // 3. Notification Manager
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenNotifications)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notification manager",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Manage muted apps and view notifications",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Switch(
                        checked = notificationManagerEnabled,
                        onCheckedChange = { targetState ->
                            onToggleNotificationManager(targetState) {
                                showNotificationDialog = true
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.background,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // 4. Screen time statistics
            SettingItem(
                title = "Screen time",
                subtitle = "View app usage and daily screen time",
                trailing = {
                    Text(
                        text = "Open",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = onOpenScreenTime
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --- SECTION: ABOUT ---
            SectionHeader(title = "ABOUT")

            SettingItem(
                title = "About Unloop",
                subtitle = "Version $appVersion",
                onClick = onOpenAbout
            )
        }
    }

    // Accessibility Permission Rationale Dialog for Short-Video Blocker
    if (showAccessibilityDialog) {
        PermissionRationaleDialog(
            title = "Accessibility Permission Required",
            message = "Short-Video Blocker requires Accessibility permission to detect and exit short-form video reels and shorts (YouTube Shorts, Instagram Reels, Facebook Reels, and Snapchat Spotlight).\n\nUnloop operates entirely on-device and never records keystrokes, personal messages, or private data.",
            confirmButtonText = "Open Settings",
            dismissButtonText = "Cancel",
            onConfirm = {
                showAccessibilityDialog = false
                onOpenAccessibilitySettings()
            },
            onDismiss = {
                showAccessibilityDialog = false
                onCancelPendingPermission()
            }
        )
    }

    // Notification Access Rationale Dialog for Notification Manager
    if (showNotificationDialog) {
        PermissionRationaleDialog(
            title = "Notification Access Required",
            message = "Notification Manager requires Notification Access to filter distracting notifications from your status bar and store them privately for mindful review.\n\nYour notifications are stored locally on your device and never leave Unloop.",
            confirmButtonText = "Open Settings",
            dismissButtonText = "Cancel",
            onConfirm = {
                showNotificationDialog = false
                onOpenNotificationSettings()
            },
            onDismiss = {
                showNotificationDialog = false
                onCancelPendingPermission()
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            trailing()
        }
    }
}

