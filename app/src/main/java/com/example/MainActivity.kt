package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.core.GameManager
import com.example.core.SceneManager
import com.example.core.SceneType
import com.example.ui.DialogType
import com.example.ui.GameScreen
import com.example.ui.UIManager
import com.example.ui.screens.BootScreen
import com.example.ui.screens.CharacterSelectScreen
import com.example.ui.screens.MainMenuScreen
import com.example.ui.screens.MapLoadingScreen
import com.example.ui.screens.PostMatchScreen
import com.example.ui.screens.RecordsDialog
import com.example.ui.screens.SettingsDialog
import com.example.ui.screens.StageSelectScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on during fighting gameplay
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize Global GameManager and Tamper-Proof Save Engine
        GameManager.instance.initialize(applicationContext)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF070B14)
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val sceneManager = SceneManager.instance
    val uiManager = UIManager.instance

    val currentScene by sceneManager.currentScene.collectAsState()
    val activeDialog by uiManager.activeDialog.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Asynchronous Scene Layer with Crossfade transition
        Crossfade(
            targetState = currentScene,
            animationSpec = tween(180),
            label = "scene_crossfade"
        ) { scene ->
            when (scene) {
                SceneType.BOOT -> BootScreen()
                SceneType.MAIN_MENU -> MainMenuScreen()
                SceneType.CHARACTER_SELECT -> CharacterSelectScreen()
                SceneType.STAGE_SELECT -> StageSelectScreen()
                SceneType.MAP_LOADING -> MapLoadingScreen()
                SceneType.IN_GAME -> GameScreen()
                SceneType.POST_MATCH -> PostMatchScreen()
            }
        }

        // Global Modal Dialog Layer
        when (activeDialog) {
            DialogType.SETTINGS -> SettingsDialog(onDismiss = { uiManager.dismissDialog() })
            DialogType.RECORDS -> RecordsDialog(onDismiss = { uiManager.dismissDialog() })
            DialogType.CONFIRM_RESET_SAVE -> Unit
            DialogType.NONE -> Unit
        }
    }
}
