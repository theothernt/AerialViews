package com.codingbuffalo.aerialdream.data.protium

import java.util.concurrent.Callable

abstract class Task<T> : Callable<T> {
    fun onError(e: Exception) {
        e.printStackTrace()
    }
}