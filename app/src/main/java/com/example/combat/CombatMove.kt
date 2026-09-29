package com.example.combat

import com.example.engine.animation.BoneId

/**
 * Complete Brawlhalla 1:1 Combat Move Matrix.
 * Divided into Grounded Lights, Grounded Signatures (Heavies),
 * Aerial Lights, and Aerial Heavy/Specials.
 */
enum class BrawlhallaMoveType {
    // Grounded Light Attacks
    NEUTRAL_LIGHT, // nLight: Fast stationary combo opener
    SIDE_LIGHT,    // sLight: Lunging / sliding horizontal slash
    DOWN_LIGHT,    // dLight: Low sweep pop-up starter

    // Grounded Heavy Signatures (Chargeable)
    NEUTRAL_SIG,   // nSig: Anti-air heavy signature blast
    SIDE_SIG,      // sSig: High-momentum charging signature
    DOWN_SIG,      // dSig: Defensive ground-covering eruption

    // Aerial Light Attacks
    NEUTRAL_AIR,   // nAir: 360-degree aerial halo swipe
    SIDE_AIR,      // sAir: Forward horizontal aerial kick/strike
    DOWN_AIR,      // dAir: Downward diagonal aerial spike/thrust

    // Aerial Heavy Specials
    RECOVERY,      // Upward leaping vertical recovery swing
    GROUND_POUND   // Steep downward vertical dive bomb
}

/**
 * Three-Phase Frame Lifecycle of a Fighting Game Move.
 */
enum class CombatPhase {
    IDLE,      // Neutral / Ready for input
    STARTUP,   // Wind-up frames before hitbox appears
    ACTIVE,    // Frame window where hitbox is active and delivers damage
    RECOVERY   // Endlag frames after hitbox expires before returning to neutral
}

/**
 * Frame-exact structural specification for each combat move.
 */
data class CombatMoveDefinition(
    val moveType: BrawlhallaMoveType,
    val displayName: String,
    val isAerial: Boolean,
    val isHeavySig: Boolean,

    // 3 Distinct Frame Windows
    val startupFrames: Int,
    val activeFrames: Int,
    val recoveryFrames: Int,

    // Momentum & Sliding Physics
    val initialImpulseX: Float = 0f,
    val initialImpulseY: Float = 0f,
    val momentumPreservationRatio: Float = 0.85f, // Ratio of running velocity carried into attack
    val slideFriction: Float = 1400f,             // Custom ground friction applied during attack slide
    val allowAirDrift: Boolean = true,
    val airDriftAcceleration: Float = 900f,

    // Damage & Knockback Payload
    val damage: Float,
    val baseKnockback: Float,
    val knockbackScaling: Float,
    val knockbackAngleDeg: Float,
    val hitStunFrames: Int,

    // Hitbox Geometry & Skeletal Attachment
    val hitboxRelativeOffsetX: Float,
    val hitboxRelativeOffsetY: Float,
    val hitboxWidth: Float,
    val hitboxHeight: Float,
    val attachedBone: BoneId = BoneId.WEAPON_R,

    // Visual Trail Color
    val vfxTrailHexColor: String = "#FF4500"
) {
    val totalFrames: Int
        get() = startupFrames + activeFrames + recoveryFrames
}

/**
 * Global repository of standard Brawlhalla Combat Move Specifications.
 */
object BrawlhallaCombatMatrix {

    val MOVES: Map<BrawlhallaMoveType, CombatMoveDefinition> = mapOf(
        // 1. NEUTRAL LIGHT (nLight)
        BrawlhallaMoveType.NEUTRAL_LIGHT to CombatMoveDefinition(
            moveType = BrawlhallaMoveType.NEUTRAL_LIGHT,
            displayName = "Neutral Light",
            isAerial = false,
            isHeavySig = false,
            startupFrames = 4,
            activeFrames = 4,
            recoveryFrames = 8,
            initialImpulseX = 40f,
            initialImpulseY = 0f,
            momentumPreservationRatio = 0.35f,
            slideFriction = 3200f,
            damage = 11f,
            baseKnockback = 170f,
            knockbackScaling = 0.95f,
            knockbackAngleDeg = 45f,
            hitStunFrames = 14,
            hitboxRelativeOffsetX = 26f,
            hitboxRelativeOffsetY = -10f,
            hitboxWidth = 44f,
            hitboxHeight = 34f,
            attachedBone = BoneId.WEAPON_R,
            vfxTrailHexColor = "#FF4500" // Flame Orange
        ),

        // 2. SIDE LIGHT (sLight) - Momentum-carrying sliding slash
        BrawlhallaMoveType.SIDE_LIGHT to CombatMoveDefinition(
            moveType = BrawlhallaMoveType.SIDE_LIGHT,
            displayName = "Side Light",
            isAerial = false,
            isHeavySig = false,
            startupFrames = 6,
            activeFrames = 5,
            recoveryFrames = 10,
            initialImpulseX = 320f, // Forward burst
            initialImpulseY = 0f,
            momentumPreservationRatio = 0.88f, // Preserves high sprint speed for long sliding slice
            slideFriction = 1200f,
            damage = 14f,
            baseKnockback = 220f,
            knockbackScaling = 1.15f,
            knockbackAngleDeg = 35f,
            hitStunFrames = 17,
            hitboxRelativeOffsetX = 34f,
            hitboxRelativeOffsetY = -8f,
            hitboxWidth = 52f,
            hitboxHeight = 32f,
            attachedBone = BoneId.WEAPON_R,
            vfxTrailHexColor = "#00FFCC" // Electric Cyan
        ),

        // 3. DOWN LIGHT (dLight) - Low pop-up sweep
        BrawlhallaMoveType.DOWN_LIGHT to CombatMoveDefinition(
            moveType = BrawlhallaMoveType.DOWN_LIGHT,
            displayName = "Down Light",
            isAerial = false,
            isHeavySig = false,
            startupFrames = 5,
            activeFrames = 4,
            recoveryFrames = 9,
            initialImpulseX = 110f,
            initialImpulseY = 0f,
            momentumPreservationRatio = 0.50f,
            slideFriction = 2400f,
            damage = 12f,
            baseKnockback = 200f,
            knockbackScaling = 0.80f,
            knockbackAngleDeg = 82f, // High vertical pop-up for aerial follow-ups
            hitStunFrames = 20,
            hitboxRelativeOffsetX = 28f,
            hitboxRelativeOffsetY = 12f,
            hitboxWidth = 48f,
            hitboxHeight = 24f,
            attachedBone = BoneId.FOOT_R,
            vfxTrailHexColor = "#FFD700" // Gold
        ),

        // 4. NEUTRAL SIGNATURE (nSig) - Anti-Air Heavy
        BrawlhallaMoveType.NEUTRAL_SIG to CombatMoveDefinition(
            moveType = BrawlhallaMoveType.NEUTRAL_SIG,
            displayName = "Neutral Signature",
            isAerial = false,
            isHeavySig = true,
            startupFrames = 10,
            activeFrames = 6,
            recoveryFrames = 16,
            initialImpulseX = 20f,
            initialImpulseY = -120f, // Slight upward hop
            momentumPreservationRatio = 0.40f,
            slideFriction = 2200f,
            damage = 24f,
            baseKnockback = 340f,
            knockbackScaling = 1.55f,
            knockbackAngleDeg = 65f,
            hitStunFrames = 25,
            hitboxRelativeOffsetX = 20f,
            hitboxRelativeOffsetY = -38f, // High vertical reach
            hitboxWidth = 48f,
            hitboxHeight = 56f,
            attachedBone = BoneId.WEAPON_R,
            vfxTrailHexColor = "#B388FF" // Neon Purple
        ),

        // 5. SIDE SIGNATURE (sSig) - Lunging Heavy Executioner
        BrawlhallaMoveType.SIDE_SIG to CombatMoveDefinition(
            moveType = BrawlhallaMoveType.SIDE_SIG,
            displayName = "Side Signature",
            isAerial = false,
            isHeavySig = true,
            startupFrames = 12,
            activeFrames = 7,
            recoveryFrames = 18,
            initialImpulseX = 460f, // Massive forward charge
            initialImpulseY = 0f,
            momentumPreservationRatio = 0.92f,
            slideFriction = 950f,
            damage = 28f,
            baseKnockback = 390f,
            knockbackScaling = 1.70f,
            knockbackAngleDeg = 30f,
            hitStunFrames = 28,
            hitboxRelativeOffsetX = 42f,
            hitboxRelativeOffsetY = -10f,
            hitboxWidth = 60f,
            hitboxHeight = 36f,
            attachedBone = BoneId.WEAPON_R,
            vfxTrailHexColor = "#FF1744" // Crimson Danger
        ),

        // 6. DOWN SIGNATURE (dSig) - Area of Effect Ground Eruption
        BrawlhallaMoveType.DOWN_SIG to CombatMoveDefinition(
            moveType = BrawlhallaMoveType.DOWN_SIG,
            displayName = "Down Signature",
            isAerial = false,
            isHeavySig = true,
            startupFrames = 11,
            activeFrames = 8,
            recoveryFrames = 17,
            initialImpulseX = 0f,
            initialImpulseY = 0f,
            momentumPreservationRatio = 0.20f,
            slideFriction = 3500f,
            damage = 25f,
            baseKnockback = 360f,
            knockbackScaling = 1.60f,
            knockbackAngleDeg = 50f,
            hitStunFrames = 26,
            hitboxRelativeOffsetX = 0f, // Surrounds character
            hitboxRelativeOffsetY = 6f,
            hitboxWidth = 78f,
            hitboxHeight = 36f,
            attachedBone = BoneId.PELVIS,
            vfxTrailHexColor = "#00E5FF" // Cyan Shockwave
        ),

        // 7. NEUTRAL AIR (nAir) - 360 Degree Aerial Halo
        BrawlhallaMoveType.NEUTRAL_AIR to CombatMoveDefinition(
            moveType = BrawlhallaMoveType.NEUTRAL_AIR,
            displayName = "Neutral Air",
            isAerial = true,
            isHeavySig = false,
            startupFrames = 5,
            activeFrames = 6,
            recoveryFrames = 8,
            initialImpulseX = 0f,
            initialImpulseY = 0f,
            momentumPreservationRatio = 1.0f, // 100% aerial aerodynamic conservation
            allowAirDrift = true,
            damage = 13f,
            baseKnockback = 190f,
            knockbackScaling = 1.05f,
            knockbackAngleDeg = 55f,
            hitStunFrames = 15,
            hitboxRelativeOffsetX = 0f,
            hitboxRelativeOffsetY = -6f,
            hitboxWidth = 64f,
            hitboxHeight = 64f,
            attachedBone = BoneId.TORSO,
            vfxTrailHexColor = "#FFD700"
        ),

        // 8. SIDE AIR (sAir) - Directional Horizontal Thrust
        BrawlhallaMoveType.SIDE_AIR to CombatMoveDefinition(
            moveType = BrawlhallaMoveType.SIDE_AIR,
            displayName = "Side Air",
            isAerial = true,
            isHeavySig = false,
            startupFrames = 6,
            activeFrames = 5,
            recoveryFrames = 10,
            initialImpulseX = 90f,
            initialImpulseY = -20f,
            momentumPreservationRatio = 0.95f,
            allowAirDrift = true,
            damage = 15f,
            baseKnockback = 240f,
            knockbackScaling = 1.25f,
            knockbackAngleDeg = 35f,
            hitStunFrames = 18,
            hitboxRelativeOffsetX = 32f,
            hitboxRelativeOffsetY = -6f,
            hitboxWidth = 50f,
            hitboxHeight = 32f,
            attachedBone = BoneId.WEAPON_R,
            vfxTrailHexColor = "#00FFCC"
        ),

        // 9. DOWN AIR (dAir) - Diagonal Spike
        BrawlhallaMoveType.DOWN_AIR to CombatMoveDefinition(
            moveType = BrawlhallaMoveType.DOWN_AIR,
            displayName = "Down Air",
            isAerial = true,
            isHeavySig = false,
            startupFrames = 7,
            activeFrames = 5,
            recoveryFrames = 12,
            initialImpulseX = 20f,
            initialImpulseY = 50f,
            momentumPreservationRatio = 0.90f,
            allowAirDrift = true,
            damage = 16f,
            baseKnockback = 260f,
            knockbackScaling = 1.35f,
            knockbackAngleDeg = 290f, // Downward meteor spike
            hitStunFrames = 22,
            hitboxRelativeOffsetX = 16f,
            hitboxRelativeOffsetY = 32f,
            hitboxWidth = 42f,
            hitboxHeight = 44f,
            attachedBone = BoneId.FOOT_R,
            vfxTrailHexColor = "#FF1744"
        ),

        // 10. RECOVERY (Aerial Special Up)
        BrawlhallaMoveType.RECOVERY to CombatMoveDefinition(
            moveType = BrawlhallaMoveType.RECOVERY,
            displayName = "Recovery",
            isAerial = true,
            isHeavySig = true,
            startupFrames = 8,
            activeFrames = 8,
            recoveryFrames = 14,
            initialImpulseX = 0f,
            initialImpulseY = -620f, // Massive upward vertical propulsion
            momentumPreservationRatio = 0.75f,
            allowAirDrift = true,
            damage = 22f,
            baseKnockback = 330f,
            knockbackScaling = 1.50f,
            knockbackAngleDeg = 75f,
            hitStunFrames = 24,
            hitboxRelativeOffsetX = 0f,
            hitboxRelativeOffsetY = -36f,
            hitboxWidth = 56f,
            hitboxHeight = 56f,
            attachedBone = BoneId.WEAPON_R,
            vfxTrailHexColor = "#FF4500"
        ),

        // 11. GROUND POUND (Aerial Special Down)
        BrawlhallaMoveType.GROUND_POUND to CombatMoveDefinition(
            moveType = BrawlhallaMoveType.GROUND_POUND,
            displayName = "Ground Pound",
            isAerial = true,
            isHeavySig = true,
            startupFrames = 9,
            activeFrames = 60, // Remains active until hitting stage or manual release
            recoveryFrames = 16,
            initialImpulseX = 0f,
            initialImpulseY = 850f, // Rocket downward dive
            momentumPreservationRatio = 0.50f,
            allowAirDrift = false,
            damage = 26f,
            baseKnockback = 380f,
            knockbackScaling = 1.65f,
            knockbackAngleDeg = 270f, // Pure downward death spike
            hitStunFrames = 28,
            hitboxRelativeOffsetX = 0f,
            hitboxRelativeOffsetY = 38f,
            hitboxWidth = 46f,
            hitboxHeight = 54f,
            attachedBone = BoneId.FOOT_R,
            vfxTrailHexColor = "#FF1744"
        )
    )

    fun getMove(type: BrawlhallaMoveType): CombatMoveDefinition {
        return MOVES[type] ?: error("Undefined move definition for $type")
    }
}
