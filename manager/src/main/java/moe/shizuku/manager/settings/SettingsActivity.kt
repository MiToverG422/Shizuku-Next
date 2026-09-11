package moe.shizuku.manager.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource
import androidx.fragment.compose.AndroidFragment
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.component.MaterialPage
import moe.shizuku.manager.ui.theme.ShizukuTheme

class SettingsActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShizukuTheme {
                MaterialPage(stringResource(R.string.settings_title), onBack = { finish() }) {
                    AndroidFragment<SettingsFragment>()
                }
            }
        }
    }
}
