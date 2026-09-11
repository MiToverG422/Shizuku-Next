package moe.shizuku.manager.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pixel-driven navigation avoids PagerState.animateScrollToPage's distant-page
 * pre-jump. Selection follows the requested destination while pages spring across.
 */
@Stable
class MaterialPagerNavigation internal constructor(
    private val pager: PagerState,
    private val scope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pager.currentPage)
        private set
    var isNavigating by mutableStateOf(false)
        private set
    private var navigation: Job? = null
    private var request = 0

    fun select(page: Int) {
        if (page !in 0 until pager.pageCount || page == selectedPage) return
        val currentRequest = ++request
        navigation?.cancel()
        selectedPage = page
        isNavigating = true
        navigation = scope.launch {
            try {
                pager.animateMaterialPage(page)
            } finally {
                // An obsolete animation must not reset a newer selection.
                if (currentRequest == request) {
                    isNavigating = false
                    selectedPage = pager.currentPage
                }
            }
        }
    }

    internal fun syncSwipe() {
        if (!isNavigating) selectedPage = pager.currentPage
    }
}

@Composable
fun rememberMaterialPagerNavigation(pager: PagerState): MaterialPagerNavigation {
    val scope = rememberCoroutineScope()
    val navigation = remember(pager, scope) { MaterialPagerNavigation(pager, scope) }
    LaunchedEffect(pager.currentPage, navigation.isNavigating) { navigation.syncSwipe() }
    return navigation
}

private suspend fun PagerState.animateMaterialPage(target: Int) {
    var hitBoundary = false
    scroll(MutatePriority.UserInput) {
        val pagePixels = layoutInfo.pageSize + layoutInfo.pageSpacing
        val distance = (target - currentPage - currentPageOffsetFraction) * pagePixels
        if (abs(distance) <= 0.5f) return@scroll
        var consumed = 0f
        var finished = false
        Animatable(0f).animateTo(distance, PagerNavigationSpring) {
            if (!finished) {
                val delta = value - consumed
                if (abs(delta) > 0.5f) {
                    val moved = scrollBy(delta)
                    consumed += moved
                    if (abs(delta - moved) > 0.1f) {
                        hitBoundary = true
                        finished = true
                    }
                } else consumed = value
                if (abs(velocity) < 0.1f && abs(distance - consumed) < 1f) finished = true
            }
        }
        if (abs(distance - consumed) > 0.5f) scrollBy(distance - consumed)
    }
    if (hitBoundary || currentPage != target) scrollToPage(target)
}
