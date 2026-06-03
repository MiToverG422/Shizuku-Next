package moe.shizuku.manager.home

import android.content.Intent
import android.os.SystemClock
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeServerStatusBinding
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.ShizukuPidResolver
import rikka.html.text.HtmlCompat
import rikka.html.text.toHtml
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

class ServerStatusViewHolder(private val binding: HomeServerStatusBinding, root: View) :
    BaseViewHolder<ServiceStatus>(root) {

    companion object {
        private var runtimePid: Int = -1
        private var runtimeSinceElapsed: Long = 0L
        private var lastKnownPid: Int = -1

        val CREATOR = Creator<ServiceStatus> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeServerStatusBinding.inflate(inflater, outer.root, true)
            ServerStatusViewHolder(inner, outer.root)
        }
    }

    private inline val textView get() = binding.text1
    private inline val modeView get() = binding.text4
    private inline val summaryView get() = binding.text2
    private inline val runtimeView get() = binding.text3
    private var currentRunning = false
    private var currentPid = -1
    private val runtimeTicker = object : Runnable {
        override fun run() {
            runtimeView.text = buildRuntimeText(itemView.context, currentRunning, currentPid)
            if (itemView.isAttachedToWindow) {
                itemView.postDelayed(this, 1000L)
            }
        }
    }

    init {
        itemView.setOnClickListener { v ->
            v.context.startActivity(Intent(v.context, ActivationMethodsActivity::class.java))
        }
        itemView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                itemView.removeCallbacks(runtimeTicker)
                itemView.post(runtimeTicker)
            }

            override fun onViewDetachedFromWindow(v: View) {
                itemView.removeCallbacks(runtimeTicker)
            }
        })
    }

    override fun onBind() {
        val context = itemView.context
        val status = data
        val ok = status.isRunning
        val apiVersion = status.apiVersion
        val patchVersion = status.patchVersion

        (itemView as? MaterialCardView)?.setCardBackgroundColor(
            MaterialColors.getColor(
                itemView,
                if (ok) com.google.android.material.R.attr.colorPrimaryContainer
                else com.google.android.material.R.attr.colorErrorContainer
            )
        )

        val title = if (ok) {
            context.getString(R.string.home_status_running_simple)
        } else {
            context.getString(R.string.home_status_not_running_simple)
        }
        if (ok) {
            modeView.visibility = View.VISIBLE
            modeView.text = resolveModeText(context, status)
        } else {
            modeView.visibility = View.GONE
        }
        val version = if (ok && apiVersion != -1) {
            "${apiVersion}.${patchVersion}"
        } else {
            "-"
        }
        val processInfo = if (ok) ShizukuPidResolver.resolveProcessInfo(context) else null
        val detectedPid = processInfo?.pid ?: -1
        if (detectedPid > 0) {
            lastKnownPid = detectedPid
        }
        val pidValue = when {
            !ok -> -1
            detectedPid > 0 -> detectedPid
            lastKnownPid > 0 -> lastKnownPid
            else -> -1
        }
        val pid = if (pidValue > 0) pidValue.toString() else "-"
        val summary = context.getString(R.string.home_status_version_pid_format, version, pid)
        currentRunning = ok
        currentPid = pidValue
        if (ok && pidValue > 0) {
            val runtimeSec = processInfo?.runtimeSeconds
            if (runtimeSec != null && runtimeSec >= 0) {
                runtimePid = pidValue
                runtimeSinceElapsed = SystemClock.elapsedRealtime() - runtimeSec * 1000L
            }
        }
        val runtime = buildRuntimeText(context, currentRunning, currentPid)
        textView.text = title.toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE)
        summaryView.text = summary.toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE)
        runtimeView.text = runtime
        itemView.removeCallbacks(runtimeTicker)
        itemView.post(runtimeTicker)
        if (TextUtils.isEmpty(summaryView.text)) {
            summaryView.visibility = View.GONE
        } else {
            summaryView.visibility = View.VISIBLE
        }
    }

    private fun resolveModeText(context: android.content.Context, status: ServiceStatus): String {
        val lastLaunchMode = ShizukuSettings.getLastLaunchMode()
        return when {
            status.uid == 2000 -> context.getString(R.string.home_status_mode_adb)
            lastLaunchMode == ShizukuSettings.LaunchMethod.ROOT -> context.getString(R.string.home_status_mode_root)
            lastLaunchMode == ShizukuSettings.LaunchMethod.ADB -> context.getString(R.string.home_status_mode_adb)
            status.uid == 0 -> context.getString(R.string.home_status_mode_root)
            else -> context.getString(R.string.home_status_mode_unknown)
        }
    }

    private fun buildRuntimeText(context: android.content.Context, running: Boolean, pid: Int): String {
        if (!running) {
            runtimePid = -1
            runtimeSinceElapsed = 0L
            return context.getString(R.string.home_status_runtime_format, "--:--:--")
        }
        if (pid <= 0) {
            return context.getString(R.string.home_status_runtime_format, "--:--:--")
        }

        val now = SystemClock.elapsedRealtime()
        if (pid > 0 && runtimePid > 0 && runtimePid != pid) {
            runtimePid = pid
            runtimeSinceElapsed = now
        } else if (runtimePid <= 0) {
            runtimePid = pid
            runtimeSinceElapsed = now
        }

        val elapsed = (now - runtimeSinceElapsed).coerceAtLeast(0L) / 1000L
        val h = elapsed / 3600L
        val m = (elapsed % 3600L) / 60L
        val s = elapsed % 60L
        val formatted = String.format("%02d:%02d:%02d", h, m, s)
        return context.getString(R.string.home_status_runtime_format, formatted)
    }
}
