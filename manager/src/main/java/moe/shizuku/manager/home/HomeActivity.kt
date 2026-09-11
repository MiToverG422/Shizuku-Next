package moe.shizuku.manager.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.management.AppsPageFragment
import moe.shizuku.manager.management.appsViewModel
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.settings.SettingsFragment
import moe.shizuku.manager.shell.QuickShellFragment
import moe.shizuku.manager.ui.theme.ShizukuTheme
import moe.shizuku.manager.ui.component.*
import moe.shizuku.manager.utils.ServiceStarter
import rikka.lifecycle.viewModels
import rikka.shizuku.Shizuku

open class HomeActivity : AppActivity() {
    private enum class Tab(val label: Int, val icon: Int, val selectedIcon: Int = icon) {
        HOME(R.string.nav_home, R.drawable.ic_home_outline_24, R.drawable.ic_home_24dp),
        APPS(R.string.nav_apps, R.drawable.ic_apps_24dp),
        QUICKSHELL(R.string.nav_quickshell, R.drawable.ic_terminal_24),
        SETTINGS(R.string.nav_settings, R.drawable.ic_settings_outline_24dp, R.drawable.ic_action_settings_24dp)
    }
    private var currentTab by mutableStateOf(Tab.HOME)
    private var status by mutableStateOf(ServiceStatus())
    private var aboutDialogVisible by mutableStateOf(false)
    private val homeModel by viewModels { HomeViewModel() }
    private val appsModel by appsViewModel()
    private var autoStartTried = false
    private var autoStartInFlight = false
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        homeModel.reload()
        appsModel.load()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        homeModel.reload()
        appsModel.load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        currentTab = savedInstanceState?.getString("selected_tab")
            ?.let { name -> Tab.entries.find { it.name == name } } ?: Tab.HOME
        homeModel.serviceStatus.observe(this) {
            status = it.data ?: ServiceStatus()
        }
        setContent { ShizukuTheme { MainScreen(); HomeDialogs() } }
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun MainScreen() {
        val homeListState = rememberLazyListState()
        val pager = rememberPagerState(initialPage = currentTab.ordinal) { Tab.entries.size }
        var visitedPages by rememberSaveable { mutableIntStateOf(1 shl currentTab.ordinal) }
        val navigation = rememberMaterialPagerNavigation(pager)
        val selectedTab = Tab.entries[navigation.selectedPage]
        val destinations = remember { Tab.entries.map { NavigationDestination(it.label, it.icon, it.selectedIcon) } }
        fun select(tab: Tab) {
            // Compose every page on the route before starting the continuous scroll.
            for (page in minOf(pager.currentPage, tab.ordinal)..maxOf(pager.currentPage, tab.ordinal)) {
                visitedPages = visitedPages or (1 shl page)
            }
            navigation.select(tab.ordinal)
        }
        BackHandler(selectedTab != Tab.HOME) { select(Tab.HOME) }
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            val useRail = maxWidth >= 600.dp
            val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottomSpace = if (useRail) navInset else
                UiMetrics.FloatingBarHeight + if (navInset > 0.dp) navInset + 8.dp else 16.dp
            Row(Modifier.fillMaxSize()) {
                if (useRail) MaterialNavigationRail(destinations, selectedTab.ordinal, { select(Tab.entries[it]) })
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    CompositionLocalProvider(LocalPageBottomPadding provides bottomSpace) {
                        HorizontalPager(
                            state = pager, beyondViewportPageCount = 3, overscrollEffect = null,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            val tab = Tab.entries[page]
                            // Each page owns its app bar and collapse state, as in KernelSU-Mi.
                            // Swiping moves both the title and content; switching tabs never jumps
                            // a shared toolbar back to its expanded state.
                            val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
                            Scaffold(
                                modifier = Modifier.fillMaxSize().then(
                                    if (tab == Tab.HOME) Modifier.nestedScroll(scroll.nestedScrollConnection) else Modifier),
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                                topBar = {
                                    MaterialTopAppBar(
                                        title = stringResource(if (tab == Tab.HOME) R.string.app_name else tab.label),
                                        scrollBehavior = scroll,
                                        actions = {
                                            if (tab == Tab.HOME) IconButton(onClick = { showAbout() }) {
                                                Icon(painterResource(R.drawable.ic_action_about_24dp), stringResource(R.string.action_about))
                                            }
                                        },
                                    )
                                },
                            ) { padding ->
                                CompositionLocalProvider(LocalPageScrollConnection provides scroll.nestedScrollConnection) {
                                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                                        if (visitedPages and (1 shl page) != 0 || page == navigation.selectedPage || page == pager.targetPage) {
                                            when (tab) {
                                                Tab.HOME -> HomeScreen(status, homeListState,
                                                    active = selectedTab == Tab.HOME,
                                                    onActivate = { startActivity(Intent(this@HomeActivity, ActivationMethodsActivity::class.java)) })
                                                Tab.APPS -> AndroidFragment<AppsPageFragment>(Modifier.fillMaxSize())
                                                Tab.QUICKSHELL -> AndroidFragment<QuickShellFragment>(Modifier.fillMaxSize())
                                                Tab.SETTINGS -> AndroidFragment<SettingsFragment>(Modifier.fillMaxSize())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!useRail) FloatingNavigationBar(
                        destinations = destinations,
                        selectedIndex = selectedTab.ordinal,
                        onSelect = { select(Tab.entries[it]) },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                    )
                }
            }
        }
        LaunchedEffect(pager.settledPage) { currentTab = Tab.entries[pager.settledPage] }
        LaunchedEffect(pager.targetPage) { visitedPages = visitedPages or (1 shl pager.targetPage) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_tab", currentTab.name)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        homeModel.reload()
        maybeAutoStartService()
    }

    private fun maybeAutoStartService() {
        if (autoStartTried || autoStartInFlight || Shizuku.pingBinder()) return
        if (!ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.AUTO_START_ON_APP_OPEN, false)) return
        autoStartTried = true
        autoStartInFlight = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { ServiceStarter.tryStartByLastMode() }
                for (attempt in 0 until 8) {
                    if (Shizuku.pingBinder()) break
                    delay(300)
                }
                if (!Shizuku.pingBinder()) autoStartTried = false
                homeModel.reload()
            } finally {
                autoStartInFlight = false
            }
        }
    }

    private fun showAbout() { aboutDialogVisible = true }

    @Composable
    private fun HomeDialogs() {
        if (aboutDialogVisible) AlertDialog(
            onDismissRequest = { aboutDialogVisible = false },
            icon = { Icon(painterResource(R.drawable.ic_action_about_24dp), null) },
            title = { Text(stringResource(R.string.app_name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text(moe.shizuku.manager.BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium)
                    HtmlText(getString(R.string.about_view_source_code,
                        "<a href='https://github.com/MiToverG422/Shizuku-Next'>GitHub</a>"))
                    HtmlText(getString(R.string.about_join_telegram,
                        "<a href='https://t.me/miaomiao114514'>@miaomiao114514</a>"))
                }
            },
            confirmButton = { TextButton(onClick = { aboutDialogVisible = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }
}
