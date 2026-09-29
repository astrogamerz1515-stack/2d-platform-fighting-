package com.example.game

import com.example.engine.pool.ObjectPool

enum class ParticleType {
    DUST,
    DASH_GHOST,
    WALL_SPARK,
    JUMP_RING,
    HIT_SPARK
}

/**
 * Ephemeral visual effect element, pooled to maintain 0 B/frame GC pressure.
 */
class VisualParticle {
    var active: Boolean = false
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var life: Float = 1.0f // 1.0 to 0.0
    var decayRate: Float = 0.05f
    var size: Float = 10f
    var colorArgb: Long = 0xFFFFFFFF
    var type: ParticleType = ParticleType.DUST

    fun reset() {
        active = false
        x = 0f
        y = 0f
        vx = 0f
        vy = 0f
        life = 1.0f
        decayRate = 0.05f
        size = 10f
        colorArgb = 0xFFFFFFFF
        type = ParticleType.DUST
    }

    fun update(dt: Float): Boolean {
        if (!active) return false
        x += vx * dt
        y += vy * dt
        life -= decayRate
        if (life <= 0f) {
            active = false
            return false
        }
        return true
    }
}

/**
 * High-performance, zero-allocation particle system.
 */
class ParticleSystem(maxParticles: Int = 120) {
    val particles: Array<VisualParticle> = Array(maxParticles) { VisualParticle() }

    fun spawn(
        type: ParticleType,
        x: Float,
        y: Float,
        vx: Float = 0f,
        vy: Float = 0f,
        size: Float = 12f,
        decayRate: Float = 0.05f,
        colorArgb: Long = 0xFFFFFFFF
    ) {
        for (i in particles.indices) {
            val p = particles[i]
            if (!p.active) {
                p.active = true
                p.type = type
                p.x = x
                p.y = y
                p.vx = vx
                p.vy = vy
                p.size = size
                p.life = 1.0f
                p.decayRate = decayRate
                p.colorArgb = colorArgb
                break
            }
        }
    }

    fun update(dt: Float) {
        for (i in particles.indices) {
            val p = particles[i]
            if (p.active) {
                p.update(dt)
            }
        }
    }
}
