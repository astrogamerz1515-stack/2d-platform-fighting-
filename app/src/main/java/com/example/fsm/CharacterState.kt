package com.example.fsm

/**
 * Mutually exclusive states for the 2D platform fighter character controller.
 */
enum class CharacterStateType {
    IDLE,
    RUN,
    JUMP,
    FALL,
    DASH,
    WALL_CLING,
    WALL_SLIDE,
    HURT,
    KNOCKBACK
}

/**
 * Base contract for a character state in the Finite State Machine.
 */
interface ICharacterState {
    val type: CharacterStateType

    /**
     * Called strictly once upon entering the state.
     * Guaranteed to run after the previous state's onExit.
     */
    fun onEnter(previousState: CharacterStateType)

    /**
     * Deterministic fixed physics tick update (60Hz).
     * Returns a new state type if a state transition should occur, or null to remain in this state.
     */
    fun onFixedUpdate(dt: Float): CharacterStateType?

    /**
     * Called strictly once upon exiting the state.
     */
    fun onExit(nextState: CharacterStateType)
}
