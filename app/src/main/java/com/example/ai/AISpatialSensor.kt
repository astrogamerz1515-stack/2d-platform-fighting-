package com.example.ai

import com.example.combat.CombatHitbox
import com.example.engine.collision.AABB
import com.example.engine.collision.ColliderType
import com.example.engine.collision.ObstacleCollider
import com.example.engine.math.Vector2
import com.example.game.BrawlStage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Localized Stage Zones for spatial positioning and behavior tree transitions.
 */
enum class StageZone {
    CENTER_STAGE,
    LEFT_LEDGE,
    RIGHT_LEDGE,
    OFFSTAGE_LEFT,
    OFFSTAGE_RIGHT,
    WALL_CLING_LEFT,
    WALL_CLING_RIGHT,
    CRITICAL_BLAST_ZONE
}

/**
 * Perception output gathered by the AI spatial sensor each frame.
 */
data class AISensorSnapshot(
    var botZone: StageZone = StageZone.CENTER_STAGE,
    var targetZone: StageZone = StageZone.CENTER_STAGE,
    var distanceToTargetX: Float = 0f,
    var distanceToTargetY: Float = 0f,
    var directDistanceToTarget: Float = 0f,
    var hasLineOfSight: Boolean = true,
    var isOffStage: Boolean = false,
    var distanceToNearestLedgeX: Float = 0f,
    var distanceToNearestLedgeY: Float = 0f,
    var incomingThreatDetected: Boolean = false,
    var threatDistance: Float = Float.MAX_VALUE,
    var nearestPlatformFloorY: Float = 0f
)

/**
 * Low-overhead, zero-allocation 2D spatial sensor:
 * Simulates human vision through line-of-sight raycasting, ledge detection,
 * and incoming hitbox proximity queries without cheating.
 */
class AISpatialSensor {

    val currentSnapshot = AISensorSnapshot()

    /**
     * Executes the comprehensive environmental perception query.
     */
    fun evaluateEnvironment(
        botBounds: AABB,
        botVelocity: Vector2,
        targetBounds: AABB,
        targetVelocity: Vector2,
        stage: BrawlStage,
        activeThreatHitboxes: List<CombatHitbox>
    ): AISensorSnapshot {
        val botCenterX = botBounds.centerX
        val botCenterY = botBounds.centerY
        val targetCenterX = targetBounds.centerX
        val targetCenterY = targetBounds.centerY

        // 1. Calculate relative distances
        val dx = targetCenterX - botCenterX
        val dy = targetCenterY - botCenterY
        val dist = sqrt(dx * dx + dy * dy)

        currentSnapshot.distanceToTargetX = dx
        currentSnapshot.distanceToTargetY = dy
        currentSnapshot.directDistanceToTarget = dist

        // 2. Classify Bot Stage Zone
        currentSnapshot.botZone = classifyStageZone(botBounds, stage)
        currentSnapshot.isOffStage = (currentSnapshot.botZone == StageZone.OFFSTAGE_LEFT ||
                currentSnapshot.botZone == StageZone.OFFSTAGE_RIGHT ||
                currentSnapshot.botZone == StageZone.WALL_CLING_LEFT ||
                currentSnapshot.botZone == StageZone.WALL_CLING_RIGHT ||
                currentSnapshot.botZone == StageZone.CRITICAL_BLAST_ZONE)

        // 3. Classify Target Stage Zone
        currentSnapshot.targetZone = classifyStageZone(targetBounds, stage)

        // 4. Calculate Distance to Nearest Main Stage Ledge
        val mainMinX = stage.mainPlatformMinX
        val mainMaxX = stage.mainPlatformMaxX
        val mainTopY = stage.mainPlatformMinY

        if (botCenterX < mainMinX) {
            currentSnapshot.distanceToNearestLedgeX = mainMinX - botCenterX
            currentSnapshot.distanceToNearestLedgeY = mainTopY - botCenterY
        } else if (botCenterX > mainMaxX) {
            currentSnapshot.distanceToNearestLedgeX = mainMaxX - botCenterX
            currentSnapshot.distanceToNearestLedgeY = mainTopY - botCenterY
        } else {
            val distToLeft = abs(botCenterX - mainMinX)
            val distToRight = abs(botCenterX - mainMaxX)
            currentSnapshot.distanceToNearestLedgeX = if (distToLeft < distToRight) -distToLeft else distToRight
            currentSnapshot.distanceToNearestLedgeY = mainTopY - botCenterY
        }
        currentSnapshot.nearestPlatformFloorY = mainTopY

        // 5. Line-of-sight Raycast between bot and target
        currentSnapshot.hasLineOfSight = checkLineOfSight(botCenterX, botCenterY, targetCenterX, targetCenterY, stage.colliders)

        // 6. Threat Detection (Incoming Active Hitboxes)
        detectIncomingThreats(botBounds, activeThreatHitboxes)

        return currentSnapshot
    }

    /**
     * Maps physical coordinates to discrete strategic stage zones.
     */
    fun classifyStageZone(bounds: AABB, stage: BrawlStage): StageZone {
        val cx = bounds.centerX
        val cy = bounds.centerY

        // Blast zone emergency check
        if (cy > stage.mainPlatformMaxY + 140f || cx < stage.blastZoneLeft + 100f || cx > stage.blastZoneRight - 100f) {
            return StageZone.CRITICAL_BLAST_ZONE
        }

        val mainMinX = stage.mainPlatformMinX
        val mainMaxX = stage.mainPlatformMaxX
        val mainTopY = stage.mainPlatformMinY
        val mainBotY = stage.mainPlatformMaxY

        // Off-stage horizontally
        if (cx < mainMinX) {
            // Check if within wall-cling proximity
            if (cx >= mainMinX - 30f && cy >= mainTopY && cy <= mainBotY) {
                return StageZone.WALL_CLING_LEFT
            }
            return StageZone.OFFSTAGE_LEFT
        }

        if (cx > mainMaxX) {
            // Check if within wall-cling proximity
            if (cx <= mainMaxX + 30f && cy >= mainTopY && cy <= mainBotY) {
                return StageZone.WALL_CLING_RIGHT
            }
            return StageZone.OFFSTAGE_RIGHT
        }

        // On-stage positioning
        val ledgeThreshold = 65f
        if (cx <= mainMinX + ledgeThreshold) {
            return StageZone.LEFT_LEDGE
        }
        if (cx >= mainMaxX - ledgeThreshold) {
            return StageZone.RIGHT_LEDGE
        }

        return StageZone.CENTER_STAGE
    }

    /**
     * Line-of-sight raycast against solid obstacles.
     * Evaluates parametric line segment intersection with 2D axis-aligned obstacle boxes.
     */
    private fun checkLineOfSight(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        colliders: List<ObstacleCollider>
    ): Boolean {
        for (i in colliders.indices) {
            val col = colliders[i]
            // Only solid stage obstacles block vision
            if (col.type != ColliderType.SOLID) continue

            if (lineIntersectsAABB(x1, y1, x2, y2, col.bounds)) {
                return false
            }
        }
        return true
    }

    /**
     * Determines whether a 2D line segment intersects an AABB using the Liang-Barsky parametric algorithm.
     */
    private fun lineIntersectsAABB(x1: Float, y1: Float, x2: Float, y2: Float, box: AABB): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1

        var t0 = 0.0f
        var t1 = 1.0f

        val p = floatArrayOf(-dx, dx, -dy, dy)
        val q = floatArrayOf(x1 - box.minX, box.maxX - x1, y1 - box.minY, box.maxY - y1)

        for (i in 0 until 4) {
            val pi = p[i]
            val qi = q[i]

            if (pi == 0.0f) {
                if (qi < 0.0f) return false
            } else {
                val t = qi / pi
                if (pi < 0.0f) {
                    if (t > t1) return false
                    if (t > t0) t0 = t
                } else {
                    if (t < t0) return false
                    if (t < t1) t1 = t
                }
            }
        }
        return t0 <= t1
    }

    /**
     * Detects active hitboxes threatening the bot within a dynamic lookahead radius.
     */
    private fun detectIncomingThreats(botBounds: AABB, threatHitboxes: List<CombatHitbox>) {
        currentSnapshot.incomingThreatDetected = false
        currentSnapshot.threatDistance = Float.MAX_VALUE

        val lookaheadRadius = 90f // Proximity detection sphere
        val botX = botBounds.centerX
        val botY = botBounds.centerY

        for (i in threatHitboxes.indices) {
            val h = threatHitboxes[i]
            if (!h.isActive) continue

            val hx = (h.bounds.minX + h.bounds.maxX) * 0.5f
            val hy = (h.bounds.minY + h.bounds.maxY) * 0.5f

            val dx = hx - botX
            val dy = hy - botY
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < lookaheadRadius) {
                currentSnapshot.incomingThreatDetected = true
                if (dist < currentSnapshot.threatDistance) {
                    currentSnapshot.threatDistance = dist
                }
            }
        }
    }
}
