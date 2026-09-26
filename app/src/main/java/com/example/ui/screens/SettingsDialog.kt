package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.GameManager
import com.example.localization.LocalizationManager
import com.example.localization.StringKey
import com.example.localization.SupportedLanguage
import com.example.ui.UIManager
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val loc = LocalizationManager.instance
    val gameManager = GameManager.instance
    val uiManager = UIManager.instance

    var sfxVol by remember { mutableFloatStateOf(gameManager.saveData.sfxVolume) }
    var musicVol by remember { mutableFloatStateOf(gameManager.saveData.musicVolume) }
    var vibration by remember { mutableStateOf(gameManager.saveData.vibrationEnabled) }
    var currentLang by remember { mutableStateOf(loc.getLanguage()) }
    var resetConfirmMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF00F172A)),
            modifier = Modifier
                .width(520.dp)
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .testTag("settings_dialog_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = loc.getString(StringKey.SETTINGS_TITLE),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("✕", fontSize = 12.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Audio Sliders
                Text(
                    text = loc.getString(StringKey.SETTINGS_AUDIO),
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // SFX Volume
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${loc.getString(StringKey.SETTINGS_SFX)} (${(sfxVol * 100).roundToInt()}%)",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        modifier = Modifier.width(130.dp)
                    )
                    Slider(
                        value = sfxVol,
                        onValueChange = {
                            sfxVol = it
                            gameManager.saveData.sfxVolume = it
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF0284C7),
                            inactiveTrackColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Music Volume
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${loc.getString(StringKey.SETTINGS_MUSIC)} (${(musicVol * 100).roundToInt()}%)",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        modifier = Modifier.width(130.dp)
                    )
                    Slider(
                        value = musicVol,
                        onValueChange = {
                            musicVol = it
                            gameManager.saveData.musicVolume = it
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF0284C7),
                            inactiveTrackColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Language Selector
                Text(
                    text = loc.getString(StringKey.SETTINGS_LANGUAGE),
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (lang in SupportedLanguage.values()) {
                        val isCurrent = lang == currentLang
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isCurrent) Color(0xFF0284C7) else Color(0xFF1E293B),
                            modifier = Modifier
                                .border(1.dp, if (isCurrent) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(6.dp))
                        ) {
                            Text(
                                text = lang.displayName,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                    .testTag("lang_btn_${lang.code}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tamper-Proof Save Management
                Text(
                    text = loc.getString(StringKey.SETTINGS_DATA_MANAGEMENT),
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = loc.getString(StringKey.SAVE_STATUS_SECURE),
                            color = Color(0xFF34D399),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Button(
                            onClick = {
                                gameManager.resetPersistentData()
                                resetConfirmMsg = loc.getString(StringKey.SAVE_RESET_CONFIRM)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("btn_reset_save_data")
                        ) {
                            Text(loc.getString(StringKey.SAVE_RESET_BUTTON), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (resetConfirmMsg != null) {
                    Text(
                        text = resetConfirmMsg!!,
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
