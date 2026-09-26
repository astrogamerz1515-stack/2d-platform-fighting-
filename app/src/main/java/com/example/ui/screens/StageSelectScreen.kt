package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.core.StageProfile
import com.example.localization.LocalizationManager
import com.example.localization.StringKey

@Composable
fun StageSelectScreen() {
    val loc = LocalizationManager.instance
    val gameManager = GameManager.instance
    val sceneManager = SceneManager.instance

    val activeSelectedStage by gameManager.selectedStage.collectAsState()
    var highlightedStage by remember { mutableStateOf(activeSelectedStage) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF070B14), Color(0xFF0F172A), Color(0xFF1E1B4B))
                )
            )
            .testTag("stage_select_container")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { sceneManager.transitionTo(SceneType.MAIN_MENU) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("btn_back_from_stage")
            ) {
                Text(loc.getString(StringKey.BACK), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                text = loc.getString(StringKey.MENU_STAGES),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.width(60.dp))
        }

        // 3 Stage Columns in a row
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, top = 64.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            for (stage in gameManager.stageCatalog) {
                val isSelected = stage.id == highlightedStage.id
                val isEquipped = stage.id == activeSelectedStage.id

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF1E293B) else Color(0xCC0F172A)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { highlightedStage = stage }
                        .testTag("stage_card_${stage.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            // Mini Arena Preview Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(stage.themeColorArgb))
                                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                // Simplified Platform Visualizer
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .width(70.dp)
                                            .height(4.dp)
                                            .background(Color(0xFF38BDF8))
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .height(10.dp)
                                            .background(Color(0xFF64748B), RoundedCornerShape(3.dp))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = loc.getString(stage.nameKey),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )

                            Text(
                                text = "Span: ${stage.stageWidth.toInt()}x${stage.stageHeight.toInt()} px",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            Text(
                                text = "Blast Zone: ±${stage.blastZoneX.toInt()} px",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // Select Button
                        Button(
                            onClick = {
                                gameManager.selectStage(stage.id)
                                sceneManager.transitionTo(SceneType.MAIN_MENU)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isEquipped) Color(0xFF10B981) else Color(0xFF0284C7)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("btn_select_stage_${stage.id}")
                        ) {
                            Text(
                                text = if (isEquipped) "EQUIPPED" else loc.getString(StringKey.SELECT),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}
