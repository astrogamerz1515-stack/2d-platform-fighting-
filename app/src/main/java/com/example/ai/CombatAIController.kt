package com.example.ai

import com.example.combat.BrawlhallaMoveType
import com.example.combat.CombatHitbox
import com.example.combat.HitResolution
import com.example.combat.IntegratedPlayerController
import com.example.controller.CharacterMovementConfig
import com.example.engine.collision.AABB
import com.example.engine.math.Vector2
import com.example.fsm.CharacterStateType
import com.example.game.BrawlStage
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

/**
 * High-level Combat AI Behavior States.
 */
enum class AICombatState {
    NEUTRAL,            // Micro-spacing, positioning, awaiting tactical openings
    AGGRESSIVE,         // Within strike range: executes directional lights, combos, or signatures
    DEFENSIVE_RECOVERY, // Off-stage: horizontal dashes, wall-clings, wall-jumps, vertical recovery
    EDGE_GUARDING       // Opponent off-stage: controls ledge edge, executes down-spikes and ground pounds
}

/**
 * CombatAIController Class:
 * Autonomous competitive NPC fighter that drives an IntegratedPlayerController purely
 * through human-like sensory evaluation and non-cheating programmatic input emulation.
 */
class CombatAIController(
    val difficultyLevel: AIDifficultyLevel = AIDifficultyLevel.HARD,
    val config: AIDifficultyConfig = AIDifficultyConfig.getConfig(difficultyLevel),
    characterId: String = "valkyrie_bot"
) {

    // Dedicated Integrated Player Controller for the NPC
    val botPlayer: IntegratedPlayerController = IntegratedPlayerController(
        config = CharacterMovementConfig(),
        characterId = characterId
    )

    // Sensory Perception Subsystem
    val sensor: AISpatialSensor = AISpatialSensor()

    // Human Input Emulation Subsystem
    val inputEmulator: AIInputEmulator = AIInputEmulator(config)

    // Active AI State Machine
    var currentState: AICombatState = AICombatState.NEUTRAL
        private set

    // Internal timing and pacing counters
    private var stateTimerFrames: Int = 0
    private var attackCooldownFrames: Int = 0
    private var chargeDurationFrames: Int = 0
    private val random = Random(1337)

    // Spatial & Combat Accessors
    val position: Vector2 get() = botPlayer.position
    val velocity: Vector2 get() = botPlayer.velocity
    val bounds: AABB get() = botPlayer.bounds
    val isGrounded: Boolean get() = botPlayer.isGrounded
    val isAttacking: Boolean get() = botPlayer.isAttacking

    /**
     * Resets the bot spawn position on stage.
     */
    fun setSpawnPosition(x: Float, y: Float) {
        botPlayer.setSpawnPosition(x, y)
        inputEmulator.reset()
        currentState = AICombatState.NEUTRAL
        stateTimerFrames = 0
        attackCooldownFrames = 0
    }

    /**
     * Deterministic 60Hz AI and Physics Execution Tick:
     * 1. Evaluates environment, distances, and threats via AISpatialSensor.
     * 2. Evaluates the Behavior Tree / State Machine to produce high-level intents.
     * 3. Emulates human reaction latency and noisy stick inputs into AIInputEmulator.
     * 4. Updates IntegratedPlayerController physics, combat frame phases, and skeletal rig.
     */
    fun fixedUpdate(
        dt: Float,
        targetBounds: AABB,
        targetVelocity: Vector2,
        targetIsOffStage: Boolean,
        activeThreatHitboxes: List<CombatHitbox>,
        stage: BrawlStage
    ) {
        // 1. Evaluate spatial environment & perception (Line-of-Sight, zones, threats)
        val perception = sensor.evaluateEnvironment(
            botBounds = botPlayer.bounds,
            botVelocity = botPlayer.velocity,
            targetBounds = targetBounds,
            targetVelocity = targetVelocity,
            stage = stage,
            activeThreatHitboxes = activeThreatHitboxes
        )

        // 2. Decrement internal frame timers
        if (attackCooldownFrames > 0) attackCooldownFrames--
        stateTimerFrames++

        // 3. Clear immediate staging intent
        val intent = inputEmulator.stagingIntent
        intent.reset()

        // 4. Emergency Threat Evasion (Dynamic Dodge Reaction)
        if (perception.incomingThreatDetected && !botPlayer.isInvincible && attackCooldownFrames <= 0) {
            val roll = random.nextFloat()
            if (roll < config.dodgeProbability) {
                // Execute dodge away from threat or spot dodge
                intent.requestDash = true
                intent.stickX = if (perception.distanceToTargetX > 0f) -0.8f else 0.8f
                intent.stickY = 0f
            }
        }

        // 5. High-Level Situational State Transitions
        evaluateStateTransitions(perception, targetIsOffStage)

        // 6. Execute State Subroutine
        when (currentState) {
            AICombatState.DEFENSIVE_RECOVERY -> executeRecovery(perception, intent, stage)
            AICombatState.EDGE_GUARDING -> executeEdgeGuard(perception, intent, stage)
            AICombatState.AGGRESSIVE -> executeAggressiveCombat(perception, intent)
            AICombatState.NEUTRAL -> executeNeutralSpacing(perception, intent, stage)
        }

        // 7. Tick Input Emulator (applies reaction-time buffer and noise)
        val snapshot = inputEmulator.tick(dt)

        // 8. Feed emulated inputs directly into bot's input queue
        botPlayer.kinematicController.inputQueue.onRawInputUpdated(snapshot)

        // 9. Execute Integrated Player Controller physics, swept collision, combat & animation
        botPlayer.fixedUpdate(dt, stage.colliders)
    }

    /**
     * Evaluates multi-tier situational transitions based on environmental perception.
     */
    private fun evaluateStateTransitions(
        perception: AISensorSnapshot,
        targetIsOffStage: Boolean
    ) {
        // Priority 1: If bot is off-stage or in blast zone, immediately switch to RECOVERY
        if (perception.isOffStage || !botPlayer.isGrounded && perception.distanceToNearestLedgeY < -40f) {
            if (currentState != AICombatState.DEFENSIVE_RECOVERY) {
                currentState = AICombatState.DEFENSIVE_RECOVERY
                stateTimerFrames = 0
            }
            return
        }

        // Priority 2: If bot is safely on stage, but target is knocked off-stage, switch to EDGE_GUARDING
        if (targetIsOffStage && (perception.botZone == StageZone.LEFT_LEDGE || perception.botZone == StageZone.RIGHT_LEDGE || perception.botZone == StageZone.CENTER_STAGE)) {
            val roll = random.nextFloat()
            if (roll < config.edgeGuardAggressiveness) {
                if (currentState != AICombatState.EDGE_GUARDING) {
                    currentState = AICombatState.EDGE_GUARDING
                    stateTimerFrames = 0
                }
                return
            }
        }

        // Priority 3: If target is within striking distance, switch to AGGRESSIVE
        val strikeRangeX = 130f
        val strikeRangeY = 70f
        val inStrikingDistance = abs(perception.distanceToTargetX) <= strikeRangeX && abs(perception.distanceToTargetY) <= strikeRangeY

        if (inStrikingDistance && perception.hasLineOfSight) {
            if (currentState != AICombatState.AGGRESSIVE) {
                currentState = AICombatState.AGGRESSIVE
                stateTimerFrames = 0
            }
            return
        }

        // Priority 4: Default to NEUTRAL spacing and positioning
        if (currentState != AICombatState.NEUTRAL) {
            currentState = AICombatState.NEUTRAL
            stateTimerFrames = 0
        }
    }

    /**
     * Defensive Recovery Subroutine:
     * Executes horizontal air-dashing, wall-clinging, and recovery attacks to return to stage.
     */
    private fun executeRecovery(
        perception: AISensorSnapshot,
        intent: AIIntent,
        stage: BrawlStage
    ) {
        val toLedgeX = perception.distanceToNearestLedgeX
        val toLedgeY = perception.distanceToNearestLedgeY
        val dirToStage = sign(toLedgeX)

        // Steer horizontal drift towards main platform
        intent.stickX = dirToStage

        // Case A: Wall Cling / Slide Contact
        val state = botPlayer.kinematicController.stateMachine.getCurrentStateType()
        if (state == CharacterStateType.WALL_CLING || state == CharacterStateType.WALL_SLIDE) {
            // Jump away from wall up onto the ledge
            intent.requestJump = true
            intent.stickX = dirToStage
            return
        }

        // Case B: Critical Fall near blast zone (Below stage floor)
        if (toLedgeY < -60f || botPlayer.position.y > stage.mainPlatformMinY + 120f) {
            // If high negative velocity, trigger aerial recovery attack or jump
            if (botPlayer.kinematicController.remainingAirJumps > 0) {
                intent.requestJump = true
            } else if (!botPlayer.isAttacking) {
                // Use vertical Recovery move (Up + Heavy in air)
                intent.stickX = dirToStage * 0.4f
                intent.stickY = -0.8f
                intent.requestHeavyAttack = true
            }
        } else if (abs(toLedgeX) > 120f) {
            // Long horizontal gap: Air-dash toward stage
            if (!botPlayer.isAttacking && abs(botPlayer.velocity.x) < 400f) {
                intent.requestDash = true
                intent.stickX = dirToStage
            }
        }
    }

    /**
     * Edge-Guarding Subroutine:
     * Patrols the ledge and times down-attacks, ground pounds, or side-airs to intercept recovery.
     */
    private fun executeEdgeGuard(
        perception: AISensorSnapshot,
        intent: AIIntent,
        stage: BrawlStage
    ) {
        val targetX = perception.distanceToTargetX
        val targetY = perception.distanceToTargetY

        // Position on the ledge closest to the target
        val targetLedgeX = if (targetX > 0f) stage.mainPlatformMaxX - 25f else stage.mainPlatformMinX + 25f
        val deltaToLedge = targetLedgeX - botPlayer.position.x

        if (abs(deltaToLedge) > 20f) {
            // Move toward ledge lip
            intent.stickX = sign(deltaToLedge) * 0.7f
        } else {
            // Face the recovering opponent
            intent.stickX = sign(targetX) * 0.1f

            // Target is recovering low beneath the ledge
            if (targetY > 60f && abs(targetX) < 80f && attackCooldownFrames <= 0) {
                val roll = random.nextFloat()
                if (roll < 0.5f) {
                    // Down Light / Down Sig at ledge lip
                    intent.stickY = 0.8f
                    intent.requestLightAttack = true
                    attackCooldownFrames = 25
                } else if (botPlayer.isGrounded) {
                    // Leap off with Ground Pound
                    intent.requestJump = true
                    intent.stickY = 0.8f
                    intent.requestHeavyAttack = true
                    attackCooldownFrames = 30
                }
            } else if (abs(targetX) < 110f && abs(targetY) < 50f && attackCooldownFrames <= 0) {
                // Side-Air / Side-Sig off the ledge
                intent.stickX = sign(targetX)
                intent.requestLightAttack = true
                attackCooldownFrames = 20
            }
        }
    }

    /**
     * Aggressive Combat Subroutine:
     * Selects Brawlhalla attacks (Light combo openers or Signatures) based on relative vector.
     */
    private fun executeAggressiveCombat(
        perception: AISensorSnapshot,
        intent: AIIntent
    ) {
        val dx = perception.distanceToTargetX
        val dy = perception.distanceToTargetY
        val facing = sign(dx)

        // Steer toward opponent
        intent.stickX = facing * 0.8f

        if (attackCooldownFrames > 0 || botPlayer.isAttacking) {
            // Maintain slide momentum or spacing
            return
        }

        val roll = random.nextFloat()
        if (roll > config.attackCommitmentRate) return

        // Tactical Decision Tree:
        if (botPlayer.isGrounded) {
            // Grounded Combat Choices
            when {
                // Opponent is directly above (Anti-Air): Neutral Sig or Down Light
                dy < -35f -> {
                    if (random.nextFloat() < 0.6f) {
                        intent.stickX = 0f
                        intent.stickY = -0.7f
                        intent.requestHeavyAttack = true // Neutral Sig
                    } else {
                        intent.stickY = 0.7f
                        intent.requestLightAttack = true // Down Light
                    }
                    attackCooldownFrames = 24
                }

                // Opponent is close horizontally: Side Light or Side Sig
                abs(dx) in 30f..110f -> {
                    val useSig = random.nextFloat() < config.signatureChargeProbability
                    intent.stickX = facing
                    intent.stickY = 0f
                    if (useSig) {
                        intent.requestHeavyAttack = true // Side Sig
                    } else {
                        intent.requestLightAttack = true // Side Light (Sliding slash)
                    }
                    attackCooldownFrames = 20
                }

                // Point-blank: Neutral Light jab
                else -> {
                    intent.stickX = 0f
                    intent.stickY = 0f
                    intent.requestLightAttack = true // Neutral Light
                    attackCooldownFrames = 18
                }
            }
        } else {
            // Aerial Combat Choices
            when {
                // Target is below in air: Down Air
                dy > 30f -> {
                    intent.stickX = facing * 0.3f
                    intent.stickY = 0.8f
                    intent.requestLightAttack = true // Down Air
                    attackCooldownFrames = 22
                }

                // Target is level horizontally: Side Air
                abs(dx) > 35f -> {
                    intent.stickX = facing
                    intent.stickY = 0f
                    intent.requestLightAttack = true // Side Air
                    attackCooldownFrames = 20
                }

                // Target is above: Neutral Air
                else -> {
                    intent.stickX = 0f
                    intent.stickY = 0f
                    intent.requestLightAttack = true // Neutral Air
                    attackCooldownFrames = 18
                }
            }
        }
    }

    /**
     * Neutral Spacing Subroutine:
     * Micro-spaces, baits, and navigates toward center-stage.
     */
    private fun executeNeutralSpacing(
        perception: AISensorSnapshot,
        intent: AIIntent,
        stage: BrawlStage
    ) {
        val dx = perception.distanceToTargetX
        val dy = perception.distanceToTargetY

        // Target ideal distance: ~120px with micro-spacing variance
        val idealDistance = 120f + (sinWave(stateTimerFrames) * config.microSpacingVariance)
        val currentDistX = abs(dx)

        if (currentDistX > idealDistance + 30f) {
            // Approach target
            intent.stickX = sign(dx) * 0.75f
        } else if (currentDistX < idealDistance - 30f) {
            // Back away / Bait
            intent.stickX = -sign(dx) * 0.65f
        } else {
            // Micro-spacing feint
            val feintDir = if ((stateTimerFrames / 20) % 2 == 0) sign(dx) else -sign(dx)
            intent.stickX = feintDir * 0.35f
        }

        // Jump onto soft platforms if target is elevated
        if (dy < -80f && botPlayer.isGrounded && stateTimerFrames % 60 == 0) {
            intent.requestJump = true
        }
    }

    private fun sinWave(frame: Int): Float {
        return kotlin.math.sin((frame * 0.1).toDouble()).toFloat()
    }

    fun applyHit(resolution: HitResolution) {
        botPlayer.applyHit(resolution)
        attackCooldownFrames = resolution.hitStunFrames + 4
        currentState = AICombatState.DEFENSIVE_RECOVERY
    }
}
