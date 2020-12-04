package com.codingbuffalo.aerialdream.data.protium

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

abstract class Interactor(private val mService: ExecutorService) {
    protected fun <T> execute(task: Task<T>): Future<T> {
        return mService.submit(task)
    }
}