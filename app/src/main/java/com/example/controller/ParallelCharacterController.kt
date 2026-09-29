package com.example.controller

import com.example.engine.collision.AABB
import com.example.engine.collision.ObstacleCollider
import com.example.engine.collision.SweptCollision
import com.example.engine.input.BufferableAction
import com.example.engine.input.InputBufferQueue
import com.example.engine.input.InputButton
import com.example.engine.math.Vector2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Parallel Layer 1: Spatial Locomotion & Physics Track State.
 */
enum class MovementTrackState {
    GROUNDED,
    AIRBORNE,
    WALL_CLING
}

/**
 * Parallel Layer 2: Action & Combat Execution Track State.
 */
enum class ActionTrackState {
    IDLE,
    ATTACKING,
    DODGING,
    HITSTUN
}

/**
 * ParallelCharacterController:
 * Resolves the action-locking bug by decoupling movement from combat action states.
 *
 * Movement states (Grounded, Airborne, WallCling) and Action states (Idle, Attacking,
 * Dodging, HitStun) execute on independent parallel state tracks. Horizontal velocity
 * is NEVER overridden or clamped to zero during an attack, but instead preserves kinetic
 * energy subject to attack-specific slide friction decay curves.
 *
 * Features:
 * 1. Dash-Jumping: Jump within 4 frames of active dash carries dash velocity into jump curve.
 * 2. Gravity-Canceling freeze: locks vertical position while mid-air grounded sig is executing.
 * 3. Wall-Clinging raycast module: ledgeless recovery, capped wall touches resetting jump count.
 * 4. Chase Dodge & Directional Dodge invulnerability window tracking.
 */
class ParallelCharacterController(
    val config: CharacterMovementConfig = CharacterMovementConfig(),
    val inputQueue: InputBufferQueue = InputBufferQueue()
) {
    // Spatial Transform & Collision Bounds
    val position = Vector2(0f, 0f)
    val velocity = Vector2(0f, 0f)
    val bounds = AABB()

    // Facing Direction (+1.0 = Right, -1.0 = Left)
    var facingDirection: Float = 1.0f
        private set

    // Independent Parallel State Tracks
    var movementState: MovementTrackState = MovementTrackState.AIRBORNE
        private set
    var actionState: ActionTrackState = ActionTrackState.IDLE
        private set

    // Environmental Contact Sensors
    var isGrounded: Boolean = false
        private set
    var isTouchingWallLeft: Boolean = false
        private set
    var isTouchingWallRight: Boolean = false
        private set
    var isTouchingCeiling: Boolean = false
        private set
    var wallNormalDirection: Float = 0f
        private set

    // Ledgeless Wall-Cling Mechanics
    var wallTouchCount: Int = 0
        private set
    val maxWallTouches: Int = config.wallSlipMaxTouches
    var wallSlideSpeed: Float = config.wallSlideMaxFallSpeed

    // Aerial Resources
    var remainingAirJumps: Int = config.maxAirJumps
        private set
    var coyoteTimeRemaining: Float = 0f
        private set
    val coyoteTimeDuration: Float = 0.083f // ~5 frames at 60Hz
    var isFastFalling: Boolean = false
        private set

    // Dash & Dash-Jump Window
    var isDashing: Boolean = false
        private set
    var dashTimer: Float = 0f
        private set
    val dashDuration: Float = 0.20f // 12 frames
    var dashCooldownTimer: Float = 0f
        private set
    val dashCooldown: Float = 0.35f
    var dashSpeed: Float = config.dashSpeed

    // Dash-Jump Window (4-frame window = 0.067s from dash start)
    val dashJumpWindowThreshold: Float = 0.067f
    var dashJumpEligible: Boolean = false
        private set

    // Dodge & Invulnerability Window
    var isInvincible: Boolean = false
        private set
    var dodgeTimer: Float = 0f
        private set
    val dodgeDuration: Float = 0.333f // 20 frames
    val invulnerabilityWindowStart: Float = 0.0f // Frame 0
    val invulnerabilityWindowEnd: Float = 0.266f   // Frame 16
    var dodgeCooldownTimer: Float = 0f
        private set
    val standardDodgeCooldown: Float = 1.2f

    // Gravity Cancel State (locks vertical motion and pauses gravity)
    var isGravityCanceled: Boolean = false
        private set
    var gravityCancelFreezeY: Float = 0f

    // Chase Dodge Active Window
    var chaseDodgeWindowRemaining: Float = 0f
        private set

    // Hitstun Timer
    var hitStunTimer: Float = 0f
        private set

    // Active Attack Sliding Friction
    var attackSlideFriction: Float = 1400f
    var attackAllowsAirDrift: Boolean = true

    init {
        updateBounds()
    }

    fun setSpawnPosition(x: Float, y: Float) {
        position.set(x, y)
        velocity.set(0f, 0f)
        movementState = MovementTrackState.AIRBORNE
        actionState = ActionTrackState.IDLE
        isGrounded = false
        remainingAirJumps = config.maxAirJumps
        coyoteTimeRemaining = 0f
        isFastFalling = false
        isDashing = false
        dashTimer = 0f
        dashCooldownTimer = 0f
        dashJumpEligible = false
        isInvincible = false
        dodgeTimer = 0f
        dodgeCooldownTimer = 0f
        isGravityCanceled = false
        chaseDodgeWindowRemaining = 0f
        hitStunTimer = 0f
        wallTouchCount = 0
        updateBounds()
    }

    fun setFacingDirectionManually(facing: Float) {
        if (facing != 0f) {
            facingDirection = sign(facing)
        }
    }

    /**
     * Fixed physics tick decoupled from visual refresh rate.
     */
    fun fixedUpdate(dt: Float, obstacles: List<ObstacleCollider>) {
        // 0. Synchronize physics inputs and age buffered commands
        inputQueue.tickPhysicsFrame()

        // 1. Advance time-based internal timers
        advanceTimers(dt)

        // 2. Poll input queue for movement commands (Dash, Jump, Fast-fall, Directional Stick)
        processLocomotionInputs(dt)

        // 3. Process Action State Logic (Attacking, Dodging, Hitstun)
        processActionTrack(dt)

        // 4. Process Movement State Physics (Grounded acceleration/friction, Airborne gravity, Wall cling)
        processMovementTrack(dt)

        // 5. Swept-AABB continuous collision detection against stage
        integrateSweptCollision(dt, obstacles)

        // 6. Update environmental contact flags and synchronize parallel state tracks
        synchronizeStates(obstacles)
    }

    private fun advanceTimers(dt: Float) {
        if (coyoteTimeRemaining > 0f) coyoteTimeRemaining -= dt
        if (dashCooldownTimer > 0f) dashCooldownTimer -= dt
        if (dodgeCooldownTimer > 0f) dodgeCooldownTimer -= dt
        if (chaseDodgeWindowRemaining > 0f) chaseDodgeWindowRemaining -= dt

        // Dash Progress
        if (isDashing) {
            dashTimer += dt
            dashJumpEligible = (dashTimer <= dashJumpWindowThreshold)
            if (dashTimer >= dashDuration) {
                isDashing = false
                dashJumpEligible = false
            }
        }

        // Dodge & Invulnerability Window
        if (actionState == ActionTrackState.DODGING) {
            dodgeTimer += dt
            isInvincible = (dodgeTimer >= invulnerabilityWindowStart && dodgeTimer <= invulnerabilityWindowEnd)
            if (dodgeTimer >= dodgeDuration) {
                actionState = ActionTrackState.IDLE
                isInvincible = false
            }
        }

        // Hitstun Progress
        if (actionState == ActionTrackState.HITSTUN) {
            hitStunTimer -= dt
            if (hitStunTimer <= 0f) {
                actionState = ActionTrackState.IDLE
            }
        }
    }

    private fun processLocomotionInputs(dt: Float) {
        if (actionState == ActionTrackState.HITSTUN) return

        val stickX = inputQueue.getStickX()
        val stickY = inputQueue.getStickY()

        // Turn facing direction if moving horizontally and not locked in active attack
        if (abs(stickX) > 0.25f && actionState != ActionTrackState.ATTACKING) {
            facingDirection = sign(stickX)
        }

        // --- DASH & CHASE DODGE ---
        val dashRequested = inputQueue.consumeAction(BufferableAction.DASH)
        if (dashRequested) {
            val hasChaseDodge = (chaseDodgeWindowRemaining > 0f)
            val canDodge = (dodgeCooldownTimer <= 0f || hasChaseDodge)

            if (hasChaseDodge) {
                // Execute Hyper-Speed Chase Dodge
                chaseDodgeWindowRemaining = 0f
                actionState = ActionTrackState.DODGING
                dodgeTimer = 0f
                isInvincible = true
                val dirX = if (abs(stickX) > 0.2f) sign(stickX) else facingDirection
                velocity.x = dirX * (dashSpeed * 1.35f)
                velocity.y = 0f
            } else if (movementState == MovementTrackState.GROUNDED && dashCooldownTimer <= 0f && actionState != ActionTrackState.ATTACKING) {
                // Grounded Dash
                isDashing = true
                dashTimer = 0f
                dashJumpEligible = true
                dashCooldownTimer = dashCooldown
                val dir = if (abs(stickX) > 0.2f) sign(stickX) else facingDirection
                velocity.x = dir * dashSpeed
            } else if (canDodge && actionState != ActionTrackState.ATTACKING) {
                // Directional Air / Ground Dodge
                actionState = ActionTrackState.DODGING
                dodgeTimer = 0f
                isInvincible = true
                dodgeCooldownTimer = standardDodgeCooldown
                if (abs(stickX) > 0.2f || abs(stickY) > 0.2f) {
                    val mag = Vector2(stickX, stickY).length()
                    velocity.x = (stickX / mag) * 450f
                    velocity.y = (stickY / mag) * 450f
                } else {
                    velocity.x *= 0.2f
                    velocity.y *= 0.2f
                }
            }
        }

        // --- JUMP & DASH-JUMP ---
        val jumpRequested = inputQueue.consumeAction(BufferableAction.JUMP)
        if (jumpRequested && actionState != ActionTrackState.ATTACKING && actionState != ActionTrackState.DODGING) {
            if (movementState == MovementTrackState.GROUNDED || coyoteTimeRemaining > 0f) {
                executeJump(isDashJump = (isDashing && dashJumpEligible))
            } else if (movementState == MovementTrackState.WALL_CLING) {
                velocity.x = wallNormalDirection * config.wallJumpVelocityX
                velocity.y = config.wallJumpVelocityY
                facingDirection = wallNormalDirection
                movementState = MovementTrackState.AIRBORNE
            } else if (remainingAirJumps > 0) {
                remainingAirJumps--
                velocity.y = config.airJumpImpulse
                movementState = MovementTrackState.AIRBORNE
            }
        }

        // --- FAST-FALL ---
        if (movementState == MovementTrackState.AIRBORNE && velocity.y > 0f && stickY > 0.65f) {
            isFastFalling = true
        }
    }

    private fun executeJump(isDashJump: Boolean) {
        coyoteTimeRemaining = 0f
        isGrounded = false
        movementState = MovementTrackState.AIRBORNE

        if (isDashJump) {
            // DASH-JUMP: Transfers horizontal dash momentum directly into jump curve!
            velocity.y = config.jumpImpulse * 0.95f
            velocity.x = facingDirection * (dashSpeed * 0.92f)
            isDashing = false
            dashJumpEligible = false
        } else {
            velocity.y = config.jumpImpulse
        }
    }

    private fun processActionTrack(dt: Float) {
        when (actionState) {
            ActionTrackState.ATTACKING -> {
                // NON-BLOCKING VELOCITY PRESERVATION:
                // Velocity is NOT clamped to zero. Horizontal kinetic energy is preserved
                // and decays gracefully according to attack-specific sliding friction.
                if (movementState == MovementTrackState.GROUNDED) {
                    val frictionDecay = attackSlideFriction * dt
                    if (abs(velocity.x) > frictionDecay) {
                        velocity.x -= sign(velocity.x) * frictionDecay
                    } else {
                        velocity.x = 0f
                    }
                } else if (attackAllowsAirDrift) {
                    val stickX = inputQueue.getStickX()
                    if (abs(stickX) > 0.2f) {
                        velocity.x += stickX * 900f * dt
                        velocity.x = velocity.x.coerceIn(-config.maxAirSpeed, config.maxAirSpeed)
                    }
                }

                // Gravity Cancel vertical lock
                if (isGravityCanceled) {
                    velocity.y = 0f
                    position.y = gravityCancelFreezeY
                }
            }

            ActionTrackState.DODGING -> {
                velocity.x *= 0.94f
                velocity.y *= 0.94f
            }

            ActionTrackState.HITSTUN -> {
                isDashing = false
                isGravityCanceled = false
            }

            ActionTrackState.IDLE -> Unit
        }
    }

    private fun processMovementTrack(dt: Float) {
        val stickX = inputQueue.getStickX()

        when (movementState) {
            MovementTrackState.GROUNDED -> {
                isFastFalling = false
                isGravityCanceled = false
                wallTouchCount = 0
                remainingAirJumps = config.maxAirJumps

                if (actionState != ActionTrackState.ATTACKING && actionState != ActionTrackState.DODGING) {
                    if (isDashing) {
                        velocity.x = facingDirection * dashSpeed
                    } else if (abs(stickX) > 0.15f) {
                        val targetSpeed = stickX * config.maxGroundSpeed
                        val accel = config.groundAcceleration * dt
                        velocity.x = approach(velocity.x, targetSpeed, accel)
                    } else {
                        val friction = config.groundFriction * dt
                        velocity.x = approach(velocity.x, 0f, friction)
                    }
                }
                velocity.y = 0f
            }

            MovementTrackState.AIRBORNE -> {
                if (!isGravityCanceled) {
                    val grav = if (isFastFalling) config.fastFallGravity else config.gravity
                    val maxFall = if (isFastFalling) config.fastFallMaxSpeed else config.maxFallSpeed
                    velocity.y = min(velocity.y + grav * dt, maxFall)
                }

                if (actionState != ActionTrackState.ATTACKING && actionState != ActionTrackState.DODGING) {
                    if (abs(stickX) > 0.15f) {
                        val targetAirSpeed = stickX * config.maxAirSpeed
                        val airAccel = config.airAcceleration * dt
                        velocity.x = approach(velocity.x, targetAirSpeed, airAccel)
                    } else {
                        val airFriction = config.airFriction * dt
                        velocity.x = approach(velocity.x, 0f, airFriction)
                    }
                }
            }

            MovementTrackState.WALL_CLING -> {
                isFastFalling = false
                isGravityCanceled = false

                // LEDGELESS RECOVERY:
                // Wall cling clamps downward sliding velocity to slow crawl
                velocity.x = 0f
                velocity.y = min(velocity.y + 400f * dt, wallSlideSpeed)

                if (inputQueue.getStickY() > 0.7f) {
                    movementState = MovementTrackState.AIRBORNE
                }
            }
        }
    }

    private fun integrateSweptCollision(dt: Float, obstacles: List<ObstacleCollider>) {
        val dropThrough = inputQueue.isButtonHeld(InputButton.DOWN) && inputQueue.consumeAction(BufferableAction.DROP_THROUGH_PLATFORM)
        var hitGround = false
        var hitCeil = false
        var hitWall = false
        var wallNx = 0f

        SweptCollision.moveAndSlide(
            box = bounds,
            position = position,
            velocity = velocity,
            dt = dt,
            obstacles = obstacles,
            oneWayDropThrough = dropThrough,
            onGrounded = { hitGround = true },
            onWallContact = { normalX, _ ->
                hitWall = true
                wallNx = normalX
            },
            onCeilingContact = { hitCeil = true }
        )

        isGrounded = hitGround
        isTouchingCeiling = hitCeil
        if (hitWall) {
            wallNormalDirection = wallNx
            if (wallNormalDirection > 0f) {
                isTouchingWallLeft = true
                isTouchingWallRight = false
            } else {
                isTouchingWallLeft = false
                isTouchingWallRight = true
            }
        } else {
            isTouchingWallLeft = false
            isTouchingWallRight = false
        }

        updateBounds()
    }

    private fun synchronizeStates(obstacles: List<ObstacleCollider>) {
        val dropThrough = inputQueue.isButtonHeld(InputButton.DOWN)
        val groundCollider = SweptCollision.checkGroundProbe(bounds, obstacles, dropThrough)
        val wasGrounded = isGrounded
        if (groundCollider != null) {
            isGrounded = true
        }

        if (isGrounded) {
            movementState = MovementTrackState.GROUNDED
            coyoteTimeRemaining = coyoteTimeDuration
            wallTouchCount = 0
        } else {
            val againstWall = (isTouchingWallLeft || isTouchingWallRight)
            if (againstWall && velocity.y > 0f && wallTouchCount < maxWallTouches) {
                if (movementState != MovementTrackState.WALL_CLING) {
                    wallTouchCount++
                    remainingAirJumps = max(remainingAirJumps, 1)
                }
                movementState = MovementTrackState.WALL_CLING
                wallNormalDirection = if (isTouchingWallLeft) 1.0f else -1.0f
            } else {
                movementState = MovementTrackState.AIRBORNE
                if (wasGrounded && coyoteTimeRemaining <= 0f) {
                    coyoteTimeRemaining = coyoteTimeDuration
                }
            }
        }
    }

    /**
     * Engages an attack state with non-blocking momentum handling.
     */
    fun startAttack(slideFriction: Float, initialImpulseX: Float, initialImpulseY: Float, momentumRatio: Float) {
        actionState = ActionTrackState.ATTACKING
        attackSlideFriction = slideFriction

        // Preserve entry horizontal momentum scaled by move ratio
        velocity.x = (velocity.x * momentumRatio) + (initialImpulseX * facingDirection)
        if (abs(initialImpulseY) > 0.01f) {
            velocity.y = initialImpulseY
        }
    }

    /**
     * Terminates an attack state and restores idle action track.
     */
    fun finishAttack() {
        if (actionState == ActionTrackState.ATTACKING) {
            actionState = ActionTrackState.IDLE
        }
        isGravityCanceled = false
    }

    /**
     * Executes Gravity Cancel: mid-air freeze for grounded signatures.
     */
    fun activateGravityCancel() {
        isGravityCanceled = true
        gravityCancelFreezeY = position.y
        velocity.x *= 0.3f
        velocity.y = 0f
    }

    /**
     * Grants Chase Dodge window upon successful attack hit.
     */
    fun grantChaseDodgeWindow() {
        chaseDodgeWindowRemaining = 0.166f // 10 frames at 60Hz
    }

    /**
     * Applies knockback and hitstun directly into physics track.
     */
    fun applyHit(knockbackVelX: Float, knockbackVelY: Float, hitStunDuration: Float) {
        finishAttack()
        actionState = ActionTrackState.HITSTUN
        hitStunTimer = hitStunDuration
        velocity.set(knockbackVelX, knockbackVelY)
        movementState = MovementTrackState.AIRBORNE
        isGrounded = false
        isDashing = false
        isInvincible = false
    }

    private fun updateBounds() {
        bounds.setFromCenter(
            centerX = position.x,
            centerY = position.y,
            halfWidth = config.colliderWidth * 0.5f,
            halfHeight = config.colliderHeight * 0.5f
        )
    }

    private fun approach(current: Float, target: Float, maxDelta: Float): Float {
        return if (current < target) {
            min(current + maxDelta, target)
        } else {
            max(current - maxDelta, target)
        }
    }
}
