package moe.shizuku.manager.adb

import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.R
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ui.component.*
import moe.shizuku.manager.ui.theme.ShizukuTheme
import rikka.compatibility.DeviceCompatibility

@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingTutorialActivity : AppActivity() {

    private var notificationEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationEnabled = isNotificationEnabled()
        if (notificationEnabled) startPairingService()
        setContent {
            ShizukuTheme {
                MaterialPage(stringResource(R.string.adb_pairing), { finish() }) {
                    LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        item("notification") {
                            TonalCard(color = if (notificationEnabled) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.errorContainer) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    HtmlText(stringResource(if (notificationEnabled) R.string.adb_pairing_tutorial_content_notification
                                        else R.string.adb_pairing_tutorial_content_notification_blocked))
                                    if (!notificationEnabled) Button(onClick = {
                                        runCatching { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
                                    }) { Text(stringResource(R.string.notification_settings)) }
                                }
                            }
                        }
                        if (notificationEnabled) {
                            item("steps") {
                                Column(verticalArrangement = Arrangement.spacedBy(UiMetrics.SegmentGap)) {
                                    SegmentedCard(0, 4) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            HtmlText(stringResource(R.string.adb_pairing_tutorial_content_network))
                                            HtmlText(stringResource(R.string.adb_pairing_tutorial_content_network_limation_not_foreground))
                                        }
                                    }
                                    MaterialRow(stringResource(R.string.adb_pairing_tutorial_content_steps),
                                        stringResource(R.string.adb_pairing_tutorial_content_left_is_clickable),
                                        R.drawable.ic_numeric_1_circle_outline_24, index = 1, count = 4,
                                        onClick = {
                                            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                                            }) }
                                        })
                                    MaterialRow(stringResource(R.string.adb_pairing_tutorial_content_enter_pairing_code),
                                        icon = R.drawable.ic_numeric_2_circle_outline_24, index = 2, count = 4)
                                    MaterialRow(stringResource(R.string.adb_pairing_tutorial_content_finish),
                                        icon = R.drawable.ic_numeric_3_circle_outline_24, index = 3, count = 4)
                                }
                            }
                        }
                        if (DeviceCompatibility.isMiui()) item("miui") {
                            TonalCard(color = MaterialTheme.colorScheme.errorContainer) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HtmlText(stringResource(R.string.adb_pairing_tutorial_content_miui))
                                    HtmlText(stringResource(R.string.adb_pairing_tutorial_content_miui_2))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isNotificationEnabled(): Boolean {
        val context = this

        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(AdbPairingService.notificationChannel)
        return nm.areNotificationsEnabled() &&
                (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    override fun onResume() {
        super.onResume()

        val newNotificationEnabled = isNotificationEnabled()
        if (newNotificationEnabled != notificationEnabled) {
            notificationEnabled = newNotificationEnabled

            if (newNotificationEnabled) {
                startPairingService()
            }
        }
    }

    private fun startPairingService() {
        val intent = AdbPairingService.startIntent(this)
        try {
            startForegroundService(intent)
        } catch (e: Throwable) {
            Log.e(AppConstants.TAG, "startForegroundService", e)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                val mode = getSystemService(AppOpsManager::class.java)
                    .noteOpNoThrow("android:start_foreground", android.os.Process.myUid(), packageName, null, null)
                if (mode == AppOpsManager.MODE_ERRORED) {
                    Toast.makeText(this, "OP_START_FOREGROUND is denied. What are you doing?", Toast.LENGTH_LONG).show()
                }
                startService(intent)
            }
        }
    }
}
