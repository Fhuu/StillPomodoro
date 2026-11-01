package com.pomodoro.sound

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.util.function.Consumer
import com.pomodoro.timer.ui.DarkScheme

class SoundPanelActivity : ComponentActivity() {
    private var blurListener: Consumer<Boolean>? = null
    private var blurAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateBackground()
        setContent {
            var cyberpunk by remember { mutableStateOf(QuickSoundsStore.cyberpunkPanel(this)) }
            var blurred by remember { mutableStateOf(QuickSoundsStore.blurredPanel(this)) }
            var selected by remember {
                mutableStateOf(if (QuickSoundsService.isPlaying) QuickSoundsStore.selection(this) else emptySet())
            }
            LaunchedEffect(QuickSoundsService.isPlaying) {
                if (!QuickSoundsService.isPlaying) selected = emptySet()
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                cyberpunk = QuickSoundsStore.cyberpunkPanel(this)
                blurred = QuickSoundsStore.blurredPanel(this)
                selected = if (QuickSoundsService.isPlaying) QuickSoundsStore.selection(this) else emptySet()
                updateBackground()
            }
            val dark = isSystemInDarkTheme()
            val colors = when {
                cyberpunk -> cyberpunkColors(dark)
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(this)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (blurred) Color.Transparent else colors.background,
                    contentColor = colors.onBackground,
                ) {
                    Box(
                        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (cyberpunk) {
                                Text("SOUND / MIXER", color = colors.tertiary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
                            }
                            Text("Ambient sounds", style = MaterialTheme.typography.headlineMedium)
                            Text("Tap a sound to play. Turn all off to stop.",
                                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            BuiltInAmbientSection(selected = selected, onSelectionChange = {
                                selected = it
                                QuickSoundsService.selectSounds(this@SoundPanelActivity, it)
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 31 && QuickSoundsStore.blurredPanel(this)) {
            val listener = Consumer<Boolean> { enabled ->
                blurAvailable = enabled
                updateBackground()
            }
            blurListener = listener
            windowManager.addCrossWindowBlurEnabledListener(mainExecutor, listener)
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= 31) {
            blurListener?.let { windowManager.removeCrossWindowBlurEnabledListener(it) }
            blurListener = null
        }
        blurAvailable = false
        super.onStop()
    }

    private fun updateBackground() {
        val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val cyberpunk = QuickSoundsStore.cyberpunkPanel(this)
        val background = if (cyberpunk) cyberpunkColors(dark).background
            else if (dark) Color(0xFF141418) else Color(0xFFF8F8FC)
        val blur = QuickSoundsStore.blurredPanel(this) && blurAvailable
        window.setBackgroundDrawable(ColorDrawable(background.toArgb()).apply {
            alpha = if (blur) 175 else 255
        })
        window.setDimAmount(if (blur) 0.18f else 0f)
        if (Build.VERSION.SDK_INT >= 31) {
            if (blur) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = if (blur) (32 * resources.displayMetrics.density).toInt() else 0
            }
        }
    }
}

private fun cyberpunkColors(dark: Boolean): ColorScheme = if (dark) {
    DarkScheme.copy(
        secondaryContainer = DarkScheme.tertiaryContainer,
        onSecondaryContainer = DarkScheme.onTertiaryContainer,
    )
} else {
    lightColorScheme(
        primary = Color(0xFF1659CB), onPrimary = Color.White,
        secondary = Color(0xFF1659CB),
        tertiary = Color(0xFF9C450C), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDCC6), onTertiaryContainer = Color(0xFF542600),
        background = Color(0xFFF5F7FC), onBackground = Color(0xFF101C33),
        surface = Color.White, onSurface = Color(0xFF101C33),
        onSurfaceVariant = Color(0xFF4F607B), outline = Color(0xFF778BAA),
        secondaryContainer = Color(0xFFFFDCC6), onSecondaryContainer = Color(0xFF542600),
    )
}
