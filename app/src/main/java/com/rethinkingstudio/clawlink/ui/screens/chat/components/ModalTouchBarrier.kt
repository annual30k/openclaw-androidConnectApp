package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

internal fun Modifier.modalTouchBarrier(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            event.changes.forEach { change ->
                change.consume()
            }
        }
    }
}
