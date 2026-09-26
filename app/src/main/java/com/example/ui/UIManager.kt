package com.example.ui

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * Types of modal overlay dialogs supported by the game engine.
 */
enum class DialogType {
    NONE,
    SETTINGS,
    RECORDS,
    CONFIRM_RESET_SAVE
}

/**
 * Ephemeral floating notifications for achievements, unlocks, or network/save warnings.
 */
data class ToastNotification(
    val message: String,
    val durationFrames: Int = 180,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Central UIManager & Screen Stack Layer Controller.
 * Decoupled observer-driven controller coordinating modal overlays,
 * in-game HUD layers, and global notifications without polling.
 *
 * Embeds explicit production HEX color codes for pixel-perfect visual styling
 * across all fighting game menus, HUD counters, buttons, and state selections.
 */
class UIManager private constructor() {

    // =========================================================================
    // EXPLICIT PRODUCTION HEX COLOR CONSTANTS
    // =========================================================================
    companion object {
        val instance: UIManager by lazy { UIManager() }

        // Primary & Action Colors
        const val HEX_PRIMARY_ACTION = "#FF4500"      // Vivid Flame Orange (Play / Confirm / Primary Buttons)
        const val HEX_ACTIVE_SELECTION = "#00FFCC"    // Electric Cyan (Active fighter / stage highlight)
        const val HEX_SECONDARY_ACTION = "#7C4DFF"   // Deep Indigo / Secondary interactive triggers

        // Dark Canvas & Surface Theme
        const val HEX_BACKGROUND_DARK = "#070B14"     // Void Abyss Deep Black
        const val HEX_SURFACE_CARD = "#121A2E"        // Dark Steel Blue Surface
        const val HEX_SURFACE_ELEVATED = "#1A2642"    // Elevated Card Container
        const val HEX_CARD_BORDER = "#253554"         // Metallic Outline
        const val HEX_CARD_BORDER_ACTIVE = "#00FFCC"  // Active Selected Border Glow

        // Accents & Status Indicators
        const val HEX_ACCENT_GOLD = "#FFD700"         // Champion Gold (Records, High scores, KOs)
        const val HEX_DANGER_RED = "#FF1744"          // Critical Red (Reset Save / Knockout Blast)
        const val HEX_SUCCESS_GREEN = "#00E676"       // Victory / Save Verified Green
        const val HEX_NEON_BLUE = "#00E5FF"           // Energy Meter / Cyber Monk Glow
        const val HEX_NEON_PURPLE = "#B388FF"         // Shadow Ninja Void Trail

        // Text & Telemetry Contrast
        const val HEX_TEXT_PRIMARY = "#FFFFFF"        // High-contrast Pure White
        const val HEX_TEXT_MUTED = "#8899AC"          // Subdued Slate Gray
        const val HEX_TEXT_ACCENT = "#00FFCC"         // Cyan Data Readout

        // Health & Damage Thresholds
        const val HEX_HEALTH_LOW = "#00E676"          // 0% - 50% Safe (Green)
        const val HEX_HEALTH_MID = "#FFEA00"          // 51% - 100% Caution (Yellow)
        const val HEX_HEALTH_HIGH = "#FF9100"         // 101% - 150% Danger (Orange)
        const val HEX_HEALTH_CRITICAL = "#FF1744"     // 151%+ Lethal Knockback (Red)

        // Compose Color Helpers
        fun colorFromHex(hex: String): Color {
            return Color(android.graphics.Color.parseColor(hex))
        }
    }

    // Modal Overlay Dialog State Flow
    private val _activeDialog = MutableStateFlow(DialogType.NONE)
    val activeDialog: StateFlow<DialogType> = _activeDialog.asStateFlow()

    // Screen Stack Layer Controller for sub-navigation hierarchies
    private val screenStack = ArrayDeque<String>()
    private val _currentStackDepth = MutableStateFlow(0)
    val currentStackDepth: StateFlow<Int> = _currentStackDepth.asStateFlow()

    // Ephemeral Toast Flow
    private val _activeToast = MutableStateFlow<ToastNotification?>(null)
    val activeToast: StateFlow<ToastNotification?> = _activeToast.asStateFlow()

    // Telemetry & Debug HUD Overlay visibility
    private val _isDebugHudVisible = MutableStateFlow(true)
    val isDebugHudVisible: StateFlow<Boolean> = _isDebugHudVisible.asStateFlow()

    // Pause Screen State Flow
    private val _isGamePaused = MutableStateFlow(false)
    val isGamePaused: StateFlow<Boolean> = _isGamePaused.asStateFlow()

    // =========================================================================
    // MODAL DIALOG OPERATIONS
    // =========================================================================

    fun showDialog(dialog: DialogType) {
        _activeDialog.value = dialog
    }

    fun dismissDialog() {
        _activeDialog.value = DialogType.NONE
    }

    // =========================================================================
    // SCREEN STACK LAYER CONTROLLER
    // =========================================================================

    /**
     * Pushes a new sub-screen destination onto the backstack.
     */
    fun pushScreen(screenRoute: String) {
        screenStack.push(screenRoute)
        _currentStackDepth.value = screenStack.size
    }

    /**
     * Pops the top screen from the backstack. Returns null if empty.
     */
    fun popScreen(): String? {
        if (screenStack.isEmpty()) return null
        val popped = screenStack.pop()
        _currentStackDepth.value = screenStack.size
        return popped
    }

    /**
     * Peeks at the current top of the screen stack.
     */
    fun peekScreen(): String? {
        return screenStack.peek()
    }

    /**
     * Clears all sub-screens on the navigation stack (e.g. on return to Main Menu).
     */
    fun clearScreenStack() {
        screenStack.clear()
        _currentStackDepth.value = 0
    }

    // =========================================================================
    // TOAST NOTIFICATIONS & HUD CONTROLS
    // =========================================================================

    fun showToast(message: String, isError: Boolean = false) {
        _activeToast.value = ToastNotification(message = message, isError = isError)
    }

    fun dismissToast() {
        _activeToast.value = null
    }

    fun toggleDebugHud() {
        _isDebugHudVisible.value = !_isDebugHudVisible.value
    }

    fun setGamePaused(paused: Boolean) {
        _isGamePaused.value = paused
    }
}
