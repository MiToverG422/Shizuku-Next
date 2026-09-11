package moe.shizuku.manager.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialNavigationRail(destinations: List<NavigationDestination>, selected: Int, onSelect: (Int) -> Unit) {
    val prefs = remember { ShizukuSettings.getPreferences() }
    val state = rememberWideNavigationRailState(
        initialValue = if (prefs.getBoolean("navigation_rail_expanded", false))
            WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed)
    val expanded = state.targetValue == WideNavigationRailValue.Expanded
    val scope = rememberCoroutineScope()
    LaunchedEffect(expanded) { prefs.edit().putBoolean("navigation_rail_expanded", expanded).apply() }
    WideNavigationRail(
        state = state,
        modifier = Modifier.fillMaxHeight(),
        colors = WideNavigationRailDefaults.colors().copy(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface),
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
        contentPadding = PaddingValues(vertical = 20.dp),
        header = {
            IconButton(modifier = Modifier.padding(start = 24.dp), onClick = {
                scope.launch { if (expanded) state.collapse() else state.expand() }
            }) {
                Icon(painterResource(if (expanded) R.drawable.ic_arrow_back_24 else R.drawable.ic_menu_24),
                    stringResource(if (expanded) R.string.ui_navigation_collapse else R.string.ui_navigation_expand))
            }
        },
    ) {
        destinations.forEachIndexed { index, destination ->
            WideNavigationRailItem(railExpanded = expanded, selected = index == selected,
                onClick = { if (index != selected) onSelect(index) },
                icon = { Icon(painterResource(if (index == selected) destination.selectedIcon else destination.icon), null) },
                label = { Text(stringResource(destination.label)) })
        }
    }
}
