@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package top.redlnn.spectra.presentation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import top.redlnn.spectra.render.GraphColors
import top.redlnn.spectra.settings.*
import top.yukonga.miuix.kmp.theme.*
import top.yukonga.miuix.kmp.theme.darkColorScheme as miDark
import top.yukonga.miuix.kmp.theme.lightColorScheme as miLight

val LocalGraphColors = staticCompositionLocalOf { GraphColors() }
val LocalDarkMode = staticCompositionLocalOf { true }

@Composable
fun SpectraTheme(settings: AnalyzerSettings, content: @Composable () -> Unit) {
    val dark =
        when (settings.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
    val mode =
        when {
            settings.monet && dark -> ColorSchemeMode.MonetDark
            settings.monet -> ColorSchemeMode.MonetLight
            dark -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.Light
        }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = LocalResources.current
    var paletteRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    paletteRevision++
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    val seed =
        if (settings.monet && Build.VERSION.SDK_INT >= 31)
            remember(resources, configuration, paletteRevision) {
                Color(resources.getColor(android.R.color.system_accent1_500, context.theme))
            }
        else null
    val paletteStyle =
        when (settings.monetStyle) {
            MonetStyle.TONAL_SPOT -> ThemePaletteStyle.TonalSpot
            MonetStyle.NEUTRAL -> ThemePaletteStyle.Neutral
            MonetStyle.VIBRANT -> ThemePaletteStyle.Vibrant
            MonetStyle.EXPRESSIVE -> ThemePaletteStyle.Expressive
            MonetStyle.RAINBOW -> ThemePaletteStyle.Rainbow
            MonetStyle.FRUIT_SALAD -> ThemePaletteStyle.FruitSalad
            MonetStyle.MONOCHROME -> ThemePaletteStyle.Monochrome
            MonetStyle.FIDELITY -> ThemePaletteStyle.Fidelity
        }
    // Let Miuix generate the chosen palette from the system's wallpaper accent.
    val controller =
        remember(mode, seed, paletteStyle) {
            ThemeController(
                colorSchemeMode = mode,
                keyColor = seed,
                paletteStyle = paletteStyle,
                colorSpec =
                    if (Build.VERSION.SDK_INT >= 36) ThemeColorSpec.Spec2025
                    else ThemeColorSpec.Spec2021,
                lightColors =
                    miLight(
                        primary = Color(0xFF16745E),
                        primaryContainer = Color(0xFFB7F1DC),
                        onPrimaryContainer = Color(0xFF00382B),
                        background = Color(0xFFF1F4F8),
                        surface = Color.White,
                        surfaceContainer = Color.White,
                    ),
                darkColors =
                    miDark(
                        primary = Color(0xFF77E1C7),
                        onPrimary = Color(0xFF00382B),
                        primaryContainer = Color(0xFF24483F),
                        onPrimaryContainer = Color(0xFF77E1C7),
                        background = Color(0xFF0C1018),
                        surface = Color(0xFF080D15),
                        surfaceContainer = Color(0xFF171F2D),
                    ),
            )
        }
    MiuixTheme(controller = controller) {
        val c = MiuixTheme.colorScheme
        val scheme =
            if (dark) androidx.compose.material3.darkColorScheme()
            else androidx.compose.material3.lightColorScheme()
        val material =
            scheme.copy(
                primary = c.primary,
                onPrimary = c.onPrimary,
                primaryContainer = c.primaryContainer,
                onPrimaryContainer = c.onPrimaryContainer,
                secondary = c.primary,
                onSecondary = c.onPrimary,
                secondaryContainer = c.primaryContainer,
                onSecondaryContainer = c.onPrimaryContainer,
                background = c.background,
                onBackground = c.onBackground,
                surface = c.surface,
                onSurface = c.onSurface,
                surfaceVariant = c.surfaceVariant,
                onSurfaceVariant = c.onSurfaceSecondary,
                surfaceContainer = c.surfaceContainer,
                surfaceContainerHigh = c.surfaceContainerHigh,
                surfaceContainerHighest = c.surfaceContainerHighest,
                outline = c.outline,
                outlineVariant = c.dividerLine,
                error = c.error,
                onError = c.onError,
                errorContainer = c.errorContainer,
                onErrorContainer = c.onErrorContainer,
            )
        val live =
            if (settings.monet) c.primary
            else
                when (settings.spectrumPalette) {
                    SpectrumPalette.NIGHT -> if (dark) Color(0xFFFF6074) else Color(0xFFB10D31)
                    SpectrumPalette.YELLOW -> if (dark) Color(0xFFFFD44E) else Color(0xFF8C6500)
                    SpectrumPalette.GREEN -> if (dark) Color(0xFF58E59D) else Color(0xFF137C4D)
                    SpectrumPalette.CLASSIC -> if (dark) Color(0xFF5DB8FF) else Color(0xFF006BBB)
                }
        val liveEnd =
            if (settings.monet) live
            else
                when (settings.spectrumPalette) {
                    SpectrumPalette.CLASSIC -> if (dark) Color(0xFF6FE1B7) else Color(0xFF157B59)
                    SpectrumPalette.NIGHT -> if (dark) Color(0xFFFFD44E) else Color(0xFFA76800)
                    else -> live
                }
        val wave =
            if (settings.monet) c.primary
            else
                when (settings.waveColor) {
                    "Yellow" -> if (dark) Color(0xFFFFDB57) else Color(0xFF8C6500)
                    "Invert" -> c.onSurface
                    else -> if (dark) Color(0xFF53E6AE) else Color(0xFF14774F)
                }
        val graph =
            GraphColors(
                dark,
                c.surface.toArgb(),
                c.onSurface.toArgb(),
                c.onSurfaceSecondary.toArgb(),
                c.dividerLine.toArgb(),
                live.toArgb(),
                (if (settings.monet) c.onSurfaceSecondary
                    else if (settings.night) Color(0xFFBC365A) else c.onSurfaceSecondary)
                    .toArgb(),
                wave.toArgb(),
                (if (settings.monet) c.primary
                    else if (dark) Color(0xFF79D4FF) else Color(0xFF00658A))
                    .toArgb(),
                c.primaryContainer.toArgb(),
                c.onPrimaryContainer.toArgb(),
                liveEnd.toArgb(),
                (if (settings.monet) c.error
                    else if (dark) Color(0xFFFFBE68) else Color(0xFF964B00))
                    .toArgb(),
            )
        val view = LocalView.current
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        MaterialExpressiveTheme(colorScheme = material) {
            CompositionLocalProvider(
                LocalGraphColors provides graph,
                LocalDarkMode provides dark,
                content = content,
            )
        }
    }
}
