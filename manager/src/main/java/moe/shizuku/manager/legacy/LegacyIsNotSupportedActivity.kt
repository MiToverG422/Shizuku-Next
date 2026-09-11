package moe.shizuku.manager.legacy

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import moe.shizuku.manager.ui.component.HtmlText
import moe.shizuku.manager.ui.theme.ShizukuTheme
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity

class LegacyIsNotSupportedActivity : AppActivity() {

    companion object {

        /**
         * Activity result: user denied request (only API pre-23).
         */
        private inline val RESULT_CANCELED get() = Activity.RESULT_CANCELED

        /**
         * Activity result: error, such as manager app itself not authorized.
         */
        private const val RESULT_ERROR = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callingComponent = callingActivity
        if (callingComponent == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val ai = try {
            packageManager.getApplicationInfo(callingComponent.packageName, PackageManager.GET_META_DATA)
        } catch (e: Throwable) {
            finish()
            return
        }

        val label = try {
            ai.loadLabel(packageManager)
        } catch (e: Exception) {
            ai.packageName
        }

        val v3Support = ai.metaData?.getBoolean("moe.shizuku.client.V3_SUPPORT") == true
        fun close() {
            setResult(RESULT_ERROR)
            finish()
        }
        setContent {
            ShizukuTheme {
                AlertDialog(
                    onDismissRequest = {},
                    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                    title = { Text(getString(if (v3Support) R.string.dialog_requesting_legacy_title
                        else R.string.dialog_legacy_not_support_title, label)) },
                    text = { HtmlText(getString(if (v3Support) R.string.dialog_requesting_legacy_message
                        else R.string.dialog_legacy_not_support_message, android.text.TextUtils.htmlEncode(label.toString()))) },
                    confirmButton = { TextButton(onClick = { close() }) { Text(stringResource(android.R.string.ok)) } },
                    dismissButton = if (v3Support) {
                        {
                            TextButton(onClick = {
                                startActivity(Intent(this@LegacyIsNotSupportedActivity, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                close()
                            }) { Text(stringResource(R.string.dialog_requesting_legacy_button_open_shizuku)) }
                        }
                    } else null,
                )
            }
        }
    }
}
