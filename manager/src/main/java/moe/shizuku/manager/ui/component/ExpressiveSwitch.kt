package moe.shizuku.manager.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import moe.shizuku.manager.R

internal fun switchThumbIcon(checked: Boolean, enabled: Boolean): Int? = when {
    checked -> R.drawable.ic_switch_check_18
    enabled -> R.drawable.ic_close_24
    else -> null
}

/** KernelSU-Mi's icon-enabled Material switch, including its disabled-state colors. */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val palette = MaterialTheme.colorScheme
    val icon = switchThumbIcon(checked, enabled)
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        interactionSource = interactionSource,
        thumbContent = icon?.let { { Icon(painterResource(it), null, Modifier.size(SwitchDefaults.IconSize)) } },
        colors = SwitchDefaults.colors(
            checkedIconColor = palette.primary,
            uncheckedIconColor = palette.surfaceContainerHighest,
            disabledCheckedThumbColor = palette.surface.copy(alpha = 0.38f),
            disabledCheckedTrackColor = palette.onSurface.copy(alpha = 0.12f),
            disabledCheckedIconColor = palette.onSurface.copy(alpha = 0.12f),
            disabledUncheckedThumbColor = palette.outline.copy(alpha = 0.38f),
            disabledUncheckedTrackColor = palette.surfaceContainerHighest.copy(alpha = 0.12f),
            disabledUncheckedBorderColor = palette.onSurface.copy(alpha = 0.12f),
            disabledUncheckedIconColor = palette.surfaceContainerHighest,
        ),
    )
}
