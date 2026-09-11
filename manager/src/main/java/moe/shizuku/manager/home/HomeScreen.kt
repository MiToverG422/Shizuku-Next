package moe.shizuku.manager.home

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.shell.ShellTutorialActivity
import moe.shizuku.manager.ui.component.MaterialRow
import moe.shizuku.manager.ui.component.TonalCard
import moe.shizuku.manager.ui.component.LocalPageBottomPadding
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.ShizukuPidResolver
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    status: ServiceStatus,
    listState: LazyListState,
    onActivate: () -> Unit,
    active: Boolean = true,
) {
    val context = LocalContext.current
    val bottomPadding = LocalPageBottomPadding.current
    val running = status.isRunning
    var processInfo by remember { mutableStateOf<ShizukuPidResolver.ProcessInfo?>(null) }
    var runtime by remember { mutableStateOf("--:--:--") }
    var startedAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(status) {
        processInfo = null
        runtime = "--:--:--"
        startedAt = null
        if (!running) return@LaunchedEffect
        // Process queries can execute shell commands; never perform them during composition.
        val info = withContext(Dispatchers.IO) { ShizukuPidResolver.resolveProcessInfo(context) }
        processInfo = info
        val seconds = info.runtimeSeconds ?: return@LaunchedEffect
        startedAt = SystemClock.elapsedRealtime() - seconds * 1000L
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(startedAt, active, lifecycle) {
        val started = startedAt ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val elapsed = ((SystemClock.elapsedRealtime() - started) / 1000L).coerceAtLeast(0)
                runtime = String.format(Locale.getDefault(), "%02d:%02d:%02d", elapsed / 3600, elapsed / 60 % 60, elapsed % 60)
                delay(1000)
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item("status") {
            val container = if (running) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
            val foreground = contentColorFor(container)
            MaterialRow(
                title = stringResource(if (running) R.string.home_status_running_simple else R.string.home_status_not_running_simple),
                summary = if (running) stringResource(R.string.home_status_version_pid_format,
                    "${status.apiVersion}.${status.patchVersion}", processInfo?.pid?.takeIf { it > 0 }?.toString() ?: "-")
                    else stringResource(R.string.activation_methods_title),
                icon = if (running) R.drawable.ic_server_ok_24dp else R.drawable.ic_server_error_24dp,
                onClick = onActivate,
                colors = ListItemDefaults.segmentedColors(
                    containerColor = container, contentColor = foreground,
                    leadingContentColor = foreground, trailingContentColor = foreground,
                    supportingContentColor = foreground.copy(alpha = 0.7f),
                ),
                trailing = if (running) {
                    {
                        Surface(color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {
                            Text(stringResource(when (status.uid) {
                                0 -> R.string.home_status_mode_root
                                2000 -> R.string.home_status_mode_adb
                                else -> R.string.home_status_mode_unknown
                            }), Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmallEmphasized)
                        }
                    }
                } else null,
                headlineStyle = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.animateItem(),
            )
        }
        item("device") {
            val offset = if (running) 1 else 0
            val count = 3 + offset
            Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (running) MaterialRow(stringResource(R.string.home_runtime_title),
                    stringResource(R.string.home_status_runtime_format, runtime),
                    R.drawable.ic_clock_24, index = 0, count = count)
                MaterialRow(stringResource(R.string.home_version_info_app_label),
                    stringResource(R.string.home_version_info_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    R.drawable.ic_outline_info_24, index = offset, count = count)
                val model = if (Build.MODEL.startsWith(Build.BRAND, true)) Build.MODEL else "${Build.BRAND} ${Build.MODEL}"
                MaterialRow(stringResource(R.string.home_version_info_model_label), model,
                    R.drawable.ic_root_24dp, index = offset + 1, count = count)
                MaterialRow(stringResource(R.string.home_version_info_android_label),
                    stringResource(R.string.home_version_info_android_format, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
                    R.drawable.ic_adb_24dp, index = offset + 2, count = count)
            }
        }
        if (running && !status.permission) item("warning") {
            TonalCard(color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.home_adb_is_limited_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_adb_is_limited_description), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { CustomTabsHelper.launchUrlOrCopy(context, Helps.ADB_PERMISSION.get()) }) {
                        Text(stringResource(R.string.home_adb_button_view_help))
                    }
                }
            }
        }
        item("links") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (status.permission) MaterialRow(stringResource(R.string.home_terminal_title),
                    stringResource(R.string.home_terminal_description), R.drawable.ic_terminal_24,
                    index = 0, count = 2,
                    onClick = { context.startActivity(Intent(context, ShellTutorialActivity::class.java)) })
                MaterialRow(stringResource(R.string.home_learn_more_title), stringResource(R.string.home_learn_more_description),
                    R.drawable.ic_learn_more_24dp, index = if (status.permission) 1 else 0,
                    count = if (status.permission) 2 else 1,
                    onClick = { CustomTabsHelper.launchUrlOrCopy(context, Helps.HOME.get()) })
            }
        }
    }
}
