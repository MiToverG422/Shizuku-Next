package moe.shizuku.manager.ui.theme

import android.content.SharedPreferences
import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.ThemeHelper

internal val DefaultMaterialColorSpec = ColorSpec.SpecVersion.SPEC_2025

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShizukuTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = ShizukuSettings.getPreferences()
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == ShizukuSettings.NIGHT_MODE ||
                key == ThemeHelper.KEY_BLACK_NIGHT_THEME || key == ThemeHelper.KEY_USE_SYSTEM_COLOR) revision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val settings = remember(revision) { prefs.all }
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings[ShizukuSettings.NIGHT_MODE] as? Int) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    val amoled = dark && ThemeHelper.isBlackNightTheme(context)
    val dynamic = settings[ThemeHelper.KEY_USE_SYSTEM_COLOR] as? Boolean ?: true
    LaunchedEffect(context, dark) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val configuration = LocalConfiguration.current
    val base = remember(context, configuration, dynamic, dark) {
        when {
            dynamic && Build.VERSION.SDK_INT >= 31 ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> darkColorScheme()
            else -> expressiveLightColorScheme()
        }
    }
    val colors = rememberDynamicColorScheme(
        seedColor = base.primary,
        isDark = dark,
        isAmoled = amoled,
        style = PaletteStyle.TonalSpot,
        specVersion = DefaultMaterialColorSpec,
    ).let {
        if (!amoled) it else it.copy(
            background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color.Black,
            surfaceContainer = Color.Black, surfaceContainerHigh = Color.Black,
            surfaceContainerHighest = Color.Black,
        )
    }
    MaterialExpressiveTheme(
        colorScheme = colors.animated(),
        motionScheme = MotionScheme.expressive(),
        typography = ShizukuTypography,
        content = content,
    )
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable fun color(value: Color) =
        animateColorAsState(value, spring(), label = "palette").value
    return copy(
        primary = color(primary),
        onPrimary = color(onPrimary),
        primaryContainer = color(primaryContainer),
        onPrimaryContainer = color(onPrimaryContainer),
        inversePrimary = color(inversePrimary),
        secondary = color(secondary),
        onSecondary = color(onSecondary),
        secondaryContainer = color(secondaryContainer),
        onSecondaryContainer = color(onSecondaryContainer),
        tertiary = color(tertiary),
        onTertiary = color(onTertiary),
        tertiaryContainer = color(tertiaryContainer),
        onTertiaryContainer = color(onTertiaryContainer),
        background = color(background),
        onBackground = color(onBackground),
        surface = color(surface),
        onSurface = color(onSurface),
        surfaceVariant = color(surfaceVariant),
        onSurfaceVariant = color(onSurfaceVariant),
        surfaceTint = color(surfaceTint),
        inverseSurface = color(inverseSurface),
        inverseOnSurface = color(inverseOnSurface),
        error = color(error),
        onError = color(onError),
        errorContainer = color(errorContainer),
        onErrorContainer = color(onErrorContainer),
        outline = color(outline),
        outlineVariant = color(outlineVariant),
        scrim = color(scrim),
        surfaceBright = color(surfaceBright),
        surfaceDim = color(surfaceDim),
        surfaceContainer = color(surfaceContainer),
        surfaceContainerHigh = color(surfaceContainerHigh),
        surfaceContainerHighest = color(surfaceContainerHighest),
        surfaceContainerLow = color(surfaceContainerLow),
        surfaceContainerLowest = color(surfaceContainerLowest),
        primaryFixed = color(primaryFixed),
        primaryFixedDim = color(primaryFixedDim),
        onPrimaryFixed = color(onPrimaryFixed),
        onPrimaryFixedVariant = color(onPrimaryFixedVariant),
        secondaryFixed = color(secondaryFixed),
        secondaryFixedDim = color(secondaryFixedDim),
        onSecondaryFixed = color(onSecondaryFixed),
        onSecondaryFixedVariant = color(onSecondaryFixedVariant),
        tertiaryFixed = color(tertiaryFixed),
        tertiaryFixedDim = color(tertiaryFixedDim),
        onTertiaryFixed = color(onTertiaryFixed),
        onTertiaryFixedVariant = color(onTertiaryFixedVariant),
    )
}
