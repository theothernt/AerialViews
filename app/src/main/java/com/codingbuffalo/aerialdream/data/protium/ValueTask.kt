package com.codingbuffalo.aerialdream.data.protium

abstract class ValueTask<T> : Task<T>() {
    override fun call(): T? {
        try {
            val data = onExecute()
            if (!Thread.currentThread().isInterrupted) {
                onComplete(data)
                return data
            }
        } catch (e: Exception) {
            onError(e)
        }
        return null
    }

    @Throws(Exception::class)
    abstract fun onExecute(): T
    open fun onComplete(data: T) {}
}