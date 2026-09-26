package com.example.controller

/**
 * Decoupled movement parameters configuration.
 * Mirrors ScriptableObject / Data Resource patterns to allow instant tuning and zero logic coupling.
 */
data class CharacterMovementConfig(
    // Horizontal Ground Movement
    val groundAcceleration: Float = 2800f,
    val groundFriction: Float = 3200f,
    val maxGroundSpeed: Float = 520f,
    val turnAroundBoost: Float = 1.35f,

    // Horizontal Aerial Movement
    val airAcceleration: Float = 1900f,
    val airFriction: Float = 650f,
    val maxAirSpeed: Float = 480f,

    // Vertical Movement & Gravity
    val gravity: Float = 1950f,
    val maxFallSpeed: Float = 820f,
    val jumpCutMultiplier: Float = 2.4f,
    val fastFallThresholdStickY: Float = 0.65f,
    val fastFallGravity: Float = 3200f,
    val fastFallMaxSpeed: Float = 1380f,

    // Jump Parameters
    val jumpImpulse: Float = -760f,
    val airJumpImpulse: Float = -690f,
    val maxAirJumps: Int = 2,
    val minJumpFrames: Int = 3,
    val coyoteFrames: Int = 5,
    val jumpBufferFrames: Int = 5,

    // Dash / Dodge Parameters (Brawlhalla ground dash & chase dodge)
    val dashSpeed: Float = 880f,
    val dashDurationFrames: Int = 14,
    val dashCooldownFrames: Int = 22,
    val backDashPenalty: Float = 0.82f,
    val dashInvincibleStartFrame: Int = 1,
    val dashInvincibleEndFrame: Int = 10,

    // Wall Mechanics (Brawlhalla Wall Cling / Wall Slide / Wall Slip)
    val wallSlideMaxFallSpeed: Float = 220f,
    val wallClingMaxDurationFrames: Int = 40,
    val wallJumpVelocityX: Float = 540f,
    val wallJumpVelocityY: Float = -720f,
    val wallSlipMaxTouches: Int = 3,

    // Collider Dimensions
    val colliderWidth: Float = 46f,
    val colliderHeight: Float = 66f
)
