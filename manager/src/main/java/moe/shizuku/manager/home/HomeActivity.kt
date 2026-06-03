package moe.shizuku.manager.home

import android.content.DialogInterface
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.AboutDialogBinding
import moe.shizuku.manager.databinding.HomeActivityBinding
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.management.AppsPageFragment
import moe.shizuku.manager.management.appsViewModel
import moe.shizuku.manager.settings.SettingsFragment
import moe.shizuku.manager.shell.QuickShellFragment
import moe.shizuku.manager.utils.AppIconCache
import moe.shizuku.manager.utils.ServiceStarter
import rikka.core.ktx.unsafeLazy
import rikka.lifecycle.Status
import rikka.lifecycle.viewModels
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.addItemSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.shizuku.Shizuku

open class HomeActivity : AppBarActivity() {

    private enum class Tab {
        HOME, APPS, SETTINGS, QUICKSHELL
    }

    private var currentTab = Tab.HOME
    private var composeTabState by mutableStateOf(Tab.HOME)
    private lateinit var binding: HomeActivityBinding
    private var autoStartTried = false
    private var autoStartInFlight = false
    private var appsFragmentCreated = false
    private var settingsFragmentCreated = false
    private var quickShellFragmentCreated = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkServerStatus()
        if (appsFragmentCreated && currentTab == Tab.APPS) {
            appsModel.load()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        checkServerStatus()
    }

    private val homeModel by viewModels { HomeViewModel() }
    private val appsModel by appsViewModel()
    private val adapter by unsafeLazy {
        HomeAdapter(
            homeModel,
            onAuthorizedCountClick = { switchToAppsTab() },
            onStopClick = { showStopDialog() }
        )
    }
    private data class BottomBarPalette(
        val background: Int,
        val selectedIndicator: Int,
        val selectedContent: Int,
        val unselectedContent: Int
    )

    override fun onApplyUserThemeResource(theme: Resources.Theme, isDecorView: Boolean) {
        super.onApplyUserThemeResource(theme, isDecorView)
        theme.applyStyle(R.style.ThemeOverlay_Rikka_Material3_Preference, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        binding = HomeActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val bottomBarPalette = BottomBarPalette(
            background = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceContainer, 0),
            selectedIndicator = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer, 0),
            selectedContent = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSecondaryContainer, 0),
            unselectedContent = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
        )
        binding.bottomNavCompose.setContent {
            KernelStyleBottomBar(
                selected = composeTabState,
                onSelect = { switchToTab(it) },
                palette = bottomBarPalette
            )
        }

        homeModel.serviceStatus.observe(this) {
            val status = it.data ?: return@observe
            adapter.updateData()
            if (status.isRunning) {
                appsModel.load(onlyCount = true)
            }
        }
        appsModel.grantedCount.observe(this) {
            if (it.status == Status.SUCCESS) {
                adapter.grantedCount = it.data ?: 0
            }
        }

        val recyclerView = binding.list
        recyclerView.adapter = adapter
        recyclerView.fixEdgeEffect()
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null
        recyclerView.addItemSpacing(top = 1f, bottom = 1f, unit = TypedValue.COMPLEX_UNIT_DIP)
        recyclerView.addEdgeSpacing(top = 1f, bottom = 1f, left = 16f, right = 16f, unit = TypedValue.COMPLEX_UNIT_DIP)
        if (savedInstanceState != null) {
            settingsFragmentCreated = supportFragmentManager.findFragmentById(R.id.settings_container) != null
            appsFragmentCreated = supportFragmentManager.findFragmentById(R.id.apps_container) != null
            quickShellFragmentCreated = supportFragmentManager.findFragmentById(R.id.quickshell_container) != null
        }
        renderTab(Tab.HOME)
        composeTabState = Tab.HOME
        updateToolbarTitle(Tab.HOME)

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onResume() {
        super.onResume()
        maybeAutoStartService()
        checkServerStatus()
    }

    private fun checkServerStatus() {
        homeModel.reload()
    }

    private fun maybeAutoStartService() {
        if (autoStartTried) return
        if (autoStartInFlight) return
        if (Shizuku.pingBinder()) return
        if (!ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.AUTO_START_ON_APP_OPEN, false)) return

        autoStartTried = true
        autoStartInFlight = true
        CoroutineScope(Dispatchers.IO).launch {
            val started = ServiceStarter.tryStartByLastMode()
            if (started) {
                repeat(8) {
                    if (Shizuku.pingBinder()) return@repeat
                    delay(300L)
                }
            }
            Handler(Looper.getMainLooper()).post {
                autoStartInFlight = false
                if (!Shizuku.pingBinder()) {
                    // Allow retry on next resume if still not started.
                    autoStartTried = false
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        val inHome = currentTab == Tab.HOME
        menu.findItem(R.id.action_settings)?.isVisible = false
        menu.findItem(R.id.action_about)?.isVisible = inHome
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                val binding = AboutDialogBinding.inflate(LayoutInflater.from(this), null, false)
                binding.sourceCode.movementMethod = LinkMovementMethod.getInstance()
                val sourceCode = getString(
                    R.string.about_view_source_code,
                    "<b><a href=\"https://github.com/MiToverG422/Shizuku-Next\">GitHub</a></b>"
                )
                val telegram = getString(
                    R.string.about_join_telegram,
                    "<b><a href=\"https://t.me/miaomiao114514\">@miaomiao114514</a></b>"
                )
                binding.sourceCode.text = "$sourceCode<br>$telegram".toHtml()
                binding.icon.setImageBitmap(
                    AppIconCache.getOrLoadBitmap(
                        this,
                        applicationInfo,
                        Process.myUid() / 100000,
                        resources.getDimensionPixelOffset(R.dimen.default_app_icon_size)
                    )
                )
                binding.versionName.text = packageManager.getPackageInfo(packageName, 0).versionName
                MaterialAlertDialogBuilder(this)
                    .setView(binding.root)
                    .show()
                true
            }
            R.id.action_settings -> {
                switchToTab(Tab.SETTINGS)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showStopDialog() {
        if (!Shizuku.pingBinder()) return
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.dialog_stop_message)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                try {
                    Shizuku.exit()
                } catch (_: Throwable) {
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun switchToTab(tab: Tab) {
        if (currentTab == tab) return
        val fromTab = currentTab
        currentTab = tab
        composeTabState = tab
        ensureTabFragment(tab)
        invalidateOptionsMenu()
        renderTab(fromTab, tab, true)
        updateToolbarTitle(tab)
    }

    private fun switchToAppsTab() {
        switchToTab(Tab.APPS)
    }

    private fun ensureTabFragment(tab: Tab) {
        when (tab) {
            Tab.APPS -> if (!appsFragmentCreated) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.apps_container, AppsPageFragment())
                    .commitNowAllowingStateLoss()
                appsFragmentCreated = true
            }
            Tab.SETTINGS -> if (!settingsFragmentCreated) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, SettingsFragment())
                    .commitNowAllowingStateLoss()
                settingsFragmentCreated = true
            }
            Tab.QUICKSHELL -> if (!quickShellFragmentCreated) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.quickshell_container, QuickShellFragment())
                    .commitNowAllowingStateLoss()
                quickShellFragmentCreated = true
            }
            Tab.HOME -> Unit
        }
    }

    private fun renderTab(tab: Tab) {
        renderTab(tab, tab, false)
    }

    private fun renderTab(fromTab: Tab, toTab: Tab, animate: Boolean) {
        currentTab = toTab
        val allViews = listOf(
            binding.list,
            binding.appsContainer,
            binding.settingsContainer,
            binding.quickshellContainer
        )
        allViews.forEach {
            it.animate().cancel()
            it.animate().setListener(null)
        }

        val fromView = getTabView(fromTab)
        val toView = getTabView(toTab)
        if (!animate || fromView === toView) {
            allViews.forEach {
                it.translationX = 0f
                it.alpha = 1f
                it.visibility = View.GONE
            }
            toView.visibility = View.VISIBLE
            updateToolbarTitle(toTab)
            invalidateOptionsMenu()
            return
        }

        val forward = tabOrder(toTab) > tabOrder(fromTab)
        val width = binding.root.width.toFloat().takeIf { it > 0f } ?: dp(320).toFloat()
        val startOffset = if (forward) width * 0.06f else -width * 0.06f
        val endOffset = -startOffset * 0.5f

        allViews.filter { it !== fromView && it !== toView }.forEach { it.visibility = View.GONE }

        toView.visibility = View.VISIBLE
        toView.translationX = startOffset
        toView.alpha = 0f

        fromView.animate()
            .translationX(endOffset)
            .alpha(0f)
            .setDuration(130L)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                fromView.visibility = View.GONE
                fromView.translationX = 0f
                fromView.alpha = 1f
            }
            .start()

        toView.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(150L)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()

        updateToolbarTitle(toTab)
        invalidateOptionsMenu()
    }

    private fun updateToolbarTitle(tab: Tab) {
        setAppBarTitle(getTabTitle(tab))
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun getTabView(tab: Tab): View {
        return when (tab) {
            Tab.HOME -> binding.list
            Tab.APPS -> binding.appsContainer
            Tab.SETTINGS -> binding.settingsContainer
            Tab.QUICKSHELL -> binding.quickshellContainer
        }
    }

    private fun getTabTitle(tab: Tab): String {
        return when (tab) {
            Tab.HOME -> getString(R.string.app_name)
            Tab.APPS -> getString(R.string.nav_apps)
            Tab.SETTINGS -> getString(R.string.settings_title)
            Tab.QUICKSHELL -> getString(R.string.quickshell_title)
        }
    }

    private fun tabOrder(tab: Tab): Int {
        return when (tab) {
            Tab.HOME -> 0
            Tab.APPS -> 1
            Tab.QUICKSHELL -> 2
            Tab.SETTINGS -> 3
        }
    }

    @Composable
    private fun KernelStyleBottomBar(selected: Tab, onSelect: (Tab) -> Unit, palette: BottomBarPalette) {
        data class Item(val tab: Tab, val label: Int, val icon: Int)
        val items = listOf(
            Item(Tab.HOME, R.string.nav_home, R.drawable.ic_home_24dp),
            Item(Tab.APPS, R.string.nav_apps, R.drawable.ic_apps_24dp),
            Item(Tab.QUICKSHELL, R.string.nav_quickshell, R.drawable.ic_terminal_24),
            Item(Tab.SETTINGS, R.string.nav_settings, R.drawable.ic_settings_outline_24dp)
        )

        val barBackground = Color(palette.background)
        val selectedIndicator = Color(palette.selectedIndicator)
        val selectedContent = Color(palette.selectedContent)
        val unselectedContent = Color(palette.unselectedContent)

        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = barBackground,
            tonalElevation = 0.dp,
            windowInsets = NavigationBarDefaults.windowInsets
        ) {
            Spacer(modifier = Modifier.width(6.dp))
            items.forEach { item ->
                val isSelected = selected == item.tab
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(item.tab) },
                    icon = {
                        Icon(
                            painter = painterResource(item.icon),
                            contentDescription = getString(item.label)
                        )
                    },
                    label = {
                        Text(
                            text = getString(item.label),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = selectedIndicator,
                        selectedIconColor = selectedContent,
                        selectedTextColor = selectedContent,
                        unselectedIconColor = unselectedContent,
                        unselectedTextColor = unselectedContent
                    )
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}
