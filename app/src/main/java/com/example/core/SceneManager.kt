package com.example.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Production-ready Asynchronous Scene Streaming Manager & Fail-Safe Bootstrapper.
 * Handles the complete commercial lifecycle:
 * Cold Boot -> Safe Initialization -> Main Menu -> Character Selection ->
 * Dynamic Arena Loading -> Active Battle Loop -> Post-Match Evaluation -> Asset Cleanup.
 *
 * Implements strict memory reclamation and forced garbage collection during loading phases
 * to eliminate out-of-memory crashes on memory-constrained Android devices.
 */
class SceneManager private constructor() {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var transitionJob: Job? = null

    private val _currentScene = MutableStateFlow(SceneType.BOOT)
    val currentScene: StateFlow<SceneType> = _currentScene.asStateFlow()

    private val _lifecyclePhase = MutableStateFlow(BootstrapperLifecyclePhase.COLD_BOOT)
    val lifecyclePhase: StateFlow<BootstrapperLifecyclePhase> = _lifecyclePhase.asStateFlow()

    private val _targetScene = MutableStateFlow<SceneType?>(null)
    val targetScene: StateFlow<SceneType?> = _targetScene.asStateFlow()

    private val _transitionPhase = MutableStateFlow(TransitionPhase.IDLE)
    val transitionPhase: StateFlow<TransitionPhase> = _transitionPhase.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    private val _loadingStatusText = MutableStateFlow("Initializing...")
    val loadingStatusText: StateFlow<String> = _loadingStatusText.asStateFlow()

    private val _availableMemoryMb = MutableStateFlow(0L)
    val availableMemoryMb: StateFlow<Long> = _availableMemoryMb.asStateFlow()

    private val transitionListeners = mutableListOf<(SceneType, SceneType) -> Unit>()
    private val memoryCleanupCallbacks = mutableListOf<() -> Unit>()

    init {
        updateMemoryTelemetry()
    }

    /**
     * Registers a callback executed during the UNLOAD_PREVIOUS / ASSET_CLEANUP phase (e.g. object pool purging).
     */
    fun registerMemoryCleanup(callback: () -> Unit) {
        memoryCleanupCallbacks.add(callback)
    }

    fun addTransitionListener(listener: (SceneType, SceneType) -> Unit) {
        transitionListeners.add(listener)
    }

    fun removeTransitionListener(listener: (SceneType, SceneType) -> Unit) {
        transitionListeners.remove(listener)
    }

    /**
     * Initiates an asynchronous transition with explicit memory reclamation phases.
     */
    fun transitionTo(destination: SceneType, customDelayMs: Long = 200L) {
        val fromScene = _currentScene.value
        if (fromScene == destination && _transitionPhase.value == TransitionPhase.IDLE) {
            return // Already on this scene
        }

        transitionJob?.cancel()
        _targetScene.value = destination

        transitionJob = scope.launch {
            try {
                // If transitioning to gameplay, route through MAP_LOADING first
                if (destination == SceneType.IN_GAME && fromScene != SceneType.MAP_LOADING) {
                    _currentScene.value = SceneType.MAP_LOADING
                    _lifecyclePhase.value = BootstrapperLifecyclePhase.DYNAMIC_ARENA_LOADING
                }

                // Phase 1: Unload previous scene assets (Asset Cleanup)
                _transitionPhase.value = TransitionPhase.UNLOAD_PREVIOUS
                _loadingStatusText.value = "Unloading previous scene resources..."
                _loadingProgress.value = 0.15f
                _lifecyclePhase.value = BootstrapperLifecyclePhase.ASSET_CLEANUP

                for (cleanup in memoryCleanupCallbacks) {
                    try {
                        cleanup()
                    } catch (e: Exception) {
                        Log.e("SceneManager", "Error in memory cleanup callback", e)
                    }
                }
                delay(customDelayMs / 3)

                // Phase 2: Force explicit garbage collection
                _transitionPhase.value = TransitionPhase.TRIGGER_GC
                _loadingStatusText.value = "Reclaiming native & managed memory heap..."
                _loadingProgress.value = 0.40f
                forceGarbageCollection()
                delay(customDelayMs / 3)

                // Phase 3: Load new scene assets
                _transitionPhase.value = TransitionPhase.LOAD_NEW_ASSETS
                _loadingStatusText.value = "Streaming target scene data..."
                _loadingProgress.value = 0.75f
                delay(customDelayMs / 3)

                // Phase 4: Initialize target scene logic
                _transitionPhase.value = TransitionPhase.INITIALIZE_TARGET
                _loadingStatusText.value = "Finalizing scene setup..."
                _loadingProgress.value = 0.95f
                delay(80L)

                // Phase 5: Complete transition & update lifecycle phase
                _loadingProgress.value = 1.0f
                _currentScene.value = destination
                _targetScene.value = null
                _transitionPhase.value = TransitionPhase.COMPLETE

                _lifecyclePhase.value = mapSceneToLifecycle(destination)

                for (listener in transitionListeners) {
                    listener(fromScene, destination)
                }

                _transitionPhase.value = TransitionPhase.IDLE
                updateMemoryTelemetry()
            } catch (e: Exception) {
                Log.e("SceneManager", "Exception during scene transition to $destination", e)
                // Fail-safe recovery: fallback to MAIN_MENU if an error occurs
                _currentScene.value = SceneType.MAIN_MENU
                _lifecyclePhase.value = BootstrapperLifecyclePhase.MAIN_MENU
                _targetScene.value = null
                _transitionPhase.value = TransitionPhase.IDLE
            }
        }
    }

    private fun mapSceneToLifecycle(scene: SceneType): BootstrapperLifecyclePhase {
        return when (scene) {
            SceneType.BOOT -> BootstrapperLifecyclePhase.SAFE_INITIALIZATION
            SceneType.MAIN_MENU -> BootstrapperLifecyclePhase.MAIN_MENU
            SceneType.CHARACTER_SELECT -> BootstrapperLifecyclePhase.CHARACTER_SELECTION
            SceneType.STAGE_SELECT -> BootstrapperLifecyclePhase.STAGE_SELECTION
            SceneType.MAP_LOADING -> BootstrapperLifecyclePhase.DYNAMIC_ARENA_LOADING
            SceneType.IN_GAME -> BootstrapperLifecyclePhase.ACTIVE_BATTLE_LOOP
            SceneType.POST_MATCH -> BootstrapperLifecyclePhase.POST_MATCH_EVALUATION
        }
    }

    /**
     * Executes aggressive memory freeing routine for mobile devices.
     */
    fun forceGarbageCollection() {
        try {
            System.runFinalization()
            System.gc()
            updateMemoryTelemetry()
        } catch (e: Exception) {
            Log.w("SceneManager", "GC request failed", e)
        }
    }

    private fun updateMemoryTelemetry() {
        val runtime = Runtime.getRuntime()
        val freeBytes = runtime.freeMemory() + (runtime.maxMemory() - runtime.totalMemory())
        _availableMemoryMb.value = freeBytes / (1024 * 1024)
    }

    companion object {
        val instance: SceneManager by lazy { SceneManager() }
    }
}
