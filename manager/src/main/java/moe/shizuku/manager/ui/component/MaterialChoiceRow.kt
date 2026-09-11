package moe.shizuku.manager.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import moe.shizuku.manager.R

/** Touch-anchored, expressive selection menu instead of the old radio dialog. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialChoiceRow(
    title: String,
    icon: Int,
    index: Int,
    count: Int,
    options: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(IntOffset.Zero) }
    val haptic = LocalHapticFeedback.current
    Box(Modifier.pointerInput(Unit) {
        awaitEachGesture { anchor = awaitFirstDown(requireUnconsumed = false).position.round() }
    }) {
        MaterialRow(title = title, icon = icon, index = index, count = count,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                expanded = true
            },
            trailing = {
                Text(options[selected], Modifier.fillMaxWidth(0.3f), textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.primary)
            })
        Box(Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints())
            layout(if (constraints.hasBoundedWidth) constraints.maxWidth else 0, 0) {
                placeable.place(anchor.x, anchor.y)
            }
        }) {
            DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuGroup(
                    shapes = MenuDefaults.groupShape(0, 1),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    options.forEachIndexed { optionIndex, label ->
                        SelectableDropdownMenuItem(
                            text = { Text(label) }, selected = optionIndex == selected,
                            shapes = MenuDefaults.itemShape(optionIndex, options.size),
                            selectedLeadingIcon = {
                                Icon(painterResource(R.drawable.ic_switch_check_18), null, Modifier.size(MenuDefaults.LeadingIconSize))
                            },
                            onClick = {
                                expanded = false
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                onSelected(optionIndex)
                            },
                        )
                    }
                }
            }
        }
    }
}
