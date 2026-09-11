package moe.shizuku.manager.starter

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants.EXTRA
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import moe.shizuku.manager.app.AppActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ui.component.MaterialPage
import moe.shizuku.manager.ui.theme.ShizukuTheme
import rikka.lifecycle.Resource
import rikka.lifecycle.Status
import rikka.lifecycle.viewModels
import rikka.shizuku.Shizuku
import java.net.ConnectException
import javax.net.ssl.SSLProtocolException

private class NotRootedException : Exception()

class StarterActivity : AppActivity() {
    private var outputText by mutableStateOf("")
    private var errorMessage by mutableIntStateOf(0)

    private val viewModel by viewModels {
        ViewModel(
            this,
            intent.getBooleanExtra(EXTRA_IS_ROOT, true),
            intent.getStringExtra(EXTRA_HOST),
            intent.getIntExtra(EXTRA_PORT, 0)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            ShizukuTheme {
                MaterialPage(stringResource(R.string.home_root_button_start), { finish() }) {
                    SelectionContainer {
                        Text(outputText, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
                if (errorMessage != 0) AlertDialog(
                    onDismissRequest = { errorMessage = 0 },
                    text = { Text(stringResource(errorMessage)) },
                    confirmButton = { TextButton(onClick = { errorMessage = 0 }) { Text(stringResource(android.R.string.ok)) } },
                )
            }
        }

        viewModel.output.observe(this) {
            val output = it.data!!.trim()
            if (output.endsWith("info: shizuku_starter exit with 0")) {
                viewModel.appendOutput("")
                viewModel.appendOutput("Waiting for service...")

                Shizuku.addBinderReceivedListener(object : Shizuku.OnBinderReceivedListener {
                    override fun onBinderReceived() {
                        Shizuku.removeBinderReceivedListener(this)
                        viewModel.appendOutput("Service started, this window will be automatically closed in 3 seconds")

                        window?.decorView?.postDelayed({
                            if (!isFinishing) finish()
                        }, 3000)
                    }
                })
            } else if (it.status == Status.ERROR) {
                var message = 0
                when (it.error) {
                    is AdbKeyException -> {
                        message = R.string.adb_error_key_store
                    }
                    is NotRootedException -> {
                        message = R.string.start_with_root_failed
                    }
                    is ConnectException -> {
                        message = R.string.cannot_connect_port
                    }
                    is SSLProtocolException -> {
                        message = R.string.adb_pair_required
                    }
                }

                if (message != 0) {
                    errorMessage = message
                }
            }
            outputText = output.toString()
        }
    }

    companion object {

        const val EXTRA_IS_ROOT = "$EXTRA.IS_ROOT"
        const val EXTRA_HOST = "$EXTRA.HOST"
        const val EXTRA_PORT = "$EXTRA.PORT"
    }
}

private class ViewModel(context: Context, root: Boolean, host: String?, port: Int) : androidx.lifecycle.ViewModel() {

    private val sb = StringBuilder()
    private val _output = MutableLiveData<Resource<StringBuilder>>()

    val output = _output as LiveData<Resource<StringBuilder>>

    init {
        try {
            if (root) {
                startRoot()
            } else {
                startAdb(host!!, port)
            }
        } catch (e: Throwable) {
            postResult(e)
        }
    }

    fun appendOutput(line: String) {
        sb.appendLine(line)
        postResult()
    }

    private fun postResult(throwable: Throwable? = null) {
        if (throwable == null)
            _output.postValue(Resource.success(sb))
        else
            _output.postValue(Resource.error(throwable, sb))
    }

    private fun startRoot() {
        sb.append("Starting with root...").append('\n').append('\n')
        postResult()

        GlobalScope.launch(Dispatchers.IO) {
            if (!Shell.getShell().isRoot) {
                Shell.getCachedShell()?.close()
                sb.append('\n').append("Can't open root shell, try again...").append('\n')

                postResult()
                if (!Shell.getShell().isRoot) {
                    sb.append('\n').append("Still not :(").append('\n')
                    postResult(NotRootedException())
                    return@launch
                }
            }

            Shell.cmd(Starter.internalCommand).to(object : CallbackList<String?>() {
                override fun onAddElement(s: String?) {
                    sb.append(s).append('\n')
                    postResult()
                }
            }).submit {
                if (it.code == 0) {
                    ShizukuSettings.setLastLaunchMode(ShizukuSettings.LaunchMethod.ROOT)
                } else {
                    sb.append('\n').append("Send this to developer may help solve the problem.")
                    postResult()
                }
            }
        }
    }

    private fun startAdb(host: String, port: Int) {
        sb.append("Starting with wireless adb in port $port...").append('\n').append('\n')
        postResult()

        GlobalScope.launch(Dispatchers.IO) {
            val key = try {
                AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
            } catch (e: Throwable) {
                e.printStackTrace()
                sb.append('\n').append(Log.getStackTraceString(e))

                postResult(AdbKeyException(e))
                return@launch
            }

            AdbClient(host, port, key).runCatching {
                connect()
                shellCommand(Starter.internalCommand) {
                    sb.append(String(it))
                    postResult()
                }
                close()
                ShizukuSettings.setLastLaunchMode(ShizukuSettings.LaunchMethod.ADB)
            }.onFailure {
                it.printStackTrace()

                sb.append('\n').append(Log.getStackTraceString(it))
                postResult(it)
            }
        }
    }

}
