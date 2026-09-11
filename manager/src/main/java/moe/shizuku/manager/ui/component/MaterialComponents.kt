package moe.shizuku.manager.ui.component

import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** The reference uses the Material defaults, not hand-drawn static list surfaces. */
val PagerNavigationSpring = spring<Float>(
    stiffness = 322.2f,
    dampingRatio = 32.31f / (2f * kotlin.math.sqrt(322.2f)),
    visibilityThreshold = 0.5f,
)

@Composable
fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = UiMetrics.PagePadding, top = UiMetrics.SectionGap, bottom = UiMetrics.TitleGap),
        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
}

@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceBright,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier.fillMaxWidth(), shape = shape,
        colors = CardDefaults.cardColors(containerColor = color), content = content)
}

/** Rich content uses the same outer/inner corners as adjacent segmented rows. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedCard(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    TonalCard(modifier = modifier,
        shape = if (count == 1) MaterialTheme.shapes.large
            else ListItemDefaults.segmentedShapes(index, count).shape,
        content = content)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialRow(
    title: String,
    summary: String? = null,
    icon: Int? = null,
    index: Int = 0,
    count: Int = 1,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    headlineStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    colors: ListItemColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceBright,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceBright,
        supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
) {
    val interactions = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val baseShapes = ListItemDefaults.segmentedShapes(index, count)
    val shapes = if (count == 1) baseShapes.copy(shape = MaterialTheme.shapes.large) else baseShapes
    // Sharing the source lets the switch thumb respond when the whole row is pressed.
    SegmentedListItem(
        onClick = {
            if (checked != null && onCheckedChange != null) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onCheckedChange(!checked)
            } else onClick?.invoke()
        },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shapes = shapes,
        colors = colors,
        interactionSource = interactions,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = leading ?: icon?.let { { Icon(painterResource(it), null, Modifier.size(UiMetrics.IconSize)) } },
        supportingContent = summary?.takeIf(String::isNotBlank)?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium) }
        },
        trailingContent = if (checked != null) {
            {
                ExpressiveSwitch(
                    checked = checked, onCheckedChange = null, enabled = enabled,
                    interactionSource = interactions,
                )
            }
        } else trailing,
        content = { Text(title, style = headlineStyle) },
    )
}
