package moe.shizuku.manager.shell
import moe.shizuku.manager.ui.component.pageNestedScroll

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ui.theme.ShizukuTheme
import androidx.fragment.app.Fragment
import moe.shizuku.manager.R
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class QuickShellFragment : Fragment() {

    private val executor = Executors.newSingleThreadExecutor()
    private val interactiveIoExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var interactiveProcess: java.lang.Process? = null

    @Volatile
    private var interactiveWriter: BufferedWriter? = null

    private var commandText by mutableStateOf("")
    private var outputText by mutableStateOf("")
    private var commandRunning by mutableStateOf(false)
    private var interactive by mutableStateOf(false)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { ShizukuTheme { TerminalScreen() } }
        }

    @Composable
    private fun TerminalScreen() {
        Column(Modifier.fillMaxSize().pageNestedScroll().padding(horizontal = 16.dp)
            .padding(bottom = moe.shizuku.manager.ui.component.LocalPageBottomPadding.current),
            verticalArrangement = Arrangement.spacedBy(13.dp)) {
            OutlinedTextField(
                value = commandText, onValueChange = { commandText = it },
                label = { Text(stringResource(if (interactive) R.string.quickshell_inputs else R.string.quickshell_hint)) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                minLines = 1, maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                trailingIcon = {
                    IconButton(enabled = !commandRunning && commandText.isNotBlank(), onClick = { submitCommand() }) {
                        if (commandRunning) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Icon(painterResource(if (interactive) R.drawable.ic_filled_send_24 else R.drawable.ic_filled_play_arrow_24),
                            stringResource(if (interactive) R.string.quickshell_send else R.string.quickshell_run))
                    }
                })
            Surface(Modifier.weight(1f).fillMaxWidth(), shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceBright) {
                SelectionContainer {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text(outputText.ifEmpty { getString(R.string.quickshell_hint) },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (outputText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    private fun submitCommand() {
        val text = commandText.trim()
        if (text.isBlank() || commandRunning) return
        if (interactiveProcess != null) {
            sendInteractiveInput(text)
            commandText = ""
            return
        }
        if (!Shizuku.pingBinder()) {
            Toast.makeText(requireContext(), R.string.quickshell_no_server, Toast.LENGTH_SHORT).show()
            return
        }
        if (isInteractiveShellCommand(text)) {
            commandText = ""
            startInteractiveSession(text)
            return
        }
        commandRunning = true
        outputText = ""
        executor.execute {
            val result = runCommandWithLog(text)
            activity?.runOnUiThread {
                outputText = result
                commandRunning = false
            }
        }
    }

    override fun onDestroy() {
        stopInteractiveSession()
        super.onDestroy()
        executor.shutdownNow()
        interactiveIoExecutor.shutdownNow()
    }

    private fun startInteractiveSession(command: String) {
        outputText = ""
        Thread({
            try {
                val process = startProcess(arrayOf(command)) ?: run {
                    appendOutput("Failed to start process.\n")
                    return@Thread
                }
                val pid = getPid(process)
                interactiveProcess = process
                interactiveWriter = BufferedWriter(OutputStreamWriter(process.outputStream))
                activity?.runOnUiThread {
                    interactive = true
                    outputText = buildString {
                        append("[command] ").append(command).append('\n')
                        append("[start] pid=").append(if (pid > 0) pid else "unknown").append('\n')
                        append("[input] interactive session started\n")
                    }
                }
                startStreamReaders(process)
                val exitCode = process.waitFor()
                appendOutput("\n[exit] code=$exitCode\n")
            } catch (t: Throwable) {
                appendOutput("\n[error] ${t.message ?: t.javaClass.simpleName}\n[exit] code=-1\n")
            } finally {
                activity?.runOnUiThread {
                    interactive = false
                }
                stopInteractiveSession()
            }
        }, "quickshell-interactive-session").start()
    }

    private fun sendInteractiveInput(line: String) {
        interactiveIoExecutor.execute {
            val writer = interactiveWriter ?: return@execute
            try {
                writer.write(line)
                writer.newLine()
                writer.flush()
                appendOutput("[input] $line\n")
            } catch (_: Throwable) {
                appendOutput("[input] failed to send\n")
                stopInteractiveSession()
            }
        }
    }

    private fun startStreamReaders(process: java.lang.Process) {
        val outReader = Runnable {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).forEachLine { line ->
                    appendOutput("$line\n")
                }
            } catch (_: Throwable) {
            }
        }
        val errReader = Runnable {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).forEachLine { line ->
                    appendOutput("$line\n")
                }
            } catch (_: Throwable) {
            }
        }
        Thread(outReader, "quickshell-stdout").start()
        Thread(errReader, "quickshell-stderr").start()
    }

    private fun appendOutput(text: String) {
        activity?.runOnUiThread {
            outputText += text
        }
    }

    private fun stopInteractiveSession() {
        runCatching { interactiveWriter?.close() }
        interactiveWriter = null
        runCatching { interactiveProcess?.destroy() }
        interactiveProcess = null
    }

    private fun startProcess(argv: Array<String>): java.lang.Process? {
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val m = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            m.isAccessible = true
            m.invoke(null, argv, null, null) as? java.lang.Process
        } catch (_: Throwable) {
            null
        }
    }

    private fun getPid(process: java.lang.Process): Long {
        return runCatching {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            (field.get(process) as? Int)?.toLong() ?: -1L
        }.getOrDefault(-1L)
    }

    private fun runCommandWithLog(command: String): String {
        return try {
            val process = startProcess(arrayOf("sh", "-c", command)) ?: return "Failed to start process.\n"
            val pid = getPid(process)
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                runCatching { process.destroy() }
                runCatching { process.destroyForcibly() }
                return buildString {
                    append("[command] ").append(command).append('\n')
                    if (pid > 0) append("[start] pid=").append(pid).append('\n')
                    append("[timeout] exceeded 15s\n")
                    append("[exit] code=-2")
                }
            }
            val exitCode = runCatching { process.exitValue() }.getOrDefault(-1)
            runCatching { process.destroy() }
            buildString {
                append("[command] ").append(command).append('\n')
                if (pid > 0) {
                    append("[start] pid=").append(pid).append('\n')
                } else {
                    append("[start] pid=unknown").append('\n')
                }

                val merged = buildString {
                    if (stdout.isNotBlank()) append(stdout.trimEnd())
                    if (stderr.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(stderr.trimEnd())
                    }
                }
                if (merged.isNotBlank()) {
                    append(merged).append('\n')
                }
                append(String.format(Locale.US, "[exit] code=%d", exitCode))
            }
        } catch (t: Throwable) {
            buildString {
                append("[command] ").append(command).append('\n')
                append("[start] failed\n")
                append(t.message ?: t.javaClass.simpleName).append('\n')
                append("[exit] code=-1")
            }
        }
    }

    private fun isInteractiveShellCommand(command: String): Boolean {
        val c = command.trim()
        return c == "su" || c == "sh" || c == "bash"
    }
}
