package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.core.SceneType
import com.example.localization.LocalizationManager
import com.example.localization.StringKey
import kotlin.math.roundToInt

@Composable
fun PostMatchScreen() {
    val loc = LocalizationManager.instance
    val gameManager = GameManager.instance
    val sceneManager = SceneManager.instance
    val result = gameManager.lastMatchResult

    val isVictory = result?.isVictory ?: true
    val primaryColor = if (isVictory) Color(0xFF10B981) else Color(0xFFEF4444)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF030712), Color(0xFF0F172A), Color(0xFF1E1B4B))
                )
            )
            .testTag("post_match_container"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEE0F172A)),
            modifier = Modifier
                .width(480.dp)
                .border(2.dp, primaryColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .padding(16.dp)
                .testTag("post_match_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Banner Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = primaryColor.copy(alpha = 0.2f),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = if (isVictory) loc.getString(StringKey.VICTORY) else loc.getString(StringKey.DEFEAT),
                        color = primaryColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                Text(
                    text = loc.getString(StringKey.MATCH_STATS),
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 18.dp)
                )

                // Stats Matrix
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatLine(
                        title = loc.getString(StringKey.TOTAL_KOS),
                        value = "${result?.totalKOs ?: 0}"
                    )
                    StatLine(
                        title = loc.getString(StringKey.TOTAL_DAMAGE_DEALT),
                        value = "${result?.totalDamageDealt?.roundToInt() ?: 0} HP"
                    )
                    StatLine(
                        title = loc.getString(StringKey.STOCKS_REMAINING),
                        value = "${result?.playerStocksRemaining ?: 0} Stocks"
                    )
                    StatLine(
                        title = loc.getString(StringKey.MATCH_DURATION),
                        value = "${(result?.matchDurationSeconds ?: 0f).roundToInt()}s"
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons (Rematch, Main Menu)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { sceneManager.transitionTo(SceneType.MAIN_MENU) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("btn_post_match_menu")
                    ) {
                        Text(loc.getString(StringKey.QUIT_TO_MENU), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            gameManager.startMatch()
                            sceneManager.transitionTo(SceneType.IN_GAME)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("btn_post_match_rematch")
                    ) {
                        Text(loc.getString(StringKey.REMATCH), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLine(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = Color(0xFFCBD5E1), fontSize = 12.sp)
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
