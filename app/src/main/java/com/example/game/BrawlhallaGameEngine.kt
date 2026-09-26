package com.example.game

import com.example.controller.KinematicCharacterController
import com.example.engine.input.BufferableAction
import com.example.engine.input.GameInputSnapshot
import com.example.engine.math.Vector2
import com.example.fsm.CharacterStateType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Main game simulation engine for the 2D Platform Fighter.
 * Implements a deterministic fixed-timestep accumulator (60Hz) decoupled from visual frame rates (60/90/120Hz).
 */
class BrawlhallaGameEngine(
    var onPlayerKO: () -> Unit = {}
) {
    val stage = BrawlStage()
    val player = KinematicCharacterController(
        config = com.example.core.GameManager.instance.selectedCharacter.value.movementConfig
    )
    val particleSystem = ParticleSystem()
    var isPaused: Boolean = false

    // Fixed timestep constants
    val fixedDeltaTime: Float = 1.0f / 60.0f // 16.666 ms
    private val maxFrameDeltaTime: Float = 0.25f // Prevents spiral of death on severe stutters
    private var timeAccumulator: Float = 0f

    // Camera state
    val cameraPosition = Vector2(500f, 350f)
    var cameraTargetZoom: Float = 1.0f
    var cameraCurrentZoom: Float = 1.0f

    // Performance & Debug Telemetry
    var currentFps: Float = 60.0f
        private set
    var physicsTicksPerSecond: Int = 60
        private set
    var totalFramesRendered: Long = 0L
        private set
    var totalPhysicsTicks: Long = 0L
        private set

    private var previousStateType: CharacterStateType = CharacterStateType.FALL
    private var wasGroundedLastTick: Boolean = false

    init {
        // Spawn player on main platform
        resetPlayerSpawn()
    }

    fun resetPlayerSpawn() {
        player.setSpawnPosition(500f, 300f)
    }

    fun clearAllEffects() {
        for (i in particleSystem.particles.indices) {
            particleSystem.particles[i].reset()
        }
    }

    /**
     * Feeds raw touch inputs from Android UI thread into player's input buffer.
     */
    fun processTouchInput(inputSnapshot: GameInputSnapshot) {
        player.inputQueue.onRawInputUpdated(inputSnapshot)
    }

    /**
     * Variable visual update called every display frame (e.g. from Compose Canvas or Choreographer).
     * Drives the deterministic fixed-timestep loop and camera interpolation.
     */
    fun update(frameDeltaSeconds: Float) {
        totalFramesRendered++

        // Filter extreme frame spikes
        val dt = min(frameDeltaSeconds, maxFrameDeltaTime)

        if (!isPaused) {
            timeAccumulator += dt

            // Fixed-timestep simulation steps
            var ticksThisFrame = 0
            while (timeAccumulator >= fixedDeltaTime && ticksThisFrame < 5) {
                physicsTick(fixedDeltaTime)
                timeAccumulator -= fixedDeltaTime
                totalPhysicsTicks++
                ticksThisFrame++
            }

            // Visual particles update
            particleSystem.update(dt)
        }

        // Camera smoothing (variable visual interpolation)
        updateCamera(dt)

        // Estimate FPS
        if (dt > 0.0001f) {
            val instantFps = 1.0f / dt
            currentFps = currentFps * 0.9f + instantFps * 0.1f
        }
    }

    /**
     * Exactly 60Hz deterministic physics tick.
     */
    private fun physicsTick(dt: Float) {
        // Store previous state to detect transitions for VFX
        val stateBefore = player.stateMachine.getCurrentStateType()
        val groundedBefore = player.isGrounded

        // Update kinematic controller
        player.fixedUpdate(dt, stage.colliders)

        val stateAfter = player.stateMachine.getCurrentStateType()
        val isGroundedAfter = player.isGrounded

        // Trigger visual effects on state changes
        triggerVFX(stateBefore, stateAfter, groundedBefore, isGroundedAfter)

        // Blast zone check (Brawlhalla ring out / respawn)
        if (stage.isOutOfBounds(player.position.x, player.position.y)) {
            // Spawn KO blast ring
            particleSystem.spawn(
                type = ParticleType.JUMP_RING,
                x = player.position.x,
                y = player.position.y,
                size = 36f,
                decayRate = 0.04f,
                colorArgb = 0xFFFF5555
            )
            onPlayerKO()
            resetPlayerSpawn()
        }
    }

    private fun triggerVFX(
        stateBefore: CharacterStateType,
        stateAfter: CharacterStateType,
        groundedBefore: Boolean,
        groundedAfter: Boolean
    ) {
        // Landing dust
        if (!groundedBefore && groundedAfter) {
            particleSystem.spawn(
                type = ParticleType.DUST,
                x = player.position.x - 12f,
                y = player.bounds.maxY,
                vx = -80f,
                vy = -20f,
                size = 14f,
                decayRate = 0.06f,
                colorArgb = 0xAAFFFFFF
            )
            particleSystem.spawn(
                type = ParticleType.DUST,
                x = player.position.x + 12f,
                y = player.bounds.maxY,
                vx = 80f,
                vy = -20f,
                size = 14f,
                decayRate = 0.06f,
                colorArgb = 0xAAFFFFFF
            )
        }

        // Jump Ring
        if (stateAfter == CharacterStateType.JUMP && stateBefore != CharacterStateType.JUMP) {
            particleSystem.spawn(
                type = ParticleType.JUMP_RING,
                x = player.position.x,
                y = player.bounds.maxY,
                size = 20f,
                decayRate = 0.08f,
                colorArgb = 0xEE44DDFF
            )
        }

        // Dash Ghost / Trail
        if (stateAfter == CharacterStateType.DASH) {
            particleSystem.spawn(
                type = ParticleType.DASH_GHOST,
                x = player.position.x,
                y = player.position.y,
                size = 32f,
                decayRate = 0.09f,
                colorArgb = if (player.isInvincible) 0xCCFFD700 else 0x9900FFFF
            )
        }

        // Wall Slide Sparks
        if (stateAfter == CharacterStateType.WALL_SLIDE || stateAfter == CharacterStateType.WALL_CLING) {
            val sparkX = if (player.isTouchingWallLeft) player.bounds.minX else player.bounds.maxX
            particleSystem.spawn(
                type = ParticleType.WALL_SPARK,
                x = sparkX,
                y = player.position.y + (Math.random().toFloat() - 0.5f) * 30f,
                vx = player.wallNormalDirection * 40f,
                vy = -30f,
                size = 8f,
                decayRate = 0.08f,
                colorArgb = 0xFFFFAA00
            )
        }
    }

    private fun updateCamera(dt: Float) {
        // Camera smoothly follows player with look-ahead based on velocity
        val targetX = player.position.x + player.velocity.x * 0.25f
        val targetY = player.position.y + player.velocity.y * 0.15f

        val lerpFactor = min(1.0f, dt * 6.5f)
        cameraPosition.x += (targetX - cameraPosition.x) * lerpFactor
        cameraPosition.y += (targetY - cameraPosition.y) * lerpFactor
    }
}
