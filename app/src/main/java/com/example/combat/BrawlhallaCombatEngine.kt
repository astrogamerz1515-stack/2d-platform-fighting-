package com.example.combat

import com.example.controller.ActionTrackState
import com.example.controller.MovementTrackState
import com.example.controller.ParallelCharacterController
import com.example.engine.animation.AffineMatrix2D
import com.example.engine.animation.AnimationId
import com.example.engine.animation.BoneId
import com.example.engine.animation.CharacterAnimationBridge
import com.example.engine.input.BufferableAction
import kotlin.math.abs
import kotlin.math.sign

/**
 * BrawlhallaCombatEngine Module:
 * Manages 1:1 Brawlhalla Light and Signature attacks, time-backed 3-phase frame windows,
 * Gravity Canceling, and Chase Dodge injection without blocking the locomotion loop.
 *
 * Frequency Correction: Uses a continuous time-backed accumulator (seconds) rather than
 * discrete hardware frame counters, ensuring identical timing across 60Hz, 90Hz, and 120Hz displays.
 */
class BrawlhallaCombatEngine(
    val hitboxSpawner: FramePerfectHitboxSpawner = FramePerfectHitboxSpawner()
) {

    // Active Combat Phase Tracking
    var currentPhase: CombatPhase = CombatPhase.IDLE
        private set
    var activeMove: CombatMoveDefinition? = null
        private set

    // Time-backed Phase Accumulator (Hardware Frequency Independent)
    var phaseTimeAccumulator: Float = 0f
        private set

    // Gravity Cancel Window Tracking (6 frames = 0.100s from dodge start)
    private val gravityCancelWindowThreshold: Float = 0.100f
    var gravityCancelEligible: Boolean = false
        private set
    var isGravityCanceledMove: Boolean = false
        private set

    // Signature Attack Charging
    var isChargingSignature: Boolean = false
        private set
    var chargeTimeAccumulator: Float = 0f
        private set
    val maxChargeDuration: Float = 1.0f // 1 second charge cap

    // Advanced Touch Input Buffer for Combat (6 frames = ~0.100s)
    private var bufferedAttackAction: BufferableAction? = null
    private var bufferedStickX: Float = 0f
    private var bufferedStickY: Float = 0f
    private var bufferedAttackTimeRemaining: Float = 0f
    private val defaultCombatBufferDuration: Float = 0.100f

    // 60Hz reference frame duration
    private val frameStepTime: Float = 1.0f / 60.0f

    /**
     * Feeds raw inputs into the specialized combat buffer.
     */
    fun recordAttackInput(action: BufferableAction, stickX: Float, stickY: Float) {
        bufferedAttackAction = action
        bufferedStickX = stickX
        bufferedStickY = stickY
        bufferedAttackTimeRemaining = defaultCombatBufferDuration
    }

    /**
     * Main combat simulation step driven by delta time (dt in seconds).
     */
    fun fixedUpdate(
        controller: ParallelCharacterController,
        animationBridge: CharacterAnimationBridge?,
        dt: Float
    ) {
        // 1. Age time-backed input buffer
        if (bufferedAttackTimeRemaining > 0f) {
            bufferedAttackTimeRemaining -= dt
            if (bufferedAttackTimeRemaining <= 0f) {
                bufferedAttackAction = null
            }
        }

        // 2. Poll input queue for attack requests
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

        // 3. Detect Gravity-Cancel eligibility during active airborne dodge
        if (controller.movementState == MovementTrackState.AIRBORNE &&
            controller.actionState == ActionTrackState.DODGING &&
            controller.dodgeTimer <= gravityCancelWindowThreshold
        ) {
            gravityCancelEligible = true
        } else if (controller.actionState != ActionTrackState.DODGING) {
            gravityCancelEligible = false
        }

        // 4. Hitstun cancels combat state immediately
        if (controller.actionState == ActionTrackState.HITSTUN) {
            cancelAttackImmediately(controller)
            return
        }

        // 5. Consume buffered attack if idle or executing gravity cancel
        if (currentPhase == CombatPhase.IDLE) {
            if (bufferedAttackAction != null) {
                val action = bufferedAttackAction!!
                val sx = bufferedStickX
                val sy = bufferedStickY

                // Check for GRAVITY CANCEL:
                if (gravityCancelEligible) {
                    bufferedAttackAction = null
                    bufferedAttackTimeRemaining = 0f
                    gravityCancelEligible = false

                    // Execute mid-air grounded version of the move!
                    val move = resolveDirectionalMove(action, sx, sy, isGrounded = true)
                    isGravityCanceledMove = true
                    controller.activateGravityCancel()
                    startMove(move, controller, animationBridge)
                } else if (canInitiateAttack(controller)) {
                    bufferedAttackAction = null
                    bufferedAttackTimeRemaining = 0f
                    isGravityCanceledMove = false

                    val move = resolveDirectionalMove(action, sx, sy, controller.isGrounded)
                    startMove(move, controller, animationBridge)
                }
            }
        } else {
            // 6. Progress active 3-phase combat lifecycle (Startup -> Active -> Recovery)
            advanceCombatPhase(controller, animationBridge, dt)
        }

        // 7. Update active hitboxes attached to skeletal rig
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

    private fun canInitiateAttack(controller: ParallelCharacterController): Boolean {
        return controller.actionState != ActionTrackState.HITSTUN &&
                controller.actionState != ActionTrackState.DODGING &&
                currentPhase == CombatPhase.IDLE
    }

    /**
     * Directional Move Resolution Matrix (1:1 Brawlhalla).
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
                when {
                    stickY > 0.35f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.DOWN_LIGHT)
                    abs(stickX) > 0.28f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.SIDE_LIGHT)
                    else -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.NEUTRAL_LIGHT)
                }
            } else {
                when {
                    stickY > 0.35f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.DOWN_SIG)
                    abs(stickX) > 0.28f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.SIDE_SIG)
                    else -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.NEUTRAL_SIG)
                }
            }
        } else {
            if (!isHeavy) {
                when {
                    stickY > 0.35f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.DOWN_AIR)
                    abs(stickX) > 0.28f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.SIDE_AIR)
                    else -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.NEUTRAL_AIR)
                }
            } else {
                when {
                    stickY > 0.35f -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.GROUND_POUND)
                    else -> BrawlhallaCombatMatrix.getMove(BrawlhallaMoveType.RECOVERY)
                }
            }
        }
    }

    private fun startMove(
        move: CombatMoveDefinition,
        controller: ParallelCharacterController,
        animationBridge: CharacterAnimationBridge?
    ) {
        activeMove = move
        currentPhase = CombatPhase.STARTUP
        phaseTimeAccumulator = 0f
        chargeTimeAccumulator = 0f
        isChargingSignature = move.isHeavySig

        // Update facing direction if stick was tilted
        val stickX = controller.inputQueue.getStickX()
        if (abs(stickX) > 0.25f) {
            controller.setFacingDirectionManually(sign(stickX))
        }

        // Notify parallel controller of attack engagement and momentum preservation
        controller.startAttack(
            slideFriction = move.slideFriction,
            initialImpulseX = move.initialImpulseX,
            initialImpulseY = move.initialImpulseY,
            momentumRatio = move.momentumPreservationRatio
        )

        // Trigger corresponding skeletal animation
        syncAnimationForMove(move, animationBridge)
    }

    private fun advanceCombatPhase(
        controller: ParallelCharacterController,
        animationBridge: CharacterAnimationBridge?,
        dt: Float
    ) {
        val move = activeMove ?: return
        phaseTimeAccumulator += dt

        // Signature Charge handling during Startup
        if (isChargingSignature && currentPhase == CombatPhase.STARTUP) {
            val heavyHeld = controller.inputQueue.isButtonHeld(com.example.engine.input.InputButton.HEAVY_ATTACK)
            if (heavyHeld && chargeTimeAccumulator < maxChargeDuration) {
                chargeTimeAccumulator += dt
                val startupDuration = move.startupFrames * frameStepTime
                if (phaseTimeAccumulator >= startupDuration - frameStepTime) {
                    phaseTimeAccumulator = startupDuration - frameStepTime
                    return
                }
            } else {
                isChargingSignature = false
            }
        }

        val startupDuration = move.startupFrames * frameStepTime
        val activeDuration = move.activeFrames * frameStepTime
        val recoveryDuration = move.recoveryFrames * frameStepTime

        when (currentPhase) {
            CombatPhase.STARTUP -> {
                if (phaseTimeAccumulator >= startupDuration) {
                    currentPhase = CombatPhase.ACTIVE
                    phaseTimeAccumulator -= startupDuration

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
                // Ground Pound terminates upon stage floor impact
                if (move.moveType == BrawlhallaMoveType.GROUND_POUND && controller.isGrounded) {
                    phaseTimeAccumulator = activeDuration
                }

                if (phaseTimeAccumulator >= activeDuration) {
                    currentPhase = CombatPhase.RECOVERY
                    phaseTimeAccumulator -= activeDuration
                    hitboxSpawner.clearAllHitboxes()
                }
            }

            CombatPhase.RECOVERY -> {
                if (phaseTimeAccumulator >= recoveryDuration) {
                    cancelAttackImmediately(controller)

                    // Execute queued buffered attack on exact freedom frame
                    if (bufferedAttackAction != null && canInitiateAttack(controller)) {
                        val action = bufferedAttackAction!!
                        val sx = bufferedStickX
                        val sy = bufferedStickY
                        bufferedAttackAction = null
                        bufferedAttackTimeRemaining = 0f
                        val nextMove = resolveDirectionalMove(action, sx, sy, controller.isGrounded)
                        startMove(nextMove, controller, animationBridge)
                    }
                }
            }

            CombatPhase.IDLE -> Unit
        }
    }

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
     * Invoked when a hitbox successfully strikes an opponent.
     * Grants the attacker a 10-frame Chase Dodge window for hyper-speed combo extensions.
     */
    fun onHitConfirmed(controller: ParallelCharacterController) {
        controller.grantChaseDodgeWindow()
    }

    fun cancelAttackImmediately(controller: ParallelCharacterController) {
        currentPhase = CombatPhase.IDLE
        activeMove = null
        phaseTimeAccumulator = 0f
        chargeTimeAccumulator = 0f
        isChargingSignature = false
        isGravityCanceledMove = false
        hitboxSpawner.clearAllHitboxes()
        controller.finishAttack()
    }

    fun isAttacking(): Boolean = currentPhase != CombatPhase.IDLE
}
