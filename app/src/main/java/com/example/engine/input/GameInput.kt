package com.example.engine.input

/**
 * Bitmask flags for discrete fighting game controller buttons.
 */
object InputButton {
    const val NONE: Int = 0
    const val LEFT: Int = 1 shl 0
    const val RIGHT: Int = 1 shl 1
    const val UP: Int = 1 shl 2
    const val DOWN: Int = 1 shl 3
    const val JUMP: Int = 1 shl 4
    const val DASH: Int = 1 shl 5
    const val LIGHT_ATTACK: Int = 1 shl 6
    const val HEAVY_ATTACK: Int = 1 shl 7
}

/**
 * Snapshot of player controller input at a specific frame.
 */
data class GameInputSnapshot(
    var buttons: Int = InputButton.NONE,
    var stickX: Float = 0f,
    var stickY: Float = 0f,
    var timestampNanos: Long = 0L
) {
    fun isPressed(buttonMask: Int): Boolean = (buttons and buttonMask) != 0

    fun reset() {
        buttons = InputButton.NONE
        stickX = 0f
        stickY = 0f
        timestampNanos = 0L
    }

    fun copyFrom(other: GameInputSnapshot) {
        this.buttons = other.buttons
        this.stickX = other.stickX
        this.stickY = other.stickY
        this.timestampNanos = other.timestampNanos
    }
}
