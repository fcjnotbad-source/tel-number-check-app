package com.fcj.telnumbercheck.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class HomeTab(val label: String, val subtitle: String) {
    CALLS("来电", "自动检测与最近来电"),
    LOOKUP("查号", "手动查询号码信息"),
    SETTINGS("设置", "权限、隐私与应用信息"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeTabsScaffold(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("来电快检", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(selected.subtitle, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("home_tab_${tab.name.lowercase()}"),
                        selected = selected == tab,
                        onClick = {
                            focus.clearFocus()
                            keyboard?.hide()
                            onSelect(tab)
                        },
                        icon = {
                            val icon = when (tab) {
                                HomeTab.CALLS -> if (selected == tab) Icons.Rounded.Call else Icons.Outlined.Call
                                HomeTab.LOOKUP -> if (selected == tab) Icons.AutoMirrored.Rounded.ManageSearch else Icons.AutoMirrored.Outlined.ManageSearch
                                HomeTab.SETTINGS -> if (selected == tab) Icons.Rounded.Tune else Icons.Outlined.Tune
                            }
                            Icon(icon, contentDescription = null)
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
        content = content,
    )
}
