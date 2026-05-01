package moe.shizuku.manager.shell

import android.os.Bundle
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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

    private lateinit var input: TextInputEditText
    private lateinit var inputLayout: TextInputLayout
    private lateinit var output: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.quickshell_fragment, container, false)
        input = root.findViewById(R.id.command_input)
        inputLayout = root.findViewById(R.id.command_input_layout)
        output = root.findViewById(R.id.output_text)
        val monetColor = MaterialColors.getColor(inputLayout, com.google.android.material.R.attr.colorPrimary)
        inputLayout.setEndIconTintList(ColorStateList.valueOf(monetColor))

        inputLayout.setEndIconOnClickListener {
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) return@setEndIconOnClickListener

            if (interactiveProcess != null) {
                sendInteractiveInput(text)
                input.setText("")
                return@setEndIconOnClickListener
            }

            if (!Shizuku.pingBinder()) {
                Toast.makeText(requireContext(), R.string.quickshell_no_server, Toast.LENGTH_SHORT).show()
                return@setEndIconOnClickListener
            }

            val command = text
            if (isInteractiveShellCommand(command)) {
                input.setText("")
                startInteractiveSession(command)
                return@setEndIconOnClickListener
            }

            inputLayout.isEndIconVisible = false
            output.text = ""
            executor.execute {
                val result = runCommandWithLog(command)
                activity?.runOnUiThread {
                    output.text = result
                    inputLayout.isEndIconVisible = true
                }
            }
        }
        return root
    }

    override fun onDestroy() {
        stopInteractiveSession()
        super.onDestroy()
        executor.shutdownNow()
        interactiveIoExecutor.shutdownNow()
    }

    private fun startInteractiveSession(command: String) {
        output.text = ""
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
                    inputLayout.setEndIconDrawable(R.drawable.ic_filled_send_24)
                    inputLayout.setEndIconContentDescription(R.string.quickshell_send)
                    inputLayout.hint = getString(R.string.quickshell_inputs)
                    output.text = buildString {
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
                    inputLayout.setEndIconDrawable(R.drawable.ic_filled_play_arrow_24)
                    inputLayout.setEndIconContentDescription(R.string.quickshell_run)
                    inputLayout.hint = getString(R.string.quickshell_hint)
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
            if (::output.isInitialized) output.append(text)
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
