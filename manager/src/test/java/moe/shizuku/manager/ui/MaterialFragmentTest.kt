package moe.shizuku.manager.ui

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.compose.AndroidFragment
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.home.WadbNotEnabledDialogFragment
import moe.shizuku.manager.ui.component.LocalPageBottomPadding
import moe.shizuku.manager.ui.component.LocalPageScrollConnection
import moe.shizuku.manager.ui.component.MaterialTopAppBar
import moe.shizuku.manager.ui.component.pageNestedScroll
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaterialFragmentTest {
    @get:Rule val compose = createAndroidComposeRule<FragmentActivity>()

    @Test fun embeddedPagesReceiveFloatingBarClearance() {
        compose.setContent {
            CompositionLocalProvider(LocalPageBottomPadding provides 72.dp) {
                AndroidFragment<InsetProbeFragment>()
            }
        }
        compose.onNodeWithTag("clearance").assertHeightIsEqualTo(72.dp)
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Test fun embeddedPageCollapsesItsOwnTopBar() {
        lateinit var bar: TopAppBarState
        compose.setContent {
            MaterialExpressiveTheme {
                val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                SideEffect { bar = scroll.state }
                CompositionLocalProvider(LocalPageScrollConnection provides scroll.nestedScrollConnection) {
                    Scaffold(topBar = { MaterialTopAppBar("Page", scroll) }) { padding ->
                        AndroidFragment<ScrollProbeFragment>(Modifier.padding(padding))
                    }
                }
            }
        }
        compose.onNodeWithTag("scroll").performTouchInput { swipeUp() }
        compose.runOnIdle { org.junit.Assert.assertTrue(bar.heightOffset < -10f) }
    }

    @Test fun composeDialogDismissesWithoutReplacingHostPage() {
        compose.runOnUiThread { ShizukuSettings.initialize(compose.activity) }
        compose.setContent { Text("Underlying page") }
        compose.runOnUiThread {
            WadbNotEnabledDialogFragment().show(compose.activity.supportFragmentManager)
            compose.activity.supportFragmentManager.executePendingTransactions()
        }
        compose.onNodeWithText(compose.activity.getString(R.string.dialog_wireless_adb_not_enabled)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(android.R.string.ok)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Underlying page").assertIsDisplayed()
        compose.runOnUiThread {
            compose.activity.supportFragmentManager.executePendingTransactions()
            assertEquals(0, compose.activity.supportFragmentManager.fragments.size)
        }
    }
}

class ScrollProbeFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LazyColumn(Modifier.fillMaxSize().pageNestedScroll().testTag("scroll")) {
                    items(100) { Text("Row $it", Modifier.height(72.dp)) }
                }
            }
        }
}

class InsetProbeFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { Spacer(Modifier.width(100.dp).height(LocalPageBottomPadding.current).testTag("clearance")) }
        }
}
