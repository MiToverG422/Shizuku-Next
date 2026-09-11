package moe.shizuku.manager.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.materialkolor.dynamiccolor.ColorSpec
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.component.MaterialPagerNavigation
import moe.shizuku.manager.ui.component.rememberMaterialPagerNavigation
import moe.shizuku.manager.ui.component.switchThumbIcon
import moe.shizuku.manager.ui.theme.DefaultMaterialColorSpec
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MaterialNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var pager: PagerState
    private lateinit var navigation: MaterialPagerNavigation

    private fun showPager() {
        compose.setContent {
            pager = rememberPagerState { 4 }
            navigation = rememberMaterialPagerNavigation(pager)
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 3) {
                Text("Page $it")
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @Test fun switchIconsMatchCheckedAndDisabledStates() {
        assertEquals(R.drawable.ic_switch_check_18, switchThumbIcon(true, true))
        assertEquals(R.drawable.ic_close_24, switchThumbIcon(false, true))
        assertEquals(R.drawable.ic_switch_check_18, switchThumbIcon(true, false))
        assertNull(switchThumbIcon(false, false))
    }

    @Test fun colorSpecificationIsExplicitly2025() {
        assertEquals(ColorSpec.SpecVersion.SPEC_2025, DefaultMaterialColorSpec)
    }

    @Test fun distantNavigationAnimatesWithoutPreJump() {
        showPager()
        compose.runOnIdle {
            navigation.select(3)
            assertEquals(3, navigation.selectedPage)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals("Must start at the original page", 0, pager.currentPage) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            val position = pager.currentPage + pager.currentPageOffsetFraction
            assertTrue("Must traverse intermediate positions: $position", position > 0f && position < 3f)
        }
        compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle {
            assertEquals(3, pager.currentPage)
            assertEquals(0f, pager.currentPageOffsetFraction, 0.01f)
            assertFalse(navigation.isNavigating)
        }
    }

    @Test fun rapidRetargetKeepsLatestSelection() {
        showPager()
        compose.runOnIdle { navigation.select(3) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { navigation.select(1) }
        compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle {
            assertEquals(1, pager.currentPage)
            assertEquals(1, navigation.selectedPage)
            assertEquals(0f, pager.currentPageOffsetFraction, 0.01f)
            assertFalse(navigation.isNavigating)
        }
    }
}
