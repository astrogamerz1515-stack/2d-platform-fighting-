package com.example.ai

import com.example.engine.input.GameInputSnapshot
import com.example.engine.input.InputButton
import kotlin.random.Random

/**
 * Raw input intent created by the AI Decision Tree before latency simulation.
 */
data class AIIntent(
    var stickX: Float = 0f,
    var stickY: Float = 0f,
    var requestJump: Boolean = false,
    var requestDash: Boolean = false,
    var requestLightAttack: Boolean = false,
    var requestHeavyAttack: Boolean = false,
    var requestDown: Boolean = false,
    var holdHeavyCharge: Boolean = false
) {
    fun reset() {
        stickX = 0f
        stickY = 0f
        requestJump = false
        requestDash = false
        requestLightAttack = false
        requestHeavyAttack = false
        requestDown = false
        holdHeavyCharge = false
    }

    fun copyFrom(other: AIIntent) {
        stickX = other.stickX
        stickY = other.stickY
        requestJump = other.requestJump
        requestDash = other.requestDash
        requestLightAttack = other.requestLightAttack
        requestHeavyAttack = other.requestHeavyAttack
        requestDown = other.requestDown
        holdHeavyCharge = other.holdHeavyCharge
    }
}

/**
 * AIInputEmulator:
 * Programmatically emulates human player inputs into the InputBufferQueue.
 * Applies reaction-time latencies (ring buffer) and human-like inaccuracies.
 */
class AIInputEmulator(
    val config: AIDifficultyConfig
) {
    // Ring buffer for simulated human reaction latency
    private val maxBufferCapacity = 32
    private val latencyRingBuffer: Array<AIIntent> = Array(maxBufferCapacity) { AIIntent() }
    private var writeHead: Int = 0

    // Immediate intent currently constructed by the behavior tree
    val stagingIntent = AIIntent()

    // Output snapshot fed to the IntegratedPlayerController
    val outputSnapshot = GameInputSnapshot()

    // Internal state for button hold and tap durations
    private var previousJumpHeld = false
    private var previousDashHeld = false
    private var previousLightHeld = false
    private var previousHeavyHeld = false
    private var previousDownHeld = false

    private val random = Random(42)

    /**
     * Commits the current frame's intent to the latency ring buffer and
     * emits the delayed, noise-adjusted GameInputSnapshot.
     */
    fun tick(dt: Float): GameInputSnapshot {
        // 1. Store staging intent into latency buffer
        latencyRingBuffer[writeHead].copyFrom(stagingIntent)

        // 2. Read from readHead (writeHead - reactionDelayFrames)
        val delay = config.reactionDelayFrames.coerceIn(0, maxBufferCapacity - 1)
        var readHead = writeHead - delay
        if (readHead < 0) readHead += maxBufferCapacity

        val delayedIntent = latencyRingBuffer[readHead]

        // Advance writeHead for next tick
        writeHead = (writeHead + 1) % maxBufferCapacity

        // 3. Inject human-like analog inaccuracy / jitter
        var sx = delayedIntent.stickX
        var sy = delayedIntent.stickY

        if (config.inputInaccuracyNoise > 0.001f) {
            val noiseX = (random.nextFloat() * 2f - 1f) * config.inputInaccuracyNoise
            val noiseY = (random.nextFloat() * 2f - 1f) * config.inputInaccuracyNoise
            sx = (sx + noiseX).coerceIn(-1.0f, 1.0f)
            sy = (sy + noiseY).coerceIn(-1.0f, 1.0f)
        }

        outputSnapshot.stickX = sx
        outputSnapshot.stickY = sy

        // 4. Construct Pressed and Held bitmasks
        var pressedMask = 0
        var heldMask = 0

        // JUMP
        if (delayedIntent.requestJump) {
            heldMask = heldMask or InputButton.JUMP
            if (!previousJumpHeld) {
                pressedMask = pressedMask or InputButton.JUMP
            }
        }
        previousJumpHeld = delayedIntent.requestJump

        // DASH
        if (delayedIntent.requestDash) {
            heldMask = heldMask or InputButton.DASH
            if (!previousDashHeld) {
                pressedMask = pressedMask or InputButton.DASH
            }
        }
        previousDashHeld = delayedIntent.requestDash

        // LIGHT ATTACK
        if (delayedIntent.requestLightAttack) {
            heldMask = heldMask or InputButton.LIGHT_ATTACK
            if (!previousLightHeld) {
                pressedMask = pressedMask or InputButton.LIGHT_ATTACK
            }
        }
        previousLightHeld = delayedIntent.requestLightAttack

        // HEAVY ATTACK
        val heavyActive = delayedIntent.requestHeavyAttack || delayedIntent.holdHeavyCharge
        if (heavyActive) {
            heldMask = heldMask or InputButton.HEAVY_ATTACK
            if (!previousHeavyHeld) {
                pressedMask = pressedMask or InputButton.HEAVY_ATTACK
            }
        }
        previousHeavyHeld = heavyActive

        // DOWN / FAST-FALL
        if (delayedIntent.requestDown) {
            heldMask = heldMask or InputButton.DOWN
            if (!previousDownHeld) {
                pressedMask = pressedMask or InputButton.DOWN
            }
        }
        previousDownHeld = delayedIntent.requestDown

        outputSnapshot.buttons = heldMask

        return outputSnapshot
    }

    /**
     * Resets input state and empties the latency pipeline.
     */
    fun reset() {
        stagingIntent.reset()
        for (i in latencyRingBuffer.indices) {
            latencyRingBuffer[i].reset()
        }
        previousJumpHeld = false
        previousDashHeld = false
        previousLightHeld = false
        previousHeavyHeld = false
        previousDownHeld = false
        outputSnapshot.stickX = 0f
        outputSnapshot.stickY = 0f
        outputSnapshot.buttons = InputButton.NONE
    }
}
