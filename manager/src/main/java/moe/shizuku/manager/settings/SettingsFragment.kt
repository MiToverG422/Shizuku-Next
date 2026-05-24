package moe.shizuku.manager.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.RectF
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.KEEP_START_ON_BOOT
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.app.ThemeHelper.KEY_USE_SYSTEM_COLOR
import moe.shizuku.manager.ktx.isComponentEnabled
import moe.shizuku.manager.ktx.setComponentEnabled
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.receiver.BootCompleteReceiver
import moe.shizuku.manager.service.KeepAliveNotificationHelper
import moe.shizuku.manager.service.KeepAliveWorker
import moe.shizuku.manager.utils.CustomTabsHelper
import rikka.material.app.LocaleDelegate
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.widget.borderview.BorderRecyclerView
import java.util.*
import moe.shizuku.manager.ShizukuSettings.LANGUAGE as KEY_LANGUAGE
import moe.shizuku.manager.ShizukuSettings.NIGHT_MODE as KEY_NIGHT_MODE

class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var languagePreference: ListPreference
    private lateinit var nightModePreference: IntegerSimpleMenuPreference
    private lateinit var startOnBootPreference: TwoStatePreference
    private lateinit var startupAccessibilityPreference: TwoStatePreference
    private lateinit var startupScriptPreference: TwoStatePreference
    private lateinit var autoRestartInBackgroundPreference: TwoStatePreference
    private lateinit var keepAlivePreference: TwoStatePreference
    private lateinit var translationPreference: Preference
    private lateinit var translationContributorsPreference: Preference
    private lateinit var useSystemColorPreference: TwoStatePreference
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()

        preferenceManager.setStorageDeviceProtected()
        preferenceManager.sharedPreferencesName = ShizukuSettings.NAME
        preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
        setPreferencesFromResource(R.xml.settings, null)

        languagePreference = findPreference(KEY_LANGUAGE)!!
        nightModePreference = findPreference(KEY_NIGHT_MODE)!!
        startOnBootPreference = findPreference(KEEP_START_ON_BOOT)!!
        startupAccessibilityPreference = findPreference("startup_accessibility")!!
        startupScriptPreference = findPreference("startup_script")!!
        autoRestartInBackgroundPreference = findPreference(ShizukuSettings.AUTO_RESTART_IN_BACKGROUND)!!
        keepAlivePreference = findPreference(ShizukuSettings.KEEP_ALIVE_ENABLED)!!
        translationPreference = findPreference("translation")!!
        translationContributorsPreference = findPreference("translation_contributors")!!
        useSystemColorPreference = findPreference(KEY_USE_SYSTEM_COLOR)!!

        val componentName = ComponentName(context.packageName, BootCompleteReceiver::class.java.name)

        fun syncStartupUi() {
            val mode = ShizukuSettings.getPreferences()
                .getString(ShizukuSettings.STARTUP_MODE, ShizukuSettings.StartupMode.BROADCAST)
                ?: ShizukuSettings.StartupMode.BROADCAST
            startOnBootPreference.isChecked = mode == ShizukuSettings.StartupMode.BROADCAST
            startupAccessibilityPreference.isChecked = mode == ShizukuSettings.StartupMode.ACCESSIBILITY
            startupScriptPreference.isChecked = mode == ShizukuSettings.StartupMode.SCRIPT
            context.packageManager.setComponentEnabled(componentName, true)
        }

        fun setStartupMode(mode: String) {
            ShizukuSettings.getPreferences().edit()
                .putString(ShizukuSettings.STARTUP_MODE, mode)
                .putBoolean(KEEP_START_ON_BOOT, true)
                .apply()
            syncStartupUi()
        }

        syncStartupUi()

        startOnBootPreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as? Boolean ?: return@OnPreferenceChangeListener false
                if (enabled) {
                    setStartupMode(ShizukuSettings.StartupMode.BROADCAST)
                } else if (startOnBootPreference.isChecked) {
                    ShizukuSettings.getPreferences().edit().putBoolean(KEEP_START_ON_BOOT, false).apply()
                    startOnBootPreference.isChecked = false
                }
                true
            }

        startupAccessibilityPreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as? Boolean ?: return@OnPreferenceChangeListener false
                if (enabled) {
                    setStartupMode(ShizukuSettings.StartupMode.ACCESSIBILITY)
                    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                    val enabledServices = accessibilityManager?.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                    ).orEmpty()
                    val selfServiceId = "${context.packageName}/${moe.shizuku.manager.receiver.StartupAccessibilityService::class.java.name}"
                    val isServiceEnabled = enabledServices.any { it.id == selfServiceId }
                    if (!isServiceEnabled) {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                } else if (startupAccessibilityPreference.isChecked) {
                    ShizukuSettings.getPreferences().edit().putBoolean(KEEP_START_ON_BOOT, false).apply()
                    startupAccessibilityPreference.isChecked = false
                }
                true
            }

        startupScriptPreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as? Boolean ?: return@OnPreferenceChangeListener false
                if (enabled) {
                    setStartupMode(ShizukuSettings.StartupMode.SCRIPT)
                } else if (startupScriptPreference.isChecked) {
                    ShizukuSettings.getPreferences().edit().putBoolean(KEEP_START_ON_BOOT, false).apply()
                    startupScriptPreference.isChecked = false
                }
                true
            }

        autoRestartInBackgroundPreference.isChecked = ShizukuSettings.getPreferences()
            .getBoolean(ShizukuSettings.AUTO_RESTART_IN_BACKGROUND, false)

        keepAlivePreference.isChecked = ShizukuSettings.getPreferences()
            .getBoolean(ShizukuSettings.KEEP_ALIVE_ENABLED, false)
        keepAlivePreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as? Boolean ?: return@OnPreferenceChangeListener false
                if (enabled) {
                    KeepAliveWorker.schedule(context)
                    KeepAliveWorker.runNow(context)
                } else {
                    KeepAliveWorker.cancel(context)
                    KeepAliveNotificationHelper.cancel(context)
                }
                true
            }

        languagePreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                if (newValue is String) {
                    val locale: Locale = if ("SYSTEM" == newValue) {
                        LocaleDelegate.systemLocale
                    } else {
                        Locale.forLanguageTag(newValue)
                    }
                    LocaleDelegate.defaultLocale = locale
                    activity?.recreate()
                }
                true
            }

        setupLocalePreference()

        nightModePreference.value = ShizukuSettings.getNightMode()
        nightModePreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, value: Any? ->
                if (value is Int) {
                    if (ShizukuSettings.getNightMode() != value) {
                        AppCompatDelegate.setDefaultNightMode(value)
                        activity?.recreate()
                    }
                }
                true
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            useSystemColorPreference.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, value: Any? ->
                    if (value is Boolean) {
                        if (ThemeHelper.isUsingSystemColor() != value) {
                            activity?.recreate()
                        }
                    }
                    true
                }
        } else {
            useSystemColorPreference.isVisible = false
        }

        translationPreference.summary =
            context.getString(R.string.settings_translation_summary, context.getString(R.string.app_name))
        translationPreference.setOnPreferenceClickListener {
            CustomTabsHelper.launchUrlOrCopy(context, context.getString(R.string.translation_url))
            true
        }

        val contributors = context.getString(R.string.translation_contributors).toHtml().toString()
        if (contributors.isNotBlank()) {
            translationContributorsPreference.summary = contributors
        } else {
            translationContributorsPreference.isVisible = false
        }
    }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ): RecyclerView {
        val recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState) as BorderRecyclerView
        recyclerView.fixEdgeEffect()
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null
        recyclerView.clipToPadding = false
        recyclerView.scrollToPosition(0)
        val horizontalMargin = recyclerView.context.resources
            .getDimension(R.dimen.rd_activity_horizontal_margin).toInt()
        recyclerView.setPadding(
            horizontalMargin,
            0,
            horizontalMargin,
            recyclerView.paddingBottom + (16 * recyclerView.resources.displayMetrics.density).toInt()
        )
        recyclerView.addEdgeSpacing(
            left = 16f,
            right = 16f,
            bottom = 8f,
            unit = TypedValue.COMPLEX_UNIT_DIP
        )
        recyclerView.addItemDecoration(SettingsCardDecoration())

        val lp = recyclerView.layoutParams
        if (lp is FrameLayout.LayoutParams) {
            lp.rightMargin = 0
            lp.leftMargin = 0
            lp.topMargin = 0
        }

        return recyclerView
    }

    private inner class SettingsCardDecoration : ItemDecoration() {
        private val itemVertical = (1f * resources.displayMetrics.density).toInt()
        private val categoryTop = (18f * resources.displayMetrics.density).toInt()
        private val categoryBottom = (8f * resources.displayMetrics.density).toInt()
        private val cornerRadius = 28f * resources.displayMetrics.density
        private val middleRadius = 2f * resources.displayMetrics.density
        private val cardColor = requireContext().getColor(R.color.home_card_background_color)
        private val rippleColor by lazy { resolveRippleColor() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardColor }
        private val rectF = RectF()

        override fun onDraw(c: android.graphics.Canvas, parent: RecyclerView, state: RecyclerView.State) {
            applyCardBackgrounds(c, parent)
        }

        override fun onDrawOver(c: android.graphics.Canvas, parent: RecyclerView, state: RecyclerView.State) {
            applyCardBackgrounds(c, parent)
        }

        private fun applyCardBackgrounds(c: android.graphics.Canvas, parent: RecyclerView) {
            val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val pos = parent.getChildAdapterPosition(child)
                if (pos == RecyclerView.NO_POSITION) continue
                val pref = adapter.getItem(pos)
                if (pref is PreferenceCategory) continue
                val prevIsCard = pos > 0 && adapter.getItem(pos - 1) !is PreferenceCategory
                val nextIsCard = pos < adapter.itemCount - 1 && adapter.getItem(pos + 1) !is PreferenceCategory
                val topRadius = if (prevIsCard) middleRadius else cornerRadius
                val bottomRadius = if (nextIsCard) middleRadius else cornerRadius
                val shapeCode = when {
                    !prevIsCard && !nextIsCard -> 0
                    !prevIsCard -> 1
                    !nextIsCard -> 2
                    else -> 3
                }
                rectF.set(
                    child.left.toFloat(),
                    child.top.toFloat(),
                    child.right.toFloat(),
                    child.bottom.toFloat()
                )
                val radii = floatArrayOf(
                    topRadius, topRadius,
                    topRadius, topRadius,
                    bottomRadius, bottomRadius,
                    bottomRadius, bottomRadius
                )
                val path = android.graphics.Path().apply { addRoundRect(rectF, radii, android.graphics.Path.Direction.CW) }
                c.drawPath(path, paint)
                // Keep ripple only as foreground interaction; transparent base avoids card disappearing after press.
                val needResetBackground = (child.getTag(R.id.tag_card_shape_code) as? Int) != shapeCode || child.background !is RippleDrawable
                if (needResetBackground) {
                    child.setTag(R.id.tag_card_shape_code, shapeCode)
                    child.background = createCardBackground(topRadius, bottomRadius, rippleColor, android.graphics.Color.TRANSPARENT)
                }
            }
        }

        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: android.view.View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val pos = parent.getChildAdapterPosition(view)
            if (pos == RecyclerView.NO_POSITION) return
            val pref = (parent.adapter as? PreferenceGroupAdapter)?.getItem(pos)
            if (pref is PreferenceCategory) {
                outRect.top = categoryTop
                outRect.bottom = categoryBottom
            } else {
                outRect.top = itemVertical
                outRect.bottom = itemVertical
            }
        }

        private fun resolveRippleColor(): Int {
            val out = TypedValue()
            requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorControlHighlight, out, true)
            return out.data
        }

        private fun createCardBackground(topRadius: Float, bottomRadius: Float, rippleColor: Int, cardColor: Int): RippleDrawable {
            val content = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    topRadius, topRadius,
                    topRadius, topRadius,
                    bottomRadius, bottomRadius,
                    bottomRadius, bottomRadius
                )
                setColor(cardColor)
            }
            return RippleDrawable(ColorStateList.valueOf(rippleColor), content, content.constantState?.newDrawable())
        }
    }

    private fun setupLocalePreference() {
        val localeTags = arrayOf("SYSTEM", "en", "zh-CN", "zh-TW", "zh-HK")
        val localeLabels = arrayOf(
            getString(R.string.follow_system),
            getString(R.string.language_english),
            getString(R.string.language_simplified_chinese),
            getString(R.string.language_traditional_chinese_taiwan),
            getString(R.string.language_traditional_chinese_hong_kong)
        )
        languagePreference.entryValues = localeTags
        languagePreference.entries = localeLabels

        val currentLocaleTag = languagePreference.value ?: "SYSTEM"
        val currentLocaleIndex = localeTags.indexOf(currentLocaleTag).takeIf { it >= 0 } ?: 0
        languagePreference.summary = localeLabels[currentLocaleIndex]
    }
}
