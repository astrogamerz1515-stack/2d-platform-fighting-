package com.example.engine.collision

import com.example.engine.math.Vector2
import kotlin.math.max
import kotlin.math.min

/**
 * Axis-Aligned Bounding Box (AABB) in 2D space.
 * Follows the standard screen coordinates: X rightward (+), Y downward (+).
 */
data class AABB(
    var minX: Float = 0f,
    var minY: Float = 0f,
    var maxX: Float = 0f,
    var maxY: Float = 0f
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerY: Float get() = (minY + maxY) * 0.5f

    fun set(minX: Float, minY: Float, maxX: Float, maxY: Float): AABB {
        this.minX = minX
        this.minY = minY
        this.maxX = maxX
        this.maxY = maxY
        return this
    }

    fun setFromCenter(centerX: Float, centerY: Float, halfWidth: Float, halfHeight: Float): AABB {
        this.minX = centerX - halfWidth
        this.maxX = centerX + halfWidth
        this.minY = centerY - halfHeight
        this.maxY = centerY + halfHeight
        return this
    }

    fun overlaps(other: AABB): Boolean {
        if (maxX <= other.minX || minX >= other.maxX) return false
        if (maxY <= other.minY || minY >= other.maxY) return false
        return true
    }

    fun contains(px: Float, py: Float): Boolean {
        return px in minX..maxX && py in minY..maxY
    }

    fun copyFrom(other: AABB): AABB {
        this.minX = other.minX
        this.minY = other.minY
        this.maxX = other.maxX
        this.maxY = other.maxY
        return this
    }
}

/**
 * Represents the result of a swept collision test.
 * Pooled to ensure zero garbage collection impact.
 */
data class HitResult(
    var hit: Boolean = false,
    var timeOfImpact: Float = 1.0f, // Normalized time [0.0, 1.0]
    var normalX: Float = 0f,
    var normalY: Float = 0f,
    var collider: ObstacleCollider? = null
) {
    fun reset() {
        hit = false
        timeOfImpact = 1.0f
        normalX = 0f
        normalY = 0f
        collider = null
    }

    fun set(hit: Boolean, toi: Float, nx: Float, ny: Float, col: ObstacleCollider?) {
        this.hit = hit
        this.timeOfImpact = toi
        this.normalX = nx
        this.normalY = ny
        this.collider = col
    }
}

enum class ColliderType {
    SOLID,          // Solid walls, floors, ceilings
    ONE_WAY_PLATFORM, // Can jump through from bottom, land on top, drop through
    HAZARD          // Blast zone or hazard spikes
}

/**
 * Stage geometry obstacle box with tagging for one-way platforms and walls.
 */
data class ObstacleCollider(
    val bounds: AABB,
    val type: ColliderType = ColliderType.SOLID,
    val id: String = ""
)
