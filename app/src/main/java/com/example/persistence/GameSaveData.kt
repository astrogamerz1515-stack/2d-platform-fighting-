package com.example.persistence

/**
 * Clean, zero-dependency data model representing all persistent player statistics, progression, and settings.
 * Serializes and parses JSON in pure Kotlin to avoid Android SDK runtime mocking issues in JVM unit tests.
 */
data class GameSaveData(
    // Player Progression & Battle Records
    var totalMatchesPlayed: Int = 0,
    var totalWins: Int = 0,
    var totalLosses: Int = 0,
    var totalKOs: Int = 0,
    var highestDamageDealt: Float = 0f,
    var currentWinStreak: Int = 0,
    var bestWinStreak: Int = 0,

    // Unlocks & Selections
    var selectedCharacterId: String = "valkyrie",
    var selectedStageId: String = "mammoth_fortress",
    val unlockedCharacterIds: MutableSet<String> = mutableSetOf("valkyrie", "cyber_monk", "shadow_ninja"),

    // User Preferences & Settings
    var masterVolume: Float = 1.0f,
    var sfxVolume: Float = 0.85f,
    var musicVolume: Float = 0.70f,
    var vibrationEnabled: Boolean = true,
    var languageCode: String = "en",

    // Tamper Prevention Metadata
    var saveVersion: Int = 1,
    var lastSavedTimestamp: Long = 0L,
    var integrityHash: String = ""
) {

    /**
     * Serializes this data structure into a standardized, deterministic JSON string.
     */
    fun toJsonString(): String {
        val unlocksJson = unlockedCharacterIds.joinToString(separator = ", ", prefix = "[", postfix = "]") { "\"$it\"" }
        return buildString {
            append("{\n")
            append("  \"saveVersion\": $saveVersion,\n")
            append("  \"lastSavedTimestamp\": $lastSavedTimestamp,\n")
            append("  \"totalMatchesPlayed\": $totalMatchesPlayed,\n")
            append("  \"totalWins\": $totalWins,\n")
            append("  \"totalLosses\": $totalLosses,\n")
            append("  \"totalKOs\": $totalKOs,\n")
            append("  \"highestDamageDealt\": $highestDamageDealt,\n")
            append("  \"currentWinStreak\": $currentWinStreak,\n")
            append("  \"bestWinStreak\": $bestWinStreak,\n")
            append("  \"selectedCharacterId\": \"$selectedCharacterId\",\n")
            append("  \"selectedStageId\": \"$selectedStageId\",\n")
            append("  \"unlockedCharacterIds\": $unlocksJson,\n")
            append("  \"masterVolume\": $masterVolume,\n")
            append("  \"sfxVolume\": $sfxVolume,\n")
            append("  \"musicVolume\": $musicVolume,\n")
            append("  \"vibrationEnabled\": $vibrationEnabled,\n")
            append("  \"languageCode\": \"$languageCode\",\n")
            append("  \"integrityHash\": \"$integrityHash\"\n")
            append("}")
        }
    }

    companion object {
        /**
         * Reconstructs a GameSaveData instance from a JSON string using pure Kotlin extraction.
         */
        fun fromJsonString(json: String): GameSaveData {
            val data = GameSaveData()

            data.saveVersion = extractInt(json, "saveVersion", 1)
            data.lastSavedTimestamp = extractLong(json, "lastSavedTimestamp", 0L)
            data.totalMatchesPlayed = extractInt(json, "totalMatchesPlayed", 0)
            data.totalWins = extractInt(json, "totalWins", 0)
            data.totalLosses = extractInt(json, "totalLosses", 0)
            data.totalKOs = extractInt(json, "totalKOs", 0)
            data.highestDamageDealt = extractFloat(json, "highestDamageDealt", 0f)
            data.currentWinStreak = extractInt(json, "currentWinStreak", 0)
            data.bestWinStreak = extractInt(json, "bestWinStreak", 0)

            data.selectedCharacterId = extractString(json, "selectedCharacterId", "valkyrie")
            data.selectedStageId = extractString(json, "selectedStageId", "mammoth_fortress")

            val unlocks = extractStringList(json, "unlockedCharacterIds")
            data.unlockedCharacterIds.clear()
            if (unlocks.isNotEmpty()) {
                data.unlockedCharacterIds.addAll(unlocks)
            } else {
                data.unlockedCharacterIds.addAll(listOf("valkyrie", "cyber_monk", "shadow_ninja"))
            }

            data.masterVolume = extractFloat(json, "masterVolume", 1.0f)
            data.sfxVolume = extractFloat(json, "sfxVolume", 0.85f)
            data.musicVolume = extractFloat(json, "musicVolume", 0.70f)
            data.vibrationEnabled = extractBoolean(json, "vibrationEnabled", true)
            data.languageCode = extractString(json, "languageCode", "en")
            data.integrityHash = extractString(json, "integrityHash", "")

            return data
        }

        private fun extractString(json: String, key: String, default: String): String {
            val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            return regex.find(json)?.groupValues?.get(1) ?: default
        }

        private fun extractInt(json: String, key: String, default: Int): Int {
            val regex = "\"$key\"\\s*:\\s*(-?\\d+)".toRegex()
            return regex.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: default
        }

        private fun extractLong(json: String, key: String, default: Long): Long {
            val regex = "\"$key\"\\s*:\\s*(-?\\d+)".toRegex()
            return regex.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: default
        }

        private fun extractFloat(json: String, key: String, default: Float): Float {
            val regex = "\"$key\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)".toRegex()
            return regex.find(json)?.groupValues?.get(1)?.toFloatOrNull() ?: default
        }

        private fun extractBoolean(json: String, key: String, default: Boolean): Boolean {
            val regex = "\"$key\"\\s*:\\s*(true|false)".toRegex()
            return regex.find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: default
        }

        private fun extractStringList(json: String, key: String): List<String> {
            val regex = "\"$key\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex()
            val match = regex.find(json) ?: return emptyList()
            val arrayContent = match.groupValues[1]
            val itemRegex = "\"([^\"]*)\"".toRegex()
            return itemRegex.findAll(arrayContent).map { it.groupValues[1] }.toList()
        }
    }
}
