package com.example.engine.math

import kotlin.math.sqrt

/**
 * High-performance 2D Vector designed for zero-allocation game loops on Android.
 * Supports both mutable in-place operations and immutable chaining to eliminate GC churn.
 */
data class Vector2(
    var x: Float = 0f,
    var y: Float = 0f
) {
    fun set(newX: Float, newY: Float): Vector2 {
        this.x = newX
        this.y = newY
        return this
    }

    fun set(other: Vector2): Vector2 {
        this.x = other.x
        this.y = other.y
        return this
    }

    fun add(dx: Float, dy: Float): Vector2 {
        this.x += dx
        this.y += dy
        return this
    }

    fun add(other: Vector2): Vector2 {
        this.x += other.x
        this.y += other.y
        return this
    }

    fun subtract(other: Vector2): Vector2 {
        this.x -= other.x
        this.y -= other.y
        return this
    }

    fun scale(factor: Float): Vector2 {
        this.x *= factor
        this.y *= factor
        return this
    }

    fun length(): Float = sqrt(x * x + y * y)

    fun lengthSquared(): Float = x * x + y * y

    fun normalize(): Vector2 {
        val len = length()
        if (len > 0.00001f) {
            x /= len
            y /= len
        } else {
            x = 0f
            y = 0f
        }
        return this
    }

    fun dot(other: Vector2): Float = x * other.x + y * other.y

    fun copyFrom(other: Vector2): Vector2 {
        this.x = other.x
        this.y = other.y
        return this
    }

    fun reset(): Vector2 {
        this.x = 0f
        this.y = 0f
        return this
    }
}
