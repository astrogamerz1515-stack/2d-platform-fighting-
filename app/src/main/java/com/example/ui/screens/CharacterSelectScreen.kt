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
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.core.CharacterProfile
import com.example.core.GameManager
import com.example.core.SceneManager
import com.example.core.SceneType
import com.example.localization.LocalizationManager
import com.example.localization.StringKey

@Composable
fun CharacterSelectScreen() {
    val loc = LocalizationManager.instance
    val gameManager = GameManager.instance
    val sceneManager = SceneManager.instance

    val activeSelectedChar by gameManager.selectedCharacter.collectAsState()
    var highlightedChar by remember { mutableStateOf(activeSelectedChar) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF070B14), Color(0xFF0F172A), Color(0xFF1E1B4B))
                )
            )
            .testTag("character_select_container")
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
                    .testTag("btn_back_to_menu")
            ) {
                Text(loc.getString(StringKey.BACK), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                text = loc.getString(StringKey.MENU_CHARACTERS),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = 1.sp
            )

            // Placeholder to balance row
            Spacer(modifier = Modifier.width(60.dp))
        }

        // Main content: Left list of fighters, Right detailed preview
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, top = 60.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left: Fighter Selection Cards
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (char in gameManager.characterRoster) {
                    val isHighlighted = char.id == highlightedChar.id
                    val isEquipped = char.id == activeSelectedChar.id

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isHighlighted) Color(0xFF1E293B) else Color(0xFF0F172A)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isHighlighted) 2.dp else 1.dp,
                                color = if (isHighlighted) Color(char.primaryColorArgb) else Color(0xFF334155),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { highlightedChar = char }
                            .testTag("char_card_${char.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(char.primaryColorArgb)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⚔", fontSize = 22.sp, color = Color.White)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = loc.getString(char.nameKey),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Speed: ${char.speedRating} | Power: ${char.powerRating}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (isEquipped) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "EQUIPPED",
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right: Detailed Fighter Stat Card & Equip Action
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xCC0F172A)),
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .testTag("char_detail_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        // Title & Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = loc.getString(highlightedChar.nameKey),
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(highlightedChar.primaryColorArgb), RoundedCornerShape(4.dp))
                            )
                        }

                        Text(
                            text = loc.getString(highlightedChar.descriptionKey),
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                        )

                        // Stat Bars
                        StatRow(label = loc.getString(StringKey.STAT_SPEED), value = highlightedChar.speedRating, color = Color(0xFF38BDF8))
                        StatRow(label = loc.getString(StringKey.STAT_POWER), value = highlightedChar.powerRating, color = Color(0xFFF87171))
                        StatRow(label = loc.getString(StringKey.STAT_DEFENSE), value = highlightedChar.defenseRating, color = Color(0xFFFBBF24))
                        StatRow(label = loc.getString(StringKey.STAT_JUMP), value = highlightedChar.jumpRating, color = Color(0xFFA78BFA))
                    }

                    // Action Button
                    Button(
                        onClick = {
                            gameManager.selectCharacter(highlightedChar.id)
                            sceneManager.transitionTo(SceneType.MAIN_MENU)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(highlightedChar.primaryColorArgb)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_confirm_character")
                    ) {
                        Text(
                            text = loc.getString(StringKey.CONFIRM),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            modifier = Modifier.width(80.dp)
        )

        LinearProgressIndicator(
            progress = { value / 5f },
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
            color = color,
            trackColor = Color(0xFF334155)
        )

        Text(
            text = "$value/5",
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
