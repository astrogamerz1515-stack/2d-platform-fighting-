package com.example.fsm

/**
 * Robust, deterministic Finite State Machine (FSM) for character control.
 * Enforces mutual exclusivity and state-entry / state-exit safety checks.
 */
class StateMachine(
    private val stateRegistry: Map<CharacterStateType, ICharacterState>,
    initialStateType: CharacterStateType
) {
    var currentState: ICharacterState = stateRegistry[initialStateType]
        ?: throw IllegalArgumentException("Initial state $initialStateType not registered")
        private set

    var previousStateType: CharacterStateType = initialStateType
        private set

    var stateFrameCount: Int = 0
        private set

    var isTransitioning: Boolean = false
        private set

    init {
        currentState.onEnter(initialStateType)
    }

    /**
     * Executes fixed update on the active state and handles transitions.
     */
    fun tick(dt: Float) {
        val nextStateType = currentState.onFixedUpdate(dt)
        if (nextStateType != null && nextStateType != currentState.type) {
            changeState(nextStateType)
        } else {
            stateFrameCount++
        }
    }

    /**
     * Safely transitions to a new state with strict exit/enter lifecycle guarantees.
     * Prevents state blending and recursive re-entrancy.
     */
    fun changeState(newStateType: CharacterStateType): Boolean {
        if (isTransitioning) {
            // Defensive check against re-entrant calls during onEnter/onExit
            return false
        }

        val nextState = stateRegistry[newStateType]
        if (nextState == null) {
            return false
        }

        // Validate legal transition guards
        if (!canTransition(currentState.type, newStateType)) {
            return false
        }

        isTransitioning = true
        val oldState = currentState
        previousStateType = oldState.type

        try {
            oldState.onExit(newStateType)
            currentState = nextState
            stateFrameCount = 0
            currentState.onEnter(oldState.type)
        } finally {
            isTransitioning = false
        }

        return true
    }

    /**
     * Guard rules defining valid state transitions to eliminate illegal state blends.
     */
    private fun canTransition(from: CharacterStateType, to: CharacterStateType): Boolean {
        // Cannot dash or run while in hitstun / knockback
        if ((from == CharacterStateType.HURT || from == CharacterStateType.KNOCKBACK) &&
            to != CharacterStateType.FALL && to != CharacterStateType.IDLE && to != CharacterStateType.HURT && to != CharacterStateType.KNOCKBACK
        ) {
            return false
        }

        // Cannot wall slide or cling while grounded
        if ((to == CharacterStateType.WALL_SLIDE || to == CharacterStateType.WALL_CLING) &&
            from == CharacterStateType.IDLE
        ) {
            return false
        }

        return true
    }

    fun getCurrentStateType(): CharacterStateType = currentState.type
}
