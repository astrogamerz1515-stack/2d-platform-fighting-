package com.example.engine.pool

/**
 * High-performance Object Pool designed to eliminate Garbage Collection allocations
 * in Android 60Hz/120Hz physics loops.
 *
 * Pre-allocates instances and reuses them deterministically.
 */
class ObjectPool<T>(
    private val initialCapacity: Int,
    private val factory: () -> T,
    private val resetAction: (T) -> Unit = {}
) {
    @Suppress("UNCHECKED_CAST")
    private val pool: Array<Any?> = arrayOfNulls(initialCapacity)
    private var head: Int = 0

    init {
        for (i in 0 until initialCapacity) {
            pool[i] = factory()
        }
        head = initialCapacity
    }

    /**
     * Obtains an object from the pool. If empty, lazily instantiates a fallback.
     */
    @Suppress("UNCHECKED_CAST")
    fun obtain(): T {
        return if (head > 0) {
            head--
            val item = pool[head] as T
            pool[head] = null
            item
        } else {
            factory()
        }
    }

    /**
     * Recycles an object back into the pool.
     */
    fun recycle(instance: T) {
        resetAction(instance)
        if (head < pool.size) {
            pool[head] = instance
            head++
        }
    }

    /**
     * Executes a lambda with a pooled object, recycling it immediately upon completion.
     */
    inline fun <R> use(block: (T) -> R): R {
        val item = obtain()
        return try {
            block(item)
        } finally {
            recycle(item)
        }
    }
}
