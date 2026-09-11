package moe.shizuku.manager.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.*
import moe.shizuku.manager.ui.component.MaterialDialogFragment
import rikka.lifecycle.viewModels
import java.net.ConnectException

@RequiresApi(VERSION_CODES.R)
class AdbPairDialogFragment : MaterialDialogFragment() {
    private val viewModel by viewModels { ViewModel(requireContext()) }
    private var errorMessage by mutableIntStateOf(0)
    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.result.observe(this) {
            busy = false
            if (it == null) dismissAllowingStateLoss()
            else errorMessage = when (it) {
                is ConnectException -> R.string.cannot_connect_port
                is AdbInvalidPairingCodeException -> R.string.paring_code_is_wrong
                else -> R.string.adb_error_key_store
            }
        }
    }

    @Composable override fun Content() {
        val discovered by viewModel.port.observeAsState(-1)
        var code by rememberSaveable { mutableStateOf("") }
        var portText by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(discovered) { if (discovered in 1..65535) portText = discovered.toString() }
        val discovery = discovered !in 1..65535
        val multiWindow = requireActivity().isInMultiWindowMode ||
            (requireActivity().window.decorView.display?.displayId ?: 0) > 0
        AlertDialog(
            onDismissRequest = { dismissAllowingStateLoss() },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(if (discovery) R.string.dialog_adb_pairing_discovery else R.string.dialog_adb_pairing_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    if (discovery) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.dialog_adb_pairing_message))
                        if (!multiWindow) {
                            Text(stringResource(R.string.adb_pairing_requires_multi_window))
                            Text(stringResource(R.string.adb_pairing_requires_multi_window_reason))
                        }
                    } else {
                        OutlinedTextField(code, {
                            code = it.filter(Char::isDigit).take(6)
                            errorMessage = 0
                        }, label = { Text(stringResource(R.string.dialog_adb_pairing_paring_code)) },
                            singleLine = true, enabled = !busy, isError = errorMessage == R.string.paring_code_is_wrong,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                        OutlinedTextField(portText, { portText = it; errorMessage = 0 },
                            label = { Text(stringResource(R.string.dialog_adb_port)) }, singleLine = true, enabled = !busy,
                            isError = errorMessage == R.string.dialog_adb_invalid_port || errorMessage == R.string.cannot_connect_port,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (errorMessage != 0) Text(stringResource(errorMessage), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                if (discovery) TextButton(onClick = {
                    runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                    }) }
                }) { Text(stringResource(R.string.development_settings)) }
                else TextButton(enabled = !busy && code.length == 6, onClick = {
                    val port = portText.toIntOrNull()
                    if (port == null || port !in 1..65535) errorMessage = R.string.dialog_adb_invalid_port
                    else {
                        errorMessage = 0
                        busy = true
                        viewModel.run(port, code)
                    }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { dismissAllowingStateLoss() }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@SuppressLint("NewApi")
private class ViewModel(context: Context) : androidx.lifecycle.ViewModel() {

    private val _result = MutableLiveData<Throwable?>()
    val result = _result as LiveData<Throwable?>

    private val _port = MutableLiveData<Int>()
    val port = _port as LiveData<Int>

    private val adbMdns: AdbMdns = AdbMdns(context, AdbMdns.TLS_PAIRING) {
        _port.postValue(it)
    }

    init {
        adbMdns.start()
    }

    fun run(port: Int, password: String) {
        GlobalScope.launch(Dispatchers.IO) {
            val host = "127.0.0.1"

            val key = try {
                AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
            } catch (e: Throwable) {
                e.printStackTrace()
                _result.postValue(AdbKeyException(e))
                return@launch
            }

            AdbPairingClient(host, port, password, key).runCatching {
                start()
            }.onFailure {
                _result.postValue(it)
                it.printStackTrace()
            }.onSuccess {
                if (it) {
                    _result.postValue(null)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        adbMdns.stop()
    }
}
