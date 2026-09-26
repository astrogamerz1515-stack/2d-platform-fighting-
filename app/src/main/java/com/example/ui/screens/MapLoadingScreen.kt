package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.GameManager
import com.example.core.SceneManager
import com.example.localization.LocalizationManager
import com.example.localization.StringKey

@Composable
fun MapLoadingScreen() {
    val loc = LocalizationManager.instance
    val sceneManager = SceneManager.instance
    val gameManager = GameManager.instance

    val progress by sceneManager.loadingProgress.collectAsState()
    val statusText by sceneManager.loadingStatusText.collectAsState()
    val availableRamMb by sceneManager.availableMemoryMb.collectAsState()
    val selectedStage by gameManager.selectedStage.collectAsState()
    val selectedChar by gameManager.selectedCharacter.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF030712), Color(0xFF0F172A), Color(0xFF172554))
                )
            )
            .testTag("map_loading_container"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0284C7).copy(alpha = 0.25f),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = "INITIALIZING COMPETITIVE MATCH",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Text(
                text = loc.getString(selectedStage.nameKey),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp
            )

            Text(
                text = "Fighter: ${loc.getString(selectedChar.nameKey)}",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            // Progress bar
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .padding(horizontal = 12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Color(0xFF38BDF8),
                    trackColor = Color(0xFF1E293B)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = statusText,
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${loc.getString(StringKey.MEMORY_FREE_MB)}: $availableRamMb MB (Garbage Collector Active)",
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
