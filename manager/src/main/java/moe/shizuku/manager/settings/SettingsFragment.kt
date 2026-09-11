package moe.shizuku.manager.settings

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.ktx.setComponentEnabled
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.receiver.BootCompleteReceiver
import moe.shizuku.manager.service.KeepAliveNotificationHelper
import moe.shizuku.manager.service.KeepAliveTaskService
import moe.shizuku.manager.service.KeepAliveWorker
import moe.shizuku.manager.ui.component.MaterialRow
import moe.shizuku.manager.ui.component.SectionTitle
import moe.shizuku.manager.ui.component.MaterialChoiceRow
import moe.shizuku.manager.ui.component.pageNestedScroll
import moe.shizuku.manager.ui.theme.ShizukuTheme
import moe.shizuku.manager.utils.CustomTabsHelper
import rikka.material.app.LocaleDelegate
import java.util.Locale

class SettingsFragment : Fragment() {
    private val prefs get() = ShizukuSettings.getPreferences()
    private var backgroundAllowed by mutableStateOf(false)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { setKeepAliveEnabled(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStartupMode(prefs.getString(ShizukuSettings.STARTUP_MODE, ShizukuSettings.StartupMode.NONE)
            ?.takeIf { it == ShizukuSettings.StartupMode.BROADCAST || it == ShizukuSettings.StartupMode.SCRIPT }
            ?: ShizukuSettings.StartupMode.NONE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { ShizukuTheme { SettingsScreen() } }
        }

    override fun onResume() {
        super.onResume()
        backgroundAllowed = isBackgroundRunningAllowed()
    }

    @Composable
    private fun SettingsScreen() {
        var revision by remember { mutableIntStateOf(0) }
        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        val snapshot = remember(revision) { prefs.all }
        fun enabled(key: String, default: Boolean = false) = snapshot[key] as? Boolean ?: default
        val mode = snapshot[ShizukuSettings.STARTUP_MODE] as? String ?: ShizukuSettings.StartupMode.NONE
        val languages = listOf("SYSTEM", "en", "zh-CN", "zh-TW", "zh-HK")
        val languageLabels = listOf(R.string.follow_system, R.string.language_english,
            R.string.language_simplified_chinese, R.string.language_traditional_chinese_taiwan,
            R.string.language_traditional_chinese_hong_kong).map { stringResource(it) }
        val languageIndex = languages.indexOf(snapshot[ShizukuSettings.LANGUAGE] ?: "SYSTEM").coerceAtLeast(0)
        val nightValues = listOf(1, 2, -1)
        val nightLabels = listOf(R.string.dark_theme_off, R.string.dark_theme_on, R.string.follow_system).map { stringResource(it) }
        val nightIndex = nightValues.indexOf(snapshot[ShizukuSettings.NIGHT_MODE] as? Int ?: -1).coerceAtLeast(0)

        val bottomPadding = moe.shizuku.manager.ui.component.LocalPageBottomPadding.current
        LazyColumn(Modifier.fillMaxSize().pageNestedScroll(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, bottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item { SectionTitle(stringResource(R.string.settings_startup)) }
            item {
                MaterialRow(stringResource(R.string.settings_startup_broadcast),
                    stringResource(R.string.settings_startup_broadcast_summary), R.drawable.ic_server_start_24dp,
                    index = 0, count = 4, checked = mode == ShizukuSettings.StartupMode.BROADCAST,
                    onCheckedChange = { setStartupMode(if (it) ShizukuSettings.StartupMode.BROADCAST else ShizukuSettings.StartupMode.NONE) })
            }
            item {
                MaterialRow(stringResource(R.string.settings_startup_script),
                    stringResource(R.string.settings_startup_script_summary), R.drawable.ic_code_24dp,
                    index = 1, count = 4, checked = mode == ShizukuSettings.StartupMode.SCRIPT,
                    onCheckedChange = { setStartupMode(if (it) ShizukuSettings.StartupMode.SCRIPT else ShizukuSettings.StartupMode.NONE) })
            }
            item {
                MaterialRow(stringResource(R.string.settings_auto_start_on_app_open),
                    stringResource(R.string.settings_auto_start_on_app_open_summary), R.drawable.ic_outline_play_arrow_24,
                    index = 2, count = 4, checked = enabled(ShizukuSettings.AUTO_START_ON_APP_OPEN),
                    onCheckedChange = { prefs.edit().putBoolean(ShizukuSettings.AUTO_START_ON_APP_OPEN, it).apply() })
            }
            item {
                MaterialRow(stringResource(R.string.settings_auto_restart_in_background),
                    stringResource(R.string.settings_auto_restart_in_background_summary), R.drawable.ic_server_restart,
                    index = 3, count = 4, checked = enabled(ShizukuSettings.AUTO_RESTART_IN_BACKGROUND),
                    onCheckedChange = { prefs.edit().putBoolean(ShizukuSettings.AUTO_RESTART_IN_BACKGROUND, it).apply() })
            }
            item { SectionTitle(stringResource(R.string.settings_service)) }
            item {
                MaterialRow(stringResource(R.string.settings_keep_alive), stringResource(R.string.settings_keep_alive_summary),
                    R.drawable.ic_outline_notifications_active_24, index = 0, count = 2,
                    checked = enabled(ShizukuSettings.KEEP_ALIVE_ENABLED), onCheckedChange = { value ->
                        if (value && Build.VERSION.SDK_INT >= 33 &&
                            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else setKeepAliveEnabled(value)
                    })
            }
            item {
                MaterialRow(stringResource(R.string.settings_allow_background_running),
                    stringResource(if (backgroundAllowed) R.string.settings_allow_background_running_already_allowed else R.string.settings_allow_background_running_summary),
                    R.drawable.ic_outline_arrow_upward_24, index = 1, count = 2,
                    enabled = !backgroundAllowed, onClick = { openBackgroundRunningSettings() })
            }
            item { SectionTitle(stringResource(R.string.settings_user_interface)) }
            item {
                MaterialChoiceRow(stringResource(R.string.dark_theme), R.drawable.ic_outline_dark_mode_24,
                    index = 0, count = if (Build.VERSION.SDK_INT >= 31) 2 else 1,
                    options = nightLabels, selected = nightIndex, onSelected = {
                        prefs.edit().putInt(ShizukuSettings.NIGHT_MODE, nightValues[it]).apply()
                        AppCompatDelegate.setDefaultNightMode(nightValues[it])
                    })
            }
            if (Build.VERSION.SDK_INT >= 31) item {
                MaterialRow(stringResource(R.string.settings_use_system_color), icon = R.drawable.ic_settings_outline_24dp,
                    index = 1, count = 2, checked = enabled(ThemeHelper.KEY_USE_SYSTEM_COLOR, true),
                    onCheckedChange = {
                        prefs.edit().putBoolean(ThemeHelper.KEY_USE_SYSTEM_COLOR, it).apply()
                        requireActivity().recreate()
                    })
            }
            item { SectionTitle(stringResource(R.string.settings_language)) }
            item {
                MaterialChoiceRow(stringResource(R.string.settings_language), R.drawable.ic_outline_translate_24,
                    index = 0, count = 2, options = languageLabels, selected = languageIndex, onSelected = {
                        val tag = languages[it]
                        prefs.edit().putString(ShizukuSettings.LANGUAGE, tag).apply()
                        LocaleDelegate.defaultLocale = if (tag == "SYSTEM") LocaleDelegate.systemLocale else Locale.forLanguageTag(tag)
                        requireActivity().recreate()
                    })
            }
            item {
                MaterialRow(stringResource(R.string.settings_translation),
                    stringResource(R.string.settings_translation_summary, stringResource(R.string.app_name)),
                    R.drawable.ic_outline_open_in_new_24, index = 1, count = 2,
                    onClick = { CustomTabsHelper.launchUrlOrCopy(requireContext(), getString(R.string.translation_url)) })
            }
            val contributors = getString(R.string.translation_contributors).toHtml().toString()
            if (contributors.isNotBlank()) item {
                SectionTitle(stringResource(R.string.settings_translation_contributors))
                MaterialRow(contributors)
            }
        }
    }

    private fun setStartupMode(mode: String) {
        val enabled = mode != ShizukuSettings.StartupMode.NONE
        prefs.edit().putString(ShizukuSettings.STARTUP_MODE, mode)
            .putBoolean(ShizukuSettings.KEEP_START_ON_BOOT, enabled).apply()
        requireContext().packageManager.setComponentEnabled(
            ComponentName(requireContext(), BootCompleteReceiver::class.java), enabled)
    }

    private fun setKeepAliveEnabled(enabled: Boolean) {
        val context = requireContext()
        prefs.edit().putBoolean(ShizukuSettings.KEEP_ALIVE_ENABLED, enabled).apply()
        if (enabled) {
            KeepAliveNotificationHelper.startTicker(context)
            KeepAliveTaskService.start(context)
            KeepAliveWorker.schedule(context)
            KeepAliveWorker.runNow(context)
        } else {
            KeepAliveTaskService.stop(context)
            KeepAliveWorker.cancel(context)
            KeepAliveNotificationHelper.cancel(context)
        }
    }

    private fun isBackgroundRunningAllowed() =
        requireContext().getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(requireContext().packageName)

    private fun openBackgroundRunningSettings() {
        val context = requireContext()
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        if (runCatching { startActivity(intent) }.isSuccess) return
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        if (runCatching { startActivity(fallback) }.isFailure)
            Toast.makeText(context, R.string.settings_allow_background_running_unavailable, Toast.LENGTH_SHORT).show()
    }
}
