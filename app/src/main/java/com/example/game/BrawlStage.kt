package com.example.game

import com.example.engine.collision.AABB
import com.example.engine.collision.ColliderType
import com.example.engine.collision.ObstacleCollider

/**
 * Defines the arena geometry for the 2D Platform Fighter.
 * Mirrors classic competitive platform fighter stages (e.g. Brawlhalla Mammoth Fortress / Smash Battlefield).
 */
class BrawlStage(
    val stageWidth: Float = 1000f,
    val stageHeight: Float = 600f
) {
    val colliders = mutableListOf<ObstacleCollider>()

    // Blast zones (bounds beyond which a KO / respawn occurs)
    val blastZoneLeft: Float = -250f
    val blastZoneRight: Float = stageWidth + 250f
    val blastZoneTop: Float = -350f
    val blastZoneBottom: Float = stageHeight + 250f

    // Main Floating Stage Platform
    val mainPlatformMinX = 240f
    val mainPlatformMaxX = 760f
    val mainPlatformMinY = 380f
    val mainPlatformMaxY = 560f

    // Left Floating Soft Platform (One-Way)
    val leftPlatformMinX = 260f
    val leftPlatformMaxX = 420f
    val leftPlatformY = 270f

    // Right Floating Soft Platform (One-Way)
    val rightPlatformMinX = 580f
    val rightPlatformMaxX = 740f
    val rightPlatformY = 270f

    // Top Floating Soft Platform (One-Way)
    val topPlatformMinX = 420f
    val topPlatformMaxX = 580f
    val topPlatformY = 170f

    init {
        // 1. Solid Main Stage Block (Solid floor, left wall, right wall)
        colliders.add(
            ObstacleCollider(
                bounds = AABB(
                    minX = mainPlatformMinX,
                    minY = mainPlatformMinY,
                    maxX = mainPlatformMaxX,
                    maxY = mainPlatformMaxY
                ),
                type = ColliderType.SOLID,
                id = "main_stage_solid"
            )
        )

        // 2. Left Floating One-Way Platform
        colliders.add(
            ObstacleCollider(
                bounds = AABB(
                    minX = leftPlatformMinX,
                    minY = leftPlatformY,
                    maxX = leftPlatformMaxX,
                    maxY = leftPlatformY + 12f
                ),
                type = ColliderType.ONE_WAY_PLATFORM,
                id = "platform_left_oneway"
            )
        )

        // 3. Right Floating One-Way Platform
        colliders.add(
            ObstacleCollider(
                bounds = AABB(
                    minX = rightPlatformMinX,
                    minY = rightPlatformY,
                    maxX = rightPlatformMaxX,
                    maxY = rightPlatformY + 12f
                ),
                type = ColliderType.ONE_WAY_PLATFORM,
                id = "platform_right_oneway"
            )
        )

        // 4. Top Floating One-Way Platform
        colliders.add(
            ObstacleCollider(
                bounds = AABB(
                    minX = topPlatformMinX,
                    minY = topPlatformY,
                    maxX = topPlatformMaxX,
                    maxY = topPlatformY + 12f
                ),
                type = ColliderType.ONE_WAY_PLATFORM,
                id = "platform_top_oneway"
            )
        )
    }

    fun isOutOfBounds(x: Float, y: Float): Boolean {
        return x < blastZoneLeft || x > blastZoneRight || y < blastZoneTop || y > blastZoneBottom
    }
}
