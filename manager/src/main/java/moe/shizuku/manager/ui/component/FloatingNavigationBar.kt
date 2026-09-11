package moe.shizuku.manager.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class NavigationDestination(val label: Int, val icon: Int, val selectedIcon: Int = icon)

@Composable
fun FloatingNavigationBar(
    destinations: List<NavigationDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = if (navigationInset > 0.dp) navigationInset + 8.dp else 16.dp
    Box(modifier.fillMaxWidth().windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    ).padding(bottom = bottom), contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                Modifier.height(UiMetrics.FloatingBarHeight).padding(horizontal = 6.dp, vertical = 5.dp).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                destinations.forEachIndexed { index, destination ->
                    val selected = index == selectedIndex
                    val background by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        tween(UiMetrics.NavigationDuration, easing = FastOutSlowInEasing), label = "navigationBackground",
                    )
                    val foreground by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        tween(UiMetrics.NavigationDuration, easing = FastOutSlowInEasing), label = "navigationForeground",
                    )
                    val label = stringResource(destination.label)
                    Row(
                        Modifier.fillMaxHeight().defaultMinSize(minWidth = 48.dp)
                            .clip(CircleShape).background(background)
                            .selectable(selected, role = Role.Tab, onClick = { if (!selected) onSelect(index) })
                            .padding(horizontal = if (selected) 14.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(if (selected) destination.selectedIcon else destination.icon),
                            contentDescription = label, tint = foreground, modifier = Modifier.size(24.dp))
                        AnimatedVisibility(
                            visible = selected,
                            enter = expandHorizontally(tween(250, easing = FastOutSlowInEasing), Alignment.Start) + fadeIn(tween(250)),
                            exit = shrinkHorizontally(tween(250, easing = FastOutSlowInEasing), Alignment.Start) + fadeOut(tween(250)),
                        ) {
                            Text(label, Modifier.padding(start = 8.dp), color = foreground,
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
                        }
                    }
                }
            }
        }
    }
}
