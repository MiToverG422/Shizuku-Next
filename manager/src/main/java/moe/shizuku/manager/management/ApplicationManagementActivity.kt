package moe.shizuku.manager.management

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource
import androidx.fragment.compose.AndroidFragment
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.component.MaterialPage
import moe.shizuku.manager.ui.theme.ShizukuTheme
import rikka.shizuku.Shizuku

class ApplicationManagementActivity : AppActivity() {
    private val binderDeadListener = Shizuku.OnBinderDeadListener { if (!isFinishing) finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Shizuku.pingBinder()) {
            finish()
            return
        }
        Shizuku.addBinderDeadListener(binderDeadListener)
        enableEdgeToEdge()
        setContent {
            ShizukuTheme {
                MaterialPage(stringResource(R.string.home_app_management_title), onBack = { finish() }) {
                    AndroidFragment<AppsPageFragment>()
                }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }
}
