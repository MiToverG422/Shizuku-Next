package moe.shizuku.manager.authorization

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import moe.shizuku.manager.ui.component.HtmlText
import moe.shizuku.manager.ui.theme.ShizukuTheme
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.utils.Logger.LOGGER
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME
import rikka.shizuku.server.ktx.workerHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RequestPermissionActivity : AppActivity() {


    private fun setResult(requestUid: Int, requestPid: Int, requestCode: Int, allowed: Boolean, onetime: Boolean) {
        val data = Bundle()
        data.putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
        data.putBoolean(REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
        try {
            Shizuku.dispatchPermissionConfirmationResult(requestUid, requestPid, requestCode, data)
        } catch (e: Throwable) {
            LOGGER.e("dispatchPermissionConfirmationResult")
        }
    }

    private fun checkSelfPermission(): Boolean {
        val permission = Shizuku.checkRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS") == PackageManager.PERMISSION_GRANTED
        if (permission) return true

        setContent {
            ShizukuTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    icon = { Icon(painterResource(R.drawable.ic_system_icon), null) },
                    title = { Text("Shizuku: ${getString(R.string.app_management_dialog_adb_is_limited_title)}") },
                    text = { HtmlText(getString(R.string.app_management_dialog_adb_is_limited_message, Helps.ADB.get())) },
                    confirmButton = { TextButton(onClick = { finish() }) { Text(stringResource(android.R.string.ok)) } },
                )
            }
        }
        return false
    }

    private fun waitForBinder(): Boolean {
        val countDownLatch = CountDownLatch(1)

        val listener = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                countDownLatch.countDown()
                Shizuku.removeBinderReceivedListener(this)
            }
        }

        Shizuku.addBinderReceivedListenerSticky(listener, workerHandler)

        return try {
            countDownLatch.await(5, TimeUnit.SECONDS)
            true
        } catch (e: TimeoutException) {
            LOGGER.e(e, "Binder not received in 5s")
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!waitForBinder()) {
            finish()
            return
        }

        val uid = intent.getIntExtra("uid", -1)
        val pid = intent.getIntExtra("pid", -1)
        val requestCode = intent.getIntExtra("requestCode", -1)
        val ai = intent.getParcelableExtra<ApplicationInfo>("applicationInfo")
        if (uid == -1 || pid == -1 || ai == null) {
            finish()
            return
        }
        if (!checkSelfPermission()) {
            setResult(uid, pid, requestCode, allowed = false, onetime = true)
            return
        }

        val label = try {
            ai.loadLabel(packageManager)
        } catch (e: Exception) {
            ai.packageName
        }

        setContent {
            ShizukuTheme {
                AlertDialog(
                    onDismissRequest = {},
                    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                    icon = { Icon(painterResource(R.drawable.ic_system_icon), null) },
                    text = { HtmlText(getString(R.string.permission_warning_template,
                        android.text.TextUtils.htmlEncode(label.toString()), getString(R.string.permission_group_description))) },
                    confirmButton = {
                        TextButton(onClick = {
                            setResult(uid, pid, requestCode, allowed = true, onetime = false)
                            finish()
                        }) { Text(stringResource(R.string.grant_dialog_button_allow_always)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            setResult(uid, pid, requestCode, allowed = false, onetime = true)
                            finish()
                        }) { Text(stringResource(R.string.grant_dialog_button_deny)) }
                    },
                )
            }
        }
    }
}
