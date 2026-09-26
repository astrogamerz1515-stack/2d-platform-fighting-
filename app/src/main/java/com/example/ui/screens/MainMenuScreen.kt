package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.core.GameMode
import com.example.core.SceneManager
import com.example.core.SceneType
import com.example.localization.LocalizationManager
import com.example.localization.StringKey
import com.example.ui.DialogType
import com.example.ui.UIManager

@Composable
fun MainMenuScreen() {
    val loc = LocalizationManager.instance
    val gameManager = GameManager.instance
    val sceneManager = SceneManager.instance
    val uiManager = UIManager.instance

    val selectedChar by gameManager.selectedCharacter.collectAsState()
    val selectedStage by gameManager.selectedStage.collectAsState()
    val freeRamMb by sceneManager.availableMemoryMb.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF070B14), Color(0xFF0F172A), Color(0xFF1E1B4B))
                )
            )
            .testTag("main_menu_container")
    ) {
        // Top Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.2f),
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Text(
                        text = "BRAWL",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = loc.getString(StringKey.APP_NAME),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Free Memory Telemetry
                Text(
                    text = "${loc.getString(StringKey.MEMORY_FREE_MB)}: ${freeRamMb} MB",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 16.dp)
                )

                // Battle Records Button
                Button(
                    onClick = { uiManager.showDialog(DialogType.RECORDS) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .padding(end = 8.dp)
                        .testTag("menu_records_button")
                ) {
                    Text(loc.getString(StringKey.MENU_RECORDS), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Settings Button
                Button(
                    onClick = { uiManager.showDialog(DialogType.SETTINGS) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("menu_settings_button")
                ) {
                    Text(loc.getString(StringKey.MENU_SETTINGS), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Center Content Area: 2 Columns (Left: Mode Selection, Right: Match Configuration Preview)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 32.dp, end = 32.dp, top = 64.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Play Actions
            Column(
                modifier = Modifier.width(340.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Primary Action: Stock Brawl Match
                Button(
                    onClick = {
                        gameManager.setGameMode(GameMode.STOCK_BRAWL)
                        sceneManager.transitionTo(SceneType.IN_GAME)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("btn_play_stock_brawl")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = loc.getString(StringKey.MENU_PLAY),
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = loc.getString(StringKey.MODE_STOCK_BRAWL),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // Training Dojo
                Button(
                    onClick = {
                        gameManager.setGameMode(GameMode.TRAINING)
                        sceneManager.transitionTo(SceneType.IN_GAME)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(12.dp))
                        .testTag("btn_training_dojo")
                ) {
                    Text(
                        text = loc.getString(StringKey.MENU_TRAINING),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Choose Fighter & Arena Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { sceneManager.transitionTo(SceneType.CHARACTER_SELECT) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .border(1.dp, Color(0xFF475569), RoundedCornerShape(10.dp))
                            .testTag("btn_select_character")
                    ) {
                        Text(loc.getString(StringKey.MENU_CHARACTERS), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { sceneManager.transitionTo(SceneType.STAGE_SELECT) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .border(1.dp, Color(0xFF475569), RoundedCornerShape(10.dp))
                            .testTag("btn_select_stage")
                    ) {
                        Text(loc.getString(StringKey.MENU_STAGES), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Right Column: Match Configuration Showcase Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xCC0F172A)),
                modifier = Modifier
                    .width(360.dp)
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .testTag("match_preview_card")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "SELECTED LOADOUT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF38BDF8),
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Character Preview
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B))
                            .padding(12.dp)
                            .clickable { sceneManager.transitionTo(SceneType.CHARACTER_SELECT) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(selectedChar.primaryColorArgb)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚔", fontSize = 20.sp, color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = loc.getString(selectedChar.nameKey),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Speed: ${selectedChar.speedRating}/5 | Power: ${selectedChar.powerRating}/5",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Stage Preview
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B))
                            .padding(12.dp)
                            .clickable { sceneManager.transitionTo(SceneType.STAGE_SELECT) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(selectedStage.themeColorArgb))
                                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🏛", fontSize = 20.sp, color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = loc.getString(selectedStage.nameKey),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Standard Competitive Arena",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
