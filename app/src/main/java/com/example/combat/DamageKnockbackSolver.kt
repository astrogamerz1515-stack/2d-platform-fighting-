package com.example.combat

import com.example.engine.collision.AABB
import com.example.engine.math.Vector2
import com.example.game.BrawlStage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Combat damage and knockback packet returned when an attack connects.
 */
data class KnockbackPayload(
    val damageDealt: Float,
    val resultingDamagePercent: Float,
    val knockbackVelocity: Vector2,
    val hitStunDurationSeconds: Float,
    val hitStunFrames: Int,
    val isFatalTrajectory: Boolean
)

/**
 * DamageKnockbackSolver:
 * Evaluates dynamic physics damage scaling, exact Brawlhalla knockback vector metrics,
 * hitstun scaling, and blast zone boundary kill checks.
 *
 * Formula:
 * Knockback Vector = Base Knockback + (Player Damage % * Attack Knockback Scaling Factor) * Attacker Force Vector
 */
class DamageKnockbackSolver(
    val stage: BrawlStage = BrawlStage()
) {

    /**
     * Resolves knockback velocity and hitstun from an attack hitbox against a defender.
     *
     * @param currentDamagePercent Current damage accrued by defender (0% to 700%).
     * @param attackDamage Damage value of the attacking move.
     * @param baseKnockback Base impulse magnitude independent of damage.
     * @param knockbackScaling Multiplier scaling with defender's damage %.
     * @param knockbackAngleDeg Angle of force in degrees (0 = forward, 90 = straight up, 270 = spike down).
     * @param attackerFacing Facing direction of attacker (+1.0 = right, -1.0 = left).
     * @param baseHitStunFrames Base frames of hitstun.
     * @return Fully resolved KnockbackPayload.
     */
    fun solveKnockback(
        currentDamagePercent: Float,
        attackDamage: Float,
        baseKnockback: Float,
        knockbackScaling: Float,
        knockbackAngleDeg: Float,
        attackerFacing: Float,
        baseHitStunFrames: Int = 14
    ): KnockbackPayload {
        // 1. Accrue new damage percentage
        val newDamagePercent = currentDamagePercent + attackDamage

        // 2. Compute Attacker Force Unit Vector
        val angleRad = (knockbackAngleDeg * (PI / 180.0)).toFloat()
        val forceDirX = cos(angleRad) * attackerFacing
        val forceDirY = -sin(angleRad) // Screen coordinates: negative Y is upward
        val attackerForceVector = Vector2(forceDirX, forceDirY)

        // 3. Exact Scaling Formula:
        // Knockback Magnitude = Base Knockback + (Player Damage % * Attack Knockback Scaling Factor)
        val knockbackMagnitude = baseKnockback + (newDamagePercent * knockbackScaling)

        val knockbackVelocity = Vector2(
            attackerForceVector.x * knockbackMagnitude,
            attackerForceVector.y * knockbackMagnitude
        )

        // 4. Hitstun Scaling: higher percent = longer hitstun
        val scaledHitStunFrames = (baseHitStunFrames + (newDamagePercent * 0.12f)).toInt().coerceIn(baseHitStunFrames, 60)
        val hitStunDurationSeconds = scaledHitStunFrames / 60.0f

        // 5. Fatal Trajectory Assessment (velocity magnitude capable of reaching blast zone)
        val isFatal = knockbackMagnitude > 850f || (newDamagePercent > 130f && knockbackMagnitude > 600f)

        return KnockbackPayload(
            damageDealt = attackDamage,
            resultingDamagePercent = newDamagePercent,
            knockbackVelocity = knockbackVelocity,
            hitStunDurationSeconds = hitStunDurationSeconds,
            hitStunFrames = scaledHitStunFrames,
            isFatalTrajectory = isFatal
        )
    }

    /**
     * Checks if an entity's center or bounding box has penetrated the stage blast zones.
     * Triggers the instant lifecycle kill callback when out of bounds.
     */
    fun checkBlastZoneCollision(
        bounds: AABB,
        onInstantKill: () -> Unit
    ): Boolean {
        val cx = bounds.centerX
        val cy = bounds.centerY

        val isOutOfBounds = cx < stage.blastZoneLeft ||
                cx > stage.blastZoneRight ||
                cy < stage.blastZoneTop ||
                cy > stage.blastZoneBottom

        if (isOutOfBounds) {
            onInstantKill()
            return true
        }
        return false
    }

    /**
     * Evaluates position coordinates directly against blast zones.
     */
    fun checkPositionOutOfBounds(x: Float, y: Float): Boolean {
        return x < stage.blastZoneLeft ||
                x > stage.blastZoneRight ||
                y < stage.blastZoneTop ||
                y > stage.blastZoneBottom
    }
}
