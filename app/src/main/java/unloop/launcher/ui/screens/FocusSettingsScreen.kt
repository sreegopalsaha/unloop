package unloop.launcher.ui.screens

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import unloop.launcher.data.AppItem
import unloop.launcher.ui.components.HeaderActionType
import unloop.launcher.ui.components.ScreenHeader
import unloop.launcher.ui.components.AppSearchBar
import unloop.launcher.util.AppSearchEngine

@Composable
fun FocusSettingsScreen(
    focusModeEnabled: Boolean,
    focusApps: Set<String>,
    allApps: List<AppItem>,
    onToggleFocusMode: (Boolean) -> Unit,
    onToggleAppRestriction: (AppItem) -> Unit,
    onBack: () -> Unit,
    onSetFocusApps: ((Set<String>) -> Unit)? = null,
    focusDurationMinutes: Int = 10,
    onChangeDuration: (Int) -> Unit = {}
) {
    BackHandler(onBack = onBack)

    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) {
            allApps
        } else {
            AppSearchEngine.filterAndRank(allApps, searchQuery)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 16.dp)
        ) {
            ScreenHeader(
                title = "Focus Mode",
                onActionClick = onBack,
                actionType = HeaderActionType.BACK
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Master switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Focus Mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Prompts a mindful pause before opening distracting apps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECT DISTRACTING APPLICATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                if (filteredApps.isNotEmpty()) {
                    val allFilteredSelected = filteredApps.all { focusApps.contains(it.packageName) }
                    Text(
                        text = if (allFilteredSelected) "Deselect all" else "Select all",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                val filteredPackageNames = filteredApps.map { it.packageName }.toSet()
                                val newFocusApps = if (allFilteredSelected) {
                                    focusApps - filteredPackageNames
                                } else {
                                    focusApps + filteredPackageNames
                                }
                                if (onSetFocusApps != null) {
                                    onSetFocusApps(newFocusApps)
                                } else {
                                    val toToggle = if (allFilteredSelected) {
                                        filteredApps.filter { focusApps.contains(it.packageName) }
                                    } else {
                                        filteredApps.filterNot { focusApps.contains(it.packageName) }
                                    }
                                    toToggle.forEach { onToggleAppRestriction(it) }
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reusable AppSearchBar
            AppSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholderText = "Search apps...",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No applications found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isChecked = focusApps.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleAppRestriction(app) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )

                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleAppRestriction(app) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    checkmarkColor = MaterialTheme.colorScheme.background
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
