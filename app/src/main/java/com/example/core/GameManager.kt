package com.example.core

import android.content.Context
import com.example.controller.CharacterMovementConfig
import com.example.localization.LocalizationManager
import com.example.localization.StringKey
import com.example.localization.SupportedLanguage
import com.example.persistence.GameSaveData
import com.example.persistence.SecureSaveSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Competitive Game Modes.
 */
enum class GameMode(val displayNameKey: StringKey) {
    STOCK_BRAWL(StringKey.MODE_STOCK_BRAWL),
    TIME_ATTACK(StringKey.MODE_TIME_ATTACK),
    TRAINING(StringKey.MODE_TRAINING)
}

/**
 * Match progression states.
 */
enum class MatchState {
    NOT_STARTED,
    IN_PROGRESS,
    PAUSED,
    FINISHED
}

/**
 * Character specification with unique physics/combat attributes.
 */
data class CharacterProfile(
    val id: String,
    val nameKey: StringKey,
    val descriptionKey: StringKey,
    val speedRating: Int, // 1 - 5
    val powerRating: Int, // 1 - 5
    val defenseRating: Int, // 1 - 5
    val jumpRating: Int, // 1 - 5
    val movementConfig: CharacterMovementConfig,
    val primaryColorArgb: Long
)

/**
 * Stage specification with layout attributes.
 */
data class StageProfile(
    val id: String,
    val nameKey: StringKey,
    val stageWidth: Float,
    val stageHeight: Float,
    val blastZoneX: Float,
    val blastZoneY: Float,
    val themeColorArgb: Long
)

/**
 * Post-match statistical summary.
 */
data class MatchResult(
    val isVictory: Boolean,
    val matchDurationSeconds: Float,
    val playerStocksRemaining: Int,
    val totalDamageDealt: Float,
    val totalKOs: Int,
    val characterId: String,
    val stageId: String
)

/**
 * Thread-safe Central GameManager Singleton.
 * Serves as the master coordinator across game modes, match lifecycle, fighter rosters,
 * stages, persistent save data, and audio/haptic settings.
 */
class GameManager private constructor() {

    // Persistent storage engine
    private var saveSystem: SecureSaveSystem? = null
    var saveData: GameSaveData = GameSaveData()
        private set

    // Match State Flow
    private val _matchState = MutableStateFlow(MatchState.NOT_STARTED)
    val matchState: StateFlow<MatchState> = _matchState.asStateFlow()

    private val _activeGameMode = MutableStateFlow(GameMode.STOCK_BRAWL)
    val activeGameMode: StateFlow<GameMode> = _activeGameMode.asStateFlow()

    // Match Telemetry & Rules
    val defaultStocks: Int = 3
    private val _playerStocks = MutableStateFlow(defaultStocks)
    val playerStocks: StateFlow<Int> = _playerStocks.asStateFlow()

    private val _playerDamagePercent = MutableStateFlow(0f)
    val playerDamagePercent: StateFlow<Float> = _playerDamagePercent.asStateFlow()

    private val _matchTimeRemainingSeconds = MutableStateFlow(120f)
    val matchTimeRemainingSeconds: StateFlow<Float> = _matchTimeRemainingSeconds.asStateFlow()

    private val _matchDamageDealt = MutableStateFlow(0f)
    val matchDamageDealt: StateFlow<Float> = _matchDamageDealt.asStateFlow()

    private val _matchKOCount = MutableStateFlow(0)
    val matchKOCount: StateFlow<Int> = _matchKOCount.asStateFlow()

    var lastMatchResult: MatchResult? = null
        private set

    // Character Roster Definition
    val characterRoster: List<CharacterProfile> = listOf(
        CharacterProfile(
            id = "valkyrie",
            nameKey = StringKey.CHAR_VALKYRIE,
            descriptionKey = StringKey.CHAR_VALKYRIE_DESC,
            speedRating = 4,
            powerRating = 3,
            defenseRating = 3,
            jumpRating = 4,
            movementConfig = CharacterMovementConfig(
                maxGroundSpeed = 530f,
                maxAirSpeed = 490f,
                jumpImpulse = -770f,
                dashSpeed = 890f
            ),
            primaryColorArgb = 0xFF0EA5E9 // Cyan/Valkyrie Blue
        ),
        CharacterProfile(
            id = "cyber_monk",
            nameKey = StringKey.CHAR_CYBER_MONK,
            descriptionKey = StringKey.CHAR_CYBER_MONK_DESC,
            speedRating = 2,
            powerRating = 5,
            defenseRating = 5,
            jumpRating = 2,
            movementConfig = CharacterMovementConfig(
                maxGroundSpeed = 440f,
                maxAirSpeed = 400f,
                gravity = 2200f,
                jumpImpulse = -730f,
                dashSpeed = 820f
            ),
            primaryColorArgb = 0xFFF59E0B // Amber/Gold Monk
        ),
        CharacterProfile(
            id = "shadow_ninja",
            nameKey = StringKey.CHAR_SHADOW_NINJA,
            descriptionKey = StringKey.CHAR_SHADOW_NINJA_DESC,
            speedRating = 5,
            powerRating = 3,
            defenseRating = 2,
            jumpRating = 5,
            movementConfig = CharacterMovementConfig(
                maxGroundSpeed = 580f,
                maxAirSpeed = 530f,
                jumpImpulse = -800f,
                airJumpImpulse = -730f,
                dashSpeed = 960f,
                dashDurationFrames = 12
            ),
            primaryColorArgb = 0xFF8B5CF6 // Shadow Purple
        )
    )

    // Stage Catalog Definition
    val stageCatalog: List<StageProfile> = listOf(
        StageProfile(
            id = "mammoth_fortress",
            nameKey = StringKey.STAGE_MAMMOTH_FORTRESS,
            stageWidth = 1000f,
            stageHeight = 600f,
            blastZoneX = 250f,
            blastZoneY = 250f,
            themeColorArgb = 0xFF0F172A
        ),
        StageProfile(
            id = "crystal_spire",
            nameKey = StringKey.STAGE_CRYSTAL_SPIRE,
            stageWidth = 900f,
            stageHeight = 550f,
            blastZoneX = 220f,
            blastZoneY = 220f,
            themeColorArgb = 0xFF1E1B4B
        ),
        StageProfile(
            id = "thunder_plateau",
            nameKey = StringKey.STAGE_THUNDER_PLATEAU,
            stageWidth = 1100f,
            stageHeight = 650f,
            blastZoneX = 280f,
            blastZoneY = 280f,
            themeColorArgb = 0xFF172554
        )
    )

    private val _selectedCharacter = MutableStateFlow(characterRoster[0])
    val selectedCharacter: StateFlow<CharacterProfile> = _selectedCharacter.asStateFlow()

    private val _selectedStage = MutableStateFlow(stageCatalog[0])
    val selectedStage: StateFlow<StageProfile> = _selectedStage.asStateFlow()

    /**
     * Initializes persistence with Android application context.
     */
    fun initialize(context: Context) {
        if (saveSystem == null) {
            saveSystem = SecureSaveSystem(context)
            loadPersistentData()
        }
    }

    fun loadPersistentData() {
        saveData = saveSystem?.load() ?: GameSaveData()

        // Sync selected character
        val char = characterRoster.find { it.id == saveData.selectedCharacterId }
        if (char != null) {
            _selectedCharacter.value = char
        }

        // Sync selected stage
        val stage = stageCatalog.find { it.id == saveData.selectedStageId }
        if (stage != null) {
            _selectedStage.value = stage
        }

        // Apply saved language
        val supported = SupportedLanguage.values().find { it.code == saveData.languageCode }
        if (supported != null) {
            LocalizationManager.instance.setLanguage(supported)
        }
    }

    fun selectCharacter(charId: String) {
        val found = characterRoster.find { it.id == charId }
        if (found != null) {
            _selectedCharacter.value = found
            saveData.selectedCharacterId = charId
            saveSystem?.save(saveData)
        }
    }

    fun selectStage(stageId: String) {
        val found = stageCatalog.find { it.id == stageId }
        if (found != null) {
            _selectedStage.value = found
            saveData.selectedStageId = stageId
            saveSystem?.save(saveData)
        }
    }

    fun setGameMode(mode: GameMode) {
        _activeGameMode.value = mode
    }

    /**
     * Starts or resets match lifecycle.
     */
    fun startMatch() {
        _matchState.value = MatchState.IN_PROGRESS
        _playerStocks.value = if (_activeGameMode.value == GameMode.STOCK_BRAWL) defaultStocks else 1
        _playerDamagePercent.value = 0f
        _matchTimeRemainingSeconds.value = if (_activeGameMode.value == GameMode.TIME_ATTACK) 120f else 300f
        _matchDamageDealt.value = 0f
        _matchKOCount.value = 0
    }

    fun pauseMatch() {
        if (_matchState.value == MatchState.IN_PROGRESS) {
            _matchState.value = MatchState.PAUSED
        }
    }

    fun resumeMatch() {
        if (_matchState.value == MatchState.PAUSED) {
            _matchState.value = MatchState.IN_PROGRESS
        }
    }

    /**
     * Records a KO event (e.g. falling into blast zone or blasting an opponent).
     */
    fun recordPlayerKO() {
        if (_matchState.value != MatchState.IN_PROGRESS) return

        if (_activeGameMode.value == GameMode.STOCK_BRAWL) {
            _playerStocks.value = (_playerStocks.value - 1).coerceAtLeast(0)
            _playerDamagePercent.value = 0f

            if (_playerStocks.value <= 0) {
                concludeMatch(isVictory = false)
            }
        } else {
            // Training or time attack
            _playerDamagePercent.value = 0f
        }
    }

    fun addDamageDealt(damage: Float) {
        _matchDamageDealt.value += damage
        if (_matchDamageDealt.value > saveData.highestDamageDealt) {
            saveData.highestDamageDealt = _matchDamageDealt.value
        }
    }

    fun recordOpponentKO() {
        _matchKOCount.value += 1
        saveData.totalKOs += 1
        if (_matchKOCount.value >= 3 && _activeGameMode.value == GameMode.STOCK_BRAWL) {
            concludeMatch(isVictory = true)
        }
    }

    fun updateMatchTimer(dt: Float) {
        if (_matchState.value != MatchState.IN_PROGRESS) return

        if (_activeGameMode.value == GameMode.TIME_ATTACK) {
            _matchTimeRemainingSeconds.value = (_matchTimeRemainingSeconds.value - dt).coerceAtLeast(0f)
            if (_matchTimeRemainingSeconds.value <= 0f) {
                val won = _matchKOCount.value > 0
                concludeMatch(isVictory = won)
            }
        }
    }

    /**
     * Finalizes match, records stats, and triggers persistent save.
     */
    fun concludeMatch(isVictory: Boolean) {
        _matchState.value = MatchState.FINISHED

        saveData.totalMatchesPlayed += 1
        if (isVictory) {
            saveData.totalWins += 1
            saveData.currentWinStreak += 1
            if (saveData.currentWinStreak > saveData.bestWinStreak) {
                saveData.bestWinStreak = saveData.currentWinStreak
            }
        } else {
            saveData.totalLosses += 1
            saveData.currentWinStreak = 0
        }

        saveSystem?.save(saveData)

        lastMatchResult = MatchResult(
            isVictory = isVictory,
            matchDurationSeconds = 120f - _matchTimeRemainingSeconds.value,
            playerStocksRemaining = _playerStocks.value,
            totalDamageDealt = _matchDamageDealt.value,
            totalKOs = _matchKOCount.value,
            characterId = _selectedCharacter.value.id,
            stageId = _selectedStage.value.id
        )

        // Route to PostMatch
        SceneManager.instance.transitionTo(SceneType.POST_MATCH, 300L)
    }

    fun resetPersistentData() {
        saveData = saveSystem?.resetSave() ?: GameSaveData()
        loadPersistentData()
    }

    companion object {
        val instance: GameManager by lazy { GameManager() }
    }
}
