package moe.shizuku.manager.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.adb.AdbPairingTutorialActivity
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.ui.component.*
import moe.shizuku.manager.ui.theme.ShizukuTheme
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.lifecycle.viewModels
import rikka.shizuku.Shizuku

class ActivationMethodsActivity : AppActivity() {
    private val homeModel by viewModels { HomeViewModel() }
    private var status by mutableStateOf(ServiceStatus())
    private var statusLoaded by mutableStateOf(false)
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { homeModel.reload() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { homeModel.reload() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        homeModel.serviceStatus.observe(this) {
            status = it.data ?: ServiceStatus()
            statusLoaded = true
        }
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        setContent {
            ShizukuTheme {
                MaterialPage(stringResource(R.string.activation_methods_title), { finish() }) { ActivationScreen() }
            }
        }
    }

    @Composable
    private fun ActivationScreen() {
        var showCommand by remember { mutableStateOf(false) }
        var showStopDialog by rememberSaveable { mutableStateOf(false) }
        val rooted = remember { EnvironmentUtils.isRooted() }
        val showWireless = Build.VERSION.SDK_INT >= 30 || EnvironmentUtils.getAdbTcpPort() > 0
        val methodCount = if (showWireless) 3 else 2
        ActivationList(ready = statusLoaded) {
            if (status.isRunning) item("stop") {
                MaterialRow(stringResource(R.string.action_stop),
                    stringResource(R.string.home_stop_service_summary), R.drawable.ic_close_24,
                    onClick = { showStopDialog = true },
                    modifier = Modifier.padding(bottom = UiMetrics.SectionGap - UiMetrics.SegmentGap))
            }
            if (UserHandleCompat.myUserId() == 0) {
                if (rooted) item("root") { RootCard(0, methodCount) }
                if (showWireless) item("wireless") {
                    SegmentedCard(index = if (rooted) 1 else 0, count = methodCount) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.home_wireless_adb_title), style = MaterialTheme.typography.titleMedium)
                            HtmlText(stringResource(if (Build.VERSION.SDK_INT >= 30)
                                R.string.home_wireless_adb_description else R.string.home_wireless_adb_description_pre_11))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { startWireless() }) { Text(stringResource(R.string.home_root_button_start)) }
                                if (Build.VERSION.SDK_INT >= 30) {
                                    FilledTonalButton(onClick = {
                                        if ((display?.displayId ?: 0) > 0) AdbPairDialogFragment().show(supportFragmentManager)
                                        else startActivity(Intent(this@ActivationMethodsActivity, AdbPairingTutorialActivity::class.java))
                                    }) { Text(stringResource(R.string.adb_pairing)) }
                                    TextButton(onClick = { CustomTabsHelper.launchUrlOrCopy(this@ActivationMethodsActivity, Helps.ADB_ANDROID11.get()) }) {
                                        Text(stringResource(R.string.home_wireless_adb_view_guide_button))
                                    }
                                }
                            }
                        }
                    }
                }
                item("adb") {
                    MaterialRow(stringResource(R.string.home_adb_title),
                        icon = R.drawable.ic_adb_24dp, onClick = { showCommand = true },
                        index = (if (rooted) 1 else 0) + (if (showWireless) 1 else 0), count = methodCount,
                        summary = stringResource(R.string.home_adb_button_view_command))
                }
                if (!rooted) item("root") { RootCard(methodCount - 1, methodCount) }
            }
        }
        if (showStopDialog) AlertDialog(
            onDismissRequest = { showStopDialog = false },
            text = { Text(stringResource(R.string.dialog_stop_message)) },
            confirmButton = { TextButton(onClick = {
                showStopDialog = false
                if (Shizuku.pingBinder()) runCatching { Shizuku.exit() }
                homeModel.reload()
            }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { showStopDialog = false }) {
                Text(stringResource(android.R.string.cancel))
            } },
        )
        if (showCommand) AlertDialog(
            onDismissRequest = { showCommand = false },
            title = { Text(stringResource(R.string.home_adb_button_view_command)) },
            text = {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HtmlText(stringResource(R.string.home_adb_description, Helps.ADB.get()))
                        HtmlText(stringResource(R.string.home_adb_dialog_view_command_message, Starter.adbCommand))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", Starter.adbCommand))
                    showCommand = false
                }) { Text(stringResource(R.string.home_adb_dialog_view_command_copy_button)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, Starter.adbCommand)
                        }, getString(R.string.home_adb_dialog_view_command_button_send)))
                    }) { Text(stringResource(R.string.home_adb_dialog_view_command_button_send)) }
                    TextButton(onClick = { showCommand = false }) { Text(stringResource(android.R.string.cancel)) }
                }
            },
        )
    }

    @Composable
    private fun RootCard(index: Int, count: Int) {
        SegmentedCard(index, count) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.home_root_title), style = MaterialTheme.typography.titleMedium)
                HtmlText(stringResource(R.string.home_root_description, "<a href='https://dontkillmyapp.com/'>Don't kill my app!</a>"))
                if (status.isRunning) HtmlText(stringResource(R.string.home_root_description_sui,
                    "<a href='${Helps.SUI.get()}'>Sui</a>", "Sui"))
                Button(onClick = {
                    startActivity(Intent(this@ActivationMethodsActivity, StarterActivity::class.java)
                        .putExtra(StarterActivity.EXTRA_IS_ROOT, true))
                }) {
                    Icon(painterResource(R.drawable.ic_server_start_24dp), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(if (status.isRunning && status.uid == 0)
                        R.string.home_root_button_restart else R.string.home_root_button_start))
                }
            }
        }
    }

    private fun startWireless() {
        if (Build.VERSION.SDK_INT >= 30) AdbDialogFragment().show(supportFragmentManager)
        else {
            val port = EnvironmentUtils.getAdbTcpPort()
            if (port > 0) startActivity(Intent(this, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                putExtra(StarterActivity.EXTRA_HOST, "127.0.0.1")
                putExtra(StarterActivity.EXTRA_PORT, port)
            }) else WadbNotEnabledDialogFragment().show(supportFragmentManager)
        }
    }

    override fun onResume() {
        super.onResume()
        homeModel.reload()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }
}

/** Do not anchor the list to a method before the asynchronous stop row is known. */
@Composable
internal fun ActivationList(ready: Boolean, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(UiMetrics.SegmentGap), content = content)
    }
}
