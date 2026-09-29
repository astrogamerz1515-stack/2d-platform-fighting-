package com.example.combat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.controller.CharacterMovementConfig
import com.example.controller.KinematicCharacterController
import com.example.engine.animation.AnimationDataProfile
import com.example.engine.animation.CharacterAnimationBridge
import com.example.engine.collision.AABB
import com.example.engine.collision.ObstacleCollider
import com.example.engine.input.InputBufferQueue
import com.example.engine.math.Vector2
import com.example.fsm.CharacterStateType

/**
 * IntegratedPlayerController Class:
 * Combines deterministic kinematic physics, Minkowski Swept-AABB environmental collision,
 * non-blocking 1:1 Brawlhalla combat status tracking, frame-perfect hitbox generation,
 * and 2D skeletal animation synthesis into a unified, high-performance controller.
 */
class IntegratedPlayerController(
    val config: CharacterMovementConfig = CharacterMovementConfig(),
    val inputQueue: InputBufferQueue = InputBufferQueue(),
    characterId: String = "valkyrie"
) {

    // Decoupled Physics Subsystem
    val kinematicController: KinematicCharacterController = KinematicCharacterController(
        config = config,
        inputQueue = inputQueue
    )

    // Decoupled Combat Subsystem
    val combatEngine: CombatEngine = CombatEngine()

    // Decoupled 2D Skeletal Animation Synthesizer
    val animationBridge: CharacterAnimationBridge = CharacterAnimationBridge(
        profile = AnimationDataProfile.createStandardFighterProfile(characterId)
    )

    // Convenient Spatial & State Accessors
    val position: Vector2 get() = kinematicController.position
    val velocity: Vector2 get() = kinematicController.velocity
    val bounds: AABB get() = kinematicController.bounds

    val facingDirection: Float get() = kinematicController.facingDirection
    val isGrounded: Boolean get() = kinematicController.isGrounded
    val isInvincible: Boolean get() = kinematicController.isInvincible

    val isAttacking: Boolean get() = combatEngine.isAttacking()
    val currentCombatPhase: CombatPhase get() = combatEngine.currentPhase
    val activeCombatMove: CombatMoveDefinition? get() = combatEngine.activeMove

    /**
     * Teleports or initializes character position cleanly.
     */
    fun setSpawnPosition(x: Float, y: Float) {
        kinematicController.setSpawnPosition(x, y)
        combatEngine.cancelAttackImmediately()
    }

    /**
     * Deterministic 60Hz Physics & Combat Tick:
     * Decouples the Movement Engine from the Combat Subsystem.
     * Attacks and movement NEVER mutually lock each other out.
     */
    fun fixedUpdate(dt: Float, obstacles: List<ObstacleCollider>) {
        // 1. Fixed tick for Combat Engine (processes 4-6 frame input buffer & directional move mapping)
        combatEngine.fixedUpdate(
            controller = kinematicController,
            animationBridge = animationBridge,
            dt = dt
        )

        // 2. Fixed tick for Kinematic Physics (Swept-AABB, gravity, sliding momentum, wall mechanics)
        // If an attack is active, entry momentum and slide friction are continuously integrated
        kinematicController.fixedUpdate(dt, obstacles)

        // 3. Fixed tick for Skeletal Animation Bridge (forward kinematics bone matrices & poses)
        animationBridge.fixedUpdate(kinematicController, dt)
    }

    /**
     * Executes damage, hitstun, and knockback vector onto this character.
     * Disrupts any active attack immediately without animation locking.
     */
    fun applyHit(resolution: HitResolution) {
        // 1. Interrupt active combat attack
        combatEngine.cancelAttackImmediately()

        // 2. Apply knockback launch velocity
        velocity.set(resolution.knockbackVelocityX, resolution.knockbackVelocityY)

        // 3. Transition Kinematic FSM to KNOCKBACK or HURT
        kinematicController.stateMachine.changeState(CharacterStateType.KNOCKBACK)

        // 4. Update Animation Bridge to flinch/knockback pose
        animationBridge.syncWithFsmState(
            state = CharacterStateType.KNOCKBACK,
            vel = velocity,
            facingDir = facingDirection,
            isGrounded = isGrounded,
            stickX = 0f,
            stickY = 0f,
            dt = 1f / 60f,
            groundMaxSpeed = config.maxGroundSpeed
        )
    }

    /**
     * Canvas Renderer:
     * Draws the complete 2D layered cutout skeletal rig, weapon slashing arcs,
     * and dynamic combat hitboxes.
     */
    fun render(
        drawScope: DrawScope,
        worldToScreen: (Float, Float) -> Offset,
        zoom: Float,
        themeColorArgb: Long
    ) {
        // 1. Render 2D Skeletal Pose & Visual Trails
        animationBridge.render(
            drawScope = drawScope,
            worldToScreen = worldToScreen,
            zoom = zoom,
            themeColorArgb = themeColorArgb,
            isInvincible = kinematicController.isInvincible
        )

        // 2. Render Active Combat Hitboxes
        combatEngine.hitboxSpawner.render(
            drawScope = drawScope,
            worldToScreen = worldToScreen,
            zoom = zoom
        )
    }
}
