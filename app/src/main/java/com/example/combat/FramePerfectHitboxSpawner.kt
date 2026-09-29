package com.example.combat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.engine.animation.AffineMatrix2D
import com.example.engine.animation.BoneId
import com.example.engine.collision.AABB
import com.example.engine.math.Vector2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Combat resolution data package returned when a hitbox strikes a target.
 */
data class HitResolution(
    val damage: Float,
    val knockbackVelocityX: Float,
    val knockbackVelocityY: Float,
    val hitStunFrames: Int,
    val hitPointX: Float,
    val hitPointY: Float
)

/**
 * Reusable, object-pooled hitbox representation.
 */
class CombatHitbox {
    var isActive: Boolean = false
    var moveType: BrawlhallaMoveType? = null
    val bounds: AABB = AABB()

    var framesRemaining: Int = 0
    var damage: Float = 0f
    var baseKnockback: Float = 0f
    var knockbackScaling: Float = 0f
    var knockbackAngleDeg: Float = 0f
    var hitStunFrames: Int = 0

    var attachedBone: BoneId = BoneId.WEAPON_R
    var relativeOffsetX: Float = 0f
    var relativeOffsetY: Float = 0f
    var width: Float = 0f
    var height: Float = 0f
    var facingDirection: Float = 1.0f

    fun reset() {
        isActive = false
        moveType = null
        framesRemaining = 0
        damage = 0f
        baseKnockback = 0f
        knockbackScaling = 0f
        knockbackAngleDeg = 0f
        hitStunFrames = 0
        relativeOffsetX = 0f
        relativeOffsetY = 0f
        width = 0f
        height = 0f
        facingDirection = 1.0f
    }

    fun updatePosition(
        rootX: Float,
        rootY: Float,
        facing: Float,
        skeletalMatrices: Map<BoneId, AffineMatrix2D>?
    ) {
        if (!isActive) return
        facingDirection = facing

        val boneMatrix = skeletalMatrices?.get(attachedBone)
        val centerX: Float
        val centerY: Float

        if (boneMatrix != null) {
            // Anchor dynamically to bone world matrix
            val localX = relativeOffsetX * 0.35f
            val localY = relativeOffsetY * 0.35f
            centerX = boneMatrix.transformX(localX, localY)
            centerY = boneMatrix.transformY(localX, localY)
        } else {
            // Fallback to controller position with relative offset
            centerX = rootX + (relativeOffsetX * facing)
            centerY = rootY + relativeOffsetY
        }

        bounds.setFromCenter(
            centerX = centerX,
            centerY = centerY,
            halfWidth = width * 0.5f,
            halfHeight = height * 0.5f
        )
    }

    /**
     * Resolves Brawlhalla-style knockback launch vector against a defender.
     * V_launch = baseKnockback + (targetDamagePercent / 10.0) * knockbackScaling * 1.2
     */
    fun computeLaunchVector(targetDamagePercent: Float): Vector2 {
        val totalMagnitude = baseKnockback + (targetDamagePercent * 0.1f * knockbackScaling * 1.2f)
        val angleRad = (knockbackAngleDeg * (PI / 180.0)).toFloat()

        // Flip horizontal launch direction based on character facing direction
        val dirX = cos(angleRad) * facingDirection
        val dirY = -sin(angleRad) // Screen Y is inverted (negative is upward)

        return Vector2(dirX * totalMagnitude, dirY * totalMagnitude)
    }
}

/**
 * Production-ready FramePerfectHitboxSpawner:
 * Manages zero-allocation pooled hitboxes, precise frame lifecycle windows,
 * dynamic bone attachment, and combat resolution calculations.
 */
class FramePerfectHitboxSpawner(
    private val poolCapacity: Int = 16
) {
    private val hitboxPool: Array<CombatHitbox> = Array(poolCapacity) { CombatHitbox() }

    /**
     * Spawns an active combat hitbox for the specified move definition.
     */
    fun spawnHitbox(
        def: CombatMoveDefinition,
        rootX: Float,
        rootY: Float,
        facingDirection: Float,
        skeletalMatrices: Map<BoneId, AffineMatrix2D>?
    ): CombatHitbox? {
        val hitbox = findFreeHitbox() ?: return null

        hitbox.isActive = true
        hitbox.moveType = def.moveType
        hitbox.framesRemaining = def.activeFrames
        hitbox.damage = def.damage
        hitbox.baseKnockback = def.baseKnockback
        hitbox.knockbackScaling = def.knockbackScaling
        hitbox.knockbackAngleDeg = def.knockbackAngleDeg
        hitbox.hitStunFrames = def.hitStunFrames
        hitbox.attachedBone = def.attachedBone
        hitbox.relativeOffsetX = def.hitboxRelativeOffsetX
        hitbox.relativeOffsetY = def.hitboxRelativeOffsetY
        hitbox.width = def.hitboxWidth
        hitbox.height = def.hitboxHeight
        hitbox.facingDirection = facingDirection

        hitbox.updatePosition(rootX, rootY, facingDirection, skeletalMatrices)
        return hitbox
    }

    /**
     * Decrements active frame counters and updates spatial attachment every 60Hz physics tick.
     */
    fun tickActiveHitboxes(
        rootX: Float,
        rootY: Float,
        facingDirection: Float,
        skeletalMatrices: Map<BoneId, AffineMatrix2D>?
    ) {
        for (i in hitboxPool.indices) {
            val h = hitboxPool[i]
            if (h.isActive) {
                h.framesRemaining--
                if (h.framesRemaining <= 0) {
                    h.isActive = false
                } else {
                    h.updatePosition(rootX, rootY, facingDirection, skeletalMatrices)
                }
            }
        }
    }

    /**
     * Evaluates collision between active hitboxes and a target hurtbox AABB.
     */
    fun checkCollisionAndResolve(
        targetHurtbox: AABB,
        targetDamagePercent: Float
    ): HitResolution? {
        for (i in hitboxPool.indices) {
            val h = hitboxPool[i]
            if (!h.isActive) continue

            if (h.bounds.overlaps(targetHurtbox)) {
                val launchVector = h.computeLaunchVector(targetDamagePercent)
                // Consume hitbox on hit to prevent multi-hitting the same single frame
                h.isActive = false

                return HitResolution(
                    damage = h.damage,
                    knockbackVelocityX = launchVector.x,
                    knockbackVelocityY = launchVector.y,
                    hitStunFrames = h.hitStunFrames,
                    hitPointX = (h.bounds.minX + h.bounds.maxX) * 0.5f,
                    hitPointY = (h.bounds.minY + h.bounds.maxY) * 0.5f
                )
            }
        }
        return null
    }

    fun clearAllHitboxes() {
        for (i in hitboxPool.indices) {
            hitboxPool[i].reset()
        }
    }

    fun getActiveHitboxes(): List<CombatHitbox> {
        val list = mutableListOf<CombatHitbox>()
        for (i in hitboxPool.indices) {
            if (hitboxPool[i].isActive) {
                list.add(hitboxPool[i])
            }
        }
        return list
    }

    private fun findFreeHitbox(): CombatHitbox? {
        for (i in hitboxPool.indices) {
            if (!hitboxPool[i].isActive) {
                return hitboxPool[i]
            }
        }
        return null
    }

    /**
     * Visual Debug & Canvas Rendering for Combat Hitboxes.
     */
    fun render(
        drawScope: DrawScope,
        worldToScreen: (Float, Float) -> Offset,
        zoom: Float
    ) {
        for (i in hitboxPool.indices) {
            val h = hitboxPool[i]
            if (!h.isActive) continue

            val topLeft = worldToScreen(h.bounds.minX, h.bounds.minY)
            val botRight = worldToScreen(h.bounds.maxX, h.bounds.maxY)
            val w = abs(botRight.x - topLeft.x)
            val hSize = abs(botRight.y - topLeft.y)

            drawScope.drawRect(
                color = Color(0x66FF1744), // Translucent Red
                topLeft = topLeft,
                size = Size(w, hSize)
            )
            drawScope.drawRect(
                color = Color(0xFFFF1744), // Solid Crimson Outline
                topLeft = topLeft,
                size = Size(w, hSize),
                style = Stroke(width = 2f * zoom)
            )
        }
    }
}
