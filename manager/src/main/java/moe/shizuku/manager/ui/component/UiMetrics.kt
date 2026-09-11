package moe.shizuku.manager.ui.component

import androidx.compose.ui.unit.dp
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll

val LocalPageBottomPadding = compositionLocalOf { 0.dp }
val LocalPageScrollConnection = compositionLocalOf<NestedScrollConnection?> { null }

/** ComposeView inside AndroidFragment is a scroll boundary; forward to its page app bar. */
@Composable
fun Modifier.pageNestedScroll(): Modifier =
    LocalPageScrollConnection.current?.let { nestedScroll(it) } ?: this

/** Measured tokens from KernelSU-Mi's Material implementation. */
object UiMetrics {
    val IconSize = 24.dp
    val AppIconSize = 48.dp
    val PagePadding = 16.dp
    val SectionGap = 13.dp
    val TitleGap = 8.dp
    val SegmentGap = 2.dp
    val FloatingBarHeight = 56.dp
    const val NavigationDuration = 250
}
