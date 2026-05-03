package com.neilturner.aerialviews.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MessageRepository {
    private val _messageEvent = MutableSharedFlow<MessageEvent>(extraBufferCapacity = 10)
    val messageEvent: SharedFlow<MessageEvent> = _messageEvent.asSharedFlow()

    fun post(event: MessageEvent) {
        _messageEvent.tryEmit(event)
    }
}
