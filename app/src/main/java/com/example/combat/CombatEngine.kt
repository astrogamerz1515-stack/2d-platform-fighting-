package com.example.combat

import com.example.controller.KinematicCharacterController
import com.example.engine.animation.AffineMatrix2D
import com.example.engine.animation.AnimationId
import com.example.engine.animation.BoneId
import com.example.engine.animation.CharacterAnimationBridge
import com.example.engine.input.BufferableAction
import com.example.engine.math.Vector2
import com.example.fsm.CharacterStateType
import kotlin.math.abs
import kotlin.math.sign

/**
 * CombatEngine Module:
 * Coordinates the 1:1 Brawlhalla combat matrix, directional attack mapping,
 * 4–6 frame Android touch input buffering, and 3-phase frame calculations
 * (Startup -> Active -> Recovery) without blocking the kinematic movement engine.
 */
class CombatEngine(
    val hitboxSpawner: FramePerfectHitboxSpawner = FramePerfectHitboxSpawner()
) {

    // Combat State Tracking
    var currentPhase: CombatPhase = CombatPhase.IDLE
        private set
    var activeMove: CombatMoveDefinition? = null
        private set
    var currentFrameInPhase: Int = 0
        private set

    // Heavy Signature Charging
    var isChargingSignature: Boolean = false
        private set
    var chargeFrames: Int = 0
        private set
    private val maxChargeFrames = 60 // 1 second charge cap

    // Advanced Touch Input Buffer for Combat (4-6 frames)
    private var bufferedAttackAction: BufferableAction? = null
    private var bufferedStickX: Float = 0f
    private var bufferedStickY: Float = 0f
    private var bufferedAttackFramesRemaining: Int = 0
    private val defaultCombatBufferWindow: Int = 6 // 6 physics ticks = 100ms window

    /**
     * Feeds raw inputs from controller input queue into the specialized combat buffer.
     */
    fun recordAttackInput(action: BufferableAction, stickX: Float, stickY: Float) {
        bufferedAttackAction = action
        bufferedStickX = stickX
        bufferedStickY = stickY
        bufferedAttackFramesRemaining = defaultCombatBufferWindow
    }

    /**
     * Fixed-physics tick (60Hz) driving combat frame counters and momentum sliding.
     */
    fun fixedUpdate(
        controller: KinematicCharacterController,
        animationBridge: CharacterAnimationBridge?,
        dt: Float
    ) {
        // 1. Age buffered combat actions
        if (bufferedAttackFramesRemaining > 0) {
            bufferedAttackFramesRemaining--
            if (bufferedAttackFramesRemaining <= 0) {
                bufferedAttackAction = null
            }
        }

        // 2. Poll the controller's input buffer for attack requests
        if (controller.inputQueue.consumeAction(BufferableAction.LIGHT_ATTACK)) {
            recordAttackInput(
                BufferableAction.LIGHT_ATTACK,
                controller.inputQueue.getStickX(),
                controller.inputQueue.getStickY()
            )
        }
        if (controller.inputQueue.consumeAction(BufferableAction.HEAVY_ATTACK)) {
            recordAttackInput(
                BufferableAction.HEAVY_ATTACK,
                controller.inputQueue.getStickX(),
                controller.inputQueue.getStickY()
            )
        }

        // 3. Disrupted State check (HitStun or Knockback cancels combat immediately)
        val state = controller.stateMachine.getCurrentStateType()
        if (state == CharacterStateType.HURT || state == CharacterStateType.KNOCKBACK) {
            cancelAttackImmediately()
            return
        }

        // 4. If idle, attempt to consume buffered attack action
        if (currentPhase == CombatPhase.IDLE) {
            if (bufferedAttackAction != null && canInitiateAttack(state)) {
                val action = bufferedAttackAction!!
                val sx = bufferedStickX
                val sy = bufferedStickY
                bufferedAttackAction = null
                bufferedAttackFramesRemaining = 0

                val move = resolveDirectionalMove(action, sx, sy, controller.isGrounded)
                startMove(move, controller, animationBridge)
            }
        } else {
            // 5. Progress active combat phase
            advanceCombatPhase(controller, animationBridge, dt)
        }

        // 6. Update active hitboxes attached to skeletal rig
        val skeletalMatrices = if (animationBridge != null) {
            mutableMapOf<BoneId, AffineMatrix2D>().apply {
                for (b in BoneId.values()) {
                    val m = animationBridge.getWorldBoneMatrix(b)
                    if (m != null) put(b, m)
                }
            }
        } else null

        hitboxSpawner.tickActiveHitboxes(
            rootX = controller.position.x,
            rootY = controller.position.y,
            facingDirection = controller.facingDirection,
            skeletalMatrices = skeletalMatrices
        )
    }

    /**
     * Determines if the character controller is in a state permitted to attack.
     */
    private fun canInitiateAttack(state: CharacterStateType): Boolean {
        return state != CharacterStateType.HURT &&
                state != CharacterStateType.KNOCKBACK &&
                currentPhase == CombatPhase.IDLE
    }

    /**
     * Translates directional stick coordinates and button type into 1:1 Brawlhalla moves.
     */
    fun resolveDirectionalMove(
        action: BufferableAction,
        stickX: Float,
        stickY: Float,
        isGrounded: Boolean
    ): CombatMoveDefinition {
        val isHeavy = (action == BufferableAction.HEAVY_ATTACK)

        return if (isGrounded) {
            if (!isHeavy) {
                // Grounded Lights
                when {
                    stickY > 0.35f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.DOWN_LIGHT)
                    abs(stickX) > 0.28f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.SIDE_LIGHT)
                    else -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.NEUTRAL_LIGHT)
                }
            } else {
                // Grounded Signatures (Heavies)
                when {
                    stickY > 0.35f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.DOWN_SIG)
                    abs(stickX) > 0.28f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.SIDE_SIG)
                    else -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.NEUTRAL_SIG)
                }
            }
        } else {
            if (!isHeavy) {
                // Aerial Lights
                when {
                    stickY > 0.35f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.DOWN_AIR)
                    abs(stickX) > 0.28f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.SIDE_AIR)
                    else -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.NEUTRAL_AIR)
                }
            } else {
                // Aerial Heavy Specials
                when {
                    stickY > 0.35f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.GROUND_POUND)
                    else -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.RECOVERY)
                }
            }
        }
    }

    /**
     * Begins executing the specified combat move and applies non-blocking momentum.
     */
    private fun startMove(
        move: CombatMoveDefinition,
        controller: KinematicCharacterController,
        animationBridge: CharacterAnimationBridge?
    ) {
        activeMove = move
        currentPhase = CombatPhase.STARTUP
        currentFrameInPhase = 0
        chargeFrames = 0
        isChargingSignature = move.isHeavySig

        // Update facing direction if directional stick was tilted
        val stickX = controller.inputQueue.getStickX()
        if (abs(stickX) > 0.25f) {
            controller.setFacingDirectionManually(sign(stickX))
        }

        // NON-BLOCKING MOMENTUM FIX:
        // Carry entry velocity scaled by momentum preservation ratio
        controller.velocity.x *= move.momentumPreservationRatio

        // Apply initial forward/upward impulse without freezing movement loops
        controller.velocity.x += move.initialImpulseX * controller.facingDirection
        if (abs(move.initialImpulseY) > 0.01f) {
            controller.velocity.y = move.initialImpulseY
        }

        // Sync with Skeletal Animation Bridge
        syncAnimationForMove(move, animationBridge)
    }

    /**
     * Marries the combat move to the corresponding animation clip.
     */
    private fun syncAnimationForMove(
        move: CombatMoveDefinition,
        animationBridge: CharacterAnimationBridge?
    ) {
        if (animationBridge == null) return
        val animId = when (move.moveType) {
            BrawlhallaMoveType.NEUTRAL_LIGHT -> AnimationId.ATTACK_NEUTRAL
            BrawlhallaMoveType.SIDE_LIGHT -> AnimationId.ATTACK_SIDE
            BrawlhallaMoveType.DOWN_LIGHT -> AnimationId.ATTACK_DOWN
            BrawlhallaMoveType.NEUTRAL_SIG -> AnimationId.ATTACK_SIDE
            BrawlhallaMoveType.SIDE_SIG -> AnimationId.ATTACK_SIDE
            BrawlhallaMoveType.DOWN_SIG -> AnimationId.ATTACK_DOWN
            BrawlhallaMoveType.NEUTRAL_AIR -> AnimationId.ATTACK_NEUTRAL
            BrawlhallaMoveType.SIDE_AIR -> AnimationId.ATTACK_SIDE
            BrawlhallaMoveType.DOWN_AIR -> AnimationId.ATTACK_DOWN
            BrawlhallaMoveType.RECOVERY -> AnimationId.JUMP_RISE
            BrawlhallaMoveType.GROUND_POUND -> AnimationId.ATTACK_DOWN
        }
        animationBridge.triggerAttack(animId)
    }

    /**
     * Progresses through the 3 distinct frame phases: Startup -> Active -> Recovery -> Free.
     */
    private fun advanceCombatPhase(
        controller: KinematicCharacterController,
        animationBridge: CharacterAnimationBridge?,
        dt: Float
    ) {
        val move = activeMove ?: return
        currentFrameInPhase++

        // Handle Heavy Signature Charging during Startup
        if (isChargingSignature && currentPhase == CombatPhase.STARTUP) {
            val heavyHeld = controller.inputQueue.isButtonHeld(
                com.example.engine.input.InputButton.HEAVY_ATTACK
            )
            if (heavyHeld && chargeFrames < maxChargeFrames) {
                chargeFrames++
                // Hold on startup frame 3 while charging
                if (currentFrameInPhase >= move.startupFrames - 1) {
                    currentFrameInPhase = move.startupFrames - 1
                    return
                }
            } else {
                isChargingSignature = false
            }
        }

        // Apply Sliding Deceleration during active/recovery phases
        applySlidingDecay(controller, move, dt)

        when (currentPhase) {
            CombatPhase.STARTUP -> {
                if (currentFrameInPhase >= move.startupFrames) {
                    // Transition to ACTIVE Phase
                    currentPhase = CombatPhase.ACTIVE
                    currentFrameInPhase = 0

                    // Spawn combat hitbox dynamically
                    val skeletalMatrices = if (animationBridge != null) {
                        mutableMapOf<BoneId, AffineMatrix2D>().apply {
                            for (b in BoneId.values()) {
                                val m = animationBridge.getWorldBoneMatrix(b)
                                if (m != null) put(b, m)
                            }
                        }
                    } else null

                    hitboxSpawner.spawnHitbox(
                        def = move,
                        rootX = controller.position.x,
                        rootY = controller.position.y,
                        facingDirection = controller.facingDirection,
                        skeletalMatrices = skeletalMatrices
                    )
                }
            }

            CombatPhase.ACTIVE -> {
                // Ground Pound stays active until ground contact or timeout
                if (move.moveType == BrawlhallaMoveType.GROUND_POUND && controller.isGrounded) {
                    currentFrameInPhase = move.activeFrames
                }

                if (currentFrameInPhase >= move.activeFrames) {
                    // Transition to RECOVERY Phase
                    currentPhase = CombatPhase.RECOVERY
                    currentFrameInPhase = 0
                    hitboxSpawner.clearAllHitboxes()
                }
            }

            CombatPhase.RECOVERY -> {
                if (currentFrameInPhase >= move.recoveryFrames) {
                    // End of move: return to neutral IDLE
                    cancelAttackImmediately()
                    // Immediately check if an action was buffered so it fires on this exact freedom frame
                    if (bufferedAttackAction != null && canInitiateAttack(controller.stateMachine.getCurrentStateType())) {
                        val action = bufferedAttackAction!!
                        val sx = bufferedStickX
                        val sy = bufferedStickY
                        bufferedAttackAction = null
                        bufferedAttackFramesRemaining = 0
                        val nextMove = resolveDirectionalMove(action, sx, sy, controller.isGrounded)
                        startMove(nextMove, controller, animationBridge)
                    }
                }
            }

            CombatPhase.IDLE -> Unit
        }
    }

    /**
     * Applies non-blocking slide deceleration while preserving kinematic velocity.
     */
    private fun applySlidingDecay(
        controller: KinematicCharacterController,
        move: CombatMoveDefinition,
        dt: Float
    ) {
        if (controller.isGrounded) {
            // Decay horizontal slide with move-specific friction
            val friction = move.slideFriction * dt
            if (abs(controller.velocity.x) > friction) {
                controller.velocity.x -= sign(controller.velocity.x) * friction
            } else {
                controller.velocity.x = 0f
            }
        } else if (move.allowAirDrift) {
            // Allow directional aerial drift during jump attacks
            val stickX = controller.inputQueue.getStickX()
            if (abs(stickX) > 0.15f) {
                controller.velocity.x += stickX * move.airDriftAcceleration * dt
                val maxAir = controller.config.maxAirSpeed
                controller.velocity.x = controller.velocity.x.coerceIn(-maxAir, maxAir)
            }
        }
    }

    /**
     * Clears all attack state immediately (e.g. on hitstun, death, or completion).
     */
    fun cancelAttackImmediately() {
        currentPhase = CombatPhase.IDLE
        activeMove = null
        currentFrameInPhase = 0
        chargeFrames = 0
        isChargingSignature = false
        hitboxSpawner.clearAllHitboxes()
    }

    fun isAttacking(): Boolean = currentPhase != CombatPhase.IDLE
}
