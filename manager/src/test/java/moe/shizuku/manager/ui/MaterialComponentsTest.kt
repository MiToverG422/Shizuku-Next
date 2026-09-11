package moe.shizuku.manager.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import android.graphics.Bitmap
import java.io.File
import moe.shizuku.manager.home.HomeScreen
import moe.shizuku.manager.home.ActivationList
import moe.shizuku.manager.model.ServiceStatus
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.component.*
import moe.shizuku.manager.ui.theme.ShizukuTypography
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
class MaterialComponentsTest {
    @get:Rule val compose = createComposeRule()

    private val destinations = listOf(
        NavigationDestination(R.string.nav_home, R.drawable.ic_home_24dp),
        NavigationDestination(R.string.nav_apps, R.drawable.ic_apps_24dp),
        NavigationDestination(R.string.nav_quickshell, R.drawable.ic_terminal_24),
        NavigationDestination(R.string.nav_settings, R.drawable.ic_settings_outline_24dp),
    )

    @Test fun typographyMatchesReference() {
        assertEquals(16.sp, ShizukuTypography.bodyLarge.fontSize)
        assertEquals(24.sp, ShizukuTypography.bodyLarge.lineHeight)
        assertEquals(0.5.sp, ShizukuTypography.bodyLarge.letterSpacing)
        assertEquals(14.sp, ShizukuTypography.bodyMedium.fontSize)
        assertEquals(12.sp, ShizukuTypography.labelMedium.fontSize)
        assertEquals(16.sp, ShizukuTypography.titleMedium.fontSize)
        assertEquals(13.dp, UiMetrics.SectionGap)
        assertEquals(56.dp, UiMetrics.FloatingBarHeight)
        assertEquals(24.dp, UiMetrics.IconSize)
        assertEquals(48.dp, UiMetrics.AppIconSize)
    }

    @Test fun activationStopIsVisibleAfterAsynchronousStatusLoad() {
        var ready by mutableStateOf(false)
        compose.setContent {
            MaterialExpressiveTheme {
                Box(Modifier.width(320.dp).height(300.dp)) {
                    ActivationList(ready) {
                        if (ready) item("stop") { MaterialRow("Stop Shizuku", onClick = {}) }
                        items(10) { MaterialRow("Method $it") }
                    }
                }
            }
        }
        compose.onNodeWithText("Method 0").assertDoesNotExist()
        compose.runOnIdle { ready = true }
        compose.onNodeWithText("Stop Shizuku").assertIsDisplayed()
        assertTrue(compose.onNodeWithText("Stop Shizuku").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithText("Method 0").fetchSemanticsNode().boundsInRoot.top)
    }

    @Test fun richCardsHaveJoinedInnerCornersAndSeparatedGroups() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialExpressiveTheme(colorScheme = lightColorScheme(surfaceBright = Color.White)) {
                    Column(Modifier.width(200.dp).background(Color.Blue),
                        verticalArrangement = Arrangement.spacedBy(UiMetrics.SegmentGap)) {
                        for (index in 0..2) SegmentedCard(index, 3) { Spacer(Modifier.height(64.dp)) }
                    }
                }
            }
        }
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(Color.Blue, pixels[2, 2]) // First: outside the antialiased outer corner.
        assertEquals(Color.White, pixels[4, 59]) // First: small inner bottom corner.
        assertEquals(Color.Blue, pixels[20, 64]) // Exactly 2 dp between segments.
        assertEquals(Color.Blue, pixels[20, 65])
        assertEquals(Color.White, pixels[4, 70]) // Middle: small corners on both ends.
        assertEquals(Color.White, pixels[4, 125])
        assertEquals(Color.White, pixels[4, 136]) // Last: small inner top corner.
        assertEquals(Color.Blue, pixels[2, 193]) // Last: large outer bottom corner.
    }

    @Test fun floatingBarChangesSelectionAndKeepsFourAccessibleTabs() {
        compose.setContent {
            MaterialExpressiveTheme(typography = ShizukuTypography) {
                var selected by remember { mutableIntStateOf(0) }
                FloatingNavigationBar(destinations, selected, { selected = it })
            }
        }
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.onAllNodes(tab).assertCountEquals(4)
        compose.onAllNodes(tab)[0].assertIsSelected()
        compose.onAllNodes(tab)[3].performClick()
        compose.waitForIdle()
        compose.onAllNodes(tab)[3].assertIsSelected()
        compose.onAllNodes(tab)[0].assertIsNotSelected()
        // Finished transitions must remove the previous label, not leave four labels.
        compose.onAllNodes(hasText("Home", substring = false)).assertCountEquals(0)
    }

    @Test fun switchRowTogglesExactlyOnceAndDisabledRowCannotToggle() {
        var clicks = 0
        compose.setContent {
            MaterialExpressiveTheme(typography = ShizukuTypography) {
                var checked by remember { mutableStateOf(false) }
                Column {
                    MaterialRow("Enabled", checked = checked, onCheckedChange = { clicks++; checked = it },
                        index = 0, count = 2)
                    MaterialRow("Disabled", checked = false, enabled = false,
                        onCheckedChange = { clicks++ }, index = 1, count = 2)
                }
            }
        }
        compose.onNodeWithText("Enabled").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.onNodeWithText("Disabled").assertIsNotEnabled()
        compose.onNodeWithText("Disabled").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun largeFontNavigationFitsCompactScreen() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialExpressiveTheme(typography = ShizukuTypography) {
                    Box(Modifier.width(320.dp)) { FloatingNavigationBar(destinations, 2, {}) }
                }
            }
        }
        val tabs = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        tabs.assertCountEquals(4)
        for (i in 0..3) tabs[i].assertIsDisplayed()
    }

    @Test fun pressedSegmentHasAnimatedFeedback() {
        compose.setContent {
            MaterialExpressiveTheme(typography = ShizukuTypography, motionScheme = MotionScheme.expressive()) {
                Column(Modifier.width(320.dp)) {
                    MaterialRow("First", "Press feedback", index = 0, count = 2, onClick = {})
                    Spacer(Modifier.height(2.dp))
                    MaterialRow("Second", index = 1, count = 2, onClick = {})
                }
            }
        }
        val before = compose.onRoot().captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("First").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        val pressed = compose.onRoot().captureToImage().toPixelMap()
        assertTrue("A press must visibly animate the segmented surface",
            (0 until before.height).any { y ->
                (0 until before.width).any { x -> before[x, y] != pressed[x, y] }
            })
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            val file = File("build/reports/ui/segment-pressed.png")
            file.parentFile.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("First").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
    }

    @Test fun renderHomePreview() {
        compose.setContent {
            MaterialExpressiveTheme(typography = ShizukuTypography, motionScheme = MotionScheme.expressive()) {
                Box(Modifier.width(390.dp).height(844.dp)) {
                    CompositionLocalProvider(LocalPageBottomPadding provides 72.dp) {
                        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                        Scaffold(
                            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            topBar = { MaterialTopAppBar("Shizuku", scroll) },
                        ) { padding ->
                            Box(Modifier.padding(padding)) {
                                HomeScreen(ServiceStatus(), rememberLazyListState(), {})
                            }
                        }
                    }
                    FloatingNavigationBar(destinations, 0, {}, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                }
            }
        }
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File("build/reports/ui/home-preview.png")
        output.parentFile.mkdirs()
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(output.length() > 1000)
    }
}
