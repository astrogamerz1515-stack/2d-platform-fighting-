package com.example.engine.animation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Structural Bone Identifiers for 2D Cutout / Skeletal Fighter Hierarchy.
 * Maps directly to individual layered sprite attachments.
 */
enum class BoneId {
    ROOT,
    PELVIS,
    TORSO,
    HEAD,
    UPPER_ARM_L,
    LOWER_ARM_L,
    HAND_L,
    WEAPON_L,
    UPPER_ARM_R,
    LOWER_ARM_R,
    HAND_R,
    WEAPON_R,
    UPPER_LEG_L,
    LOWER_LEG_L,
    FOOT_L,
    UPPER_LEG_R,
    LOWER_LEG_R,
    FOOT_R
}

/**
 * Standardized Animation Clip Identifiers mapped to Kinematic FSM & Combat Moves.
 */
enum class AnimationId {
    IDLE,
    RUN,
    DASH,
    JUMP_RISE,
    JUMP_APEX,
    FALL,
    WALL_CLING,
    WALL_SLIDE,
    ATTACK_NEUTRAL,
    ATTACK_SIDE,
    ATTACK_DOWN,
    HITSTUN,
    KNOCKBACK,
    RESPAWN
}

/**
 * Explicit Layer Z-Order sorting indexes for 2D cutout depth rendering.
 */
object ZOrderLayer {
    const val WEAPON_BACK = 10
    const val HAND_BACK = 12
    const val ARM_BACK_LOWER = 14
    const val ARM_BACK_UPPER = 16
    const val FOOT_BACK = 20
    const val LEG_BACK_LOWER = 22
    const val LEG_BACK_UPPER = 24
    const val PELVIS = 30
    const val TORSO = 35
    const val HEAD = 40
    const val LEG_FRONT_UPPER = 50
    const val LEG_FRONT_LOWER = 52
    const val FOOT_FRONT = 54
    const val ARM_FRONT_UPPER = 60
    const val ARM_FRONT_LOWER = 62
    const val HAND_FRONT = 64
    const val WEAPON_FRONT = 70
}

/**
 * Sprite Anchor & Attachment Specification with Hex Color Verification Anchors.
 */
data class AnchorPoint(
    val normalizedX: Float,
    val normalizedY: Float,
    val pivotColorHex: String = "#FF00FF",      // Magenta: Base joint rotational pivot
    val attachmentColorHex: String = "#00FFFF" // Cyan: Child socket attachment
)

/**
 * 2D Affine Transformation Matrix (3x3 column-major) for zero-allocation skeletal kinematics.
 */
class AffineMatrix2D(
    var m00: Float = 1f, var m01: Float = 0f, var m02: Float = 0f,
    var m10: Float = 0f, var m11: Float = 1f, var m12: Float = 0f
) {
    fun identity() {
        m00 = 1f; m01 = 0f; m02 = 0f
        m10 = 0f; m11 = 1f; m12 = 0f
    }

    fun copyFrom(other: AffineMatrix2D) {
        m00 = other.m00; m01 = other.m01; m02 = other.m02
        m10 = other.m10; m11 = other.m11; m12 = other.m12
    }

    fun multiply(local: AffineMatrix2D) {
        val t00 = m00 * local.m00 + m01 * local.m10
        val t01 = m00 * local.m01 + m01 * local.m11
        val t02 = m00 * local.m02 + m01 * local.m12 + m02

        val t10 = m10 * local.m00 + m11 * local.m10
        val t11 = m10 * local.m01 + m11 * local.m11
        val t12 = m10 * local.m02 + m11 * local.m12 + m12

        m00 = t00; m01 = t01; m02 = t02
        m10 = t10; m11 = t11; m12 = t12
    }

    fun setTRS(tx: Float, ty: Float, rotDegrees: Float, sx: Float, sy: Float) {
        val rad = (rotDegrees * (PI / 180.0)).toFloat()
        val c = cos(rad)
        val s = sin(rad)

        m00 = c * sx
        m01 = -s * sy
        m02 = tx

        m10 = s * sx
        m11 = c * sy
        m12 = ty
    }

    fun transformX(x: Float, y: Float): Float = m00 * x + m01 * y + m02
    fun transformY(x: Float, y: Float): Float = m10 * x + m11 * y + m12
}

/**
 * Local spatial transform for an individual skeletal bone relative to parent.
 */
data class BoneTransform(
    var tx: Float = 0f,
    var ty: Float = 0f,
    var rotationDegrees: Float = 0f,
    var sx: Float = 1f,
    var sy: Float = 1f
) {
    fun copy(): BoneTransform = BoneTransform(tx, ty, rotationDegrees, sx, sy)

    fun lerp(target: BoneTransform, alpha: Float, result: BoneTransform) {
        result.tx = tx + (target.tx - tx) * alpha
        result.ty = ty + (target.ty - ty) * alpha
        result.rotationDegrees = rotationDegrees + (target.rotationDegrees - rotationDegrees) * alpha
        result.sx = sx + (target.sx - sx) * alpha
        result.sy = sy + (target.sy - sy) * alpha
    }
}

/**
 * Discrete Combat Animation Event Types.
 */
enum class AnimationEventType {
    HITBOX_ACTIVATE,
    HITBOX_DEACTIVATE,
    HURTBOX_ENABLE,
    HURTBOX_DISABLE,
    TRAIL_EFFECT_START,
    TRAIL_EFFECT_END,
    SFX_TRIGGER,
    SCREEN_SHAKE
}

/**
 * Data payload for combat hitboxes spawned on exact animation frames.
 */
data class HitboxDefinition(
    val relativeX: Float,
    val relativeY: Float,
    val width: Float,
    val height: Float,
    val damage: Float,
    val baseKnockback: Float,
    val knockbackScaling: Float,
    val knockbackAngleDeg: Float,
    val hitStunFrames: Int
)

/**
 * Animation Event Trigger mapped to an exact discrete frame.
 */
data class AnimationEvent(
    val frame: Int,
    val type: AnimationEventType,
    val hitbox: HitboxDefinition? = null,
    val hexColor: String? = null,
    val soundId: String? = null,
    val shakeIntensity: Float = 0f
)

/**
 * Single pose snapshot at a specific frame index.
 */
data class Keyframe(
    val frame: Int,
    val boneTransforms: Map<BoneId, BoneTransform>
)

/**
 * Complete clip definition with discrete keyframes and event triggers.
 */
data class AnimationClip(
    val id: AnimationId,
    val name: String,
    val totalFrames: Int,
    val playbackFps: Int = 60,
    val isLooping: Boolean,
    val keyframes: List<Keyframe>,
    val events: List<AnimationEvent> = emptyList()
) {
    fun samplePose(frame: Float, output: MutableMap<BoneId, BoneTransform>) {
        if (keyframes.isEmpty()) return
        if (keyframes.size == 1) {
            val kf = keyframes[0]
            for ((bone, transform) in kf.boneTransforms) {
                output[bone] = transform.copy()
            }
            return
        }

        val clampedFrame = if (isLooping) {
            val mod = frame % totalFrames
            if (mod < 0f) mod + totalFrames else mod
        } else {
            frame.coerceIn(0f, (totalFrames - 1).toFloat())
        }

        var prevIndex = 0
        var nextIndex = 0
        for (i in 0 until keyframes.size - 1) {
            if (clampedFrame >= keyframes[i].frame && clampedFrame <= keyframes[i + 1].frame) {
                prevIndex = i
                nextIndex = i + 1
                break
            }
        }

        val kf0 = keyframes[prevIndex]
        val kf1 = keyframes[nextIndex]

        val span = (kf1.frame - kf0.frame).coerceAtLeast(1)
        val alpha = (clampedFrame - kf0.frame) / span.toFloat()

        for (bone in BoneId.values()) {
            val t0 = kf0.boneTransforms[bone]
            val t1 = kf1.boneTransforms[bone]
            if (t0 != null && t1 != null) {
                val target = output.getOrPut(bone) { BoneTransform() }
                t0.lerp(t1, alpha, target)
            } else if (t0 != null) {
                val target = output.getOrPut(bone) { BoneTransform() }
                target.tx = t0.tx
                target.ty = t0.ty
                target.rotationDegrees = t0.rotationDegrees
                target.sx = t0.sx
                target.sy = t0.sy
            }
        }
    }
}

/**
 * Character Skeletal Rig Configuration & Animation Catalog Profile.
 */
data class AnimationDataProfile(
    val characterId: String,
    val boneHierarchy: Map<BoneId, BoneId?>,
    val defaultBindPose: Map<BoneId, BoneTransform>,
    val zOrderMap: Map<BoneId, Int>,
    val anchorPoints: Map<BoneId, AnchorPoint>,
    val clips: Map<AnimationId, AnimationClip>
) {
    companion object {
        /**
         * Generates the commercial-grade default skeletal animation profile.
         */
        fun createStandardFighterProfile(characterId: String): AnimationDataProfile {
            val hierarchy = mapOf<BoneId, BoneId?>(
                BoneId.ROOT to null,
                BoneId.PELVIS to BoneId.ROOT,
                BoneId.TORSO to BoneId.PELVIS,
                BoneId.HEAD to BoneId.TORSO,

                BoneId.UPPER_ARM_L to BoneId.TORSO,
                BoneId.LOWER_ARM_L to BoneId.UPPER_ARM_L,
                BoneId.HAND_L to BoneId.LOWER_ARM_L,
                BoneId.WEAPON_L to BoneId.HAND_L,

                BoneId.UPPER_ARM_R to BoneId.TORSO,
                BoneId.LOWER_ARM_R to BoneId.UPPER_ARM_R,
                BoneId.HAND_R to BoneId.LOWER_ARM_R,
                BoneId.WEAPON_R to BoneId.HAND_R,

                BoneId.UPPER_LEG_L to BoneId.PELVIS,
                BoneId.LOWER_LEG_L to BoneId.UPPER_LEG_L,
                BoneId.FOOT_L to BoneId.LOWER_LEG_L,

                BoneId.UPPER_LEG_R to BoneId.PELVIS,
                BoneId.LOWER_LEG_R to BoneId.UPPER_LEG_R,
                BoneId.FOOT_R to BoneId.LOWER_LEG_R
            )

            val bindPose = mapOf(
                BoneId.ROOT to BoneTransform(0f, 0f, 0f, 1f, 1f),
                BoneId.PELVIS to BoneTransform(0f, 0f, 0f, 1f, 1f),
                BoneId.TORSO to BoneTransform(0f, -18f, 0f, 1f, 1f),
                BoneId.HEAD to BoneTransform(0f, -22f, 0f, 1f, 1f),

                BoneId.UPPER_ARM_L to BoneTransform(-10f, -14f, 15f, 1f, 1f),
                BoneId.LOWER_ARM_L to BoneTransform(0f, 14f, 10f, 1f, 1f),
                BoneId.HAND_L to BoneTransform(0f, 12f, 0f, 1f, 1f),
                BoneId.WEAPON_L to BoneTransform(0f, 6f, 0f, 1f, 1f),

                BoneId.UPPER_ARM_R to BoneTransform(10f, -14f, -15f, 1f, 1f),
                BoneId.LOWER_ARM_R to BoneTransform(0f, 14f, -10f, 1f, 1f),
                BoneId.HAND_R to BoneTransform(0f, 12f, 0f, 1f, 1f),
                BoneId.WEAPON_R to BoneTransform(0f, 6f, 0f, 1f, 1f),

                BoneId.UPPER_LEG_L to BoneTransform(-8f, 10f, 0f, 1f, 1f),
                BoneId.LOWER_LEG_L to BoneTransform(0f, 16f, 0f, 1f, 1f),
                BoneId.FOOT_L to BoneTransform(0f, 12f, 0f, 1f, 1f),

                BoneId.UPPER_LEG_R to BoneTransform(8f, 10f, 0f, 1f, 1f),
                BoneId.LOWER_LEG_R to BoneTransform(0f, 16f, 0f, 1f, 1f),
                BoneId.FOOT_R to BoneTransform(0f, 12f, 0f, 1f, 1f)
            )

            val zOrder = mapOf(
                BoneId.WEAPON_L to ZOrderLayer.WEAPON_BACK,
                BoneId.HAND_L to ZOrderLayer.HAND_BACK,
                BoneId.LOWER_ARM_L to ZOrderLayer.ARM_BACK_LOWER,
                BoneId.UPPER_ARM_L to ZOrderLayer.ARM_BACK_UPPER,
                BoneId.FOOT_L to ZOrderLayer.FOOT_BACK,
                BoneId.LOWER_LEG_L to ZOrderLayer.LEG_BACK_LOWER,
                BoneId.UPPER_LEG_L to ZOrderLayer.LEG_BACK_UPPER,
                BoneId.PELVIS to ZOrderLayer.PELVIS,
                BoneId.TORSO to ZOrderLayer.TORSO,
                BoneId.HEAD to ZOrderLayer.HEAD,
                BoneId.UPPER_LEG_R to ZOrderLayer.LEG_FRONT_UPPER,
                BoneId.LOWER_LEG_R to ZOrderLayer.LEG_FRONT_LOWER,
                BoneId.FOOT_R to ZOrderLayer.FOOT_FRONT,
                BoneId.UPPER_ARM_R to ZOrderLayer.ARM_FRONT_UPPER,
                BoneId.LOWER_ARM_R to ZOrderLayer.ARM_FRONT_LOWER,
                BoneId.HAND_R to ZOrderLayer.HAND_FRONT,
                BoneId.WEAPON_R to ZOrderLayer.WEAPON_FRONT
            )

            val anchors = mapOf(
                BoneId.PELVIS to AnchorPoint(0.5f, 0.5f),
                BoneId.TORSO to AnchorPoint(0.5f, 0.9f),
                BoneId.HEAD to AnchorPoint(0.5f, 0.9f),
                BoneId.UPPER_ARM_L to AnchorPoint(0.5f, 0.1f),
                BoneId.LOWER_ARM_L to AnchorPoint(0.5f, 0.1f),
                BoneId.HAND_L to AnchorPoint(0.5f, 0.2f),
                BoneId.WEAPON_L to AnchorPoint(0.5f, 0.8f),
                BoneId.UPPER_ARM_R to AnchorPoint(0.5f, 0.1f),
                BoneId.LOWER_ARM_R to AnchorPoint(0.5f, 0.1f),
                BoneId.HAND_R to AnchorPoint(0.5f, 0.2f),
                BoneId.WEAPON_R to AnchorPoint(0.5f, 0.8f),
                BoneId.UPPER_LEG_L to AnchorPoint(0.5f, 0.1f),
                BoneId.LOWER_LEG_L to AnchorPoint(0.5f, 0.1f),
                BoneId.FOOT_L to AnchorPoint(0.3f, 0.2f),
                BoneId.UPPER_LEG_R to AnchorPoint(0.5f, 0.1f),
                BoneId.LOWER_LEG_R to AnchorPoint(0.5f, 0.1f),
                BoneId.FOOT_R to AnchorPoint(0.3f, 0.2f)
            )

            val clips = createDefaultClips(bindPose)

            return AnimationDataProfile(
                characterId = characterId,
                boneHierarchy = hierarchy,
                defaultBindPose = bindPose,
                zOrderMap = zOrder,
                anchorPoints = anchors,
                clips = clips
            )
        }

        private fun createDefaultClips(bind: Map<BoneId, BoneTransform>): Map<AnimationId, AnimationClip> {
            val clipMap = mutableMapOf<AnimationId, AnimationClip>()

            // 1. IDLE (Breathe Loop, 60 frames)
            val idleKeyframes = listOf(
                Keyframe(0, bind.mapValues { (bone, t) ->
                    when (bone) {
                        BoneId.TORSO -> t.copy(ty = -18f, rotationDegrees = 0f)
                        BoneId.HEAD -> t.copy(ty = -22f, rotationDegrees = 0f)
                        BoneId.UPPER_ARM_L -> t.copy(rotationDegrees = 12f)
                        BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -12f)
                        else -> t.copy()
                    }
                }),
                Keyframe(30, bind.mapValues { (bone, t) ->
                    when (bone) {
                        BoneId.TORSO -> t.copy(ty = -16.5f, rotationDegrees = 1.5f)
                        BoneId.HEAD -> t.copy(ty = -21f, rotationDegrees = -1.5f)
                        BoneId.UPPER_ARM_L -> t.copy(rotationDegrees = 18f)
                        BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -18f)
                        else -> t.copy()
                    }
                }),
                Keyframe(60, bind.mapValues { (bone, t) ->
                    when (bone) {
                        BoneId.TORSO -> t.copy(ty = -18f, rotationDegrees = 0f)
                        BoneId.HEAD -> t.copy(ty = -22f, rotationDegrees = 0f)
                        BoneId.UPPER_ARM_L -> t.copy(rotationDegrees = 12f)
                        BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -12f)
                        else -> t.copy()
                    }
                })
            )
            clipMap[AnimationId.IDLE] = AnimationClip(
                id = AnimationId.IDLE,
                name = "Idle_Loop",
                totalFrames = 60,
                isLooping = true,
                keyframes = idleKeyframes
            )

            // 2. RUN (Locomotion Loop, 30 frames)
            val runKeyframes = listOf(
                Keyframe(0, bind.mapValues { (bone, t) ->
                    when (bone) {
                        BoneId.TORSO -> t.copy(rotationDegrees = 8f)
                        BoneId.UPPER_LEG_L -> t.copy(rotationDegrees = 35f)
                        BoneId.LOWER_LEG_L -> t.copy(rotationDegrees = 15f)
                        BoneId.UPPER_LEG_R -> t.copy(rotationDegrees = -40f)
                        BoneId.LOWER_LEG_R -> t.copy(rotationDegrees = 50f)
                        BoneId.UPPER_ARM_L -> t.copy(rotationDegrees = -45f)
                        BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = 45f)
                        else -> t.copy()
                    }
                }),
                Keyframe(15, bind.mapValues { (bone, t) ->
                    when (bone) {
                        BoneId.TORSO -> t.copy(rotationDegrees = 8f)
                        BoneId.UPPER_LEG_L -> t.copy(rotationDegrees = -40f)
                        BoneId.LOWER_LEG_L -> t.copy(rotationDegrees = 50f)
                        BoneId.UPPER_LEG_R -> t.copy(rotationDegrees = 35f)
                        BoneId.LOWER_LEG_R -> t.copy(rotationDegrees = 15f)
                        BoneId.UPPER_ARM_L -> t.copy(rotationDegrees = 45f)
                        BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -45f)
                        else -> t.copy()
                    }
                }),
                Keyframe(30, bind.mapValues { (bone, t) ->
                    when (bone) {
                        BoneId.TORSO -> t.copy(rotationDegrees = 8f)
                        BoneId.UPPER_LEG_L -> t.copy(rotationDegrees = 35f)
                        BoneId.LOWER_LEG_L -> t.copy(rotationDegrees = 15f)
                        BoneId.UPPER_LEG_R -> t.copy(rotationDegrees = -40f)
                        BoneId.LOWER_LEG_R -> t.copy(rotationDegrees = 50f)
                        BoneId.UPPER_ARM_L -> t.copy(rotationDegrees = -45f)
                        BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = 45f)
                        else -> t.copy()
                    }
                })
            )
            clipMap[AnimationId.RUN] = AnimationClip(
                id = AnimationId.RUN,
                name = "Run_Loop",
                totalFrames = 30,
                isLooping = true,
                keyframes = runKeyframes
            )

            // 3. DASH (Burst Dodge, 16 frames with Invulnerability & Trail)
            val dashEvents = listOf(
                AnimationEvent(frame = 0, type = AnimationEventType.HURTBOX_DISABLE),
                AnimationEvent(frame = 1, type = AnimationEventType.TRAIL_EFFECT_START, hexColor = "#00FFCC"),
                AnimationEvent(frame = 11, type = AnimationEventType.HURTBOX_ENABLE),
                AnimationEvent(frame = 14, type = AnimationEventType.TRAIL_EFFECT_END)
            )
            val dashKeyframes = listOf(
                Keyframe(0, bind.mapValues { (bone, t) ->
                    when (bone) {
                        BoneId.TORSO -> t.copy(rotationDegrees = 25f, ty = -14f)
                        BoneId.HEAD -> t.copy(rotationDegrees = -10f)
                        BoneId.UPPER_ARM_L -> t.copy(rotationDegrees = -60f)
                        BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -70f)
                        BoneId.UPPER_LEG_L -> t.copy(rotationDegrees = 45f)
                        BoneId.UPPER_LEG_R -> t.copy(rotationDegrees = -55f)
                        else -> t.copy()
                    }
                }),
                Keyframe(16, bind.mapValues { (bone, t) ->
                    when (bone) {
                        BoneId.TORSO -> t.copy(rotationDegrees = 5f, ty = -18f)
                        BoneId.UPPER_ARM_L -> t.copy(rotationDegrees = 10f)
                        BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -10f)
                        else -> t.copy()
                    }
                })
            )
            clipMap[AnimationId.DASH] = AnimationClip(
                id = AnimationId.DASH,
                name = "Dash_Burst",
                totalFrames = 16,
                isLooping = false,
                keyframes = dashKeyframes,
                events = dashEvents
            )

            // 4. JUMP_RISE (Ascending, 20 frames)
            clipMap[AnimationId.JUMP_RISE] = AnimationClip(
                id = AnimationId.JUMP_RISE,
                name = "Jump_Ascend",
                totalFrames = 20,
                isLooping = false,
                keyframes = listOf(
                    Keyframe(0, bind.mapValues { (bone, t) ->
                        when (bone) {
                            BoneId.TORSO -> t.copy(ty = -20f, rotationDegrees = -5f)
                            BoneId.UPPER_ARM_L -> t.copy(rotationDegrees = -70f)
                            BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -70f)
                            BoneId.UPPER_LEG_L -> t.copy(rotationDegrees = 30f)
                            BoneId.UPPER_LEG_R -> t.copy(rotationDegrees = 20f)
                            else -> t.copy()
                        }
                    })
                )
            )

            // 5. JUMP_APEX (Zero-G Apex, 10 frames)
            clipMap[AnimationId.JUMP_APEX] = AnimationClip(
                id = AnimationId.JUMP_APEX,
                name = "Jump_Apex",
                totalFrames = 10,
                isLooping = false,
                keyframes = listOf(
                    Keyframe(0, bind.mapValues { (bone, t) ->
                        when (bone) {
                            BoneId.TORSO -> t.copy(ty = -18f, rotationDegrees = 0f)
                            BoneId.UPPER_ARM_L -> t.copy(rotationDegrees = -30f)
                            BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -30f)
                            BoneId.UPPER_LEG_L -> t.copy(rotationDegrees = 15f)
                            BoneId.UPPER_LEG_R -> t.copy(rotationDegrees = -15f)
                            else -> t.copy()
                        }
                    })
                )
            )

            // 6. FALL (Descent / Aerial, 15 frames)
            clipMap[AnimationId.FALL] = AnimationClip(
                id = AnimationId.FALL,
                name = "Fall_Air",
                totalFrames = 15,
                isLooping = true,
                keyframes = listOf(
                    Keyframe(0, bind.mapValues { (bone, t) ->
                        when (bone) {
                            BoneId.TORSO -> t.copy(ty = -17f, rotationDegrees = -4f)
                            BoneId.UPPER_ARM_L -> t.copy(rotationDegrees = -40f)
                            BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -40f)
                            BoneId.UPPER_LEG_L -> t.copy(rotationDegrees = -25f)
                            BoneId.LOWER_LEG_L -> t.copy(rotationDegrees = 35f)
                            BoneId.UPPER_LEG_R -> t.copy(rotationDegrees = 20f)
                            BoneId.LOWER_LEG_R -> t.copy(rotationDegrees = 40f)
                            else -> t.copy()
                        }
                    })
                )
            )

            // 7. WALL_CLING (Slide grip, 20 frames)
            clipMap[AnimationId.WALL_CLING] = AnimationClip(
                id = AnimationId.WALL_CLING,
                name = "Wall_Grip",
                totalFrames = 20,
                isLooping = true,
                keyframes = listOf(
                    Keyframe(0, bind.mapValues { (bone, t) ->
                        when (bone) {
                            BoneId.TORSO -> t.copy(rotationDegrees = -12f)
                            BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -90f)
                            BoneId.LOWER_ARM_R -> t.copy(rotationDegrees = -40f)
                            BoneId.UPPER_LEG_R -> t.copy(rotationDegrees = 45f)
                            BoneId.LOWER_LEG_R -> t.copy(rotationDegrees = 30f)
                            else -> t.copy()
                        }
                    })
                )
            )

            // 8. WALL_SLIDE
            clipMap[AnimationId.WALL_SLIDE] = AnimationClip(
                id = AnimationId.WALL_SLIDE,
                name = "Wall_Slide",
                totalFrames = 20,
                isLooping = true,
                keyframes = listOf(
                    Keyframe(0, bind.mapValues { (bone, t) ->
                        when (bone) {
                            BoneId.TORSO -> t.copy(rotationDegrees = -10f)
                            BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -85f)
                            BoneId.UPPER_LEG_R -> t.copy(rotationDegrees = 50f)
                            else -> t.copy()
                        }
                    })
                )
            )

            // 9. ATTACK_NEUTRAL (Rapid Jab / Slash: Frame 4 Hitbox ON, Frame 7 Hitbox OFF)
            val neutralAttackEvents = listOf(
                AnimationEvent(frame = 2, type = AnimationEventType.TRAIL_EFFECT_START, hexColor = "#FF4500"),
                AnimationEvent(
                    frame = 4,
                    type = AnimationEventType.HITBOX_ACTIVATE,
                    hitbox = HitboxDefinition(
                        relativeX = 28f,
                        relativeY = -12f,
                        width = 46f,
                        height = 36f,
                        damage = 12f,
                        baseKnockback = 180f,
                        knockbackScaling = 1.1f,
                        knockbackAngleDeg = 45f,
                        hitStunFrames = 14
                    )
                ),
                AnimationEvent(frame = 7, type = AnimationEventType.HITBOX_DEACTIVATE),
                AnimationEvent(frame = 9, type = AnimationEventType.TRAIL_EFFECT_END)
            )
            val neutralAttackKeyframes = listOf(
                Keyframe(0, bind.mapValues { (bone, t) ->
                    when (bone) {
                        BoneId.TORSO -> t.copy(rotationDegrees = -10f)
                        BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = 70f)
                        BoneId.LOWER_ARM_R -> t.copy(rotationDegrees = 60f)
                        BoneId.WEAPON_R -> t.copy(rotationDegrees = 30f)
                        else -> t.copy()
                    }
                }),
                Keyframe(4, bind.mapValues { (bone, t) ->
                    when (bone) {
                        BoneId.TORSO -> t.copy(rotationDegrees = 15f)
                        BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -80f)
                        BoneId.LOWER_ARM_R -> t.copy(rotationDegrees = -20f)
                        BoneId.WEAPON_R -> t.copy(rotationDegrees = -70f)
                        else -> t.copy()
                    }
                }),
                Keyframe(12, bind.mapValues { (bone, t) ->
                    when (bone) {
                        BoneId.TORSO -> t.copy(rotationDegrees = 0f)
                        BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -15f)
                        else -> t.copy()
                    }
                })
            )
            clipMap[AnimationId.ATTACK_NEUTRAL] = AnimationClip(
                id = AnimationId.ATTACK_NEUTRAL,
                name = "Attack_Neutral_Jab",
                totalFrames = 14,
                isLooping = false,
                keyframes = neutralAttackKeyframes,
                events = neutralAttackEvents
            )

            // 10. ATTACK_SIDE (Heavy Forward Thrust: Frame 5 Hitbox ON, Frame 9 Hitbox OFF)
            val sideAttackEvents = listOf(
                AnimationEvent(frame = 3, type = AnimationEventType.TRAIL_EFFECT_START, hexColor = "#FFD700"),
                AnimationEvent(
                    frame = 5,
                    type = AnimationEventType.HITBOX_ACTIVATE,
                    hitbox = HitboxDefinition(
                        relativeX = 36f,
                        relativeY = -8f,
                        width = 54f,
                        height = 32f,
                        damage = 18f,
                        baseKnockback = 260f,
                        knockbackScaling = 1.4f,
                        knockbackAngleDeg = 30f,
                        hitStunFrames = 18
                    )
                ),
                AnimationEvent(frame = 9, type = AnimationEventType.HITBOX_DEACTIVATE),
                AnimationEvent(frame = 12, type = AnimationEventType.TRAIL_EFFECT_END)
            )
            clipMap[AnimationId.ATTACK_SIDE] = AnimationClip(
                id = AnimationId.ATTACK_SIDE,
                name = "Attack_Side_Thrust",
                totalFrames = 18,
                isLooping = false,
                keyframes = listOf(
                    Keyframe(0, bind.mapValues { (bone, t) ->
                        when (bone) {
                            BoneId.TORSO -> t.copy(rotationDegrees = -18f, tx = -8f)
                            BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = 90f)
                            else -> t.copy()
                        }
                    }),
                    Keyframe(5, bind.mapValues { (bone, t) ->
                        when (bone) {
                            BoneId.TORSO -> t.copy(rotationDegrees = 20f, tx = 14f)
                            BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -95f)
                            BoneId.WEAPON_R -> t.copy(rotationDegrees = -90f)
                            else -> t.copy()
                        }
                    }),
                    Keyframe(18, bind.mapValues { (bone, t) -> t.copy() })
                ),
                events = sideAttackEvents
            )

            // 11. ATTACK_DOWN (Downward Spike / Dive: Frame 6 Hitbox ON, Frame 10 Hitbox OFF)
            val downAttackEvents = listOf(
                AnimationEvent(frame = 4, type = AnimationEventType.TRAIL_EFFECT_START, hexColor = "#FF1744"),
                AnimationEvent(
                    frame = 6,
                    type = AnimationEventType.HITBOX_ACTIVATE,
                    hitbox = HitboxDefinition(
                        relativeX = 10f,
                        relativeY = 28f,
                        width = 44f,
                        height = 48f,
                        damage = 22f,
                        baseKnockback = 310f,
                        knockbackScaling = 1.6f,
                        knockbackAngleDeg = 280f,
                        hitStunFrames = 22
                    )
                ),
                AnimationEvent(frame = 10, type = AnimationEventType.HITBOX_DEACTIVATE),
                AnimationEvent(frame = 14, type = AnimationEventType.TRAIL_EFFECT_END)
            )
            clipMap[AnimationId.ATTACK_DOWN] = AnimationClip(
                id = AnimationId.ATTACK_DOWN,
                name = "Attack_Down_Spike",
                totalFrames = 20,
                isLooping = false,
                keyframes = listOf(
                    Keyframe(0, bind.mapValues { (bone, t) ->
                        when (bone) {
                            BoneId.TORSO -> t.copy(rotationDegrees = 30f)
                            BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -120f)
                            else -> t.copy()
                        }
                    }),
                    Keyframe(6, bind.mapValues { (bone, t) ->
                        when (bone) {
                            BoneId.TORSO -> t.copy(rotationDegrees = -45f)
                            BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = 130f)
                            BoneId.WEAPON_R -> t.copy(rotationDegrees = 110f)
                            else -> t.copy()
                        }
                    }),
                    Keyframe(20, bind.mapValues { (bone, t) -> t.copy() })
                ),
                events = downAttackEvents
            )

            // 12. HITSTUN
            clipMap[AnimationId.HITSTUN] = AnimationClip(
                id = AnimationId.HITSTUN,
                name = "HitStun_Flinch",
                totalFrames = 12,
                isLooping = false,
                keyframes = listOf(
                    Keyframe(0, bind.mapValues { (bone, t) ->
                        when (bone) {
                            BoneId.TORSO -> t.copy(rotationDegrees = -24f, tx = -6f)
                            BoneId.HEAD -> t.copy(rotationDegrees = 20f)
                            BoneId.UPPER_ARM_L -> t.copy(rotationDegrees = -50f)
                            BoneId.UPPER_ARM_R -> t.copy(rotationDegrees = -50f)
                            else -> t.copy()
                        }
                    }),
                    Keyframe(12, bind.mapValues { (bone, t) -> t.copy() })
                )
            )

            // 13. KNOCKBACK
            clipMap[AnimationId.KNOCKBACK] = AnimationClip(
                id = AnimationId.KNOCKBACK,
                name = "Knockback_Tumble",
                totalFrames = 24,
                isLooping = true,
                keyframes = listOf(
                    Keyframe(0, bind.mapValues { (bone, t) ->
                        when (bone) {
                            BoneId.TORSO -> t.copy(rotationDegrees = -45f)
                            BoneId.UPPER_LEG_L -> t.copy(rotationDegrees = -60f)
                            BoneId.UPPER_LEG_R -> t.copy(rotationDegrees = 40f)
                            else -> t.copy()
                        }
                    }),
                    Keyframe(24, bind.mapValues { (bone, t) ->
                        when (bone) {
                            BoneId.TORSO -> t.copy(rotationDegrees = 315f)
                            else -> t.copy()
                        }
                    })
                )
            )

            // 14. RESPAWN
            clipMap[AnimationId.RESPAWN] = AnimationClip(
                id = AnimationId.RESPAWN,
                name = "Respawn_Platform",
                totalFrames = 30,
                isLooping = true,
                keyframes = listOf(
                    Keyframe(0, bind.mapValues { (bone, t) ->
                        when (bone) {
                            BoneId.TORSO -> t.copy(ty = -18f)
                            else -> t.copy()
                        }
                    })
                )
            )

            return clipMap
        }
    }
}
