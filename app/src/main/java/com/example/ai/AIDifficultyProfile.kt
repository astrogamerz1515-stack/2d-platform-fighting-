package com.example.ai

/**
 * Competitive Difficulty Scales for Combat NPC AI.
 * Maps to natural reaction latencies, input noise, and tactical probabilities.
 */
enum class AIDifficultyLevel {
    EASY,
    MEDIUM,
    HARD,
    TOURNAMENT
}

/**
 * Tuning parameters defining human-like limitations and tactical behaviors.
 */
data class AIDifficultyConfig(
    val level: AIDifficultyLevel,

    // Simulated human reaction latency (frames at 60Hz: 6 frames = 100ms)
    val reactionDelayFrames: Int,

    // Analog stick coordinate noise/overshoot (0.0 = perfect, 0.2 = casual drift)
    val inputInaccuracyNoise: Float,

    // Probability of reacting to incoming hitboxes with a dodge (0.0 to 1.0)
    val dodgeProbability: Float,

    // Probability of chaining follow-up attacks upon hit confirm
    val comboContinuationProbability: Float,

    // Aggressiveness when opponent is recovering off-stage
    val edgeGuardAggressiveness: Float,

    // Probability of charging heavy signatures rather than instant release
    val signatureChargeProbability: Float,

    // Neutral micro-spacing positioning variance in pixels
    val microSpacingVariance: Float,

    // Attack commitment frequency in neutral (higher = more aggressive)
    val attackCommitmentRate: Float
) {
    companion object {
        val PROFILES: Map<AIDifficultyLevel, AIDifficultyConfig> = mapOf(
            AIDifficultyLevel.EASY to AIDifficultyConfig(
                level = AIDifficultyLevel.EASY,
                reactionDelayFrames = 18, // ~300ms reaction time (casual/beginner)
                inputInaccuracyNoise = 0.18f,
                dodgeProbability = 0.25f,
                comboContinuationProbability = 0.35f,
                edgeGuardAggressiveness = 0.20f,
                signatureChargeProbability = 0.40f,
                microSpacingVariance = 65f,
                attackCommitmentRate = 0.45f
            ),

            AIDifficultyLevel.MEDIUM to AIDifficultyConfig(
                level = AIDifficultyLevel.MEDIUM,
                reactionDelayFrames = 10, // ~166ms reaction time (average player)
                inputInaccuracyNoise = 0.08f,
                dodgeProbability = 0.55f,
                comboContinuationProbability = 0.65f,
                edgeGuardAggressiveness = 0.55f,
                signatureChargeProbability = 0.30f,
                microSpacingVariance = 35f,
                attackCommitmentRate = 0.65f
            ),

            AIDifficultyLevel.HARD to AIDifficultyConfig(
                level = AIDifficultyLevel.HARD,
                reactionDelayFrames = 5, // ~83ms reaction time (skilled competitive rank)
                inputInaccuracyNoise = 0.03f,
                dodgeProbability = 0.85f,
                comboContinuationProbability = 0.85f,
                edgeGuardAggressiveness = 0.82f,
                signatureChargeProbability = 0.25f,
                microSpacingVariance = 18f,
                attackCommitmentRate = 0.82f
            ),

            AIDifficultyLevel.TOURNAMENT to AIDifficultyConfig(
                level = AIDifficultyLevel.TOURNAMENT,
                reactionDelayFrames = 2, // ~33ms reaction time (esports frame-perfect pro)
                inputInaccuracyNoise = 0.005f,
                dodgeProbability = 0.98f,
                comboContinuationProbability = 0.98f,
                edgeGuardAggressiveness = 0.95f,
                signatureChargeProbability = 0.20f,
                microSpacingVariance = 8f,
                attackCommitmentRate = 0.95f
            )
        )

        fun getConfig(level: AIDifficultyLevel): AIDifficultyConfig {
            return PROFILES[level] ?: PROFILES[AIDifficultyLevel.MEDIUM]!!
        }
    }
}
