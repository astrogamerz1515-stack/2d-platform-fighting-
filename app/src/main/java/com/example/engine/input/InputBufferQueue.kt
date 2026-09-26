package com.example.engine.input

import java.util.concurrent.atomic.AtomicInteger

/**
 * Types of discrete actions buffered in the fighting game input queue.
 */
enum class BufferableAction {
    JUMP,
    DASH,
    FAST_FALL,
    DROP_THROUGH_PLATFORM,
    LIGHT_ATTACK,
    HEAVY_ATTACK
}

/**
 * Individual entry inside the frame-perfect input buffer.
 */
data class BufferedCommand(
    var action: BufferableAction? = null,
    var framesRemaining: Int = 0,
    var directionalX: Float = 0f,
    var directionalY: Float = 0f,
    var consumed: Boolean = false
) {
    fun reset() {
        action = null
        framesRemaining = 0
        directionalX = 0f
        directionalY = 0f
        consumed = false
    }
}

/**
 * High-performance, zero-allocation ring buffer input queue.
 * Decouples asynchronous 60Hz/90Hz/120Hz/240Hz Android touch digitizer events
 * from the deterministic 60Hz fixed physics tick.
 */
class InputBufferQueue(
    private val bufferCapacity: Int = 16,
    val defaultBufferWindowFrames: Int = 5 // 5 physics frames = ~83.3ms input buffer
) {
    private val ringBuffer: Array<BufferedCommand> = Array(bufferCapacity) { BufferedCommand() }
    private var writeIndex = 0
    private val lock = Any()

    // Current continuous real-time state for analog controls and button hold checks
    private val currentRawInput = GameInputSnapshot()
    private val currentPhysicsInput = GameInputSnapshot()

    // Edge triggers (just pressed this tick)
    private var buttonsPressedThisTick = 0
    private var buttonsReleasedThisTick = 0
    private var previousButtonsState = 0

    /**
     * Called asynchronously from Android UI / Touch dispatch thread.
     * Thread-safe updates to the raw input snapshot and buffers button triggers.
     */
    fun onRawInputUpdated(snapshot: GameInputSnapshot) {
        synchronized(lock) {
            val newlyPressed = snapshot.buttons and previousButtonsState.inv()
            previousButtonsState = snapshot.buttons

            currentRawInput.copyFrom(snapshot)

            // Detect button-down edge transitions and buffer them immediately
            if ((newlyPressed and InputButton.JUMP) != 0) {
                enqueueAction(BufferableAction.JUMP, snapshot.stickX, snapshot.stickY)
            }
            if ((newlyPressed and InputButton.DASH) != 0) {
                enqueueAction(BufferableAction.DASH, snapshot.stickX, snapshot.stickY)
            }
            if ((newlyPressed and InputButton.LIGHT_ATTACK) != 0) {
                enqueueAction(BufferableAction.LIGHT_ATTACK, snapshot.stickX, snapshot.stickY)
            }
            if ((newlyPressed and InputButton.HEAVY_ATTACK) != 0) {
                enqueueAction(BufferableAction.HEAVY_ATTACK, snapshot.stickX, snapshot.stickY)
            }
        }
    }

    /**
     * Manually enqueues an action with a directional vector into the ring buffer.
     */
    fun enqueueAction(
        action: BufferableAction,
        dirX: Float = 0f,
        dirY: Float = 0f,
        customWindowFrames: Int = defaultBufferWindowFrames
    ) {
        synchronized(lock) {
            val entry = ringBuffer[writeIndex]
            entry.action = action
            entry.framesRemaining = customWindowFrames
            entry.directionalX = dirX
            entry.directionalY = dirY
            entry.consumed = false
            writeIndex = (writeIndex + 1) % bufferCapacity
        }
    }

    /**
     * Called strictly at the start of each deterministic 60Hz physics tick.
     * Age existing buffer commands and advance frame timers.
     */
    fun tickPhysicsFrame() {
        synchronized(lock) {
            // Compute edge triggers for the tick
            buttonsPressedThisTick = currentRawInput.buttons and currentPhysicsInput.buttons.inv()
            buttonsReleasedThisTick = currentPhysicsInput.buttons and currentRawInput.buttons.inv()
            currentPhysicsInput.copyFrom(currentRawInput)

            // Age buffered commands
            for (i in ringBuffer.indices) {
                val cmd = ringBuffer[i]
                if (!cmd.consumed && cmd.framesRemaining > 0) {
                    cmd.framesRemaining--
                    if (cmd.framesRemaining <= 0) {
                        cmd.reset()
                    }
                }
            }
        }
    }

    /**
     * Checks if a specific action is available in the buffer without consuming it.
     */
    fun hasBufferedAction(action: BufferableAction): Boolean {
        synchronized(lock) {
            for (i in ringBuffer.indices) {
                val cmd = ringBuffer[i]
                if (!cmd.consumed && cmd.action == action && cmd.framesRemaining > 0) {
                    return true
                }
            }
            return false
        }
    }

    /**
     * Consumes an action from the buffer if present. Returns true if successfully consumed.
     */
    fun consumeAction(action: BufferableAction): Boolean {
        synchronized(lock) {
            for (i in ringBuffer.indices) {
                val cmd = ringBuffer[i]
                if (!cmd.consumed && cmd.action == action && cmd.framesRemaining > 0) {
                    cmd.consumed = true
                    cmd.framesRemaining = 0
                    return true
                }
            }
            return false
        }
    }

    /**
     * Checks if a button is currently held down this physics tick.
     */
    fun isButtonHeld(buttonMask: Int): Boolean {
        return (currentPhysicsInput.buttons and buttonMask) != 0
    }

    /**
     * Checks if a button was just pressed on this exact physics tick.
     */
    fun wasButtonPressedThisTick(buttonMask: Int): Boolean {
        return (buttonsPressedThisTick and buttonMask) != 0
    }

    /**
     * Checks if a button was just released on this exact physics tick.
     */
    fun wasButtonReleasedThisTick(buttonMask: Int): Boolean {
        return (buttonsReleasedThisTick and buttonMask) != 0
    }

    fun getStickX(): Float = currentPhysicsInput.stickX

    fun getStickY(): Float = currentPhysicsInput.stickY

    fun getPhysicsInput(): GameInputSnapshot = currentPhysicsInput
}
