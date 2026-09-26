package com.example.controller

import com.example.engine.collision.AABB
import com.example.engine.collision.ColliderType
import com.example.engine.collision.ObstacleCollider
import com.example.engine.collision.SweptCollision
import com.example.engine.input.BufferableAction
import com.example.engine.input.InputBufferQueue
import com.example.engine.input.InputButton
import com.example.engine.math.Vector2
import com.example.fsm.CharacterStateType
import com.example.fsm.ICharacterState
import com.example.fsm.StateMachine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Production-ready Kinematic Character Controller for 2D Platform Fighters.
 * Features frame-perfect deterministic physics, continuous collision resolution (CCD),
 * strict Finite State Machine, variable jump height, fast-falling, dashing, and wall cling/slide.
 */
class KinematicCharacterController(
    val config: CharacterMovementConfig = CharacterMovementConfig(),
    val inputQueue: InputBufferQueue = InputBufferQueue()
) {
    // Spatial Transform & Collision
    val position = Vector2(0f, 0f)
    val velocity = Vector2(0f, 0f)
    val bounds = AABB()

    // Facing Direction (+1.0 = Right, -1.0 = Left)
    var facingDirection: Float = 1.0f
        private set

    // Environmental Contact Flags
    var isGrounded: Boolean = false
        private set
    var isTouchingWallLeft: Boolean = false
        private set
    var isTouchingWallRight: Boolean = false
        private set
    var isTouchingCeiling: Boolean = false
        private set

    // Aerial & Jump Counters
    var remainingAirJumps: Int = config.maxAirJumps
        private set
    var coyoteFramesRemaining: Int = 0
        private set
    var isFastFalling: Boolean = false
        private set
    var jumpHoldDurationFrames: Int = 0
        private set

    // Dash / Dodge State
    var dashCooldownRemaining: Int = 0
        private set
    var isInvincible: Boolean = false
        private set

    // Wall Mechanics (Brawlhalla mechanics)
    var wallClingFramesRemaining: Int = 0
        private set
    var wallSlipTouches: Int = 0
        private set
    var wallNormalDirection: Float = 0f
        private set

    // One-Way Platform drop-through timer
    var dropThroughTimerFrames: Int = 0
        private set

    // State Machine
    val stateMachine: StateMachine

    init {
        // Initialize AABB around origin
        bounds.setFromCenter(
            centerX = position.x,
            centerY = position.y,
            halfWidth = config.colliderWidth * 0.5f,
            halfHeight = config.colliderHeight * 0.5f
        )

        // Build State Registry
        val states = mapOf<CharacterStateType, ICharacterState>(
            CharacterStateType.IDLE to IdleState(this),
            CharacterStateType.RUN to RunState(this),
            CharacterStateType.JUMP to JumpState(this),
            CharacterStateType.FALL to FallState(this),
            CharacterStateType.DASH to DashState(this),
            CharacterStateType.WALL_CLING to WallClingState(this),
            CharacterStateType.WALL_SLIDE to WallSlideState(this),
            CharacterStateType.HURT to HurtState(this),
            CharacterStateType.KNOCKBACK to KnockbackState(this)
        )

        stateMachine = StateMachine(states, CharacterStateType.FALL)
    }

    /**
     * Teleports or initializes character position cleanly.
     */
    fun setSpawnPosition(x: Float, y: Float) {
        position.set(x, y)
        velocity.set(0f, 0f)
        bounds.setFromCenter(
            centerX = position.x,
            centerY = position.y,
            halfWidth = config.colliderWidth * 0.5f,
            halfHeight = config.colliderHeight * 0.5f
        )
    }

    /**
     * Fixed physics tick (called strictly at 60Hz: dt = 1/60f).
     */
    fun fixedUpdate(dt: Float, obstacles: List<ObstacleCollider>) {
        // Step 1: Advance input buffer frame counters
        inputQueue.tickPhysicsFrame()

        // Step 2: Decay frame timers
        if (dashCooldownRemaining > 0) dashCooldownRemaining--
        if (dropThroughTimerFrames > 0) dropThroughTimerFrames--
        if (coyoteFramesRemaining > 0) coyoteFramesRemaining--

        // Step 3: Run active state logic (accelerations & transitions)
        stateMachine.tick(dt)

        // Step 4: Reset contact flags before collision detection
        val wasGroundedBefore = isGrounded
        isGrounded = false
        isTouchingWallLeft = false
        isTouchingWallRight = false
        isTouchingCeiling = false

        // Check if player requested drop-through on one-way platform (Down + Jump)
        val isDroppingThrough = dropThroughTimerFrames > 0

        // Step 5: Execute continuous swept collision & slide
        SweptCollision.moveAndSlide(
            box = bounds,
            position = position,
            velocity = velocity,
            dt = dt,
            obstacles = obstacles,
            oneWayDropThrough = isDroppingThrough,
            onGrounded = { collider ->
                isGrounded = true
                isFastFalling = false
                remainingAirJumps = config.maxAirJumps
                wallSlipTouches = 0 // Wall slip resets upon landing on solid ground
                coyoteFramesRemaining = 0
            },
            onWallContact = { normalX, collider ->
                if (normalX > 0.5f) {
                    isTouchingWallLeft = true
                    wallNormalDirection = 1.0f
                } else if (normalX < -0.5f) {
                    isTouchingWallRight = true
                    wallNormalDirection = -1.0f
                }
            },
            onCeilingContact = { collider ->
                isTouchingCeiling = true
                if (velocity.y < 0f) {
                    velocity.y = 0f
                }
            }
        )

        // Handle leaving ground (start coyote frames)
        if (wasGroundedBefore && !isGrounded && velocity.y >= 0f) {
            coyoteFramesRemaining = config.coyoteFrames
        }

        // Keep bounds centered precisely on position
        bounds.setFromCenter(
            centerX = position.x,
            centerY = position.y,
            halfWidth = config.colliderWidth * 0.5f,
            halfHeight = config.colliderHeight * 0.5f
        )
    }

    // Facing direction helper
    fun updateFacingFromStick(stickX: Float) {
        if (stickX > 0.15f) {
            facingDirection = 1.0f
        } else if (stickX < -0.15f) {
            facingDirection = -1.0f
        }
    }

    // Jump Execution Helper
    fun performGroundJump() {
        velocity.y = config.jumpImpulse
        jumpHoldDurationFrames = 0
        isFastFalling = false
        coyoteFramesRemaining = 0
        isGrounded = false
    }

    fun performAirJump() {
        if (remainingAirJumps > 0) {
            remainingAirJumps--
            velocity.y = config.airJumpImpulse
            jumpHoldDurationFrames = 0
            isFastFalling = false
        }
    }

    fun performWallJump(wallDir: Float) {
        // Push horizontally away from the wall
        velocity.x = -wallDir * config.wallJumpVelocityX
        velocity.y = config.wallJumpVelocityY
        facingDirection = -wallDir
        jumpHoldDurationFrames = 0
        isFastFalling = false
        wallClingFramesRemaining = 0
    }

    fun initiateDropThrough() {
        dropThroughTimerFrames = 12 // Ignore one-way collisions for 12 frames
        velocity.y = max(100f, velocity.y)
        isGrounded = false
    }

    fun applyGroundHorizontalMovement(stickX: Float, dt: Float) {
        val targetSpeed = stickX * config.maxGroundSpeed
        val currentSpeed = velocity.x

        if (abs(stickX) > 0.08f) {
            // Turning around gives responsiveness boost
            val isReversing = (currentSpeed > 0f && stickX < 0f) || (currentSpeed < 0f && stickX > 0f)
            val accel = if (isReversing) config.groundAcceleration * config.turnAroundBoost else config.groundAcceleration
            velocity.x = moveTowards(currentSpeed, targetSpeed, accel * dt)
            updateFacingFromStick(stickX)
        } else {
            // Snappy ground friction
            velocity.x = moveTowards(currentSpeed, 0f, config.groundFriction * dt)
        }
    }

    fun applyAirHorizontalMovement(stickX: Float, dt: Float) {
        val currentSpeed = velocity.x
        if (abs(stickX) > 0.08f) {
            val targetSpeed = stickX * config.maxAirSpeed
            velocity.x = moveTowards(currentSpeed, targetSpeed, config.airAcceleration * dt)
            updateFacingFromStick(stickX)
        } else {
            // Air drag
            velocity.x = moveTowards(currentSpeed, 0f, config.airFriction * dt)
        }
    }

    fun applyVerticalGravity(dt: Float) {
        val jumpHeld = inputQueue.isButtonHeld(InputButton.JUMP)
        var appliedGravity = config.gravity

        // Jump Cut: If releasing jump during ascent, increase gravity to cut the jump short
        if (!jumpHeld && velocity.y < 0f && jumpHoldDurationFrames > config.minJumpFrames) {
            appliedGravity *= config.jumpCutMultiplier
        }

        // Fast Falling
        if (isFastFalling) {
            appliedGravity = config.fastFallGravity
            velocity.y = moveTowards(velocity.y, config.fastFallMaxSpeed, appliedGravity * dt)
        } else {
            velocity.y = moveTowards(velocity.y, config.maxFallSpeed, appliedGravity * dt)
        }
    }

    private fun moveTowards(current: Float, target: Float, maxDelta: Float): Float {
        return if (abs(target - current) <= maxDelta) {
            target
        } else {
            current + sign(target - current) * maxDelta
        }
    }

    // ==========================================
    // INNER STATE CLASSES IMPLEMENTATION
    // ==========================================

    class IdleState(private val ctx: KinematicCharacterController) : ICharacterState {
        override val type: CharacterStateType = CharacterStateType.IDLE

        override fun onEnter(previousState: CharacterStateType) {
            ctx.isFastFalling = false
        }

        override fun onFixedUpdate(dt: Float): CharacterStateType? {
            val stickX = ctx.inputQueue.getStickX()
            val stickY = ctx.inputQueue.getStickY()

            // Check Drop-through on one-way platforms
            if (stickY > 0.6f && ctx.inputQueue.consumeAction(BufferableAction.JUMP)) {
                ctx.initiateDropThrough()
                return CharacterStateType.FALL
            }

            // Check Jump buffer
            if (ctx.inputQueue.consumeAction(BufferableAction.JUMP)) {
                ctx.performGroundJump()
                return CharacterStateType.JUMP
            }

            // Check Dash buffer
            if (ctx.dashCooldownRemaining <= 0 && ctx.inputQueue.consumeAction(BufferableAction.DASH)) {
                return CharacterStateType.DASH
            }

            // Check transition to Fall (e.g. platform moved or walked off edge)
            if (!ctx.isGrounded) {
                return CharacterStateType.FALL
            }

            // Check transition to Run
            if (abs(stickX) > 0.12f) {
                return CharacterStateType.RUN
            }

            // Apply friction
            ctx.applyGroundHorizontalMovement(0f, dt)
            return null
        }

        override fun onExit(nextState: CharacterStateType) {}
    }

    class RunState(private val ctx: KinematicCharacterController) : ICharacterState {
        override val type: CharacterStateType = CharacterStateType.RUN

        override fun onEnter(previousState: CharacterStateType) {
            ctx.isFastFalling = false
        }

        override fun onFixedUpdate(dt: Float): CharacterStateType? {
            val stickX = ctx.inputQueue.getStickX()
            val stickY = ctx.inputQueue.getStickY()

            // Check Drop-through on one-way platforms
            if (stickY > 0.6f && ctx.inputQueue.consumeAction(BufferableAction.JUMP)) {
                ctx.initiateDropThrough()
                return CharacterStateType.FALL
            }

            // Check Jump buffer
            if (ctx.inputQueue.consumeAction(BufferableAction.JUMP)) {
                ctx.performGroundJump()
                return CharacterStateType.JUMP
            }

            // Check Dash buffer
            if (ctx.dashCooldownRemaining <= 0 && ctx.inputQueue.consumeAction(BufferableAction.DASH)) {
                return CharacterStateType.DASH
            }

            // Check transition to Fall
            if (!ctx.isGrounded) {
                return CharacterStateType.FALL
            }

            // Check return to Idle
            if (abs(stickX) <= 0.08f && abs(ctx.velocity.x) < 8.0f) {
                ctx.velocity.x = 0f
                return CharacterStateType.IDLE
            }

            // Apply ground run physics
            ctx.applyGroundHorizontalMovement(stickX, dt)
            return null
        }

        override fun onExit(nextState: CharacterStateType) {}
    }

    class JumpState(private val ctx: KinematicCharacterController) : ICharacterState {
        override val type: CharacterStateType = CharacterStateType.JUMP

        override fun onEnter(previousState: CharacterStateType) {
            ctx.jumpHoldDurationFrames = 0
        }

        override fun onFixedUpdate(dt: Float): CharacterStateType? {
            ctx.jumpHoldDurationFrames++
            val stickX = ctx.inputQueue.getStickX()
            val stickY = ctx.inputQueue.getStickY()

            // Fast fall check (downward stick flick)
            if (stickY > ctx.config.fastFallThresholdStickY && ctx.velocity.y > -200f) {
                ctx.isFastFalling = true
            }

            // Double jump check in air
            if (ctx.inputQueue.consumeAction(BufferableAction.JUMP)) {
                if (ctx.remainingAirJumps > 0) {
                    ctx.performAirJump()
                    return CharacterStateType.JUMP
                }
            }

            // Air Dodge / Dash check
            if (ctx.dashCooldownRemaining <= 0 && ctx.inputQueue.consumeAction(BufferableAction.DASH)) {
                return CharacterStateType.DASH
            }

            // Wall Cling / Slide check
            if (ctx.isTouchingWallLeft && stickX < -0.3f) {
                ctx.wallNormalDirection = 1.0f
                return CharacterStateType.WALL_CLING
            }
            if (ctx.isTouchingWallRight && stickX > 0.3f) {
                ctx.wallNormalDirection = -1.0f
                return CharacterStateType.WALL_CLING
            }

            // Apply aerial physics
            ctx.applyAirHorizontalMovement(stickX, dt)
            ctx.applyVerticalGravity(dt)

            // Landed back on ground
            if (ctx.isGrounded) {
                return if (abs(stickX) > 0.12f) CharacterStateType.RUN else CharacterStateType.IDLE
            }

            // Reached apex of jump, begin falling
            if (ctx.velocity.y >= 0f) {
                return CharacterStateType.FALL
            }

            return null
        }

        override fun onExit(nextState: CharacterStateType) {}
    }

    class FallState(private val ctx: KinematicCharacterController) : ICharacterState {
        override val type: CharacterStateType = CharacterStateType.FALL

        override fun onEnter(previousState: CharacterStateType) {}

        override fun onFixedUpdate(dt: Float): CharacterStateType? {
            val stickX = ctx.inputQueue.getStickX()
            val stickY = ctx.inputQueue.getStickY()

            // Fast fall trigger
            if (stickY > ctx.config.fastFallThresholdStickY && !ctx.isFastFalling) {
                ctx.isFastFalling = true
                ctx.velocity.y = max(ctx.velocity.y, 450f)
            }

            // Jump handling (Coyote time ground jump OR Air jump)
            if (ctx.inputQueue.consumeAction(BufferableAction.JUMP)) {
                if (ctx.coyoteFramesRemaining > 0) {
                    ctx.performGroundJump()
                    return CharacterStateType.JUMP
                } else if (ctx.remainingAirJumps > 0) {
                    ctx.performAirJump()
                    return CharacterStateType.JUMP
                }
            }

            // Aerial Dodge / Dash
            if (ctx.dashCooldownRemaining <= 0 && ctx.inputQueue.consumeAction(BufferableAction.DASH)) {
                return CharacterStateType.DASH
            }

            // Wall Contact Detection
            if (ctx.isTouchingWallLeft && stickX < -0.2f) {
                ctx.wallNormalDirection = 1.0f
                return if (ctx.wallSlipTouches < ctx.config.wallSlipMaxTouches) {
                    CharacterStateType.WALL_CLING
                } else {
                    CharacterStateType.WALL_SLIDE
                }
            }
            if (ctx.isTouchingWallRight && stickX > 0.2f) {
                ctx.wallNormalDirection = -1.0f
                return if (ctx.wallSlipTouches < ctx.config.wallSlipMaxTouches) {
                    CharacterStateType.WALL_CLING
                } else {
                    CharacterStateType.WALL_SLIDE
                }
            }

            // Apply aerial physics
            ctx.applyAirHorizontalMovement(stickX, dt)
            ctx.applyVerticalGravity(dt)

            // Landing check
            if (ctx.isGrounded) {
                ctx.isFastFalling = false
                return if (abs(stickX) > 0.12f) CharacterStateType.RUN else CharacterStateType.IDLE
            }

            return null
        }

        override fun onExit(nextState: CharacterStateType) {}
    }

    class DashState(private val ctx: KinematicCharacterController) : ICharacterState {
        override val type: CharacterStateType = CharacterStateType.DASH
        private var dashFrame: Int = 0
        private var dashDirection: Float = 1.0f

        override fun onEnter(previousState: CharacterStateType) {
            dashFrame = 0
            ctx.dashCooldownRemaining = ctx.config.dashCooldownFrames
            val stickX = ctx.inputQueue.getStickX()

            dashDirection = if (abs(stickX) > 0.1f) sign(stickX) else ctx.facingDirection

            val isBackDash = dashDirection != ctx.facingDirection
            val speed = if (isBackDash) ctx.config.dashSpeed * ctx.config.backDashPenalty else ctx.config.dashSpeed

            ctx.velocity.x = dashDirection * speed
            // If airborne, zero vertical momentum temporarily (chase dodge)
            if (!ctx.isGrounded) {
                ctx.velocity.y = 0f
            }
        }

        override fun onFixedUpdate(dt: Float): CharacterStateType? {
            dashFrame++

            // Invulnerability frame window
            ctx.isInvincible = dashFrame in ctx.config.dashInvincibleStartFrame..ctx.config.dashInvincibleEndFrame

            // Allow Jump-Cancelling a dash (Dash-Jump mechanic)
            if (ctx.inputQueue.consumeAction(BufferableAction.JUMP)) {
                ctx.isInvincible = false
                if (ctx.isGrounded) {
                    ctx.performGroundJump()
                    return CharacterStateType.JUMP
                } else if (ctx.remainingAirJumps > 0) {
                    ctx.performAirJump()
                    return CharacterStateType.JUMP
                }
            }

            // Dash duration completed
            if (dashFrame >= ctx.config.dashDurationFrames) {
                return if (ctx.isGrounded) {
                    val stickX = ctx.inputQueue.getStickX()
                    if (abs(stickX) > 0.12f) CharacterStateType.RUN else CharacterStateType.IDLE
                } else {
                    CharacterStateType.FALL
                }
            }

            return null
        }

        override fun onExit(nextState: CharacterStateType) {
            ctx.isInvincible = false
        }
    }

    class WallClingState(private val ctx: KinematicCharacterController) : ICharacterState {
        override val type: CharacterStateType = CharacterStateType.WALL_CLING

        override fun onEnter(previousState: CharacterStateType) {
            ctx.wallClingFramesRemaining = ctx.config.wallClingMaxDurationFrames
            ctx.velocity.x = 0f
            ctx.velocity.y = 0f
            ctx.isFastFalling = false
            ctx.wallSlipTouches++
        }

        override fun onFixedUpdate(dt: Float): CharacterStateType? {
            val stickX = ctx.inputQueue.getStickX()

            // Wall Jump check
            if (ctx.inputQueue.consumeAction(BufferableAction.JUMP)) {
                ctx.performWallJump(if (ctx.isTouchingWallLeft) -1.0f else 1.0f)
                return CharacterStateType.JUMP
            }

            // Check if grounded
            if (ctx.isGrounded) {
                return CharacterStateType.IDLE
            }

            // Check if player lets go of wall (pointing stick away from wall)
            val wallSide = if (ctx.isTouchingWallLeft) -1.0f else 1.0f
            if (stickX * wallSide < -0.2f || (!ctx.isTouchingWallLeft && !ctx.isTouchingWallRight)) {
                return CharacterStateType.FALL
            }

            ctx.wallClingFramesRemaining--
            if (ctx.wallClingFramesRemaining <= 0) {
                return CharacterStateType.WALL_SLIDE
            }

            return null
        }

        override fun onExit(nextState: CharacterStateType) {}
    }

    class WallSlideState(private val ctx: KinematicCharacterController) : ICharacterState {
        override val type: CharacterStateType = CharacterStateType.WALL_SLIDE

        override fun onEnter(previousState: CharacterStateType) {
            ctx.isFastFalling = false
        }

        override fun onFixedUpdate(dt: Float): CharacterStateType? {
            val stickX = ctx.inputQueue.getStickX()

            // Wall Jump
            if (ctx.inputQueue.consumeAction(BufferableAction.JUMP)) {
                ctx.performWallJump(if (ctx.isTouchingWallLeft) -1.0f else 1.0f)
                return CharacterStateType.JUMP
            }

            // Check if grounded
            if (ctx.isGrounded) {
                return CharacterStateType.IDLE
            }

            // Check if lost wall contact
            if (!ctx.isTouchingWallLeft && !ctx.isTouchingWallRight) {
                return CharacterStateType.FALL
            }

            // Damp fall speed along the wall
            val maxSlideSpeed = if (ctx.wallSlipTouches >= ctx.config.wallSlipMaxTouches) {
                ctx.config.maxFallSpeed // Wall slip! slides down fast
            } else {
                ctx.config.wallSlideMaxFallSpeed
            }

            ctx.velocity.y = min(ctx.velocity.y + ctx.config.gravity * 0.35f * dt, maxSlideSpeed)
            ctx.velocity.x = 0f

            return null
        }

        override fun onExit(nextState: CharacterStateType) {}
    }

    class HurtState(private val ctx: KinematicCharacterController) : ICharacterState {
        override val type: CharacterStateType = CharacterStateType.HURT
        private var hitstunRemainingFrames: Int = 0

        fun setHitstun(frames: Int) {
            hitstunRemainingFrames = frames
        }

        override fun onEnter(previousState: CharacterStateType) {}

        override fun onFixedUpdate(dt: Float): CharacterStateType? {
            hitstunRemainingFrames--
            if (hitstunRemainingFrames <= 0) {
                return if (ctx.isGrounded) CharacterStateType.IDLE else CharacterStateType.FALL
            }
            return null
        }

        override fun onExit(nextState: CharacterStateType) {}
    }

    class KnockbackState(private val ctx: KinematicCharacterController) : ICharacterState {
        override val type: CharacterStateType = CharacterStateType.KNOCKBACK
        private var knockbackFrames: Int = 0

        fun triggerKnockback(launchVelocity: Vector2, durationFrames: Int) {
            ctx.velocity.copyFrom(launchVelocity)
            knockbackFrames = durationFrames
        }

        override fun onEnter(previousState: CharacterStateType) {}

        override fun onFixedUpdate(dt: Float): CharacterStateType? {
            knockbackFrames--
            // Air friction damping
            ctx.velocity.x *= 0.96f
            ctx.applyVerticalGravity(dt)

            if (knockbackFrames <= 0) {
                return if (ctx.isGrounded) CharacterStateType.IDLE else CharacterStateType.FALL
            }
            return null
        }

        override fun onExit(nextState: CharacterStateType) {}
    }
}
