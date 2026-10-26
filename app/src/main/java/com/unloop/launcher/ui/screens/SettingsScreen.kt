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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    isDefaultLauncher: Boolean,
    shortVideoBlockerEnabled: Boolean,
    focusModeEnabled: Boolean,
    themeMode: String,
    onSetDefaultLauncher: () -> Unit,
    onToggleShortVideoBlocker: (Boolean) -> Unit,
    onToggleFocusMode: (Boolean) -> Unit,
    onOpenFocusSettings: () -> Unit,
    onOpenHiddenApps: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenScreenTime: () -> Unit,
    onChangeTheme: (String) -> Unit,
    onBackToHome: () -> Unit
) {
    BackHandler(onBack = onBackToHome)

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
                title = "Set as default launcher",
                subtitle = if (isDefaultLauncher) "Currently default launcher" else "Tap to set Unloop as your default home app",
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
                title = "Device dark mode",
                subtitle = "Appearance: $themeMode",
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
                title = "Hidden applications",
                subtitle = "Manage and unhide hidden apps",
                onClick = onOpenHiddenApps
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --- SECTION: DIGITAL WELLBEING ---
            SectionHeader(title = "DIGITAL WELLBEING")

            SettingItem(
                title = "Screen time statistics",
                subtitle = "Open system Digital Wellbeing to inspect usage stats",
                onClick = onOpenScreenTime
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Focus Mode with master toggle + config option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).clickable(onClick = onOpenFocusSettings)) {
                    Text(
                        text = "Focus mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Mindful prompt & timers for distracting apps (tap to configure)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Switch(
                    checked = focusModeEnabled,
                    onCheckedChange = onToggleFocusMode,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Short-Video Blocker (ONE global toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Short-Video Blocker",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Blocks YouTube Shorts, Instagram Reels, Facebook Reels, and Snapchat Spotlight while preserving standard feeds & messages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Switch(
                    checked = shortVideoBlockerEnabled,
                    onCheckedChange = onToggleShortVideoBlocker,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- SECTION: NOTIFICATIONS ---
            SectionHeader(title = "NOTIFICATIONS")

            SettingItem(
                title = "Notification Manager",
                subtitle = "Filter and view status bar alerts inside Unloop",
                onClick = onOpenNotifications
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --- SECTION: ABOUT ---
            SectionHeader(title = "ABOUT")

            SettingItem(
                title = "Unloop Launcher",
                subtitle = "Version 1.0 • Built with native Kotlin & Jetpack Compose",
                onClick = {}
            )
        }
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
