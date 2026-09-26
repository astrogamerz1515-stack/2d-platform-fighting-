package com.example.core

/**
 * Mutually exclusive scene destinations in the game lifecycle.
 */
enum class SceneType {
    BOOT,
    MAIN_MENU,
    CHARACTER_SELECT,
    STAGE_SELECT,
    MAP_LOADING,
    IN_GAME,
    POST_MATCH
}

/**
 * Individual stages during an asynchronous scene transition.
 */
enum class TransitionPhase {
    IDLE,
    UNLOAD_PREVIOUS,
    TRIGGER_GC,
    LOAD_NEW_ASSETS,
    INITIALIZE_TARGET,
    COMPLETE
}

/**
 * Strict Commercial-Grade Lifecycle Pipeline states:
 * Cold Boot -> Safe Initialization -> Main Menu -> Character Selection ->
 * Dynamic Arena Loading -> Active Battle Loop -> Post-Match Evaluation -> Asset Cleanup.
 */
enum class BootstrapperLifecyclePhase {
    COLD_BOOT,
    SAFE_INITIALIZATION,
    MAIN_MENU,
    CHARACTER_SELECTION,
    STAGE_SELECTION,
    DYNAMIC_ARENA_LOADING,
    ACTIVE_BATTLE_LOOP,
    POST_MATCH_EVALUATION,
    ASSET_CLEANUP
}
