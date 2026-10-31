package unloop.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import unloop.launcher.ui.components.HeaderActionType
import unloop.launcher.ui.components.ScreenHeader
import unloop.launcher.data.AppItem
import unloop.launcher.ui.components.UsageAccessRationaleDialog
import unloop.launcher.util.AppCleanupManager
import unloop.launcher.util.AppIconCache

@Composable
fun AppCleanupScreen(
    installedApps: List<AppItem>,
    isUsageAccessGranted: Boolean,
    isUsageBannerDismissed: Boolean,
    showSystemApps: Boolean = false,
    onToggleShowSystemApps: (Boolean) -> Unit = {},
    onOpenUsageSettings: () -> Unit,
    onDismissUsageBanner: () -> Unit,
    onUninstallApp: (AppItem) -> Unit,
    onOpenApp: (AppItem) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }

    val filteredApps = remember(installedApps, showSystemApps) {
        if (showSystemApps) installedApps else installedApps.filterNot { it.isSystemApp }
    }

    val totalCount = filteredApps.size
    val userCount = filteredApps.count { !it.isSystemApp }
    val systemCount = filteredApps.count { it.isSystemApp }

    // Check if we have cached data for the current state
    val initialCachedData = remember(installedApps, showSystemApps, isUsageAccessGranted) {
        AppCleanupManager.getCachedCleanupData(installedApps, showSystemApps, isUsageAccessGranted)
    }

    var cleanupData by remember(installedApps, showSystemApps, isUsageAccessGranted) {
        mutableStateOf(initialCachedData)
    }

    var isLoading by remember(installedApps, showSystemApps, isUsageAccessGranted) {
        mutableStateOf(initialCachedData == null)
    }

    LaunchedEffect(installedApps, showSystemApps, isUsageAccessGranted) {
        val cached = AppCleanupManager.getCachedCleanupData(installedApps, showSystemApps, isUsageAccessGranted)
        if (cached != null) {
            cleanupData = cached
            isLoading = false
            AppCleanupManager.prewarmOtherToggleState(context, installedApps, showSystemApps, isUsageAccessGranted)
        } else {
            isLoading = true
            val data = AppCleanupManager.loadCleanupData(
                context = context,
                installedApps = installedApps,
                showSystemApps = showSystemApps,
                isUsageAccessGranted = isUsageAccessGranted
            )
            cleanupData = data
            isLoading = false
            AppCleanupManager.prewarmOtherToggleState(context, installedApps, showSystemApps, isUsageAccessGranted)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // Header: Title and Done button (consistent with SettingsScreen)
            item(key = "header") {
                ScreenHeader(
                    title = "App Cleanup",
                    onActionClick = onBack,
                    actionType = HeaderActionType.DONE
                )
            }

            // Total App Counts Summary
            item(key = "counts_summary") {
                Column {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Total installed apps: $totalCount",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "User-installed apps: $userCount  ·  System apps: $systemCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }

            // "Show system apps" Toggle Row (reusing Settings screen design)
            item(key = "system_apps_toggle") {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleShowSystemApps(!showSystemApps) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show system apps",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Include pre-installed and system applications",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Switch(
                            checked = showSystemApps,
                            onCheckedChange = onToggleShowSystemApps,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.background,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }

            // Usage access permission guidance banner
            if (!isUsageAccessGranted && !isUsageBannerDismissed) {
                item(key = "usage_banner") {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
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
                                        text = "Usage Access Needed",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = onDismissUsageBanner,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Grant usage access so Unloop can identify apps you haven't used in 10 or more days. Your data stays completely on-device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { showPermissionDialog = true }) {
                                        Text(
                                            text = "Open Settings →",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item(key = "loading_indicator") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp, bottom = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                }
            } else {
                val categoryGroups = cleanupData?.categoryGroups ?: emptyList()
                val usageTimestamps = cleanupData?.usageTimestamps ?: emptyMap()

                categoryGroups.forEachIndexed { groupIndex, group ->
                    item(key = "header_${group.key}") {
                        Column {
                            Spacer(modifier = Modifier.height(if (groupIndex == 0) 24.dp else 28.dp))
                            val countText = if (group.isUnusedCategory && !isUsageAccessGranted) {
                                "–"
                            } else {
                                group.apps.size.toString()
                            }
                            SectionHeader(title = "${group.title} · $countText")
                        }
                    }

                    if (group.isUnusedCategory && !isUsageAccessGranted) {
                        item(key = "unused_no_permission") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Usage access is required to identify apps that have not been used for 10 or more days.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = { showPermissionDialog = true }
                                ) {
                                    Text(
                                        text = "Enable Usage Access →",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    } else if (group.apps.isEmpty()) {
                        item(key = "empty_${group.key}") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (group.isUnusedCategory) {
                                        "No unused apps found. All apps have been used in the past 10 days."
                                    } else {
                                        "No apps in this category."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = group.apps,
                            key = { _, app -> "${group.key}_${app.packageName}" },
                            contentType = { _, _ -> "app_row" }
                        ) { appIndex, app ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val lastUsedText = if (group.isUnusedCategory) {
                                    val ts = usageTimestamps[app.packageName]
                                    AppCleanupManager.formatLastUsed(ts) ?: "Usage data unavailable"
                                } else {
                                    val ts = usageTimestamps[app.packageName]
                                    AppCleanupManager.formatLastUsed(ts)
                                }

                                AppCleanupItemRow(
                                    app = app,
                                    lastUsedText = lastUsedText,
                                    onAppClick = { onOpenApp(app) },
                                    onUninstall = { onUninstallApp(app) }
                                )

                                if (appIndex < group.apps.size - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPermissionDialog) {
        UsageAccessRationaleDialog(
            onConfirm = {
                showPermissionDialog = false
                onOpenUsageSettings()
            },
            onDismiss = {
                showPermissionDialog = false
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
private fun AppCleanupItemRow(
    app: AppItem,
    lastUsedText: String?,
    onAppClick: () -> Unit,
    onUninstall: () -> Unit
) {
    val subtitle = remember(lastUsedText, app.isSystemApp) {
        val parts = mutableListOf<String>()
        if (!lastUsedText.isNullOrBlank()) {
            parts.add(lastUsedText)
        }
        if (app.isSystemApp) {
            parts.add("System app")
        }
        if (parts.isNotEmpty()) parts.joinToString(" · ") else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAppClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconView(
            packageName = app.packageName,
            label = app.label,
            modifier = Modifier.size(38.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(
            onClick = onUninstall,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Uninstall ${app.label}",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AppIconView(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cachedBitmap = remember(packageName) { AppIconCache.get(packageName) }
    var bitmap by remember(packageName) { mutableStateOf(cachedBitmap) }

    LaunchedEffect(packageName) {
        if (bitmap == null) {
            bitmap = AppIconCache.loadIcon(context, packageName)
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = label,
            modifier = modifier.clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
