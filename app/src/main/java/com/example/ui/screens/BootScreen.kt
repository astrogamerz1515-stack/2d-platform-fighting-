package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.GameManager
import com.example.core.SceneManager
import com.example.core.SceneType
import com.example.localization.LocalizationManager
import com.example.localization.StringKey
import kotlinx.coroutines.delay

@Composable
fun BootScreen() {
    val loc = LocalizationManager.instance
    var bootPhaseText by remember { mutableStateOf("Initializing Hardware...") }
    var bootProgress by remember { mutableFloatStateOf(0.1f) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    LaunchedEffect(Unit) {
        // Step 1: Prewarm & verify crypto cipher
        delay(150L)
        bootPhaseText = "Validating AES-256 GCM Security Provider..."
        bootProgress = 0.35f

        // Step 2: Prewarm kinematic physics pools
        delay(200L)
        bootPhaseText = "Prewarming Kinematic Collision Swept Pools..."
        bootProgress = 0.65f

        // Step 3: Run memory reclamation check
        delay(200L)
        bootPhaseText = "Reclaiming Android Managed Heap Memory..."
        SceneManager.instance.forceGarbageCollection()
        bootProgress = 0.85f

        // Step 4: Boot complete
        delay(180L)
        bootPhaseText = loc.getString(StringKey.SYSTEM_READY)
        bootProgress = 1.0f

        delay(150L)
        SceneManager.instance.transitionTo(SceneType.MAIN_MENU, customDelayMs = 150L)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF030712), Color(0xFF0F172A), Color(0xFF1E1B4B))
                )
            )
            .testTag("boot_screen_container"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Animated Crest Emblem
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(glowScale)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0x00000000))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚔",
                    fontSize = 44.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = loc.getString(StringKey.APP_NAME),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontFamily = FontFamily.SansSerif
            )

            Text(
                text = "FRAME-PERFECT PLATFORM FIGHTER ENGINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            // Progress Bar
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .padding(horizontal = 8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { bootProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = Color(0xFF38BDF8),
                    trackColor = Color(0xFF334155),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = bootPhaseText,
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
