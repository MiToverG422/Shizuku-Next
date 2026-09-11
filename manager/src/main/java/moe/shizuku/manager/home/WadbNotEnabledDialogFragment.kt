package moe.shizuku.manager.home

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.component.MaterialDialogFragment

class WadbNotEnabledDialogFragment : MaterialDialogFragment() {
    @Composable override fun Content() {
        AlertDialog(onDismissRequest = { dismissAllowingStateLoss() },
            text = { Text(stringResource(R.string.dialog_wireless_adb_not_enabled)) },
            confirmButton = { TextButton(onClick = { dismissAllowingStateLoss() }) { Text(stringResource(android.R.string.ok)) } })
    }
}
