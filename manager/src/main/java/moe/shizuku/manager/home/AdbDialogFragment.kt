package moe.shizuku.manager.home

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.MutableLiveData
import moe.shizuku.manager.R
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.ui.component.MaterialDialogFragment
import moe.shizuku.manager.utils.EnvironmentUtils

@RequiresApi(Build.VERSION_CODES.R)
class AdbDialogFragment : MaterialDialogFragment() {
    private lateinit var adbMdns: AdbMdns
    private val discoveredPort = MutableLiveData<Int>()
    private var starting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adbMdns = AdbMdns(requireContext(), AdbMdns.TLS_CONNECT) { discoveredPort.postValue(it) }
        discoveredPort.observe(this) { if (it in 1..65535) startAndDismiss(it) }
    }

    override fun onStart() {
        super.onStart()
        adbMdns.start()
        if (requireContext().checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
            val resolver = requireContext().contentResolver
            Settings.Global.putInt(resolver, "adb_wifi_enabled", 1)
            Settings.Global.putInt(resolver, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putLong(resolver, "adb_allowed_connection_time", 0L)
        }
    }

    override fun onStop() {
        adbMdns.stop()
        super.onStop()
    }

    @Composable override fun Content() {
        val tcpPort = EnvironmentUtils.getAdbTcpPort()
        AlertDialog(
            onDismissRequest = { dismissAllowingStateLoss() },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.dialog_adb_discovery)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.dialog_adb_discovery_message))
                    Text(stringResource(R.string.dialog_adb_discovery_message_toggle_wireless_debugging))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                    }) }
                }) { Text(stringResource(R.string.development_settings)) }
            },
            dismissButton = {
                Row {
                    if (tcpPort in 1..65535) TextButton(onClick = { startAndDismiss(tcpPort) }) { Text(tcpPort.toString()) }
                    TextButton(onClick = { dismissAllowingStateLoss() }) { Text(stringResource(android.R.string.cancel)) }
                }
            },
        )
    }

    private fun startAndDismiss(port: Int) {
        if (starting || port !in 1..65535) return
        starting = true
        startActivity(Intent(requireContext(), StarterActivity::class.java).apply {
            putExtra(StarterActivity.EXTRA_IS_ROOT, false)
            putExtra(StarterActivity.EXTRA_HOST, "127.0.0.1")
            putExtra(StarterActivity.EXTRA_PORT, port)
        })
        dismissAllowingStateLoss()
    }
}
