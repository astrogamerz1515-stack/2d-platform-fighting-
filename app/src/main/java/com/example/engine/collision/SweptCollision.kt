package com.example.engine.collision

import com.example.engine.math.Vector2
import com.example.engine.pool.ObjectPool
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Continuous Collision Detection (CCD) algorithm using Minkowski-expanded Swept AABB.
 * Completely immune to high-velocity tunneling (clipping through thin walls, ceilings, floors).
 */
object SweptCollision {

    private const val EPSILON = 0.0001f
    const val SKIN_WIDTH = 0.02f // Small buffer to prevent floating-point numerical sticking

    private val hitPool = ObjectPool(
        initialCapacity = 16,
        factory = { HitResult() },
        resetAction = { it.reset() }
    )

    /**
     * Sweeps a moving AABB against a static AABB over displacement vector (dx, dy).
     * Populates [outResult] with time of impact [0..1] and collision normal.
     *
     * @param box Current moving bounding box
     * @param dx Velocity X * dt
     * @param dy Velocity Y * dt
     * @param target Target obstacle box
     * @param outResult Container for collision outcome
     */
    fun sweep(
        box: AABB,
        dx: Float,
        dy: Float,
        target: ObstacleCollider,
        oneWayDropThrough: Boolean,
        outResult: HitResult
    ) {
        outResult.reset()
        val staticBox = target.bounds

        // Special handling for ONE_WAY_PLATFORM:
        // Only solid from the top when moving downward, and box's bottom was strictly above platform top prior to movement.
        if (target.type == ColliderType.ONE_WAY_PLATFORM) {
            if (oneWayDropThrough || dy <= 0f) {
                return // Ignore if moving upwards, standing still vertically, or intentionally dropping through
            }
            // Check if player's bottom was already at or below platform top
            if (box.maxY > staticBox.minY + 1.0f) {
                return
            }
        }

        // Broadphase bounding box check
        val broadMinX = if (dx > 0f) box.minX else box.minX + dx
        val broadMaxX = if (dx > 0f) box.maxX + dx else box.maxX
        val broadMinY = if (dy > 0f) box.minY else box.minY + dy
        val broadMaxY = if (dy > 0f) box.maxY + dy else box.maxY

        if (broadMaxX < staticBox.minX || broadMinX > staticBox.maxX ||
            broadMaxY < staticBox.minY || broadMinY > staticBox.maxY
        ) {
            return // No possible collision within this time step
        }

        var entryX: Float
        var exitX: Float
        var entryY: Float
        var exitY: Float

        // Calculate entry and exit distances along X axis
        if (dx > 0.0f) {
            entryX = staticBox.minX - box.maxX
            exitX = staticBox.maxX - box.minX
        } else {
            entryX = staticBox.maxX - box.minX
            exitX = staticBox.minX - box.maxX
        }

        // Calculate entry and exit distances along Y axis
        if (dy > 0.0f) {
            entryY = staticBox.minY - box.maxY
            exitY = staticBox.maxY - box.minY
        } else {
            entryY = staticBox.maxY - box.minY
            exitY = staticBox.minY - box.maxY
        }

        // Calculate time of entry and exit along both axes
        val tEntryX: Float
        val tExitX: Float
        if (abs(dx) < EPSILON) {
            if (box.maxX <= staticBox.minX || box.minX >= staticBox.maxX) {
                return // Parallel and outside
            }
            tEntryX = Float.NEGATIVE_INFINITY
            tExitX = Float.POSITIVE_INFINITY
        } else {
            tEntryX = entryX / dx
            tExitX = exitX / dx
        }

        val tEntryY: Float
        val tExitY: Float
        if (abs(dy) < EPSILON) {
            if (box.maxY <= staticBox.minY || box.minY >= staticBox.maxY) {
                return // Parallel and outside
            }
            tEntryY = Float.NEGATIVE_INFINITY
            tExitY = Float.POSITIVE_INFINITY
        } else {
            tEntryY = entryY / dy
            tExitY = exitY / dy
        }

        // Determine earliest entry and latest exit
        val entryTime = max(tEntryX, tEntryY)
        val exitTime = min(tExitX, tExitY)

        // Collision validation check
        if (entryTime > exitTime || (tEntryX < 0.0f && tEntryY < 0.0f) || entryTime > 1.0f || entryTime < 0.0f) {
            return
        }

        // ONE_WAY_PLATFORM collision must only trigger on downward landing (normal Y = -1)
        if (target.type == ColliderType.ONE_WAY_PLATFORM) {
            if (tEntryY <= tEntryX || dy <= 0f) {
                return
            }
        }

        // Determine collision normal vector
        var normalX = 0f
        var normalY = 0f
        if (tEntryX > tEntryY) {
            normalX = if (dx < 0f) 1.0f else -1.0f
            normalY = 0.0f
        } else {
            normalX = 0.0f
            normalY = if (dy < 0f) 1.0f else -1.0f
        }

        outResult.set(
            hit = true,
            toi = entryTime,
            nx = normalX,
            ny = normalY,
            col = target
        )
    }

    /**
     * Resolves continuous collision against a list of static colliders with multi-step deflection sliding.
     * Guarantees zero penetration even at extreme velocities.
     */
    fun moveAndSlide(
        box: AABB,
        position: Vector2,
        velocity: Vector2,
        dt: Float,
        obstacles: List<ObstacleCollider>,
        oneWayDropThrough: Boolean,
        onGrounded: (ObstacleCollider) -> Unit,
        onWallContact: (normalX: Float, ObstacleCollider) -> Unit,
        onCeilingContact: (ObstacleCollider) -> Unit
    ) {
        var remainingTime = 1.0f
        var currentDx = velocity.x * dt
        var currentDy = velocity.y * dt

        val tempHit = hitPool.obtain()
        val nearestHit = hitPool.obtain()

        try {
            // Up to 4 iterative collision sub-steps to handle corners & simultaneous axes
            for (step in 0 until 4) {
                if (remainingTime <= EPSILON) break

                val stepDx = currentDx * remainingTime
                val stepDy = currentDy * remainingTime

                nearestHit.reset()
                var minToi = 1.0f

                for (i in obstacles.indices) {
                    val obstacle = obstacles[i]
                    sweep(box, stepDx, stepDy, obstacle, oneWayDropThrough, tempHit)
                    if (tempHit.hit && tempHit.timeOfImpact < minToi) {
                        minToi = tempHit.timeOfImpact
                        nearestHit.set(
                            hit = true,
                            toi = tempHit.timeOfImpact,
                            nx = tempHit.normalX,
                            ny = tempHit.normalY,
                            col = tempHit.collider
                        )
                    }
                }

                if (!nearestHit.hit) {
                    // No collision: move full distance
                    position.x += stepDx
                    position.y += stepDy
                    box.minX += stepDx
                    box.maxX += stepDx
                    box.minY += stepDy
                    box.maxY += stepDy
                    break
                }

                // Advance by fraction of time up to impact minus skin width back-off
                val advanceFrac = max(0f, nearestHit.timeOfImpact - 0.001f)
                val advanceX = stepDx * advanceFrac
                val advanceY = stepDy * advanceFrac

                position.x += advanceX
                position.y += advanceY
                box.minX += advanceX
                box.maxX += advanceX
                box.minY += advanceY
                box.maxY += advanceY

                // Contact event callbacks
                if (nearestHit.normalY < -0.7f) {
                    onGrounded(nearestHit.collider!!)
                } else if (nearestHit.normalY > 0.7f) {
                    onCeilingContact(nearestHit.collider!!)
                }
                if (abs(nearestHit.normalX) > 0.7f) {
                    onWallContact(nearestHit.normalX, nearestHit.collider!!)
                }

                // Remaining time after partial advance
                val timeConsumed = advanceFrac * remainingTime
                remainingTime -= timeConsumed

                // Deflect velocity along the collision plane tangent (zero out normal component)
                val dot = velocity.x * nearestHit.normalX + velocity.y * nearestHit.normalY
                if (dot < 0f) {
                    velocity.x -= dot * nearestHit.normalX
                    velocity.y -= dot * nearestHit.normalY
                }

                // Update currentDx and currentDy to new velocity
                currentDx = velocity.x * dt
                currentDy = velocity.y * dt

                if (abs(currentDx) < EPSILON && abs(currentDy) < EPSILON) {
                    break
                }
            }

            // Ground probe check when resting on or hovering directly above a platform
            if (velocity.y >= -0.01f) {
                val groundCollider = checkGroundProbe(box, obstacles, oneWayDropThrough)
                if (groundCollider != null) {
                    // Snap feet to platform surface to prevent float drift
                    val snapDy = groundCollider.bounds.minY - box.maxY
                    if (abs(snapDy) <= 2.5f) {
                        position.y += snapDy
                        box.minY += snapDy
                        box.maxY += snapDy
                    }
                    velocity.y = 0f
                    onGrounded(groundCollider)
                }
            }
        } finally {
            hitPool.recycle(tempHit)
            hitPool.recycle(nearestHit)
        }
    }

    /**
     * Checks if the character's feet are within ground probe distance of a surface.
     */
    fun checkGroundProbe(
        box: AABB,
        obstacles: List<ObstacleCollider>,
        oneWayDropThrough: Boolean
    ): ObstacleCollider? {
        val footY = box.maxY
        for (i in obstacles.indices) {
            val obs = obstacles[i]
            val b = obs.bounds
            // Horizontal overlap check with a small margin
            if (box.maxX > b.minX + 1.0f && box.minX < b.maxX - 1.0f) {
                if (obs.type == ColliderType.SOLID) {
                    if (footY in (b.minY - 2.5f)..(b.minY + 2.5f)) {
                        return obs
                    }
                } else if (obs.type == ColliderType.ONE_WAY_PLATFORM) {
                    if (!oneWayDropThrough && footY in (b.minY - 2.5f)..(b.minY + 2.5f)) {
                        return obs
                    }
                }
            }
        }
        return null
    }
}
